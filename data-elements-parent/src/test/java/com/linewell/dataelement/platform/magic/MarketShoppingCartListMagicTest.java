package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.elasticsearch.EsCommonService;
import com.linewell.dataelement.elasticsearch.EsProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.elasticsearch.ElasticSearchModule;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.exception.MagicExitException;
import org.ssssssss.script.runtime.ExitValue;

/** Executes the canonical cart reader and shared eligibility function with SQL and ES request counters. */
class MarketShoppingCartListMagicTest {
    private static final String RESOURCE_ID = "9fc031d411354d8baff7006b11f5ad0a";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TENANT = "validation-tenant";
    private static final String OTHER_TENANT = "validation-other-tenant";
    private static final String DEPT = "validation-dept";
    private static final String OTHER_DEPT = "validation-other-dept";
    private static final String ACTOR = "validation-user";
    private static final Timestamp ADDED = Timestamp.valueOf("2026-07-01 12:00:00");

    @Test
    void canonicalResourceCompilesWithAllImports() throws Exception {
        MagicScript.create(CanonicalMagicSources.byId(RESOURCE_ID), null).compile();
    }

    @Test
    void twentyAndHundredUseThreeReadsAndOneSearchWhileBadgeUsesOneAggregate() throws Exception {
        var fixture = new Fixture();
        for (int index = 0; index < 125; index++) {
            String id = "validation-catalog-%03d".formatted(index);
            fixture.catalog(id, TENANT, "目录 " + index, "资源系统", 2, 0, "validation-table");
            fixture.cart("validation-cart-%03d".formatted(index), TENANT, DEPT, id, 0);
            fixture.property(id, TENANT, "catalog", "shareType", "2", 0, ADDED);
            fixture.publish(id, TENANT);
        }

        for (int size : List.of(20, 100)) {
            var expectedIds = IntStream.range(0, size).mapToObj(index -> "validation-catalog-%03d".formatted(124 - index)).toList();
            var page = fixture.page(1, size, "");
            assertThat(list(page)).hasSize(size);
            assertThat(number(page, "total")).isEqualTo(125);
            assertThat(number(page, "cartTotal")).isEqualTo(125);
            assertThat(number(page, "pageSize")).isEqualTo(size);
            assertThat(list(page).getFirst().get("shoppingCarId")).isEqualTo("validation-cart-124");
            assertThat(list(page)).allSatisfy(row -> {
                assertThat(row.get("available")).isEqualTo(true);
                assertThat(row.get("availabilityStatus")).isEqualTo("available");
                assertThat(row.get("resType")).isEqualTo(List.of("table"));
            });
            assertThat(fixture.reads).hasSize(3);
            fixture.assertSearched(expectedIds);
            System.out.printf("Market cart page %d: 1 API call, 3 cart SQL reads, 1 ES search%n", size);

            var filtered = fixture.page(1, size, "资源系统");
            assertThat(list(filtered)).hasSize(size);
            assertThat(number(filtered, "total")).isEqualTo(125);
            assertThat(number(filtered, "cartTotal")).isEqualTo(125);
            assertThat(fixture.reads).hasSize(4);
            fixture.assertSearched(expectedIds);
            System.out.printf("Market cart filtered page %d: 1 API call, 4 cart SQL reads, 1 ES search%n", size);
        }

        var last = fixture.page(100, 100, "");
        assertThat(number(last, "pageNum")).isEqualTo(2);
        assertThat(list(last)).hasSize(25);
        assertThat(list(last).getFirst().get("shoppingCarId")).isEqualTo("validation-cart-024");

        assertThat(number(StructuredValues.objectMap(fixture.run(Map.of("mode", "count"))), "total")).isEqualTo(125);
        assertThat(fixture.reads).hasSize(1);
        assertThat(fixture.reads.getFirst()).containsIgnoringCase("count(*)");
        assertThat(fixture.reads.getFirst()).doesNotContain("da_catalog_t", "da_prop_t");
        assertThat(fixture.searches).isEmpty();

        assertThat(StructuredValues.objectRows(fixture.run(null))).hasSize(10);
        assertThat(fixture.searches).hasSize(1);
        assertThat(StructuredValues.objectRows(fixture.run(Map.of("keyword", "资源系统")))).hasSize(10);
    }

