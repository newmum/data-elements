package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.ddl.CanvasTableDdl;
import com.linewell.dataelement.metautil.explorer.impl.HiveMetadataExplorer;
import com.linewell.dataelement.metautil.explorer.impl.MySqlMetadataExplorer;
import com.linewell.dataelement.metautil.explorer.impl.OracleMetadataExplorer;
import com.linewell.dataelement.metautil.explorer.impl.OceanBaseMetadataExplorer;
import com.linewell.dataelement.metautil.explorer.impl.PostgreSqlMetadataExplorer;
import com.linewell.dataelement.platform.magic.module.JsonModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

/** Runs the canonical DDL API's batch branch with a real tenant metadata query. */
class BatchCreateTableDdlMagicTest {
    @Test
    void partitionErrorsExplainMissingValuesAndUseTheRegisteredDatasourceName() throws Exception {
        Fixture f=new Fixture(1);
        f.jdbc.update("update db_datasource_t set db_name='汇聚库' where tid='db-0'");
        var target=new LinkedHashMap<>(f.targets.getFirst());
        target.put("partitionPlan",Map.of("enabled",true,"strategy","LIST","field","person_id","partitions",List.of()));
        f.targets.set(0,target);
        var failure=assertInstanceOf(ExitValue.class,f.run());
        String message=java.util.Arrays.toString(failure.getValues());
        assertTrue(message.contains("目标数据源“汇聚库”"),message);
        assertTrue(message.contains("请填写至少一个区域编码或枚举值"),message);
        assertFalse(message.contains("db-0")); assertFalse(message.contains("请配置1至"));
        assertEquals(1,f.counter.reads); assertEquals(0,f.counter.writes);
    }

    @Test
    void validRegionValuesGeneratePartitionsAndInvalidValuesHaveActionableMessages() throws Exception {
        Fixture f=new Fixture(1);
        f.jdbc.update("update db_datasource_t set db_type='oracle',db_name='汇聚库' where tid='db-0'");
        var target=new LinkedHashMap<>(f.targets.getFirst());
        target.put("tableItems",List.of(Map.of("columnName","region_code","columnComment","区域编码","dataType","int","columnType","int","primaryKey",1)));
        target.put("partitionPlan",Map.of("enabled",true,"strategy","LIST","field","region_code","partitions",
                List.of(Map.of("name","p_fz","values",List.of("350100")),Map.of("name","p_xm","values",List.of("350200")))));
        f.targets.set(0,target);
        var success=assertInstanceOf(Map.class,f.run());
        String ddl=((Map<?,?>)((List<?>)success.get("results")).getFirst()).get("ddl").toString();
        assertTrue(ddl.contains("PARTITION BY LIST"),ddl); assertFalse(ddl.contains("'350100'"),ddl);
        target.put("partitionPlan",Map.of("enabled",true,"strategy","LIST","field","region_code","partitions",
                List.of(Map.of("name","p_fz","values",List.of("福州")))));
        String numeric=java.util.Arrays.toString(assertInstanceOf(ExitValue.class,f.run()).getValues());
        assertTrue(numeric.contains("数字类型，请填写数字枚举值"),numeric);
        target.put("partitionPlan",Map.of("enabled",true,"strategy","LIST","field","region_code","partitions",
                List.of(Map.of("name","p_fz","values",List.of("350100","350100")))));
        String duplicate=java.util.Arrays.toString(assertInstanceOf(ExitValue.class,f.run()).getValues());
        assertTrue(duplicate.contains("区域编码或枚举值有重复"),duplicate);
        assertEquals(1,f.counter.reads); assertEquals(0,f.counter.writes);
    }
    private static final String SOURCE_ID = "8a56e7d664934c1499102cfa508cd58a";

