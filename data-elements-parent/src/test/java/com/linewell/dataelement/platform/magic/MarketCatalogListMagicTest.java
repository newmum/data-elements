package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.elasticsearch.EsCommonService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

/** Runs the canonical market list with H2 status reads and a captured read-only ES search. */
class MarketCatalogListMagicTest {
    private static final String RESOURCE_ID = "64c0bb65fad54c538d5b80423623cbf7";
    private static final String TENANT = "validation-tenant";
    private static final String OTHER_TENANT = "validation-other-tenant";
    private static final String DEPT = "validation-department";
    private static final String OTHER_DEPT = "validation-other-department";
    private static final String MARKET_TYPE = "catalogSubscribe";
    private static final String LOGICAL_INDEX = "dataassets";
    private static final String PHYSICAL_INDEX = "elements_dataassets__tenant_validation";

    @Test
    void canonicalResourceCompilesWithAllImports() throws Exception {
        MagicScript.create(CanonicalMagicSources.byId(RESOURCE_ID), null).compile();
    }

    @Test
    void twentyAndHundredHaveOneSearchAndFourPageScopedSqlReads() throws Exception {
        for (int size : List.of(20, 100)) {
            var fixture = new Fixture();
            var ids = IntStream.range(0, size).mapToObj(i -> "validation-%03d".formatted(i)).toList();
            fixture.search.docs = ids.stream().map(MarketCatalogListMagicTest::document).toList();
            fixture.search.total = Map.of("value", 200, "relation", "eq");
            for (String id : ids) {
                fixture.catalog(id, TENANT, 0, 2, 2, 31);
                fixture.form(TENANT, DEPT, MARKET_TYPE, 2, 0, id);
                fixture.favorite(TENANT, DEPT, id);
            }

            var result = fixture.list(Map.of("pageNum", 1, "pageSize", size));

            assertThat(rows(result)).hasSize(size).allSatisfy(item -> {
                assertThat(item.get("applyStatus")).isEqualTo(2);
                assertThat(item.get("collected")).isEqualTo(true);
                assertThat(item.get("canApply")).isEqualTo(false);
                assertThat(item.get("canCollect")).isEqualTo(true);
                assertThat(((Number) item.get("viewCount")).longValue()).isEqualTo(31);
                assertThat(item).doesNotContainKey("internalField");
            });
            assertThat(((Number) result.get("total")).longValue()).isEqualTo(200L);
            assertThat(result.get("totalLimited")).isEqualTo(false);
            assertThat(fixture.reads).hasSize(4);
            assertThat(fixture.search.calls).isEqualTo(1);
            assertThat(((Number) fixture.search.request.get("size")).intValue()).isEqualTo(size);
            assertThat(fixture.search.indexName).isEqualTo(PHYSICAL_INDEX);
            verify(fixture.resolver).resolveTenantIndexName(LOGICAL_INDEX);
            verifyNoMoreInteractions(fixture.resolver);
            assertThat(((List<?>) fixture.search.request.get("_source")).contains("viewCount")).isFalse();
            System.out.printf("Market catalog list %d: 1 API call, 1 ES search, 4 SQL reads%n", size);
        }
    }

