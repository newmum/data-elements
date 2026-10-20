package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.metautil.ddl.CanvasTableDdl;
import com.linewell.dataelement.metautil.ddl.TablePartitions;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

/** Runs the canonical createTable Magic source through its read-only batch validation path. */
class CanvasCreateTableBatchValidationMagicTest {
    private static final String SOURCE_ID = "a0b5bba38fbf428c8e3646864d83d56b";
    private SQLModule db;
    private QueryCounter counter;
    private Scope scope;
    private JdbcTemplate jdbc;
    private PhysicalMetadata physical;
    private CountingPartitions partitions;

    @Test
    void hiveMaterializationExecutesTheGeneratedMultiCharacterTerminatorVerbatim() throws Exception {
        physical.allowCreate=true;
        var column=new ColumnInfo();column.setColumnName("id");column.setColumnType("STRING");
        String ddl=com.linewell.dataelement.metautil.ddl.HiveTableDdl.generate("ods_lines",List.of(column),"换行验证");
        assertTrue(ddl.contains("LINES TERMINATED BY '\\002\\002\\002\\003\\003\\003\\n'"));
        var result=assertInstanceOf(Map.class,run(Map.of("canvas",true,"dbId","target-db",
                "tableName","ods_lines","ddl",ddl,"partitionPlan",Map.of("enabled",false))));
        assertEquals(true,result.get("created"));
        assertEquals(List.of(ddl),physical.createdDdls);
        assertEquals(0,partitions.calls);assertEquals(0,counter.writes);
    }

    @Test
    void nativeDatabaseFailureIsReturnedWithItsReasonAndSensitiveValuesRemoved() throws Exception {
        physical.allowCreate=true;
        physical.createFailure=new java.sql.SQLException("Hive ParseException: unexpected token; password='private-fixture-value'", "42000",40000);
        var result=assertInstanceOf(ExitValue.class,run(Map.of("canvas",true,"dbId","target-db",
                "tableName","ods_plain","ddl","CREATE TABLE ???")));
        assertEquals(500,result.getValues()[0]);
        String message=result.getValues()[1].toString();
        assertTrue(message.contains("Hive ParseException: unexpected token"));
        assertFalse(message.contains("private-fixture-value"));
        assertEquals(0,partitions.calls); assertEquals(0,counter.writes);
    }

    @Test
    void uncheckedHiveSingleAndBatchValidationIgnoreAbsentAndStalePartitionSettings() throws Exception {
        for (Map<String,Object> plan : List.of(Map.<String,Object>of(),
                Map.<String,Object>of("enabled",false,"strategy","LIST","field","removed_field"))) {
            var target = new LinkedHashMap<String,Object>(Map.of("dbId","target-db","tableName","ods_plain",
                    "ddl","CREATE TABLE ods_plain(id STRING)","partitionPlan",plan));
            var batch = assertInstanceOf(Map.class,run(Map.of("canvas",true,"validateOnly",true,
                    "batchValidateOnly",true,"targets",List.of(target))));
            assertEquals(false,batch.get("sideEffectsApplied"));
            target.put("canvas",true); target.put("validateOnly",true);
            assertEquals(true,assertInstanceOf(Map.class,run(target)).get("valid"));
        }
        assertEquals(0,partitions.calls); assertEquals(0,physical.calls); assertEquals(0,counter.writes);
    }

    @Test
    void uncheckedHiveCreatesAnOrdinaryTableWithoutPartitionCatalogAccess() throws Exception {
        physical.allowCreate = true;
        var result = assertInstanceOf(Map.class,run(Map.of("canvas",true,"dbId","target-db",
                "tableName","ods_plain","ddl","CREATE TABLE ods_plain(id STRING)",
                "partitionPlan",Map.of("enabled",false,"strategy","LIST","field","missing"))));
        assertEquals(true,result.get("created"));
        assertEquals(1,physical.createdDdls.size());
        assertTrue(physical.createdDdls.getFirst().contains("ods_plain"));
        assertFalse(physical.createdDdls.getFirst().toUpperCase().contains("PARTITION"));
        assertEquals(0,partitions.calls); assertEquals(0,physical.calls); assertEquals(0,counter.writes);
    }