    @Test void regionPartitionOceanbaseLogicalPrimaryAndOtherSourceUniqueUseOneReadAtTwentyAndHundredFields() throws Exception {
        for(int count:List.of(20,100)) {
            Fixture f=new Fixture(20);f.jdbc.update("update db_datasource_t set db_type='oceanbaseoracle',username='ODS_OWNER'");
            f.ddlGenerator.expectedColumns=count;
            var fields=new ArrayList<Map<String,Object>>();
            fields.add(Map.of("columnName","DSBM","dataType","varchar","length",6));
            fields.add(Map.of("columnName","META_ADDR_ID","dataType","varchar","length",32,"primaryKey",1));
            fields.add(Map.of("columnName","RECORDNO","dataType","bigint"));
            for(int i=3;i<count;i++)fields.add(Map.of("columnName","f"+i,"dataType","varchar","length",32));
            for(int i=0;i<f.targets.size();i++) {
                var target=new LinkedHashMap<>(f.targets.get(i));target.put("tableItems",fields);
                target.put("sourceConstraints",List.of(Map.of("name","UN_RECORDNO","type","U","columns",List.of("RECORDNO"))));
                target.put("partitionPlan",Map.of("enabled",true,"strategy","REGION","field","DSBM"));f.targets.set(i,target);
            }
            var result=(Map<?,?>)f.run();
            for(Object item:(List<?>)result.get("results")) {
                String ddl=((Map<?,?>)item).get("ddl").toString();
                assertFalse(ddl.contains("PRIMARY KEY"),ddl);assertTrue(ddl.contains("UNIQUE (META_ADDR_ID)"),ddl);
                assertTrue(ddl.contains("UNIQUE (RECORDNO)"),ddl);assertTrue(ddl.contains("'351000'"),ddl);
            }
            assertEquals(1,f.counter.reads);assertEquals(0,f.counter.writes);
        }
    }

    @Test void yearlyPlansAndSourceUniqueConstraintsUseOneReadForTwentyAndHundredFields() throws Exception {
        for(int count:List.of(20,100)) {
            Fixture f=new Fixture(20);f.jdbc.update("update db_datasource_t set db_type='oceanbaseoracle',username='ODS_OWNER'");
            f.ddlGenerator.expectedColumns=count;
            var fields=new ArrayList<Map<String,Object>>();
            fields.add(Map.of("columnName","occurred_at","dataType","timestamp"));
            for(int i=1;i<count;i++)fields.add(Map.of("columnName","f"+i,"dataType","varchar","length",32));
            for(int i=0;i<f.targets.size();i++) {
                var target=new LinkedHashMap<>(f.targets.get(i));target.put("tableItems",fields);
                target.put("sourceConstraints",List.of(Map.of("name","PK_named_but_unique","type","U","columns",List.of("f1","f2"))));
                target.put("partitionPlan",Map.of("enabled",true,"strategy","YEAR","field","occurred_at","startYear","2025","years",2));
                f.targets.set(i,target);
            }
            var result=(Map<?,?>)f.run();
            for(Object row:(List<?>)result.get("results")) {
                String ddl=((Map<?,?>)row).get("ddl").toString();
                assertTrue(ddl.contains("PARTITION BY RANGE"));assertTrue(ddl.contains("2027-01-01"));
                assertTrue(ddl.contains("CONSTRAINT UK_"));assertTrue(ddl.contains("UNIQUE (f1, f2)"));assertFalse(ddl.contains("PRIMARY KEY"));
            }
            assertEquals(1,f.counter.reads);assertEquals(0,f.counter.writes);
        }
    }

