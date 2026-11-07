package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.LowerColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.exception.MagicExitException;
import org.ssssssss.script.runtime.ExitValue;

class CatalogDirectoryDetailMagicTest {
    private static final String DETAIL = "datapd_catalog_detail_compat_01";
    private static final String READ = "084a92c02bd05f8bb8507cc4c4660e40";
    private static final String COMMAND = "0454d01c38ab5d66aafdaf02d9b547d6";
    private static final String GATEWAY = "b6aaff9e31925977b94369d347e7c32a";

    @ParameterizedTest
    @CsvSource({"MySQL,20", "MySQL,100", "PostgreSQL,20", "PostgreSQL,100"})
    void preservesLegacyFieldsAndLoadsPropertiesInTwoReads(String mode, int size) throws IOException {
        var fixture = new Fixture(mode);
        for (int i = 0; i < size; i++) {
            fixture.property("tenant-a", "catalog-owned", "property-" + i, "value-" + i, null);
        }
        fixture.property("tenant-a", "catalog-owned", "catalogName", "legacy name", 0);
        fixture.property("tenant-a", "catalog-owned", "dataSourceType", "ftp", 0);
        fixture.property("tenant-a", "catalog-owned", "viewCount", "999", 0);
        fixture.jdbc.update("UPDATE da_catalog_t SET market_view_count=41 WHERE tid='catalog-owned' AND tenant_id='tenant-a'");
        fixture.property("tenant-a", "catalog-owned", "deletedProperty", "deleted", 1);
        fixture.property("tenant-b", "catalog-owned", "foreignProperty", "foreign", null);

        var result = assertInstanceOf(Map.class, fixture.detail(Map.of("tid", "", "id", "catalog-owned")));
        assertEquals("catalog-owned", result.get("tid"));
        assertEquals(result.get("tid"), result.get("id"));
        assertEquals("tenant-a", result.get("tenantId"));
        assertEquals("catalog-owned", result.get("catalogName"));
        assertEquals("table-b", result.get("sourceTableId"));
        assertEquals("ftp", result.get("dataSourceType"));
        assertEquals(41L, result.get("viewCount"));
        assertEquals(result.get("assetType"), result.get("asset_type"));
        assertEquals(result.get("assetStatus"), result.get("asset_status"));
        assertEquals(result.get("flowStatus"), result.get("flow_status"));
        var properties = assertInstanceOf(Map.class, result.get("props"));
        assertEquals(size + 2, properties.size());
        assertEquals("legacy name", properties.get("catalogName"));
        assertFalse(properties.containsKey("viewCount"));
        assertFalse(result.containsKey("deletedProperty"));
        assertFalse(result.containsKey("foreignProperty"));
        assertEquals(2, fixture.reads);
    }

    @Test
    void distinguishesInventoryOwnerAndGovernedSourceOwner() throws IOException {
        var fixture = new Fixture("MySQL");
        assertStatus(404, fixture.detail(Map.of("tid", "source-owned", "mode", "directory-index-projection",
                "tenantId", "tenant-b", "scope", "ALL")));
        assertEquals(1, fixture.reads);
        var governed = assertInstanceOf(Map.class, fixture.centerDetail("source-owned"));
        assertEquals("source-owned", governed.get("tid"));
        assertEquals(5, fixture.reads);
        assertStatus(404, fixture.centerDetail("catalog-owned"));
        assertStatus(404, fixture.centerDetail("unbound"));
        assertStatus(404, fixture.centerDetail("foreign"));
        fixture.scope.policy = "ORG";
        assertEquals(governed, fixture.centerDetail("source-owned"));
        assertStatus(404, fixture.centerDetail("catalog-owned"));
        fixture.scope.policy = "ALL";
        assertInstanceOf(Map.class, fixture.centerDetail("catalog-owned"));
        assertStatus(404, fixture.centerDetail("unbound"));
        assertStatus(404, fixture.centerDetail("foreign"));

        // The post-transition index refresh is internal and still uses the current tenant.
        var projection = assertInstanceOf(Map.class,
                fixture.run(READ, Map.of("input", Map.of("mode", "directory-index-projection", "id", "source-owned"))));
        assertEquals(governed, projection);
        assertEquals(2, fixture.reads);
        assertStatus(404, fixture.run(READ,
                Map.of("input", Map.of("mode", "directory-index-projection", "id", "foreign"))));
    }

