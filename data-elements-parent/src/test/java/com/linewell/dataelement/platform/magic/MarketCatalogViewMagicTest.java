package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import com.linewell.dataelement.utils.StructuredValues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import javax.sql.DataSource;
import org.apache.http.HttpHost;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.elasticsearch.ElasticSearchModule;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

/** Executes canonical Magic, real ES search encoding and the transaction counter against H2. */
class MarketCatalogViewMagicTest {
    private static final String RESOURCE_ID = "f12fde8d078541abb7083a4cfc6fb3b4";
    private static final String TENANT = "validation-tenant";
    private static final String OTHER_TENANT = "validation-other-tenant";
    private static final String CATALOG = "validation-catalog";
    private static final String PHYSICAL = "elements_dataassets__tenant_" + TENANT;

    @Test
    void canonicalResourceCompilesWithAllImports() throws Exception {
        MagicScript.create(CanonicalMagicSources.byId(RESOURCE_ID), null).compile();
    }

    @Test
    void verifiesPublicationThenIncrementsDatabaseOnceAndOnlyProjectsTheConfirmedCount() throws Exception {
        var fixture = new Fixture();
        fixture.catalog(CATALOG, TENANT, 2, 2, 0);

        var result = fixture.run(Map.of("tid", CATALOG, "indexType", "untrusted-index", "tenantId", OTHER_TENANT,
                "appId", "untrusted-app", "viewCount", 500));

        assertCount(result, 8);
        assertThat(fixture.value()).isEqualTo(8);
        assertThat(fixture.counter.calls).isEqualTo(1);
        assertThat(fixture.reads).hasSize(1);
        assertThat(fixture.counterStatements).hasSize(2);
        assertThat(fixture.projection.calls).isEqualTo(1);
        assertThat(fixture.projection.count).isEqualTo(8L);
        assertThat(fixture.requests).singleElement().satisfies(request -> {
            assertThat(request.getMethod()).isEqualTo("POST");
            assertThat(request.getEndpoint()).isEqualTo("/" + PHYSICAL + "/_search");
        });
        var query = new ObjectMapper().readTree(EntityUtils.toString(fixture.requests.getFirst().getEntity()));
        assertThat(query.path("track_total_hits").asBoolean()).isTrue();
        assertThat(query.path("size").asInt()).isEqualTo(1);
        var filters = query.path("query").path("bool").path("filter");
        assertThat(filters.get(0).path("ids").path("values").get(0).asText()).isEqualTo(CATALOG);
        assertThat(filters.get(1).path("term").path("assetType").asText()).isEqualTo("catalog");
        assertThat(filters.get(2).path("term").path("publishStatus").asInt()).isEqualTo(1);
        assertThat(filters.get(3).toString()).contains(TENANT, "must_not");
        assertThat(fixture.jdbc.queryForObject("SELECT resource_revision_no FROM da_catalog_t", Integer.class)).isEqualTo(3);
        System.out.println("Market view: API=1, SQL=3 (precheck=1, lock=1, update=1), ES eligibility search=1, projection=1");
    }

    @Test
    void malformedOrUnauthenticatedRequestsDoNotReadSqlOrEs() throws Exception {
        var fixture = new Fixture();
        for (String value : List.of(" ", "../internal", "validation/foo", "validation?query", "x".repeat(33))) {
            assertCode(fixture.run(Map.of("tid", value)), 400);
        }
        assertCode(fixture.run(Map.of()), 400);
        fixture.user = Map.of();
        assertCode(fixture.run(Map.of("tid", CATALOG)), 401);
        fixture.tenant = "";
        assertCode(fixture.run(Map.of("tid", CATALOG)), 401);
        assertThat(fixture.reads).isEmpty();
        assertThat(fixture.requests).isEmpty();
        assertThat(fixture.counter.calls).isZero();
        assertThat(fixture.projection.calls).isZero();
    }