    @Test void partitionProbeReadsOneSourceAndOneAuthorizedDatasourceBatchAndRejectsEveryHiddenId() throws Exception {
        Fixture f=new Fixture(20);
        f.jdbc.execute("create table db_table_t(tid varchar(64), tenant_id varchar(32), datasource_id varchar(64), table_name varchar(100), is_del int)");
        f.jdbc.update("insert into db_table_t values('source','police','db-0','source_t',0)");
        var ids=java.util.stream.IntStream.range(0,20).mapToObj(i->"db-"+i).toList();
        var body=Map.<String,Object>of("partitionProbe",true,"canvas",true,"sourceTableId","source","targetDbIds",ids);
        var response=assertInstanceOf(Map.class,f.run(body));
        assertEquals(20,((Map<?,?>)response.get("targetSupport")).size());
        assertEquals(2,f.counter.reads);assertEquals(0,f.counter.writes);
        f.jdbc.update("update db_datasource_t set db_type='hive',pool_cfg='{}' where tid='db-1'");
        var ordinaryHive=assertInstanceOf(Map.class,f.run(body));
        assertEquals(false,((Map<?,?>)ordinaryHive.get("targetSupport")).get("db-1"));
        f.jdbc.update("update db_datasource_t set pool_cfg='{\"usePlatformHiveConfig\":true}' where tid='db-1'");
        var managedHive=assertInstanceOf(Map.class,f.run(body));
        assertEquals(true,((Map<?,?>)managedHive.get("targetSupport")).get("db-1"));
        assertEquals(2,f.counter.reads);assertEquals(0,f.counter.writes);
        f.scope.deniedId="db-19";
        assertInstanceOf(Map.class,f.run(body));assertEquals(2,f.counter.reads);assertEquals(0,f.counter.writes);
        f.scope.deniedId=null;f.jdbc.update("update db_datasource_t set tenant_id='other' where tid='db-19'");
        assertInstanceOf(ExitValue.class,f.run(body));assertEquals(2,f.counter.reads);assertEquals(0,f.counter.writes);
        f.jdbc.update("update db_table_t set tenant_id='other' where tid='source'");
        assertInstanceOf(ExitValue.class,f.run(body));assertEquals(1,f.counter.reads);assertEquals(0,f.counter.writes);
    }

    @Test void twentyOracleTargetsWithTwentyOrHundredUnicodeFieldsStillUseOneAuthorizedRead()throws Exception {
        for(int count:List.of(20,100)) {
            Fixture f=new Fixture(20);f.jdbc.update("update db_datasource_t set db_type='oceanbaseoracle',username='ODS_OWNER'");
            f.ddlGenerator.expectedColumns=count;
            var fields=new ArrayList<Map<String,Object>>();
            for(int i=0;i<count;i++)fields.add(Map.of("columnName","f"+i,"dataType","varchar","sourceDatabaseType","postgresql","sourceColumnType","VARCHAR(50)","length",50));
            for(int i=0;i<f.targets.size();i++){var target=new LinkedHashMap<>(f.targets.get(i));target.put("tableItems",fields);f.targets.set(i,target);}
            var result=(Map<?,?>)f.run();
            for(var item:(List<Map<String,Object>>)result.get("results"))assertEquals(count,item.get("ddl").toString().split("50 CHAR",-1).length-1);
            assertEquals(1,f.counter.reads);assertEquals(0,f.counter.writes);assertEquals(20,f.ddlGenerator.calls);
        }
    }

    @Test
    void sourceTextConversionNeedsNoTenantSessionOrDatabaseObjectsForTwentyAndHundredFields() throws Exception {
        String script = CanonicalMagicSources.byId(SOURCE_ID);
        script = script.substring(0,script.indexOf("var textOf ="))
                .replaceAll("(?m)^import (?!com\\.linewell\\.dataelement\\.metautil\\.model\\.|java\\.util\\.)[^\\n]*\\n", "");
        for (int count : List.of(20,100)) {
            String fields = java.util.stream.IntStream.range(0,count)
                    .mapToObj(index -> "f"+index+" varchar(36) DEFAULT ('Y'::varchar)")
                    .collect(java.util.stream.Collectors.joining(","));
            Object result = MagicScript.create(script,null).execute(new MagicScriptContext(Map.of(
                    "body",Map.of("convertSourceDdl",true,"sourceDdl","CREATE TABLE people("+fields+")",
                            "sourceDatabaseType","postgresql","targetDatabaseType","oracle"),
                    "portableDdl",new com.linewell.dataelement.metautil.ddl.PortableDdlConverter(),
                    "metadataExplorerService",new PureGenerator())));
            var converted = assertInstanceOf(Map.class,result);
            assertEquals(count,((List<?>) converted.get("tableItems")).size());
            assertTrue(converted.get("ddl").toString().contains("DEFAULT 'Y'"));
            assertFalse(converted.get("ddl").toString().contains("::"));
        }
    }

