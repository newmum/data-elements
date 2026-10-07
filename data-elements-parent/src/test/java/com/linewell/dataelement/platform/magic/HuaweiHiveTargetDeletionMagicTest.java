package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.linewell.dataelement.elasticsearch.EsCommonService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.metautil.service.MetadataExplorerService;
import com.linewell.dataelement.metautil.service.TargetTableDeletionService;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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

/** Executes the released reset script with real Magic/H2 persistence and a mocked Hive connection. */
class HuaweiHiveTargetDeletionMagicTest {
    private static final String SOURCE_ID = "ods_data_agg_reset_01";
    private SQLModule db;
    private JdbcTemplate jdbc;
    private HiveModule hive;
    private MetadataExplorerService explorer;
    private Fixture fixture;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:hive_reset_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        var sources = new MagicDynamicDataSource();
        sources.setDefault(source);
        db = new SQLModule(sources);
        db.setDataSourceNode(sources.getDataSource());
        var columns = new ColumnMapperAdapter();
        columns.add(new DefaultColumnMapperProvider());
        columns.add(new CamelColumnMapperProvider());
        columns.setDefault("camel");
        db.setColumnMapperProvider(columns);
        db.setSqlInterceptors(List.of());
        db.setNamedTableInterceptors(List.of());
        db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
        db.setRowMapColumnMapper(value -> value);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                create table db_table_t(tid varchar(32),tenant_id varchar(32),datasource_id varchar(32),
                    table_name varchar(100),source_table_id varchar(32),source_catalog_id varchar(32),
                    catalog_name varchar(100),is_del int,updated_time timestamp)
                """);
        jdbc.execute("create table db_datasource_t(tid varchar(32),tenant_id varchar(32),db_type varchar(32),is_del int)");
        jdbc.execute("""
                create table data_access_agg_task_t(tid varchar(32),tenant_id varchar(32),
                    source_table_id varchar(32),target_table_id varchar(32),target_db_id varchar(32),
                    pipeline_id varchar(32),process_group_id varchar(32),task_status int,is_del int,updated_time timestamp)
                """);
        jdbc.execute("create table data_access_field_mapping(tid varchar(32),task_id varchar(32),is_del int,updated_time timestamp)");
        jdbc.execute("create table data_access_task_monitor_snap_t(tid varchar(32),task_id varchar(32),is_del int)");
        jdbc.execute("create table db_table_column_t(tid varchar(32),table_id varchar(32),is_del int,updated_time timestamp)");
        jdbc.execute("create table da_prop_t(parent_id varchar(32),prop_name varchar(100),prop_value varchar(100),is_del int,updated_time timestamp)");
        jdbc.execute("create table da_catalog_t(tid varchar(32),is_del int,updated_time timestamp)");
        jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) values('source-1','police','source-db','person',0)");
        jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,source_table_id,is_del) values('target-1','police','target-db','ods_person','source-1',0)");
        jdbc.update("insert into db_datasource_t values('target-db','police','hive',0)");
        jdbc.update("insert into data_access_agg_task_t(tid,tenant_id,source_table_id,target_table_id,target_db_id,is_del) values('task-1','police','source-1','target-1','target-db',0)");
        jdbc.update("insert into data_access_field_mapping(tid,task_id,is_del) values('mapping-1','task-1',0)");
        jdbc.update("insert into db_table_column_t(tid,table_id,is_del) values('target-column','target-1',0),('source-column','source-1',0)");
        hive = mock(HiveModule.class);
        explorer = mock(MetadataExplorerService.class);
        fixture = new Fixture(hive);
    }

    @Test
    void managedHiveDryRunDoesNotRequireJdbcFieldsAndNeverDeletes() throws Exception {
        when(hive.showTables("target_db")).thenReturn(List.of("ods_person"));
        Map<String, Object> body = request();
        body.put("dryRun", true);

        Map<?, ?> result = assertInstanceOf(Map.class, run(body));

        assertEquals(true, result.get("targetPhysicalExists"));
        assertEquals("hive", result.get("targetDatabaseType"));
        assertEquals(false, result.get("sideEffectsApplied"));
        assertLifecycleIntact();
        verify(hive, never()).execDDLSqlInDatabase(any(), any());
        verifyNoInteractions(explorer);
    }

    @Test
    void historicalQualifiedHiveTargetUsesCreatedPhysicalTableNameInsideSelectedDatabase() throws Exception {
        jdbc.update("update db_table_t set table_name='ODS_SIMDEV.CORP' where tid='target-1'");
        when(hive.showTables("target_db")).thenReturn(List.of("CORP"));
        Map<String, Object> request = request();
        request.put("targetTableName", "ODS_SIMDEV.CORP");
        request.put("dryRun", true);

        Map<?, ?> result = assertInstanceOf(Map.class, run(request));

        assertEquals("CORP", result.get("physicalTargetTableName"));
        assertEquals("target_db", result.get("targetScope"));
        assertEquals(true, result.get("historicalScopeNormalized"));
        assertEquals(true, result.get("targetPhysicalExists"));
        assertLifecycleIntact();
        verify(hive, never()).execDDLSqlInDatabase(any(), any());
    }

    @Test
    void managedHiveDeletesAndVerifiesBeforeCleaningTaskAndTargetOnly() throws Exception {
        when(hive.showTables("target_db")).thenReturn(List.of("ods_person"), List.of("ods_person"), List.of());

        Map<?, ?> result = assertInstanceOf(Map.class, run(request()));

        assertEquals(true, result.get("targetPhysicalDeleted"));
        verify(hive).execDDLSqlInDatabase("target_db", "DROP TABLE IF EXISTS `target_db`.`ods_person`");
        verifyNoInteractions(explorer);
        assertEquals(1, deleted("db_table_t", "target-1"));
        assertEquals(1, deleted("data_access_agg_task_t", "task-1"));
        assertEquals(1, deleted("data_access_field_mapping", "mapping-1"));
        assertEquals(1, deleted("db_table_column_t", "target-column"));
        assertEquals(0, deleted("db_table_t", "source-1"));
        assertEquals(0, deleted("db_table_column_t", "source-column"));
    }

    @Test
    void authenticationFailureLeavesTaskAndMetadataIntact() throws Exception {
        when(hive.showTables("target_db")).thenThrow(new SQLException("Kerberos authentication failed"));

        assertInstanceOf(ExitValue.class, run(request()));

        assertLifecycleIntact();
        verify(hive, never()).execDDLSqlInDatabase(any(), any());
    }

    @Test
    void missingHiveDatabaseDoesNotSilentlyDiscardTargetMetadata() throws Exception {
        when(hive.showTables("target_db")).thenThrow(new SQLException("database does not exist"));

        assertInstanceOf(ExitValue.class, run(request()));

        assertLifecycleIntact();
        verify(hive, never()).execDDLSqlInDatabase(any(), any());
    }

    @Test
    void dropPermissionFailureLeavesTaskAndMetadataIntact() throws Exception {
        when(hive.showTables("target_db")).thenReturn(List.of("ods_person"));
        doThrow(new SQLException("permission denied")).when(hive).execDDLSqlInDatabase(any(), any());

        assertInstanceOf(ExitValue.class, run(request()));

        assertLifecycleIntact();
    }

    @Test
    void gatewayPostDeleteCheckMustConfirmAbsenceBeforeMetadataCleanup() throws Exception {
        when(hive.showTables("target_db")).thenReturn(List.of("ods_person"));

        assertInstanceOf(ExitValue.class, run(request()));

        assertLifecycleIntact();
        verify(hive).execDDLSqlInDatabase("target_db", "DROP TABLE IF EXISTS `target_db`.`ods_person`");
    }

    @Test
    void missingPhysicalHiveTableAllowsStaleTargetCleanup() throws Exception {
        when(hive.showTables("target_db")).thenReturn(List.of());

        Map<?, ?> result = assertInstanceOf(Map.class, run(request()));

        assertEquals(false, result.get("targetPhysicalDeleted"));
        assertEquals(1, deleted("db_table_t", "target-1"));
        assertEquals(0, deleted("db_table_t", "source-1"));
        verify(hive, never()).execDDLSqlInDatabase(any(), any());
    }

    @Test
    void sourceTableAndOtherTenantRemainProtected() throws Exception {
        Map<String, Object> body = request();
        body.put("targetTableId", "source-1");
        body.put("targetDbId", "source-db");
        body.put("targetTableName", "person");
        assertInstanceOf(ExitValue.class, run(body));
        jdbc.update("update db_table_t set tenant_id='another-tenant' where tid='target-1'");
        assertInstanceOf(ExitValue.class, run(request()));
        assertLifecycleIntact();
        verifyNoInteractions(hive, explorer);
    }

    @Test
    void ordinaryHiveStillRequiresRegisteredJdbcConnection() throws Exception {
        fixture.managed = false;
        assertInstanceOf(ExitValue.class, run(request()));
        assertLifecycleIntact();
        verifyNoInteractions(hive, explorer);
    }

    private Object run(Map<String, Object> body) throws Exception {
        // Replace only runtime imports with test adapters; execute every validation and
        // cleanup statement from the actual released API using the project's Magic engine.
        String script = CanonicalMagicSources.byId(SOURCE_ID)
                .replaceAll("(?m)^import (?!com\\.linewell\\.dataelement\\.metautil\\.model\\.|java\\.util\\.Map)[^\\n]*\\n", "");
        String prefix = "var datasourceConnectionConfig = (action, datasourceId, values, clearAll) => fixture.connectionConfig(datasourceId);\n"
                + "var huaweiHiveGateway = () => fixture.gateway(body);\n";
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("body", body);
        inputs.put("db", db);
        inputs.put("fixture", fixture);
        inputs.put("tenantRuntime", fixture);
        inputs.put("targetTableDeletionService", new TargetTableDeletionService(explorer, hive));
        inputs.put("esCommonService", mock(EsCommonService.class));
        inputs.put("nifiClient", mock(NifiClient.class));
        inputs.put("log", LoggerFactory.getLogger(getClass()));
        return MagicScript.create(prefix + script, null).execute(new MagicScriptContext(inputs));
    }

    private Map<String, Object> request() {
        return new LinkedHashMap<>(Map.of("sourceTableId", "source-1", "targetTableId", "target-1",
                "targetDbId", "target-db", "targetTableName", "ods_person", "deleteTargetTable", true));
    }

    private void assertLifecycleIntact() {
        assertEquals(0, deleted("db_table_t", "source-1"));
        assertEquals(0, deleted("db_table_t", "target-1"));
        assertEquals(0, deleted("data_access_agg_task_t", "task-1"));
        assertEquals(0, deleted("data_access_field_mapping", "mapping-1"));
        assertEquals(0, deleted("db_table_column_t", "target-column"));
    }

    private int deleted(String table, String id) {
        return jdbc.queryForObject("select is_del from " + table + " where tid=?", Integer.class, id);
    }

    public static class Fixture {
        boolean managed = true;
        private final HiveModule hive;
        Fixture(HiveModule hive) { this.hive = hive; }
        public String id() { return "police"; }
        public Map<String, Object> connectionConfig(String datasourceId) {
            return Map.of("database", "target_db", "hiveProfile", "default",
                    "hiveConnectionMode", managed ? "huawei-mrs" : "open-source",
                    "metadataAccessMode", managed ? "server-managed-mrs" : "jdbc");
        }
        public Map<String, Object> gateway(Map<String, Object> request) throws Exception {
            String action = String.valueOf(request.get("action"));
            String database = String.valueOf(request.get("database"));
            if ("tables".equals(action)) {
                return Map.of("success", true, "tables", hive.showTables(database));
            }
            if ("dropTable".equals(action)) {
                String table = String.valueOf(request.get("table"));
                String expected = "DROP_TABLE:" + database + "." + table;
                if (!expected.equals(request.get("confirm"))) {
                    throw new IllegalArgumentException("missing explicit Hive drop confirmation");
                }
                hive.execDDLSqlInDatabase(database,
                        "DROP TABLE IF EXISTS `" + database + "`.`" + table + "`");
                return Map.of("success", true);
            }
            throw new IllegalArgumentException("unexpected gateway action: " + action);
        }
    }
}
