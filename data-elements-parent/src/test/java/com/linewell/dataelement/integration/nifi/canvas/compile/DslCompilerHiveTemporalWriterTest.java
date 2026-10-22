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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DslCompilerHiveTemporalWriterTest {
    private record Created(String id, String type, String name, Map<String, String> properties) {}
    private final List<Created> services = new ArrayList<>();
    private final List<Created> processors = new ArrayList<>();
    private final NifiClient nifi = mock(NifiClient.class);
    private final DslCompiler compiler;

    DslCompilerHiveTemporalWriterTest() throws Exception {
        ManifestRegistry registry = mock(ManifestRegistry.class);
        ObjectMapper json = new ObjectMapper();
        for (String name : List.of("hive", "mysql", "oracle", "oceanbase")) {
            try (var stream = getClass().getResourceAsStream("/manifests/sources/" + name + ".json")) {
                when(registry.get("source." + name)).thenReturn(json.readValue(stream, ComponentManifest.class));
            }
        }
        try (var stream = getClass().getResourceAsStream("/manifests/transforms/sql.json")) {
            when(registry.get("transform.sql")).thenReturn(json.readValue(stream, ComponentManifest.class));
        }
        try (var stream = getClass().getResourceAsStream("/manifests/transforms/field-mapping.json")) {
            when(registry.get("transform.field-mapping")).thenReturn(json.readValue(stream, ComponentManifest.class));
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
            ((Map<String, ?>)call.getArgument(0)).keySet().forEach(id -> plans.put(id, prepared()));
            return plans;
        });
        compiler.setUnifiedMappingService(unified);
        var versions=mock(MappingVersionStore.class);
        when(versions.archive(any(),anyMap())).thenReturn(Map.of("mapping","plan-version"));
        compiler.setMappingVersions(versions);
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
                .containsEntry("record-reader","global-json-reader").containsEntry("typed-record-reader","global-json-reader");
        assertThat(processor.type()).isEqualTo("com.linewell.dataelement.nifi.HaitongTransformRecord");
        assertEmbeddedMappingMetadata(processor);
        verify(nifi,never()).waitForControllerServiceEnabled(eq("true"),anyLong());
        assertThat(namedProcessor("source/execute").properties()).containsEntry("Use Avro Logical Types","true");
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
                .containsEntry("record-reader","global-json-reader").containsEntry("typed-record-reader","global-json-reader");
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

    @Test void mysqlUsesSameGlobalWriterAndReader() {
        compiler.compile(flow(List.of(source("m", "mysql", Map.of("dbType", "MYSQL")))));
        assertThat(namedProcessor("m/execute").properties()).containsEntry("Record Writer", "global-json-writer");
        assertNoTaskJsonServices();
    }

    @Test void mixedSourcesSelectTheWriterOfTheirDatabase() {
        compiler.compile(flow(List.of(source("h", "hive", Map.of()), source("m", "mysql", Map.of("dbType", "MYSQL")))));
        assertThat(namedProcessor("h/execute").properties()).containsEntry("Record Writer", "global-hive-json-writer");
        assertThat(namedProcessor("m/execute").properties()).containsEntry("Record Writer", "global-json-writer");
        assertNoTaskJsonServices();
    }

    @Test void queryExecutedDirectlyInHiveUsesTheSameTemporalWriter() {
        Pipeline.Node source = source("h", "hive", Map.of());
        Pipeline.Node query = new Pipeline.Node("q", "transform.sql", "query", "transform", 300, 0,
                Map.of("executionMode", "SOURCE_DB", "sql", "SELECT event_time FROM EVENT_LOG"));
        Pipeline base = flow(List.of(source, query));
        Pipeline pipeline = new Pipeline(base.id(), base.name(), null, null, null,
                new Pipeline.Dsl(1, base.dsl().nodes(), List.of(new Pipeline.Edge("edge", "h", "q", "default"))),
                null, null, null, null, null, null, null);
        compiler.compile(pipeline);
        assertThat(namedProcessor("query/query").properties()).containsEntry("Record Writer", "global-hive-json-writer");
    }

    @ParameterizedTest @ValueSource(ints = {20, 100})
    void sourcesShareOneTemporalWriterWithoutServiceLookupPerNode(int count) {
        List<Pipeline.Node> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) nodes.add(source("h" + i, "hive", Map.of()));
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
    void hiveTargetUsesRawSourceAndNamedCodecsRegardlessOfSourceDatabase(String sourceDatabase) {
        compiler.compile(hiveTargetFlow(List.of(source("source",sourceDatabase,Map.of()),mapping(),hiveSink("sink")),
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
        List<Pipeline.Node> nodes=new ArrayList<>();List<Pipeline.Edge> edges=new ArrayList<>();
        for(int i=0;i<count;i++) {
            nodes.add(source("s"+i,"mysql",Map.of()));nodes.add(hiveSink("h"+i));
            edges.add(new Pipeline.Edge("e"+i,"s"+i,"h"+i,"default"));
        }
        compiler.compile(hiveTargetFlow(nodes,edges));
        assertThat(services.stream().filter(s->HiveTemporalJsonPolicy.WRITER_NAME.equals(s.name()))).isEmpty();
        assertThat(services.stream().filter(s->HiveTemporalJsonPolicy.READER_NAME.equals(s.name()))).isEmpty();
        verify(nifi,times(1)).listControllerServices("root");
        verify(nifi,times(1)).findProcessorType("PutHwHDFS");
        assertThat(processors).noneMatch(p->p.type().endsWith(".MergeRecord"));
    }

    @Test void unrelatedMysqlSourceAlsoUsesThePublicWriter() {
        compiler.compile(hiveTargetFlow(List.of(source("s","mysql",Map.of()),source("unrelated","mysql",Map.of()),hiveSink("h")),List.of(new Pipeline.Edge("e","s","h","default"))));
        assertThat(namedProcessor("s/execute").properties()).containsEntry("Record Writer","global-hive-json-writer");
        assertThat(namedProcessor("unrelated/execute").properties()).containsEntry("Record Writer","global-json-writer");
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
        var pipeline=hiveTargetFlow(List.of(source("source","mysql",Map.of()),mapping(),hiveSink("h"),jdbc),List.of(new Pipeline.Edge("sm","source","mapping","default"),new Pipeline.Edge("mh","mapping","h","default"),new Pipeline.Edge("mj","mapping","jdbc","default")));
        compiler.createMappingReplacement(pipeline,mapping(),"existing-group","reader","typed","global-json-writer",prepared());
        assertThat(services).isEmpty();
        assertThat(namedProcessor("字段映射").properties()).containsEntry("record-writer","global-json-writer");
        verify(nifi,times(1)).listControllerServices("root");
    }
    private static Pipeline.Node hiveSink(String id){return new Pipeline.Node(id,"sink.hive",id,"sink",600,0,Map.of("table","EVENT_LOG","database","default"));}
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
