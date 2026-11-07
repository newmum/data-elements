package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class DatasourceRegistryPagingTest {
    private static final Map<String, Object> ALL = Map.of("scope", "ALL");
    private static final Map<String, Object> ORG = Map.of("scope", "ORG", "organizationIds", List.of("org-a"));
    private static final Map<String, Object> OWNER = Map.of("scope", "OWNER", "userId", "owner");

    @Test
    void readsFourTimesForTwentyOrOneHundredAndSortsByTenantAggregatesBeforePaging() throws Exception {
        var fixture = new Fixture();
        var small = fixture.page(ORG, Map.of("pageSize", 20, "sortField", "fieldCount"));
        assertEquals(4, fixture.source.reads);
        var large = fixture.page(ORG, Map.of("pageSize", 100, "sortField", "fieldCount"));
        assertEquals(4, fixture.source.reads);
        assertEquals(105, count(large, "total"));
        var rows = rows(large);
        assertEquals(100, rows.size());
        assertEquals(rows(small), rows.subList(0, 20));
        var first = rows.getFirst();
        assertEquals("source-104", first.get("id"));
        assertEquals(3, count(first, "collectedTableCount"));
        assertEquals(4, count(first, "fieldCount"));
        assertEquals(2, count(first, "registerTableNum"));
        assertEquals("实际应用 %_!'", first.get("appName"));
        assertEquals("warehouse", first.get("database"));
        assertTrue(first.get("lastCollectedAt").toString().startsWith("2026-04-05"));
        assertEquals(first.get("lastCollectedAt"), first.get("lastcollectedat"));
        assertEquals(first.get("fieldCount"), first.get("fieldcount"));
        assertEquals(first.get("fieldCount"), first.get("collectedFieldCount"));
        assertFalse(first.containsKey("poolCfg"));
        assertFalse(first.containsKey("pool_cfg"));
        assertFalse(first.containsKey("ROW_ID"));

        var summary = StructuredValues.objectMap(large.get("summary"));
        assertEquals(105, count(summary, "sourceCount"));
        assertEquals(107, count(summary, "tableCount"));
        assertEquals(108, count(summary, "fieldCount"));
        assertEquals(105, count(summary, "collectedCount"));
        assertEquals(1, count(summary, "attentionCount"));
        var groups = StructuredValues.objectRows(large.get("distributions"));
        assertEquals(1, groups.size());
        assertEquals("mysql", groups.getFirst().get("dbType"));
        assertEquals("internal", groups.getFirst().get("storageDomain"));
        assertEquals(105, count(groups.getFirst(), "sourceCount"));

        var last = fixture.page(ORG, Map.of("pageNum", 2, "pageSize", 100, "sortField", "fieldCount"));
        assertEquals(5, rows(last).size());
        assertEquals(4, fixture.source.reads);
        var empty = fixture.page(ORG, Map.of("pageNum", 3, "pageSize", 100));
        assertEquals(105, count(empty, "total"));
        assertTrue(rows(empty).isEmpty());
        assertEquals(3, fixture.source.reads);
    }

    @Test
    void mixedIdsAndFiltersStayInsideOwnerAndOrgScopeAndKeepSummaryUnfiltered() throws Exception {
        var fixture = new Fixture();
        var ids = filter("tid", "in", List.of("source-104", "other-owner", "owner-other-org", "other",
                "foreign", "deleted", "missing", "source-104"));
        var owner = fixture.page(OWNER, Map.of("conditions", List.of(ids, filter("keyword", "match", "%_!'"),
                filter("collectionState", "match", "collected"), filter("registrationState", "match", "incomplete"))));
        assertEquals(List.of("source-104"), ids(owner));
        assertEquals(1, count(owner, "total"));
        assertEquals(105, count(StructuredValues.objectMap(owner.get("summary")), "sourceCount"));
        assertEquals(4, fixture.source.reads);

        var org = fixture.page(ORG, Map.of("conditions", List.of(ids, filter("dbType", "eq", "mysql"))));
        assertEquals(List.of("other-owner", "source-104"), ids(org));
        assertEquals(2, count(org, "total"));
        var narrowed = fixture.page(ORG, Map.of("conditions", List.of(filter("orgId", "in", List.of("org-b")))));
        assertEquals(0, count(narrowed, "total"));
        assertEquals(3, fixture.source.reads);
        var noOrg = fixture.page(Map.of("scope", "ORG", "organizationIds", List.of()), Map.of());
        assertEquals(0, count(noOrg, "total"));
        assertEquals(0, count(StructuredValues.objectMap(noOrg.get("summary")), "sourceCount"));
        var absentOwner = fixture.page(Map.of("scope", "OWNER"), Map.of());
        assertEquals(0, count(absentOwner, "total"));
        assertEquals(107, count(fixture.page(ALL, Map.of()), "total"));
    }

    @Test
    void preservesFilterTypesAndBindsUntrustedText() throws Exception {
        var fixture = new Fixture();
        var values = fixture.page(ORG, Map.of("conditions", List.of(filter("dbName", "notPrefix", "Source 0"),
                filter("dbName", "like", "10_"), filter("fieldCount", "in", List.of("4", "invalid")),
                filter("createdTime", "match", "2026-04-01"))));
        assertEquals(List.of("source-104"), ids(values));
        assertEquals(0, count(fixture.page(ORG, Map.of("conditions",
                List.of(filter("keyword", "match", "' OR 1=1 --")))), "total"));
        assertEquals(0, count(fixture.page(ORG, Map.of("conditions",
                List.of(filter("assetType", "match", "app")))), "total"));
        assertThrows(RuntimeException.class, () -> fixture.page(ALL,
                Map.of("conditions", List.of(filter("db_name;drop", "match", "test")))));
        assertEquals(0, fixture.source.reads);
        assertThrows(RuntimeException.class, () -> fixture.page(ALL, Map.of("sortField", "pool_cfg")));
        assertEquals(0, fixture.source.reads);
    }

    private static Map<String, Object> filter(String field, String type, Object value) {
        return Map.of("field", field, "type", type, "value", value);
    }

    private static List<Map<String, Object>> rows(Map<String, Object> page) {
        return StructuredValues.objectRows(page.get("list"));
    }

    private static List<Object> ids(Map<String, Object> page) {
        return rows(page).stream().map(row -> row.get("tid")).toList();
    }

    private static int count(Map<String, Object> row, String name) {
        return ((Number) row.get(name)).intValue();
    }

    public static class Tenant {
        public String id() { return TenantContext.requireTenantId(); }
    }

    public record Scope(Map<String, Object> value) {
        public Map<String, Object> resource(String resourceCode) {
            assertEquals("MENU:2081000000000000002", resourceCode);
            return value;
        }
    }

    private static final class Fixture {
        private final CountedSource source = new CountedSource();
        private final StructuredTableReadMagicModule store = new StructuredTableReadMagicModule(new JdbcTemplate(source));
        private final MagicScript script;

        Fixture() throws Exception {
            script = MagicScript.create(CanonicalMagicSources.byId("datapd_database_page_01").replaceAll(
                    "(?m)^import (tenantRuntime|dataScope|tableRead|tableSort)\\s*\\R", ""), null);
            JdbcTemplate db = new JdbcTemplate(source);
            db.execute("""
                    CREATE TABLE db_datasource_t (tid VARCHAR,tenant_id VARCHAR,db_name VARCHAR,db_type VARCHAR,
                    app_id VARCHAR,org_id VARCHAR,created_by VARCHAR,asset_status INT,flow_status INT,created_time TIMESTAMP,
                    updated_time TIMESTAMP,reg_time TIMESTAMP,table_num INT,data_catalog_num INT,remark CLOB,is_enable INT,
                    connection_status VARCHAR,database_type VARCHAR,storage_domain VARCHAR,node_id VARCHAR,db_version VARCHAR,
                    pool_cfg CLOB,is_del INT)
                    """);
            db.execute("CREATE TABLE sym_application_t(tid VARCHAR,tenant_id VARCHAR,app_name VARCHAR,is_del INT)");
            db.execute("CREATE TABLE db_table_t(tid VARCHAR,tenant_id VARCHAR,datasource_id VARCHAR,asset_status INT,is_del INT)");
            db.execute("CREATE TABLE db_table_column_t(tid VARCHAR,tenant_id VARCHAR,table_id VARCHAR,is_del INT)");
            db.execute("""
                    CREATE TABLE metadata_table_collection_job_t(tid VARCHAR,tenant_id VARCHAR,datasource_id VARCHAR,
                    status VARCHAR,finished_time TIMESTAMP,is_del INT)
                    """);
            db.update("INSERT INTO sym_application_t VALUES('app','tenant-a',?,NULL),('app','tenant-b','外租户应用',0)",
                    "实际应用 %_!'");
            for (int i = 0; i < 105; i++) {
                String number = String.format("%03d", i);
                String owner = i == 0 ? "other" : "owner";
                insertSource(db, i == 0 ? "other-owner" : "source-" + number, "tenant-a", "org-a",
                        owner, i, i == 2 ? null : 0, i == 104 ? "failed" : "success");
                db.update("INSERT INTO db_table_t VALUES (?,'tenant-a',?,1,0)", "table-" + number,
                        i == 0 ? "other-owner" : "source-" + number);
                db.update("INSERT INTO db_table_column_t VALUES (?,'tenant-a',?,0)", "field-" + number, "table-" + number);
            }
            insertSource(db, "owner-other-org", "tenant-a", "org-b", "owner", 105, 0, "success");
            insertSource(db, "other", "tenant-a", "org-b", "other", 106, 0, "success");
            insertSource(db, "foreign", "tenant-b", "org-a", "owner", 107, 0, "success");
            insertSource(db, "deleted", "tenant-a", "org-a", "owner", 108, 1, "success");
            db.update("""
                    INSERT INTO db_table_t VALUES
                    ('local-a','tenant-a','source-104',0,0),('local-b','tenant-a','source-104',2,0),
                    ('deleted','tenant-a','source-104',2,1),('foreign','tenant-b','source-104',2,0)
                    """);
            db.update("""
                    INSERT INTO db_table_column_t VALUES
                    ('extra-1','tenant-a','local-a',NULL),('extra-2','tenant-a','local-b',0),
                    ('extra-3','tenant-a','table-104',0),('deleted','tenant-a','table-104',1),
                    ('foreign','tenant-b','table-104',0),('orphan','tenant-a','deleted',0)
                    """);
            db.update("""
                    INSERT INTO metadata_table_collection_job_t VALUES
                    ('old','tenant-a','source-104','SUCCEEDED','2026-04-01 00:00:00',0),
                    ('latest','tenant-a','source-104','SUCCEEDED','2026-04-05 00:00:00',0),
                    ('failed','tenant-a','source-104','FAILED','2026-04-06 00:00:00',0),
                    ('deleted','tenant-a','source-104','SUCCEEDED','2026-04-07 00:00:00',1),
                    ('foreign','tenant-b','source-104','SUCCEEDED','2026-04-08 00:00:00',0)
                    """);
        }

        private static void insertSource(JdbcTemplate db, String id, String tenant, String org, String owner,
                int sequence, Integer deleted, String status) {
            db.update("""
                    INSERT INTO db_datasource_t(tid,tenant_id,db_name,db_type,app_id,org_id,created_by,asset_status,flow_status,
                    created_time,updated_time,is_enable,connection_status,storage_domain,pool_cfg,is_del)
                    VALUES(?,?,?,'mysql','app',?,?,1,2,'2026-04-01 00:00:00','2026-04-01 00:00:00',1,?,'internal',?,?)
                    """, id, tenant, "Source " + String.format("%03d", sequence), org, owner, status,
                    "{\"schema\":\"warehouse\",\"password\":\"validation-example\"}", deleted);
        }

        Map<String, Object> page(Map<String, Object> scope, Map<String, Object> body) {
            source.reads = 0;
            var vars = Map.<String, Object>of("tenantRuntime", new Tenant(), "dataScope", new Scope(scope),
                    "tableRead", store, "tableSort", new TableSortMagicModule(), "body", body);
            return TenantContext.call("tenant-a", () -> StructuredValues.objectMap(script.execute(new MagicScriptContext(vars))));
        }
    }

    /** Execute the MySQL pagination shape on H2, counting JDBC reads from the canonical script. */
    private static final class CountedSource extends DriverManagerDataSource {
        int reads;

        CountedSource() {
            super("jdbc:h2:mem:datasource_registry_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection actual = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("getMetaData".equals(method.getName())) {
                            DatabaseMetaData metadata = actual.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (ignored, metaMethod, metaArgs) -> "getURL".equals(metaMethod.getName())
                                            ? "jdbc:mysql://datasource-registry-validation" : invoke(metadata, metaMethod, metaArgs));
                        }
                        if ("prepareStatement".equals(method.getName())
                                && ((String) args[0]).stripLeading().regionMatches(true, 0, "SELECT", 0, 6)) reads++;
                        return invoke(actual, method, args);
                    });
        }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        }
    }
}