    @Test
    void nativePartitionDdlAndManualSqlRemainVerbatimWithoutLocalSyntaxValidation() throws Exception {
        scope.registered = Map.of("dbType","oracle","database","fixture","host","fixture.invalid","username","APP");
        jdbc.update("update db_datasource_t set db_type='oracle',username='APP',pool_cfg=?",
                "{\"host\":\"fixture.invalid\",\"database\":\"fixture\"}");
        String ddl = "CREATE TABLE ods_native(region_code VARCHAR2(6)) PARTITION BY LIST(region_code) "
                + "(PARTITION p_fz VALUES ('350100')); COMMENT ON TABLE ods_native IS '区域表'";
        var target = new LinkedHashMap<String,Object>(Map.of("dbId","target-db","tableName","ods_native",
                "ddl",ddl,"partitionPlan",Map.of("enabled",false)));
        var batch = assertInstanceOf(Map.class,run(Map.of("canvas",true,"validateOnly",true,
                "batchValidateOnly",true,"targets",List.of(target))));
        assertTrue(((Map<?,?>)((List<?>)batch.get("results")).getFirst()).get("ddl").toString().contains("PARTITION BY LIST"));
        target.put("canvas",true); target.put("validateOnly",true);
        assertTrue(assertInstanceOf(Map.class,run(target)).get("ddl").toString().contains("350100"));
        for (String edited : List.of(ddl + "; CREATE INDEX native_idx ON ods_native(region_code) GLOBAL",
                "CREATE TABLE ods_native(id NUMBER) vendor_storage_option", "CREATE TABLE ods_native AS SELECT 1 FROM dual",
                "CREATE TABLE ods_native(id CLOB PRIMARY KEY)","invalid SQL for the database to diagnose")) {
            target.put("ddl",edited);
            var single = assertInstanceOf(Map.class,run(target));
            assertEquals(edited,single.get("ddl")); assertEquals(false,single.get("syntaxChecked"));
            var batchTarget = new LinkedHashMap<>(target); batchTarget.remove("canvas"); batchTarget.remove("validateOnly");
            var response=assertInstanceOf(Map.class,run(Map.of("canvas",true,"validateOnly",true,
                    "batchValidateOnly",true,"targets",List.of(batchTarget))));
            assertEquals(edited,((Map<?,?>)((List<?>)response.get("results")).getFirst()).get("ddl"));
        }
        assertEquals(0,partitions.calls); assertEquals(0,physical.calls); assertEquals(0,counter.writes);
    }

    @Test
    void submittedSqlDoesNotRequireMatchingPartitionOrKeyConfiguration() throws Exception {
        var target = Map.<String,Object>of("dbId","target-db","tableName","ods_plain",
                "ddl","CREATE TABLE ods_plain(id STRING)","partitionPlan",Map.of("enabled",true));
        var single = new LinkedHashMap<>(target); single.put("canvas",true); single.put("validateOnly",true);
        assertEquals(false,assertInstanceOf(Map.class,run(single)).get("syntaxChecked"));
        assertInstanceOf(Map.class,run(Map.of("canvas",true,"validateOnly",true,
                "batchValidateOnly",true,"targets",List.of(target))));
        assertEquals(0,partitions.calls); assertEquals(0,physical.calls); assertEquals(0,counter.writes);
    }