    @Test
    void requiresApprovedActiveCurrentTenantCatalogBeforeIndexAccess() throws Exception {
        var fixture = new Fixture();
        fixture.catalog("validation-foreign", OTHER_TENANT, 2, 2, 0);
        fixture.catalog("validation-deleted", TENANT, 2, 2, 1);
        fixture.catalog("validation-asset-draft", TENANT, 0, 2, 0);
        fixture.catalog("validation-flow-draft", TENANT, 2, 0, 0);
        for (String id : List.of("validation-foreign", "validation-deleted", "validation-asset-draft",
                "validation-flow-draft", "validation-missing")) {
            assertCode(fixture.run(Map.of("tid", id, "tenantId", OTHER_TENANT)), 404);
            assertThat(fixture.reads).hasSize(1);
        }
        assertThat(fixture.requests).isEmpty();
        assertThat(fixture.counter.calls).isZero();
        assertThat(fixture.projection.calls).isZero();
    }

    @Test
    void missingOrUnpublishedProjectionNeverIncrementsDatabase() throws Exception {
        var fixture = new Fixture();
        fixture.catalog(CATALOG, TENANT, 2, 2, 0);
        fixture.documents = List.of();
        fixture.total = Map.of("value", 0, "relation", "eq");
        assertCode(fixture.run(Map.of("tid", CATALOG)), 404);
        fixture.total = Map.of("value", 1, "relation", "eq");
        for (Map<String, Object> source : List.of(
                Map.<String, Object>of("tid", "validation-wrong", "assetType", "catalog", "publishStatus", 1),
                Map.<String, Object>of("tid", CATALOG, "assetType", "table", "publishStatus", 1),
                Map.<String, Object>of("tid", CATALOG, "assetType", "catalog", "publishStatus", 0),
                Map.<String, Object>of("tid", CATALOG, "assetType", "catalog", "publishStatus", 1, "tenantId", OTHER_TENANT))) {
            fixture.documents = List.of(Map.of("_id", CATALOG, "_source", source));
            assertCode(fixture.run(Map.of("tid", CATALOG)), 404);
        }
        fixture.documents = List.of(Map.of("_id", "validation-wrong-id", "_source", published()));
        assertCode(fixture.run(Map.of("tid", CATALOG)), 404);
        fixture.error(404, "index_not_found_exception");
        assertCode(fixture.run(Map.of("tid", CATALOG)), 404);
        assertThat(fixture.counter.calls).isZero();
        assertThat(fixture.value()).isEqualTo(7);
    }

    @Test
    void incompleteOrFailedEligibilitySearchCannotBecomeACountableVisit() throws Exception {
        var fixture = new Fixture();
        fixture.catalog(CATALOG, TENANT, 2, 2, 0);
        for (String reply : List.of("{}", "{\"hits\":{\"hits\":[]}}",
                "{\"timed_out\":true,\"_shards\":{\"failed\":0},\"hits\":{\"total\":0,\"hits\":[]}}",
                "{\"timed_out\":false,\"_shards\":{\"failed\":1},\"hits\":{\"total\":0,\"hits\":[]}}",
                "{\"timed_out\":false,\"_shards\":{\"failed\":0},\"hits\":{\"total\":{\"value\":0,\"relation\":\"gte\"},\"hits\":[]}}")) {
            fixture.reply = reply;
            assertCode(fixture.run(Map.of("tid", CATALOG)), 502);
        }
        fixture.error(403, "security_exception");
        assertThatThrownBy(() -> fixture.run(Map.of("tid", CATALOG))).isInstanceOf(RuntimeException.class);
        assertThat(fixture.counter.calls).isZero();
        assertThat(fixture.projection.calls).isZero();
        assertThat(fixture.value()).isEqualTo(7);
    }

    @Test
    void mainRowIsRecheckedAfterTheEsReadBeforeAnIncrement() throws Exception {
        var fixture = new Fixture();
        fixture.catalog(CATALOG, TENANT, 2, 2, 0);
        fixture.beforeReply = () -> fixture.jdbc.update("UPDATE da_catalog_t SET flow_status=0 WHERE tid=?", CATALOG);

        assertCode(fixture.run(Map.of("tid", CATALOG)), 404);

        assertThat(fixture.counter.calls).isEqualTo(1);
        assertThat(fixture.counterStatements).hasSize(1);
        assertThat(fixture.projection.calls).isZero();
        assertThat(fixture.value()).isEqualTo(7);
    }

