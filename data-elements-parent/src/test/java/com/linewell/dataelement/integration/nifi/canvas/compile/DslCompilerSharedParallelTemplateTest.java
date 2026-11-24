package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ComponentManifest;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingVersionStore;
import com.linewell.dataelement.integration.nifi.canvas.mapping.RegisteredDictionaryCatalog;
import com.linewell.dataelement.integration.nifi.canvas.mapping.RegisteredMappingSchemaService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.mapping.core.MappingEngine;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DslCompilerSharedParallelTemplateTest {
    private static final String TIMESTAMP_TYPE="com.linewell.microservice.processor.record.TimestampFormatProcessor";
    private static final String EXACT_READER="com.linewell.dataelement.nifi.HaitongJdbcRecordReader";
    private static final ObjectMapper JSON=new ObjectMapper();
    private record Created(String id,String type,String name,Map<String,String> properties) {}
    private record CreatedService(String id,String group,String type,String name,Map<String,String> properties) {}
    private final NifiClient nifi=mock(NifiClient.class);
    private final List<Created> processors=new ArrayList<>();
    private final List<CreatedService> services=new ArrayList<>();
    private final Map<String,String> processorComments=new LinkedHashMap<>();
    private DslCompiler compiler;
    private MappingEngine.Plan plan;

    @BeforeEach void setup() throws Exception {
        var registry=mock(ManifestRegistry.class);
        for(String file:List.of("sources/oracle","sinks/jdbc-sink","sinks/hive-sink","transforms/field-enrichment")) {
            try(var stream=getClass().getResourceAsStream("/manifests/"+file+".json")) {
                var manifest=JSON.readValue(stream,ComponentManifest.class);when(registry.get(manifest.key())).thenReturn(manifest);
            }
        }
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        HiveServiceFixtures.stubGlobals(nifi);
        when(nifi.listControllerServices("root")).thenReturn(HiveServiceFixtures.withCsv(JSON.valueToTree(Map.of("component",Map.of(
                "id","csv","name","PUBLIC-WRITER-CSV-HDFS","type","org.apache.nifi.csv.CSVRecordSetWriter",
                "properties",Map.of("Include Header Line","false","Value Separator","\\u0001"))))));
        when(nifi.findProcessorType("LinewellPutDatabaseRecord")).thenReturn(Optional.of("com.linewell.microservice.processors.database.LinewellPutDatabaseRecord"));
        when(nifi.findProcessorType("PutHwHDFS")).thenReturn(Optional.of("com.linewell.microservice.processor.hadoop.PutHwHDFS"));
        when(nifi.findProcessorType(TIMESTAMP_TYPE)).thenReturn(Optional.of(TIMESTAMP_TYPE));
        when(nifi.findProcessorType("org.apache.nifi.processors.standard.QueryDatabaseTableRecord")).thenReturn(Optional.of("org.apache.nifi.processors.standard.QueryDatabaseTableRecord"));
        when(nifi.get("/flow/controller-service-types",JsonNode.class)).thenReturn(JSON.valueToTree(Map.of("controllerServiceTypes",List.of(Map.of(
                "type",EXACT_READER,"bundle",Map.of("group","com.linewell.dataelement","artifact","haitong-mapping-nar","version",MappingEngine.engineVersion()))))));
        when(nifi.createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble())).thenReturn(entity("pg"));
        var sequence=new AtomicInteger();
        when(nifi.createControllerService(anyString(),anyString(),anyString(),anyMap())).thenAnswer(call->{
            String id="cs-"+sequence.incrementAndGet();services.add(new CreatedService(id,call.getArgument(0),call.getArgument(1),call.getArgument(2),new LinkedHashMap<>(call.getArgument(3))));return entity(id);});
        when(nifi.createProcessor(anyString(),anyString(),anyString(),anyDouble(),anyDouble(),anyMap(),nullable(String.class),nullable(String.class)))
                .thenAnswer(call->{String id="processor-"+sequence.incrementAndGet();processors.add(new Created(id,call.getArgument(1),call.getArgument(2),new LinkedHashMap<>(call.getArgument(5))));return entity(id);});
        when(nifi.createProcessor(anyString(),anyString(),anyString(),anyDouble(),anyDouble(),anyMap(),nullable(String.class),nullable(String.class),nullable(String.class)))
                .thenAnswer(call->{String id="processor-"+sequence.incrementAndGet();processors.add(new Created(id,call.getArgument(1),call.getArgument(2),new LinkedHashMap<>(call.getArgument(5))));processorComments.put(id,call.getArgument(8));return entity(id);});
        when(nifi.get("/flow/process-groups/pg",JsonNode.class)).thenAnswer(call->processorSnapshot());
        when(nifi.createConnection(anyString(),anyString(),anyString(),anyString(),anyString(),anyList())).thenAnswer(call->entity("edge-"+sequence.incrementAndGet()));
        when(nifi.createBufferedConnection(anyString(),anyString(),anyString(),anyString(),anyList())).thenAnswer(call->entity("edge-"+sequence.incrementAndGet()));
        when(nifi.createRetentionFunnel(anyString(),anyDouble(),anyDouble())).thenAnswer(call->entity("funnel-"+sequence.incrementAndGet()));
        when(nifi.configureWriteFailureRetries(any())).thenReturn(List.of("failure"));
        var hive=mock(HiveModule.class);
        when(hive.resolveRuntimeProfile("default")).thenReturn(Map.of("dbType","HIVE","jdbcUrl","jdbc:hive2://validation.invalid:10000/default",
                "hiveConnectionMode","JDBC","clientConfigDir","/validation/client","keytabPath","/validation/user.keytab","krb5ConfPath","/validation/krb5.conf","userPrincipal","validation"));
        compiler=new DslCompiler(nifi,registry,mock(FieldMappingService.class),hive);
        plan=MappingEngine.compile(JSON.readTree("""
            {"version":"1.0","mappings":[{"to":"/ID","constant":"row-key"},
              {"to":"/ODS_UUID","expression":"UUID()"},
              {"to":"/ODS_RKSJ","expression":"LOCALTIMESTAMP","targetType":"DATETIME"}]}
            """));
        var unified=mock(UnifiedMappingService.class);
        when(unified.compileBatch(anyMap())).thenAnswer(call->{Map<String,UnifiedMappingService.Compiled> values=new LinkedHashMap<>();call.<Map<String,?>>getArgument(0).keySet().forEach(id->values.put(id,new UnifiedMappingService.Compiled(plan,Map.of())));return values;});
        compiler.setUnifiedMappingService(unified);
        var versions=mock(MappingVersionStore.class);when(versions.archive(any(),anyMap())).thenReturn(Map.of("mapping","plan-version"));compiler.setMappingVersions(versions);
    }

    @Test void deploymentAuthorizesDirectRegisteredSourceForLegacyDecimalToHiveInteger() throws Exception {
        var fields = Map.of(
                "registered-table", JSON.readTree("""
                        [{"name":"status","logicalType":"INT","physicalType":"tinyint(1)"}]
                        """),
                "oracle-table", JSON.readTree("""
                        [{"name":"STATUS","logicalType":"DECIMAL","physicalType":"NUMBER","precision":5,"scale":0}]
                        """),
                "hive-table", JSON.readTree("""
                        [{"name":"status","logicalType":"INT","physicalType":"SMALLINT"}]
                        """));
        var dictionaries = mock(RegisteredDictionaryCatalog.class);
        when(dictionaries.bind(any())).thenAnswer(call -> new RegisteredDictionaryCatalog.Bound(call.getArgument(0), Map.of()));
        var mappingService = new UnifiedMappingService(dictionaries, new FieldMappingService());
        var schemas = spy(new RegisteredMappingSchemaService(null, null, mappingService));
        doAnswer(call -> {
            Map<String, JsonNode> references = call.getArgument(0);
            var resolved = new LinkedHashMap<String, RegisteredMappingSchemaService.Resolved>();
            references.forEach((hash, ref) -> {
                String table = ref.path("tableId").asText();
                resolved.put(hash, new RegisteredMappingSchemaService.Resolved(ref.path("datasourceId").asText(),
                        table, "", table, fields.get(table), Map.of(), false, false, null));
            });
            return resolved;
        }).when(schemas).resolve(anyMap());
        doAnswer(call -> {
            Map<String, RegisteredMappingSchemaService.Resolved> resolved = call.getArgument(0);
            var physical = new LinkedHashMap<String, JsonNode>();
            resolved.forEach((hash, table) -> physical.put(hash, table.fields().deepCopy()));
            return physical;
        }).when(schemas).physicalBatch(anyMap());
        mappingService.setSchemas(schemas);
        compiler.setUnifiedMappingService(mappingService);

        var input = new Pipeline.Node("source", "source.mysql", "登记来源", "source", 0, 0,
                Map.of("registeredDatasourceId", "registered-db", "sourceTableId", "registered-table"));
        var governance = new Pipeline.Node("mapping", "transform.field-enrichment", "字段治理", "transform", 0, 0,
                Map.of("mappings", JSON.readTree("""
                        {"version":"1.0","executionEngine":"JAVA_RECORD","mappings":[
                          {"from":"/status","to":"/STATUS","expression":"CAST(${field:status} AS INTEGER)","targetType":"NUMBER(5)"}
                        ]}
                        """)));
        var oracle = new Pipeline.Node("jdbc", "sink.jdbc", "Oracle", "sink", 0, 0,
                Map.of("targetDbId", "oracle-db", "targetTableId", "oracle-table"));
        var hiveTarget = new Pipeline.Node("hive", "sink.hive", "Hive", "sink", 0, 0,
                Map.of("targetDbId", "hive-db", "targetTableId", "hive-table"));
        var edges = List.of(edge("sm", "source", "mapping"), edge("mj", "mapping", "jdbc"), edge("mh", "mapping", "hive"));
        compiler.preflightMapping(flow(List.of(input, governance, oracle, hiveTarget), edges));
        verify(schemas).resolve(argThat(refs -> refs.size() == 3 && refs.values().stream()
                .anyMatch(ref -> "registered-table".equals(ref.path("tableId").asText()))));
        verify(schemas).physicalBatch(anyMap());
        verify(nifi).requireMappingExtension();

        var custom = new Pipeline.Node(input.id(), input.manifestKey(), input.label(), input.category(), 0, 0,
                Map.of("registeredDatasourceId", "registered-db", "sourceTableId", "registered-table", "customQuery", "SELECT 40000 AS status"));
        assertThatThrownBy(() -> compiler.preflightMapping(flow(List.of(custom, governance, oracle, hiveTarget), edges)))
                .isInstanceOfSatisfying(MappingException.class, error -> assertThat(error.code()).isEqualTo("TARGET_TYPE_MISMATCH"));
    }

    @Test void oneGovernanceFeedsIndependentInsertFallbackAndExplicitHiveTimestampConversion() {
        var source=source("source",Map.of("manualBackfill",true,"incrementalColumn","UPDATE_TIME","customWherePredicate","ID > 0","customQuery","SELECT ID FROM EVENT_LOG"));
        var mapping=new Pipeline.Node("mapping","transform.field-enrichment","共享字段治理","transform",500,0,Map.of("mappings",plan.document()));
        var jdbc=jdbc(Map.of("insertThenMerge",true));
        var hive=hive(Map.of("hiveTimestampFormat",true));
        var pipeline=flow(List.of(source,mapping,jdbc,hive),List.of(edge("sm","source","mapping"),edge("mj","mapping","jdbc"),edge("mh","mapping","hive")));
        var result=compiler.compile(pipeline);
        assertThat(pipeline.dsl().nodes()).hasSize(4);
        assertThat(result.nodes()).containsOnlyKeys("source","mapping","jdbc","hive");
        assertThat(processors.stream().filter(p->p.type().endsWith(".HaitongTransformRecord"))).hasSize(1);
        var replay=processor("source/备用补数/fetch");
        assertThat(replay.properties()).containsEntry("Additional WHERE Clause","1=0")
                .doesNotContainKeys("Maximum-value Columns","Custom Query");
        assertThat(result.nodes().get("source").processorIds).containsEntry("manual-backfill-fetch",replay.id());
        assertThat(result.nodes().get("source").processorIds.values().iterator().next()).isEqualTo(processor("source/fetch").id());
        verify(nifi).executeOnPrimaryNode(replay.id());
        var order=inOrder(nifi);
        order.verify(nifi).createConnection("pg",replay.id(),"PROCESSOR",result.nodes().get("mapping").inletProcessorId,"PROCESSOR",List.of("success"));
        order.verify(nifi).finalizeGeneratedProcessGroup("pg");
        order.verify(nifi).put(eq("/processors/"+replay.id()+"/run-status"),argThat(body->body instanceof JsonNode request
                &&"DISABLED".equals(request.path("state").asText())),eq(NifiEntity.class));
        var insert=processor("Oracle目标/insert");var merge=processor("Oracle目标/put");
        assertThat(insert.properties()).containsEntry("put-db-record-statement-type","INSERT");
        assertThat(merge.properties()).containsEntry("put-db-record-statement-type","MERGE").containsEntry("merge update keys","ID");
        verify(nifi).createConnection("pg",insert.id(),"PROCESSOR",merge.id(),"PROCESSOR",List.of("failure"));
        verify(nifi).createConnection("pg",insert.id(),"PROCESSOR",insert.id(),"PROCESSOR",List.of("retry"));
        verify(nifi,never()).createConnection(eq("pg"),eq(insert.id()),anyString(),eq(merge.id()),anyString(),eq(List.of("failure","retry")));
        var timestamp=processor("Hive目标/timestamp-format");
        assertThat(timestamp.properties()).containsEntry("timestamp-fields","ODS_RKSJ").containsEntry("timestamp-unit","milliseconds")
                .containsEntry("auto-detect-timestamp-fields","false").containsEntry("date-format","yyyy-MM-dd HH:mm:ss");
        assertThat(result.nodes().get("hive").inletProcessorId).isEqualTo(timestamp.id());
        verify(nifi).createConnection("pg",timestamp.id(),"PROCESSOR",processor("Hive目标/merge-records").id(),"PROCESSOR",List.of("success"));
        assertThat(processor("Oracle目标/normalize").properties().get("success")).doesNotContain("ODS_RKSJ").contains("ODS_UUID");
    }

    @Test void manualReplayIsDisabledPrimaryOnlyWithoutWatermarkAndKeepsItsPredicate() {
        var replay=source("backfill",Map.of("backfillSource",true,"incrementalColumn","UPDATE_TIME","customWherePredicate","ID >= 20 AND ID < 100","batchSize",1000,"maxRowsPerRun",5000));
        compiler.compile(flow(List.of(replay,jdbc(Map.of())),List.of(edge("replay-target","backfill","jdbc"))));
        var fetch=processor("backfill/fetch");
        assertThat(fetch.type()).endsWith(".QueryDatabaseTableRecord");
        assertThat(fetch.properties()).doesNotContainKey("Maximum-value Columns").containsEntry("Additional WHERE Clause","ID >= 20 AND ID < 100")
                .containsEntry("Initial Load Strategy","Start at Beginning").containsEntry("Max Rows Per FlowFile","1000").containsEntry("Fetch Size","5000");
        verify(nifi).executeOnPrimaryNode(fetch.id());
        verify(nifi).createProcessor(eq("pg"),anyString(),eq("backfill/fetch"),anyDouble(),anyDouble(),anyMap(),eq("1 min"),eq("TIMER_DRIVEN"),eq(NifiClient.MANUAL_BACKFILL_MARKER));
        verify(nifi,never()).setProcessorRunStatus(anyString(),anyString());
        var order=inOrder(nifi);
        order.verify(nifi).createConnection("pg",fetch.id(),"PROCESSOR",processor("Oracle目标/put").id(),"PROCESSOR",List.of("success"));
        order.verify(nifi).finalizeGeneratedProcessGroup("pg");
        order.verify(nifi).get("/flow/process-groups/pg",JsonNode.class);
        order.verify(nifi).put(eq("/processors/"+fetch.id()+"/run-status"),argThat(body->body instanceof JsonNode request
                &&"DISABLED".equals(request.path("state").asText())&&request.path("revision").path("version").asLong(-1)==7L
                &&!request.path("disconnectedNodeAcknowledged").asBoolean()),eq(NifiEntity.class));
        verify(nifi,times(1)).get("/flow/process-groups/pg",JsonNode.class);
        assertThat(processors).hasSize(2);
    }

    @Test void allBackfillMarkersAreValidatedBeforeAnySourceIsDisabled() {
        when(nifi.get("/flow/process-groups/pg",JsonNode.class)).thenAnswer(call->{
            var snapshot=processorSnapshot();
            var second=processor("second/fetch");
            for(JsonNode entity:snapshot.path("processGroupFlow").path("flow").path("processors"))
                if(second.id().equals(entity.path("id").asText()))((com.fasterxml.jackson.databind.node.ObjectNode)entity.path("component").path("config")).put("comments","");
            return snapshot;
        });
        var first=source("first",Map.of("backfillSource",true,"customWherePredicate","ID >= 20 AND ID < 40"));
        var second=source("second",Map.of("backfillSource",true,"customWherePredicate","ID >= 40 AND ID < 100"));
        assertThatThrownBy(()->compiler.compile(flow(List.of(first,second,jdbc(Map.of())),List.of(
                edge("first-target","first","jdbc"),edge("second-target","second","jdbc")))))
                .hasMessageContaining("补数入口状态或标记不完整");
        verify(nifi).finalizeGeneratedProcessGroup("pg");
        verify(nifi,times(1)).get("/flow/process-groups/pg",JsonNode.class);
        verify(nifi,never()).put(contains("/run-status"),any(),eq(NifiEntity.class));
    }

    @Test void invalidReplayQueryIsRejectedBeforeAnyGroupIsCreated() {
        assertThatThrownBy(()->compiler.compile(flow(List.of(source("backfill",Map.of("backfillSource",true,"customQuery","DELETE FROM EVENT_LOG"))),List.of())))
                .hasMessageContaining("SELECT");
        verify(nifi,never()).createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble());
    }

    @Test void nativeReplayDoesNotDuplicateAnExplicitReplayForTheSameRegisteredSource() {
        var main=source("source",Map.of("manualBackfill",true));
        var explicit=source("backfill",Map.of("backfillSource",true,"customWherePredicate","ID >= 20 AND ID < 100"));
        var result=compiler.compile(flow(List.of(main,explicit,jdbc(Map.of())),List.of(edge("main-target","source","jdbc"),edge("replay-target","backfill","jdbc"))));
        assertThat(processors.stream().filter(p->p.type().endsWith(".QueryDatabaseTableRecord"))).hasSize(1);
        assertThat(result.nodes().get("source").processorIds).doesNotContainKey("manual-backfill-fetch");
        assertThat(processor("backfill/fetch").properties()).containsEntry("Additional WHERE Clause","ID >= 20 AND ID < 100");
    }

    @Test void nativeReplayOnUnsupportedSourceIsRejectedBeforeCreatingAGroup() {
        var source=new Pipeline.Node("api","source.api","接口来源","source",0,0,Map.of("manualBackfill",true));
        assertThatThrownBy(()->compiler.compile(flow(List.of(source),List.of()))).hasMessageContaining("JDBC");
        verify(nifi,never()).createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble());
    }

    @Test void missingNativeReplayProcessorIsRejectedBeforeCreatingAGroup() {
        when(nifi.findProcessorType("org.apache.nifi.processors.standard.QueryDatabaseTableRecord")).thenReturn(Optional.empty());
        assertThatThrownBy(()->compiler.compile(flow(List.of(source("source",Map.of("manualBackfill",true))),List.of())))
                .hasMessageContaining("QueryDatabaseTableRecord");
        verify(nifi,never()).createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble());
    }

    @ParameterizedTest @ValueSource(strings={"DATE","TIMESTAMP","TIMESTAMP(6)"})
    void onlyOracleDateFetcherUsesTaskLocalStandardTimestampPool(String temporalType) {
        var source=source("source",Map.of("syncMode","FULL_THEN_INCR","incrementalColumn","UPDATE_TIME","manualBackfill",true,
                "jdbcProperties",Map.of("oracle.jdbc.J2EE13Compliant","false","oracle.net.CONNECT_TIMEOUT","5000"),
                "sourceColumns",List.of(Map.of("columnName","ID","dataType","NUMBER(38)"),Map.of("columnName","UPDATE_TIME","dataType",temporalType))));
        var result=compiler.compile(flow(List.of(source,jdbc(Map.of())),List.of(edge("source-target","source","jdbc"))));
        var compiled=result.nodes().get("source");
        var original=service(compiled.csIds.get("dbcp"));var fetchPool=service(compiled.csIds.get("date-fetch-dbcp"));
        assertThat(fetchPool.group()).isEqualTo("pg");assertThat(fetchPool.name()).isEqualTo("source/date-fetch-dbcp");
        Map<String,String> expected=new LinkedHashMap<>(original.properties());expected.put("oracle.jdbc.J2EE13Compliant","true");
        assertThat(fetchPool.properties()).isEqualTo(expected);
        assertThat(original.properties()).containsEntry("oracle.jdbc.J2EE13Compliant","false").containsEntry("oracle.net.CONNECT_TIMEOUT","5000");
        assertThat(processor("source/fetch").type()).isEqualTo(DateTableFetchPolicy.TYPE);
        assertThat(processor("source/fetch").properties()).containsEntry("Database Connection Pooling Service",fetchPool.id());
        assertThat(processor("source/execute").properties()).containsEntry("Database Connection Pooling Service",original.id());
        assertThat(processor("source/备用补数/fetch").properties()).containsEntry("Database Connection Pooling Service",original.id());
        assertThat(source.config().get("jdbcProperties")).isEqualTo(Map.of("oracle.jdbc.J2EE13Compliant","false","oracle.net.CONNECT_TIMEOUT","5000"));
        var order=inOrder(nifi);
        order.verify(nifi).createControllerService(eq("pg"),eq("org.apache.nifi.dbcp.DBCPConnectionPool"),eq("source/date-fetch-dbcp"),anyMap());
        order.verify(nifi).enableControllerServices(eq("pg"),argThat(ids->ids.contains(original.id())&&ids.contains(fetchPool.id())),anySet(),eq(30_000L));
        order.verify(nifi).createProcessor(eq("pg"),eq(DateTableFetchPolicy.TYPE),eq("source/fetch"),anyDouble(),anyDouble(),anyMap(),nullable(String.class),nullable(String.class));
    }

    @ParameterizedTest @ValueSource(strings={"FULL","MYSQL","OCEANBASE_ORACLE","UNREGISTERED"})
    void standardAndOtherVendorSourcesDoNotReceiveOracleFetchCompatibilityPool(String condition) {
        Map<String,Object> config=new LinkedHashMap<>(Map.of("syncMode","FULL_THEN_INCR","incrementalColumn","UPDATE_TIME",
                "sourceColumns",List.of(Map.of("columnName","UPDATE_TIME","dataType","TIMESTAMP"))));
        if("FULL".equals(condition))config.put("syncMode","FULL");
        if("MYSQL".equals(condition)){config.put("dbType","MYSQL");config.put("jdbcUrl","jdbc:mysql://validation.invalid:3306/validation");}
        if("OCEANBASE_ORACLE".equals(condition)){config.put("dbType","OCEANBASE_ORACLE");config.put("jdbcUrl","jdbc:oceanbase:oracle://validation.invalid:2881/validation");}
        var configured=source("source",config);
        if("UNREGISTERED".equals(condition)) {
            Map<String,Object> legacy=new LinkedHashMap<>(configured.config());legacy.remove("registeredDatasourceId");legacy.remove("sourceTableId");
            configured=new Pipeline.Node(configured.id(),configured.manifestKey(),configured.label(),configured.category(),0,0,legacy);
        }
        var result=compiler.compile(flow(List.of(configured),List.of()));
        assertThat(result.nodes().get("source").csIds).doesNotContainKey("date-fetch-dbcp");
        assertThat(services).noneMatch(service->service.properties().containsKey("oracle.jdbc.J2EE13Compliant"));
        if("FULL".equals(condition))assertThat(processor("source/fetch").type()).isEqualTo(DateTableFetchPolicy.NATIVE_TYPE);
        if("OCEANBASE_ORACLE".equals(condition))assertThat(service(result.nodes().get("source").csIds.get("dbcp")).properties())
                .doesNotContainEntry("Database Driver Class Name","oracle.jdbc.OracleDriver");
    }

    @ParameterizedTest @ValueSource(strings={"NUMBER(38)","VARCHAR2(50)","TIMESTAMP WITH TIME ZONE","TIMESTAMP(6) WITH LOCAL TIME ZONE"})
    void registeredNonTemporalAndZonedWatermarksAreRejectedBeforeCreatingAGroup(String type) {
        var source=source("source",Map.of("syncMode","FULL_THEN_INCR","incrementalColumn","UPDATE_TIME",
                "sourceColumns",List.of(Map.of("columnName","UPDATE_TIME","dataType",type))));
        assertThatThrownBy(()->compiler.compile(flow(List.of(source),List.of()))).hasMessageContaining("UPDATE_TIME").hasMessageContaining(type);
        verify(nifi,never()).createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble());
        verify(nifi,never()).createControllerService(anyString(),anyString(),anyString(),anyMap());
    }

    @Test void hiveNumericFieldsKeepOneExactReaderAcrossBothFormattersMergeAndCsvConversion() throws Exception {
        plan=MappingEngine.compile(JSON.readTree("""
            {"version":"1.0","mappings":[{"to":"/ID","constant":"9223372036854775806","targetType":"LONG"},
            {"to":"/AMOUNT","constant":"12345678901234.123456","targetType":"DECIMAL"},
            {"to":"/EVENT_TIME","constant":"2026-11-23 10:20:30","targetType":"DATETIME"},
            {"to":"/BIRTHDAY","constant":"2026-11-23","targetType":"DATE"}]}
            """));
        var globalServices=HiveServiceFixtures.withCsv(JSON.valueToTree(Map.of("component",Map.of("id","csv","name","PUBLIC-WRITER-CSV-HDFS",
                "type","org.apache.nifi.csv.CSVRecordSetWriter","properties",Map.of("Include Header Line","false","Value Separator","\\u0001")))));
        for(JsonNode service:globalServices)if("global-json-reader".equals(service.path("component").path("id").asText()))
            ((com.fasterxml.jackson.databind.node.ObjectNode)service.path("component")).putObject("properties")
                    .put("Schema Access Strategy","infer-schema").put("Starting Field Strategy","ROOT_NODE")
                    .put("Date Format","yyyy-MM-dd").put("Timestamp Format","yyyy-MM-dd HH:mm:ss.SSS").put("Max String Length","20 MB");
        when(nifi.listControllerServices("root")).thenReturn(globalServices);
        var mapping=new Pipeline.Node("mapping","transform.field-enrichment","共享字段治理","transform",500,0,Map.of("mappings",plan.document()));
        var hive=hive(Map.of("hiveTimestampFormat",true,"targetColumns",List.of(
                Map.of("columnName","id","dataType","BIGINT"),Map.of("columnName","amount","dataType","DECIMAL(20,6)"),
                Map.of("columnName","event_time","dataType","TIMESTAMP"),Map.of("columnName","birthday","dataType","DATE"))));
        var result=compiler.compile(flow(List.of(source("source",Map.of()),mapping,jdbc(Map.of()),hive),List.of(
                edge("sm","source","mapping"),edge("mj","mapping","jdbc"),edge("mh","mapping","hive"))));
        var reader=service(result.nodes().get("hive").csIds.get("hive-value-reader"));
        assertThat(reader.group()).isEqualTo("pg");assertThat(reader.type()).isEqualTo(EXACT_READER);
        assertThat(reader.properties()).containsEntry("number-fields","[\"ID\",\"AMOUNT\"]")
                .containsEntry("Date Format","yyyy-MM-dd").containsEntry("Timestamp Format","yyyy-MM-dd HH:mm:ss.SSS")
                .containsEntry("Max String Length","20 MB").doesNotContainKeys("binary-fields","delegate-reader");
        for(String name:List.of("Hive目标/timestamp-format","Hive目标/date-format"))
            assertThat(processor(name).properties()).containsEntry("record-reader",reader.id());
        for(String name:List.of("Hive目标/merge-records","Hive目标/convert"))
            assertThat(processor(name).properties()).containsEntry("Record Reader",reader.id());
        assertThat(processors.stream().filter(p->p.type().endsWith(".HaitongTransformRecord"))).hasSize(1);
        assertThat(processors.stream().filter(p->p.type().endsWith(".HaitongTransformRecord")).findFirst().orElseThrow().properties())
                .containsEntry("record-reader","global-json-reader");
        verify(nifi,times(1)).get("/flow/controller-service-types",JsonNode.class);
        var order=inOrder(nifi);
        order.verify(nifi).createControllerService(eq("pg"),eq(EXACT_READER),eq("Hive目标/Hive精确数值Reader"),anyMap());
        order.verify(nifi).enableControllerServices(eq("pg"),argThat(ids->ids.contains(reader.id())),anySet(),eq(30_000L));
        order.verify(nifi).createProcessor(eq("pg"),eq(TIMESTAMP_TYPE),eq("Hive目标/timestamp-format"),anyDouble(),anyDouble(),anyMap(),nullable(String.class),nullable(String.class));
    }

    @ParameterizedTest @ValueSource(ints={20,100})
    void hiveExactReaderCapabilityIsReadOnceForAllRegisteredNumericTargets(int count) {
        List<Pipeline.Node> nodes=new ArrayList<>();
        for(int i=0;i<count;i++){
            var template=hive(Map.of("targetColumns",List.of(Map.of("columnName","amount","dataType","NUMERIC(20,6)"))));
            nodes.add(new Pipeline.Node("h"+i,template.manifestKey(),template.label(),template.category(),0,0,template.config()));
        }
        compiler.preflightWriters(flow(nodes,List.of()));
        verify(nifi,times(1)).get("/flow/controller-service-types",JsonNode.class);
        verify(nifi,never()).createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble());
    }

    @Test void hiveStringTargetPreservesSharedOracleNumbersThroughMergeAndCsvWithoutDoubleInference()throws Exception {
        plan=MappingEngine.compile(JSON.readTree("""
            {"version":"1.0","mappings":[{"to":"/OPRSTATUS","constant":"-12345678901234567890.123456789","targetType":"NUMBER"}]}
            """));
        var globalServices=HiveServiceFixtures.withCsv(JSON.valueToTree(Map.of("component",Map.of("id","csv","name","PUBLIC-WRITER-CSV-HDFS",
                "type","org.apache.nifi.csv.CSVRecordSetWriter","properties",Map.of("Include Header Line","false","Value Separator","\\u0001")))));
        for(JsonNode service:globalServices)if("global-json-reader".equals(service.path("component").path("id").asText()))
            ((com.fasterxml.jackson.databind.node.ObjectNode)service.path("component")).putObject("properties")
                    .put("Schema Access Strategy","infer-schema").put("Starting Field Strategy","ROOT_NODE");
        when(nifi.listControllerServices("root")).thenReturn(globalServices);
        var mapping=new Pipeline.Node("mapping","transform.field-enrichment","共享字段治理","transform",500,0,Map.of("mappings",plan.document()));
        var hive=hive(Map.of("targetColumns",List.of(Map.of("columnName","oprstatus","dataType","STRING"))));
        var pipeline=flow(List.of(source("source",Map.of()),mapping,jdbc(Map.of()),hive),List.of(
                edge("sm","source","mapping"),edge("mj","mapping","jdbc"),edge("mh","mapping","hive")));
        var result=compiler.compile(pipeline);
        var reader=service(result.nodes().get("hive").csIds.get("hive-value-reader"));
        assertThat(reader.type()).isEqualTo(EXACT_READER);assertThat(reader.properties()).containsEntry("number-fields","[\"OPRSTATUS\"]");
        for(String name:List.of("Hive目标/merge-records","Hive目标/convert"))
            assertThat(processor(name).properties()).containsEntry("Record Reader",reader.id());
        verify(nifi,times(1)).get("/flow/controller-service-types",JsonNode.class);
        var guard=inOrder(nifi);guard.verify(nifi).get("/flow/controller-service-types",JsonNode.class);
        guard.verify(nifi).createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble());
    }

    @Test void missingHiveExactReaderStopsBeforeAnyGroupIsCreated() {
        when(nifi.get("/flow/controller-service-types",JsonNode.class)).thenReturn(JSON.valueToTree(Map.of("controllerServiceTypes",List.of())));
        var hive=hive(Map.of("targetColumns",List.of(Map.of("columnName","amount","dataType","DECIMAL(20,6)"))));
        assertThatThrownBy(()->compiler.compile(flow(List.of(hive),List.of())))
                .isInstanceOf(DslCompiler.NodeConfigException.class).hasMessageContaining("HaitongJdbcRecordReader");
        verify(nifi,never()).createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble());
    }

    @Test void ambiguousHiveNumericCanonicalFieldFailsBeforeCreatingItsReader() {
        var source=source("source",Map.of("sourceColumns",List.of(Map.of("columnName","AMOUNT","dataType","DECIMAL(20,6)"),
                Map.of("columnName","amount","dataType","DECIMAL(20,6)"))));
        var hive=hive(Map.of("targetColumns",List.of(Map.of("columnName","aMoUnT","dataType","DECIMAL(20,6)"))));
        assertThatThrownBy(()->compiler.compile(flow(List.of(source,hive),List.of(edge("sh","source","hive")))))
                .isInstanceOf(DslCompiler.NodeConfigException.class).hasMessageContaining("大小写歧义");
        assertThat(services).noneMatch(service->service.name().equals("Hive目标/Hive精确数值Reader"));
    }

    @Test void exactNumericFieldCaseWinsBeforeCaseInsensitiveMatching() {
        var source=source("source",Map.of("sourceColumns",List.of(Map.of("columnName","AMOUNT","dataType","DECIMAL(20,6)"),
                Map.of("columnName","amount","dataType","DECIMAL(20,6)"))));
        var hive=hive(Map.of("targetColumns",List.of(Map.of("columnName","amount","dataType","DECIMAL(20,6)"))));
        var result=compiler.compile(flow(List.of(source,hive),List.of(edge("sh","source","hive"))));
        assertThat(service(result.nodes().get("hive").csIds.get("hive-value-reader")).properties()).containsEntry("number-fields","[\"amount\"]");
    }

    @Test void insertFallbackWithoutAKeyIsRejectedBeforeAnyGroupIsCreated() {
        var node=jdbc(Map.of("insertThenMerge",true,"targetColumns",List.of(Map.of("columnName","ID","dataType","VARCHAR2"))));
        assertThatThrownBy(()->compiler.compile(flow(List.of(node),List.of()))).hasMessageContaining("key");
        verify(nifi,never()).createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble());
    }

    @Test void dateFieldsHaveTheirOwnFormatWithoutSelectingLongNumericIdentifiers() {
        var source=source("source",Map.of("sourceColumns",List.of(Map.of("columnName","id","dataType","BIGINT"),Map.of("columnName","birthday","dataType","DATE"))));
        compiler.compile(flow(List.of(source,hive(Map.of("hiveTimestampFormat",true,"targetColumns",List.of(
                Map.of("columnName","id","dataType","BIGINT"),Map.of("columnName","birthday","dataType","DATE"))))),List.of(edge("sh","source","hive"))));
        var date=processor("Hive目标/date-format");
        assertThat(date.properties()).containsEntry("timestamp-fields","birthday").containsEntry("timestamp-unit","milliseconds")
                .containsEntry("date-format","yyyy-MM-dd").containsEntry("auto-detect-timestamp-fields","false");
        assertThat(processors.stream().filter(p->p.type().equals(TIMESTAMP_TYPE))).hasSize(1);
    }

    @ParameterizedTest @ValueSource(ints={20,100})
    void timestampCapabilityIsCheckedOnceForTheWholeGraph(int count) {
        var nodes=new ArrayList<Pipeline.Node>();
        for(int i=0;i<count;i++){var template=hive(Map.of("hiveTimestampFormat",true));nodes.add(new Pipeline.Node("h"+i,template.manifestKey(),template.label(),template.category(),0,0,template.config()));}
        compiler.preflightWriters(flow(nodes,List.of()));verify(nifi,times(1)).findProcessorType(TIMESTAMP_TYPE);
        verifyNoInteractionsAfterCapabilityRead();
    }
    private void verifyNoInteractionsAfterCapabilityRead(){verify(nifi,never()).createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble());}

    @Test void missingFormatterIsReportedBeforeAnyGroupIsCreated() {
        when(nifi.findProcessorType(TIMESTAMP_TYPE)).thenReturn(Optional.empty());
        assertThatThrownBy(()->compiler.compile(flow(List.of(hive(Map.of("hiveTimestampFormat",true))),List.of()))).hasMessageContaining("TimestampFormatProcessor");
        verify(nifi,never()).createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble());
    }

    @Test void configuredMergeAgeIsAppliedWithoutChangingExistingDefaultSizes() {
        compiler.compile(flow(List.of(hive(Map.of("mergeMaxBinAge","30 sec"))),List.of()));
        assertThat(processor("Hive目标/merge-records").properties()).containsEntry("Max Bin Age","30 sec")
                .containsEntry("Minimum Bin Size","128 MB").containsEntry("Maximum Bin Size","256 MB");
    }

    @ParameterizedTest @ValueSource(strings={"0 sec","2 days","not-a-duration"})
    void invalidMergeAgeIsRejectedBeforeCreatingAnyGroup(String age) {
        assertThatThrownBy(()->compiler.compile(flow(List.of(hive(Map.of("mergeMaxBinAge",age))),List.of()))).hasMessageContaining("Hive 合并最长等待");
        verify(nifi,never()).createProcessGroup(anyString(),anyString(),anyDouble(),anyDouble());
    }

    private static Pipeline.Node source(String id,Map<String,Object> extras) {
        Map<String,Object> cfg=new LinkedHashMap<>(Map.of("dbType","Oracle","host","validation.invalid","port",1521,"sid","VALIDATION","username","validation","password","validation-only","table","EVENT_LOG","syncMode","FULL","sourceColumns",List.of(Map.of("columnName","ID","dataType","VARCHAR2"))));cfg.putAll(extras);
        cfg.put("registeredDatasourceId","validation-source-db");cfg.put("sourceTableId","validation-source-table");
        return new Pipeline.Node(id,"source.oracle",id,"source",0,0,cfg);
    }
    private static Pipeline.Node jdbc(Map<String,Object> extras) {
        Map<String,Object> cfg=new LinkedHashMap<>(Map.of("dbType","Oracle","jdbcUrl","jdbc:oracle:thin:@//validation.invalid:1521/validation","username","validation","password","validation-only","table","EVENT_LOG","writerType","LINEWELL_PUT_DATABASE_RECORD","statementType","INSERT","targetColumns",List.of(
                Map.of("columnName","ID","dataType","VARCHAR2","primaryKey",true),Map.of("columnName","ODS_UUID","dataType","VARCHAR2"),Map.of("columnName","ODS_RKSJ","dataType","TIMESTAMP"))));cfg.putAll(extras);
        cfg.put("targetDbId","validation-oracle-db");cfg.put("targetTableId","validation-oracle-table");
        return new Pipeline.Node("jdbc","sink.jdbc","Oracle目标","sink",1000,0,cfg);
    }
    private static Pipeline.Node hive(Map<String,Object> extras) {
        Map<String,Object> cfg=new LinkedHashMap<>(Map.of("table","event_log","database","default","targetColumns",List.of(Map.of("columnName","id","dataType","STRING"),Map.of("columnName","ods_rksj","dataType","TIMESTAMP"))));cfg.putAll(extras);
        cfg.put("targetDbId","validation-hive-db");cfg.put("targetTableId","validation-hive-table");
        return new Pipeline.Node("hive","sink.hive","Hive目标","sink",1000,500,cfg);
    }
    private static Pipeline flow(List<Pipeline.Node> nodes,List<Pipeline.Edge> edges){return new Pipeline("validation-parallel","验证并行接入",null,null,null,new Pipeline.Dsl(1,nodes,edges),null,null,null,null,null,null,null);}
    private static Pipeline.Edge edge(String id,String source,String target){return new Pipeline.Edge(id,source,target,"default");}
    private Created processor(String name){return processors.stream().filter(p->p.name().equals(name)).findFirst().orElseThrow();}
    private CreatedService service(String id){return services.stream().filter(service->service.id().equals(id)).findFirst().orElseThrow();}
    private JsonNode processorSnapshot(){return JSON.valueToTree(Map.of("processGroupFlow",Map.of("flow",Map.of("processors",processors.stream()
            .map(p->Map.of("id",p.id(),"revision",Map.of("version",7),"component",Map.of("id",p.id(),"type",p.type(),"state","STOPPED",
                    "config",Map.of("comments",processorComments.getOrDefault(p.id(),""))))).toList()))));}
    private static NifiEntity entity(String id){return new NifiEntity(null,Map.of("id",id),null,null);}
}
