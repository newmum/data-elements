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
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class LegacySourceShapeUpgradeServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FLOW = "/flow/process-groups/group";
    private static final String UPGRADE_TENANT = "tenant";
    private static final Set<String> BRANCHES = Set.of("oracle", "hive");
    private final NifiClient nifi = mock(NifiClient.class);
    private final DslCompiler compiler = mock(DslCompiler.class);
    private final UnifiedMappingService mapping = mock(UnifiedMappingService.class);
    private final MappingVersionStore versions = mock(MappingVersionStore.class);
    private final LegacySourceShapeUpgradeService upgrade = new LegacySourceShapeUpgradeService(nifi, compiler, mapping,
            versions, mock(PipelineRepository.class), mock(PlatformTransactionManager.class));
    private final ObjectNode watermark = JSON.createObjectNode().put("key", "last-listed").put("value", "5");
    private final ObjectNode response = JSON.createObjectNode();
    private final ObjectNode sourceEdge = edge("first", "outlet", "governor");
    private final ObjectNode secondEdge = edge("second", "outlet", "governor-hive");
    private final JsonNode shape = JSON.createObjectNode().put("mode", "NESTED_JSON");
    private final JsonNode planSpec = JSON.createObjectNode().put("schemaVersion", "2.0").set("rules", JSON.createArrayNode());
    private final Pipeline pipeline = new Pipeline("pipeline", "existing", "", 1L, 2L,
            new Pipeline.Dsl(1, List.of(
                    node("source", "source.minio", Map.of("minioBucket", "source-bucket",
                            "minioEndpoint", "https://source.invalid:9000", "minioObjectKey", "registered.xlsx")),
                    node("oracle", "transform.field-enrichment", Map.of("mappings", planSpec.toString())),
                    node("hive", "transform.field-enrichment", Map.of("mappings", planSpec.toString()))),
                    List.of(new Pipeline.Edge("to-oracle", "source", "oracle", null),
                            new Pipeline.Edge("to-hive", "source", "hive", null))),
            "group", PipelineStatus.STOPPED, "deployed", 1L, 2L,
            new NifiNodeMapping(Map.of("source", "list", "oracle", "governor", "hive", "governor-hive"),
                    Map.of("list", "source", "outlet", "source", "governor", "oracle", "governor-hive", "hive"),
                    Map.of("to-oracle", new NifiNodeMapping.EdgeEndpoint("outlet", "governor", "first"),
                            "to-hive", new NifiNodeMapping.EdgeEndpoint("outlet", "governor-hive", "second"))), 0L);

    @BeforeEach
    void graph() {
        var owner = JSON.createObjectNode();
        owner.putObject("component").put("comments", "data-elements:v1:tenant:pipeline:pipeline");
        when(nifi.getProcessGroup("group")).thenReturn(owner);
        var flow = response.putObject("processGroupFlow").putObject("flow");
        flow.putArray("processGroups");
        flow.putArray("connections").add(sourceEdge).add(secondEdge);
        var list = processor("list", "org.apache.nifi.processors.aws.s3.ListS3");
        ((ObjectNode) list.path("component")).put("persistsState", true).putObject("config").putObject("properties")
                .put("Bucket", "source-bucket").put("Endpoint Override URL", "https://source.invalid:9000").put("Prefix", "registered.xlsx");
        flow.putArray("processors").add(list).add(processor("outlet", "UpdateAttribute"))
                .add(processor("governor", "com.linewell.dataelement.nifi.HaitongTransformRecord"))
                .add(processor("governor-hive", "com.linewell.dataelement.nifi.HaitongTransformRecord"));
        when(nifi.get(FLOW, JsonNode.class)).thenAnswer(call -> response.deepCopy());
        var sourceState = JSON.createObjectNode();
        var state = sourceState.putObject("componentState");
        state.putObject("localState").putArray("state");
        state.putObject("clusterState").putArray("state").add(watermark);
        when(nifi.get("/processors/list/state", JsonNode.class)).thenAnswer(call -> sourceState.deepCopy());
        when(compiler.registeredSourceShape(pipeline, "source")).thenReturn(shape);
        var compiled = new UnifiedMappingService.Compiled(MappingEngine.compile(planSpec), Map.of());
        when(mapping.compileBatch(anyMap())).thenReturn(Map.of("oracle", compiled, "hive", compiled));
    }

    @Test
    void previewResolvesBothSelectedPlansInOneBatchAndRejectsAnyChangedWatermark() {
        try (var ignored = TenantContext.use(UPGRADE_TENANT)) {
            var preview = upgrade.upgrade(pipeline, UPGRADE_TENANT, "source", true, "");
            assertEquals(BRANCHES, ((Map<?, ?>) preview.get("planHashes")).keySet());
            verify(mapping).compileBatch(argThat(plans -> plans.keySet().equals(BRANCHES)));
            String version = (String) preview.get("fingerprint");
            watermark.put("value", "6");
            var refused = assertThrows(MappingException.class, () -> upgrade.upgrade(pipeline, UPGRADE_TENANT, "source", false, version));
            assertEquals("DRAFT_VERSION_CONFLICT", refused.code());
            verifyNoInteractions(versions);
        }
    }

    @Test
    void nonemptyQueueBytesAndForeignTenantStopBeforeRegistrationOrMutation() {
        try (var ignored = TenantContext.use(UPGRADE_TENANT)) {
            ((ObjectNode) secondEdge.path("status").path("aggregateSnapshot")).put("bytesQueued", 1);
            var refused = assertThrows(MappingException.class, () -> upgrade.upgrade(pipeline, UPGRADE_TENANT, "source", true, ""));
            assertEquals("MIGRATION_QUEUE_NOT_EMPTY", refused.code());
            var foreign = assertThrows(MappingException.class, () -> upgrade.upgrade(pipeline, "elsewhere", "source", true, ""));
            assertEquals("RESOURCE_FORBIDDEN", foreign.code());
            verifyNoInteractions(compiler, mapping, versions);
        }
    }

    @Test
    void bundleUpgradeAcceptsOnlyNewImplicitDefaultsAndPreservesOtherConfiguration() {
        var original = processor("governor", "com.linewell.dataelement.nifi.HaitongTransformRecord");
        var component = (ObjectNode) original.path("component");
        component.putObject("bundle").put("group", "com.linewell.dataelement").put("artifact", "haitong-mapping-nar").put("version", "1.1.30");
        var originalConfig = component.putObject("config");
        originalConfig.putObject("properties").put("execution-plan", "old").put("record-reader", "registered-reader");
        originalConfig.putObject("descriptors").putObject("record-reader");
        var revised = original.deepCopy();
        ((ObjectNode) revised.path("component").path("bundle")).put("version", MappingEngine.engineVersion());
        var config = (ObjectNode) revised.path("component").path("config");
        var props = (ObjectNode) config.path("properties");
        props.put("execution-plan", "new").put("timestamp-input-contract", "AUTO");
        ((ObjectNode) config.path("descriptors")).putObject("timestamp-input-contract").put("defaultValue", "AUTO");
        ObjectNode replacement = JSON.createObjectNode().put("execution-plan", "new");
        assertDoesNotThrow(() -> LegacySourceShapeUpgradeService.requireChangedGovernor(original, revised, replacement));
        props.put("timestamp-input-contract", "ABSOLUTE_INSTANT");
        assertThrows(MappingException.class, () -> LegacySourceShapeUpgradeService.requireChangedGovernor(original, revised, replacement));
        props.put("timestamp-input-contract", "AUTO").put("record-reader", "unexpected-reader");
        assertThrows(MappingException.class, () -> LegacySourceShapeUpgradeService.requireChangedGovernor(original, revised, replacement));
    }

    @Test
    void workbookPolicyPreviewRequiresTheSameRegisteredPathsAndSelectsTheSharedNormalizerOnce() {
        ObjectNode worksheet = JSON.createObjectNode().put("mode", "RECORD");
        worksheet.putObject("fieldPaths").putArray("amount").add("column_1");
        ObjectNode newPolicy = worksheet.deepCopy().put("skipBlankRecords", true);
        var flow = response.path("processGroupFlow").path("flow");
        var outlet = (ObjectNode) flow.path("processors").get(1).path("component");
        outlet.put("type", "com.linewell.dataelement.nifi.HaitongNormalizeJsonRecords");
        outlet.putObject("bundle").put("group", "com.linewell.dataelement").put("artifact", "haitong-mapping-nar").put("version", "1.1.41");
        outlet.putObject("config").putObject("properties").put("source-record-shape", worksheet.toString());
        when(compiler.registeredSourceShape(pipeline, "source")).thenReturn(newPolicy);
        try (var ignored = TenantContext.use(UPGRADE_TENANT)) {
            var preview = upgrade.upgradeWorkbookRows(pipeline, UPGRADE_TENANT, "source", true, "");
            assertEquals(Set.of("outlet"), preview.get("normalizerIds"));
            assertEquals(false, preview.get("applied"));
            newPolicy.putObject("fieldPaths").putArray("amount").add("anotherColumn");
            var error = assertThrows(MappingException.class, () -> upgrade.upgradeWorkbookRows(pipeline, UPGRADE_TENANT, "source", true, ""));
            assertEquals("MIGRATION_VALIDATION_FAILED", error.code());
            verifyNoInteractions(mapping, versions);
        }
    }

    private static Pipeline.Node node(String id, String type, Map<String, Object> config) {
        return new Pipeline.Node(id, type, id, "", 0, 0, config);
    }

    private static ObjectNode processor(String id, String type) {
        var entity = JSON.createObjectNode().put("id", id);
        entity.putObject("revision").put("version", 2);
        entity.putObject("status").putObject("aggregateSnapshot").put("activeThreadCount", 0);
        entity.putObject("component").put("type", type).put("state", "STOPPED");
        return entity;
    }

    private static ObjectNode edge(String id, String from, String to) {
        var entity = JSON.createObjectNode().put("id", id);
        entity.putObject("revision").put("version", 3);
        entity.putObject("status").putObject("aggregateSnapshot").put("flowFilesQueued", 0).put("bytesQueued", 0);
        var component = entity.putObject("component").put("parentGroupId", "group");
        component.putObject("source").put("id", from).put("groupId", "group").put("type", "PROCESSOR");
        component.putObject("destination").put("id", to).put("groupId", "group").put("type", "PROCESSOR");
        return entity;
    }

}