    @Test
    void searchAppliesBeforePagingAndReturnsAnIndependentCartTotal() throws Exception {
        var fixture = new Fixture();
        fixture.org(TENANT, "目录所有部门");
        fixture.org(OTHER_TENANT, "其他租户部门");
        fixture.catalog("validation-a", TENANT, "中文目录", "业务平台", 2, 0, "table-a");
        fixture.catalog("validation-b", TENANT, "第二目录", "其他系统", 2, 0, "table-b");
        fixture.cart("validation-cart-a", TENANT, DEPT, "validation-a", 0);
        fixture.cart("validation-cart-b", TENANT, DEPT, "validation-b", 0);

        for (String keyword : List.of("中文", "业务平台", "VALIDATION-A")) {
            var page = fixture.page(1, 1, keyword);
            assertThat(number(page, "total")).isEqualTo(1);
            assertThat(number(page, "cartTotal")).isEqualTo(2);
            assertThat(list(page)).singleElement().satisfies(row -> {
                assertThat(row.get("tid")).isEqualTo("validation-a");
                assertThat(row.get("orgName")).isEqualTo("目录所有部门");
            });
            assertThat(fixture.reads).hasSize(4);
        }
        assertThat(number(fixture.page(1, 1, "所有部门"), "total")).isEqualTo(2);
        var empty = fixture.page(10, 20, "其他租户部门");
        assertThat(number(empty, "total")).isZero();
        assertThat(number(empty, "cartTotal")).isEqualTo(2);
        assertThat(number(empty, "pageNum")).isEqualTo(1);
        assertThat(list(empty)).isEmpty();
        assertThat(fixture.reads).hasSize(2);
        assertThat(fixture.searches).isEmpty();
    }

    @Test
    void staleRowsRemainRemovableWithoutLeakingCatalogFieldsAcrossScope() throws Exception {
        var fixture = new Fixture();
        fixture.catalog("validation-foreign", OTHER_TENANT, "其他租户目录", "其他租户系统", 2, 0, "table-a");
        fixture.catalog("validation-deleted", TENANT, "已删除目录", "目录系统", 2, 1, "table-b");
        fixture.catalog("validation-pending", TENANT, "未批准目录", "目录系统", 1, 0, "table-c");
        fixture.catalog("validation-invalid,id", TENANT, "历史编号异常", "目录系统", 2, 0, "table-d");
        fixture.cart("validation-cart-missing", TENANT, DEPT, "validation-missing", 0);
        fixture.cart("validation-cart-foreign", TENANT, DEPT, "validation-foreign", 0);
        fixture.cart("validation-cart-deleted", TENANT, DEPT, "validation-deleted", 0);
        fixture.cart("validation-cart-pending", TENANT, DEPT, "validation-pending", 0);
        fixture.cart("validation-cart-invalid", TENANT, DEPT, "validation-invalid,id", 0);
        fixture.cart("validation-other-dept", TENANT, OTHER_DEPT, "validation-pending", 0);
        fixture.cart("validation-other-tenant", OTHER_TENANT, DEPT, "validation-foreign", 0);
        fixture.cart("validation-inactive", TENANT, DEPT, "validation-foreign", 1);

        var result = StructuredValues.objectMap(fixture.run(Map.of("mode", "page", "tenantId", OTHER_TENANT,
                "orgId", OTHER_DEPT, "applyOrgId", OTHER_DEPT, "appId", "validation-other-app")));
        assertThat(number(result, "total")).isEqualTo(5);
        var rows = list(result);
        assertThat(rows).extracting(row -> row.get("shoppingCarId")).containsExactly(
                "validation-cart-pending", "validation-cart-missing", "validation-cart-invalid", "validation-cart-foreign", "validation-cart-deleted");
        assertThat(rows.get(2).get("availabilityReason")).isEqualTo("目录编号无效");
        assertThat(rows.stream().filter(row -> !"validation-cart-invalid".equals(row.get("shoppingCarId")))).allSatisfy(row -> {
            assertThat(row.get("catalogName")).isEqualTo("目录不可用");
            assertThat(row.get("appName")).isNull();
            assertThat(row.get("orgName")).isNull();
            assertThat(row.get("available")).isEqualTo(false);
            assertThat(row.get("availabilityStatus")).isEqualTo("unavailable");
            assertThat(row.get("resType")).isEqualTo(List.of());
        });
        assertThat(fixture.reads).hasSize(3);
        assertThat(fixture.searches).isEmpty();
    }