    public static class PureGenerator {
        public String generateCreateTableDdl(DataSourceConfig config,String table,List<ColumnInfo> columns,String comment) {
            return new OracleMetadataExplorer().generateCreateTableDdl(config,table,columns,comment);
        }
    }

    @Test
    void postgresLiteralDefaultIsNormalizedBeforeCallingTheJavaGenerator() throws Exception {
        for (String dialect : List.of("oceanbaseoracle", "oracle", "mysql", "hive", "postgresql")) {
            Fixture f = new Fixture(1);
            f.jdbc.update("update db_datasource_t set db_type=?,username='ODS_OWNER' where tid='db-0'", dialect);
            var target = new LinkedHashMap<>(f.targets.getFirst());
            target.put("tableItems", List.of(Map.of("columnName", "enabled", "dataType", "varchar",
                    "columnType", "varchar(1)", "length", 1, "defaultValue", "'Y'::character varying")));
            f.targets.set(0, target);
            var result = assertInstanceOf(Map.class, f.run());
            assertEquals(dialect.equals("hive") ? null : "'Y'", f.ddlGenerator.lastColumns.getFirst().getDefaultValue());
            String ddl = ((Map<?, ?>) ((List<?>) result.get("results")).getFirst()).get("ddl").toString();
            assertFalse(ddl.contains("::"));
            if (dialect.equals("hive")) assertFalse(ddl.contains("DEFAULT"));
            else assertTrue(ddl.contains("DEFAULT 'Y'"));
            assertEquals(1, f.counter.reads); assertEquals(0, f.counter.writes);
        }
    }

    @Test
    void changingTextToVarcharAndEditingLengthUpdatesTheNativeDefinition() throws Exception {
        Fixture f = new Fixture(1);
        f.jdbc.update("update db_datasource_t set db_type='oceanbaseoracle',username='ODS_OWNER' where tid='db-0'");
        var target = new LinkedHashMap<>(f.targets.getFirst());
        target.put("tableItems", List.of(Map.of("columnName", "UNID", "dataType", "varchar",
                "columnType", "text", "length", 64, "primaryKey", 1)));
        f.targets.set(0, target);
        var result = assertInstanceOf(Map.class, f.run());
        String ddl = ((Map<?, ?>) ((List<?>) result.get("results")).getFirst()).get("ddl").toString();
        assertTrue(ddl.contains("VARCHAR2(64 CHAR)") || ddl.contains("VARCHAR2 (64 CHAR)"));
        assertFalse(ddl.contains("CLOB"));
        assertEquals("VARCHAR2(64 CHAR)", f.ddlGenerator.lastColumns.getFirst().getColumnType());
        target.put("tableItems", List.of(Map.of("columnName", "UNID", "dataType", "varchar",
                "columnType", "varchar(32)", "length", 128, "primaryKey", 1)));
        f.run(); assertEquals("VARCHAR2(128 CHAR)", f.ddlGenerator.lastColumns.getFirst().getColumnType());
    }

    @Test
    void previewShowsLobKeyWarningsAndConvertsCommonClockDefaultsWithoutBlockingEditing() throws Exception {
        for (boolean lobKey : List.of(true, false)) {
            Fixture f = new Fixture(1);
            f.jdbc.update("update db_datasource_t set db_type='oceanbaseoracle',username='ODS_OWNER' where tid='db-0'");
            var target = new LinkedHashMap<>(f.targets.getFirst());
            target.put("tableItems", List.of(lobKey
                    ? Map.of("columnName", "UNID", "dataType", "text", "columnType", "text", "length", 0, "primaryKey", 1)
                    : Map.of("columnName", "created_at", "dataType", "timestamp", "defaultValue", "now()::timestamp")));
            f.targets.set(0,target);
            Map<?,?> result = assertInstanceOf(Map.class,f.run());
            var row = (Map<?,?>) ((List<?>) result.get("results")).getFirst();
            if (lobKey) assertFalse(((List<?>) row.get("warnings")).isEmpty());
            else assertTrue(row.get("ddl").toString().contains("CURRENT_TIMESTAMP"));
            assertEquals(1,f.ddlGenerator.calls); assertEquals(0,f.counter.writes);
        }
    }

