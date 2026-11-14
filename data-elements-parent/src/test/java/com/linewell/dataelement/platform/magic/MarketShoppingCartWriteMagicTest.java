package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.elasticsearch.EsCommonService;
import com.linewell.dataelement.elasticsearch.EsProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.elasticsearch.ElasticSearchModule;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.exception.MagicExitException;
import org.ssssssss.script.runtime.ExitValue;

class MarketShoppingCartWriteMagicTest {
    private static final String CART = "624a0191565640a18c81c131ab0b4228";
    private static final ObjectMapper JSON = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void dryRunAndCreateUseTrustedDepartmentAndReturnLegacyId(int rows) throws Exception {
        var fixture = new Fixture();
        fixture.catalog("validation", "tenant-a", 0, 2, 2);
        fixture.cart("foreign-tenant", "tenant-b", "department", "validation", 0);
        fixture.cart("foreign-org", "tenant-a", "other", "validation", 0);
        fixture.cart("deleted-cart", "tenant-a", "department", "validation", 1);
        for (int i = 0; i < rows; i++) {
            fixture.cart("validation-cart-" + i, "tenant-a", "department", "validation-extra-" + i, 0);
        }
        var body = Map.of("catalogId", "validation", "tenantId", "tenant-b", "applyOrgId", "other",
                "applyUserId", "other", "appId", "other");
        assertEquals(Map.of("dryRun", true, "valid", true), fixture.run(Map.of("catalogId", "validation", "dryRun", true)));
        assertEquals(3, fixture.reads);
        assertEquals(rows + 3, fixture.countCart());
        assertEquals(0, fixture.locks.lock.attempts);
        fixture.assertOneSearch("validation");

        var tid = assertInstanceOf(String.class, fixture.run(body));
        assertEquals(3, fixture.reads);
        assertEquals(rows + 4, fixture.countCart());
        assertEquals("data-elements:market:cart:tenant-a:department", fixture.locks.key);
        assertEquals("tenant-a", fixture.cartValue(tid, "tenant_id"));
        assertEquals("department", fixture.cartValue(tid, "apply_org_id"));
        assertEquals("owner", fixture.cartValue(tid, "apply_user_id"));
        assertEquals(1, fixture.locks.lock.releases);
        fixture.assertOneSearch("validation");

        assertExit(409, fixture.run(body));
        assertEquals(rows + 4, fixture.countCart());
        assertEquals(2, fixture.locks.lock.releases);
        assertTrue(fixture.searches.isEmpty());
    }

    @Test
    void updateChecksOldRowAndNewCatalogBeforeWriting() throws Exception {
        var fixture = new Fixture();
        for (String id : List.of("current", "next", "duplicate")) {
            fixture.catalog(id, "tenant-a", 0, 2, 2);
        }
        fixture.catalog("other-tenant-catalog", "tenant-b", 0, 2, 2);
        fixture.catalog("deleted-catalog", "tenant-a", 1, 2, 2);
        fixture.catalog("incomplete-catalog", "tenant-a", 0, 1, 2);
        fixture.catalog("pending-catalog", "tenant-a", 0, 2, 1);
        fixture.cart("own-cart", "tenant-a", "department", "current", 0);
        fixture.cart("other-tenant-cart", "tenant-b", "department", "current", 0);
        fixture.cart("other-dept-cart", "tenant-a", "other", "current", 0);
        fixture.cart("deleted-cart", "tenant-a", "department", "current", 1);
        fixture.cart("duplicate-cart", "tenant-a", "department", "duplicate", 0);
        for (String tid : List.of("other-tenant-cart", "other-dept-cart", "deleted-cart")) {
            assertExit(404, fixture.run(Map.of("tid", tid, "catalogId", "next")));
            assertEquals("current", fixture.cartValue(tid, "catalog_id"));
            assertTrue(fixture.searches.isEmpty());
        }
        for (String id : List.of("other-tenant-catalog", "deleted-catalog", "incomplete-catalog", "pending-catalog")) {
            assertExit(404, fixture.run(Map.of("tid", "own-cart", "catalogId", id)));
            assertTrue(fixture.searches.isEmpty());
        }
        assertExit(409, fixture.run(Map.of("tid", "own-cart", "catalogId", "duplicate")));
        assertTrue(fixture.searches.isEmpty());
        fixture.indexed.remove("current");
        fixture.searchError = new IOException("connection reset");
        assertEquals("own-cart", fixture.run(Map.of("tid", "own-cart", "catalogId", "current")));
        assertEquals(4, fixture.reads);
        assertTrue(fixture.searches.isEmpty());
        fixture.searchError = null;
        fixture.indexed.remove("next");
        assertExit(404, fixture.run(Map.of("tid", "own-cart", "catalogId", "next")));
        assertEquals("current", fixture.cartValue("own-cart", "catalog_id"));
        fixture.publish("next", "tenant-a");
        assertEquals("own-cart", fixture.run(Map.of("tid", "own-cart", "catalogId", "next")));
        assertEquals(4, fixture.reads);
        fixture.assertOneSearch("next");
        assertEquals("next", fixture.cartValue("own-cart", "catalog_id"));
        assertEquals(5, fixture.countCart());
    }

