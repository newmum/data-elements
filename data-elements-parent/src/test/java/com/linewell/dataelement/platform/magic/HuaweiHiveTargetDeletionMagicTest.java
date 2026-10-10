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
import java.util.ArrayList;
import java.util.Collection;
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
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.dialect.MySQLDialect;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.core.context.RequestEntity;

/** Executes the released reset script with real Magic/H2 persistence and a mocked Hive connection. */
class HuaweiHiveTargetDeletionMagicTest {
    private static final String SOURCE_ID = "ods_data_agg_reset_01";
    private SQLModule db;
    private JdbcTemplate jdbc;
    private HiveModule hive;
    private MetadataExplorerService explorer;
    private Fixture fixture;
    private QueryCounter queryCounter;
    private NifiClient nifi;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:hive_reset_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        var sources = new MagicDynamicDataSource();
        sources.setDefault(source);
        db = new SQLModule(sources);
        db.setDataSourceNode(sources.getDataSource());
        var dialects = new DialectAdapter();
        dialects.add(new MySQLDialect() {
            @Override public boolean match(String productName) { return true; }
        });
        db.setDialectAdapter(dialects);
        db.setResultProvider((request, status, message, data) -> data);
        var columns = new ColumnMapperAdapter();
        columns.add(new DefaultColumnMapperProvider());
        columns.add(new CamelColumnMapperProvider());
        columns.setDefault("camel");
        db.setColumnMapperProvider(columns);
        queryCounter = new QueryCounter();
        db.setSqlInterceptors(List.of(queryCounter));
        db.setNamedTableInterceptors(List.of());
        db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
        db.setRowMapColumnMapper(value -> value);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                create table db_table_t(tid varchar(32),tenant_id varchar(32),datasource_id varchar(32),
                    table_name varchar(100),source_table_id varchar(32),source_catalog_id varchar(32),
                    catalog_name varchar(100),is_del int,updated_time timestamp)
                """);
        jdbc.execute("create table db_datasource_t(tid varchar(32),tenant_id varchar(32),db_type varchar(32),pool_cfg varchar(512),is_del int)");
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
        jdbc.execute("create table nifi_pipeline_t(id varchar(32),tenant_id varchar(32),nifi_process_group_id varchar(32),is_del int,updated_at bigint,dsl_json varchar(1000))");
        jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) values('source-1','police','source-db','person',0)");
        jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,source_table_id,is_del) values('target-1','police','target-db','ods_person','source-1',0)");
        jdbc.update("insert into db_datasource_t values('source-db','police','mysql','{}',0)");
        jdbc.update("insert into db_datasource_t values('target-db','police','hive','{\"database\":\"target_db\",\"metadataAccessMode\":\"server-managed-mrs\",\"hiveConnectionMode\":\"huawei-mrs\"}',0)");
        jdbc.update("insert into data_access_agg_task_t(tid,tenant_id,source_table_id,target_table_id,target_db_id,is_del) values('task-1','police','source-1','target-1','target-db',0)");
        jdbc.update("insert into data_access_field_mapping(tid,task_id,is_del) values('mapping-1','task-1',0)");
        jdbc.update("insert into db_table_column_t(tid,table_id,is_del) values('target-column','target-1',0),('source-column','source-1',0)");
        hive = mock(HiveModule.class);
        explorer = mock(MetadataExplorerService.class);
        fixture = new Fixture(hive);
        nifi = mock(NifiClient.class);
    }

    @Test
    void managedHiveDryRunDoesNotProbeOrDeleteAndExplicitlyRetainsPhysicalTable() throws Exception {
        Map<String, Object> body = request();
        body.put("dryRun", true);
        Map<?, ?> result = assertInstanceOf(Map.class, run(body));
        assertEquals(true, result.get("targetHivePhysicalRetained"));
        assertEquals("hive", result.get("targetDatabaseType"));
        assertEquals(false, result.get("sideEffectsApplied"));
        assertLifecycleIntact();
        verifyNoInteractions(hive, explorer, nifi);
    }

    @Test
    void historicalQualifiedHiveTargetDoesNotRequirePhysicalNameResolution() throws Exception {
        jdbc.update("update db_table_t set table_name='ODS_SIMDEV.CORP' where tid='target-1'");
        Map<String, Object> request = request();
        request.put("targetTableName", "ODS_SIMDEV.CORP");
        Map<?, ?> result = assertInstanceOf(Map.class, run(request));
        assertEquals(true, result.get("targetHivePhysicalRetained"));
        assertEquals(false, result.get("targetPhysicalDeleted"));
        assertEquals(1, deleted("db_table_t", "target-1"));
        assertEquals(0, deleted("db_table_t", "source-1"));
        verifyNoInteractions(hive, explorer);
    }

    @Test
    void hiveAuthenticationDriverAndDropFailuresCannotBlockLogicalCleanup() throws Exception {
        when(hive.showTables("target_db")).thenThrow(new SQLException("Kerberos authentication failed"));
        doThrow(new SQLException("permission denied")).when(hive).execDDLSqlInDatabase(any(), any());
        Map<?, ?> result = assertInstanceOf(Map.class, run(request()));
        assertEquals(true, result.get("targetHivePhysicalRetained"));
        assertEquals(false, result.get("targetPhysicalDeleted"));
        assertEquals(1, deleted("db_table_t", "target-1"));
        assertEquals(1, deleted("data_access_agg_task_t", "task-1"));
        assertEquals(1, deleted("data_access_field_mapping", "mapping-1"));
        assertEquals(1, deleted("db_table_column_t", "target-column"));
        assertEquals(0, deleted("db_table_t", "source-1"));
        assertEquals(0, deleted("db_table_column_t", "source-column"));
        verifyNoInteractions(hive, explorer);
    }

    @Test
    void nonHiveExecutesPhysicalDropAndVerifiesItBeforeRemovingMetadata() throws Exception {
        configureNonHive();
        var table = new com.linewell.dataelement.metautil.model.dto.TableInfo();
        table.setTableName("ods_person");
        when(explorer.getTables(any())).thenReturn(List.of(table), List.of(table), List.of());
        Map<?, ?> result = assertInstanceOf(Map.class, run(request()));
        assertEquals(true, result.get("targetPhysicalDeleted"));
        assertEquals(false, result.get("targetHivePhysicalRetained"));
        assertEquals(1, deleted("db_table_t", "target-1"));
        verify(explorer).createTable(any(), eq("DROP TABLE `ods_person`"));
        verifyNoInteractions(hive);
    }

    @Test
    void nonHivePreflightUnavailableCancelsTaskButRetainsRegistrationForRetry() throws Exception {
        configureNonHive();
        when(explorer.getTables(any())).thenThrow(new IllegalStateException("connection timed out"));
        Map<?, ?> result = assertInstanceOf(Map.class, run(request()));
        assertEquals(true, result.get("targetCleanupPending"));
        assertEquals(true, result.get("targetMetadataRetained"));
        assertEquals("unknown", result.get("targetPhysicalState"));
        assertEquals(0, deleted("db_table_t", "target-1"));
        assertEquals(0, deleted("db_table_column_t", "target-column"));
        assertEquals(1, deleted("data_access_agg_task_t", "task-1"));
        verify(explorer, never()).createTable(any(), any());
    }

    @Test
    void nonHiveDropDeniedCancelsTaskButNeverClaimsTableWasDeleted() throws Exception {
        configureNonHive();
        var table = new com.linewell.dataelement.metautil.model.dto.TableInfo();
        table.setTableName("ods_person");
        when(explorer.getTables(any())).thenReturn(List.of(table));
        doThrow(new IllegalStateException("permission denied")).when(explorer).createTable(any(), any());
        Map<?, ?> result = assertInstanceOf(Map.class, run(request()));
        assertEquals(true, result.get("targetCleanupPending"));
        assertEquals(false, result.get("targetPhysicalDeleted"));
        assertEquals(0, deleted("db_table_t", "target-1"));
        assertEquals(1, deleted("data_access_agg_task_t", "task-1"));
    }

    @Test
    void nifiFailurePreservesRetryableTaskAndRegistrationAndDoesNotDropTable() throws Exception {
        configureNonHive();
        jdbc.update("update data_access_agg_task_t set process_group_id='pg-1' where tid='task-1'");
        doThrow(new IllegalStateException("NiFi unavailable")).when(nifi).cleanupProcessGroup("pg-1");
        Map<?, ?> result = assertInstanceOf(Map.class, run(request()));
        assertEquals(1, result.get("processGroupCleanupPendingCount"));
        assertEquals(true, result.get("targetCleanupPending"));
        assertEquals(0, deleted("db_table_t", "target-1"));
        assertEquals(0, deleted("data_access_agg_task_t", "task-1"));
        verify(explorer, never()).createTable(any(), any());
    }

    @Test
    void oldTaskUsesTenantPipelineProcessGroupAndRetainsItWhenCleanupFails() throws Exception {
        jdbc.update("update data_access_agg_task_t set pipeline_id='pipeline-1' where tid='task-1'");
        jdbc.update("insert into nifi_pipeline_t(id,tenant_id,nifi_process_group_id,is_del,updated_at) values('pipeline-1','police','old-pg',0,0)");
        doThrow(new IllegalStateException("NiFi unavailable")).when(nifi).cleanupProcessGroup("old-pg");
        Map<?, ?> result = assertInstanceOf(Map.class, run(request()));
        assertEquals(1, result.get("processGroupCleanupPendingCount"));
        assertEquals(0, deleted("data_access_agg_task_t", "task-1"));
        assertEquals(0, jdbc.queryForObject("select is_del from nifi_pipeline_t where id='pipeline-1'", Integer.class));
        assertEquals(0, deleted("db_table_t", "target-1"));
        verify(nifi).cleanupProcessGroup("old-pg");
        verifyNoInteractions(hive, explorer);
    }

    @Test
    void hiveGatewayFirstColumnExtractionWorksWithActualMagicIteration() throws Exception {
        String gateway = CanonicalMagicSources.byId("mrs_hive_jdbc_debug_23");
        String helper = gateway.substring(gateway.indexOf("var valuesOf ="), gateway.indexOf("var requireConfirm ="));
        var row1 = new LinkedHashMap<String, Object>();
        row1.put("tab_name", "ods_person"); row1.put("other", "ignored");
        var rows = List.of(row1, Map.of("database_name", "target_db"), Map.of());
        Object values = MagicScript.create("import java.util.Map\n" + helper + "return valuesOf(rows)", null)
                .execute(new MagicScriptContext(Map.of("rows", rows)));
        assertEquals(List.of("ods_person", "target_db"), values);
    }

    private void configureNonHive() {
        jdbc.update("update db_datasource_t set db_type='mysql' where tid='target-db'");
        fixture.nonHive = true;
    }

    @Test
    void hiveCleanupBatchDoesNotOpenHiveConnectionEvenWhenGatewayIsUnavailable() throws Exception {
        var body = batchRequest(List.of(Map.of("targetDbId", "target-db", "targetTableName", "ods_person",
                "targetTableId", "target-1")));
        body.remove("checkPhysicalExistence");
        Map<?, ?> result = assertInstanceOf(Map.class, run(body));
        Map<?, ?> target = assertInstanceOf(Map.class, ((List<?>) result.get("results")).getFirst());
        assertEquals(true, target.get("targetHivePhysicalRetained"));
        assertEquals("retained", target.get("targetPhysicalState"));
        assertEquals(0, queryCounter.writes);
        verifyNoInteractions(hive, explorer);
    }

    @Test
    void materializationDuplicateReferenceReadsStayAtFiveForTwentyAndHundredCandidates() throws Exception {
        String materialization = CanonicalMagicSources.byId("e3320ec899a24426adf5f77e2dab36f9");
        String helpers = materialization.substring(materialization.indexOf("var text ="), materialization.indexOf("var upsertCatalogProp"));
        String duplicateCheck = materialization.substring(materialization.indexOf("var duplicateRows ="), materialization.indexOf("// 表字段名称去重"));
        for (int size : List.of(20, 100)) {
            setUp();
            for (int i = 0; i < size; i++) {
                jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) values(?,?,?,?,0)",
                        "placeholder-" + i, "police", "target-db", " ods_duplicate ");
            }
            jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) values('foreign','other','target-db','ods_duplicate',0)");
            Map<String, Object> inputs = Map.of("db", db, "tenantId", "police", "sourceTableId", "source-1",
                    "propList", Map.of("dbId", "target-db", "tableName", "ods_duplicate"),
                    "targetDatasource", Map.of("dbName", "target"));
            Object result = MagicScript.create(helpers + duplicateCheck + "return stalePlaceholderIds", null)
                    .execute(new MagicScriptContext(inputs));
            assertEquals(size, assertInstanceOf(List.class, result).size());
            assertEquals(5, queryCounter.queries);
            assertEquals(0, queryCounter.writes, "Do not remove placeholder rows before physical creation succeeds");
        }
    }

    @Test
    void materializingExistingHiveTableStopsBeforeAnyPlaceholderMetadataWrite() throws Exception {
        String materialization = CanonicalMagicSources.byId("e3320ec899a24426adf5f77e2dab36f9");
        String helpers = materialization.substring(materialization.indexOf("var text ="), materialization.indexOf("var upsertCatalogProp"));
        String boundary = materialization.substring(materialization.indexOf("var originalBody = body"),
                materialization.indexOf("// Hive 重名或物理建表失败之前"));
        String prefix = "var createTable = () => {return {created:false}}; var getCreateTableDDL = () => 'CREATE TABLE ods_person (id string)';";
        Map<String, Object> inputs = Map.of("body", Map.of(), "db", db, "reuseTargetTable", true,
                "targetDatasource", Map.of("dbType", "hive"), "submittedDdl", "CREATE TABLE ods_person (id string)",
                "propList", Map.of("dbId", "target-db", "tableName", "ods_person"), "tableItems", List.of());
        ExitValue result = assertInstanceOf(ExitValue.class, MagicScript.create(helpers + prefix + boundary, null)
                .execute(new MagicScriptContext(inputs)));
        assertEquals(0, queryCounter.writes);
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
    void ordinaryHiveCleanupDoesNotRequireRegisteredJdbcConnection() throws Exception {
        fixture.managed = false;
        Map<?, ?> result = assertInstanceOf(Map.class, run(request()));
        assertEquals(true, result.get("targetHivePhysicalRetained"));
        assertEquals(1, deleted("db_table_t", "target-1"));
        verifyNoInteractions(hive, explorer);
    }

    @Test
    void twentyBatchPreflightsUseBoundedTenantQueriesAndNoWrites() throws Exception {
        when(hive.showTables("target_db")).thenReturn(List.of());
        List<Map<String, Object>> targets = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            targets.add(Map.of("targetDbId", "target-db", "targetTableName", "ods_person_" + i,
                    "confirmUnmanagedTarget", true));
        }
        Map<?, ?> result = assertInstanceOf(Map.class, run(batchRequest(targets)));
        assertEquals(20, ((Collection<?>) result.get("results")).size());
        assertTrue(queryCounter.queries <= 6, "tenant SQL must be bounded across targets");
        assertEquals(0, queryCounter.writes);
        assertLifecycleIntact();
        verify(hive, times(1)).showTables("target_db");
    }

    @Test
    void hundredBatchTargetsAreRejectedBeforeAnyQuery() throws Exception {
        List<Map<String, Object>> targets = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            targets.add(Map.of("targetDbId", "target-db", "targetTableName", "ods_person_" + i,
                    "confirmUnmanagedTarget", true));
        }
        assertInstanceOf(ExitValue.class, run(batchRequest(targets)));
        assertEquals(0, queryCounter.queries);
        assertEquals(0, queryCounter.writes);
        verifyNoInteractions(hive, explorer);
    }

    @Test
    void batchPreflightRejectsInvisibleTargetDatasourceBeforePhysicalProbe() throws Exception {
        fixture.deniedDatasourceId = "target-db";
        var targets = List.<Map<String, Object>>of(Map.of(
                "targetDbId", "target-db", "targetTableName", "ods_person", "targetTableId", "target-1"));
        assertInstanceOf(ExitValue.class, run(batchRequest(targets)));
        assertTrue(queryCounter.queries <= 2);
        assertEquals(0, queryCounter.writes);
        verifyNoInteractions(hive, explorer);
    }

    @Test
    void unlinkedRegisteredTargetIsShownAsAConflictWithoutDeletingIt() throws Exception {
        prepareUnlinkedTargetWithAnotherActiveTask();
        when(hive.showTables("target_db")).thenReturn(List.of("ods_person"));

        var targets = List.<Map<String, Object>>of(Map.of(
                "targetDbId", "target-db", "targetTableName", "ods_person",
                "confirmUnmanagedTarget", true));
        Map<?, ?> result = assertInstanceOf(Map.class, run(batchRequest(targets)));
        Map<?, ?> target = assertInstanceOf(Map.class, ((List<?>) result.get("results")).getFirst());

        assertEquals("target-1", target.get("targetTableId"));
        assertEquals(true, target.get("targetUnlinked"));
        assertEquals(true, target.get("targetPhysicalExists"));
        assertEquals(false, target.get("sideEffectsApplied"));
        assertEquals(0, queryCounter.writes);
        assertTrue(queryCounter.queries <= 8);
        assertLifecycleIntact();
        verify(hive, never()).execDDLSqlInDatabase(any(), any());
    }

    @Test
    void unlinkedTargetDeleteRequiresPreviewedIdAndExplicitConfirmation() throws Exception {
        prepareUnlinkedTargetWithAnotherActiveTask();
        when(hive.showTables("target_db")).thenReturn(List.of("ods_person"));
        Map<String, Object> byName = request();
        byName.remove("targetTableId");
        byName.put("dryRun", true);

        Map<?, ?> preview = assertInstanceOf(Map.class, run(byName));
        assertEquals("target-1", preview.get("targetTableId"));
        assertEquals(true, preview.get("targetUnlinked"));
        byName.remove("dryRun");
        byName.put("confirmUnlinkedTarget", true);
        assertInstanceOf(ExitValue.class, run(byName));
        assertInstanceOf(ExitValue.class, run(request()));
        assertEquals(0, queryCounter.writes);
        assertLifecycleIntact();
        verify(hive, never()).execDDLSqlInDatabase(any(), any());
    }

    @Test
    void confirmedUnlinkedTargetDeleteLeavesOtherSourceTasksUntouched() throws Exception {
        prepareUnlinkedTargetWithAnotherActiveTask();
        when(hive.showTables("target_db")).thenReturn(List.of("ods_person"), List.of("ods_person"), List.of());
        Map<String, Object> request = request();
        request.put("confirmUnlinkedTarget", true);

        Map<?, ?> result = assertInstanceOf(Map.class, run(request));

        assertEquals(false, result.get("targetPhysicalDeleted"));
        assertEquals(true, result.get("targetHivePhysicalRetained"));
        assertEquals(true, result.get("targetUnlinked"));
        assertEquals(0, result.get("taskCount"));
        assertEquals(1, deleted("db_table_t", "target-1"));
        assertEquals(1, deleted("db_table_column_t", "target-column"));
        assertEquals(0, deleted("db_table_t", "source-1"));
        assertEquals(0, deleted("db_table_t", "target-2"));
        assertEquals(0, deleted("data_access_agg_task_t", "task-1"));
        assertEquals(0, deleted("data_access_field_mapping", "mapping-1"));
    }

    @Test
    void unlinkedMetadataWithAnotherSourcesTaskIsNeverOfferedForDeletion() throws Exception {
        jdbc.update("update db_table_t set source_table_id=null where tid='target-1'");
        jdbc.update("update data_access_agg_task_t set source_table_id='other-source' where tid='task-1'");
        var targets = List.<Map<String, Object>>of(Map.of(
                "targetDbId", "target-db", "targetTableName", "ods_person",
                "confirmUnmanagedTarget", true));

        assertInstanceOf(ExitValue.class, run(batchRequest(targets)));
        assertEquals(0, queryCounter.writes);
        assertLifecycleIntact();
        verifyNoInteractions(hive, explorer);
    }

    @Test
    void unlinkedMetadataUsedAsAnotherSourcesInputIsNotDeletable() throws Exception {
        prepareUnlinkedTargetWithAnotherActiveTask();
        jdbc.update("insert into data_access_agg_task_t(tid,tenant_id,source_table_id,target_table_id,target_db_id,is_del) "
                + "values('other-task','police','target-1','missing-target','target-db',0)");

        Map<String, Object> request = request();
        request.put("confirmUnlinkedTarget", true);
        assertInstanceOf(ExitValue.class, run(request));
        assertEquals(0, queryCounter.writes);
        assertLifecycleIntact();
        verifyNoInteractions(hive, explorer);
    }

    @Test
    void singleTargetDeleteRechecksRoleScopeAfterAnyEarlierDryRun() throws Exception {
        fixture.deniedDatasourceId = "target-db";
        assertInstanceOf(ExitValue.class, run(request()));
        assertLifecycleIntact();
        verifyNoInteractions(hive, explorer);
    }

    @Test
    void cancellationWithTwentyOrHundredTasksUsesTheSameReadCount() throws Exception {
        prepareCancellationCandidates(20);
        Object twentyResult = run(Map.of("sourceTableId", "source-1", "dryRun", true));
        assertEquals("target-20", assertInstanceOf(Map.class, twentyResult).get("targetTableId"));
        int twentyQueries = queryCounter.queries;
        assertEquals(0, queryCounter.writes);

        setUp();
        prepareCancellationCandidates(100);
        Object hundredResult = run(Map.of("sourceTableId", "source-1", "dryRun", true));
        assertEquals("target-100", assertInstanceOf(Map.class, hundredResult).get("targetTableId"));
        assertEquals(twentyQueries, queryCounter.queries);
        assertTrue(queryCounter.queries <= 4);
        assertEquals(0, queryCounter.writes);
    }

    private void prepareCancellationCandidates(int count) {
        jdbc.update("update data_access_agg_task_t set target_table_id='missing-1' where tid='task-1'");
        for (int i = 2; i <= count; i++) {
            jdbc.update("insert into data_access_agg_task_t(tid,tenant_id,source_table_id,target_table_id,target_db_id,is_del) values(?,?,?,?,?,0)",
                    "task-" + i, "police", "source-1", "missing-" + i, "target-db");
        }
        jdbc.update("update data_access_agg_task_t set target_table_id=? where tid=?",
                "target-" + count, "task-" + count);
        jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,source_table_id,is_del) values(?,?,?,?,?,0)",
                "target-" + count, "police", "target-db", "ods_person_" + count, "source-1");
    }

    @Test
    void independentTaskCancellationAndDeletionPreserveOtherTasks() throws Exception {
        jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,source_table_id,is_del) "
                + "values('target-2','police','target-db','ods_other','source-1',0)");
        jdbc.update("insert into data_access_agg_task_t(tid,tenant_id,source_table_id,target_table_id,target_db_id,is_del) "
                + "values('task-2','police','source-1','target-2','target-db',0)");
        Map<?,?> preview=assertInstanceOf(Map.class,run(Map.of("sourceTableId","source-1","taskId","task-2","dryRun",true)));
        assertEquals(1,preview.get("taskCount"));
        run(Map.of("sourceTableId","source-1","taskId","task-2"));
        assertEquals(0,jdbc.queryForObject("select is_del from data_access_agg_task_t where tid='task-1'",Integer.class));
        assertEquals(1,jdbc.queryForObject("select is_del from data_access_agg_task_t where tid='task-2'",Integer.class));
        assertInstanceOf(ExitValue.class,run(Map.of("sourceTableId","source-1","taskId","foreign-task","dryRun",true)));
    }

    @Test
    void activeDownstreamTaskBlocksDeletingItsSourceTableBeforePhysicalProbe() throws Exception {
        jdbc.update("insert into data_access_agg_task_t(tid,tenant_id,source_table_id,target_table_id,target_db_id,is_del) "
                + "values('task-2','police','target-1','target-2','target-db',0)");
        assertInstanceOf(ExitValue.class,run(request()));
        assertLifecycleIntact();
        verifyNoInteractions(hive,explorer);
    }

    private void prepareUnlinkedTargetWithAnotherActiveTask() {
        jdbc.update("update db_table_t set source_table_id=null where tid='target-1'");
        jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,source_table_id,is_del) "
                + "values('target-2','police','target-db','ods_other','source-1',0)");
        jdbc.update("update data_access_agg_task_t set target_table_id='target-2' where tid='task-1'");
    }

    private Object run(Map<String, Object> body) throws Exception {
        // Replace only runtime imports with test adapters; execute every validation and
        // cleanup statement from the actual released API using the project's Magic engine.
        String script = CanonicalMagicSources.byId(SOURCE_ID)
                .replaceAll("(?m)^import (?!com\\.linewell\\.dataelement\\.metautil\\.model\\.|java\\.util\\.(?:Map|List))[^\\n]*\\n", "");
        String prefix = "var datasourceConnectionConfig = (action, datasourceId, values, clearAll) => fixture.connectionConfig(datasourceId);\n"
                + "var huaweiHiveGateway = () => fixture.gateway(body);\n";
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("body", body);
        inputs.put("db", db);
        inputs.put("fixture", fixture);
        inputs.put("tenantRuntime", fixture);
        inputs.put("dataScope", fixture);
        inputs.put("jsons", fixture);
        inputs.put("poolJsons", fixture);
        inputs.put("targetTableDeletionService", new TargetTableDeletionService(explorer, hive));
        inputs.put("esCommonService", mock(EsCommonService.class));
        inputs.put("nifiClient", nifi);
        inputs.put("log", LoggerFactory.getLogger(getClass()));
        return MagicScript.create(prefix + script, null).execute(new MagicScriptContext(inputs));
    }

    private Map<String, Object> request() {
        return new LinkedHashMap<>(Map.of("sourceTableId", "source-1", "targetTableId", "target-1",
                "targetDbId", "target-db", "targetTableName", "ods_person", "deleteTargetTable", true));
    }

    private Map<String, Object> batchRequest(List<Map<String, Object>> targets) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceTableId", "source-1");
        result.put("deleteTargetTable", true);
        result.put("dryRun", true);
        result.put("batchDryRun", true);
        result.put("checkPhysicalExistence", true);
        result.put("targets", targets);
        return result;
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
        boolean nonHive;
        String deniedDatasourceId;
        private final HiveModule hive;
        Fixture(HiveModule hive) { this.hive = hive; }
        public String id() { return "police"; }
        public List<Map<String, Object>> visibleDataSourcesFor(String resource, Collection<Map<String, Object>> rows) {
            assertEquals("MENU:2081000000000000002", resource);
            return rows.stream().filter(row -> !String.valueOf(row.get("tid")).equals(deniedDatasourceId)).toList();
        }
        public Map<String, Object> parse(String raw) {
            return Map.of("database", "target_db", "metadataAccessMode", "server-managed-mrs",
                    "hiveConnectionMode", "huawei-mrs");
        }
        public Map<String, Object> connectionConfig(String datasourceId) {
            if (nonHive) return Map.of("database", "target_db", "host", "localhost", "username", "test-fixture");
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

    private static final class QueryCounter implements SQLInterceptor {
        int queries;
        int writes;
        @Override
        public void preHandle(BoundSql sql, RequestEntity request) {
            String statement = sql.getSql().stripLeading().toLowerCase();
            if (statement.startsWith("select")) { queries++; }
            else { writes++; }
        }
    }
}
