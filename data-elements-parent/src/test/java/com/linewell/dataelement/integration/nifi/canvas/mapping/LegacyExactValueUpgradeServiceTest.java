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
