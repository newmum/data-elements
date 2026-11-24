package com.linewell.dataelement.integration.nifi.canvas.mapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.mapping.core.MappingEngine;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.metautil.jdbc.NifiOracleJdbcDriver;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class LegacyExactValueUpgradeServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TARGET_READER = "put-db-record-record-reader";
    private static final String DBCP = "org.apache.nifi.dbcp.DBCPConnectionPool";
    private static final String URL = "Database Connection URL";
    private static final String USER = "Database User";
    private static final String DRIVE = "Database Driver Class Name";
    private static final String ASSET = "Database Driver Locations";
    private static final String FLOW = "/flow/process-groups/group";
    private final NifiClient nifi = mock(NifiClient.class);
    private final DslCompiler compiler = mock(DslCompiler.class);
    private final RegisteredMappingSchemaService schemas = mock(RegisteredMappingSchemaService.class);
    private final UnifiedMappingService mapping = mock(UnifiedMappingService.class);
    private final MappingVersionStore versions = mock(MappingVersionStore.class);
    private final LegacyExactValueUpgradeService upgrade = new LegacyExactValueUpgradeService(nifi, compiler, schemas,
            mapping, versions, mock(PipelineRepository.class), mock(PlatformTransactionManager.class));
    private final Map<String, Object> sourceConnection = Map.of("dbType", "postgresql");
    private final Map<String, Object> targetConnection = Map.of("dbType", "oracle");
    private final ObjectNode sourcePool = controller("source-pool", DBCP,
            Map.of(URL, "jdbc:postgresql://source.invalid:5432/source", USER, "readonly"));
    private final ObjectNode targetPool = controller("target-pool", DBCP,
            Map.of(URL, "jdbc:oracle:thin:@target.invalid:1521:warehouse", USER, "target-user"));
    private Pipeline draft;

    @BeforeEach
    void fixture() {
        ObjectNode schema = JSON.createObjectNode().put("schemaVersion", "2.0");
        schema.putArray("rules").add(rule("payload", "BINARY")).add(rule("amount", "DECIMAL"));
        draft = new Pipeline("pipeline", "signed-live", "", 1L, 2L,
                new Pipeline.Dsl(1, List.of(
                        node("source", "source.postgresql", Map.of("registeredDatasourceId", "src", "sourceTableId", "st")),
                        node("governor", "transform.field-enrichment", Map.of("mappings", schema.toString())),
                        node("target", "sink.jdbc", Map.of("targetDbId", "dst", "targetTableId", "tt"))),
                        List.of(new Pipeline.Edge("read", "source", "governor", null),
                                new Pipeline.Edge("persist", "governor", "target", null))),
                "group", PipelineStatus.STOPPED, "hash", 1L, 2L,
                new NifiNodeMapping(Map.of("source", "fetch", "governor", "mapping", "target", "project"),
                        Map.of("fetch", "source", "query", "source", "mapping", "governor", "project", "target", "writer", "target"),
                        Map.of("read", new NifiNodeMapping.EdgeEndpoint("query", "mapping", "read-edge"),
                                "persist", new NifiNodeMapping.EdgeEndpoint("mapping", "project", "project-edge"))), 0L);
        var owner = JSON.createObjectNode();
        owner.putObject("component").put("comments", "data-elements:v1:tenant:pipeline:pipeline");
        when(nifi.getProcessGroup("group")).thenReturn(owner);
        var flow = JSON.createObjectNode();
        var graph = flow.putObject("processGroupFlow").putObject("flow");
        graph.putArray("processGroups");
        graph.putArray("processors")
                .add(processor("fetch", "GenerateTableFetch", true, Map.of("Table Name", "source_table", "Database Connection Pooling Service", "source-pool")))
                .add(processor("query", "ExecuteSQLRecord", false, Map.of()))
                .add(processor("mapping", "com.linewell.dataelement.nifi.HaitongTransformRecord", false,
                        Map.of("record-reader", "json", "record-writer", "json-writer")))
                .add(processor("project", "QueryRecord", false, Map.of("Record Reader", "json")))
                .add(processor("writer", "LinewellPutDatabaseRecord", false, Map.of("put-db-record-table-name", "TARGET_TABLE",
                        TARGET_READER, "json", "put-db-record-dcbp-service", "target-pool")));
        graph.putArray("connections").add(edge("read-edge", "query", "mapping")).add(edge("project-edge", "mapping", "project"));
        when(nifi.get(FLOW, JsonNode.class)).thenReturn(flow);
        var state = JSON.createObjectNode().putObject("componentState");
        state.putObject("localState");
        state.putObject("clusterState");
        when(nifi.get("/processors/fetch/state", JsonNode.class)).thenReturn(JSON.createObjectNode().set("componentState", state));
        var services = JSON.createObjectNode();
        services.putArray("controllerServices").add(sourcePool).add(targetPool)
                .add(controller("json", "org.apache.nifi.json.JsonTreeReader", Map.of("Schema Access Strategy", "infer-schema")))
                .add(controller("json-writer", "org.apache.nifi.json.JsonRecordSetWriter", Map.of("Timestamp Format", "yyyy-MM-dd HH:mm:ss")));
        when(nifi.get(FLOW + "/controller-services?includeAncestorGroups=true&includeDescendantGroups=false", JsonNode.class)).thenReturn(services);
        var source = new RegisteredMappingSchemaService.Resolved("src", "st", "public", "source_table",
                JSON.createArrayNode(), Map.of("src", sourceConnection));
        var physical = JSON.createArrayNode();
        physical.addObject().put("name", "PAYLOAD").put("physicalType", "BLOB");
        physical.addObject().put("name", "AMOUNT").put("physicalType", "NUMBER(28,8)");
        var target = new RegisteredMappingSchemaService.Resolved("dst", "tt", "TARGET", "TARGET_TABLE",
                physical, Map.of("dst", targetConnection));
        when(schemas.resolve(anyMap())).thenReturn(Map.of("source", source, "target", target));
        when(schemas.physicalBatch(Map.of("target", target))).thenReturn(Map.of("target", physical));
        when(compiler.registeredJdbcTransportPool(sourceConnection, null)).thenReturn(
                Map.of(URL, "jdbc:postgresql://source.invalid:5432/source", USER, "readonly"));
        when(compiler.registeredJdbcTransportPool(targetConnection, physical)).thenReturn(
                Map.of(URL, "jdbc:haitong:oracle:thin:@target.invalid:1521:warehouse", USER, "target-user",
                        DRIVE, NifiOracleJdbcDriver.class.getName(), ASSET, NifiOracleJdbcDriver.ASSET_NAME));
        var compiled = new UnifiedMappingService.Compiled(MappingEngine.compile(schema), Map.of());
        when(mapping.compileBatch(Map.of("governor", schema))).thenReturn(Map.of("governor", compiled));
    }

    @Test
    void plansOracleBinaryAndExactNumberReadersFromOneAuthorizedRegistrationBatch() {
        try (var ignored = TenantContext.use("tenant")) {
            var preview = upgrade.upgrade(draft, "tenant", "source", true, "");
            assertEquals(false, preview.get("started"));
            var fields = (Map<?, ?>) preview.get("targetFields");
            JsonNode target = (JsonNode) fields.get("target");
            assertEquals(1, target.path("binaryFieldCount").asInt());
            assertEquals(1, target.path("numberFieldCount").asInt());
            assertTrue(target.path("dedicatedOraclePoolRequired").asBoolean());
            verify(schemas).resolve(argThat(references -> references.keySet().equals(Set.of("source", "target"))));
            verify(schemas).physicalBatch(argThat(references -> references.keySet().equals(Set.of("target"))));
            verifyNoInteractions(versions);
        }
    }

    @Test
    void rejectsTheSameTargetTableOnAnUnregisteredConnectionBeforeAnyStaging() {
        try (var ignored = TenantContext.use("tenant")) {
            var props = (ObjectNode) targetPool.path("component").path("properties");
            props.put(URL, "jdbc:oracle:thin:@other-target.invalid:1521:warehouse");
            var failure = assertThrows(MappingException.class, () -> upgrade.upgrade(draft, "tenant", "source", false, "wrong"));
            assertEquals("RESOURCE_FORBIDDEN", failure.code());
            verifyNoInteractions(versions);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={20,100})
    void timestampPreviewUsesFixedCatalogReadsAndKeepsEverySourceWatermark(int fields) {
        timestampFixture(fields);
        var original = nifi.get(FLOW,JsonNode.class).deepCopy();
        clearInvocations(nifi);
        try (var ignored=TenantContext.use("tenant")) {
            var result=upgrade.upgradeTimestampCodecs(draft,"tenant","source",true,"");
            assertEquals(2,result.get("changedProcessors"));
            assertEquals("precise",result.get("writerId"));
            assertEquals(false,result.get("started"));
            verify(schemas).resolve(anyMap()); verify(schemas).physicalBatch(anyMap());
            verify(nifi,times(2)).get(FLOW+"/controller-services?includeAncestorGroups=true&includeDescendantGroups=false",JsonNode.class);
            verify(nifi).listControllerServices("root");
            verify(nifi,never()).put(anyString(),any(),eq(JsonNode.class));
            assertEquals(original,nifi.get(FLOW,JsonNode.class));
            verifyNoInteractions(versions);
        }
    }

    @Test void timestampUpgradeRejectsForeignTenantBeforeReadingNativeGraph() {
        try (var ignored=TenantContext.use("tenant")) {
            assertEquals("RESOURCE_FORBIDDEN",assertThrows(MappingException.class,
                    ()->upgrade.upgradeTimestampCodecs(draft,"other","source",true,"")).code());
            verifyNoInteractions(nifi,schemas,versions);
        }
    }

    @Test void timestampUpgradeRejectsStaleFingerprintWithoutChangingReferences() {
        timestampFixture(2);
        try (var ignored=TenantContext.use("tenant")) {
            assertEquals("DRAFT_VERSION_CONFLICT",assertThrows(MappingException.class,
                    ()->upgrade.upgradeTimestampCodecs(draft,"tenant","source",false,"stale")).code());
            verify(nifi,never()).put(anyString(),any(),eq(JsonNode.class));
            verifyNoInteractions(versions);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false","false,true","true,false","true,true"})
    void timestampUpgradeChangesOnlyCodecReferencesAndRollsBackIfValidationFails(boolean timeReader,boolean validationFails) {
        timestampFixture(2);
        if(timeReader)timestampTargetFixture();
        ObjectNode live=(ObjectNode)nifi.get(FLOW,JsonNode.class);
        when(nifi.get(FLOW,JsonNode.class)).thenAnswer(call->live.deepCopy());
        when(nifi.get(matches("^/processors/[^/]+$"),eq(JsonNode.class))).thenAnswer(call->{
            String id=call.<String>getArgument(0).substring("/processors/".length());
            for(JsonNode processor:live.path("processGroupFlow").path("flow").path("processors"))if(id.equals(processor.path("id").asText()))return processor.deepCopy();
            throw new AssertionError("Unknown processor");
        });
        var catalog=nifi.get(FLOW+"/controller-services?includeAncestorGroups=true&includeDescendantGroups=false",JsonNode.class);
        if(timeReader){
            when(nifi.createControllerService(eq("group"),eq("com.linewell.dataelement.nifi.HaitongJdbcRecordReader"),anyString(),anyMap())).thenAnswer(call->{
                Map<String,String> properties=call.getArgument(3);
                assertEquals("[\"at\"]",properties.get("timestamp-fields"));
                var service=controller("timestamp-reader","com.linewell.dataelement.nifi.HaitongJdbcRecordReader",properties);
                ((ObjectNode)service.path("component")).put("id","timestamp-reader").put("parentGroupId","group").put("state","DISABLED")
                        .set("bundle",JSON.createObjectNode().put("group","com.linewell.dataelement").put("artifact","haitong-mapping-nar").put("version",MappingEngine.engineVersion()));
                ((com.fasterxml.jackson.databind.node.ArrayNode)catalog.path("controllerServices")).add(service);
                return new com.linewell.dataelement.model.nifi.NifiEntity(null,Map.of("id","timestamp-reader"),null,null);
            });
            doAnswer(call->{for(JsonNode service:catalog.path("controllerServices"))if("timestamp-reader".equals(service.path("id").asText()))((ObjectNode)service.path("component")).put("state","ENABLED");return null;})
                    .when(nifi).enableControllerServices(eq("group"),anySet(),anySet(),eq(30_000L));
            doAnswer(call->{for(JsonNode service:catalog.path("controllerServices"))if("timestamp-reader".equals(service.path("id").asText()))((ObjectNode)service.path("component")).put("state","DISABLED");return null;})
                    .when(nifi).setControllerServiceRunStatus(eq("timestamp-reader"),eq("DISABLED"));
            when(nifi.waitForControllerServiceState(eq("timestamp-reader"),eq("DISABLED"),anyLong())).thenReturn(true);
            doAnswer(call->{var rows=(com.fasterxml.jackson.databind.node.ArrayNode)catalog.path("controllerServices");for(int i=0;i<rows.size();i++)if("timestamp-reader".equals(rows.get(i).path("id").asText())){rows.remove(i);break;}return null;})
                    .when(nifi).delete(startsWith("/controller-services/timestamp-reader?version="));
            when(nifi.listControllerServices("group")).thenAnswer(call->{var rows=new java.util.ArrayList<JsonNode>();catalog.path("controllerServices").forEach(rows::add);return rows;});
        }
        when(nifi.get(startsWith("/controller-services/"),eq(JsonNode.class))).thenAnswer(call->{
            String id=call.<String>getArgument(0).substring("/controller-services/".length());
            for(JsonNode service:catalog.path("controllerServices"))if(id.equals(service.path("id").asText()))return service.deepCopy();
            throw new AssertionError("Unknown service");
        });
        when(nifi.put(startsWith("/processors/"),any(),eq(JsonNode.class))).thenAnswer(call->{
            String id=call.<String>getArgument(0).substring("/processors/".length());JsonNode body=call.getArgument(1);
            for(JsonNode processor:live.path("processGroupFlow").path("flow").path("processors"))if(id.equals(processor.path("id").asText())) {
                ((ObjectNode)processor.path("component").path("config").path("properties")).setAll((ObjectNode)body.path("component").path("config").path("properties"));
                ((ObjectNode)processor.path("revision")).put("version",processor.path("revision").path("version").asLong()+1);return processor.deepCopy();
            }
            throw new AssertionError("Unknown processor");
        });
        if(validationFails)doThrow(new IllegalStateException("validation unavailable")).when(nifi).requireValidProcessors(eq("group"),anySet());
        try(var ignored=TenantContext.use("tenant")) {
            var preview=upgrade.upgradeTimestampCodecs(draft,"tenant","source",true,"");
            if(validationFails){
                assertEquals("TRANSPORT_UPGRADE_FAILED",assertThrows(MappingException.class,
                        ()->upgrade.upgradeTimestampCodecs(draft,"tenant","source",false,preview.get("fingerprint").toString())).code());
                assertEquals(false,upgrade.upgradeTimestampCodecs(draft,"tenant","source",true,"").get("alreadyApplied"));
                verify(nifi,times(timeReader?6:4)).put(startsWith("/processors/"),any(),eq(JsonNode.class));
                verifyNoInteractions(versions);return;
            }
            var applied=upgrade.upgradeTimestampCodecs(draft,"tenant","source",false,preview.get("fingerprint").toString());
            assertEquals(true,applied.get("applied"));
            assertEquals(true,upgrade.upgradeTimestampCodecs(draft,"tenant","source",true,"").get("alreadyApplied"));
            verify(nifi,times(timeReader?3:2)).put(startsWith("/processors/"),any(),eq(JsonNode.class));
            verifyNoInteractions(versions);
        }
    }

    private void timestampTargetFixture(){
        try{
            var registered=schemas.resolve(Map.of());
            ((com.fasterxml.jackson.databind.node.ArrayNode)registered.get("target").fields()).addObject().put("name","AT").put("physicalType","TIMESTAMP(9)");
            ObjectNode document=JSON.valueToTree(draft);
            ObjectNode spec=(ObjectNode)JSON.readTree(draft.dsl().nodes().get(1).config().get("mappings").toString());
            ((com.fasterxml.jackson.databind.node.ArrayNode)spec.path("rules")).add(rule("at","DATETIME"));
            ((ObjectNode)document.path("dsl").path("nodes").get(1).path("config")).put("mappings",spec.toString());
            draft=JSON.treeToValue(document,Pipeline.class);
            when(mapping.compileBatch(anyMap())).thenReturn(Map.of("governor",new UnifiedMappingService.Compiled(MappingEngine.compile(spec),Map.of())));
            var graph=nifi.get(FLOW,JsonNode.class).path("processGroupFlow").path("flow");
            for(JsonNode processor:graph.path("processors"))if("mapping".equals(processor.path("id").asText())){
                var active=MappingEngine.compile(spec).document().deepCopy();active.putObject("_deployment").put("planId","active-time-plan");
                ((ObjectNode)processor.path("component").path("config").path("properties")).put("execution-plan",active.toString());
            }
            clearInvocations(schemas,mapping);
        }catch(Exception error){throw new AssertionError(error);}
    }

    private void timestampFixture(int fields) {
        var original=schemas.resolve(Map.of());
        var sourceFields=JSON.createArrayNode();
        for(int i=0;i<fields;i++)sourceFields.addObject().put("name","time_"+i).put("physicalType","TIMESTAMP(9)").put("logicalType","DATETIME");
        var source=new RegisteredMappingSchemaService.Resolved("src","st","public","source_table",sourceFields,Map.of("src",sourceConnection));
        when(schemas.resolve(anyMap())).thenReturn(Map.of("source",source,"target",original.get("target")));
        var codec=controller("precise","org.apache.nifi.json.JsonRecordSetWriter",Map.of("Schema Access Strategy","inherit-record-schema",
                "Timestamp Format",com.linewell.dataelement.integration.nifi.canvas.compile.JdbcTimestampJsonPolicy.TIMESTAMP_FORMAT));
        ((ObjectNode)codec.path("component")).put("id","precise").put("name",com.linewell.dataelement.integration.nifi.canvas.compile.JdbcTimestampJsonPolicy.WRITER_NAME);
        when(nifi.get("/flow/process-groups/root",JsonNode.class)).thenReturn(JSON.createObjectNode().set("processGroupFlow",JSON.createObjectNode().put("id","root")));
        when(nifi.listControllerServices("root")).thenReturn(List.of(codec));
        var catalog=(ObjectNode)nifi.get(FLOW+"/controller-services?includeAncestorGroups=true&includeDescendantGroups=false",JsonNode.class);
        ((com.fasterxml.jackson.databind.node.ArrayNode)catalog.path("controllerServices")).add(codec);
        var graph=nifi.get(FLOW,JsonNode.class).path("processGroupFlow").path("flow");
        for(JsonNode processor:graph.path("processors"))if(processor.path("id").asText().equals("query"))
            ((ObjectNode)processor.path("component").path("config").path("properties")).put("Record Writer","json-writer");
        for(JsonNode processor:graph.path("processors"))if(processor.path("id").asText().equals("mapping")) {
            try {
                JsonNode raw=JSON.readTree(draft.dsl().nodes().get(1).config().get("mappings").toString());
                var active=MappingEngine.compile(raw).document().deepCopy();active.putObject("_deployment").put("planId","active-plan");
                ((ObjectNode)processor.path("component").path("config").path("properties")).put("execution-plan",active.toString())
                        .put("format-historical-timestamp-text","false");
            }catch(Exception error){throw new AssertionError(error);}
        }
        clearInvocations(schemas);
    }

    private static ObjectNode rule(String field, String type) {
        var rule = JSON.createObjectNode();
        rule.putObject("input").put("kind", "FIELD").put("name", field);
        var target = rule.putObject("target").put("name", field).put("logicalType", type);
        if ("DECIMAL".equals(type)) target.put("precision", 28).put("scale", 8);
        return rule;
    }

    private static Pipeline.Node node(String id, String type, Map<String, Object> config) {
        return new Pipeline.Node(id, type, id, "", 0, 0, config);
    }

    private static ObjectNode processor(String id, String type, boolean stateful, Map<String, String> properties) {
        var entity = JSON.createObjectNode().put("id", id);
        entity.putObject("revision").put("version", 3);
        entity.putObject("status").putObject("aggregateSnapshot").put("activeThreadCount", 0);
        entity.putObject("component").put("type", type).put("state", "STOPPED").put("persistsState", stateful)
                .putObject("config").set("properties", JSON.valueToTree(properties));
        return entity;
    }

    private static ObjectNode edge(String id, String from, String to) {
        var edge = JSON.createObjectNode().put("id", id);
        edge.putObject("revision").put("version", 1);
        edge.putObject("status").putObject("aggregateSnapshot").put("flowFilesQueued", 0).put("bytesQueued", 0);
        var component = edge.putObject("component");
        component.putObject("source").put("id", from);
        component.putObject("destination").put("id", to);
        return edge;
    }

    private static ObjectNode controller(String id, String type, Map<String, String> properties) {
        var entity = JSON.createObjectNode().put("id", id);
        entity.putObject("revision").put("version", 0);
        entity.putObject("component").put("type", type).put("state", "ENABLED").put("parentGroupId", "root")
                .set("properties", JSON.valueToTree(properties));
        return entity;
    }
}