    @Test
    void rejectsUnpublishedRestrictedEmptyAndForeignProjectionsWithoutChangingCart() throws Exception {
        var fixture = new Fixture();
        var id = "validation";
        fixture.catalog(id, "tenant-a", 0, 2, 2);
        var published = fixture.indexed.get(id);
        for (var invalid : List.<Map<String, Object>>of(
                Map.of("publishStatus", 0), Map.of("shareType", "3"),
                Map.of("targetTableId", ""), Map.of("tenantId", "tenant-b"),
                Map.of("assetType", "table"), Map.of("tid", "other-id"))) {
            var projection = new LinkedHashMap<>(published);
            projection.putAll(invalid);
            fixture.indexed.put(id, projection);
            assertExit(404, fixture.run(Map.of("catalogId", id)));
            assertEquals(0, fixture.countCart());
            fixture.assertOneSearch(id);
        }
        fixture.indexed.remove(id);
        assertExit(404, fixture.run(Map.of("catalogId", id)));
        assertEquals(0, fixture.countCart());
        fixture.assertOneSearch(id);
        assertEquals(7, fixture.locks.lock.releases);
    }

    @Test
    void legacyProjectionMayUseTheRegisteredPrimaryResourceAndIndexedResources() throws Exception {
        var fixture = new Fixture();
        var id = "validation";
        fixture.catalog(id, "tenant-a", 0, 2, 2);
        var legacy = Map.<String, Object>of("tid", id, "assetType", "catalog", "publishStatus", 1, "shareType", 2);
        fixture.indexed.put(id, legacy);
        fixture.jdbc.update("UPDATE da_catalog_t SET target_table_id=? WHERE tid=?", "validation-table", id);
        var body = Map.of("catalogId", id, "dryRun", true);
        assertEquals(Map.of("dryRun", true, "valid", true), fixture.run(body));
        fixture.assertOneSearch(id);
        fixture.jdbc.update("UPDATE da_catalog_t SET target_table_id=NULL WHERE tid=?", id);
        for (String field : List.of("apiId", "fileId", "dirId")) {
            var projection = new LinkedHashMap<>(legacy);
            projection.put(field, "validation-resource");
            fixture.indexed.put(id, projection);
            assertEquals(Map.of("dryRun", true, "valid", true), fixture.run(body));
            fixture.assertOneSearch(id);
        }
        assertEquals(0, fixture.countCart());
        assertEquals(0, fixture.locks.lock.attempts);
    }

    @Test
    void distinguishesMissingIndexFromSearchErrorsAndReleasesLock() throws Exception {
        var fixture = new Fixture();
        fixture.catalog("validation", "tenant-a", 0, 2, 2);
        var body = Map.of("catalogId", "validation");
        fixture.searchError = new IOException("index_not_found_exception");
        assertExit(404, fixture.run(body));
        fixture.searchError = new IOException("connection reset");
        assertTrue(assertThrows(RuntimeException.class, () -> fixture.run(body)).getMessage().contains("connection reset"));
        fixture.searchError = null;
        fixture.timedOut = true;
        assertExit(502, fixture.run(body));
        fixture.timedOut = false;
        fixture.failedShards = 1;
        assertExit(502, fixture.run(body));
        assertEquals(0, fixture.countCart());
        assertEquals(4, fixture.locks.lock.releases);
    }

    @ParameterizedTest
    @CsvSource({
            "0,catalogSubscribe,validation_%~",
            "1,CATALOG_SUBSCRIPTION,'validation_%~ ,next'",
            "2,catalogSubscribe,'prev, validation_%~'",
            "4,catalogSubscribe,'prev, validation_%~ ,next'"
    })
    void exactCsvRejectsOnlyActiveMarketApplicationsInCurrentScope(int status, String type, String legacyIds)
            throws Exception {
        var fixture = new Fixture();
        fixture.catalog("validation_%~", "tenant-a", 0, 2, 2);
        fixture.form("other-tenant", "tenant-b", "department", 0, 2, type, legacyIds);
        fixture.form("other-org", "tenant-a", "other", 0, 2, type, legacyIds);
        fixture.form("deleted", "tenant-a", "department", 1, 2, type, legacyIds);
        fixture.form("rejected", "tenant-a", "department", 0, 5, type, legacyIds);
        fixture.form("unrelated", "tenant-a", "department", 0, 2, "unrelated", legacyIds);
        fixture.form("nearby", "tenant-a", "department", 0, 2, type, "validationXabc~,validation_%~000");
        var body = Map.of("catalogId", "validation_%~", "dryRun", true);
        assertEquals(Map.of("dryRun", true, "valid", true), fixture.run(body));
        assertEquals(3, fixture.reads);

        fixture.form("active", "tenant-a", "department", 0, status, type, legacyIds);
        assertExit(409, fixture.run(body));
        assertEquals(3, fixture.reads);
        assertTrue(fixture.searches.isEmpty());
        assertEquals(0, fixture.countCart());
        assertEquals(0, fixture.locks.lock.attempts);
    }