    @Test
    void castsAreConvertedOrRetainedWithWarningsInsteadOfRejectingTheCompletePreview() throws Exception {
        for (String value : List.of("'2026-10-12'::date", "1.4::integer", "TRUE::boolean")) {
            Fixture f=new Fixture(1);
            f.jdbc.update("update db_datasource_t set db_type='oceanbaseoracle',username='ODS_OWNER' where tid='db-0'");
            var target=new LinkedHashMap<>(f.targets.getFirst());
            target.put("tableItems",List.of(Map.of("columnName","source_default","dataType","varchar","columnType","varchar(64)","length",64,"defaultValue",value)));
            f.targets.set(0,target);
            Map<?,?> result=assertInstanceOf(Map.class,f.run());
            var row=(Map<?,?>)((List<?>)result.get("results")).getFirst();
            if (value.equals("1.4::integer")) assertFalse(((List<?>)row.get("warnings")).isEmpty());
            else assertFalse(row.get("ddl").toString().contains("::"));
            assertEquals(1,f.ddlGenerator.calls); assertEquals(0,f.counter.writes);
        }
    }

    @Test
    void temporalDdlUsesTheDeclaredNativeTypeAcrossDestinations() throws Exception {
        for (String dialect : List.of("mysql", "oceanbasemysql", "oracle", "oceanbaseoracle", "postgresql", "hive")) {
            String timeType = dialect.contains("oracle") ? "date"
                    : dialect.equals("hive") || dialect.equals("postgresql") ? "timestamp" : "datetime";
            for (String type : List.of("date", timeType)) {
                Fixture fixture = new Fixture(1);
                fixture.targetNames("ODS_TEMPORAL");
                fixture.jdbc.update("update db_datasource_t set db_type=?,username='ODS_OWNER' where tid='db-0'", dialect);
                Map<String, Object> target = new LinkedHashMap<>(fixture.targets.getFirst());
                target.put("tableItems", List.of(Map.of("columnName", "occurred_at", "dataType", type,
                        "columnType", type, "length", 0, "columnComment", "事件时间")));
                fixture.targets.set(0, target);
                Object raw = fixture.run();
                Map<?, ?> response = assertInstanceOf(Map.class, raw, dialect + "/" + type + ": " + raw);
                String ddl = ((Map<?, ?>) ((List<?>) response.get("results")).getFirst()).get("ddl").toString().toUpperCase();
                assertTrue(ddl.contains(type.toUpperCase()), ddl);
                assertFalse(ddl.contains("VARCHAR"), ddl);
                assertEquals(1, fixture.counter.reads);
            }
        }
    }

    @Test
    void hiveRowTerminatorSurvivesBatchGenerationWithTwentyAndHundredFieldsAndScopedTargets() throws Exception {
        for(int count:List.of(20,100)) {
            Fixture fixture=new Fixture(20);
            fixture.jdbc.update("update db_datasource_t set db_type='hive'");
            fixture.ddlGenerator.expectedColumns=count;
            var fields=new ArrayList<Map<String,Object>>();
            for(int i=0;i<count;i++)fields.add(Map.of("columnName","f"+i,"dataType","varchar","length",64));
            for(int i=0;i<fixture.targets.size();i++) {
                var target=new LinkedHashMap<>(fixture.targets.get(i));target.put("tableItems",fields);fixture.targets.set(i,target);
            }
            var result=assertInstanceOf(Map.class,fixture.run());
            for(Object raw:(List<?>)result.get("results")) {
                String ddl=((Map<?,?>)raw).get("ddl").toString();
                assertTrue(ddl.contains("LINES TERMINATED BY '\\002\\002\\002\\003\\003\\003\\n'"),ddl);
                assertEquals(ddl.indexOf("ROW FORMAT"),ddl.lastIndexOf("ROW FORMAT"));
            }
            assertEquals(1,fixture.counter.reads);assertEquals(0,fixture.counter.writes);
            fixture.scope.deniedId="db-19";
            assertInstanceOf(Map.class,fixture.run(),"Current-tenant targets ignore the metadata menu role");
            assertEquals(1,fixture.counter.reads);assertEquals(0,fixture.counter.writes);
        }
    }