    @Test
    void projectionFailuresNeverTurnACommittedIncrementIntoARetryableFailure() throws Exception {
        for (boolean throwFailure : List.of(false, true)) {
            var fixture = new Fixture();
            fixture.catalog(CATALOG, TENANT, 2, 2, 0);
            fixture.projection.confirmed = false;
            fixture.projection.fail = throwFailure;

            assertCount(fixture.run(Map.of("tid", CATALOG)), 8);

            assertThat(fixture.counter.calls).isEqualTo(1);
            assertThat(fixture.projection.calls).isEqualTo(1);
            assertThat(fixture.value()).isEqualTo(8);
        }
    }

    @Test
    void saturatedAndLegacyTenantlessPublishedCatalogsRemainBrowsable() throws Exception {
        var fixture = new Fixture();
        fixture.catalog(CATALOG, TENANT, 2, 2, 0);
        fixture.jdbc.update("UPDATE da_catalog_t SET market_view_count=?", Long.MAX_VALUE);
        fixture.documents = List.of(Map.of("_id", CATALOG, "_source",
                Map.of("tid", CATALOG, "assetType", "catalog", "publishStatus", 1, "shareType", 3)));

        assertCount(fixture.run(Map.of("tid", CATALOG)), Long.MAX_VALUE);

        assertThat(fixture.counter.calls).isEqualTo(1);
        assertThat(fixture.counterStatements).hasSize(1);
        assertThat(fixture.value()).isEqualTo(Long.MAX_VALUE);
        assertThat(fixture.projection.count).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void anUnknownCommitResultIsNotRetriedAndDoesNotStartAProjection() throws Exception {
        var fixture = new Fixture();
        fixture.catalog(CATALOG, TENANT, 2, 2, 0);
        fixture.counter.failAfterCommit = true;

        assertThatThrownBy(() -> fixture.run(Map.of("tid", CATALOG)))
                .hasRootCauseMessage("validation commit outcome unknown");

        assertThat(fixture.counter.calls).isEqualTo(1);
        assertThat(fixture.projection.calls).isZero();
        assertThat(fixture.value()).isEqualTo(8);
    }

    private static Map<String, Object> published() {
        return Map.of("tid", CATALOG, "assetType", "catalog", "publishStatus", 1, "tenantId", TENANT);
    }

    private static void assertCount(Object result, long expected) {
        assertThat(StructuredValues.objectRows(result)).singleElement().satisfies(item -> {
            assertThat(item).containsOnlyKeys("tid", "viewCount").containsEntry("tid", CATALOG);
            assertThat(((Number) item.get("viewCount")).longValue()).isEqualTo(expected);
        });
    }

    private static void assertCode(Object result, int expected) {
        assertThat(result).isInstanceOfSatisfying(ExitValue.class, exit -> assertThat(exit.getValues()[0]).isEqualTo(expected));
    }

    public record Scope(String id) {}

    public static final class Projection {
        int calls;
        Object count;
        boolean confirmed = true;
        boolean fail;

        public String resolveTenantIndexName(String logical) {
            assertThat(logical).isEqualTo("dataassets");
            return PHYSICAL;
        }

        public boolean advanceCatalogCounter(String logical, String id, Object value) {
            assertThat(logical).isEqualTo("dataassets");
            assertThat(id).isEqualTo(CATALOG);
            calls++;
            count = value;
            if (fail) throw new IllegalStateException("validation index unavailable");
            return confirmed;
        }
    }

    public static final class Counter {
        final StructuredCounterMagicModule delegate;
        int calls;
        boolean failAfterCommit;

        Counter(StructuredCounterMagicModule delegate) { this.delegate = delegate; }

        public Map<String, Object> incrementAndGet(String table, String column, String id, Map<String, Object> expected) {
            assertThat(table).isEqualTo("da_catalog_t");
            assertThat(column).isEqualTo("market_view_count");
            assertThat(expected).containsExactlyInAnyOrderEntriesOf(Map.of("is_del", 0, "asset_status", 2, "flow_status", 2));
            calls++;
            var counted = delegate.incrementAndGet(table, column, id, expected);
            if (failAfterCommit) throw new IllegalStateException("validation commit outcome unknown");
            return counted;
        }
    }

    public static final class Log {
        public void warn(String message) { assertThat(message).contains("浏览量已保存"); }
    }

    private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    private static final class Fixture {
        final DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:market_view_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final List<String> reads = new ArrayList<>();
        final List<String> counterStatements = new ArrayList<>();
        final StructuredTableReadMagicModule tableRead = TaskRuntimeMagicFixture.structuredReads(jdbc, reads::add);
        final List<Request> requests = new ArrayList<>();
        final ElasticSearchModule elasticsearch;
        final Projection projection = new Projection();
        final Counter counter;
        final MagicScript script;
        Map<String, String> user = Map.of("id", "validation-user");
        String tenant = TENANT;
        int status = 200;
        String reply;
        Runnable beforeReply = () -> {};
        Object total = Map.of("value", 1, "relation", "eq");
        List<Map<String, Object>> documents = List.of(Map.of("_id", CATALOG, "_source", published()));

        Fixture() throws Exception {
            jdbc.execute("""
                    CREATE TABLE da_catalog_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,is_del INT,
                        asset_status INT,flow_status INT,market_view_count BIGINT DEFAULT 7,
                        resource_revision_no INT DEFAULT 3)
                    """);
            DataSource counted = (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                    new Class<?>[] {DataSource.class}, (proxy, method, arguments) -> {
                        Object result = invoke(source, method, arguments);
                        if (result instanceof Connection connection) {
                            return Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                                    (connectionProxy, operation, parameters) -> {
                                        if (operation.getName().equals("prepareStatement")) counterStatements.add((String) parameters[0]);
                                        return invoke(connection, operation, parameters);
                                    });
                        }
                        return result;
                    });
            var registry = mock(TenantDataSourceRegistry.class);
            when(registry.dataSourceForTenant(TENANT)).thenReturn(counted);
            counter = new Counter(new StructuredCounterMagicModule(registry));
            var client = mock(RestClient.class);
            when(client.performRequest(any(Request.class))).thenAnswer(invocation -> {
                Request request = invocation.getArgument(0);
                requests.add(request);
                beforeReply.run();
                var response = mock(Response.class);
                when(response.getHost()).thenReturn(new HttpHost("127.0.0.1"));
                when(response.getRequestLine()).thenReturn(new BasicRequestLine(request.getMethod(), request.getEndpoint(), HttpVersion.HTTP_1_1));
                when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, status, "validation"));
                String payload = reply == null ? new ObjectMapper().writeValueAsString(Map.of("timed_out", false,
                        "_shards", Map.of("failed", 0), "hits", Map.of("total", total, "hits", documents))) : reply;
                when(response.getEntity()).thenReturn(new StringEntity(payload, ContentType.APPLICATION_JSON));
                if (status >= 400) throw new ResponseException(response);
                return response;
            });
            elasticsearch = new ElasticSearchModule(client);
            script = MagicScript.create("var me = () => authUser(body)\n" + CanonicalMagicSources.byId(RESOURCE_ID)
                    .replaceAll("(?m)^import [^\\r\\n]+\\R", ""), null);
        }

        void catalog(String id, String ownerTenant, int asset, int flow, int deleted) {
            jdbc.update("INSERT INTO da_catalog_t(tid,tenant_id,is_del,asset_status,flow_status) VALUES (?,?,?,?,?)",
                    id, ownerTenant, deleted, asset, flow);
        }

        long value() { return jdbc.queryForObject("SELECT market_view_count FROM da_catalog_t WHERE tid=?", Long.class, CATALOG); }

        void error(int code, String error) {
            status = code;
            reply = "{\"error\":{\"type\":\"" + error + "\"},\"status\":" + code + "}";
        }

        Object run(Map<String, ?> body) {
            reads.clear();
            counterStatements.clear();
            try (var ignored = TenantContext.use(tenant.isEmpty() ? TENANT : tenant)) {
                return script.execute(new MagicScriptContext(Map.of("body", body, "tenantRuntime", new Scope(tenant),
                        "tableRead", tableRead, "tableCounter", counter, "esCommonService", projection,
                        "elasticsearch", elasticsearch, "log", new Log(),
                        "configData", (Function<Object[], Object>) args -> Map.of("es_dataassets", "dataassets"),
                        "authUser", (Function<Object[], Object>) args -> {
                            assertThat(args[0]).isEqualTo(Map.of());
                            return user;
                        })));
            }
        }
    }
}