    @Test
    void rejectsInvalidIdentityAndDoesNotReleaseAnotherRequestsLock() throws Exception {
        var fixture = new Fixture();
        fixture.catalog("validation", "tenant-a", 0, 2, 2);
        assertExit(400, fixture.run(Map.of("catalogId", "validation", "dryRun", "true")));
        assertExit(400, fixture.run(Map.of("catalogId", "validation,other")));
        assertExit(400, fixture.run(Map.of("catalogId", "validation other")));
        assertExit(400, fixture.run(Map.of("catalogId", "validation", "tid", false)));
        assertExit(409, fixture.run(Map.of("catalogId", "validation"), Map.of("id", "owner")));
        assertExit(401, fixture.run(Map.of("catalogId", "validation"), Map.of("orgId", "department")));
        assertEquals(0, fixture.locks.lock.attempts);

        fixture.locks.lock.heldByAnother = true;
        assertExit(409, fixture.run(Map.of("catalogId", "validation")));
        assertEquals(0, fixture.reads);
        assertEquals(0, fixture.locks.lock.releases);
        fixture.locks.lock.heldByAnother = false;
        fixture.jdbc.execute("ALTER TABLE data_market_shopping_cart_t ADD CHECK(catalog_id <> 'validation')");
        assertThrows(RuntimeException.class, () -> fixture.run(Map.of("catalogId", "validation")));
        assertEquals(1, fixture.locks.lock.releases);
        assertEquals(0, fixture.countCart());
    }

    private static void assertExit(int expected, Object result) {
        var exit = assertInstanceOf(ExitValue.class, result);
        assertEquals(expected, ((Number) exit.getValues()[0]).intValue());
    }

    public static class Tenant {
        public String id() { return TenantContext.requireTenantId(); }
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc;
        private final SQLModule db;
        private final StructuredTableReadMagicModule tableRead;
        private final ElasticsearchOperations indexOperations = mock(ElasticsearchOperations.class);
        private final EsCommonService indexResolver;
        private final ElasticSearchModule elasticsearch;
        private final List<Request> searches = new ArrayList<>();
        private final Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        private final String script;
        private final MagicScript eligibility;
        private final ExistingTargetMaterializationMagicTest.LockClient locks =
                new ExistingTargetMaterializationMagicTest.LockClient();
        private int reads;
        private IOException searchError;
        private boolean timedOut;
        private int failedShards;