    @Test
    void everyHivePreviewIncludesTheProductionRowTerminatorWithoutExtraMetadataReads() throws Exception {
        for (String configuration : List.of("{\"hiveConnectionMode\":\"huawei-mrs\"}",
                "{\"hiveProfile\":\"default\"}", "{\"usePlatformHiveConfig\":true}")) {
            Fixture fixture = new Fixture(2);
            fixture.jdbc.update("update db_datasource_t set db_type='hive',pool_cfg=? where tid='db-1'", configuration);
            Map<?, ?> response = assertInstanceOf(Map.class, fixture.run());
            List<?> results = assertInstanceOf(List.class, response.get("results"));
            String hiveDdl = ((Map<?, ?>) results.get(1)).get("ddl").toString();
            assertTrue(hiveDdl.contains("ROW FORMAT DELIMITED"), hiveDdl);
            assertTrue(hiveDdl.contains("FIELDS TERMINATED BY '\\001'"), hiveDdl);
            assertTrue(hiveDdl.contains("LINES TERMINATED BY '\\002\\002\\002\\003\\003\\003\\n'"), hiveDdl);
            assertTrue(hiveDdl.contains("STORED AS TEXTFILE"), hiveDdl);
            assertFalse(hiveDdl.contains("\u0001"), "The preview must retain a visible octal escape");
            assertFalse(((Map<?, ?>) results.getFirst()).get("ddl").toString().contains("TEXTFILE"));
            assertEquals(1, fixture.counter.reads);
            assertEquals(0, fixture.counter.writes);
        }
        Fixture plainHive = new Fixture(1);
        plainHive.jdbc.update("update db_datasource_t set db_type='hive',pool_cfg='{\"hiveConnectionMode\":\"open-source\"}'");
        Map<?, ?> response = assertInstanceOf(Map.class, plainHive.run());
        String ddl = ((Map<?, ?>) ((List<?>) response.get("results")).getFirst()).get("ddl").toString();
        assertTrue(ddl.contains("TEXTFILE"), "The requested storage policy applies to every Hive target");
        assertTrue(ddl.contains("LINES TERMINATED BY '\\002\\002\\002\\003\\003\\003\\n'"),ddl);
    }

    @Test
    void twentyTargetsUseOneScopedDatasourceRead() throws Exception {
        Fixture fixture = new Fixture(20);

        Map<?, ?> result = assertInstanceOf(Map.class, fixture.run());

        assertEquals(true, result.get("batchGenerate"));
        assertEquals(20, ((Collection<?>) result.get("results")).size());
        assertEquals(1, fixture.counter.reads);
        assertEquals(0, fixture.counter.writes);
        assertEquals(20, fixture.ddlGenerator.calls);
    }

    @Test
    void menuRoleDoesNotRejectTheCurrentTenantTwentyTargetBatch() throws Exception {
        Fixture fixture = new Fixture(20);
        fixture.scope.deniedId = "db-19";

        assertInstanceOf(Map.class, fixture.run());

        assertEquals(1, fixture.counter.reads);
        assertEquals(0, fixture.counter.writes);
        assertEquals(20, fixture.ddlGenerator.calls);
    }

    @Test
    void aForeignTenantDatasourceRejectsTheWholeTwentyTargetBatch() throws Exception {
        Fixture fixture = new Fixture(20);
        fixture.jdbc.update("update db_datasource_t set tenant_id='other' where tid='db-19'");

        assertInstanceOf(ExitValue.class, fixture.run());

        assertEquals(1, fixture.counter.reads);
        assertEquals(0, fixture.counter.writes);
        assertEquals(0, fixture.ddlGenerator.calls);
    }

    @Test
    void oneHundredTargetsAreRejectedBeforeDatabaseAccess() throws Exception {
        Fixture fixture = new Fixture(100);

        assertInstanceOf(ExitValue.class, fixture.run());

        assertEquals(0, fixture.counter.reads);
        assertEquals(0, fixture.counter.writes);
        assertEquals(0, fixture.ddlGenerator.calls);
    }

