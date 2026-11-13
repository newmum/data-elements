package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.LowerColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class CatalogRegistryPagingTest {
    private static final Map<String, Object> ALL = Map.of("scope", "ALL");
    private static final Map<String, Object> ORG = Map.of("scope", "ORG", "organizationIds", List.of("org-a"));
    private static final Map<String, Object> OWNER = Map.of("scope", "OWNER", "userId", "owner");

    public static class Tenant {
        public String id() { return TenantContext.requireTenantId(); }
    }

    public record Scope(Map<String, Object> value) {
        public Map<String, Object> resource(String resourceCode) {
            assertEquals("MENU:2081000000000000002", resourceCode);
            return value;
        }
    }

    @Test
    void catalogPageUsesThreeReadsAtTwentyAndOneHundredAndSortsBeforePaging() throws Exception {
        var fixture = new Fixture();
        var small = fixture.page(ORG, Map.of("pageSize", 20, "sortField", "recordCount"));
        assertEquals(3, fixture.source.reads);
        var large = fixture.page(ORG, Map.of("pageSize", 100, "sortField", "recordCount"));
        assertEquals(3, fixture.source.reads);
        var rows = StructuredValues.objectRows(large.get("list"));
        assertEquals(105, ((Number) large.get("total")).intValue());
        assertEquals(100, rows.size());
        assertEquals("catalog-104", rows.getFirst().get("tid"));
        assertEquals(104, ((Number) rows.getFirst().get("recordCount")).intValue());
        assertEquals("catalog-104", rows.getFirst().get("id"));
        assertEquals("tenant-a", rows.getFirst().get("tenantId"));
        assertEquals("org-a", rows.getFirst().get("orgId"));
        assertEquals("catalog", rows.getFirst().get("assetType"));
        assertEquals("row 104", rows.getFirst().get("description"));
        assertEquals(small.get("list"), rows.subList(0, 20));

        var last = fixture.page(ORG, Map.of("pageNum", 2, "pageSize", 100, "sortField", "recordCount"));
        assertEquals(5, StructuredValues.objectRows(last.get("list")).size());
        assertEquals(3, fixture.source.reads);
        var empty = fixture.page(ORG, Map.of("pageNum", 3, "pageSize", 100));
        assertTrue(StructuredValues.objectRows(empty.get("list")).isEmpty());
        assertEquals(105, ((Number) empty.get("total")).intValue());
        assertEquals(1, fixture.source.reads);
    }

    @Test
    void boundFiltersCannotWidenScopeAndStatsFollowTheSameResult() throws Exception {
        var fixture = new Fixture();
        var ids = List.of("catalog-104", "other-owner", "foreign", "deleted", "missing");
        var conditions = List.of(filter("tid", "in", ids), filter("assetStatus", "match", "2"),
                filter("dataSourceType", "match", "ods"), filter("catalogName", "like", "%_'"));
        var page = fixture.page(OWNER, Map.of("conditions", conditions));
        assertEquals(List.of("catalog-104"), StructuredValues.objectRows(page.get("list")).stream()
                .map(row -> row.get("tid")).toList());
        assertEquals(3, fixture.source.reads);
        var row = StructuredValues.objectRows(page.get("list")).getFirst();
        assertEquals("latest", row.get("shareType"));
        assertEquals("org-a", row.get("orgId"));
        var props = StructuredValues.objectMap(row.get("props"));
        assertEquals("latest", props.get("shareType"));
        assertEquals("org-foreign", props.get("orgId"));

        var stats = fixture.page(OWNER, Map.of("statisticsOnly", true, "conditions", conditions));
        assertEquals(1, fixture.source.reads);
        assertEquals(1, ((Number) stats.get("total")).intValue());
        assertEquals(Map.of("org-a", 1), stats.get("orgCounts"));
        var orgApps = StructuredValues.objectMap(stats.get("orgApps"));
        assertEquals("登记应用", StructuredValues.objectRows(orgApps.get("org-a")).getFirst().get("label"));

        var injection = fixture.page(ALL, Map.of("keyword", "' OR 1=1 --"));
        assertEquals(0, ((Number) injection.get("total")).intValue());
        assertEquals(1, fixture.source.reads);
        var scoped = fixture.page(OWNER, Map.of("conditions", List.of(filter("orgIds", "in", List.of("org-b")))));
        assertEquals(0, ((Number) scoped.get("total")).intValue());
        assertThrows(Exception.class, () -> fixture.page(ALL, Map.of("sortField", "unknown SQL")));
    }

    private static Map<String, Object> filter(String field, String type, Object value) {
        return Map.of("field", field, "type", type, "value", value);
    }

    private static final class Fixture {
        private final CountedSource source = new CountedSource();
        private final StructuredTableReadMagicModule store = new StructuredTableReadMagicModule(new JdbcTemplate(source));
        private final SQLModule dbModule;
        private final MagicScript script;

        Fixture() throws Exception {
            String canonical = CanonicalMagicSources.byId("datapd_catalog_page_compat_01");
            script = MagicScript.create(canonical.replaceAll(
                    "(?m)^import (tenantRuntime|dataScope|tableRead|tableSort)\\s*\\R", ""), null);
            var dataSources = new MagicDynamicDataSource();
            dataSources.setDefault(source);
            dbModule = new SQLModule(dataSources);
            dbModule.setDataSourceNode(dataSources.getDataSource());
            dbModule.setDialectAdapter(new DialectAdapter());
            dbModule.setResultProvider(new DefaultResultProvider("catalog-registry-validation"));
            var columnMappers = new ColumnMapperAdapter();
            columnMappers.add(new DefaultColumnMapperProvider());
            columnMappers.add(new LowerColumnMapperProvider());
            dbModule.setColumnMapperProvider(columnMappers);
            dbModule.setColumnMapRowMapper(columnMappers.getDefaultColumnMapRowMapper());
            dbModule.setRowMapColumnMapper(value -> value);
            dbModule.setSqlInterceptors(List.of());
            dbModule.setNamedTableInterceptors(List.of());
            JdbcTemplate db = new JdbcTemplate(source);
            db.execute("""
                    CREATE TABLE da_catalog_t (tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,asset_status INT,flow_status INT,
                      reg_time TIMESTAMP,created_time TIMESTAMP,updated_time TIMESTAMP,catalog_name VARCHAR,
                      catalog_name_en VARCHAR,asset_desc VARCHAR,app_id VARCHAR,app_name VARCHAR,db_id VARCHAR,
                      db_name VARCHAR,source_table_id VARCHAR,source_table_name VARCHAR,org_id VARCHAR,manage_unit VARCHAR,
                      business_type VARCHAR,timestamp_field VARCHAR,dictionary_table_flag VARCHAR,data_source_type VARCHAR,
                      is_del INT,created_by VARCHAR)
                    """);
            db.execute("CREATE TABLE sym_application_t(tid VARCHAR,tenant_id VARCHAR,app_name VARCHAR,is_del INT)");
            db.execute("CREATE TABLE db_datasource_t(tid VARCHAR,tenant_id VARCHAR,db_name VARCHAR,db_type VARCHAR,is_del INT)");
            db.execute("""
                    CREATE TABLE db_table_t (tid VARCHAR,tenant_id VARCHAR,record_count BIGINT,field_count INT,
                      total_size_bytes BIGINT,is_del INT)
                    """);
            db.execute("""
                    CREATE TABLE da_prop_t (tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,parent_id VARCHAR,
                      data_type VARCHAR,prop_name VARCHAR,prop_value VARCHAR,updated_time TIMESTAMP,is_del INT)
                    """);
            db.update("INSERT INTO sym_application_t VALUES('app','tenant-a','登记应用',0),('app','tenant-b','外部应用',0)");
            db.update("INSERT INTO db_datasource_t VALUES('db','tenant-a','登记数据源','mysql',0)");
            for (int i = 0; i < 105; i++) {
                String number = String.format("%03d", i);
                insertCatalog(db, "catalog-" + number, "tenant-a", "org-a", "owner", 0,
                        "目录 " + number, "table-" + number);
                db.update("INSERT INTO db_table_t VALUES (?,'tenant-a',?,2,512,0)", "table-" + number, i);
                db.update("INSERT INTO da_prop_t VALUES (?,'tenant-a',?,'catalog','description',?,CURRENT_TIMESTAMP,0)",
                        "description-" + number, "catalog-" + number, "row " + i);
            }
            insertCatalog(db, "other-owner", "tenant-a", "org-b", "other", 0, "目录 %_'", "foreign-table");
            insertCatalog(db, "foreign", "tenant-b", "org-a", "owner", 0, "目录 %_'", "foreign-table");
            insertCatalog(db, "deleted", "tenant-a", "org-a", "owner", 1, "目录 %_'", "foreign-table");
            db.update("UPDATE da_catalog_t SET source_table_name='来源 %_''' WHERE tid='catalog-104'");
            db.update("""
                    INSERT INTO da_prop_t VALUES
                      ('share-1','tenant-a','catalog-104','catalog','shareType','old',NULL,0),
                      ('share-2','tenant-a','catalog-104','catalog','shareType','latest',CURRENT_TIMESTAMP,0),
                      ('share-3','tenant-a','catalog-104','catalog','shareType','deleted',CURRENT_TIMESTAMP,1),
                      ('share-4','tenant-b','catalog-104','catalog','shareType','foreign',CURRENT_TIMESTAMP,0),
                      ('org-1','tenant-a','catalog-104','catalog','orgId','org-foreign',CURRENT_TIMESTAMP,0)
                    """);
        }

        private static void insertCatalog(JdbcTemplate db, String id, String tenant, String org, String creator,
                int deleted, String name, String sourceTable) {
            db.update("""
                    INSERT INTO da_catalog_t(tid,tenant_id,asset_status,flow_status,updated_time,catalog_name,
                      app_id,db_id,source_table_id,org_id,is_del,created_by)
                    VALUES(?,?,2,2,CURRENT_TIMESTAMP,?,'app','db',?,?,?,?)
                    """, id, tenant, name, sourceTable, org, deleted, creator);
        }

        Map<String, Object> page(Map<String, Object> scope, Map<String, Object> body) {
            source.reads = 0;
            Map<String, Object> vars = new HashMap<>();
            vars.put("tenantRuntime", new Tenant());
            vars.put("dataScope", new Scope(scope));
            vars.put("tableRead", store);
            vars.put("tableSort", new TableSortMagicModule());
            vars.put("db", dbModule);
            vars.put("body", body);
            return TenantContext.call("tenant-a",
                    () -> StructuredValues.objectMap(script.execute(new MagicScriptContext(vars))));
        }
    }

    /** Executes the MySQL pagination shape on H2 and counts prepared reads at the JDBC boundary. */
    private static final class CountedSource extends DriverManagerDataSource {
        int reads;

        CountedSource() {
            super("jdbc:h2:mem:catalog_registry_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection actual = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if ("getMetaData".equals(method.getName())) {
                            DatabaseMetaData metadata = actual.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (ignored, metaMethod, metaArgs) -> "getURL".equals(metaMethod.getName())
                                            ? "jdbc:mysql://catalog-registry-validation" : invoke(metadata, metaMethod, metaArgs));
                        }
                        if ("prepareStatement".equals(method.getName())
                                && ((String) arguments[0]).stripLeading().regionMatches(true, 0, "SELECT", 0, 6)) reads++;
                        return invoke(actual, method, arguments);
                    });
        }

        private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        }
    }
}
