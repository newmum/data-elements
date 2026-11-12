package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class StandardLandingListMagicTest {
    @Test
    void twentyAndHundredFieldsKeepCoverageAndThreeReadsIndependentOfPageSize() throws Exception {
        var f = new Fixture();
        for (int i = 0; i < 100; i++) {
            String id = "column-" + String.format(Locale.ROOT, "%03d", i);
            String standard = switch (i % 5) {
                case 0 -> "published";
                case 1 -> "draft";
                case 2 -> "deleted-standard";
                case 3 -> "foreign-standard";
                default -> null;
            };
            f.column(id, "orders", "Field " + id, "", standard, null);
        }
        for (int size : List.of(20, 100)) {
            var page = f.page(Map.of("pageSize", size, "sortField", "fieldName", "sortDir", "asc"));
            assertEquals(Set.of("items", "entities", "elements", "coverage", "total", "pageNum", "pageSize"), page.keySet());
            assertEquals(100, number(page, "total"));
            assertEquals(1, number(page, "pageNum"));
            assertEquals(size, number(page, "pageSize"));
            assertEquals(size, rows(page, "items").size());
            assertEquals(List.of("CONFIRMED", "STALE", "STALE", "STALE", "UNBOUND"),
                    rows(page, "items").subList(0, 5).stream().map(row -> row.get("status")).toList());
            assertEquals(List.of("published", "draft"), rows(page, "elements").stream().map(row -> row.get("id")).toList());
            assertEquals(List.of("PUBLISHED", "PENDING"), rows(page, "elements").stream().map(row -> row.get("status")).toList());
            var entity = rows(page, "entities").getFirst();
            assertEquals("orders", entity.get("id"));
            assertEquals("Registry Alpha", entity.get("name"));
            assertEquals("Beta", entity.get("displayName"));
            assertEquals("Primary", entity.get("sourceName"));
            var firstField = StructuredValues.objectRows(entity.get("fields")).getFirst();
            assertEquals(Map.of("id", "column-000", "name", "Field column-000", "displayName", "Field column-000",
                    "type", "varchar", "length", 20, "standardId", "published"), firstField);
            assertEquals(Map.of("id", "column-000:STANDARD", "entity_id", "orders", "field_id", "column-000",
                    "mapping_mode", "STANDARD", "target_id", "published", "status", "CONFIRMED"), rows(page, "items").getFirst());
            assertCoverage(page, 100, 80, 20, "20.0");
            assertEquals(3, f.source.reads);
        }
    }

    @Test
    void nativeScopeFiltersRowsAndCoverageAndRejectsForeignRelationships() throws Exception {
        var f = new Fixture();
        f.column("primary", "orders", "Primary", null, "published", null);
        f.column("peer", "peer-table", "Peer", null, null, null);
        f.column("outside", "outside-table", "Outside", null, "draft", null);

        var all = f.page(Map.of("sortField", "fieldName", "sortDir", "asc", "tenantId", "tenant-b"));
        assertEquals(List.of("outside", "peer", "primary"), ids(all));
        assertCoverage(all, 3, 2, 1, "33.3");
        assertEquals(3, f.source.reads);

        f.scope.mode = "ORG";
        f.scope.organizationIds = List.of("org-a");
        var org = f.page(Map.of("sortField", "fieldName", "sortDir", "asc"));
        assertEquals(List.of("peer", "primary"), ids(org));
        assertCoverage(org, 2, 1, 1, "50.0");
        assertEquals(3, f.source.reads);
        f.scope.organizationIds = List.of("org-a", "org-b");
        assertEquals(3, number(f.page(Map.of()), "total"));

        f.scope.mode = "OWNER";
        var owner = f.page(Map.of("sortField", "fieldName", "sortDir", "asc", "owner", "owner-b"));
        assertEquals(List.of("outside", "primary"), ids(owner));
        assertCoverage(owner, 2, 2, 1, "50.0");
        assertEquals(3, f.source.reads);
        var hidden = f.page(Map.of("entityId", "peer-table"));
        assertEquals(List.of(), ids(hidden));
        assertEquals(0, number(hidden, "total"));
        assertCoverage(hidden, 2, 2, 1, "50.0");
        assertEquals(2, f.source.reads);

        f.scope.mode = "ORG";
        f.scope.organizationIds = List.of();
        var emptyOrg = f.page(Map.of());
        assertEquals(0, number(emptyOrg, "total"));
        assertCoverage(emptyOrg, 0, 0, 0, "0.0");
        f.scope.mode = "OWNER";
        f.scope.userId = null;
        assertRejected(f, 403, Map.of());
        f.scope.mode = "NONE";
        assertRejected(f, 403, Map.of());
    }

    @Test
    void searchMatchesAcrossColumnsAndKeepsLiteralWildcardsAndPreFilterCoverage() throws Exception {
        var f = new Fixture();
        f.column("alpha", "orders", "Field Gamma", "Delta", "published", "2026-10-24 08:00:00");
        f.column("beta", "orders", "Percent%_ marker", "", "   ", "2026-10-24 08:00:00");
        f.column("gamma", "orders", "PercentXX marker", null, null, null);
        f.column("stale", "orders", "Stale", null, "missing-standard", "2026-10-25 08:00:00");

        var search = f.page(Map.of("keyword", " ALPHA BETA ", "fieldKeyword", " gamma delta ",
                "scope", "BOUND", "entityId", "orders", "targetId", "published"));
        assertEquals(List.of("alpha"), ids(search));
        assertEquals(1, number(search, "total"));
        assertCoverage(search, 4, 2, 1, "25.0");
        assertEquals(3, f.source.reads);
        assertEquals(List.of("alpha"), ids(f.page(Map.of("keyword", "echo sigma"))));
        assertEquals(List.of("beta"), ids(f.page(Map.of("keyword", "%_"))));
        assertEquals(List.of("alpha", "beta", "gamma", "stale"), ids(f.page(Map.of("sortDir", "asc"))));

        var unbound = f.page(Map.of("scope", "UNBOUND", "sortField", "updatedTime", "sortDir", "asc"));
        assertEquals(List.of("beta", "gamma"), ids(unbound));
        assertEquals(List.of("UNBOUND", "UNBOUND"), rows(unbound, "items").stream().map(row -> row.get("status")).toList());
        assertCoverage(unbound, 4, 2, 1, "25.0");
        assertEquals(List.of("alpha", "beta", "stale", "gamma"),
                ids(f.page(Map.of("keyword", "", "fieldKeyword", "", "scope", "ALL",
                        "sortField", "updatedTime", "sortDir", "asc"))));
        assertEquals(List.of("stale", "alpha", "beta", "gamma"),
                ids(f.page(Map.of("sortField", "updatedTime", "sortDir", "desc"))));
        var noMatch = f.page(Map.of("fieldKeyword", "does not exist"));
        assertEquals(List.of(), ids(noMatch));
        assertEquals(0, number(noMatch, "total"));
        assertCoverage(noMatch, 4, 2, 1, "25.0");
        assertEquals(2, f.source.reads);
        assertRejected(f, 400, Map.of("scope", "INVALID"));
        assertRejected(f, 400, Map.of("keyword", "a".repeat(101)));
    }

    private static List<Map<String, Object>> rows(Map<String, Object> page, String field) {
        return StructuredValues.objectRows(page.get(field));
    }

    private static List<Object> ids(Map<String, Object> page) {
        return rows(page, "items").stream().map(row -> row.get("field_id")).toList();
    }

    private static long number(Map<String, Object> map, String key) {
        return assertInstanceOf(Number.class, map.get(key), key).longValue();
    }

    private static void assertCoverage(Map<String, Object> page, int total, int bound, int mapped, String percent) {
        var coverage = StructuredValues.objectMap(page.get("coverage"));
        assertEquals(Set.of("totalFields", "boundFields", "mappedFields", "coverage"), coverage.keySet());
        assertEquals(total, number(coverage, "totalFields"));
        assertEquals(bound, number(coverage, "boundFields"));
        assertEquals(mapped, number(coverage, "mappedFields"));
        assertEquals(0, new BigDecimal(percent).compareTo(new BigDecimal(coverage.get("coverage").toString())));
    }

    private static void assertRejected(Fixture fixture, int code, Map<String, Object> body) throws Exception {
        var exit = assertInstanceOf(ExitValue.class, fixture.run(body));
        assertEquals(code, exit.getValues()[0]);
        assertEquals(0, fixture.source.reads);
    }

    public static final class Scope {
        String mode = "ALL";
        String userId = "owner-a";
        List<String> organizationIds = List.of();

        public Map<String, Object> dataSource() {
            var result = new HashMap<String, Object>();
            result.put("scope", mode);
            result.put("userId", userId);
            result.put("organizationIds", organizationIds);
            return result;
        }
    }

    private static final class Fixture {
        final Scope scope = new Scope();
        final CountedSource source = new CountedSource();
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final StructuredTableReadMagicModule tableRead = new StructuredTableReadMagicModule(jdbc);

        Fixture() {
            jdbc.execute("""
                    create table db_datasource_t(tid varchar(32), tenant_id varchar(32), is_del int,
                        db_name varchar(64), created_by varchar(32), org_id varchar(32))
                    """);
            jdbc.execute("""
                    create table db_table_t(tid varchar(32), tenant_id varchar(32), is_del int,
                        datasource_id varchar(32), table_name varchar(64), table_name_cn varchar(64), table_comment clob)
                    """);
            jdbc.execute("""
                    create table da_metadata_t(tid varchar(32), tenant_id varchar(32), is_del int, publish_status int,
                        meta_name varchar(64), meta_code varchar(64), field_type varchar(64), field_length int)
                    """);
            jdbc.execute("""
                    create table db_table_column_t(tid varchar(32), tenant_id varchar(32), is_del int, table_id varchar(32),
                        column_name varchar(64), column_comment clob, data_type varchar(64), length int,
                        data_standard_id varchar(32), created_time timestamp, updated_time timestamp)
                    """);
            jdbc.execute("""
                    insert into db_datasource_t values
                        ('primary-source','tenant-a',0,'Primary','owner-a','org-a'),
                        ('peer-source','tenant-a',0,'Peer','owner-b','org-a'),
                        ('outside-source','tenant-a',0,'Outside','owner-a','org-b'),
                        ('foreign-source','tenant-b',0,'Foreign','owner-a','org-a'),
                        ('deleted-source','tenant-a',1,'Deleted','owner-a','org-a')
                    """);
            jdbc.execute("""
                    insert into db_table_t values
                        ('orders','tenant-a',0,'primary-source','Registry Alpha',null,'Beta'),
                        ('peer-table','tenant-a',0,'peer-source','Peer',null,null),
                        ('outside-table','tenant-a',0,'outside-source','Outside',null,null),
                        ('foreign-table','tenant-b',0,'primary-source','Foreign',null,null),
                        ('foreign-source-table','tenant-a',0,'foreign-source','Foreign source',null,null),
                        ('deleted-table','tenant-a',1,'primary-source','Deleted',null,null),
                        ('deleted-source-table','tenant-a',0,'deleted-source','Deleted source',null,null)
                    """);
            jdbc.execute("""
                    insert into da_metadata_t values
                        ('published','tenant-a',0,1,'Standard Echo','Sigma','varchar',20),
                        ('draft','tenant-a',0,0,'Draft','DRAFT','varchar',20),
                        ('deleted-standard','tenant-a',1,1,'Deleted','DELETED','varchar',20),
                        ('foreign-standard','tenant-b',0,1,'Foreign','FOREIGN','varchar',20),
                        ('draft','tenant-b',0,1,'Foreign draft','FOREIGN_DRAFT','varchar',20)
                    """);
            for (String table : List.of("foreign-table", "foreign-source-table", "deleted-table", "deleted-source-table", "missing-table")) {
                column(table, table, "Decoy", null, "published", null);
            }
            jdbc.execute("""
                    insert into db_table_column_t(tid,tenant_id,is_del,table_id,column_name,data_standard_id)
                    values ('foreign-column','tenant-b',0,'orders','Decoy','published'),
                        ('deleted-column','tenant-a',1,'orders','Decoy','published')
                    """);
        }

        void column(String id, String table, String name, String comment, String standard, String updated) {
            jdbc.update("""
                    insert into db_table_column_t values (?,'tenant-a',0,?,?,?,'varchar',20,?,null,?)
                    """, id, table, name, comment, standard, updated == null ? null : java.sql.Timestamp.valueOf(updated));
        }

        Map<String, Object> page(Map<String, Object> body) throws Exception {
            return StructuredValues.objectMap(run(body));
        }

        Object run(Map<String, Object> body) throws Exception {
            var code = CanonicalMagicSources.byId("dwm_standard_landing_list_01")
                    .replaceAll("(?m)^import (?:tenantRuntime|dataScope|tableSort|tableRead|db);?[ \\t]*\\r?\\n", "");
            var script = MagicScript.create(code, null);
            var vars = Map.<String, Object>of("tenantRuntime", new TenantMagicModule(null), "tableRead", tableRead,
                    "tableSort", new TableSortMagicModule(), "dataScope", scope, "body", body);
            source.reads = 0;
            return TenantContext.call("tenant-a", () -> script.execute(new MagicScriptContext(vars)));
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        int reads;

        CountedSource() {
            super("jdbc:h2:mem:standard_landing_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Override
        public Connection getConnection() throws SQLException {
            var connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getMetaData")) {
                            var metadata = connection.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (ignored, metaMethod, metaArgs) -> metaMethod.getName().equals("getURL")
                                            ? "jdbc:mysql://standard-landing-validation" : invoke(metadata, metaMethod, metaArgs));
                        }
                        if (method.getName().equals("prepareStatement")) {
                            var sql = ((String) args[0]).stripLeading().toLowerCase(Locale.ROOT);
                            if (sql.startsWith("select") || sql.startsWith("with")) reads++;
                        }
                        return invoke(connection, method, args);
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