    @Test
    void partitionedSingleAndBatchValidationPreserveFullDdlWithoutMutatingParserResult() throws Exception {
        var partitions = new com.linewell.dataelement.metautil.ddl.TablePartitions();
        var config = new DataSourceConfig();
        config.setDatabaseType(com.linewell.dataelement.metautil.model.enums.DatabaseType.HIVE);
        config.setDatabase("warehouse");
        var column = new ColumnInfo(); column.setColumnName("occurred_at"); column.setColumnType("timestamp");
        var plan = partitions.normalize(new LinkedHashMap<>(Map.of("enabled",true,"strategy","MONTH",
                "field","occurred_at","startMonth","2026-10","months",2)),List.of(column),config.getDatabaseType());
        String ddl = partitions.apply("CREATE TABLE ods_partition_probe(occurred_at TIMESTAMP)",plan,config);
        var target = new LinkedHashMap<String,Object>(Map.of("dbId","target-db","tableName","ods_partition_probe",
                "ddl",ddl,"partitionPlan",plan,"tableItems",List.of(Map.of("columnName","occurred_at","dataType","timestamp"))));
        var batch = assertInstanceOf(Map.class,run(Map.of("canvas",true,"validateOnly",true,"batchValidateOnly",true,"targets",List.of(target))));
        assertEquals(ddl,((Map<?,?>)((List<?>)batch.get("results")).getFirst()).get("ddl"));
        assertEquals(false,batch.get("sideEffectsApplied"));
        var singleRequest = new LinkedHashMap<>(target); singleRequest.put("canvas",true); singleRequest.put("validateOnly",true);
        var single = assertInstanceOf(Map.class,run(singleRequest));
        assertEquals(ddl,single.get("ddl")); assertEquals(false,single.get("sideEffectsApplied"));
        assertEquals(0,counter.writes); assertEquals(0,physical.calls);
    }

    @Test
    void uppercaseRequestedNameUsesCanonicalLowercaseBatchKey() throws Exception {
        jdbc.update("update db_datasource_t set db_type='mysql',username='fixture',pool_cfg=?",
                "{\"host\":\"fixture.invalid\",\"database\":\"warehouse\"}");
        ColumnInfo column = new ColumnInfo(); column.setColumnName("ID"); column.setDataType("varchar");
        column.setLength(64L); column.setNullable(true); physical.columns = List.of(column);
        Map<?,?> result = assertInstanceOf(Map.class, run(Map.of("canvas",true,"validateOnly",true,"batchReuseValidation",true,
                "targets",List.of(Map.of("dbId","target-db","tableName","ODS_VEHICLE_LOGOUT",
                        "ddl","CREATE TABLE ODS_VEHICLE_LOGOUT(ID VARCHAR(32))",
                        "tableItems",List.of(Map.of("columnName","ID","dataType","varchar","length",32)))))));
        assertEquals(true, ((Map<?,?>)((List<?>)result.get("results")).getFirst()).get("physicalTableReused"));
        assertEquals(1, physical.calls); assertEquals(1, counter.queries); assertEquals(0, counter.writes);
    }

    @Test
    void hiveTextWideningKeepsActualTypesAndWarnsForTwentyAndHundredFields() throws Exception {
        for (int count : List.of(20, 100)) {
            List<Map<String,Object>> fields = new ArrayList<>(); physical.columns = new ArrayList<>();
            for (int i=0;i<count;i++) {
                String name="amount_"+i;
                fields.add(Map.of("columnName",name,"dataType","decimal","precisionLength",20,"scale",6));
                ColumnInfo column=new ColumnInfo(); column.setColumnName(name); column.setDataType("string");
                physical.columns.add(column);
            }
            physical.calls=0; counter.queries=0;
            var result=assertInstanceOf(Map.class,run(Map.of("canvas",true,"validateOnly",true,"batchReuseValidation",true,
                    "targets",List.of(Map.of("dbId","target-db","tableName","ods_existing",
                            "ddl","CREATE TABLE ods_existing(amount_0 DECIMAL(20,6))","tableItems",fields,
                            "partitionPlan",Map.of("enabled",false,"strategy","LIST","field","missing"))))));
            var table=(Map<?,?>)((List<?>)result.get("results")).getFirst();
            assertEquals(count,((List<?>)table.get("compatibilityWarnings")).size());
            for(Object field:(List<?>)table.get("tableItems")) assertEquals("string",((Map<?,?>)field).get("dataType"));
            assertEquals(1,physical.calls); assertEquals(1,counter.queries); assertEquals(0,counter.writes);
            assertEquals(0,partitions.calls);
        }
    }

