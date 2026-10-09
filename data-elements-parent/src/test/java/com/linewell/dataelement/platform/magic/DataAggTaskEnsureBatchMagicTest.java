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
                "select dsl_json from nifi_pipeline_t", String.class)).getJSONArray("nodes").getJSONObject(0);
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
                "select dsl_json from nifi_pipeline_t", String.class)).getJSONArray("nodes").getJSONObject(2);
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
        assertEquals(false, first.get("repairRequired"), first.toString());
        assertEquals(false, second.get("repairRequired"), second.toString());
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
    void initialEnsureCreatesOneSharedMappingAndBothTargetSinks() throws Exception {
        Fixture fixture = new Fixture(2);
        fixture.jdbc.update("update db_table_t set table_name_cn='人员信息' where tid='source-1'");
        fixture.jdbc.update("update db_table_t set table_name_cn='人员原始表' where tid='target-0'");
        fixture.jdbc.update("delete from data_access_field_mapping");
        fixture.jdbc.update("delete from nifi_pipeline_t");
        fixture.jdbc.update("delete from data_access_agg_task_t");

        Map<?, ?> created = assertInstanceOf(Map.class, fixture.run());

        assertEquals(true, created.get("created"));
        assertEquals(2, ((Collection<?>) created.get("targetTableIds")).size());
        assertEquals(1, fixture.jdbc.queryForObject("select count(*) from data_access_field_mapping", Integer.class));
        String dsl = fixture.jdbc.queryForObject("select dsl_json from nifi_pipeline_t", String.class);
        assertNotNull(dsl);
        assertTrue(dsl.contains("\"targetTableId\":\"target-0\""));
        assertTrue(dsl.contains("\"targetTableId\":\"target-1\""));
        var nodes = cn.hutool.json.JSONUtil.parseObj(dsl).getJSONArray("nodes");
        assertEquals("人员信息", nodes.getJSONObject(0).getStr("label"));
        assertEquals("字段映射", nodes.getJSONObject(1).getStr("label"));
        assertEquals("人员原始表", nodes.getJSONObject(2).getStr("label"));
        assertEquals("ods_person_1", nodes.getJSONObject(3).getStr("label"));

        Map<?, ?> reused = assertInstanceOf(Map.class, fixture.run());
        assertEquals(false, reused.get("created"));
        assertEquals(false, reused.get("repairRequired"), reused.toString());
        assertEquals(1, fixture.jdbc.queryForObject("select count(*) from nifi_pipeline_t", Integer.class));
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

        Object run() throws Exception {
            String script = CanonicalMagicSources.byId(SOURCE_ID)
                    .replaceAll("(?m)^import (jsons|metadataAsset|targetTableSchema|apiPullConfig|tenantRuntime|dataScope)\\r?\\n", "");
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("db", db);
            inputs.put("body", Map.of("tableId", "source-1"));
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