    @Test
    void newestActiveCatalogPropertyControlsAvailabilityAndResources() throws Exception {
        var fixture = new Fixture();
        fixture.catalog("validation-api", TENANT, "接口目录", "目录系统", 2, 0, null);
        fixture.catalog("validation-restricted", TENANT, "不予共享", "目录系统", 2, 0, "table-r");
        fixture.catalog("validation-unlinked", TENANT, "未关联资源", "目录系统", 2, 0, null);
        for (String id : List.of("validation-api", "validation-restricted", "validation-unlinked")) {
            fixture.cart(id, TENANT, DEPT, id, 0);
        }
        Timestamp later = Timestamp.valueOf("2026-07-02 12:00:00");
        fixture.property("validation-api", TENANT, "catalog", "apiId", "api-allowed", 0, ADDED);
        fixture.property("validation-api", TENANT, "catalog", "shareType", "3", 0, ADDED);
        fixture.property("validation-api", TENANT, "catalog", "shareType", "2", 0, later);
        fixture.property("validation-api", TENANT, "catalog", "shareType", "3", 1, later);
        fixture.property("validation-api", OTHER_TENANT, "catalog", "fileId", "foreign-file", 0, later);
        fixture.property("validation-api", TENANT, "apply_form", "dirId", "wrong-category", 0, later);
        fixture.property("validation-api", TENANT, "catalog", "updateCycle", "daily", 0, later);
        fixture.property("validation-restricted", TENANT, "catalog", "shareType", "3", 0, later);
        fixture.publish("validation-api", TENANT);
        fixture.publish("validation-restricted", TENANT);
        fixture.publish("validation-unlinked", TENANT);

        var rows = list(fixture.page(1, 20, ""));
        assertThat(rows).extracting(row -> row.get("available")).containsExactly(false, false, true);
        var api = rows.getLast();
        assertThat(api.get("resType")).isEqualTo(List.of("api"));
        assertThat(api.get("shareType")).isEqualTo("2");
        assertThat(api.get("updateCycle")).isEqualTo("daily");
        assertThat(fixture.reads).hasSize(3);
        fixture.assertSearched(List.of("validation-api"));
    }

    @Test
    void eligibilityIsScopedAndPartialMatchesOrFailuresKeepCartRowsRemovable() throws Exception {
        var fixture = new Fixture();
        for (String id : List.of("validation-live", "validation-unpublished", "validation-foreign-index", "validation-other-dept")) {
            fixture.catalog(id, TENANT, "目录", "资源系统", 2, 0, "table");
        }
        fixture.catalog("validation-foreign", OTHER_TENANT, "其他租户目录", "其他系统", 2, 0, "table");
        fixture.publish("validation-live", TENANT);
        fixture.publish("validation-foreign-index", OTHER_TENANT);
        fixture.publish("validation-other-dept", TENANT);
        fixture.publish("validation-foreign", OTHER_TENANT);
        fixture.cart("validation-cart-1", TENANT, DEPT, "validation-live", 0);
        fixture.cart("validation-cart-2", TENANT, DEPT, "validation-live", 0);
        fixture.cart("validation-cart-3", TENANT, DEPT, "validation-unpublished", 0);
        fixture.cart("validation-cart-4", TENANT, DEPT, "validation-foreign-index", 0);
        fixture.cart("validation-cart-5", TENANT, DEPT, "validation-foreign", 0);
        fixture.cart("validation-cart-6", TENANT, OTHER_DEPT, "validation-other-dept", 0);
        fixture.cart("validation-cart-7", OTHER_TENANT, DEPT, "validation-foreign", 0);
        var ids = List.of("validation-foreign-index", "validation-unpublished", "validation-live");

        var page = fixture.page(1, 20, "");
        assertThat(number(page, "cartTotal")).isEqualTo(5);
        assertThat(list(page)).extracting(row -> row.get("availabilityStatus"))
                .containsExactly("unavailable", "unavailable", "unavailable", "available", "available");
        assertThat(list(page).get(2).get("availabilityReason")).isEqualTo("当前目录已下架或不再具备申请资格");
        fixture.assertSearched(ids);

        fixture.failedShards = 1;
        assertTemporaryFailure(fixture.page(1, 20, ""));
        fixture.assertSearched(ids);

        fixture.failedShards = 0;
        fixture.searchError = new IOException("validation socket reset");
        assertTemporaryFailure(fixture.page(1, 20, ""));
        assertThat(fixture.searches).hasSize(1);
    }

    private static void assertTemporaryFailure(Map<String, Object> page) {
        assertThat(number(page, "total")).isEqualTo(5);
        assertThat(number(page, "cartTotal")).isEqualTo(5);
        assertThat(list(page)).extracting(row -> row.get("shoppingCarId")).containsExactly(
                "validation-cart-5", "validation-cart-4", "validation-cart-3", "validation-cart-2", "validation-cart-1");
        assertThat(list(page)).extracting(row -> row.get("availabilityStatus"))
                .containsExactly("unavailable", "unconfirmed", "unconfirmed", "unconfirmed", "unconfirmed");
        assertThat(list(page)).extracting(row -> row.get("available")).containsOnly(false);
        assertThat(list(page).getLast().get("availabilityReason")).isEqualTo("暂时无法确认资格，请重试");
    }