    @Test
    void hiveDriverLabelsHeadersAndRepeatedPartitionSectionAreNormalized() throws Exception {
        physical.rawRows=List.of(Map.of(" result.COL_NAME ","# col_name","result.DATA_TYPE","data_type"),
                Map.of(" result.COL_NAME "," id ","result.DATA_TYPE"," STRING "),
                Map.of(" result.COL_NAME ","# Partition Information","result.DATA_TYPE",""),
                Map.of(" result.COL_NAME "," id ","result.DATA_TYPE"," STRING "));
        var result=assertInstanceOf(Map.class,run(Map.of("canvas",true,"reuseExistingOnly",true,"dbId","target-db",
                "tableName","ods_existing","ddl","CREATE TABLE ods_existing(id STRING)",
                "tableItems",List.of(Map.of("columnName","id","dataType","decimal")))));
        assertEquals(1,((List<?>)result.get("tableItems")).size());
        assertEquals(1,physical.calls); assertEquals(0,counter.writes);
    }

    @Test
    void emptyMetadataAndFiniteVarcharAreStillRejected() throws Exception {
        var body=Map.<String,Object>of("canvas",true,"reuseExistingOnly",true,"dbId","target-db",
                "tableName","ods_existing","ddl","CREATE TABLE ods_existing(id STRING)",
                "tableItems",List.of(Map.of("columnName","id","dataType","decimal")));
        assertInstanceOf(ExitValue.class,run(body));
        ColumnInfo column=new ColumnInfo(); column.setColumnName("id"); column.setDataType("varchar(4)");
        physical.columns=List.of(column);
        assertInstanceOf(ExitValue.class,run(body)); assertEquals(0,counter.writes);
    }

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:ddl_validation_" + UUID.randomUUID()
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
        counter = new QueryCounter();
        db.setSqlInterceptors(List.of(counter));
        db.setNamedTableInterceptors(List.of());
        db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
        db.setRowMapColumnMapper(value -> value);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(64),is_del int,db_type varchar(32),username varchar(64),pool_cfg varchar(512))");
        jdbc.update("insert into db_datasource_t values(?,?,?,?,?,?)", "target-db", "police", 0, "hive", null,
                "{\"database\":\"warehouse\",\"metadataAccessMode\":\"server-managed-mrs\"}");
        scope = new Scope();
        physical = new PhysicalMetadata();
        partitions = new CountingPartitions();
    }

    @Test
    void existingHiveWithTwentyAndHundredFieldsUsesOneReadAndNoDdl() throws Exception {
        for (int size : List.of(20, 100)) {
            counter.queries = 0; physical.calls = 0;
            List<Map<String,Object>> fields = new ArrayList<>();
            physical.columns = new ArrayList<>();
            for (int i=0;i<size;i++) {
                String name="field_"+i;
                fields.add(Map.of("columnName",name,"dataType","varchar","length",32));
                ColumnInfo column=new ColumnInfo(); column.setColumnName(name); column.setDataType("string");
                column.setColumnType("string"); column.setNullable(true); physical.columns.add(column);
            }
            Map<?,?> result=assertInstanceOf(Map.class,run(Map.of("canvas",true,"validateOnly",true,"batchReuseValidation",true,
                    "targets",List.of(Map.of("dbId","target-db","tableName","ods_existing","ddl","CREATE TABLE ods_existing(id STRING)","tableItems",fields)))));
            var item=(Map<?,?>)((List<?>)result.get("results")).getFirst();
            assertEquals(true,result.get("batchReuseValidation")); assertEquals(false,result.get("sideEffectsApplied"));
            assertEquals(size,((List<?>)item.get("tableItems")).size()); assertEquals(true,item.get("physicalTableReused"));
            assertEquals(1,counter.queries); assertEquals(1,physical.calls); assertEquals(0,counter.writes);
            System.out.println("Existing Hive fields="+size+", tenant SQL=1, DESCRIBE=1, DDL writes=0");
        }
    }

    @Test
    void hiveDescribeKeepsLengthAndDecimalCapacity() throws Exception {
        ColumnInfo name=new ColumnInfo(); name.setColumnName("name"); name.setDataType("varchar(32)");
        ColumnInfo amount=new ColumnInfo(); amount.setColumnName("amount"); amount.setDataType("decimal(10, 2)");
        physical.columns=List.of(name,amount);
        var fields=List.of(Map.of("columnName","name","dataType","varchar","length",32),
            Map.of("columnName","amount","dataType","decimal","precisionLength",10,"scale",2));
        Map<String,Object> request=new LinkedHashMap<>(Map.of("canvas",true,"reuseExistingOnly",true,"dbId","target-db",
            "tableName","ods_existing","ddl","CREATE TABLE ods_existing(name STRING,amount DECIMAL(10,2))","tableItems",fields));
        var result=assertInstanceOf(Map.class,run(request));
        var actual=(List<?>)result.get("tableItems");
        assertEquals("varchar",((Map<?,?>)actual.getFirst()).get("dataType"));
        assertEquals(32L,((Map<?,?>)actual.getFirst()).get("length"));
        assertEquals(10,((Map<?,?>)actual.get(1)).get("precisionLength"));
        assertEquals(2,((Map<?,?>)actual.get(1)).get("scale"));
        request.put("tableItems",List.of(Map.of("columnName","name","dataType","varchar","length",64),fields.get(1)));
        assertInstanceOf(ExitValue.class,run(request));
        request.put("tableItems",List.of(fields.getFirst(),Map.of("columnName","amount","dataType","decimal","precisionLength",12,"scale",3)));
        assertInstanceOf(ExitValue.class,run(request));
        assertEquals(0,counter.writes);
    }

    @Test
    void ordinaryTargetsUseOneGroupedColumnReadAndKeepPhysicalOrder() throws Exception {
        jdbc.update("update db_datasource_t set db_type='postgresql',username='fixture',pool_cfg=?",
                "{\"host\":\"fixture.invalid\",\"database\":\"warehouse\"}");
        ColumnInfo column=new ColumnInfo(); column.setColumnName("id"); column.setDataType("varchar");
        column.setColumnType("varchar(64)"); column.setLength(64L); column.setNullable(true); physical.columns=List.of(column);
        List<Map<String,Object>> targets=new ArrayList<>();
        for (int i=0;i<20;i++) targets.add(Map.of("dbId","target-db","tableName","ods_existing_"+i,
                "ddl","CREATE TABLE ods_existing_"+i+"(id VARCHAR(32))","tableItems",List.of(Map.of("columnName","id","dataType","varchar","length",32))));
        Map<?,?> result=assertInstanceOf(Map.class,run(Map.of("canvas",true,"validateOnly",true,"batchReuseValidation",true,"targets",targets)));
        assertEquals(20,((List<?>)result.get("results")).size()); assertEquals(1,physical.calls); assertEquals(1,counter.queries);
        var fields=(List<?>)((Map<?,?>)((List<?>)result.get("results")).getFirst()).get("tableItems");
        assertEquals("varchar",((Map<?,?>)fields.getFirst()).get("dataType"));
        assertEquals(64L,((Map<?,?>)fields.getFirst()).get("length")); assertEquals(0,counter.writes);
    }

    @Test
    void missingOrIncompatibleFieldsAndMixedUnauthorizedIdsNeverWrite() throws Exception {
        ColumnInfo column=new ColumnInfo(); column.setColumnName("id"); column.setDataType("int"); column.setNullable(true);
        physical.columns=List.of(column);
        for (String name:List.of("missing","id")) {
            assertInstanceOf(ExitValue.class,run(Map.of("canvas",true,"validateOnly",true,"batchReuseValidation",true,
                "targets",List.of(Map.of("dbId","target-db","tableName","ods_existing","ddl","CREATE TABLE ods_existing(id STRING)",
                    "tableItems",List.of(Map.of("columnName",name,"dataType","varchar","length",32)))))));
            assertEquals(0,counter.writes);
        }
        physical.calls=0;
        var targets=List.of(Map.of("dbId","target-db","tableName","ods_existing","ddl","CREATE TABLE ods_existing(id STRING)"),
                Map.of("dbId","foreign-db","tableName","ods_existing","ddl","CREATE TABLE ods_existing(id STRING)"));
        assertInstanceOf(ExitValue.class,run(Map.of("canvas",true,"validateOnly",true,"batchReuseValidation",true,"targets",targets)));
        assertEquals(0,physical.calls); assertEquals(0,counter.writes);
    }

    @Test
    void explicitSingleReuseNeverExecutesCreationAndRespectsRoleScope() throws Exception {
        ColumnInfo column=new ColumnInfo(); column.setColumnName("id"); column.setDataType("string"); column.setNullable(true);
        physical.columns=List.of(column);
        Map<String,Object> request=Map.of("canvas",true,"reuseExistingOnly",true,"dbId","target-db","tableName","ods_existing",
            "ddl","CREATE TABLE ods_existing(id STRING)","tableItems",List.of(Map.of("columnName","id","dataType","varchar","length",32)));
        Map<?,?> result=assertInstanceOf(Map.class,run(request));
        assertEquals(false,result.get("created")); assertEquals(true,result.get("physicalTableReused")); assertEquals(0,counter.writes);
        scope.deniedId="target-db"; physical.calls=0;
        assertInstanceOf(ExitValue.class,run(request)); assertEquals(0,physical.calls);
    }

    @Test
    void twentyEditedDdlsUseOneTenantReadAndNoWrites() throws Exception {
        Map<?, ?> response = assertInstanceOf(Map.class, run(request(20)));
        assertEquals(true, response.get("batchValidateOnly"));
        assertEquals(false, response.get("sideEffectsApplied"));
        assertEquals(20, ((Collection<?>) response.get("results")).size());
        assertEquals(1, counter.queries);
        assertEquals(0, counter.writes);
    }

    @Test
    void hundredTargetsAreRejectedBeforeSql() throws Exception {
        assertInstanceOf(ExitValue.class, run(request(100)));
        assertEquals(0, counter.queries);
        assertEquals(0, counter.writes);
    }

    @Test
    void hiveKeepsManuallyEditedSqlAndUsesCanonicalMetadataName() throws Exception {
        for (String name : List.of("ODS_person", "ods_Person")) {
            Object response = run(Map.of("canvas", true, "validateOnly", true, "batchValidateOnly", true,
                    "targets", List.of(Map.of("dbId", "target-db", "tableName", name,
                            "ddl", "CREATE TABLE `" + name + "` (`Mixed_FIELD` STRING COMMENT 'Keep_UPPER_ODS')"))));
            var result=assertInstanceOf(Map.class,response);
            var target=(Map<?,?>)((List<?>)result.get("results")).getFirst();
            assertEquals(name.toLowerCase(java.util.Locale.ROOT),target.get("tableName"));
            assertTrue(target.get("ddl").toString().contains("`"+name+"`"));
            assertEquals(0, counter.writes);
        }
        Map<?, ?> lower = assertInstanceOf(Map.class, run(Map.of("canvas", true, "validateOnly", true, "batchValidateOnly", true,
                "targets", List.of(Map.of("dbId", "target-db", "tableName", "ods_person",
                        "ddl", "CREATE TABLE `ods_person` (`Mixed_FIELD` STRING COMMENT 'Keep_UPPER_ODS')")))));
        String ddl = ((Map<?, ?>) ((List<?>) lower.get("results")).getFirst()).get("ddl").toString();
        assertTrue(ddl.contains("`ods_person`"));
        assertTrue(ddl.contains("Mixed_FIELD"));
        assertTrue(ddl.contains("Keep_UPPER_ODS"));
        assertEquals(0, counter.writes);
    }

    @Test
    void hiddenDatasourceIsRejectedBeforeDdlParsing() throws Exception {
        scope.deniedId = "target-db";
        assertInstanceOf(ExitValue.class, run(request(1)));
        assertEquals(1, counter.queries);
        assertEquals(0, counter.writes);
    }

    @Test
    void singleHivePreflightLeavesSqlCaseToDatabaseAndCanonicalizesMetadata() throws Exception {
        for (String name : List.of("ODS_person", "ods_person")) {
            Object response = run(Map.of("canvas", true, "validateOnly", true, "dbId", "target-db", "tableName", name,
                    "ddl", "CREATE TABLE `" + name + "` (id STRING COMMENT 'Keep_UPPER')"));
            {
                Map<?, ?> lower = assertInstanceOf(Map.class, response);
                assertEquals(true, lower.get("valid"));
                assertEquals(name.toLowerCase(java.util.Locale.ROOT),lower.get("tableName"));
                assertTrue(lower.get("ddl").toString().contains("Keep_UPPER"));
            }
            assertEquals(0, counter.writes);
        }
    }

    private Map<String, Object> request(int count) {
        List<Map<String, Object>> targets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String table = "ods_target_" + i;
            targets.add(Map.of("dbId", "target-db", "tableName", table,
                    "ddl", "CREATE TABLE " + table + " (id STRING)"));
        }
        return Map.of("canvas", true, "validateOnly", true, "batchValidateOnly", true, "targets", targets);
    }

    private Object run(Map<String, Object> body) throws Exception {
        String script = CanonicalMagicSources.byId(SOURCE_ID)
                .replaceAll("(?m)^import (?!com\\.linewell\\.dataelement\\.(?:metautil\\.(?:model\\.|jdbc\\.JdbcDriverPropertyResolver)|feature\\.diagnostics\\.DiagnosticSanitizer)|java\\.util\\.(?:Map|List))[^\\n]*\\n", "");
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("body", body);
        inputs.put("db", db);
        inputs.put("tenantRuntime", scope);
        inputs.put("dataScope", scope);
        inputs.put("connectionProperties", scope);
        inputs.put("poolJsons", new JsonFixture());
        inputs.put("canvasTableDdl", new CanvasTableDdl());
        inputs.put("tablePartitions", partitions);
        inputs.put("hwhive",physical);
        inputs.put("metadataExplorerService",physical);
        return MagicScript.create(script, null).execute(new MagicScriptContext(inputs));
    }

    public static class PhysicalMetadata {
        List<ColumnInfo> columns=List.of();
        List<Map<String,Object>> rawRows;
        int calls;
        boolean allowCreate;
        java.sql.SQLException createFailure;
        List<String> createdDdls = new ArrayList<>();
        public Map<String,List<ColumnInfo>> getColumnsByTables(DataSourceConfig config,List<String> names) {
            calls++; Map<String,List<ColumnInfo>> result=new LinkedHashMap<>();
            for(String name:names) result.put(name.toLowerCase(java.util.Locale.ROOT),columns); return result;
        }
        public List<Map<String,Object>> queryTable(String sql) {
            assertTrue(sql.startsWith("DESCRIBE `warehouse`.`ods_existing")); calls++;
            if(rawRows!=null) return rawRows;
            return columns.stream().map(column->Map.<String,Object>of("col_name",column.getColumnName(),"data_type",column.getDataType())).toList();
        }
        public void createTable(DataSourceConfig config,String ddl) { fail("Reuse must never issue CREATE TABLE"); }
        public void execDDLSqlInDatabase(String database,String ddl) throws java.sql.SQLException {
            if (!allowCreate) fail("Hive reuse must never issue DDL");
            if (createFailure != null) throw createFailure;
            assertEquals("warehouse",database); createdDdls.add(ddl);
        }
    }

    public static class CountingPartitions extends TablePartitions {
        int calls;
        @Override public String validate(String ddl,Map<String,Object> plan,DataSourceConfig config) {
            calls++; return super.validate(ddl,plan,config);
        }
    }

    public static class Scope {
        String deniedId;
        Map<String,Object> registered = Map.of("dbType", "hive", "database", "warehouse", "metadataAccessMode", "server-managed-mrs");
        public String id() { return "police"; }
        public Map<String, Object> resolve(String tenantId, String datasourceId) {
            assertEquals("police", tenantId);
            assertEquals("target-db", datasourceId);
            return registered;
        }
        public List<Map<String, Object>> visibleDataSourcesFor(String resource, Collection<Map<String, Object>> rows) {
            assertEquals("MENU:2081000000000000002", resource);
            return rows.stream().filter(row -> !String.valueOf(row.get("tid")).equals(deniedId)).toList();
        }
    }

    public static class JsonFixture {
        private final ObjectMapper mapper = new ObjectMapper();
        public Map<String, Object> parse(String source) throws Exception {
            return mapper.readValue(source, Map.class);
        }
    }

    private static final class QueryCounter implements SQLInterceptor {
        int queries;
        int writes;
        @Override public void preHandle(BoundSql sql, RequestEntity request) {
            if (sql.getSql().stripLeading().toLowerCase().startsWith("select")) queries++;
            else writes++;
        }
    }
}
