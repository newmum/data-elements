package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.linewell.dataelement.dataassets.runtime.MetadataAssetPersistenceModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class BatchAnnotationRegistrationTest {
    private static final String PATH = "api/02.数据盘点/02.数据库表/02.数据表管理/07.批量标注数据表.ms";
    private static final String RESOURCE_ID = "db_table_batch_annotate_01";

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void annotationsWithColumnsHaveConstantReadsAndRefreshSourceOnce(int count) throws Exception {
        var fixture = new Fixture(false);
        fixture.jdbc.update("""
                INSERT INTO db_table_t (tid, tenant_id, datasource_id, table_name, asset_status, is_del)
                SELECT 'table-' || n, 'ga', 'db', 'table-' || n, CASE WHEN n=0 THEN 0 ELSE 2 END, 0
                  FROM SYSTEM_RANGE(0,?) AS numbers(n)
                """, count - 1);
        fixture.jdbc.update("""
                INSERT INTO db_table_column_t (tid, table_id, tenant_id, column_name, is_del)
                SELECT 'column-' || n, 'table-' || n, 'ga', 'code', 0
                  FROM SYSTEM_RANGE(0,?) AS numbers(n)
                """, count - 1);

        var result = fixture.apply(rows(count));
        assertThat(((Number) result.get("affected")).intValue()).isEqualTo(count);
        assertThat(result).containsEntry("esSynced", true);
        assertThat(fixture.named.reads).isEqualTo(2);
        assertThat(fixture.structuredReads).isEqualTo(1);
        assertThat(fixture.source.reads).isEqualTo(5); // 三条脚本读取，加状态汇总的两条读取。
        assertThat(fixture.source.writes).isEqualTo(2 * count + 1); // 主表和字段更新，加一次状态批量更新。
        assertThat(fixture.scope.calls).isZero();
        assertThat(fixture.index.calls).isEqualTo(1);
        assertThat(fixture.index.ids).containsExactlyElementsOf(rows(count).stream().map(row -> (String) row.get("tid")).toList());
        assertThat(fixture.index.documents).isEqualTo(count);
        System.out.printf("Batch annotation count=%d, API=1, named reads=%d, structured reads=%d, total reads=%d, "
                        + "write statements=%d, ES calls=%d%n", count, fixture.named.reads, fixture.structuredReads,
                fixture.source.reads, fixture.source.writes, fixture.index.calls);
        assertThat(fixture.jdbc.queryForObject("SELECT asset_status FROM db_datasource_t WHERE tid='db'", Integer.class))
                .isEqualTo(1);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM db_table_column_t", Integer.class)).isEqualTo(count);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM db_table_t WHERE asset_status=0", Integer.class))
                .isEqualTo(count);
    }

    @Test
    void roleIndependentPreviewAndInvalidBatchesNeverWrite() throws Exception {
        var fixture = new Fixture(false);
        fixture.table("table-0", "db", "table-0", 0);
        fixture.jdbc.update("INSERT INTO db_datasource_t VALUES ('foreign', 'other', 2, 0, NULL, 0)");
        fixture.scope.denied = true;
        assertThat(fixture.run(rows(20), true)).isInstanceOf(Map.class);
        fixture.assertNoWrites();
        fixture.scope.denied = false;

        var mixed = new ArrayList<>(rows(20));
        mixed.add(item(null, "foreign", "foreign"));
        assertExit(403, fixture.run(mixed, false));
        fixture.assertNoWrites();
        assertExit(400, fixture.run(rows(101), false));
        fixture.assertNoWrites();
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM db_table_t WHERE asset_status=0", Integer.class))
                .isZero();
    }

    @Test
    void explicitSameSourceIdRestoresDeletedRowBeforeNameFallback() throws Exception {
        var fixture = new Fixture(false);
        fixture.table("deleted-id", "db", "orders", 1);
        fixture.table("active-id", "db", "orders", 0);

        var result = fixture.apply(List.of(item("deleted-id", "db", "orders")));
        assertThat(ids(result)).containsExactly("deleted-id");
        assertThat(fixture.index.ids).containsExactly("deleted-id");
        assertThat(fixture.jdbc.queryForObject("SELECT is_del FROM db_table_t WHERE tid='deleted-id'", Integer.class))
                .isZero();
        assertThat(fixture.jdbc.queryForObject("SELECT asset_status FROM db_table_t WHERE tid='active-id'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void explicitIdCannotCrossSourcesEvenWhenBothSourcesAreRequested() throws Exception {
        var fixture = new Fixture(false);
        fixture.jdbc.update("INSERT INTO db_datasource_t VALUES ('second-source', 'ga', 2, 0, NULL, 0)");
        fixture.table("second-id", "second-source", "orders", 0);
        fixture.table("first-id", "db", "orders", 0);
        var result = fixture.run(List.of(item("second-id", "db", "orders"), item(null, "second-source", "orders")), false);

        assertExit(403, result);
        fixture.assertNoWrites();
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM db_table_t WHERE asset_status=2", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void missingIdFallsBackToActiveNormalizedName() throws Exception {
        var fixture = new Fixture(false);
        fixture.table("active-id", "db", "Inventory_Monthly", 0);
        fixture.table("deleted-id", "db", "inventory_monthly", 1);
        var result = fixture.apply(List.of(item(null, "db", " INVENTORY_MONTHLY ")));

        assertThat(ids(result)).containsExactly("active-id");
        assertThat(fixture.index.ids).containsExactly("active-id");
        assertThat(fixture.jdbc.queryForObject("SELECT is_del FROM db_table_t WHERE tid='deleted-id'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void accentInsensitiveSqlCandidatesMatchOnlyExactMagicNamesAcrossReadWindows() throws Exception {
        var fixture = new Fixture(true);
        fixture.jdbc.update("""
                INSERT INTO db_table_t (tid, tenant_id, datasource_id, table_name, asset_status, is_del)
                SELECT 'accent-' || LPAD(n,5,'0'), 'ga', 'db', 'café', 2, 0
                  FROM SYSTEM_RANGE(1,1001) AS numbers(n)
                """);
        fixture.table("plain-id", "db", "cafe", 0);
        fixture.table("accent-resume", "db", "résumé", 0);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM db_table_t WHERE LOWER(table_name)=?",
                Integer.class, "cafe")).isEqualTo(1002);

        var result = fixture.apply(List.of(item(null, "db", "CAFE"), item(null, "db", "resume")));
        var ids = ids(result);
        assertThat(ids).hasSize(2).startsWith("plain-id").doesNotContain("accent-resume");
        assertThat(fixture.structuredReads).isGreaterThan(1);
        assertThat(fixture.index.ids).containsExactlyElementsOf(ids);
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM db_table_t WHERE asset_status=0", Integer.class))
                .isEqualTo(2);
        assertThat(fixture.jdbc.queryForObject("SELECT asset_status FROM db_table_t WHERE tid='accent-resume'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void ambiguousActiveNameRequiresUsableExplicitIdAndRejectsBeforeAnyWrite() throws Exception {
        var fixture = new Fixture(false);
        fixture.table("id-a", "db", "catalog_entries", 0);
        fixture.table("id-b", "db", "CATALOG_ENTRIES", 0);
        fixture.table("unambiguous", "db", "independent", 0);
        var result = fixture.run(List.of(item("unambiguous", "db", "independent"),
                item(null, "db", "catalog_entries")), false);

        var error = assertExit(409, result);
        assertThat(String.valueOf(error.getValues()[1])).contains("catalog_entries");
        fixture.assertNoWrites();
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM db_table_t WHERE asset_status=2", Integer.class))
                .isEqualTo(3);
        assertThat(ids(fixture.apply(List.of(item("id-b", "db", "catalog_entries"))))).containsExactly("id-b");
        assertThat(fixture.index.ids).containsExactly("id-b");
        assertThat(fixture.jdbc.queryForObject("SELECT asset_status FROM db_table_t WHERE tid='id-a'", Integer.class))
                .isEqualTo(2);
    }

    private static ExitValue assertExit(int code, Object result) {
        var exit = assertInstanceOf(ExitValue.class, result);
        assertThat(((Number) exit.getValues()[0]).intValue()).isEqualTo(code);
        return exit;
    }

    private static List<String> ids(Map<String, Object> response) {
        return StructuredValues.objectRows(response.get("rows")).stream().map(row -> (String) row.get("tid")).toList();
    }

    private static Map<String, Object> item(String id, String source, String name) {
        var props = Map.of("datasourceId", source, "tableName", name, "tableComment", "登记数据表");
        return id == null ? Map.of("propList", props) : Map.of("tid", id, "propList", props);
    }

    private static List<Map<String, Object>> rows(int count) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String tid = "table-" + index;
            rows.add(Map.of("tid", tid, "propList", Map.of("datasourceId", "db", "tableName", tid,
                    "tableComment", "业务数据表" + index,
                    "columns", List.of(Map.of("columnName", "code", "dataType", "varchar", "length", 64)))));
        }
        return rows;
    }

    private static final class Fixture {
        final CountedSource source = new CountedSource();
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final DataIngestionRegistrationScopeTest.Counter named = new DataIngestionRegistrationScopeTest.Counter();
        final DatasourceCompletionMagicTest.ScopeRuntime scope = new DatasourceCompletionMagicTest.ScopeRuntime();
        final IndexRuntime index = new IndexRuntime();
        private final SQLModule db;
        private final MetadataAssetPersistenceModule metadata = new MetadataAssetPersistenceModule(source);
        private final StructuredTableReadMagicModule tableRead;
        private final MagicScript script;
        int structuredReads;

        Fixture(boolean accentInsensitive) throws IOException {
            if (accentInsensitive) {
                jdbc.execute("SET COLLATION ENGLISH STRENGTH PRIMARY");
            }
            jdbc.execute("""
                    CREATE TABLE db_datasource_t (tid VARCHAR(64), tenant_id VARCHAR(32), asset_status INT,
                        flow_status INT, updated_time TIMESTAMP, is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE db_table_t (tid VARCHAR(64), tenant_id VARCHAR(32), datasource_id VARCHAR(64),
                        asset_status INT, is_del INT, table_name VARCHAR(255), table_name_en VARCHAR(255),
                        table_name_cn VARCHAR(512), table_comment VARCHAR(2000), table_type VARCHAR(64),
                        business_type VARCHAR(64), business_type_reason VARCHAR(2000), record_count BIGINT,
                        field_count BIGINT, storage_size BIGINT, total_size_formatted VARCHAR(64), total_size_bytes BIGINT,
                        org_id VARCHAR(64), org_path VARCHAR(512), related_directory VARCHAR(512),
                        data_source_type VARCHAR(64), source_table_id VARCHAR(64), source_table_name VARCHAR(255),
                        manage_unit VARCHAR(64), asset_desc VARCHAR(2000), field_governance_config VARCHAR(2000),
                        annotated BIGINT, flow_status BIGINT, flow_order_id VARCHAR(64), updated_time TIMESTAMP,
                        created_time TIMESTAMP, reg_time TIMESTAMP)
                    """);
            jdbc.execute("""
                    CREATE TABLE db_table_column_t (tid VARCHAR(64), table_id VARCHAR(64), tenant_id VARCHAR(32),
                        column_name VARCHAR(64), column_comment VARCHAR(200), data_type VARCHAR(100),
                        column_type VARCHAR(100), length BIGINT, precision_length BIGINT, scale BIGINT,
                        nullable INT, default_value VARCHAR(100), primary_key INT, auto_increment INT,
                        is_unique INT, indexed INT, ordinal_position INT, charset VARCHAR(64), collation VARCHAR(64),
                        extra VARCHAR(200), is_del INT, updated_time TIMESTAMP, created_time TIMESTAMP)
                    """);
            jdbc.update("INSERT INTO db_datasource_t VALUES ('db', 'ga', 2, 0, NULL, 0)");

            var dynamic = new MagicDynamicDataSource();
            dynamic.setDefault(source);
            db = new SQLModule(dynamic);
            db.setDataSourceNode(dynamic.getDataSource());
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(row -> row);
            db.setSqlInterceptors(List.of(named));
            db.setNamedTableInterceptors(List.of());
            tableRead = TaskRuntimeMagicFixture.structuredReads(jdbc, sql -> structuredReads++);
            script = MagicScript.create(CanonicalMagicSources.byPath(PATH, RESOURCE_ID)
                    .replaceAll("(?m)^import (?!cn\\.hutool\\.)[^\\r\\n]*\\R", ""), null);
        }

        void table(String id, String datasource, String name, int deleted) {
            jdbc.update("""
                    INSERT INTO db_table_t (tid, tenant_id, datasource_id, table_name, asset_status, is_del)
                    VALUES (?, 'ga', ?, ?, 2, ?)
                    """, id, datasource, name, deleted);
        }

        Map<String, Object> apply(List<Map<String, Object>> rows) {
            return StructuredValues.objectMap(run(rows, false));
        }

        Object run(List<Map<String, Object>> rows, boolean dryRun) {
            source.reads = source.writes = named.reads = structuredReads = 0;
            var bindings = Map.<String, Object>of("body", Map.of("tables", rows, "dryRun", dryRun, "refreshDatasource", false),
                    "db", db, "tableRead", tableRead, "tenantRuntime", scope, "dataScope", scope,
                    "metadataAsset", metadata, "esCommonService", index);
            try (var ignored = TenantContext.use("ga")) {
                return script.execute(new MagicScriptContext(bindings));
            }
        }

        void assertNoWrites() {
            assertThat(source.writes).isZero();
            assertThat(index.calls).isZero();
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        int reads;
        int writes;

        CountedSource() {
            super("jdbc:h2:mem:annotation_" + System.nanoTime()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Override
        public Connection getConnection() throws SQLException {
            var connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("prepareStatement")) {
                            String sql = arguments[0].toString().stripLeading().toLowerCase(Locale.ROOT);
                            if (sql.startsWith("select") || sql.startsWith("with")) reads++;
                            if (sql.startsWith("update") || sql.startsWith("insert") || sql.startsWith("delete")) writes++;
                        }
                        try {
                            return method.invoke(connection, arguments);
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                    });
        }
    }

    public static class IndexRuntime {
        int calls;
        int documents;
        List<String> ids = List.of();

        public void bulkSaveWithIds(String index, List<String> ids, List<Map<String, Object>> docs) {
            assertThat(index).isEqualTo("dataassets");
            calls++;
            this.ids = List.copyOf(ids);
            documents = docs.size();
        }
    }
}