    @Test
    void normalizesModernAndActualLegacyFiltersWithoutTrustingCallerIndex() throws Exception {
        var fixture = new Fixture();
        fixture.list(Map.of("pageNum", 2, "pageSize", 20, "keyword", "目录", "orgPath", "a/b",
                "appId", "validation-app", "shareType", 2, "dataProcessLevel", "raw",
                "publishedWithin", "1M", "sortField", "recordCount", "sortDir", "asc"));

        var conditions = fixture.conditions();
        assertPublicationFilters(conditions);
        assertThat(conditions.get("must")).isEqualTo(List.of(Map.of("multi_match",
                Map.of("query", "目录", "fields", List.of("catalogName^3", "catalogNameEn^2")))));
        assertThat(filters(conditions)).contains(Map.of("terms", Map.of("orgPath", List.of("a/b"))),
                Map.of("terms", Map.of("appId", List.of("validation-app"))),
                Map.of("terms", Map.of("shareType", List.of("2"))),
                Map.of("terms", Map.of("dataProcessLevel", List.of("raw"))));
        var range = StructuredValues.objectMap(filters(conditions).stream().filter(item -> item.containsKey("range"))
                .findFirst().orElseThrow().get("range"));
        assertThat(StructuredValues.objectMap(range.get("publishTime")))
                .containsOnlyKeys("gte", "lte", "format").containsEntry("format", "yyyy-MM-dd HH:mm:ss");
        assertThat(fixture.search.request.get("sort")).isEqualTo(List.of(
                Map.of("recordCount", Map.of("order", "asc", "unmapped_type", "keyword", "missing", "_last")),
                Map.of("tid", Map.of("order", "asc", "unmapped_type", "keyword"))));
        assertThat(((Number) fixture.search.request.get("from")).intValue()).isEqualTo(20);
        assertThat(fixture.reads).isEmpty();

        var oldRange = Map.of("gte", "2026-10-01 00:00:00", "lte", "2026-11-01 00:00:00");
        var legacy = List.of(Map.of("term", Map.of("publishStatus", 1)), Map.of("term", Map.of("assetType", "catalog")),
                Map.of("multi_match", Map.of("query", "目录", "fields", List.of("catalogName^3", "catalogNameEn^2"))),
                Map.of("terms", Map.of("policeType", List.of("sample"))), Map.of("range", Map.of("publishTime", oldRange)));
        fixture.list(Map.of("query", legacy, "sortField", "createdTime", "sortDir", "desc"));
        conditions = fixture.conditions();
        assertPublicationFilters(conditions);
        assertThat(filters(conditions)).hasSize(5).contains(Map.of("terms", Map.of("policeType", List.of("sample"))),
                Map.of("range", Map.of("publishTime", Map.of("gte", oldRange.get("gte"), "lte", oldRange.get("lte"),
                        "format", "yyyy-MM-dd HH:mm:ss"))));
        assertThat(fixture.search.request.get("_source")).isInstanceOf(List.class);
        assertThat(((List<?>) fixture.search.request.get("_source")).contains("internalField")).isFalse();
    }

    @Test
    void tenantAndDepartmentScopeAndExactLegacyTokensControlActions() throws Exception {
        var fixture = new Fixture();
        var ids = List.of("catalog_a", "catalog_b", "catalog_c", "catalog-d", "catalog-e", "catalog-f", "catalog_g");
        var docs = new ArrayList<>(ids.stream().map(MarketCatalogListMagicTest::document).toList());
        docs.add(Map.of("tid", "restricted", "assetType", "catalog", "publishStatus", "1", "shareType", "3", "apiId", "api"));
        docs.add(Map.of("tid", "no-resources", "assetType", "catalog", "publishStatus", 1));
        docs.add(Map.of("tid", "unpublished", "assetType", "catalog", "publishStatus", 0));
        docs.add(Map.of("tid", "other-asset", "assetType", "table", "publishStatus", 1));
        docs.add(Map.of("tid", "other-tenant-doc", "assetType", "catalog", "publishStatus", 1, "tenantId", OTHER_TENANT));
        fixture.search.docs = docs;
        fixture.form(TENANT, DEPT, MARKET_TYPE, 4, 0, "catalog_a,catalog_b,catalog-d");
        fixture.form(TENANT, DEPT, MARKET_TYPE, 0, 0, "catalog_a, catalog_b, catalog_c");
        fixture.form(TENANT, DEPT, MARKET_TYPE, 1, 0, "catalog_a, catalog_c");
        fixture.form(TENANT, DEPT, "CATALOG_SUBSCRIPTION", 2, 0, "before, catalog_a, after");
        fixture.form(TENANT, DEPT, MARKET_TYPE, 5, 0, "catalog-e");
        fixture.form(TENANT, DEPT, "need", 2, 0, "catalog-f");
        fixture.form(TENANT, DEPT, MARKET_TYPE, 2, 1, "catalog-f");
        fixture.form(TENANT, OTHER_DEPT, MARKET_TYPE, 2, 0, "catalog-f");
        fixture.form(OTHER_TENANT, DEPT, MARKET_TYPE, 2, 0, "catalog-f");
        fixture.form(TENANT, DEPT, MARKET_TYPE, 2, 0, "before,catalog!1g,catalogXg,after");
        fixture.cart(TENANT, DEPT, "catalog-e", 0);
        fixture.cart(TENANT, DEPT, "catalog-f", 1);
        fixture.cart(TENANT, OTHER_DEPT, "catalog-f", 0);
        fixture.favorite(TENANT, OTHER_DEPT, "catalog-f");
        fixture.favorite(OTHER_TENANT, DEPT, "catalog-f");
        fixture.favorite(TENANT, DEPT, "catalog-a-not-on-page");

        var rows = rows(fixture.list(Map.of("tenantId", OTHER_TENANT, "orgId", OTHER_DEPT)));
        assertThat(rows).hasSize(9);
        assertThat(rows.subList(0, 7).stream().map(item -> item.get("applyStatus")).toList())
                .containsExactly(2, 0, 1, 1, "cart", null, null);
        assertThat(rows.stream().map(item -> item.get("canApply")).toList())
                .containsExactly(false, false, false, false, false, true, true, false, false);
        assertThat(rows).allSatisfy(item -> assertThat(item.get("collected")).isEqualTo(false));
        assertThat(fixture.reads).hasSize(4);

        fixture.user = Map.of("id", "validation-user");
        rows = rows(fixture.list(Map.of()));
        assertThat(rows).allSatisfy(item -> {
            assertThat(item.get("canApply")).isEqualTo(false);
            assertThat(item.get("canCollect")).isEqualTo(false);
            assertThat(item.get("applyStatus")).isNull();
        });
        assertThat(fixture.reads).hasSize(1);
    }

