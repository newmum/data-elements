package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ComponentManifest;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingVersionStore;
import com.linewell.dataelement.mapping.core.MappingEngine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DslCompilerHiveTemporalWriterTest {
    @ParameterizedTest @ValueSource(strings={"Oracle 12+","OCEANBASE_ORACLE","ORACLE12"})
    void oracleDialectAliasesKeepTheirPhysicalNumberReader(String databaseType) {
        var sink=new Pipeline.Node("jdbc","sink.jdbc","Oracle目标","sink",600,0,Map.of("dbType",databaseType,
                "jdbcUrl","jdbc:oracle:thin:@//validation.invalid:1521/validation","username","validation","password","validation",
                "table","ODS_VALIDATION","writerType","LINEWELL_PUT_DATABASE_RECORD","statementType","INSERT",
                "targetColumns",List.of(Map.of("columnName","N","columnType","NUMBER(38,18)","dataType","decimal"))));
        compiler.compile(flow(List.of(source("source","mysql",Map.of()),sink)));
        var readers=services.stream().filter(s->s.type().equals("com.linewell.dataelement.nifi.HaitongJdbcRecordReader")).toList();
        assertThat(readers).hasSize(1);
        assertThat(readers.getFirst().properties()).containsEntry("number-fields","[\"N\"]");
        assertThat(namedProcessor("Oracle目标/put").properties()).containsEntry("put-db-record-record-reader",readers.getFirst().id());
    }
    @Test
    void physicalOracleNumbersUseOneExactReaderAcrossInsertProjectionAndWriter() {
        var columns=List.of(Map.<String,Object>of("columnName","HEIGH","columnType","NUMBER","dataType","decimal"),
                Map.<String,Object>of("columnName","DATA","columnType","BLOB","dataType","binary"),
                Map.<String,Object>of("columnName","ODS_RKSJ","columnType","TIMESTAMP","dataType","datetime"));
        var sink=new Pipeline.Node("jdbc","sink.jdbc","Oracle目标","sink",600,0,Map.of("dbType","ORACLE",
                "jdbcUrl","jdbc:oracle:thin:@//validation.invalid:1521/validation","username","validation","password","validation",
                "table","ODS_VALIDATION","writerType","LINEWELL_PUT_DATABASE_RECORD","statementType","INSERT","targetColumns",columns));
        compiler.compile(flow(List.of(source("source","mysql",Map.of()),sink)));
        var readers=services.stream().filter(s->s.type().equals("com.linewell.dataelement.nifi.HaitongJdbcRecordReader")).toList();
        assertThat(readers).hasSize(1);
        var reader=readers.getFirst();assertThat(reader.properties()).containsEntry("number-fields","[\"HEIGH\"]").containsEntry("binary-fields","[\"DATA\"]");
        assertThat(namedProcessor("Oracle目标/normalize").properties()).containsValue(reader.id());
        assertThat(namedProcessor("Oracle目标/put").properties()).containsEntry("put-db-record-record-reader",reader.id());
        assertThat(services).noneMatch(s->s.type().equals("com.linewell.dataelement.nifi.HaitongBinaryRecordReader"));
        verify(nifi,times(1)).listControllerServices("root");
    }

    @ParameterizedTest @ValueSource(strings={"BLOB","VARBINARY"})
    void onlyPhysicalOracleBlobTargetsRestoreTheirBinaryReaderContract(String physicalType) {
        var sink=new Pipeline.Node("jdbc","sink.jdbc","Oracle目标","sink",600,0,Map.of("dbType","ORACLE",
                "jdbcUrl","jdbc:oracle:thin:@//validation.invalid:1521/validation","username","validation","password","validation",
                "table","ODS_VALIDATION","writerType","LINEWELL_PUT_DATABASE_RECORD","statementType","INSERT",
                "targetColumns",List.of(Map.of("columnName","ID","dataType","INT"),
                        Map.of("columnName","DATA","columnType",physicalType,"dataType","binary"))));
        compiler.compile(flow(List.of(source("source","mysql",Map.of()),sink)));
        var binary=services.stream().filter(s->s.type().equals("com.linewell.dataelement.nifi.HaitongBinaryRecordReader")).toList();
        String expected="global-json-reader";
        if(physicalType.equals("BLOB")) {
            assertThat(binary).hasSize(1);expected=binary.getFirst().id();
            assertThat(binary.getFirst().properties()).containsEntry("delegate-reader","global-json-reader").containsEntry("binary-fields","[\"DATA\"]");
        } else assertThat(binary).isEmpty();
        assertThat(namedProcessor("Oracle目标/put").properties()).containsEntry("put-db-record-record-reader",expected);
        assertNoTaskJsonServices();
    }
    private record Created(String id, String type, String name, Map<String, String> properties) {}
    private final List<Created> services = new ArrayList<>();
    private final List<Created> processors = new ArrayList<>();
    private final NifiClient nifi = mock(NifiClient.class);
    private final DslCompiler compiler;

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"LINEWELL_PUT_DATABASE_RECORD,INSERT,true",
            "LINEWELL_PUT_DATABASE_RECORD,MERGE,false", "NIFI_PUT_DATABASE_RECORD,INSERT,false"})
    void typedJdbcInputProjectsDuplicateInsertAuditColumnsOnlyForLinewellInsert(String writer,String statement,boolean projection) throws Exception {
        var columns=new ArrayList<Map<String,Object>>();
        for(String field:List.of("CREATETIME","KEYWORD","SEARCHNUM","UPDATETIME","ODS_UUID","ODS_RKSJ","ODS_GXSJ"))
            columns.add(Map.of("columnName",field,"dataType",field.equals("CREATETIME")||field.startsWith("ODS_")&&!field.equals("ODS_UUID")?"TIMESTAMP":"VARCHAR2",
                    "primaryKey",statement.equals("MERGE")&&field.equals("KEYWORD")));
        var sink=new Pipeline.Node("jdbc","sink.jdbc","Oracle目标","sink",600,0,Map.of("dbType","Oracle",
                "jdbcUrl","jdbc:oracle:thin:@//validation.invalid:1521/business","username","validation","password","validation-only",
                "table","ODS_VALIDATION","writerType",writer,"statementType",statement,"targetColumns",columns));
        var result=compiler.compile(hiveTargetFlow(List.of(source("source","mysql",Map.of()),mapping(),sink),
                List.of(new Pipeline.Edge("sm","source","mapping","default"),new Pipeline.Edge("mj","mapping","jdbc","default"))));
        var put=namedProcessor("Oracle目标/put");
        if(!projection) {
            assertThat(processors).noneMatch(p->p.name().equals("Oracle目标/normalize"));
            return;
        }
        var normalize=namedProcessor("Oracle目标/normalize");
        String query=normalize.properties().get("success");
        assertThat(query).contains("\"CREATETIME\" AS \"CREATETIME\"","\"ODS_UUID\" AS \"ODS_UUID\"")
                .doesNotContain("ODS_RKSJ","ODS_GXSJ","CAST(","TO_TIMESTAMP");
        assertThat(put.properties()).containsEntry("__haitong.linewell.insert-values","ODS_RKSJ=SYSDATE,ODS_GXSJ=SYSDATE")
                .containsEntry("put-db-record-statement-type","INSERT");
        verify(nifi).createConnection("pg",normalize.id(),"PROCESSOR",put.id(),"PROCESSOR",List.of("success"));
        verify(nifi).createConnection(eq("pg"),eq(normalize.id()),eq("PROCESSOR"),startsWith("funnel-"),eq("FUNNEL"),eq(List.of("failure")));
        verify(nifi,never()).createConnection("pg",normalize.id(),"PROCESSOR",normalize.id(),"PROCESSOR",List.of("failure"));
        assertThat(result.nodes().get("jdbc").inletProcessorId).isEqualTo(normalize.id());
        // Execute the actual emitted projection, preserving every business value and row at 20/100 records.
        for(int size:List.of(20,100))try(var db=java.sql.DriverManager.getConnection("jdbc:h2:mem:validation_audit_"+size)) {
            db.createStatement().execute("CREATE TABLE FLOWFILE (CREATETIME VARCHAR(64), KEYWORD VARCHAR(100), SEARCHNUM INT, UPDATETIME VARCHAR(64), ODS_UUID VARCHAR(64), ODS_RKSJ VARCHAR(64), ODS_GXSJ VARCHAR(64))");
            try(var add=db.prepareStatement("INSERT INTO FLOWFILE VALUES(?,?,?,?,?,?,?)")) {
                for(int i=0;i<size;i++){add.setString(1,"2026-11-21T01:02:03.123");add.setString(2,"业务关键字"+i);add.setInt(3,i);
                    add.setString(4,null);add.setString(5,"validation-"+i);add.setString(6,"incoming-created");add.setString(7,"incoming-updated");add.addBatch();}add.executeBatch();
            }
            try(var rows=db.createStatement().executeQuery(query+" ORDER BY SEARCHNUM")) {
                assertThat(rows.getMetaData().getColumnCount()).isEqualTo(5);int read=0;
                while(rows.next()){assertThat(rows.getString(1)).isEqualTo("2026-11-21T01:02:03.123");assertThat(rows.getString(2)).isEqualTo("业务关键字"+read);
                    assertThat(rows.getInt(3)).isEqualTo(read);assertThat(rows.getString(4)).isNull();assertThat(rows.getString(5)).isEqualTo("validation-"+read);read++;}
                assertThat(read).isEqualTo(size);
            }
        }
    }

    DslCompilerHiveTemporalWriterTest() throws Exception {
        ManifestRegistry registry = mock(ManifestRegistry.class);
        ObjectMapper json = new ObjectMapper();
        for (var file : new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                .getResources("classpath:manifests/sources/*.json")) {
            try (var stream = file.getInputStream()) {
                var manifest = json.readValue(stream,ComponentManifest.class);
                when(registry.get(manifest.key())).thenReturn(manifest);
            }
        }
        try (var stream = getClass().getResourceAsStream("/manifests/transforms/sql.json")) {
            when(registry.get("transform.sql")).thenReturn(json.readValue(stream, ComponentManifest.class));
        }
        try (var stream = getClass().getResourceAsStream("/manifests/transforms/field-mapping.json")) {
            when(registry.get("transform.field-mapping")).thenReturn(json.readValue(stream, ComponentManifest.class));
        }
        try (var stream = getClass().getResourceAsStream("/manifests/transforms/field-enrichment.json")) {
            when(registry.get("transform.field-enrichment")).thenReturn(json.readValue(stream,ComponentManifest.class));
        }
        try (var stream = getClass().getResourceAsStream("/manifests/sinks/hive-sink.json")) {
            when(registry.get("sink.hive")).thenReturn(json.readValue(stream, ComponentManifest.class));
        }
        try (var stream = getClass().getResourceAsStream("/manifests/sinks/jdbc-sink.json")) {
            when(registry.get("sink.jdbc")).thenReturn(json.readValue(stream, ComponentManifest.class));
        }
        when(nifi.findProcessorType("LinewellPutDatabaseRecord")).thenReturn(java.util.Optional.of(
                "com.linewell.microservice.processors.database.LinewellPutDatabaseRecord"));
        HiveModule hive = mock(HiveModule.class);
        when(hive.resolveRuntimeProfile("default")).thenReturn(Map.of("dbType", "HIVE",
                "jdbcUrl", "jdbc:hive2://hive.example:10000/default", "hiveConnectionMode", "JDBC",
                "clientConfigDir","/opt/mrs/client","keytabPath","/opt/mrs/client/user.keytab","krb5ConfPath","/opt/mrs/client/krb5.conf","userPrincipal","service_user"));
        when(nifi.findProcessorType("PutHwHDFS")).thenReturn(java.util.Optional.of("com.linewell.microservice.processor.hadoop.PutHwHDFS"));
        when(nifi.listControllerServices("root")).thenReturn(HiveServiceFixtures.withCsv(json.valueToTree(Map.of("component",Map.of(
                "id","csv-service-id","name","PUBLIC-WRITER-CSV-HDFS","type","org.apache.nifi.csv.CSVRecordSetWriter",
                "properties",Map.of("Include Header Line","false","Value Separator","\\u0001"))))));
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.get("/flow/process-groups/root",com.fasterxml.jackson.databind.JsonNode.class)).thenReturn(HiveServiceFixtures.rootFlow());
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        AtomicInteger sequence = new AtomicInteger();
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap())).thenAnswer(call -> {
            String id = "cs-" + sequence.incrementAndGet();
            services.add(new Created(id, call.getArgument(1), call.getArgument(2), new LinkedHashMap<>(call.getArgument(3))));
            return entity(id);
        });
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class))).thenAnswer(call -> {
            String id = "processor-" + sequence.incrementAndGet();
            processors.add(new Created(id, call.getArgument(1), call.getArgument(2), new LinkedHashMap<>(call.getArgument(5))));
            return entity(id);
        });
        when(nifi.createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenAnswer(call -> entity("connection-" + sequence.incrementAndGet()));
        when(nifi.createRetentionFunnel(anyString(),anyDouble(),anyDouble())).thenAnswer(call->entity("funnel-"+sequence.incrementAndGet()));
        compiler = new DslCompiler(nifi, registry, mock(FieldMappingService.class), hive);
        var unified=mock(UnifiedMappingService.class);
        when(unified.compileBatch(anyMap())).thenAnswer(call -> {
            Map<String, UnifiedMappingService.Compiled> plans = new LinkedHashMap<>();
            call.<Map<String, ?>>getArgument(0).keySet().forEach(id -> plans.put(id, prepared()));
            return plans;
        });
        compiler.setUnifiedMappingService(unified);
        var versions=mock(MappingVersionStore.class);
        when(versions.archive(any(),anyMap())).thenReturn(Map.of("mapping","plan-version"));
        compiler.setMappingVersions(versions);
    }

    static java.util.stream.Stream<String> sourceManifests() throws Exception {
        return java.util.Arrays.stream(new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                .getResources("classpath:manifests/sources/*.json"))
                .map(resource -> resource.getFilename().replace(".json",""));
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("sourceManifests")
    void everySourceCompilesTheSharedGovernanceTemplateWithObAndHiveTargets(String type) {
        var cfg=new LinkedHashMap<String,Object>();
        cfg.putAll(Map.of("host","validation.invalid","hostname","validation.invalid","database","business",
                "username","validation","password","validation-only","compatibleMode","ORACLE",
                "driverLocations","/lib/validation.jar","syncMode","FULL"));
        cfg.putAll(Map.of("remotePath","/validation","sourceFileName","EVENT_LOG.csv","fileFormat","csv",
                "fileFilterRegex","EVENT_LOG\\.csv","csvEncoding","UTF-8","csvDelimiter",",",
                "fileHeaderMode","physical","protocol",type.equals("sftp")?"sftp":"ftp"));
        cfg.putAll(Map.of("url","http://validation.invalid/data","method","GET","responsePath","rows",
                "bootstrapServers","validation.invalid:9092","topic","validation-events","groupId","validation",
                "securityProtocol","PLAINTEXT","messageFormat","JSON"));
        cfg.putAll(Map.of("minioEndpoint","http://validation.invalid:9000","minioBucket","validation",
                "minioAccessKey","validation","minioSecretKey","validation-only","minioObjectKey","EVENT_LOG.csv",
                "path","/validation","clientConfigDir","/opt/validation-client","zookeeperQuorum","validation.invalid:2181"));
        cfg.putAll(Map.of("nodes","validation.invalid:9200","defaultIndex","validation-events","authMethod","NONE",
                "queryJson","{\"query\":{\"match_all\":{}}}","hiveConnectionMode","open-source","schedulingPeriod","60 sec"));
        if(type.equals("elasticsearch"))cfg.put("protocol","http");
        if(type.equals("kafka"))cfg.put("schedulingPeriod","0 sec");
        cfg.put("dbType",RegisteredDatasourceType.configType(type,Map.of("compatibleMode","ORACLE")));
        cfg.put("sourceColumns",List.of(Map.of("columnName","ID","dataType","BIGINT"),Map.of("columnName","EVENT_TIME","dataType","TIMESTAMP")));
        var origin=source("source",type,cfg);
        var ob=new Pipeline.Node("ob","sink.jdbc","OB目标","sink",1000,0,Map.of("dbType","OCEANBASE_ORACLE",
                "jdbcUrl","jdbc:oceanbase:oracle://validation.invalid:2881/business","username","validation",
                "password","validation-only","table","EVENT_LOG","driverLocations","/lib/validation.jar",
                "statementType","UPSERT","updateKeys","ID","targetColumns",List.of(Map.of("columnName","ID","dataType","BIGINT","primaryKey",true))));
        var hive=hiveSink("hive");
        var first=new Pipeline.Node("map-ob","transform.field-enrichment","OB字段治理","transform",500,0,mapping().config());
        var second=new Pipeline.Node("map-hive","transform.field-enrichment","Hive字段治理","transform",500,200,mapping().config());
        var result=compiler.compile(hiveTargetFlow(List.of(origin,first,second,ob,hive),List.of(
                new Pipeline.Edge("a","source","map-ob","default"),new Pipeline.Edge("b","map-ob","ob","default"),
                new Pipeline.Edge("c","source","map-hive","default"),new Pipeline.Edge("d","map-hive","hive","default"))));
        assertThat(result.nodes().keySet()).contains("source","map-ob","map-hive","ob","hive");
        assertThat(processors).anyMatch(processor->processor.type().endsWith(".LinewellPutDatabaseRecord"))
                .anyMatch(processor->processor.type().endsWith(".PutHwHDFS"));
        verify(nifi,times(1)).listControllerServices("root");
    }

    @ParameterizedTest @ValueSource(strings={"MySQL","Oracle","OCEANBASE_ORACLE"})
    void governanceOutputConnectsDirectlyToJdbcWriterAndKeepsErrorRoutes(String database) {
        var governance=new Pipeline.Node("mapping","transform.field-mapping","字段治理","transform",300,0,mapping().config());
        var sink=new Pipeline.Node("sink","sink.jdbc","目标表","sink",600,0,Map.of(
                "dbType",database,"jdbcUrl",database.equals("MySQL")?"jdbc:mysql://db/ods":"jdbc:oracle:thin:@//db:1521/service",
                "table","TARGET_TABLE","targetColumns",List.of(
                        Map.of("columnName","ID","dataType","BIGINT","primaryKey",true),
                        Map.of("columnName","EVENT_TIME","dataType","TIMESTAMP"))));
        var result=compiler.compile(hiveTargetFlow(List.of(source("source","mysql",Map.of()),governance,sink),
                List.of(new Pipeline.Edge("sm","source","mapping","default"),new Pipeline.Edge("ms","mapping","sink","default"))));
        Created mapped=namedProcessor("字段治理"),put=namedProcessor("目标表/put");
        assertThat(processors).noneMatch(p->p.type().endsWith(".MergeRecord")||p.name().endsWith("/normalize"));
        assertThat(put.type()).endsWith(database.equals("MySQL")?".PutDatabaseRecord":".LinewellPutDatabaseRecord");
        assertThat(result.nodes().get("sink").inletProcessorId).isEqualTo(put.id());
        verify(nifi).createConnection("pg",mapped.id(),"PROCESSOR",put.id(),"PROCESSOR",List.of("success"));
        assertErrorRoutes(mapped);
        verify(nifi,times(1)).listControllerServices("root");
    }

    @ParameterizedTest @ValueSource(strings={"hive","mysql","oracle","oceanbase"})
    void allMappingInputsAndOutputsUsePublicCodecs(String database) {
        Pipeline.Node mapping=mapping();
        compiler.compile(flow(List.of(source("source",database,Map.of()),mapping)));
        Created processor=namedProcessor("字段映射");
        assertThat(processor.properties()).containsEntry("record-writer",database.equals("hive")?"global-hive-json-writer":"global-json-writer")
                .containsEntry("record-reader","global-json-reader").containsEntry("typed-record-reader","global-json-reader").containsEntry("preserve-json-numbers","true");
        assertThat(processor.type()).isEqualTo("com.linewell.dataelement.nifi.HaitongTransformRecord");
        assertEmbeddedMappingMetadata(processor);
        verify(nifi,never()).waitForControllerServiceEnabled(eq("true"),anyLong());
        assertThat(namedProcessor("source/execute").properties())
                .containsEntry("Record Writer", "global-hive-json-writer")
                .containsEntry("Use Avro Logical Types", "true");
        assertNoTaskJsonServices();
    }
    @Test void oraclePublicationProjectsDateAsTimestampBeforeJdbcReadsIt() {
        compiler.compile(flow(List.of(source("source","oracle",Map.of("dbType","ORACLE","sourceColumns",List.of(Map.of("columnName","EVENT_TIME","dataType","DATE")))))));
        assertThat(namedProcessor("source/fetch").properties()).containsEntry("Columns to Return","CAST(\"EVENT_TIME\" AS TIMESTAMP) AS \"EVENT_TIME\"");
    }
    @Test void failureAndRetryShareSelfLoopWhileOnlyInvalidHasRetainedQueue() {
        compiler.compile(flow(List.of(source("source","mysql",Map.of()),mapping())));
        Created processor=namedProcessor("字段映射");
        assertErrorRoutes(processor);
    }

    private void assertErrorRoutes(Created processor) {
        verify(nifi).createConnection("pg",processor.id(),"PROCESSOR",processor.id(),"PROCESSOR",List.of("failure","retry"));
        verify(nifi,times(1)).createRetentionFunnel(eq("pg"),anyDouble(),anyDouble());
        verify(nifi).createConnection(eq("pg"),eq(processor.id()),eq("PROCESSOR"),anyString(),eq("FUNNEL"),eq(List.of("invalid")));
        verify(nifi,never()).createConnection(eq("pg"),eq(processor.id()),eq("PROCESSOR"),anyString(),eq("FUNNEL"),argThat(relationships->relationships.contains("failure")||relationships.contains("retry")));
    }
    @Test void oracleCurrentMaximumSourceUsesSameSupportedProjectionProperty() {
        compiler.compile(flow(List.of(source("source","oracle",Map.of("dbType","ORACLE","syncMode","INCREMENTAL","initialStrategy","START_AT_CURRENT_MAX","incrementalColumn","EVENT_TIME","sourceColumns",List.of(Map.of("columnName","EVENT_TIME","dataType","DATE")))))));
        Created fetch=namedProcessor("source/fetch");assertThat(fetch.type()).endsWith(".QueryDatabaseTableRecord");assertThat(fetch.properties()).containsEntry("Columns to Return","CAST(\"EVENT_TIME\" AS TIMESTAMP) AS \"EVENT_TIME\"").doesNotContainKey("Column Names");
    }

    @ParameterizedTest @ValueSource(strings={"hive","mysql","oracle","oceanbase"})
    void fieldOnlyUpgradeBindsCorrectWriterAndNeverRecreatesOrModifiesSources(String database) {
        compiler.createMappingReplacement(flow(List.of(source("source",database,Map.of()),mapping())),mapping(),"existing-group","reader","typed-reader","existing-writer",prepared());
        assertThat(namedProcessor("字段映射").properties()).containsEntry("record-writer",database.equals("hive")?"global-hive-json-writer":"global-json-writer")
                .containsEntry("record-reader","global-json-reader").containsEntry("typed-record-reader","global-json-reader").containsEntry("preserve-json-numbers","true");
        assertEmbeddedMappingMetadata(namedProcessor("字段映射"));
        assertThat(processors).hasSize(1);
        // The same policy applies to field-only replacement, without touching source IDs/state.
        Created processor=namedProcessor("字段映射");
        verify(nifi).createConnection("existing-group",processor.id(),"PROCESSOR",processor.id(),"PROCESSOR",List.of("failure","retry"));
        verify(nifi,times(1)).createRetentionFunnel(eq("existing-group"),anyDouble(),anyDouble());
        verify(nifi).createConnection(eq("existing-group"),eq(processor.id()),eq("PROCESSOR"),anyString(),eq("FUNNEL"),eq(List.of("invalid")));
        verify(nifi,never()).putWithRevision(contains("/processors/"),anyString(),any());
        assertNoTaskJsonServices();
    }

    @Test void numericJdbcWireProjectionAndGovernanceUseTheSameRegisteredNamesOnBuildAndUpgrade() throws Exception {
        var columns=List.of(Map.of("columnName","1231313","dataType","VARCHAR"),Map.of("columnName","id","dataType","INT"));
        var input=source("source","mysql",Map.of("sourceColumns",columns));
        var pipeline=flow(List.of(input,mapping()));
        compiler.compile(pipeline);
        String aliases=namedProcessor("字段映射").properties().get("jdbc-field-aliases");
        var parsed=new ObjectMapper().readTree(aliases);assertThat(parsed.size()).isEqualTo(1);
        assertThat(namedProcessor("source/fetch").properties().get("Columns to Return")).contains("`1231313` AS `"+parsed.path("1231313").asText()+"`");
        processors.clear();services.clear();
        compiler.createMappingReplacement(pipeline,mapping(),"existing-group","reader","typed","writer",prepared());
        assertThat(processors).hasSize(1);assertThat(namedProcessor("字段映射").properties()).containsEntry("jdbc-field-aliases",aliases);
    }
    private static Pipeline.Node mapping(){return new Pipeline.Node("mapping","transform.field-mapping","字段映射","transform",300,0,Map.of("executionEngine","JAVA_RECORD","mappings","{\"schemaVersion\":\"2.0\",\"rules\":[]}"));}
    @ParameterizedTest @ValueSource(ints={20,100})
    void mappingPreflightChecksClusterCapabilitiesOncePerGraph(int count) {
        var nodes=new ArrayList<Pipeline.Node>();for(int i=0;i<count;i++)nodes.add(new Pipeline.Node("m"+i,"transform.field-mapping","字段治理","transform",0,0,mapping().config()));
        compiler.preflightMapping(flow(nodes));verify(nifi,times(1)).requireMappingExtension();
        verify(nifi,never()).get(anyString(),eq(com.fasterxml.jackson.databind.JsonNode.class));
    }
    private static void assertEmbeddedMappingMetadata(Created processor) {
        assertThat(processor.properties()).doesNotContainKeys("plan-hash","plan-id","context-node-id","required-engine-version");
        try {var deployment=new ObjectMapper().readTree(processor.properties().get("execution-plan")).path("_deployment");
            assertThat(deployment.path("contextNodeId").asText()).isEqualTo("temporal-test:mapping");assertThat(deployment.path("planId").asText()).isEqualTo("plan-version");
        }catch(Exception e){throw new AssertionError(e);}
    }
    private static UnifiedMappingService.Compiled prepared(){try{return new UnifiedMappingService.Compiled(MappingEngine.compile(new ObjectMapper().readTree("{\"schemaVersion\":\"2.0\",\"policies\":{},\"rules\":[]}")),Map.of());}catch(Exception error){throw new IllegalStateException(error);}}

    @Test void hiveSourceUsesNamedTemporalWriterAndMatchingReader() {
        compiler.compile(flow(List.of(source("h", "hive", Map.of()))));
        assertThat(namedProcessor("h/execute").properties()).containsEntry("Record Writer", "global-hive-json-writer");
        assertNoTaskJsonServices();
    }

    @Test void mysqlUsesThePublicTimeFormatWriter() {
        compiler.compile(flow(List.of(source("m", "mysql", Map.of("dbType", "MYSQL")))));
        assertThat(namedProcessor("m/execute").properties()).containsEntry("Record Writer", "global-hive-json-writer");
        assertNoTaskJsonServices();
    }

    @Test void mixedSqlSourcesUseTheSameTimeFormatWriter() {
        compiler.compile(flow(List.of(source("h", "hive", Map.of()), source("m", "mysql", Map.of("dbType", "MYSQL")))));
        assertThat(namedProcessor("h/execute").properties()).containsEntry("Record Writer", "global-hive-json-writer");
        assertThat(namedProcessor("m/execute").properties()).containsEntry("Record Writer", "global-hive-json-writer");
        assertNoTaskJsonServices();
    }

    @ParameterizedTest @ValueSource(strings = {"hive", "mysql", "oracle"})
    void queryExecutedInSourceDatabaseUsesTheSameTemporalWriter(String database) {
        Map<String,Object> sourceConfig="oracle".equals(database)?Map.of("dbType","ORACLE",
                "jdbcUrl","jdbc:oracle:thin:@//validation.invalid:1521/validation","username","validation","password","validation",
                "syncMode","FULL_THEN_INCR","registeredDatasourceId","validation-source-db","sourceTableId","validation-source-table",
                "sourceColumns",List.of(Map.of("columnName","EVENT_TIME","dataType","TIMESTAMP"))):Map.of();
        Pipeline.Node source = source("h", database, sourceConfig);
        Pipeline.Node query = new Pipeline.Node("q", "transform.sql", "query", "transform", 300, 0,
                Map.of("executionMode", "SOURCE_DB", "sql", "SELECT event_time FROM EVENT_LOG"));
        Pipeline base = flow(List.of(source, query));
        Pipeline pipeline = new Pipeline(base.id(), base.name(), null, null, null,
                new Pipeline.Dsl(1, base.dsl().nodes(), List.of(new Pipeline.Edge("edge", "h", "q", "default"))),
                null, null, null, null, null, null, null);
        var result=compiler.compile(pipeline);
        assertThat(namedProcessor("query/query").properties()).containsEntry("Record Writer", "global-hive-json-writer");
        assertThat(result.nodes().get("h").processorIds).isEmpty();
        assertThat(result.nodes().get("h").csIds).doesNotContainKey("date-fetch-dbcp");
        assertThat(services).noneMatch(service->service.name().endsWith("/date-fetch-dbcp")
                ||service.properties().containsKey("oracle.jdbc.J2EE13Compliant"));
    }

    @Test void missingTimeFormatWriterStopsSqlCompilationBeforeAnyProcessGroupIsCreated() {
        when(nifi.listControllerServices("root")).thenReturn(HiveServiceFixtures.codecs().stream()
                .filter(service -> !HiveTemporalJsonPolicy.WRITER_NAME.equals(service.path("component").path("name").asText()))
                .toList());
        assertThatThrownBy(() -> compiler.compile(flow(List.of(source("s", "mysql", Map.of())))))
                .hasMessageContaining(HiveTemporalJsonPolicy.WRITER_NAME);
        assertThat(processors).isEmpty();
        verify(nifi, never()).createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble());
        verify(nifi, never()).createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble(), anyString());
    }

    @ParameterizedTest @ValueSource(ints = {20, 100})
    void sourcesShareOneTemporalWriterWithoutServiceLookupPerNode(int count) {
        List<Pipeline.Node> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) nodes.add(source("s" + i, i % 2 == 0 ? "mysql" : "hive", Map.of()));
        compiler.compile(flow(nodes));
        assertThat(processors.stream().filter(processor -> processor.type().endsWith(".ExecuteSQLRecord")))
                .hasSize(count).allMatch(processor -> "global-hive-json-writer".equals(processor.properties().get("Record Writer")));
        verify(nifi, times(1)).listControllerServices("root");
        assertNoTaskJsonServices();
    }

    @ParameterizedTest @ValueSource(ints = {20, 100})
    void mappingChainsReadGlobalServicesOnceAndNeverCreatePerNodeJsonCodecs(int count) {
        List<Pipeline.Node> nodes=new ArrayList<>();List<Pipeline.Edge> edges=new ArrayList<>();
        nodes.add(source("source","mysql",Map.of()));
        for(int index=0;index<count;index++) {
            String id="mapping"+index;
            nodes.add(new Pipeline.Node(id,"transform.field-mapping",id,"transform",0,0,mapping().config()));
            edges.add(new Pipeline.Edge("e"+index,index==0?"source":"mapping"+(index-1),id,"default"));
        }
        compiler.compile(new Pipeline("chain","mapping chain",null,null,null,new Pipeline.Dsl(1,nodes,edges),null,null,null,null,null,null,null));
        assertThat(processors.stream().filter(processor -> processor.type().endsWith(".HaitongTransformRecord")))
                .hasSize(count).allSatisfy(processor -> assertThat(processor.properties())
                        .containsEntry("record-reader","global-json-reader")
                        .containsEntry("typed-record-reader","global-json-reader")
                        .containsEntry("record-writer","global-json-writer"));
        verify(nifi,times(1)).listControllerServices("root");
        assertNoTaskJsonServices();
    }

    @Test void ordinaryTaskAlsoRejectsMissingGlobalsBeforeCreatingAProcessGroup() {
        when(nifi.listControllerServices("root")).thenReturn(List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> compiler.compile(flow(List.of(source("s","mysql",Map.of())))))
                .hasMessageContaining("缺少全局服务");
        verify(nifi,never()).createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble());
        assertThat(services).isEmpty();assertThat(processors).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings={"mysql","oracle","oceanbase"})
    void downstreamHiveUsesRawSourceAndNamedCodecsRegardlessOfSourceDatabase(String sourceDatabase) {
        compiler.compile(hiveTargetFlow(List.of(source("source",sourceDatabase,Map.of()),mapping(),com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveDownstreamFixtures.rawTarget(hiveSink("sink"))),
                List.of(new Pipeline.Edge("sm","source","mapping","default"),new Pipeline.Edge("mh","mapping","sink","default"))));
        assertThat(processors).noneMatch(p->p.type().endsWith(".HaitongTransformRecord")||p.type().endsWith(".MergeRecord")||p.type().endsWith(".QueryRecord"));
        Created execute=namedProcessor("source/execute"),convert=namedProcessor("sink/convert");
        assertThat(execute.properties()).containsEntry("Record Writer","global-hive-json-writer").containsEntry("Use Avro Logical Types","true");
        assertThat(convert.properties()).containsEntry("Record Reader","global-json-reader").containsEntry("Record Writer","csv-service-id");
        assertThat(services).noneMatch(s->s.name().equals(HiveTemporalJsonPolicy.WRITER_NAME)||s.name().equals(HiveTemporalJsonPolicy.READER_NAME));
        verify(nifi,never()).setControllerServiceRunStatus(eq("global-json-writer"),anyString());
        verify(nifi,never()).setControllerServiceRunStatus(eq("global-json-reader"),anyString());
        verify(nifi,never()).waitForControllerServiceEnabled(eq("global-json-writer"),anyLong());
        verify(nifi,never()).waitForControllerServiceEnabled(eq("global-json-reader"),anyLong());
        verify(nifi).createConnection("pg",execute.id(),"PROCESSOR",convert.id(),"PROCESSOR",List.of("success"));
        verify(nifi,never()).requireMappingExtension();
    }

    @ParameterizedTest @ValueSource(ints={20,100})
    void hiveTargetsShareCodecsAndOneBatchServiceRead(int count) {
        when(nifi.createBufferedConnection(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(entity("buffered"));
        when(nifi.configureWriteFailureRetries(any())).thenReturn(List.of("failure", "retry"));
        List<Pipeline.Node> nodes=new ArrayList<>();List<Pipeline.Edge> edges=new ArrayList<>();
        for(int i=0;i<count;i++) {
            nodes.add(source("s"+i,"mysql",Map.of()));
            nodes.add(new Pipeline.Node("h"+i,"sink.hive","h"+i,"sink",600,0,Map.of("table","EVENT_LOG","database","default")));
            edges.add(new Pipeline.Edge("e"+i,"s"+i,"h"+i,"default"));
        }
        compiler.compile(hiveTargetFlow(nodes,edges));
        assertThat(services.stream().filter(s->HiveTemporalJsonPolicy.WRITER_NAME.equals(s.name()))).isEmpty();
        assertThat(services.stream().filter(s->HiveTemporalJsonPolicy.READER_NAME.equals(s.name()))).isEmpty();
        verify(nifi,times(1)).listControllerServices("root");
        verify(nifi,times(1)).findProcessorType("PutHwHDFS");
        assertThat(processors.stream().filter(p->p.type().endsWith(".MergeRecord"))).hasSize(count);
    }

    @Test void defaultHiveMergeRunsAfterGovernanceAndKeepsFailuresOffTheDataPath() {
        var sink = new Pipeline.Node("sink", "sink.hive", "sink", "sink", 600, 0,
                Map.of("table", "EVENT_LOG", "database", "default"));
        when(nifi.createBufferedConnection(anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn(entity("buffered"));
        when(nifi.configureWriteFailureRetries(any())).thenReturn(List.of("failure", "retry"));

        var result = compiler.compile(hiveTargetFlow(List.of(source("source", "mysql", Map.of()), mapping(), sink),
                List.of(new Pipeline.Edge("sm", "source", "mapping", "default"),
                        new Pipeline.Edge("mh", "mapping", "sink", "default"))));
        var merge = namedProcessor("sink/merge-records");
        var convert = namedProcessor("sink/convert");
        var put = namedProcessor("sink/put");
        assertThat(result.nodes().get("sink").inletProcessorId).isEqualTo(merge.id());
        assertThat(merge.properties()).containsEntry("Record Writer", "global-hive-json-writer")
                .containsEntry("Minimum Bin Size", "128 MB").containsEntry("Maximum Bin Size", "256 MB")
                .containsEntry("Max Bin Age", "5 min").doesNotContainKey("Correlation Attribute Name");
        verify(nifi).createProcessor(eq("pg"), eq(merge.type()), eq(merge.name()), anyDouble(), anyDouble(),
                anyMap(), eq("5 sec"), eq("TIMER_DRIVEN"));
        verify(nifi).createBufferedConnection("pg", namedProcessor("字段映射").id(), merge.id(), "PROCESSOR", List.of("success"));
        verify(nifi).createBufferedConnection("pg", merge.id(), convert.id(), "PROCESSOR", List.of("merged"));
        verify(nifi).createBufferedConnection(eq("pg"), eq(merge.id()), anyString(), eq("FUNNEL"), eq(List.of("failure")));
        verify(nifi).createBufferedConnection(eq("pg"), eq(convert.id()), anyString(), eq("FUNNEL"), eq(List.of("failure")));
        verify(nifi).createBufferedConnection(eq("pg"), eq(put.id()), anyString(), eq("FUNNEL"), eq(List.of("failure", "retry")));
        assertThat(convert.properties()).containsEntry("Record Writer", "csv-service-id")
                .containsEntry("Include Zero Record FlowFiles", "false");
        verify(nifi, times(1)).listControllerServices("root");
    }

    @Test void configuredPartitionsBypassAutomaticHiveMergeEvenWithTheSettingEnabled() {
        var sink = new Pipeline.Node("sink", "sink.hive", "sink", "sink", 600, 0,
                Map.of("table", "EVENT_LOG", "database", "default", "hiveHdfsMerge", true,
                        "partitionPlan", Map.of("enabled", true)));
        compiler.compile(hiveTargetFlow(List.of(source("source", "mysql", Map.of()), sink),
                List.of(new Pipeline.Edge("sh", "source", "sink", "default"))));
        assertThat(processors).noneMatch(p -> p.type().endsWith(".MergeRecord"));
        verify(nifi).createConnection("pg", namedProcessor("source/execute").id(), "PROCESSOR",
                namedProcessor("sink/convert").id(), "PROCESSOR", List.of("success"));
        verify(nifi, never()).configureWriteFailureRetries(any());
    }

    @Test void unrelatedMysqlSourceAlsoUsesThePublicWriter() {
        compiler.compile(hiveTargetFlow(List.of(source("s","mysql",Map.of()),source("unrelated","mysql",Map.of()),hiveSink("h")),List.of(new Pipeline.Edge("e","s","h","default"))));
        assertThat(namedProcessor("s/execute").properties()).containsEntry("Record Writer","global-hive-json-writer");
        assertThat(namedProcessor("unrelated/execute").properties()).containsEntry("Record Writer","global-hive-json-writer");
    }
    @Test void hiveTargetCurrentMaximumIncrementalSourceUsesSameWriter() {
        compiler.compile(hiveTargetFlow(List.of(source("s","oracle",Map.of("dbType","ORACLE","syncMode","INCREMENTAL","initialStrategy","START_AT_CURRENT_MAX","incrementalColumn","EVENT_TIME","sourceColumns",List.of(Map.of("columnName","EVENT_TIME","dataType","DATE")))),hiveSink("h")),List.of(new Pipeline.Edge("e","s","h","default"))));
        assertThat(namedProcessor("s/fetch").properties()).containsEntry("Record Writer","global-hive-json-writer");
        assertThat(namedProcessor("s/fetch").properties()).containsEntry("Columns to Return",null);
    }
    @Test void hiveTargetSourceDatabaseQueryUsesSameWriter() {
        var query=new Pipeline.Node("q","transform.sql","query","transform",300,0,Map.of("executionMode","SOURCE_DB","sql","SELECT event_time FROM EVENT_LOG"));
        compiler.compile(hiveTargetFlow(List.of(source("s","mysql",Map.of()),query,hiveSink("h")),List.of(new Pipeline.Edge("a","s","q","default"),new Pipeline.Edge("b","q","h","default"))));
        assertThat(namedProcessor("query/query").properties()).containsEntry("Record Writer","global-hive-json-writer");
    }
    @ParameterizedTest @ValueSource(strings={"oracle","oceanbase","mysql"})
    void hiveTargetClearsFetchColumnsIncludingOracleDateProjection(String database) {
        var config=Map.<String,Object>of("dbType",database.equals("oceanbase")?"OCEANBASE_ORACLE":database.toUpperCase(),"sourceColumns",List.of(Map.of("columnName","EVENT_TIME","dataType","DATE")),"columns","EVENT_TIME");
        compiler.compile(hiveTargetFlow(List.of(source("s",database,config),hiveSink("h")),List.of(new Pipeline.Edge("e","s","h","default"))));
        assertThat(namedProcessor("s/fetch").properties()).containsEntry("Columns to Return",null);
    }
    @Test void missingGlobalCodecsFailsBeforeCreatingAnyTaskServicesOrProcessors() {
        when(nifi.listControllerServices("root")).thenReturn(List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(()->compiler.compile(hiveTargetFlow(List.of(source("s","mysql",Map.of()),hiveSink("h")),List.of(new Pipeline.Edge("e","s","h","default")))))
                .hasMessageContaining("缺少全局服务");
        assertThat(services).isEmpty();assertThat(processors).isEmpty();
        verify(nifi,never()).createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble());
    }
    @Test void sharedMappingUpgradeReusesItsResolvedWriterWithoutCreatingDuplicatePublicService() {
        var jdbc=new Pipeline.Node("jdbc","sink.jdbc","jdbc","sink",0,0,Map.of());
        var pipeline=hiveTargetFlow(List.of(source("source","mysql",Map.of()),mapping(),com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveDownstreamFixtures.rawTarget(hiveSink("h")),jdbc),List.of(new Pipeline.Edge("sm","source","mapping","default"),new Pipeline.Edge("mh","mapping","h","default"),new Pipeline.Edge("mj","mapping","jdbc","default")));
        compiler.createMappingReplacement(pipeline,mapping(),"existing-group","reader","typed","global-json-writer",prepared());
        assertThat(services).isEmpty();
        assertThat(namedProcessor("字段映射").properties()).containsEntry("record-writer","global-json-writer");
        verify(nifi,times(1)).listControllerServices("root");
    }
    @ParameterizedTest @ValueSource(strings={"mysql","oracle","oceanbase"})
    void directHiveTargetRetainsJavaGovernanceAndItsFailureRoutes(String sourceDatabase) {
        compiler.compile(hiveTargetFlow(List.of(source("source",sourceDatabase,Map.of()),mapping(),hiveSink("sink")),
                List.of(new Pipeline.Edge("sm","source","mapping","default"),new Pipeline.Edge("mh","mapping","sink","default"))));
        Created mapped=namedProcessor("字段映射"),convert=namedProcessor("sink/convert");
        assertThat(mapped.type()).endsWith(".HaitongTransformRecord");
        verify(nifi).createConnection("pg",mapped.id(),"PROCESSOR",convert.id(),"PROCESSOR",List.of("success"));
        assertErrorRoutes(mapped);
        assertThat(convert.properties()).containsEntry("Record Reader","global-json-reader").containsEntry("Record Writer","csv-service-id");
        verify(nifi,times(1)).listControllerServices("root");
    }
    @ParameterizedTest @ValueSource(strings={"HDFS_BATCH","LINEWELL_HDFS","HIVE_RECORD_PUT"})
    void binaryTransportEncodingIsOnlyEnabledForTheDeclaredHiveTextTarget(String mode) throws Exception {
        var spec=new ObjectMapper().readTree("{\"version\":\"1.0\",\"mappings\":[{\"from\":\"data\",\"to\":\"data\",\"targetType\":\"BLOB\"}]}");
        var prepared=new UnifiedMappingService.Compiled(MappingEngine.compile(spec),Map.of());
        var sink=new Pipeline.Node("sink","sink.hive","Hive目标","sink",600,0,Map.of("hiveWriteMode",mode,"table","VALIDATION"));
        var pipeline=hiveTargetFlow(List.of(source("source","mysql",Map.of()),mapping(),sink),
                List.of(new Pipeline.Edge("sm","source","mapping","default"),new Pipeline.Edge("mh","mapping","sink","default")));
        compiler.createMappingReplacement(pipeline,mapping(),"existing-group","reader","typed","writer",prepared);
        if(mode.equals("HIVE_RECORD_PUT"))assertThat(namedProcessor("字段映射").properties()).doesNotContainKey("binary-output-encoding");
        else assertThat(namedProcessor("字段映射").properties()).containsEntry("binary-output-encoding","BASE64_TEXT");
        assertThat(prepared.plan().outputContracts().get("data").logicalType()).isEqualTo("BINARY");
        verify(nifi,never()).putWithRevision(contains("/processors/"),anyString(),any());
    }
    private static Pipeline.Node hiveSink(String id){return new Pipeline.Node(id,"sink.hive",id,"sink",600,0,Map.of("table","EVENT_LOG","database","default","hiveHdfsMerge",false));}
    private static Pipeline hiveTargetFlow(List<Pipeline.Node> nodes,List<Pipeline.Edge> edges){return new Pipeline("target-hive","Hive target",null,null,null,new Pipeline.Dsl(1,nodes,edges),null,null,null,null,null,null,null);}

    @ParameterizedTest @ValueSource(strings = {"jdbc:hive2://host/default", " JDBC:HIVE://host/default "})
    void hiveJdbcUrlRecognizedWithoutExplicitType(String url) {
        assertThat(HiveTemporalJsonPolicy.isHive("source.jdbc", Map.of("jdbcUrl", url))).isTrue();
        assertThat(HiveTemporalJsonPolicy.isHive("source.mysql", Map.of("jdbcUrl", "jdbc:mysql://host/db"))).isFalse();
    }

    private Created namedService(String name) { return services.stream().filter(value -> name.equals(value.name())).findFirst().orElseThrow(); }
    private void assertNoTaskJsonServices() {
        assertThat(services).noneMatch(service -> service.type().equals("org.apache.nifi.json.JsonTreeReader")
                || service.type().equals("org.apache.nifi.json.JsonRecordSetWriter"));
        for(String id:List.of("global-json-reader","global-json-writer","global-hive-json-writer")) {
            verify(nifi,never()).setControllerServiceRunStatus(eq(id),anyString());
            verify(nifi,never()).waitForControllerServiceEnabled(eq(id),anyLong());
            verify(nifi,never()).putWithRevision(contains("/controller-services/"+id),anyString(),any());
        }
    }
    private Created namedProcessor(String name) { return processors.stream().filter(value -> name.equals(value.name())).findFirst().orElseThrow(); }
    private static Pipeline.Node source(String id, String type, Map<String, Object> extras) {
        Map<String, Object> config = new LinkedHashMap<>(extras); config.put("table", "EVENT_LOG"); config.putIfAbsent("incrementalColumn", "EVENT_TIME");
        return new Pipeline.Node(id, "source." + type, id, "source", 0, 0, config);
    }
    private static Pipeline flow(List<Pipeline.Node> nodes) {
        return new Pipeline("temporal-test", "Temporal codec test", null, null, null,
                new Pipeline.Dsl(1, nodes, nodes.stream().anyMatch(n->n.id().equals("mapping"))?List.of(new Pipeline.Edge("edge","source","mapping","default")):List.of()), null, null, null, null, null, null, null);
    }
    private static NifiEntity entity(String id) { return new NifiEntity(null, Map.of("id", id), null, null); }
}