    @Test
    void enforcesTenantOrganizationAndSoftDeletionBeforePropertyReads() throws IOException {
        var fixture = new Fixture("PostgreSQL");
        fixture.scope.policy = "ORG";
        assertInstanceOf(Map.class, fixture.detail(Map.of("tid", "catalog-owned")));
        assertStatus(404, fixture.detail(Map.of("tid", "source-owned")));
        assertEquals(1, fixture.reads);
        fixture.scope.organizations = List.of();
        assertStatus(404, fixture.detail(Map.of("tid", "catalog-owned")));
        assertEquals(0, fixture.reads);
        fixture.scope.policy = "ALL";
        assertInstanceOf(Map.class, fixture.detail(Map.of("tid", "source-owned")));
        for (String id : List.of("deleted", "foreign", "missing", "catalog-owned' OR '1'='1")) {
            assertStatus(404, fixture.detail(Map.of("tid", id)));
            assertEquals(1, fixture.reads);
        }
        assertStatus(400, fixture.detail(Map.of("tid", " ")));
        assertEquals(0, fixture.reads);
        fixture.scope.tenant = null;
        assertStatus(401, fixture.detail(Map.of("tid", "catalog-owned")));
        assertEquals(0, fixture.reads);
    }

    private static void assertStatus(int status, Object result) {
        var exit = assertInstanceOf(ExitValue.class, result);
        assertEquals(status, ((Number) exit.getValues()[0]).intValue());
    }

    public static final class Scope {
        String tenant = "tenant-a";
        String policy = "OWNER";
        List<String> organizations = List.of("org-a");

        public String id() {
            return tenant;
        }

        public Map<String, Object> resource(String resourceCode) {
            assertEquals("MENU:2081000000000000002", resourceCode);
            return dataSource();
        }

        public Map<String, Object> dataSource() {
            return Map.of("scope", policy, "userId", "person-a", "organizationIds", organizations);
        }
    }

    private static final class Fixture {
        final Scope scope = new Scope();
        final JdbcTemplate jdbc;
        final SQLModule db;
        final Map<String, String> scripts = new LinkedHashMap<>();
        int reads;

