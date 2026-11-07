package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class TableSnapshotListMagicTest {
    private static final List<String> GOVERNANCE_COLUMNS = List.of(
            "field_governance_config", "dictionary_profiles", "dictionary_profile", "dictionary_categories");
    private static final List<String> GOVERNANCE_KEYS = List.of(
            "fieldGovernanceConfig", "dictionaryProfiles", "dictionaryProfile", "dictionaryCategories");

    @Test
    void twentyAndHundredSnapshotsHaveFixedReadsAndStatsBeforeListFilters() throws Exception {
        for (String dialect : List.of("MySQL", "PostgreSQL")) {
            for (int count : List.of(20, 100)) {
                var f = new Fixture(dialect);
                for (int i = 0; i < count; i++) {
                    f.table("row-" + i, "table_" + i, "表" + i, i % 2 == 0 ? "业务表" : "字典表",
                            i % 2, i, "2026-10-24");
                }
                f.table("preferred", "TABLE_0", "已标注名称", "业务表", 1, 999, "2026-10-24");

                var all = f.page(count, Map.of("allSnapshots", true));
                assertEquals(count, number(all, "total"));
                assertStats(all, count, count / 2 + 1, count / 2, count / 2);
                var rows = StructuredValues.objectRows(all.get("rows"));
                assertEquals(rows, all.get("list"));
                assertEquals(count, rows.size());
                assertTrue(rows.stream().anyMatch(row -> number(row, "annotated") == 0));
                assertTrue(rows.stream().anyMatch(row -> number(row, "annotated") == 1));
                assertTrue(rows.stream().allMatch(row -> "police".equals(row.get("tenantId"))));
                var winner = rows.stream().filter(row -> "TABLE_0".equals(row.get("tableName"))).findFirst().orElseThrow();
                assertEquals("preferred", winner.get("tid"));
                assertEquals("已标注名称", winner.get("registeredResourceName"));
                assertTrue(rows.stream().noneMatch(row -> GOVERNANCE_KEYS.stream().anyMatch(row::containsKey)));
                assertEquals(4, f.source.reads.size());
                assertTrue(f.source.reads.stream().noneMatch(sql -> GOVERNANCE_COLUMNS.stream().anyMatch(sql::contains)));

                var marked = f.page(count, Map.of());
                assertEquals(count / 2 + 1, number(marked, "total"));
                assertStats(marked, count / 2 + 1, count / 2 + 1, 1, count / 2);
                assertEquals(4, f.source.reads.size());

                var filtered = f.page(count, Map.of("allSnapshots", true, "businessType", "字典表", "keyword", " TABLE_1 "));
                var filteredRows = StructuredValues.objectRows(filtered.get("rows"));
                assertEquals(6, number(filtered, "total"));
                assertTrue(filteredRows.stream().allMatch(row -> "字典表".equals(row.get("businessType"))));
                assertStats(filtered, count, count / 2 + 1, count / 2, count / 2);
                assertEquals(4, f.source.reads.size());
            }
        }
    }

    @Test
    void ranksSameNameBeforeKeywordFilterAndReadsGovernanceOnlyWhenRequested() throws Exception {
        var f = new Fixture("MySQL");
        f.table("unmarked", "same", "loser only", "字典表", 0, 999, "2026-10-25");
        f.table("lower-count", "same", "lower count", "字典表", 1, 9, "2026-10-25");
        f.table("older", "same", "older", "字典表", 1, 10, "2026-10-23");
        f.table("tie-a", "same", "tie loser", "字典表", 1, 10, "2026-10-24");
        f.table("tie-z", "SAME", "winner", "业务表", 1, 10, "2026-10-24");
        f.table("uncategorized", "other", "other", null, null, 1, null);
        f.jdbc.update("""
                update db_table_t set field_governance_config=?, dictionary_profiles=?,
                    dictionary_profile=?, dictionary_categories=? where tid='tie-z'
                """, "{\"fields\":[]}", "[]", "{}", "[\"test\"]");

        var page = f.page(20, Map.of("allSnapshots", true, "includeGovernance", true, "keyword", "WINNER"));
        assertEquals(List.of("tie-z"), ids(page));
        assertStats(page, 2, 1, 2, 0);
        var row = StructuredValues.objectRows(page.get("rows")).getFirst();
        assertEquals(List.of("{\"fields\":[]}", "[]", "{}", "[\"test\"]"),
                GOVERNANCE_KEYS.stream().map(row::get).toList());
        assertEquals(4, f.source.reads.size());
        assertTrue(GOVERNANCE_COLUMNS.stream().allMatch(f.source.reads.getLast()::contains));
        var statsSql = f.source.reads.stream().filter(sql -> sql.contains("group by")).findFirst().orElseThrow();
        assertTrue(GOVERNANCE_COLUMNS.stream().noneMatch(statsSql::contains));

        var noMatches = f.page(20, Map.of("allSnapshots", true, "keyword", "loser only"));
        assertEquals(0, number(noMatches, "total"));
        assertEquals(List.of(), ids(noMatches));
        assertStats(noMatches, 2, 1, 2, 0);
        assertEquals(3, f.source.reads.size());
    }

    @Test
    void orderingFallsBackToLatestAndUsesNullLastAndDescendingIdTies() throws Exception {
        for (String dialect : List.of("MySQL", "PostgreSQL")) {
            var f = new Fixture(dialect);
            f.table("sort-a", "a", "first", "业务表", 1, 2, "2026-10-24");
            f.table("sort-z", "z", "tie", "业务表", 1, 2, "2026-10-24");
            f.table("sort-new", "latest", "latest", "业务表", 1, 10, "2026-10-25");
            f.table("sort-null", "unset", null, "业务表", 1, null, null);
            assertEquals(List.of("sort-z", "sort-a", "sort-new", "sort-null"),
                    ids(f.page(20, Map.of("sortField", "fieldCount"))));
            var latest = List.of("sort-new", "sort-z", "sort-a", "sort-null");
            assertEquals(latest, ids(f.page(20, Map.of("sortField", "fieldCount", "sortDir", "DESC"))));
            assertEquals(latest, ids(f.page(20, Map.of("sortField", "fieldCount; drop table db_table_t", "sortDir", "asc"))));
            assertTrue(f.source.reads.stream().noneMatch(sql -> sql.contains("drop table")));
            assertEquals(List.of("sort-a"), ids(f.page(1, Map.of("sortField", "fieldCount", "pageNo", 2))));
        }
    }

    @Test
    void rejectsForeignDeletedOrMissingSourcesBeforeReadingSnapshots() throws Exception {
        var f = new Fixture("MySQL");
        for (String sourceId : List.of("foreign-source", "deleted-source", "unknown-source")) {
            assertInstanceOf(ExitValue.class, f.read(100, Map.of("dbId", sourceId)));
            assertEquals(1, f.source.reads.size());
        }
        f.scope.tenant = "unknown-tenant";
        assertInstanceOf(ExitValue.class, f.read(100, Map.of()));
        assertEquals(1, f.source.reads.size());
        f.scope.tenant = "police";
        f.scope.denied = true;
        f.table("current-table", "current", "有效表", "业务表", 1, 1, "2026-10-24");
        assertEquals(List.of("current-table"), ids(f.page(100, Map.of())));
        assertEquals(4, f.source.reads.size());
    }

    private static long number(Map<String, Object> row, String key) {
        return assertInstanceOf(Number.class, row.get(key), key).longValue();
    }

    private static List<Object> ids(Map<String, Object> page) {
        return StructuredValues.objectRows(page.get("rows")).stream().map(row -> row.get("tid")).toList();
    }

    private static void assertStats(Map<String, Object> page, int snapshots, int annotated, int business, int dictionary) {
        assertEquals(snapshots, number(page, "snapshotTotal"));
        assertEquals(annotated, number(page, "annotatedTotal"));
        var stats = StructuredValues.objectMap(page.get("businessStats"));
        assertEquals(business, number(stats, "业务表"));
        assertEquals(dictionary, ((Number) stats.getOrDefault("字典表", 0)).longValue());
    }

    public static class Scope {
        String tenant = "police";
        boolean denied;
        public String id() { return tenant; }
        public List<?> visibleDataSourcesFor(String resource, List<?> rows) { return denied ? List.of() : rows; }
    }

    private static class Fixture {
        final CountedSource source;
        final JdbcTemplate jdbc;
        final SQLModule db;
        final Scope scope = new Scope();

        Fixture(String dialect) {
            source = new CountedSource(dialect);
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    create table db_datasource_t(tid varchar(64), tenant_id varchar(64), db_name varchar(64),
                        app_id varchar(64), org_id varchar(64), is_del int)
                    """);
            jdbc.execute("""
                    insert into db_datasource_t values ('source','police','db','app','org',0),
                        ('foreign-source','other','secret','app','org',0), ('deleted-source','police','deleted','app','org',1)
                    """);
            jdbc.execute("""
                    create table db_table_t(tid varchar(64), datasource_id varchar(64), table_name varchar(255),
                        table_name_en varchar(255), table_name_cn varchar(255), table_comment varchar(500), table_type varchar(64),
                        business_type varchar(64), business_type_reason varchar(255), record_count bigint, storage_size varchar(64),
                        total_size_bytes bigint, total_size_formatted varchar(64), org_id varchar(64), org_path varchar(500),
                        related_directory varchar(64), data_source_type varchar(64), timestamp_field varchar(64),
                        dictionary_structure_type varchar(64), dictionary_table_flag int, source_catalog_id varchar(64),
                        field_governance_config varchar(500), dictionary_profiles varchar(500), dictionary_profile varchar(500),
                        dictionary_categories varchar(500), source_table_id varchar(64), source_table_name varchar(255),
                        manage_unit varchar(64), asset_status int, flow_status int, reg_time timestamp, created_time timestamp,
                        updated_time timestamp, tenant_id varchar(64), field_count int, annotated int, is_del int)
                    """);
            jdbc.execute("""
                    insert into db_table_t(tid, tenant_id, datasource_id, table_name, business_type, annotated, field_count, is_del)
                    values ('foreign-table','other','source','table_0','日志表',1,1000,0),
                        ('deleted-table','police','source','table_0','日志表',1,1000,1),
                        ('wrong-source-table','police','different','table_0','日志表',1,1000,0),
                        ('deleted-source-table','police','deleted-source','deleted-source-table','业务表',1,1000,0)
                    """);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setResultProvider(new DefaultResultProvider("snapshot-list-test"));
            db.setDialectAdapter(new DialectAdapter());
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of());
        }

        void table(String id, String name, String comment, String type, Integer annotated, Integer fields, String updated) {
            jdbc.update("""
                    insert into db_table_t(tid, tenant_id, datasource_id, table_name, table_comment, business_type,
                        annotated, field_count, updated_time, is_del) values (?,'police','source',?,?,?,?,?,?,0)
                    """, id, name, comment, type, annotated, fields,
                    updated == null ? null : java.sql.Timestamp.valueOf(updated + " 00:00:00"));
        }

        Map<String, Object> page(int size, Map<String, Object> options) throws Exception {
            return StructuredValues.objectMap(assertInstanceOf(Map.class, read(size, options)));
        }

        Object read(int size, Map<String, Object> options) throws Exception {
            var script = MagicScript.create(CanonicalMagicSources.byId("52e398a9f2fc429da79048b9ea719b37")
                    .replaceAll("(?m)^import (?:tenantRuntime|jsons|tableRead)[ \\t]*\\r?\\n", ""), null);
            var vars = new HashMap<String, Object>();
            vars.put("db", db);
            vars.put("tableRead", new StructuredTableReadMagicModule(jdbc));
            vars.put("tenantRuntime", scope);
            vars.put("dataScope", scope);
            vars.put("dbId", "source");
            vars.put("paged", true);
            vars.put("pageNo", 1);
            vars.put("pageSize", size);
            vars.put("allSnapshots", false);
            vars.put("includeGovernance", false);
            vars.putAll(options);
            source.reads.clear();
            return TenantContext.call(scope.tenant, () -> script.execute(new MagicScriptContext(vars)));
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        final List<String> reads = new ArrayList<>();
        private final String dialectUrl;

        CountedSource(String dialect) {
            super("jdbc:h2:mem:snapshot_" + UUID.randomUUID() + ";MODE=" + dialect
                    + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            dialectUrl = "jdbc:" + dialect.toLowerCase(Locale.ROOT) + "://snapshot-validation";
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
                                            ? dialectUrl : invoke(metadata, metaMethod, metaArgs));
                        }
                        if (method.getName().equals("prepareStatement")) {
                            String sql = ((String) args[0]).stripLeading().toLowerCase(Locale.ROOT);
                            if (sql.startsWith("select") || sql.startsWith("with")) reads.add(sql);
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
