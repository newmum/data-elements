package com.linewell.dataelement.metautil.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TargetTableDeletionServiceTest {

    @Test
    void quotedUnicodeTablesUseTheSelectedSchemaAndKeepRegisteredJdbcPropertiesAcrossFamilies() {
        for (var type : List.of(DatabaseType.MYSQL,DatabaseType.MARIADB,DatabaseType.OCEANBASE_MYSQL,
                DatabaseType.ORACLE,DatabaseType.OCEANBASE_ORACLE,DatabaseType.DAMENG,
                DatabaseType.POSTGRESQL,DatabaseType.KINGBASE,DatabaseType.GAUSSDB,DatabaseType.HIGHGO,
                DatabaseType.SQLSERVER,DatabaseType.DB2,DatabaseType.CLICKHOUSE,DatabaseType.HETU)) {
            var config = new DataSourceConfig(); config.setDatabaseType(type); config.setDatabase("catalog");
            config.setSchema("ods"); config.setJdbcUrl("jdbc:test:catalog");
            config.setJdbcProperties(Map.of("customProperty","registered-value"));
            var table = new TableInfo(); table.setTableName("人员.明细"); table.setSchemaName("ods");
            var explorer = mock(MetadataExplorerService.class);
            when(explorer.getTableDirectory(any())).thenReturn(List.of(table),List.of());
            var result = new TargetTableDeletionService(explorer,mock(HiveModule.class)).drop(config,"\"人员.明细\"");
            var copied = ArgumentCaptor.forClass(DataSourceConfig.class);
            verify(explorer).createTable(copied.capture(),org.mockito.ArgumentMatchers.eq("DROP TABLE "
                    + com.linewell.dataelement.metautil.ddl.DdlDialect.quote(type,"ods")+"."
                    + com.linewell.dataelement.metautil.ddl.DdlDialect.quote(type,"人员.明细")));
            assertEquals(config.getJdbcProperties(),copied.getValue().getJdbcProperties());
            assertEquals(true,result.get("physicalDeleted"));
        }
    }

    @Test
    void dropExecutesEvenWhenTheDirectoryDidNotShowATableAndRetryIsIdempotent() {
        var datasource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:h2:mem:portable_drop_"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(datasource);
        jdbc.execute("CREATE SCHEMA \"ods\"");
        jdbc.execute("CREATE TABLE \"ods\".\"人员.明细\"(id INT)");
        var explorer = mock(MetadataExplorerService.class);
        // A restricted directory can omit an existing object. Actual DROP still runs.
        when(explorer.getTableDirectory(any())).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(call -> { jdbc.execute(call.getArgument(1,String.class)); return null; })
                .when(explorer).createTable(any(),any());
        var config = new DataSourceConfig(); config.setDatabaseType(DatabaseType.POSTGRESQL);
        config.setSchema("ods"); config.setJdbcUrl("jdbc:test:ods");
        var service = new TargetTableDeletionService(explorer,mock(HiveModule.class));
        assertEquals(true,service.drop(config,"\"人员.明细\"").get("physicalDeleted"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='ods'",Integer.class));
        assertEquals(false,service.drop(config,"\"人员.明细\"").get("physicalDeleted"));
        verify(explorer,times(2)).createTable(any(),any());
    }

    @Test
    void twentyOracleTargetsShareOnePhysicalTableDirectoryLookup() {
        MetadataExplorerService explorer = mock(MetadataExplorerService.class);
        List<TableInfo> physical = IntStream.range(0, 20).mapToObj(index -> {
            TableInfo table = new TableInfo();
            table.setTableName("ODS_PERSON_" + index);
            table.setSchemaName("FJHYJ");
            return table;
        }).toList();
        when(explorer.getTableDirectory(any())).thenReturn(physical);
        List<String> names = IntStream.range(0, 20)
                .mapToObj(index -> index == 0 ? "OLD.ODS_PERSON_0" : "ODS_PERSON_" + index).toList();

        List<Map<String, Object>> result = new TargetTableDeletionService(explorer, mock(HiveModule.class))
                .preflightBatch(oceanBaseOracle(), names);

        assertEquals(20, result.size());
        assertTrue(result.stream().allMatch(row -> Boolean.TRUE.equals(row.get("physicalExists"))));
        assertEquals(true, result.getFirst().get("historicalScopeNormalized"));
        assertEquals("ODS_PERSON_0", result.getFirst().get("physicalTargetTableName"));
        verify(explorer, times(1)).getTableDirectory(any());
    }

    @Test
    void twentyManagedHiveTargetsShareOneServerSideShowTables() throws Exception {
        MetadataExplorerService explorer = mock(MetadataExplorerService.class);
        HiveModule hive = mock(HiveModule.class);
        when(hive.showTables("target_db")).thenReturn(IntStream.range(0, 20)
                .mapToObj(index -> "ods_person_" + index).toList());
        List<String> names = IntStream.range(0, 20)
                .mapToObj(index -> "ods_person_" + index).toList();

        List<Map<String, Object>> result = new TargetTableDeletionService(explorer, hive)
                .preflightBatch(managedHive(), names);

        assertEquals(20, result.size());
        assertTrue(result.stream().allMatch(row -> Boolean.TRUE.equals(row.get("physicalExists"))));
        verify(hive, times(1)).showTables("target_db");
        verifyNoInteractions(explorer);
    }

    @Test
    void hundredTargetsAreRejectedBeforeOpeningPhysicalDatasource() {
        MetadataExplorerService explorer = mock(MetadataExplorerService.class);
        HiveModule hive = mock(HiveModule.class);
        List<String> names = IntStream.range(0, 100)
                .mapToObj(index -> "ods_person_" + index).toList();

        assertThrows(IllegalArgumentException.class,
                () -> new TargetTableDeletionService(explorer, hive).preflightBatch(managedHive(), names));

        verifyNoInteractions(hive, explorer);
    }

    @Test
    void dropsOceanBaseOracleTableWithOwnerScopeAndVerifiesItIsGone() {
        MetadataExplorerService explorer = mock(MetadataExplorerService.class);
        TableInfo physical = new TableInfo();
        physical.setTableName("ODS_PERSON");
        physical.setSchemaName("FJHYJ");
        when(explorer.getTableDirectory(any())).thenReturn(List.of(physical), List.of());
        TargetTableDeletionService service = new TargetTableDeletionService(explorer, mock(HiveModule.class));

        Map<String, Object> result = service.drop(oceanBaseOracle(), "FJHYJ.ods_person");

        ArgumentCaptor<DataSourceConfig> config = ArgumentCaptor.forClass(DataSourceConfig.class);
        ArgumentCaptor<String> ddl = ArgumentCaptor.forClass(String.class);
        verify(explorer).createTable(config.capture(), ddl.capture());
        assertEquals(DatabaseType.OCEANBASE_ORACLE, config.getValue().getDatabaseType());
        assertEquals("FJHYJ", config.getValue().getSchema());
        assertEquals("DROP TABLE \"FJHYJ\".\"ODS_PERSON\"", ddl.getValue());
        assertTrue((Boolean) result.get("physicalExists"));
        assertTrue((Boolean) result.get("physicalDeleted"));
        verify(explorer, times(2)).getTableDirectory(any());
    }

    @Test
    void rejectsOceanBaseOracleTableFromAnotherSchemaBeforeDdl() {
        MetadataExplorerService explorer = mock(MetadataExplorerService.class);
        TargetTableDeletionService service = new TargetTableDeletionService(explorer, mock(HiveModule.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.preflight(oceanBaseOracle(), "OTHER.ODS_PERSON"));

        assertTrue(error.getMessage().contains("不一致"));
        verify(explorer, times(0)).getTableDirectory(any());
        verify(explorer, times(0)).createTable(any(), any());
    }

    @Test
    void keepsLifecycleUntouchedWhenDropFails() {
        MetadataExplorerService explorer = mock(MetadataExplorerService.class);
        TableInfo physical = new TableInfo();
        physical.setTableName("ODS_PERSON");
        physical.setSchemaName("FJHYJ");
        when(explorer.getTableDirectory(any())).thenReturn(List.of(physical));
        org.mockito.Mockito.doThrow(new RuntimeException("permission denied"))
                .when(explorer).createTable(any(), any());
        TargetTableDeletionService service = new TargetTableDeletionService(explorer, mock(HiveModule.class));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.drop(oceanBaseOracle(), "ODS_PERSON"));

        assertTrue(error.getMessage().contains("保留接入流程和元数据"));
        verify(explorer, times(1)).getTableDirectory(any());
    }

    @Test
    void reportsMissingPhysicalTableAsSafeNoop() {
        MetadataExplorerService explorer = mock(MetadataExplorerService.class);
        when(explorer.getTableDirectory(any())).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new RuntimeException("ORA-00942: table or view does not exist"))
                .when(explorer).createTable(any(), any());
        TargetTableDeletionService service = new TargetTableDeletionService(explorer, mock(HiveModule.class));

        Map<String, Object> result = service.drop(oceanBaseOracle(), "ODS_PERSON");

        assertFalse((Boolean) result.get("physicalExists"));
        assertFalse((Boolean) result.get("physicalDeleted"));
        verify(explorer, times(1)).createTable(any(), any());
    }

    @Test
    void treatsDamengMissingObjectDuringDropAsSafeNoop() {
        MetadataExplorerService explorer = mock(MetadataExplorerService.class);
        TableInfo physical = new TableInfo();
        physical.setTableName("ODS_PERSON");
        physical.setSchemaName("FJHYJ");
        when(explorer.getTableDirectory(any())).thenReturn(List.of(physical), List.of());
        org.mockito.Mockito.doThrow(new RuntimeException("无效的表或视图名"))
                .when(explorer).createTable(any(), any());
        TargetTableDeletionService service = new TargetTableDeletionService(explorer, mock(HiveModule.class));

        Map<String, Object> result = service.drop(dameng(), "ODS_PERSON");

        assertFalse((Boolean) result.get("physicalExists"));
        assertFalse((Boolean) result.get("physicalDeleted"));
        verify(explorer).createTable(any(), org.mockito.ArgumentMatchers.eq("DROP TABLE \"FJHYJ\".\"ODS_PERSON\""));
    }

    @Test
    void hiveDeletionAlwaysRetainsPhysicalDataWithoutJdbcCredentials() {
        MetadataExplorerService explorer=mock(MetadataExplorerService.class);
        HiveModule hive=mock(HiveModule.class);
        var result=new TargetTableDeletionService(explorer,hive).drop(managedHive(),"target_db.ods_person");
        assertEquals(true,result.get("physicalRetained")); assertEquals(false,result.get("physicalDeleted"));
        verifyNoInteractions(explorer,hive);
    }

    @Test
    void hiveRetentionDoesNotDependOnConnectivity() {
        MetadataExplorerService explorer=mock(MetadataExplorerService.class);
        HiveModule hive=mock(HiveModule.class);
        var result=new TargetTableDeletionService(explorer,hive).drop(managedHive(),"target_db.ods_person");
        assertEquals(true,result.get("physicalRetained")); assertEquals(false,result.get("physicalDeleted"));
        verifyNoInteractions(explorer,hive);
    }

    @Test
    void reportsAlreadyMissingHuaweiHiveTableAsSafeNoop() throws Exception {
        MetadataExplorerService explorer = mock(MetadataExplorerService.class);
        HiveModule hive = mock(HiveModule.class);
        when(hive.showTables("target_db")).thenReturn(List.of());

        Map<String, Object> result = new TargetTableDeletionService(explorer, hive)
                .drop(managedHive(), "ods_person");

        assertFalse((Boolean) result.get("physicalDeleted"));
        verify(hive, times(0)).execDDLSqlInDatabase(any(), any());
        verifyNoInteractions(explorer);
    }

    @Test
    void hiveRetentionDoesNotRunAnyPostDeleteProbe() {
        MetadataExplorerService explorer=mock(MetadataExplorerService.class);
        HiveModule hive=mock(HiveModule.class);
        var result=new TargetTableDeletionService(explorer,hive).drop(managedHive(),"target_db.ods_person");
        assertEquals(true,result.get("physicalRetained")); assertEquals(false,result.get("physicalDeleted"));
        verifyNoInteractions(explorer,hive);
    }

    @Test
    void refusesHuaweiHiveTableFromAnotherDatabaseBeforeOpeningConnection() {
        MetadataExplorerService explorer = mock(MetadataExplorerService.class);
        HiveModule hive = mock(HiveModule.class);

        assertThrows(IllegalArgumentException.class,
                () -> new TargetTableDeletionService(explorer, hive).preflight(managedHive(), "other_db.ods_person"));

        verifyNoInteractions(hive, explorer);
    }

    @Test
    void validatesHuaweiHiveDatabaseAndProfileBeforeOpeningConnection() {
        MetadataExplorerService explorer = mock(MetadataExplorerService.class);
        HiveModule hive = mock(HiveModule.class);
        TargetTableDeletionService service = new TargetTableDeletionService(explorer, hive);
        DataSourceConfig unsafe = managedHive();
        unsafe.setDatabase("target_db;DROP DATABASE target_db");
        assertThrows(IllegalArgumentException.class, () -> service.preflight(unsafe, "ods_person"));
        DataSourceConfig unknownProfile = managedHive();
        unknownProfile.setConnectorProperties(Map.of("hiveProfile", "other-cluster"));
        assertThrows(IllegalArgumentException.class, () -> service.preflight(unknownProfile, "ods_person"));
        verifyNoInteractions(hive, explorer);
    }

    @Test
    void ordinaryHiveKeepsJdbcValidationEvenWithHistoricalProfileField() {
        MetadataExplorerService explorer = mock(MetadataExplorerService.class);
        HiveModule hive = mock(HiveModule.class);
        DataSourceConfig ordinary = managedHive();
        ordinary.setConnectorProperties(Map.of("hiveProfile", "default", "hiveConnectionMode", "open-source"));

        assertThrows(IllegalArgumentException.class,
                () -> new TargetTableDeletionService(explorer, hive).preflight(ordinary, "ods_person"));
        verifyNoInteractions(hive, explorer);
    }

    private DataSourceConfig managedHive() {
        DataSourceConfig config = new DataSourceConfig();
        config.setDatabaseType(DatabaseType.HIVE);
        config.setDatabase("target_db");
        config.setConnectorProperties(Map.of("hiveProfile", "default", "metadataAccessMode", "server-managed-mrs"));
        return config;
    }

    private DataSourceConfig oceanBaseOracle() {
        DataSourceConfig config = new DataSourceConfig();
        config.setDatabaseType(DatabaseType.OCEANBASE_ORACLE);
        config.setHost("10.0.0.1");
        config.setPort(2881);
        config.setDatabase("tenant_service");
        config.setUsername("fjhyj@tenant#cluster");
        config.setPassword("test-password");
        config.setJdbcUrl("jdbc:oceanbase:oracle://10.0.0.1:2881/tenant_service");
        return config;
    }

    private DataSourceConfig dameng() {
        DataSourceConfig config = new DataSourceConfig();
        config.setDatabaseType(DatabaseType.DAMENG);
        config.setHost("10.0.0.2");
        config.setPort(5236);
        config.setDatabase("DAMENG_SERVICE");
        config.setSchema("FJHYJ");
        config.setUsername("FJHYJ");
        config.setPassword("test-password");
        return config;
    }
}