        Fixture(String mode) throws IOException {
            var source = new DriverManagerDataSource(
                    "jdbc:h2:mem:catalog_" + UUID.randomUUID() + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.add(new LowerColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(new SQLInterceptor() {
                @Override
                public void preHandle(BoundSql boundSql, RequestEntity request) {
                    reads++;
                }
            }));

            jdbc.execute("""
                    CREATE TABLE da_catalog_t (
                      tid VARCHAR(64), tenant_id VARCHAR(64), created_by VARCHAR(64), updated_by VARCHAR(64),
                      org_id VARCHAR(64), is_del INT, asset_status INT DEFAULT 2, flow_status INT DEFAULT 2,
                      reg_time TIMESTAMP, created_time TIMESTAMP, updated_time TIMESTAMP,
                      catalog_name VARCHAR(255), catalog_name_en VARCHAR(255), asset_desc VARCHAR(255),
                      app_id VARCHAR(64), app_name VARCHAR(255), db_id VARCHAR(64), db_name VARCHAR(255),
                      source_table_id VARCHAR(64), source_table_name VARCHAR(255), source_db_id VARCHAR(64),
                      source_db_name VARCHAR(255), target_table_id VARCHAR(64), target_table_name VARCHAR(255),
                      target_db_id VARCHAR(64), target_db_type VARCHAR(64), manage_unit VARCHAR(64),
                      business_type VARCHAR(64), data_source_type VARCHAR(64), timestamp_field VARCHAR(64),
                      dictionary_table_flag VARCHAR(8), field_governance_config VARCHAR(255), resource_revision_no INT, market_view_count BIGINT DEFAULT 0
                    )
                    """);
            jdbc.execute("""
                    CREATE TABLE da_prop_t (
                      tenant_id VARCHAR(64), parent_id VARCHAR(64), data_type VARCHAR(64), is_del INT,
                      prop_name VARCHAR(64), prop_value VARCHAR(255)
                    )
                    """);
            jdbc.execute("CREATE TABLE db_table_t (tid VARCHAR(64), tenant_id VARCHAR(64), datasource_id VARCHAR(64), is_del INT)");
            jdbc.execute("CREATE TABLE db_datasource_t (tid VARCHAR(64), tenant_id VARCHAR(64), created_by VARCHAR(64), org_id VARCHAR(64), is_del INT)");
            jdbc.execute("CREATE TABLE dwm_center_layer_source_t (datasource_id VARCHAR(64), tenant_id VARCHAR(64), target_type VARCHAR(64), is_del INT)");
            jdbc.execute("CREATE TABLE da_catalog_item_t (tenant_id VARCHAR(64), catalog_id VARCHAR(64), is_del INT)");
            jdbc.update("INSERT INTO db_table_t VALUES ('table-a','tenant-a','source-a',0), ('table-b','tenant-a','source-b',0), ('table-c','tenant-a','source-c',0)");
            jdbc.update("INSERT INTO db_datasource_t VALUES ('source-a','tenant-a','person-a','org-a',0), ('source-b','tenant-a','person-b','org-b',0), ('source-c','tenant-a','person-a','org-a',0)");
            jdbc.update("INSERT INTO dwm_center_layer_source_t VALUES ('source-a','tenant-a','layer',0), ('source-b','tenant-a','domain',0), ('source-c','tenant-a','layer',1)");
            catalog("catalog-owned", "tenant-a", "person-a", "org-a", "table-b", 0);
            catalog("source-owned", "tenant-a", "person-b", "org-b", "table-a", 0);
            catalog("unbound", "tenant-a", "person-b", "org-b", "table-c", 0);
            catalog("foreign", "tenant-b", "person-a", "org-a", "table-a", 0);
            catalog("deleted", "tenant-a", "person-a", "org-a", "table-a", 1);

            for (String id : List.of(DETAIL, READ, COMMAND, GATEWAY)) {
                scripts.put(id, CanonicalMagicSources.byId(id)
                        .replaceAll("(?m)^import (?!java\\.|cn\\.hutool\\.|cn\\.dev33\\.)[^\\r\\n]*\\r?\\n", ""));
            }
        }

        void catalog(String id, String tenant, String owner, String org, String source, int deleted) {
            jdbc.update("""
                    INSERT INTO da_catalog_t(tid, tenant_id, created_by, org_id, source_table_id, is_del, catalog_name)
                    VALUES (?,?,?,?,?,?,?)
                    """, id, tenant, owner, org, source, deleted, id);
        }

        void property(String tenant, String id, String name, String value, Integer deleted) {
            jdbc.update("INSERT INTO da_prop_t VALUES (?,?,'catalog',?,?,?)", tenant, id, deleted, name, value);
        }

        Object detail(Map<String, Object> body) {
            return run(DETAIL, Map.of("body", body));
        }

        Object centerDetail(String id) {
            return run(GATEWAY, Map.of("body", new LinkedHashMap<>(Map.of(
                    "kind", "catalogs", "method", "GET", "action", "directory-detail", "id", id))));
        }

        Object run(String scriptId, Map<String, Object> variables) {
            reads = 0;
            return execute(scriptId, variables);
        }

        private Object execute(String scriptId, Map<String, Object> variables) {
            var context = new LinkedHashMap<>(variables);
            context.put("db", db);
            context.put("tenantRuntime", scope);
            context.put("dataScope", scope);
            context.put("tableSort", new TableSortMagicModule());
            context.put("readCatalog", function(READ));
            context.put("catalogCommand", function(COMMAND));
            return MagicScript.create(scripts.get(scriptId), null).execute(new MagicScriptContext(context));
        }

        private Function<Object[], Object> function(String scriptId) {
            return args -> {
                var result = execute(scriptId, Map.of("input", args[0]));
                if (result instanceof ExitValue exit) {
                    throw new MagicExitException(exit);
                }
                return result;
            };
        }
    }
}
