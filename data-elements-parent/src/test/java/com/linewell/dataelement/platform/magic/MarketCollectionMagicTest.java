package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class MarketCollectionMagicTest {
    private static final String ID = "b78cf957a41645ccbea4fda1f6c6b9e9";
    private static final String LOCK_PREFIX = "data-elements:market:favorite:tenant-a:org-a:";

    @Test
    void setsDesiredStateOnceAndPreservesOtherDepartmentsAndTenants() throws Exception {
        var fixture = new Fixture();
        fixture.catalog("catalog", "tenant-a", 2);
        fixture.index.documentId = "catalog";
        fixture.favorite("other-org", "tenant-a", "org-b", "catalog");
        fixture.favorite("other-tenant", "tenant-b", "org-a", "catalog");
        var requested = Map.of("catalogId", "catalog", "collected", true,
                "tenantId", "tenant-b", "orgId", "org-b", "userId", "forged", "appId", "other-app");

        assertEquals(true, fixture.run(requested));
        assertEquals(true, fixture.run(requested));
        assertEquals(1, fixture.index.calls);
        assertEquals("operator", fixture.jdbc.queryForObject("""
                SELECT apply_user_id FROM data_market_collect_t
                WHERE tenant_id='tenant-a' AND apply_org_id='org-a'
                """, String.class));
        fixture.favorite("legacy-duplicate", "tenant-a", "org-a", "catalog");
        assertEquals(false, fixture.run(Map.of("catalogId", "catalog", "collected", false)));
        assertEquals(false, fixture.run(Map.of("catalogId", "catalog", "collected", false)));
        assertEquals(2, fixture.count());
    }

    @Test
    void legacyToggleAndUnpublishedCasesRemainScoped() throws Exception {
        var fixture = new Fixture();
        fixture.catalog("foreign", "tenant-b", 2);
        fixture.catalog("draft", "tenant-a", 0);
        fixture.catalog("local", "tenant-a", 2);
        for (String catalog : List.of("foreign", "draft")) {
            assertExit(404, fixture.run(Map.of("catalogId", catalog, "collected", true)));
        }
        assertEquals(0, fixture.index.calls);
        assertExit(404, fixture.run(Map.of("catalogId", "local", "collected", true)));
        assertEquals(0, fixture.count());
        fixture.index.documentId = "local";
        assertEquals(true, fixture.run(Map.of("catalogId", "local")));
        assertEquals(false, fixture.run(Map.of("catalogId", "local")));
        assertEquals(0, fixture.count());
    }

    @Test
    void distinguishesMissingIndexAndOutagesAndReleasesTheLock() throws Exception {
        var fixture = new Fixture();
        fixture.catalog("local", "tenant-a", 2);
        fixture.index.error = "index_not_found_exception";
        assertExit(404, fixture.run(Map.of("catalogId", "local", "collected", true)));
        verify(fixture.lock).unlock();
        fixture.index.error = "validation search unavailable";
        assertThrows(RuntimeException.class, () -> fixture.run(Map.of("catalogId", "local")));
        fixture.index.error = null;
        fixture.index.incomplete = true;
        assertExit(502, fixture.run(Map.of("catalogId", "local")));
        assertEquals(0, fixture.count());
    }

    @Test
    void validatesInputAndReportsConcurrentChanges() throws Exception {
        var fixture = new Fixture();
        assertExit(400, fixture.run(Map.of("catalogId", "../outside")));
        assertExit(400, fixture.run(Map.of("catalogId", "local", "collected", "true")));
        when(fixture.lock.tryLock()).thenReturn(false);
        assertExit(409, fixture.run(Map.of("catalogId", "local")));
        assertEquals(0, fixture.count());
    }

    private static void assertExit(int code, Object result) {
        var exit = assertInstanceOf(ExitValue.class, result);
        assertEquals(code, ((Number) exit.getValues()[0]).intValue());
    }

    public record Tenant(String id) {}

    public static class Index {
        String documentId;
        String error;
        boolean incomplete;
        int calls;

        public String resolveTenantIndexName(String logical) {
            assertEquals("dataassets", logical);
            return "tenant-a-dataassets";
        }

        public Index index(String index) {
            assertEquals("tenant-a-dataassets", index);
            return this;
        }

        public Map<String, Object> search(Map<String, Object> request) {
            calls++;
            assertEquals(1, request.get("size"));
            var query = StructuredValues.objectMap(request.get("query"));
            var filters = StructuredValues.objectRows(StructuredValues.objectMap(query.get("bool")).get("filter"));
            assertEquals(Map.of("term", Map.of("assetType", "catalog")), filters.get(1));
            assertEquals(Map.of("term", Map.of("publishStatus", 1)), filters.get(2));
            var tenant = StructuredValues.objectRows(StructuredValues.objectMap(filters.get(3).get("bool")).get("should"));
            assertEquals(Map.of("term", Map.of("tenantId", "tenant-a")), tenant.getFirst());
            if (error != null) throw new IllegalStateException(error);
            var hits = documentId == null ? List.of()
                    : List.of(Map.of("_source", Map.of("tid", documentId, "assetType", "catalog")));
            return Map.of("hits", Map.of("hits", hits), "timed_out", incomplete);
        }
    }

    private static final class Fixture {
        private final JdbcTemplate jdbc;
        private final SQLModule db;
        private final StructuredTableReadMagicModule read;
        private final MagicScript script;
        private final Index index = new Index();
        private final RLock lock = mock(RLock.class);
        private final RedissonClient redis = mock(RedissonClient.class);

        Fixture() throws Exception {
            var source = new DriverManagerDataSource("jdbc:h2:mem:market_favorite_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            read = TaskRuntimeMagicFixture.structuredReads(jdbc, sql -> {});
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
            db.setSqlInterceptors(List.of());
            var canonical = CanonicalMagicSources.byId(ID);
            MagicScript.create(canonical, null).compile();
            script = MagicScript.create(canonical.replaceAll("(?m)^import (?!cn\\.hutool\\.)[^\\r\\n]+\\R", ""), null);
            when(redis.getLock(org.mockito.ArgumentMatchers.startsWith(LOCK_PREFIX))).thenReturn(lock);
            when(lock.tryLock()).thenReturn(true);
            jdbc.execute("""
                    CREATE TABLE da_catalog_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,is_del INT,
                      asset_status INT,flow_status INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE data_market_collect_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,catalog_id VARCHAR,
                      apply_user_id VARCHAR,apply_user_name VARCHAR,apply_org_id VARCHAR,org_tree_path VARCHAR)
                    """);
        }

        void catalog(String id, String tenant, int state) {
            jdbc.update("INSERT INTO da_catalog_t VALUES (?,?,0,2,?)", id, tenant, state);
        }

        void favorite(String id, String tenant, String department, String catalog) {
            jdbc.update("INSERT INTO data_market_collect_t VALUES (?,?,?,'teammate','同事',?,'0001')",
                    id, tenant, catalog, department);
        }

        int count() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM data_market_collect_t", Integer.class);
        }

        Object run(Map<String, ?> body) {
            Function<Object[], Object> me = args -> Map.of("id", "operator", "orgId", "org-a",
                    "userName", "验证用户", "serialNumber", "0001");
            Function<Object[], Object> config = args -> Map.of("es_dataassets", "dataassets");
            return TenantContext.call("tenant-a", () -> script.execute(new MagicScriptContext(Map.of(
                    "body", body, "db", db, "tableRead", read, "tenantRuntime", new Tenant("tenant-a"),
                    "me", me, "configData", config, "esCommonService", index, "elasticsearch", index,
                    "redissonClient", redis, "log", LoggerFactory.getLogger(MarketCollectionMagicTest.class)))));
        }
    }
}