    @Test
    void countsComeOnlyFromApprovedActiveCurrentTenantMainRowsOnThisPage() throws Exception {
        var fixture = new Fixture();
        fixture.user = Map.of("id", "validation-user");
        var ids = List.of("zero", "higher", "foreign", "deleted", "draft", "pending", "missing");
        fixture.search.docs = ids.stream().map(MarketCatalogListMagicTest::document).toList();
        fixture.catalog("zero", TENANT, 0, 2, 2, 0);
        fixture.catalog("higher", TENANT, 0, 2, 2, Long.MAX_VALUE);
        fixture.catalog("foreign", OTHER_TENANT, 0, 2, 2, 53);
        fixture.catalog("deleted", TENANT, 1, 2, 2, 54);
        fixture.catalog("draft", TENANT, 0, 0, 2, 55);
        fixture.catalog("pending", TENANT, 0, 2, 1, 56);
        fixture.catalog("not-on-page", TENANT, 0, 2, 2, 57);

        var result = rows(fixture.list(Map.of("tenantId", OTHER_TENANT)));

        assertThat(result.stream().map(row -> ((Number) row.get("viewCount")).longValue()).toList())
                .containsExactly(0L, Long.MAX_VALUE, 0L, 0L, 0L, 0L, 0L);
        assertThat(result.stream().map(row -> row.get("tid")).toList()).containsExactlyElementsOf(ids);
        assertThat(fixture.reads).hasSize(1);
        assertThat(fixture.search.calls).isEqualTo(1);
        assertThat(fixture.jdbc.queryForObject("SELECT market_view_count FROM da_catalog_t WHERE tid='higher'", Long.class))
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void capsTheBrowseWindowAndDistinguishesMissingIndexFromOtherErrors() throws Exception {
        var fixture = new Fixture();
        fixture.search.total = Map.of("value", 10000, "relation", "gte");
        var bounded = fixture.list(Map.of("pageNum", 334, "pageSize", 30));
        assertThat(((Number) bounded.get("total")).longValue()).isEqualTo(10000L);
        assertThat(bounded.get("totalLimited")).isEqualTo(true);
        assertThat(((Number) fixture.search.request.get("from")).intValue()).isEqualTo(9990);
        assertThat(((Number) fixture.search.request.get("size")).intValue()).isEqualTo(10);
        assertThat(fixture.reads).isEmpty();

        fixture.search.failure = "index_not_found_exception";
        assertThat(fixture.list(Map.of())).containsEntry("total", 0).containsEntry("totalLimited", false);
        fixture.search.failure = "validation cluster error";
        assertThatThrownBy(() -> fixture.list(Map.of())).isInstanceOf(RuntimeException.class);

        fixture.search.failure = null;
        fixture.search.timedOut = true;
        assertCode(fixture.run(Map.of()), 502);
        fixture.search.timedOut = false;
        fixture.search.shardFailures = 1;
        assertCode(fixture.run(Map.of()), 502);
        assertThat(fixture.reads).isEmpty();
    }

    @Test
    void invalidAndUnauthorizedQueriesNeverReachSearchOrSql() throws Exception {
        var fixture = new Fixture();
        for (var invalid : List.of(Map.of("indexType", "elements_dataassets__tenant_other"),
                Map.of("sortField", "_script"), Map.of("sortDir", "unsafe"), Map.of("pageSize", 101),
                Map.of("pageNum", 101, "pageSize", 100), Map.of("publishedWithin", "10y"),
                Map.of("query", List.of(Map.of("script", Map.of("source", "unsafe")))),
                Map.of("query", List.of(Map.of("term", Map.of("publishStatus", 0)))),
                Map.of("query", List.of(Map.of("terms", Map.of("tenantId", List.of(OTHER_TENANT))))),
                Map.of("query", List.of(Map.of("multi_match", Map.of("query", "x", "fields", List.of("internalField"))))),
                Map.of("query", List.of(Map.of("range", Map.of("publishTime", Map.of("gte", "now-10y", "lte", "now"))))))) {
            assertCode(fixture.run(invalid), 400);
            assertThat(fixture.search.calls).isZero();
            assertThat(fixture.reads).isEmpty();
        }
        fixture.user = Map.of();
        assertCode(fixture.run(Map.of()), 401);
        assertThat(fixture.search.calls).isZero();
    }

    private static Map<String, Object> document(String id) {
        return Map.of("tid", id, "assetType", "catalog", "publishStatus", 1, "targetTableId", "validation-table",
                "catalogName", "验证目录", "internalField", "must stay hidden", "viewCount", 999);
    }

    private static List<Map<String, Object>> rows(Map<String, Object> result) {
        return StructuredValues.objectRows(result.get("list"));
    }

    private static List<Map<String, Object>> filters(Map<String, Object> conditions) {
        return StructuredValues.objectRows(conditions.get("filter"));
    }

    private static void assertPublicationFilters(Map<String, Object> conditions) {
        assertThat(filters(conditions)).startsWith(Map.of("term", Map.of("assetType", "catalog")),
                Map.of("term", Map.of("publishStatus", 1)));
        var tenantGate = StructuredValues.objectMap(filters(conditions).get(2).get("bool"));
        assertThat(tenantGate).containsEntry("minimum_should_match", 1);
        assertThat(StructuredValues.objectRows(tenantGate.get("should"))).contains(Map.of("term", Map.of("tenantId", TENANT)));
    }

    private static void assertCode(Object result, int expected) {
        assertThat(result).isInstanceOfSatisfying(ExitValue.class, exit -> assertThat(exit.getValues()[0]).isEqualTo(expected));
    }

    public static final class Scope {
        public String id() {
            return TenantContext.requireTenantId();
        }
    }

    public static final class Search {
        String indexName;
        String failure;
        int calls;
        boolean timedOut;
        int shardFailures;
        List<Map<String, Object>> docs = List.of();
        Map<String, Object> request;
        Object total = Map.of("value", 0, "relation", "eq");

        public Search index(String value) {
            indexName = value;
            return this;
        }

        public Map<String, Object> search(Map<String, Object> input) {
            calls++;
            request = input;
            if (failure != null) throw new IllegalStateException(failure);
            return Map.of("timed_out", timedOut, "_shards", Map.of("failed", shardFailures),
                    "hits", Map.of("total", total, "hits", docs.stream().map(doc -> Map.of("_source", doc)).toList()));
        }
    }

    private static final class Fixture {
        final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:market_list_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"));
        final List<String> reads = new ArrayList<>();
        final StructuredTableReadMagicModule tableRead = TaskRuntimeMagicFixture.structuredReads(jdbc, reads::add);
        final EsCommonService resolver = mock(EsCommonService.class);
        final Search search = new Search();
        Map<String, String> user = Map.of("id", "validation-user", "orgId", DEPT);
        int sequence;

        Fixture() {
            when(resolver.resolveTenantIndexName(LOGICAL_INDEX)).thenReturn(PHYSICAL_INDEX);
            jdbc.execute("""
                    CREATE TABLE da_catalog_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,is_del INT,
                        asset_status INT,flow_status INT,market_view_count BIGINT)
                    """);
            jdbc.execute("CREATE TABLE data_market_collect_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,apply_org_id VARCHAR,catalog_id VARCHAR)");
            jdbc.execute("CREATE TABLE data_market_shopping_cart_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,apply_org_id VARCHAR,catalog_id VARCHAR,is_del INT)");
            jdbc.execute("""
                    CREATE TABLE data_apply_form_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,apply_org_id VARCHAR,
                        type VARCHAR,flow_status INT,is_del INT,catalog_ids VARCHAR(500))
                    """);
        }

        void catalog(String id, String tenant, int deleted, int assetStatus, int flowStatus, long views) {
            jdbc.update("INSERT INTO da_catalog_t VALUES (?,?,?,?,?,?)", id, tenant, deleted, assetStatus, flowStatus, views);
        }

        void form(String tenant, String department, String type, int status, int deleted, String catalogs) {
            jdbc.update("INSERT INTO data_apply_form_t VALUES (?,?,?,?,?,?,?)", "validation-form-" + sequence++,
                    tenant, department, type, status, deleted, catalogs);
        }

        void favorite(String tenant, String department, String catalog) {
            jdbc.update("INSERT INTO data_market_collect_t VALUES (?,?,?,?)", "validation-favorite-" + sequence++,
                    tenant, department, catalog);
        }

        void cart(String tenant, String department, String catalog, int deleted) {
            jdbc.update("INSERT INTO data_market_shopping_cart_t VALUES (?,?,?,?,?)", "validation-cart-" + sequence++,
                    tenant, department, catalog, deleted);
        }

        Map<String, Object> conditions() {
            return StructuredValues.objectMap(StructuredValues.objectMap(search.request.get("query")).get("bool"));
        }

        Map<String, Object> list(Map<String, ?> body) throws Exception {
            Object result = run(body);
            assertThat(result).isInstanceOf(Map.class);
            return StructuredValues.objectMap(result);
        }

        Object run(Map<String, ?> body) throws Exception {
            String source = CanonicalMagicSources.byId(RESOURCE_ID)
                    .replaceAll("(?m)^import (?:[^\\r\\n]+ as (?:me|esCommonService|configData)|elasticsearch|tableRead|tenantRuntime)\\R", "")
                    .replace("import cn.hutool.core.date.DateUtil", "import cn.hutool.core.date.DateUtil\nvar me = () => userMe(body)");
            var bindings = new LinkedHashMap<String, Object>();
            bindings.put("body", body);
            bindings.put("userMe", (Function<Object[], Object>) args -> {
                assertThat(args).hasSize(1);
                assertThat(args[0]).isInstanceOf(Map.class);
                assertThat(((Map<?, ?>) args[0])).isEmpty();
                return user;
            });
            bindings.put("configData", (Function<Object[], Object>) args -> Map.of("es_dataassets", LOGICAL_INDEX));
            bindings.put("esCommonService", resolver);
            bindings.put("elasticsearch", search);
            bindings.put("tableRead", tableRead);
            bindings.put("tenantRuntime", new Scope());
            reads.clear();
            return TenantContext.call(TENANT, () -> MagicScript.create(source, null).execute(new MagicScriptContext(bindings)));
        }
    }
}
