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
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LegacyTransportUpgradeServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String POOL = "Database Connection Pooling Service";
    private static final String AUTHORIZED_URL = "jdbc:mysql://validation.invalid:3306/source";
    private static final String NEW_URL = AUTHORIZED_URL + "?tinyInt1isBit=false";
    private final NifiClient nifi = mock(NifiClient.class);
    private final RegisteredMappingSchemaService schemas = mock(RegisteredMappingSchemaService.class);
    private final DslCompiler compiler = mock(DslCompiler.class);
    private final LegacyTransportUpgradeService upgrade = new LegacyTransportUpgradeService(nifi, schemas, compiler);
    private final ObjectNode flow = JSON.createObjectNode();
    private final ObjectNode fetch = processor("fetch", "GenerateTableFetch", true);
    private final ObjectNode execute = processor("execute", "ExecuteSQLRecord", true);
    private final ObjectNode unaffected = processor("unaffected", "ExecuteSQLRecord", false);
    private final ObjectNode connection = JSON.createObjectNode().put("id", "original-connection");
    private String failProcessor;
    private final Pipeline pipeline = new Pipeline("pipeline", "validation", "", 1L, 2L,
            new Pipeline.Dsl(1, List.of(new Pipeline.Node("source-node", "source.mysql", "source", "source", 0, 0,
                    Map.of("sourceTableId", "table", "registeredDatasourceId", "datasource"))), List.of()),
            "group", PipelineStatus.STOPPED, "original-hash", 1L, 2L,
            new NifiNodeMapping(Map.of("source-node", "fetch"), Map.of("fetch", "source-node", "execute", "source-node",
                    "unaffected", "other-node"), Map.of()), 0L);

    @BeforeEach
    void fixture() {
        var registeredConnection = Map.<String,Object>of("dbType", "mysql");
        var registered = new RegisteredMappingSchemaService.Resolved("datasource", "table", "source", "owned_table",
                JSON.createArrayNode().add(JSON.createObjectNode().put("name", "flag")),
                Map.of("datasource", registeredConnection));
        when(schemas.resolve(anyMap())).thenReturn(Map.of("source-node", registered));
        when(compiler.registeredMySqlReadPool(registeredConnection)).thenReturn(Map.of(
                "Database Connection URL", NEW_URL, "Database User", "validation",
                "Database Driver Class Name", "com.mysql.cj.jdbc.Driver"));
        ObjectNode owner = JSON.createObjectNode();
        owner.putObject("component").put("comments", "data-elements:v1:tenant:pipeline:pipeline");
        when(nifi.getProcessGroup("group")).thenReturn(owner);
        ObjectNode scope = flow.putObject("processGroupFlow").putObject("flow");
        scope.putArray("processGroups");
        scope.putArray("processors").add(fetch).add(execute).add(unaffected);
        scope.putArray("connections").add(connection);
        connection.putObject("revision").put("version", 5);
        connection.putObject("component").put("source", "fetch").put("destination", "execute");
        connection.putObject("status").putObject("aggregateSnapshot").put("flowFilesQueued", 0).put("bytesQueued", 0);
        when(nifi.get("/flow/process-groups/group", JsonNode.class)).thenAnswer(call -> flow.deepCopy());
        var state = JSON.createObjectNode();
        var componentState = state.putObject("componentState");
        componentState.putObject("localState").putArray("state");
        componentState.putObject("clusterState").putArray("state").addObject().put("key", "max.id").put("value", "25");
        for (var processor : List.of(fetch, execute)) {
            String id = processor.path("id").asText();
            when(nifi.get("/processors/" + id, JsonNode.class)).thenAnswer(call -> processor.deepCopy());
            when(nifi.get("/processors/" + id + "/state", JsonNode.class)).thenAnswer(call -> state.deepCopy());
        }
        when(nifi.get("/controller-services/shared", JsonNode.class)).thenReturn(pool("shared", "root", AUTHORIZED_URL));
        when(nifi.get("/controller-services/dedicated", JsonNode.class)).thenReturn(pool("dedicated", "group", NEW_URL));
        when(nifi.clientId()).thenReturn("validation");
        when(nifi.createControllerService(eq("group"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"), anyString(),
                argThat(values -> NEW_URL.equals(values.get("Database Connection URL")))))
                .thenReturn(new NifiEntity(null, Map.of("id", "dedicated"), null, false));
        when(nifi.waitForControllerServiceState("dedicated", "DISABLED", 10_000)).thenReturn(true);
        when(nifi.put(startsWith("/processors/"), any(), eq(JsonNode.class))).thenAnswer(call -> {
            JsonNode request = call.getArgument(1);
            var processor = request.path("component").path("id").asText().equals("fetch") ? fetch : execute;
            String id = processor.path("id").asText();
            var properties = (ObjectNode) processor.path("component").path("config").path("properties");
            String value = request.path("component").path("config").path("properties").path(POOL).asText();
            assertEquals(processor.path("revision").path("version").asLong(), request.path("revision").path("version").asLong());
            properties.put(POOL, value);
            ((ObjectNode) processor.path("revision")).put("version", processor.path("revision").path("version").asInt() + 1);
            if (id.equals(failProcessor) && value.equals("dedicated")) throw new IllegalStateException("write response lost");
            return processor.deepCopy();
        });
    }

    @Test
    void dryRunOnlyReadsAndCommitSwitchesJustTheTwoAuthorizedReaders() {
        try (var tenant = TenantContext.use("tenant")) {
            var plan = run(true, "");
            assertEquals(true, plan.get("dryRun"));
            assertEquals(Set.of("fetch", "execute"), plan.get("sourceProcessorIds"));
            verify(nifi, never()).put(anyString(), any(), any());
            verify(nifi, never()).createControllerService(anyString(), anyString(), anyString(), anyMap());
            var result = run(false, plan.get("fingerprint").toString());
            assertEquals(true, result.get("applied"));
            assertEquals("dedicated", reference(fetch));
            assertEquals("dedicated", reference(execute));
            assertEquals("shared", reference(unaffected));
            assertEquals(false, run(false, run(true, "").get("fingerprint").toString()).get("applied"));
        }
        verify(nifi, times(2)).put(startsWith("/processors/"), any(), eq(JsonNode.class));
        verify(nifi, never()).setProcessorRunStatus(anyString(), anyString());
        verify(nifi, never()).delete(anyString());
    }

    @Test
    void queueBytesAndStaleFingerprintBlockMutations() {
        try (var tenant = TenantContext.use("tenant")) {
            String fingerprint = run(true, "").get("fingerprint").toString();
            ((ObjectNode) connection.path("status").path("aggregateSnapshot")).put("bytesQueued", 1);
            assertEquals("MIGRATION_QUEUE_NOT_EMPTY", assertThrows(MappingException.class, () -> run(false, fingerprint)).code());
            ((ObjectNode) connection.path("status").path("aggregateSnapshot")).put("bytesQueued", 0);
            ((ObjectNode) connection.path("revision")).put("version", 6);
            assertEquals("DRAFT_VERSION_CONFLICT", assertThrows(MappingException.class, () -> run(false, fingerprint)).code());
        }
        verify(nifi, never()).createControllerService(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void secondWriteWithLostResponseRestoresBothReadersInReverseOrder() {
        failProcessor = "execute";
        try (var tenant = TenantContext.use("tenant")) {
            String fingerprint = run(true, "").get("fingerprint").toString();
            assertEquals("TRANSPORT_UPGRADE_FAILED", assertThrows(MappingException.class, () -> run(false, fingerprint)).code());
            assertEquals("shared", reference(fetch));
            assertEquals("shared", reference(execute));
        }
        verify(nifi).delete("/controller-services/dedicated?version=3");
        verify(nifi, never()).delete(contains("shared"));
        verify(nifi, never()).setProcessorRunStatus(anyString(), anyString());
    }

    @Test
    void foreignContextAndSpoofedNativeTableAreRejectedBeforeNewPoolCreation() {
        try (var tenant = TenantContext.use("another-tenant")) {
            assertEquals("RESOURCE_FORBIDDEN", assertThrows(MappingException.class, () -> run(true, "")).code());
        }
        try (var tenant = TenantContext.use("tenant")) {
            ((ObjectNode) fetch.path("component").path("config").path("properties")).put("Table Name", "foreign");
            assertEquals("RESOURCE_FORBIDDEN", assertThrows(MappingException.class, () -> run(true, "")).code());
        }
        verify(nifi, never()).createControllerService(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void anUnconfirmedCreateDoesNotLeadToAnotherPool() {
        var priorAttempt = pool("existing-candidate", "group", NEW_URL);
        ((ObjectNode) priorAttempt.path("component")).put("name", "MySQL来源整数读取连接池/source-node");
        when(nifi.listControllerServices("group")).thenReturn(List.of(priorAttempt));
        try (var tenant = TenantContext.use("tenant")) {
            String fingerprint = run(true, "").get("fingerprint").toString();
            assertEquals("TRANSPORT_UPGRADE_RECOVERY_REQUIRED",
                    assertThrows(MappingException.class, () -> run(false, fingerprint)).code());
        }
        verify(nifi, never()).createControllerService(anyString(), anyString(), anyString(), anyMap());
    }

    private Map<String,Object> run(boolean preview, String fingerprint) {
        return upgrade.upgradeMySqlReadPool(pipeline, "tenant", "source-node", preview, fingerprint);
    }

    private static ObjectNode processor(String id, String type, boolean stateful) {
        var node = JSON.createObjectNode().put("id", id);
        node.putObject("revision").put("version", 2);
        var component = node.putObject("component").put("id", id).put("state", "STOPPED")
                .put("type", "org.apache.nifi.processors.standard." + type).put("persistsState", stateful);
        component.putObject("config").putObject("properties").put(POOL, "shared").put("Table Name", "owned_table");
        node.putObject("status").putObject("aggregateSnapshot").put("activeThreadCount", 0);
        return node;
    }

    private static ObjectNode pool(String id, String group, String url) {
        var service = JSON.createObjectNode().put("id", id);
        service.putObject("revision").put("version", 3);
        service.putObject("component").put("type", "org.apache.nifi.dbcp.DBCPConnectionPool")
                .put("parentGroupId", group).put("state", "ENABLED").putObject("properties")
                .put("Database Connection URL", url).put("Database User", "validation")
                .put("Database Driver Class Name", "com.mysql.cj.jdbc.Driver");
        return service;
    }

    private static String reference(JsonNode node) {
        return node.path("component").path("config").path("properties").path(POOL).asText();
    }
}