    @Test
    void oraclePreservesConfirmedNamesWhileHiveLowercasesThem() throws Exception {
        for (String mode : List.of("oracle", "oceanbaseoracle")) {
          for (String name : List.of("ODS_T_SJYCC_CKB", "ODS_CONFIRMED_CUSTOM")) {
            Fixture fixture = new Fixture(2);
            fixture.jdbc.update("update db_datasource_t set db_type=?,username='OB_OWNER' where tid='db-0'", mode);
            fixture.jdbc.update("update db_datasource_t set db_type='hive' where tid='db-1'");
            fixture.targetNames(name);

            Map<?, ?> response = assertInstanceOf(Map.class, fixture.run());
            List<?> results = assertInstanceOf(List.class, response.get("results"));
            for (int index = 0; index < results.size(); index++) {
                Object value = results.get(index);
                Map<?, ?> result = assertInstanceOf(Map.class, value);
                String expectedName = index == 1 ? name.toLowerCase(java.util.Locale.ROOT) : name;
                assertEquals(expectedName, result.get("requestedTableName"));
                assertEquals(expectedName, result.get("tableName"));
                assertTrue(String.valueOf(result.get("ddl")).contains(expectedName));
            }
            assertEquals(1, fixture.counter.reads);
            assertEquals(0, fixture.counter.writes);
          }
        }
    }

    @Test
    void previewRemainsEditableWhileExecutionOwnsTargetScopeValidation() throws Exception {
        Fixture qualified = new Fixture(1);
        qualified.targetNames("ODS_TC_RKXT.T_SJYCC_CKB");
        assertInstanceOf(Map.class, qualified.run());
        assertEquals(0, qualified.counter.writes);

        Fixture renamed = new Fixture(1);
        renamed.ddlGenerator.rename = true;
        assertInstanceOf(Map.class, renamed.run());
        assertEquals(0, renamed.counter.writes);
    }

