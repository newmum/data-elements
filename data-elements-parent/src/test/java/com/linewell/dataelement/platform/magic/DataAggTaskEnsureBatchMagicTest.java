package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import com.linewell.dataelement.platform.magic.module.JsonModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.dialect.MySQLDialect;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

/** Executes the canonical ensure API against H2 to guard its multi-target read path. */
class DataAggTaskEnsureBatchMagicTest {
    private static final String SOURCE_ID = "ods_data_agg_task_ensure_01";

    @ParameterizedTest
    @ValueSource(strings = {"oracle", "oceanbaseoracle", "oceanbasemysql", "kingbase8", "postgresql",
            "mysql", "mariadb", "dm", "gaussdb", "db2", "sqlserver", "hive", "hetu", "clickhouse",
            "doris", "starrocks", "gbase8a", "gbase8s", "oscar", "highgo", "vastbase", "tdsql_pg",
            "tdsql_mysql", "ftp", "sftp", "minio", "api", "kafka", "elasticsearch", "hdfs", "hbase", "iotdb"})
    void multiStandardCreatesTaskForEveryRegisteredSourceWithoutMysqlGuard(String type) throws Exception {
        Fixture fixture = new Fixture(2);
        fixture.prepareCreation();
        fixture.jdbc.update("update db_datasource_t set db_type=?,pool_cfg=? where tid='source-db'", type,
                "{\"host\":\"db-host\",\"database\":\"business\",\"compatibleMode\":\"PG\"}");
        fixture.jdbc.update("update db_datasource_t set db_type='oceanbaseoracle' where tid='target-db-0'");
        fixture.jdbc.update("update db_datasource_t set db_type='hive' where tid='target-db-1'");
        fixture.jdbc.update("update db_table_t set field_governance_config=? where tid='source-1'", """
                {"fields":[{"columnName":"person_id","dictionaryRelation":{"enabled":true,
                 "sourceType":"enum","multiValue":true,"multiValueSeparator":",",
                 "enumItems":[{"value":"U","label":"未知"},{"value":"F","label":"女"}]}}]}
                """);
        for (int index = 0; index < 2; index++) fixture.jdbc.update(
                "insert into db_table_column_t values(?,'police',?,'person_id_cn',null,2,0)", "translated-" + index, "target-" + index);
        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run());
        assertEquals(true, response.get("created"));
        assertEquals(2, fixture.jdbc.queryForObject("select count(distinct id) from nifi_pipeline_t", Integer.class),
                fixture.jdbc.queryForList("select id from nifi_pipeline_t").toString());
        String dsl = fixture.jdbc.queryForObject("select p.dsl_json from nifi_pipeline_t p join data_access_agg_task_t t on t.pipeline_id=p.id where t.target_table_id='target-0'", String.class);
        var mappingSpec = cn.hutool.json.JSONUtil.parseObj(dsl).getJSONArray("nodes").getJSONObject(1)
                .getJSONObject("config").getStr("mappings");
        var rules = cn.hutool.json.JSONUtil.parseObj(mappingSpec).getJSONArray("mappings");
        var lookup = rules.getJSONObject(1).getJSONObject("lookup");
        assertTrue(lookup.getBool("multiValue"));
        assertEquals("未知", lookup.getJSONObject("values").getStr("U"));
        assertFalse(lookup.containsKey("sql"));
        assertFalse(lookup.containsKey("dataSource"));
        var plan = new com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService().compilePlan(mappingSpec);
        assertTrue(plan.lookups().getFirst().recordLookup().inline());
        assertFalse(dsl.contains("JSON_TABLE"));
        assertFalse(dsl.contains("GROUP_CONCAT"));
    }

    @Test
    void multiDictionaryTaskReadsStayBatchedAndKeepRegisteredDictionaryType() throws Exception {
        List<Integer> counts = new ArrayList<>();
        for (int targets : List.of(20, 100)) {
        Fixture fixture = new Fixture(targets);
        fixture.prepareCreation();
        fixture.jdbc.execute("alter table nifi_pipeline_t alter column dsl_json clob");
        fixture.addDictionaryFields(1);
        fixture.jdbc.update("update db_datasource_t set db_type='oracle' where tid='source-db'");
        fixture.jdbc.update("update db_datasource_t set db_type='postgresql' where tid='dict-db-0'");
        fixture.jdbc.update("update db_table_t set field_governance_config=? where tid='source-1'", """
                {"fields":[{"columnName":"dict_field_0","dictionaryRelation":{"enabled":true,
                 "sourceType":"dictionary","dictionaryTableId":"dict-0","dictionaryTable":"code_dict_0",
                 "dictionaryKeyField":"code","dictionaryLabelField":"name","multiValue":true,
                 "conditions":[{"field":"enabled","operator":"=","value":"1"}]}}]}
                """);
        for (int index = 0; index < targets; index++) fixture.jdbc.update(
                "insert into db_table_column_t values(?,'police',?,'dict_field_0_cn',null,2,0)", "translated-" + index, "target-" + index);
        assertInstanceOf(Map.class, fixture.run());
        counts.add(fixture.counter.queries);
        System.out.println("Multi-value task targets=" + targets + ", metadata SQL=" + fixture.counter.queries);
        assertTrue(fixture.counter.queries <= 50, "Task creation must use bounded metadata reads");
        String dsl = fixture.jdbc.queryForObject("select p.dsl_json from nifi_pipeline_t p join data_access_agg_task_t t on t.pipeline_id=p.id where t.target_table_id='target-0'", String.class);
        var spec = cn.hutool.json.JSONUtil.parseObj(dsl).getJSONArray("nodes").getJSONObject(1).getJSONObject("config").getStr("mappings");
        var lookup = cn.hutool.json.JSONUtil.parseObj(spec).getJSONArray("mappings").getJSONObject(1).getJSONObject("lookup");
        assertEquals("POSTGRESQL", lookup.getJSONObject("dataSource").getStr("dbType").toUpperCase());
        assertTrue(lookup.getStr("query").contains("code IN (:codes)"));
        assertTrue(lookup.getStr("query").contains("enabled = '1'"));
        assertFalse(dsl.contains("JSON_TABLE"));
        }
        assertEquals(counts.get(0), counts.get(1), "20/100 targets must use the same number of metadata reads");
    }

    @Test
    void registeredVarcharTemporalMappingsAreExecutableAndPersisted() throws Exception {
        Fixture fixture = new Fixture(2);
        fixture.prepareCreation();
        fixture.jdbc.execute("alter table db_table_column_t add data_type varchar(64)");
        fixture.jdbc.execute("alter table db_table_column_t add data_standard_id varchar(64)");
        fixture.jdbc.update("update db_table_column_t set data_type='varchar',data_standard_id='DATETIME' where tid='source-col'");
        fixture.jdbc.update("update db_table_column_t set data_type='timestamp' where table_id like 'target-%'");
        fixture.jdbc.update("update db_table_t set field_governance_config=? where tid='source-1'",
                "{\"fields\":[{\"columnName\":\"person_id\",\"standardField\":\"DATE\"}]}");
        assertInstanceOf(Map.class, fixture.run());
        var nodes = cn.hutool.json.JSONUtil.parseObj(fixture.jdbc.queryForObject(
                "select p.dsl_json from nifi_pipeline_t p join data_access_agg_task_t t on t.pipeline_id=p.id where t.target_table_id='target-0'", String.class)).getJSONArray("nodes");
        var mappingSpec = nodes.getJSONObject(1).getJSONObject("config").getStr("mappings");
        var rules = cn.hutool.json.JSONUtil.parseObj(mappingSpec).getJSONArray("mappings");
        String expression = rules.getJSONObject(0).getStr("expression");
        assertTrue(expression.endsWith("AS TIMESTAMP)"), "Canonical column format takes precedence over the fallback snapshot");
        assertEquals(expression, fixture.jdbc.queryForObject("select func_value from data_access_field_mapping where source_field='person_id' and task_id=(select tid from data_access_agg_task_t where target_table_id='target-0')", String.class));
        assertEquals("EXPRESSION", fixture.jdbc.queryForObject("select func_code from data_access_field_mapping where source_field='person_id' and task_id=(select tid from data_access_agg_task_t where target_table_id='target-0')", String.class));
        assertEquals(1, fixture.jdbc.queryForObject("select func_enable from data_access_field_mapping where source_field='person_id' and task_id=(select tid from data_access_agg_task_t where target_table_id='target-0')", Integer.class));
        assertEquals("varchar", fixture.jdbc.queryForObject("select source_data_type from data_access_field_mapping where source_field='person_id' and task_id=(select tid from data_access_agg_task_t where target_table_id='target-0')", String.class));
        var compiler = new com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService();
        assertFalse(compiler.supportsSourceDbPushdown(mappingSpec));
        String sql = compiler.compilePlan(mappingSpec).baseQuery();
        assertFalse(sql.contains("DUAL"));
        fixture.jdbc.execute("create table flowfile(\"person_id\" varchar(100))");
        fixture.jdbc.update("insert into flowfile values(?)", " 2026-10-09 12:34:56 ");
        fixture.jdbc.update("insert into flowfile values('')");
        var values = fixture.jdbc.queryForList(sql);
        assertEquals(java.sql.Timestamp.valueOf("2026-10-09 12:34:56"), values.getFirst().get("person_id"));
        assertNull(values.get(1).get("person_id"));
    }

    @Test
    void temporalSnapshotFallbackNativeTypesAndClearAreRespected() throws Exception {
        for (String scenario : List.of("fallback", "native", "clear")) {
            Fixture fixture = new Fixture(1);
            fixture.prepareCreation();
            fixture.jdbc.execute("alter table db_table_column_t add data_type varchar(64)");
            fixture.jdbc.execute("alter table db_table_column_t add data_standard_id varchar(64)");
            fixture.jdbc.update("update db_table_column_t set data_type=? where tid='source-col'", scenario.equals("native") ? "date" : "varchar");
            fixture.jdbc.update("update db_table_t set field_governance_config=? where tid='source-1'",
                    "{\"fields\":[{\"columnName\":\"person_id\",\"standardField\":\"DATE\",\"standardFieldExplicitlyCleared\":" + scenario.equals("clear") + "}]}");
            assertInstanceOf(Map.class, fixture.run());
            String value = fixture.jdbc.queryForObject("select func_value from data_access_field_mapping where source_field='person_id' and task_id=(select tid from data_access_agg_task_t where target_table_id='target-0')", String.class);
            if (scenario.equals("fallback")) assertTrue(value.endsWith("AS DATE)"));
            else assertNull(value, "Native temporal values and explicitly cleared rules must not be cast");
        }
    }

    @ParameterizedTest
    @CsvSource({"kingbase8,kingbase", "mysql,mysql", "oracle,oracle", "oceanbasemysql,oceanbase",
            "oceanbaseoracle,oceanbase", "gaussdb,gaussdb", "gbase8a,gbase8a", "sqlserver,sqlserver",
            "hive,hive", "hetu,hetu", "doris,doris", "starrocks,starrocks", "clickhouse,clickhouse",
            "iotdb,iotdb", "hdfs,hdfs", "hbase,hbase", "dameng,dameng", "postgresql,postgresql",
            "db2,db2", "mariadb,mariadb", "gbase8s,gbase8s", "oscar,oscar", "highgo,highgo",
            "minio,minio", "ftp,ftp", "sftp,sftp", "api,api", "kafka,kafka",
            "elasticsearch,elasticsearch", "tdsql_mysql,tdsql-mysql", "tdsql_pg,tdsql-pg", "vastbase,hailiang"})
    void createsTheRegisteredSourceTypeThroughTheActualEnsureScript(String raw, String manifest) throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.prepareCreation();
        fixture.jdbc.update("update db_datasource_t set db_type=?,pool_cfg=? where tid='source-db'", raw,
                "{\"host\":\"db-host\",\"database\":\"business\",\"compatibleMode\":\"PG\",\"bootstrapServers\":\"broker:9092\"}");

        Map<?, ?> created = assertInstanceOf(Map.class, fixture.run());
        assertEquals(true, created.get("created"));
        var source = cn.hutool.json.JSONUtil.parseObj(fixture.jdbc.queryForObject(
                "select p.dsl_json from nifi_pipeline_t p join data_access_agg_task_t t on t.pipeline_id=p.id where t.target_table_id='target-0'", String.class)).getJSONArray("nodes").getJSONObject(0);
        assertEquals("source." + manifest, source.getStr("manifestKey"));
        if (raw.equals("kingbase8")) {
            assertEquals("KINGBASE", source.getJSONObject("config").getStr("dbType"));
            assertEquals("PG", source.getJSONObject("config").getStr("compatibleMode"));
            assertTrue(source.getJSONObject("config").getStr("jdbcUrl").startsWith("jdbc:kingbase8:"));
        }
        if (raw.equals("kafka")) {
            assertEquals("broker:9092", source.getJSONObject("config").getStr("bootstrapServers"));
        }
        if (raw.equals("oceanbaseoracle")) {
            assertEquals("ORACLE", source.getJSONObject("config").getStr("compatibleMode"));
        }
    }

    @ParameterizedTest
    @CsvSource({"kingbase8,KINGBASE,jdbc:kingbase8:", "oracle,Oracle,jdbc:oracle:",
            "sqlserver,SQLSERVER,jdbc:sqlserver:", "db2,DB2,jdbc:db2:",
            "oceanbaseoracle,OCEANBASE_ORACLE,jdbc:oceanbase:", "mariadb,MARIADB,jdbc:mariadb:",
            "gaussdb,GAUSSDB,jdbc:postgresql:", "hive,HIVE,jdbc:hive2:", "dm,DM,jdbc:dm:"})
    void targetDialectAndJdbcProtocolFollowTheRegisteredTarget(String raw, String expected, String protocol) throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.prepareCreation();
        fixture.jdbc.update("update db_datasource_t set db_type=?,pool_cfg=? where tid='target-db-0'", raw,
                "{\"host\":\"db-host\",\"database\":\"business\"}");
        assertInstanceOf(Map.class, fixture.run());
        var sink = cn.hutool.json.JSONUtil.parseObj(fixture.jdbc.queryForObject(
                "select p.dsl_json from nifi_pipeline_t p join data_access_agg_task_t t on t.pipeline_id=p.id where t.target_table_id='target-0'", String.class)).getJSONArray("nodes").getJSONObject(2);
        assertEquals(expected, sink.getJSONObject("config").getStr("dbType"));
        assertTrue(sink.getJSONObject("config").getStr("jdbcUrl").startsWith(protocol));
    }

    @Test
    void unsupportedSourceFailsBeforeCreatingAnyTaskOrCanvas() throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.prepareCreation();
        fixture.jdbc.update("update db_datasource_t set db_type='unknown-vendor' where tid='source-db'");
        assertInstanceOf(ExitValue.class, fixture.run());
        assertEquals(0, fixture.counter.writes);
        assertEquals(0, fixture.jdbc.queryForObject("select count(*) from nifi_pipeline_t", Integer.class));
    }

    @Test
    void deniedSourceCannotBeUsedEvenWithVisibleTargets() throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.prepareCreation();
        fixture.scope.deniedId = "source-db";
        assertInstanceOf(ExitValue.class, fixture.run());
        assertEquals(0, fixture.counter.writes);
    }

    @Test
    void twentyAndHundredTargetsUseTheSameNumberOfTenantQueries() throws Exception {
        Fixture twenty = new Fixture(20);
        Fixture hundred = new Fixture(100);

        Map<?, ?> first = assertInstanceOf(Map.class, twenty.run());
        Map<?, ?> second = assertInstanceOf(Map.class, hundred.run());

        assertEquals(false, first.get("created"));
        assertEquals(true, first.get("repairRequired"), first.toString());
        assertEquals(true, second.get("repairRequired"), second.toString());
        assertEquals(20, ((Collection<?>) first.get("targetTableIds")).size());
        assertEquals(100, ((Collection<?>) second.get("targetTableIds")).size());
        assertEquals(twenty.counter.queries, hundred.counter.queries);
        assertTrue(hundred.counter.queries <= 18, "one batch read per entity, not one per target");
        assertEquals(0, hundred.counter.writes);
    }

    @Test
    void aDeniedTargetDatasourceRejectsTheWholeBatch() throws Exception {
        Fixture fixture = new Fixture(20);
        fixture.scope.deniedId = "target-db-19";

        assertInstanceOf(ExitValue.class, fixture.run());

        assertEquals(0, fixture.counter.writes);
        assertEquals(1, fixture.jdbc.queryForObject(
                "select count(*) from data_access_agg_task_t where tid='task-1' and is_del=0", Integer.class));
    }

    @Test
    void aForeignTenantDatasourceRejectsTheWholeBatch() throws Exception {
        Fixture fixture = new Fixture(20);
        fixture.jdbc.update("update db_datasource_t set tenant_id='other' where tid='target-db-19'");

        assertInstanceOf(ExitValue.class, fixture.run());

        assertEquals(0, fixture.counter.writes);
        assertEquals(1, fixture.jdbc.queryForObject(
                "select count(*) from data_access_agg_task_t where tid='task-1' and is_del=0", Integer.class));
    }

    @Test
    void moreThanOneHundredTargetsIsRejectedBeforeLoadingTargetDatabases() throws Exception {
        Fixture fixture = new Fixture(101);

        assertInstanceOf(ExitValue.class, fixture.run());

        assertEquals(0, fixture.counter.writes);
        assertEquals(1, fixture.jdbc.queryForObject(
                "select count(*) from data_access_agg_task_t where tid='task-1' and is_del=0", Integer.class));
    }

    @Test
    void removedSinkRequiresRepairWithoutReplacingUserCanvas() throws Exception {
        Fixture fixture = new Fixture(2);
        fixture.jdbc.update("update db_table_t set is_del=1 where tid='target-1'");

        Map<?, ?> result = assertInstanceOf(Map.class, fixture.run());

        assertEquals(true, result.get("repairRequired"));
        assertEquals(0, fixture.counter.writes);
        assertEquals(1, fixture.jdbc.queryForObject(
                "select count(*) from nifi_pipeline_t where id='pipeline-1' and is_del=0", Integer.class));
    }

    @Test
    void missingSharedMappingRequiresExplicitRepairAndKeepsTheCanvas() throws Exception {
        Fixture fixture = new Fixture(2);
        fixture.jdbc.update("delete from data_access_field_mapping where tid='mapping-1'");

        Map<?, ?> result = assertInstanceOf(Map.class, fixture.run());

        assertEquals(true, result.get("repairRequired"));
        assertEquals(0, fixture.counter.writes);
        assertEquals(1, fixture.jdbc.queryForObject(
                "select count(*) from nifi_pipeline_t where id='pipeline-1' and is_del=0", Integer.class));
        assertEquals(1, fixture.jdbc.queryForObject(
                "select count(*) from data_access_agg_task_t where tid='task-1' and is_del=0", Integer.class));
    }

    @Test
    void initialEnsureCreatesIndependentChainedTasksAndReusesThem() throws Exception {
        Fixture fixture = new Fixture(2);
        fixture.jdbc.update("update db_table_t set table_name_cn='人员信息' where tid='source-1'");
        fixture.jdbc.update("update db_table_t set table_name_cn='人员原始表' where tid='target-0'");
        fixture.jdbc.update("delete from data_access_field_mapping");
        fixture.jdbc.update("delete from nifi_pipeline_t");
        fixture.jdbc.update("delete from data_access_agg_task_t");

        Map<?, ?> created = assertInstanceOf(Map.class, fixture.run());

        assertEquals(true, created.get("created"));
        assertEquals(2, ((Collection<?>) created.get("targetTableIds")).size());
        assertEquals(2, fixture.jdbc.queryForObject("select count(*) from data_access_field_mapping", Integer.class));
        String dsl = fixture.jdbc.queryForObject("select p.dsl_json from nifi_pipeline_t p join data_access_agg_task_t t on t.pipeline_id=p.id where t.target_table_id='target-0'", String.class);
        assertNotNull(dsl);
        assertTrue(dsl.contains("\"targetTableId\":\"target-0\""));
        assertFalse(dsl.contains("\"targetTableId\":\"target-1\""));
        var nodes = cn.hutool.json.JSONUtil.parseObj(dsl).getJSONArray("nodes");
        assertEquals("人员信息", nodes.getJSONObject(0).getStr("label"));
        assertEquals("字段映射", nodes.getJSONObject(1).getStr("label"));
        assertEquals("人员原始表", nodes.getJSONObject(2).getStr("label"));
        assertEquals(3, nodes.size());
        var downstream = fixture.jdbc.queryForMap("select source_table_id,target_table_id,source_db_id,target_db_id from data_access_agg_task_t where target_table_id='target-1'");
        assertEquals("target-0", downstream.get("source_table_id"));
        assertEquals("target-db-0", downstream.get("source_db_id"));
        assertEquals("target-db-1", downstream.get("target_db_id"));
        String nextDsl = fixture.jdbc.queryForObject("select p.dsl_json from nifi_pipeline_t p join data_access_agg_task_t t on t.pipeline_id=p.id where t.target_table_id='target-1'", String.class);
        var nextNodes = cn.hutool.json.JSONUtil.parseObj(nextDsl).getJSONArray("nodes");
        assertEquals(3, nextNodes.size());
        assertEquals("target-0", nextNodes.getJSONObject(0).getJSONObject("config").getStr("sourceTableId"));
        assertEquals("target-1", nextNodes.getJSONObject(2).getJSONObject("config").getStr("targetTableId"));

        Map<?, ?> reused = assertInstanceOf(Map.class, fixture.run());
        assertEquals(false, reused.get("created"));
        assertEquals(false, reused.get("repairRequired"), reused.toString());
        assertEquals(2, fixture.jdbc.queryForObject("select count(*) from nifi_pipeline_t", Integer.class));
    }

    @Test
    void twentyAndHundredRegisteredDictionaryFieldsUseBatchMetadataReads() throws Exception {
        Fixture twenty = new Fixture(1);
        Fixture hundred = new Fixture(1);
        twenty.addDictionaryFields(20);
        hundred.addDictionaryFields(100);

        Map<?, ?> first = assertInstanceOf(Map.class, twenty.run());
        Map<?, ?> second = assertInstanceOf(Map.class, hundred.run());

        assertEquals(true, first.get("repairRequired"));
        assertEquals(true, second.get("repairRequired"));
        assertEquals(twenty.counter.queries, hundred.counter.queries);
        assertTrue(hundred.counter.queries <= 25, "dictionary tables, columns and sources are prefetched");
        assertEquals(0, hundred.counter.writes);
    }

    @Test
    void selectedOrderControlsEachHopAndDownstreamPreservesOdsFields() throws Exception {
        Fixture fixture = new Fixture(3);
        fixture.prepareCreation();
        for (int index=0; index<3; index++) for (String name : List.of("ODS_UUID", "ODS_RKSJ", "ODS_GXSJ")) {
            fixture.jdbc.update("insert into db_table_column_t values(?,'police',?,?,null,3,0)", name+index, "target-"+index, name);
        }
        Map<?,?> result = assertInstanceOf(Map.class, fixture.run(Map.of("tableId","source-1", "targetTableIds", List.of("target-2","target-0"))));
        assertEquals(2,result.get("createdCount"));
        assertEquals(0, fixture.jdbc.queryForObject("select count(*) from data_access_agg_task_t where target_table_id='target-1'",Integer.class));
        assertEquals("target-2", fixture.jdbc.queryForObject("select source_table_id from data_access_agg_task_t where target_table_id='target-0'",String.class));
        var dsl = cn.hutool.json.JSONUtil.parseObj(fixture.jdbc.queryForObject("select p.dsl_json from nifi_pipeline_t p join data_access_agg_task_t t on t.pipeline_id=p.id where t.target_table_id='target-0'",String.class));
        var mapping = cn.hutool.json.JSONUtil.parseObj(dsl.getJSONArray("nodes").getJSONObject(1).getJSONObject("config").getStr("mappings"));
        var uuid = mapping.getJSONArray("mappings").stream().map(value -> cn.hutool.json.JSONUtil.parseObj(value)).filter(value -> "/ODS_UUID".equals(value.getStr("to"))).findFirst().orElseThrow();
        assertEquals("/ODS_UUID",uuid.getStr("from"));
        assertFalse(uuid.containsKey("expression"));
        assertEquals("ODS_GXSJ",dsl.getJSONArray("nodes").getJSONObject(0).getJSONObject("config").getStr("incrementalColumn"));
        assertEquals(0,fixture.jdbc.queryForObject("select count(*) from data_access_field_mapping where task_id=(select tid from data_access_agg_task_t where target_table_id='target-0') and func_enable=1",Integer.class));
    }

    @Test
    void dryRunAndUnauthorizedSelectedIdNeverCreateTasks() throws Exception {
        Fixture fixture=new Fixture(2); fixture.prepareCreation();
        Map<?,?> plan=assertInstanceOf(Map.class,fixture.run(Map.of("tableId","source-1","dryRun",true)));
        assertEquals(2,plan.get("plannedCreateCount")); assertEquals(false,plan.get("sideEffectsApplied"));
        assertEquals(0,fixture.counter.writes);
        assertInstanceOf(ExitValue.class,fixture.run(Map.of("tableId","source-1","targetTableIds",List.of("target-0","foreign-target"))));
        assertEquals(0,fixture.counter.writes);
    }

    @Test
    void explicitRepairSplitsLegacyAndFailedBatchRollsBackAllHops() throws Exception {
        Fixture fixture=new Fixture(2);
        Map<?,?> split=assertInstanceOf(Map.class,fixture.run(Map.of("tableId","source-1","forceRebuild",true)));
        assertEquals(2,split.get("createdCount"));
        assertEquals(1,fixture.jdbc.queryForObject("select is_del from data_access_agg_task_t where tid='task-1'",Integer.class));
        assertEquals(2,fixture.jdbc.queryForObject("select count(*) from data_access_agg_task_t where is_del=0",Integer.class));
        Fixture failed=new Fixture(2); failed.prepareCreation();
        failed.jdbc.execute("alter table data_access_agg_task_t add constraint fail_downstream check(target_table_id <> 'target-1')");
        assertThrows(Exception.class,failed::run);
        assertEquals(0,failed.jdbc.queryForObject("select count(*) from nifi_pipeline_t",Integer.class));
        assertEquals(0,failed.jdbc.queryForObject("select count(*) from data_access_agg_task_t",Integer.class));
    }

    @Test
    void twentyAndHundredNewHopsUseConstantMetadataReadsAndThreeBatchWrites() throws Exception {
        List<Integer> reads=new ArrayList<>();
        for (int size:List.of(20,100)) {
            Fixture fixture=new Fixture(size); fixture.prepareCreation();
            Map<?,?> result=assertInstanceOf(Map.class,fixture.run());
            assertEquals(size,result.get("createdCount"));
            assertEquals(size,fixture.jdbc.queryForObject("select count(*) from data_access_agg_task_t",Integer.class));
            reads.add(fixture.counter.queries);
            assertEquals(3,fixture.counter.writes);
            System.out.println("Chained tasks="+size+", metadata SQL="+fixture.counter.queries+", batch writes="+fixture.counter.writes);
        }
        assertEquals(reads.get(0),reads.get(1));
    }

    @Test
    void listAggregationGroupsRealHopSourcesUnderRootAndUsesConstantReads() throws Exception {
        List<Integer> readCounts = new ArrayList<>();
        for (int size : List.of(20, 100)) {
            Fixture f = new Fixture(2); f.prepareCreation();
            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                String root = "root-" + i; ids.add(root);
                rows.add(Map.of("tid", root, "datasourceId", "source-db", "tableName", root));
                f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) values(?,'police','source-db',?,0)",root,root);
                for (int hop = 0; hop < 2; hop++) {
                    String target = root+"-target-"+hop;
                    String actualSource = hop == 0 ? root : root+"-target-0";
                    String pipelineId = target+"-pipeline";
                    f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,source_table_id,is_del) values(?,'police',?,?,?,0)",target,"target-db-"+hop,target,root);
                    f.jdbc.update("insert into data_access_agg_task_t(tid,tenant_id,source_table_id,target_table_id,pipeline_id,is_del) values(?,'police',?,?,?,0)",target+"-task",actualSource,target,pipelineId);
                    f.jdbc.update("insert into nifi_pipeline_t(id,tenant_id,dsl_json,is_del) values(?,'police',?,0)",pipelineId,
                            cn.hutool.json.JSONUtil.toJsonStr(Map.of("nodes",List.of(Map.of("category","sink","config",Map.of("targetTableId",target))),
                                    "accessChain",Map.of("targetTableIds",List.of(root+"-target-0",root+"-target-1")))));
                }
            }
            String canonical = CanonicalMagicSources.byId("76fd211790f54164947f276824d19c1d");
            String aggregation = canonical.substring(canonical.indexOf("    // 表级工作流状态"),canonical.indexOf("    // ES 检索、接入任务补齐"));
            String helpers = """
                    import cn.hutool.json.JSONUtil
                    var text = value => value == null ? '' : '' + value
                    var blank = value => value == null || text(value).trim() == ''
                    var first = (row, keys) => { if (row == null) { return null } for (key in keys) { if (row[key] != null) { return row[key] } } return null }
                    var formatCycle = value => ''
                    """;
            Map<String,Object> inputs = new LinkedHashMap<>();
            inputs.put("db",f.db); inputs.put("tenantId","police"); inputs.put("dataScope",f.scope);
            inputs.put("candidateIds",ids); inputs.put("tableCandidates",rows);
            inputs.put("requestedAccessStatus","all"); inputs.put("selectedDatasourceNameMap",Map.of("source-db","来源库"));
            inputs.put("selectedDatasourceAppNameMap",Map.of());
            f.counter.queries=0;
            Map<?,?> debug = assertInstanceOf(Map.class,MagicScript.create(helpers+aggregation+"\nreturn {items:tableItems, groups:taskGroups, roots:chainRootBySource}",null).execute(new MagicScriptContext(inputs)));
            List<?> actual = assertInstanceOf(List.class,debug.get("items"));
            readCounts.add(f.counter.queries);
            assertEquals(size,actual.size());
            for (Object value : actual) {
                Map<?,?> row=assertInstanceOf(Map.class,value);
                assertEquals(2,row.get("taskCount"), "size="+size+", group="+assertInstanceOf(Map.class,debug.get("groups")).get(row.get("tid"))+", root="+assertInstanceOf(Map.class,debug.get("roots")).get(row.get("tid")+"-target-0")+", row="+row);
                List<?> tasks=assertInstanceOf(List.class,row.get("tasks"));
                Map<?,?> second=assertInstanceOf(Map.class,tasks.get(1));
                assertEquals(row.get("tid")+"-target-0",second.get("sourceTableId"));
                assertEquals(1,assertInstanceOf(List.class,second.get("targetTables")).size());
            }
            f.scope.deniedId="target-db-1";
            actual=assertInstanceOf(List.class,MagicScript.create(helpers+aggregation+"\nreturn tableItems",null).execute(new MagicScriptContext(inputs)));
            for(Object value:actual) assertEquals(1,assertInstanceOf(Map.class,value).get("taskCount"));
            assertEquals(0,f.counter.writes);
            System.out.println("List rows="+size+", aggregate metadata SQL="+readCounts.getLast());
        }
        assertEquals(readCounts.get(0),readCounts.get(1));
    }

    @Test
    void hiveStageReadsItsRealSourceAndResolvesChainedTaskTarget() throws Exception {
        Fixture f=new Fixture(2); f.prepareCreation();
        assertInstanceOf(Map.class,f.run());
        f.jdbc.execute("alter table db_table_t add updated_time timestamp");
        String script=CanonicalMagicSources.byId("1f475a245fda4ba4a885b3ac4f8e2d59")
                .replaceAll("(?m)^import (tenantRuntime|dataScope)\\r?\\n", "");
        Map<String,Object> inputs=new LinkedHashMap<>();
        inputs.put("db",f.db); inputs.put("tenantRuntime",f.scope); inputs.put("dataScope",f.scope);
        inputs.put("body",Map.of("pageNum",1,"pageSize",20));
        Map<?,?> result=assertInstanceOf(Map.class,MagicScript.create(script,null).execute(new MagicScriptContext(inputs)));
        List<?> list=assertInstanceOf(List.class,result.get("list"));
        Map<?,?> first=list.stream().map(value->(Map<?,?>)value).filter(row->"target-0".equals(row.get("tid"))).findFirst().orElseThrow();
        assertEquals(1,first.get("taskCount"));
        Map<?,?> task=assertInstanceOf(Map.class,assertInstanceOf(List.class,first.get("tasks")).getFirst());
        assertEquals("target-0",task.get("sourceTableId"));
        assertEquals("target-1",task.get("targetTableId"));
        assertEquals("ods_person_1",task.get("targetTableName"));
        assertEquals(1,assertInstanceOf(List.class,task.get("targetTables")).size());
    }

    private static final class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final QueryCounter counter = new QueryCounter();
        final ScopeRuntime scope = new ScopeRuntime();

        Fixture(int targetCount) {
            var source = new DriverManagerDataSource("jdbc:h2:mem:agg_ensure_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setResultProvider(new DefaultResultProvider("data-agg-ensure-test"));
            var dialects = new DialectAdapter();
            dialects.add(new MySQLDialect() {
                @Override
                public boolean match(String connectionUrl) {
                    return connectionUrl.contains(":h2:");
                }
            });
            db.setDialectAdapter(dialects);
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setSqlInterceptors(List.of(counter));
            db.setNamedTableInterceptors(List.of());
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    create table db_table_t(tid varchar(64),tenant_id varchar(32),datasource_id varchar(64),
                        table_name varchar(100),table_name_cn varchar(100),source_table_id varchar(64),source_catalog_id varchar(64),
                        field_governance_config clob,created_time timestamp,is_del int)
                    """);
            jdbc.execute("""
                    create table db_datasource_t(tid varchar(64),tenant_id varchar(32),db_type varchar(32),
                        pool_cfg clob,is_del int)
                    """);
            jdbc.execute("""
                    create table db_table_column_t(tid varchar(64),tenant_id varchar(32),table_id varchar(64),
                        column_name varchar(100),code_table_id varchar(64),ordinal_position int,is_del int)
                    """);
            jdbc.execute("""
                    create table da_prop_t(tenant_id varchar(32),parent_id varchar(64),prop_name varchar(100),
                        prop_value varchar(1000),is_del int)
                    """);
            jdbc.execute("create table da_catalog_t(tid varchar(64),tenant_id varchar(32),is_del int)");
            jdbc.execute("""
                    create table data_access_agg_task_t(tid varchar(64),tenant_id varchar(32),
                        source_table_id varchar(64),target_table_id varchar(64),pipeline_id varchar(64),
                        data_catalog_id varchar(64),task_name varchar(200),task_type_name varchar(100),
                        task_type_id varchar(100),task_desc varchar(300),source_db_id varchar(64),
                        source_table_primary_key varchar(200),target_db_id varchar(64),
                        target_table_primary_key varchar(200),schedule_strategy int,schedule_cycle int,
                        task_status int,created_time timestamp,
                        is_del int,updated_time timestamp)
                    """);
            jdbc.execute("""
                    create table data_access_field_mapping(tid varchar(64),tenant_id varchar(32),task_id varchar(64),
                        source_field varchar(100),source_field_cn varchar(100),source_field_length int,
                        source_data_type varchar(100),target_field varchar(100),target_field_cn varchar(100),
                        target_field_length int,target_data_type varchar(100),dict_enable int,dict_value varchar(200),
                        dict_code varchar(200),func_enable int,func_code varchar(100),func_value varchar(4000),
                        sort_no int,is_del int,created_time timestamp,updated_time timestamp)
                    """);
            jdbc.execute("""
                    create table nifi_pipeline_t(id varchar(64),tenant_id varchar(32),name varchar(200),
                        description varchar(300),status varchar(30),dsl_json varchar(32000),dsl_version int,
                        created_at bigint,updated_at bigint,is_del int)
                    """);
            jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) "
                    + "values('source-1','police','source-db','person',0)");
            jdbc.update("insert into db_datasource_t(tid,tenant_id,db_type,is_del) values('source-db','police','mysql',0)");
            jdbc.update("insert into db_table_column_t(tid,tenant_id,table_id,column_name,ordinal_position,is_del) "
                    + "values('source-col','police','source-1','person_id',1,0)");
            List<Map<String, Object>> nodes = new ArrayList<>();
            for (int i = 0; i < targetCount; i++) {
                String tableId = "target-" + i;
                String datasourceId = "target-db-" + i;
                jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,source_table_id,created_time,is_del) "
                        + "values(?,?,?,?,?,dateadd('SECOND', ?, timestamp '2026-01-01 00:00:00'),0)",
                        tableId, "police", datasourceId, "ods_person_" + i, "source-1", i);
                jdbc.update("insert into db_datasource_t(tid,tenant_id,db_type,is_del) values(?,'police','mysql',0)", datasourceId);
                jdbc.update("insert into db_table_column_t(tid,tenant_id,table_id,column_name,ordinal_position,is_del) "
                        + "values(?,'police',?,'person_id',1,0)", "target-col-" + i, tableId);
                nodes.add(Map.of("id", "sink-" + tableId, "category", "sink", "manifestKey", "sink.jdbc",
                        "config", Map.of("targetTableId", tableId, "targetDbId", datasourceId)));
            }
            jdbc.update("insert into data_access_agg_task_t(tid,tenant_id,source_table_id,target_table_id,pipeline_id,is_del) "
                    + "values('task-1','police','source-1','target-0','pipeline-1',0)");
            jdbc.update("insert into data_access_field_mapping(tid,tenant_id,task_id,is_del) "
                    + "values('mapping-1','police','task-1',0)");
            String dsl = cn.hutool.json.JSONUtil.toJsonStr(Map.of("version", 1, "nodes", nodes, "edges", List.of()));
            jdbc.update("insert into nifi_pipeline_t(id,tenant_id,dsl_json,is_del) values('pipeline-1','police',?,0)", dsl);
        }

        void addDictionaryFields(int count) {
            for (int i = 0; i < count; i++) {
                String dictId = "dict-" + i;
                String dbId = "dict-db-" + i;
                jdbc.update("insert into db_table_column_t(tid,tenant_id,table_id,column_name,code_table_id,ordinal_position,is_del) "
                        + "values(?,'police','source-1',?,?,?,0)",
                        "source-dict-col-" + i, "dict_field_" + i, dictId, i + 2);
                jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) "
                        + "values(?,'police',?,?,0)", dictId, dbId, "code_dict_" + i);
                jdbc.update("insert into db_datasource_t(tid,tenant_id,db_type,is_del) values(?,'police','mysql',0)", dbId);
                jdbc.update("insert into db_table_column_t(tid,tenant_id,table_id,column_name,ordinal_position,is_del) "
                        + "values(?,'police',?,'code',1,0)", "dict-code-" + i, dictId);
                jdbc.update("insert into db_table_column_t(tid,tenant_id,table_id,column_name,ordinal_position,is_del) "
                        + "values(?,'police',?,'name',2,0)", "dict-name-" + i, dictId);
            }
        }

        void prepareCreation() {
            jdbc.update("delete from data_access_field_mapping");
            jdbc.update("delete from nifi_pipeline_t");
            jdbc.update("delete from data_access_agg_task_t");
        }

        Object run() throws Exception { return run(Map.of("tableId", "source-1")); }

        Object run(Map<String, Object> body) throws Exception {
            String script = CanonicalMagicSources.byId(SOURCE_ID)
                    .replaceAll("(?m)^import (jsons|metadataAsset|targetTableSchema|apiPullConfig|tenantRuntime|dataScope)\\r?\\n", "");
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("db", db);
            inputs.put("body", body);
            inputs.put("tenantRuntime", scope);
            inputs.put("dataScope", scope);
            inputs.put("jsons", new JsonModule());
            inputs.put("apiPullConfig", new ApiPullStub());
            counter.queries = 0;
            counter.writes = 0;
            return MagicScript.create(script, null).execute(new MagicScriptContext(inputs));
        }
    }

    public static final class ApiPullStub {
        public Map<String, Object> resolvePipelineSource(String datasourceId, String tableName) {
            return Map.of("url", "https://api.test/person", "method", "GET");
        }
    }

    public static final class ScopeRuntime {
        String deniedId;
        public String id() { return "police"; }
        public List<Map<String, Object>> visibleDataSourcesFor(String resource, Collection<Map<String, Object>> rows) {
            assertEquals("MENU:2081000000000000002", resource);
            return rows.stream().filter(row -> !String.valueOf(row.get("tid")).equals(deniedId)).toList();
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