        Fixture() throws Exception {
            var source = new DriverManagerDataSource("jdbc:h2:mem:market_cart_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            tableRead = TaskRuntimeMagicFixture.structuredReads(jdbc, sql -> reads++);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            var columns = new ColumnMapperAdapter();
            columns.add(new CamelColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(new SQLInterceptor() {
                @Override
                public void preHandle(BoundSql sql, RequestEntity request) {
                    if (sql.getSql().stripLeading().toLowerCase(Locale.ROOT).startsWith("select")) {
                        reads++;
                    }
                }
            }));
            var properties = new EsProperties();
            properties.setPrefix("elements_");
            properties.getTenant().setLegacyTenantId("tenant-legacy");
            indexResolver = new EsCommonService(indexOperations, properties);
            var client = mock(RestClient.class);
            when(client.performRequest(any(Request.class))).thenAnswer(invocation -> {
                Request request = invocation.getArgument(0);
                searches.add(request);
                if (searchError != null) {
                    throw searchError;
                }
                var query = JSON.readTree(EntityUtils.toString(request.getEntity()));
                var catalogId = query.at("/query/bool/filter/0/ids/values/0").asText();
                var projection = indexed.get(catalogId);
                var hits = projection == null ? List.of() : List.of(Map.of("_id", catalogId, "_source", projection));
                var reply = Map.of("timed_out", timedOut, "_shards", Map.of("failed", failedShards),
                        "hits", Map.of("total", Map.of("value", hits.size(), "relation", "eq"), "hits", hits));
                var response = mock(Response.class);
                when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
                when(response.getEntity()).thenReturn(new StringEntity(JSON.writeValueAsString(reply),
                        ContentType.APPLICATION_JSON));
                return response;
            });
            elasticsearch = new ElasticSearchModule(client);
            script = "var me = () => authUser(body)\n"
                    + CanonicalMagicSources.byId(CART).replaceAll("(?m)^import (?!cn\\.hutool\\.)[^\\r\\n]*\\R", "");
            var sharedSource = CanonicalMagicSources.byPath("function/01.通用函数/24.超市目录发布资格.ms",
                    "09ecb205502244aba26408368d56aad3");
            eligibility = MagicScript.create(sharedSource.replaceAll("(?m)^import [^\\r\\n]+\\R", ""), null);
            jdbc.execute("""
                    CREATE TABLE da_catalog_t (tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,is_del INT,
                        asset_status INT,flow_status INT,target_table_id VARCHAR)
                    """);
            jdbc.execute("""
                    CREATE TABLE data_market_shopping_cart_t (tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,is_del INT,
                        catalog_id VARCHAR,apply_org_id VARCHAR,apply_user_id VARCHAR,apply_user_name VARCHAR,
                        created_by VARCHAR,created_time TIMESTAMP,updated_by VARCHAR,updated_time TIMESTAMP)
                    """);
            jdbc.execute("""
                    CREATE TABLE data_apply_form_t (tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,apply_org_id VARCHAR,
                        is_del INT,flow_status INT,type VARCHAR,catalog_ids VARCHAR)
                    """);
        }

        void catalog(String id, String tenant, int deleted, int asset, int flow) {
            jdbc.update("INSERT INTO da_catalog_t (tid,tenant_id,is_del,asset_status,flow_status) VALUES (?,?,?,?,?)",
                    id, tenant, deleted, asset, flow);
            publish(id, tenant);
        }

        void publish(String id, String tenant) {
            indexed.put(id, Map.of("tid", id, "assetType", "catalog", "publishStatus", 1,
                    "tenantId", tenant, "shareType", "1", "targetTableId", "validation-table"));
        }

        void assertOneSearch(String catalogId) throws IOException {
            assertEquals(1, searches.size());
            var request = searches.getFirst();
            assertEquals("POST", request.getMethod());
            assertEquals("/elements_dataassets__tenant_tenant-a/_search", request.getEndpoint());
            JsonNode query = JSON.readTree(EntityUtils.toString(request.getEntity()));
            assertEquals(1, query.at("/size").asInt());
            assertEquals(JSON.valueToTree(List.of(catalogId)), query.at("/query/bool/filter/0/ids/values"));
            verifyNoInteractions(indexOperations);
        }

        void cart(String id, String tenant, String dept, String catalog, int deleted) {
            jdbc.update("INSERT INTO data_market_shopping_cart_t (tid,tenant_id,apply_org_id,catalog_id,is_del)"
                    + " VALUES (?,?,?,?,?)", id, tenant, dept, catalog, deleted);
        }

        void form(String id, String tenant, String dept, int deleted, int status, String type, String ids) {
            jdbc.update("INSERT INTO data_apply_form_t VALUES (?,?,?,?,?,?,?)", id, tenant, dept, deleted, status, type, ids);
        }

        String cartValue(String tid, String column) {
            return jdbc.queryForObject("SELECT " + column + " FROM data_market_shopping_cart_t WHERE tid=?", String.class, tid);
        }

        int countCart() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM data_market_shopping_cart_t", Integer.class);
        }

        Object run(Map<String, ?> body) {
            return run(body, Map.of("id", "owner", "userName", "登记人", "orgId", "department"));
        }

        Object run(Map<String, ?> body, Map<String, String> user) {
            reads = 0;
            searches.clear();
            try (var ignored = TenantContext.use("tenant-a")) {
                return MagicScript.create(script, null).execute(new MagicScriptContext(Map.of(
                        "body", body, "db", db, "tableRead", tableRead, "tenantRuntime", new Tenant(),
                        "authUser", (Function<Object[], Object>) arguments -> {
                            assertEquals(Map.of(), arguments[0]);
                            return user;
                        }, "redissonClient", locks,
                        "log", LoggerFactory.getLogger(MarketShoppingCartWriteMagicTest.class),
                        "marketCatalogEligibility", (Function<Object[], Object>) this::checkEligibility)));
            }
        }

        private Object checkEligibility(Object[] arguments) {
            var result = eligibility.execute(new MagicScriptContext(Map.of(
                    "rows", arguments[0], "tenantRuntime", new Tenant(),
                    "esCommonService", indexResolver, "elasticsearch", elasticsearch,
                    "configData", (Function<Object[], Object>) unused -> Map.of("es_dataassets", "dataassets"))));
            if (result instanceof ExitValue exit) {
                throw new MagicExitException(exit);
            }
            return result;
        }
    }
}