    @Test
    void hiveOnlyLowercasesTableIdentityWhilePreservingFieldAndCommentText() throws Exception {
        Fixture fixture = new Fixture(1);
        fixture.jdbc.update("update db_datasource_t set db_type='hive' where tid='db-0'");
        fixture.targets.set(0, Map.of("propList", Map.of("dbId", "db-0", "dbType", "mysql",
                        "tableName", "ODS_Mixed_TABLE", "tableNameCn", "Keep_UPPER_Comment"),
                "tableItems", List.of(Map.of("columnName", "Mixed_FIELD", "dataType", "varchar",
                        "length", 32, "columnComment", "Keep_UPPER_FieldComment"))));
        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run());
        Map<?, ?> result = (Map<?, ?>) ((List<?>) response.get("results")).getFirst();
        assertEquals("ods_mixed_table", result.get("requestedTableName"));
        assertEquals("ods_mixed_table", result.get("tableName"));
        String ddl = result.get("ddl").toString();
        assertTrue(ddl.contains("`ods_mixed_table`"));
        assertTrue(ddl.contains("Mixed_FIELD"));
        assertTrue(ddl.contains("Keep_UPPER_Comment"));
        assertTrue(ddl.contains("Keep_UPPER_FieldComment"));
        assertEquals(1, fixture.counter.reads);
        assertEquals(0, fixture.counter.writes);
    }

    private static final class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final QueryCounter counter = new QueryCounter();
        final ScopeRuntime scope = new ScopeRuntime();
        final DdlGenerator ddlGenerator = new DdlGenerator();
        final CanvasTableDdl ddlParser = new CanvasTableDdl();
        final List<Map<String, Object>> targets = new ArrayList<>();

        Fixture(int targetCount) {
            var source = new DriverManagerDataSource("jdbc:h2:mem:ddl_batch_" + UUID.randomUUID()
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
            db.setSqlInterceptors(List.of(counter));
            db.setNamedTableInterceptors(List.of());
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    create table db_datasource_t(tid varchar(64),tenant_id varchar(32),is_del int,
                        db_type varchar(32),db_name varchar(100),pool_cfg varchar(1000),username varchar(100))
                    """);
            for (int i = 0; i < targetCount; i++) {
                String dbId = "db-" + i;
                jdbc.update("insert into db_datasource_t(tid,tenant_id,is_del,db_type,db_name,pool_cfg) "
                        + "values(?,'police',0,'mysql',?,'{}')", dbId, "ods_" + i);
                targets.add(Map.of(
                        "propList", Map.of("dbId", dbId, "tableName", "ods_person_" + i,
                                "tableNameCn", "人员表"),
                        "tableItems", List.of(Map.of("columnName", "person_id", "columnComment", "人员标识",
                                "dataType", "varchar", "columnType", "varchar(32)", "length", 32,
                                "nullable", 0, "primaryKey", 1))));
            }
        }

        Object run() throws Exception {
            return run(Map.of("batchGenerate", true, "canvas", true, "targets", targets));
        }
        Object run(Map<String,Object> body) throws Exception {
            String script = CanonicalMagicSources.byId(SOURCE_ID)
                    .replaceAll("(?m)^import (?!com\\.linewell\\.dataelement\\.metautil\\.model\\.|cn\\.hutool\\.json\\.JSONUtil|java\\.util\\.Map|java\\.util\\.List)[^\\r\\n]*\\r?\\n", "");
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("body", body);
            inputs.put("db", db);
            inputs.put("tenantRuntime", scope);
            inputs.put("dataScope", scope);
            inputs.put("metadataExplorerService", ddlGenerator);
            inputs.put("canvasTableDdl", ddlParser);
            inputs.put("portableDdl", new com.linewell.dataelement.metautil.ddl.PortableDdlConverter());
            inputs.put("tablePartitions", new com.linewell.dataelement.metautil.ddl.TablePartitions());
            inputs.put("jsons", new JsonModule());
            counter.reads = 0;
            counter.writes = 0;
            return MagicScript.create(script, null)
                    .execute(new MagicScriptContext(inputs));
        }

        void targetNames(String name) {
            for (int index = 0; index < targets.size(); index++) {
                var target = new LinkedHashMap<>(targets.get(index));
                @SuppressWarnings("unchecked")
                var props = new LinkedHashMap<>((Map<String, Object>) target.get("propList"));
                props.put("tableName", name);
                target.put("propList", props);
                targets.set(index, target);
            }
        }
    }

    public static final class ScopeRuntime {
        String deniedId;
        public String id() { return "police"; }
        public List<Map<String, Object>> visibleDataSourcesFor(String resource,
                                                                Collection<Map<String, Object>> rows) {
            assertEquals("MENU:2081000000000000002", resource);
            return rows.stream().filter(row -> !String.valueOf(row.get("tid")).equals(deniedId)).toList();
        }
    }

    public static final class DdlGenerator {
        int calls;
        int expectedColumns=1;
        boolean rename;
        List<ColumnInfo> lastColumns;
        public String generateCreateTableDdl(DataSourceConfig config, String tableName,
                                             List<ColumnInfo> columns, String tableComment) {
            calls++;
            lastColumns = List.copyOf(columns);
            assertEquals(expectedColumns, columns.size());
            String name = rename ? "UNEXPECTED_TABLE" : tableName;
            return switch (config.getDatabaseType().getCode()) {
                case "oracle" -> new OracleMetadataExplorer().generateCreateTableDdl(config, name, columns, tableComment);
                case "oceanbaseoracle", "oceanbasemysql" -> new OceanBaseMetadataExplorer().generateCreateTableDdl(config, name, columns, tableComment);
                case "postgresql" -> new PostgreSqlMetadataExplorer().generateCreateTableDdl(config, name, columns, tableComment);
                case "hive" -> new HiveMetadataExplorer().generateCreateTableDdl(config, name, columns, tableComment);
                default -> new MySqlMetadataExplorer().generateCreateTableDdl(config, name, columns, tableComment);
            };
        }
    }

    private static final class QueryCounter implements SQLInterceptor {
        int reads;
        int writes;
        @Override
        public void preHandle(BoundSql sql, RequestEntity request) {
            if (sql.getSql().stripLeading().toLowerCase().startsWith("select")) { reads++; }
            else { writes++; }
        }
    }
}
