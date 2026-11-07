package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class MarketCatalogDetailMagicTest {
    private static final String DETAIL = "bc7ab46f70354dab95ca7deb785d72fd";

    @Test
    void requiresTenantApprovalAndAnExplicitlyPublishedCatalog() throws Exception {
        var fixture = new Fixture();
        fixture.catalog("foreign", "tenant-b", 2, "table");
        fixture.catalog("draft", "tenant-a", 0, "table");
        fixture.catalog("unpublished", "tenant-a", 2, "table");

        for (String id : List.of("foreign", "draft", "missing")) {
            assertExit(404, fixture.run(id, "org-a"));
            assertEquals(1, fixture.reads);
            assertEquals(0, fixture.index.calls);
        }
        assertExit(400, fixture.run("%", "org-a"));
        assertEquals(0, fixture.reads);
        assertExit(404, fixture.run("unpublished", "org-a"));
        assertEquals(1, fixture.reads);
        var tenantFilter = Map.of("bool", Map.of("minimum_should_match", 1, "should", List.of(
                Map.of("term", Map.of("tenantId", "tenant-a")),
                Map.of("bool", Map.of("must_not", List.of(Map.of("exists", Map.of("field", "tenantId"))))))));
        assertEquals(List.of(
                Map.of("term", Map.of("tid", "unpublished")),
                Map.of("term", Map.of("assetType", "catalog")),
                Map.of("term", Map.of("publishStatus", 1)),
                tenantFilter), fixture.index.filters);
    }

    @Test
    void distinguishesIncompleteSearchFromUnavailableCatalog() throws Exception {
        var fixture = new Fixture();
        fixture.catalog("catalog", "tenant-a", 2, "table");
        fixture.index.document = Map.of("tid", "catalog", "assetType", "catalog", "publishStatus", 1,
                "tenantId", "tenant-b");
        assertExit(404, fixture.run("catalog", "org-a"));
        assertEquals(1, fixture.reads);
        fixture.index.timedOut = true;
        assertExit(502, fixture.run("catalog", "org-a"));
        assertEquals(1, fixture.reads);
    }

    @Test
    void appliesDepartmentScopeAndMatchesCompleteLegacyIds() throws Exception {
        var fixture = new Fixture();
        fixture.catalog("catalog_a", "tenant-a", 2, "table");
        fixture.index.document = Map.of("tid", "catalog_a", "assetType", "catalog",
                "publishStatus", 1, "shareType", "2", "apiId", "service-a");
        fixture.form("validation-substring", "tenant-a", "org-a", "before,catalogXa,after", 2);
        fixture.form("validation-escape", "tenant-a", "org-a", "catalog~2a", 2);
        fixture.form("validation-other-tenant", "tenant-b", "org-a", "catalog_a", 2);
        fixture.form("validation-other-org", "tenant-a", "org-b", "catalog_a", 1);
        fixture.jdbc.update("INSERT INTO data_market_shopping_cart_t VALUES ('cart','tenant-a','org-a','catalog_a',0)");
        fixture.jdbc.update("INSERT INTO data_market_collect_t VALUES ('favorite','tenant-a','org-b','catalog_a')");

        var cart = StructuredValues.objectMap(fixture.run("catalog_a", "org-a"));
        assertEquals("cart", cart.get("applicationState"));
        assertEquals(true, cart.get("canApply"));
        assertEquals(false, cart.get("collected"));
        assertEquals(List.of("table", "api"), cart.get("resType"));
        assertEquals(4, fixture.reads);
        assertEquals(1, fixture.index.calls);

        fixture.form("validation-draft", "tenant-a", "org-a", "before, catalog_a", 0);
        fixture.form("validation-approved", "tenant-a", "org-a", "catalog_a,after", 2);
        var approved = StructuredValues.objectMap(fixture.run("catalog_a", "org-a"));
        assertEquals("approved", approved.get("applicationState"));
        assertEquals(false, approved.get("canApply"));
        assertEquals(3, fixture.reads);
        assertEquals(1, fixture.index.calls);
    }

    @Test
    void explainsWhyAnApplicationIsUnavailable() throws Exception {
        var fixture = new Fixture();
        fixture.catalog("catalog", "tenant-a", 2, null);
        fixture.index.document = Map.of("tid", "catalog", "assetType", "catalog", "publishStatus", 1, "shareType", 3);
        assertEquals("restricted", fixture.state("catalog", "org-a"));
        fixture.index.document = Map.of("tid", "catalog", "assetType", "catalog", "publishStatus", 1, "shareType", 2);
        assertEquals("no-resources", fixture.state("catalog", "org-a"));
        assertEquals("no-department", fixture.state("catalog", ""));
        assertEquals(1, fixture.reads);
        fixture.index.document = Map.of("tid", "catalog", "assetType", "catalog", "publishStatus", 1,
                "shareType", 2, "fileId", "file");
        assertEquals("available", fixture.state("catalog", "org-a"));
    }

    @Test
    void returnsMainCounterWithoutAnotherReadOrAStaleIndexFallback() throws Exception {
        var fixture = new Fixture();
        fixture.catalog("catalog", "tenant-a", 2, "table");
        fixture.index.document = Map.of("tid", "catalog", "assetType", "catalog", "publishStatus", 1,
                "viewCount", 9999);

        for (long expected : List.of(0L, 41L, Long.MAX_VALUE)) {
            fixture.jdbc.update("UPDATE da_catalog_t SET market_view_count=? WHERE tid='catalog'", expected);
            var result = StructuredValues.objectMap(fixture.run("catalog", ""));
            assertEquals(expected, ((Number) result.get("viewCount")).longValue());
            assertEquals(1, fixture.reads);
            assertEquals(1, fixture.index.calls);
        }
    }

    private static void assertExit(int expected, Object result) {
        var exit = assertInstanceOf(ExitValue.class, result);
        assertEquals(expected, ((Number) exit.getValues()[0]).intValue());
    }

    public static class Tenant {
        public String id() { return TenantContext.requireTenantId(); }
    }

    public static class Index {
        private int calls;
        private Object filters;
        private Map<String, Object> document;
        private boolean timedOut;

        public String resolveTenantIndexName(String name) {
            assertEquals("dataassets", name);
            return "dataassets__tenant_tenant-a";
        }

        public Index index(String name) {
            assertEquals("dataassets__tenant_tenant-a", name);
            return this;
        }

        public Map<String, Object> search(Map<String, Object> request) {
            calls++;
            assertEquals(1, request.get("size"));
            assertEquals(false, request.get("track_total_hits"));
            var query = StructuredValues.objectMap(request.get("query"));
            filters = StructuredValues.objectMap(query.get("bool")).get("filter");
            var hits = document == null ? List.of() : List.of(Map.of("_source", document));
            return Map.of("hits", Map.of("hits", hits), "timed_out", timedOut);
        }
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:market_detail_" + UUID.randomUUID()
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"));
        private final Index index = new Index();
        private int reads;
        private final StructuredTableReadMagicModule tableRead =
                TaskRuntimeMagicFixture.structuredReads(jdbc, sql -> reads++);
        private final MagicScript script;

        Fixture() throws Exception {
            String canonical = CanonicalMagicSources.byId(DETAIL);
            MagicScript.create(canonical, null).compile();
            script = MagicScript.create(canonical.replaceAll("(?m)^import [^\\r\\n]+\\R", ""), null);
            jdbc.execute("""
                    CREATE TABLE da_catalog_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,is_del INT,asset_status INT,
                      flow_status INT,catalog_name VARCHAR,org_id VARCHAR,data_source_type VARCHAR,target_table_id VARCHAR,
                      market_view_count BIGINT DEFAULT 0)
                    """);
            jdbc.execute("""
                    CREATE TABLE data_apply_form_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,apply_org_id VARCHAR,
                      type VARCHAR,catalog_ids VARCHAR,flow_status INT,is_del INT)
                    """);
            jdbc.execute("CREATE TABLE data_market_collect_t(tid VARCHAR,tenant_id VARCHAR,apply_org_id VARCHAR,catalog_id VARCHAR)");
            jdbc.execute("CREATE TABLE data_market_shopping_cart_t(tid VARCHAR,tenant_id VARCHAR,apply_org_id VARCHAR,catalog_id VARCHAR,is_del INT)");
        }

        void catalog(String id, String tenant, int status, String table) {
            jdbc.update("INSERT INTO da_catalog_t VALUES (?,?,0,2,?,'登记目录','origin-org','MYSQL',?,0)",
                    id, tenant, status, table);
        }

        void form(String id, String tenant, String org, String catalogs, int status) {
            jdbc.update("INSERT INTO data_apply_form_t VALUES (?,?,?,'catalogSubscribe',?,?,0)",
                    id, tenant, org, catalogs, status);
        }

        Object state(String id, String org) {
            return StructuredValues.objectMap(run(id, org)).get("applicationState");
        }

        Object run(String id, String org) {
            reads = 0;
            index.calls = 0;
            Function<Object[], Object> me = args -> Map.of("id", "owner", "orgId", org);
            Function<Object[], Object> config = args -> Map.of("es_dataassets", "dataassets");
            return TenantContext.call("tenant-a", () -> script.execute(new MagicScriptContext(Map.of(
                    "body", Map.of("tid", id, "tenantId", "tenant-b", "orgId", "org-b"),
                    "tableRead", tableRead, "tenantRuntime", new Tenant(),
                    "esCommonService", index, "elasticsearch", index, "me", me, "configData", config))));
        }
    }
}