    @Test
    void invalidPagingAndMissingDepartmentAreHandledBeforeReading() throws Exception {
        var fixture = new Fixture();
        for (var invalid : List.of(Map.of("mode", "unknown"), Map.of("mode", "page", "pageSize", 101),
                Map.of("mode", "page", "pageNum", 0), Map.of("mode", "page", "pageSize", "1.2"),
                Map.of("keyword", "a".repeat(101)))) {
            assertThat(fixture.run(invalid)).isInstanceOfSatisfying(ExitValue.class,
                    exit -> assertThat(exit.getValues()[0]).isEqualTo(400));
            assertThat(fixture.reads).isEmpty();
        }
        fixture.user = Map.of("id", ACTOR);
        assertThat(fixture.run(Map.of("mode", "count", "orgId", DEPT))).isEqualTo(Map.of("total", 0));
        assertThat(number(fixture.page(4, 100, ""), "pageNum")).isEqualTo(1);
        assertThat(fixture.reads).isEmpty();
        fixture.user = Map.of();
        assertThat(fixture.run(Map.of("mode", "count"))).isInstanceOfSatisfying(ExitValue.class,
                exit -> assertThat(exit.getValues()[0]).isEqualTo(401));
    }

    private static long number(Map<String, Object> value, String key) {
        return ((Number) value.get(key)).longValue();
    }

    private static List<Map<String, Object>> list(Map<String, Object> page) {
        return StructuredValues.objectRows(page.get("list"));
    }

    public static final class Scope {
        public String id() {
            return TenantContext.requireTenantId();
        }
    }

    private static final class Fixture {
        final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:market_cart_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"));
        final List<String> reads = new ArrayList<>();
        final List<Request> searches = new ArrayList<>();
        final Map<String, Map<String, Object>> indexed = new LinkedHashMap<>();
        final StructuredTableReadMagicModule tableRead = TaskRuntimeMagicFixture.structuredReads(jdbc, reads::add);
        final ElasticsearchOperations indexOperations = mock(ElasticsearchOperations.class);
        final EsCommonService indexResolver;
        final ElasticSearchModule elasticsearch;
        final MagicScript script;
        final MagicScript eligibility;
        Map<String, String> user = Map.of("id", ACTOR, "orgId", DEPT);
        int propertyNumber;
        int failedShards;
        IOException searchError;

        Fixture() throws Exception {
            jdbc.execute("""
                    CREATE TABLE data_market_shopping_cart_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,
                        apply_org_id VARCHAR,catalog_id VARCHAR,is_del INT,created_time TIMESTAMP)
                    """);
            jdbc.execute("""
                    CREATE TABLE da_catalog_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,org_id VARCHAR,
                        catalog_name VARCHAR,catalog_name_en VARCHAR,app_id VARCHAR,app_name VARCHAR,
                        target_table_id VARCHAR,asset_status INT,flow_status INT,is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE rm_org_t(id VARCHAR,tenant_id VARCHAR,name VARCHAR,deleted INT,
                        PRIMARY KEY(id,tenant_id))
                    """);
            jdbc.execute("""
                    CREATE TABLE da_prop_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,parent_id VARCHAR,
                        data_type VARCHAR,prop_name VARCHAR,prop_value CLOB,is_del INT,updated_time TIMESTAMP)
                    """);
            var properties = new EsProperties();
            properties.setPrefix("elements_");
            properties.getTenant().setLegacyTenantId("validation-legacy-tenant");
            indexResolver = new EsCommonService(indexOperations, properties);
            var client = mock(RestClient.class);
            when(client.performRequest(any(Request.class))).thenAnswer(invocation -> {
                Request request = invocation.getArgument(0);
                searches.add(request);
                if (searchError != null) {
                    throw searchError;
                }
                var hits = new ArrayList<Map<String, Object>>();
                for (JsonNode id : searchBody(request).at("/query/bool/filter/0/ids/values")) {
                    var projection = indexed.get(id.asText());
                    if (projection != null) {
                        hits.add(Map.of("_id", id.asText(), "_source", projection));
                    }
                }
                var reply = Map.of("timed_out", false, "_shards", Map.of("failed", failedShards),
                        "hits", Map.of("total", Map.of("value", hits.size(), "relation", "eq"), "hits", hits));
                var response = mock(Response.class);
                when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
                when(response.getEntity()).thenReturn(new StringEntity(JSON.writeValueAsString(reply), ContentType.APPLICATION_JSON));
                return response;
            });
            elasticsearch = new ElasticSearchModule(client);
            script = MagicScript.create("var me = () => authUser(body)\n" + withoutImports(CanonicalMagicSources.byId(RESOURCE_ID)), null);
            eligibility = MagicScript.create(withoutImports(CanonicalMagicSources.byPath(
                    "function/01.通用函数/24.超市目录发布资格.ms", "09ecb205502244aba26408368d56aad3")), null);
        }

        void publish(String id, String tenant) {
            indexed.put(id, Map.of("tid", id, "assetType", "catalog", "publishStatus", 1,
                    "tenantId", tenant, "shareType", "2", "apiId", "validation-api"));
        }

        void assertSearched(List<String> expectedIds) throws IOException {
            assertThat(searches).hasSize(1);
            var request = searches.getFirst();
            assertThat(request.getMethod()).isEqualTo("POST");
            assertThat(request.getEndpoint()).isEqualTo("/elements_dataassets__tenant_" + TENANT + "/_search");
            var query = searchBody(request);
            assertThat(query.at("/size").asInt()).isEqualTo(expectedIds.size());
            assertThat(query.at("/query/bool/filter/0/ids/values")).isEqualTo(JSON.valueToTree(expectedIds));
            assertThat(query.at("/query/bool/filter/3/bool/should/0/term/tenantId").asText()).isEqualTo(TENANT);
            verifyNoInteractions(indexOperations);
        }

        private static JsonNode searchBody(Request request) throws IOException {
            return JSON.readTree(EntityUtils.toString(request.getEntity()));
        }

        private static String withoutImports(String source) {
            return source.replaceAll("(?m)^import [^\\r\\n]+\\R", "");
        }

        void cart(String id, String tenant, String department, String catalogId, int deleted) {
            jdbc.update("INSERT INTO data_market_shopping_cart_t VALUES (?,?,?,?,?,?)",
                    id, tenant, department, catalogId, deleted, ADDED);
        }

        void catalog(String id, String tenant, String name, String app, int flow, int deleted, String table) {
            jdbc.update("INSERT INTO da_catalog_t VALUES (?,?,'validation-org',?,?, 'validation-app',?,?,2,?,?)",
                    id, tenant, name, id, app, table, flow, deleted);
        }

        void org(String tenant, String name) {
            jdbc.update("INSERT INTO rm_org_t VALUES ('validation-org',?,?,0)", tenant, name);
        }

        void property(String parentId, String tenant, String type, String name, String value, int deleted, Timestamp updated) {
            jdbc.update("INSERT INTO da_prop_t VALUES (?,?,?,?,?,?,?,?)", "validation-prop-" + propertyNumber++,
                    tenant, parentId, type, name, value, deleted, updated);
        }

        Map<String, Object> page(int pageNum, int pageSize, String keyword) throws Exception {
            return StructuredValues.objectMap(run(Map.of("mode", "page", "pageNum", pageNum,
                    "pageSize", pageSize, "keyword", keyword)));
        }

        Object run(Map<String, ?> body) throws Exception {
            var bindings = new LinkedHashMap<String, Object>();
            bindings.put("body", body);
            bindings.put("tableRead", tableRead);
            bindings.put("tenantRuntime", new Scope());
            bindings.put("authUser", (Function<Object[], Object>) args -> {
                assertThat(args[0]).isEqualTo(Map.of());
                return user;
            });
            bindings.put("log", LoggerFactory.getLogger(MarketShoppingCartListMagicTest.class));
            bindings.put("marketCatalogEligibility", (Function<Object[], Object>) this::checkEligibility);
            reads.clear();
            searches.clear();
            return TenantContext.call(TENANT, () -> script.execute(new MagicScriptContext(bindings)));
        }

        private Object checkEligibility(Object[] arguments) {
            var result = eligibility.execute(new MagicScriptContext(Map.of(
                    "rows", arguments[0], "tenantRuntime", new Scope(), "esCommonService", indexResolver,
                    "elasticsearch", elasticsearch, "configData", (Function<Object[], Object>) unused -> Map.of("es_dataassets", "dataassets"))));
            // Match the Magic function registry, which converts a script exit into a caller exception.
            if (result instanceof ExitValue exit) {
                throw new MagicExitException(exit);
            }
            return result;
        }
    }
}
