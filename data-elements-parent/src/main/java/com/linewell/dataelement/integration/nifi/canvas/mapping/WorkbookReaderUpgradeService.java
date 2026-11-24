package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.compile.WorkbookReaderConfiguration;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.mapping.core.MappingEngine;
import com.linewell.dataelement.mapping.core.MappingException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.linewell.dataelement.integration.nifi.canvas.mapping.LegacyNativeGraph.revision;
import static com.linewell.dataelement.integration.nifi.canvas.mapping.LegacyNativeGraph.stableComponent;

/** Rebinds the two converters of one registered file without starting the flow or consuming a retained failure. */
@Service
public class WorkbookReaderUpgradeService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CONVERTER = "org.apache.nifi.processors.standard.ConvertRecord";
    private static final String EXCEL_READER = "org.apache.nifi.excel.ExcelReader";
    private static final String NORMALIZER = "com.linewell.dataelement.nifi.HaitongNormalizeJsonRecords";
    private static final String READER = "Record Reader";
    private static final String SERVICES = "/controller-services/";

    private final NifiClient nifi;
    private final DslCompiler compiler;
    private final LegacySourceShapeUpgradeService sources;

    public WorkbookReaderUpgradeService(NifiClient nifi, DslCompiler compiler, LegacySourceShapeUpgradeService sources) {
        this.nifi = nifi;
        this.compiler = compiler;
        this.sources = sources;
    }

    public Map<String, Object> upgrade(Pipeline pipeline, String tenant, String sourceNodeId, String retainedFailure,
                                       boolean dryRun, String expectedFingerprint) {
        sources.requireStopped(pipeline, tenant);
        var source = LegacySourceShapeUpgradeService.minioSource(pipeline, sourceNodeId);
        try (var ignored = nifi.bindAssignedNode(pipeline.nodeMapping().runtimeNodeId())) {
            var graph = sources.readOwnedGraph(pipeline, tenant, source, retainedFailure);
            var shape = compiler.registeredSourceShape(pipeline, sourceNodeId);
            Map<String, String> properties = WorkbookReaderConfiguration.properties(shape);
            String group = pipeline.nifiProcessGroupId();
            String sheet = shape.path("workbook").path("sheet").asText();
            var converters = new LinkedHashMap<String, JsonNode>();
            var oldServices = new LinkedHashMap<String, JsonNode>();
            for (var item : graph.processors().entrySet()) {
                if (!sourceNodeId.equals(pipeline.nodeMapping().processorIdToCanvasNode().get(item.getKey()))
                        || !CONVERTER.equals(item.getValue().path("component").path("type").asText())) continue;
                String readerId = reader(item.getValue());
                if (readerId.isBlank()) throw changed();
                JsonNode service = oldServices.computeIfAbsent(readerId, this::service);
                if (readerMatchesScope(service, group, sheet, properties)) converters.put(item.getKey(), item.getValue());
            }
            if (converters.size() != 2) throw changed();
            requireCompatibleNormalization(pipeline, sourceNodeId, graph, shape);
            requireLocalFailure(graph, converters.keySet(), retainedFailure);
            var snapshot = JSON.createObjectNode().put("pipelineId", pipeline.id()).put("updatedAt", pipeline.updatedAt())
                    .put("source", sourceNodeId).put("retainedFailure", retainedFailure);
            snapshot.set("readerDefinition", shape.path("workbook"));
            snapshot.set("processors", JSON.valueToTree(graph.processorRevisions()));
            snapshot.set("connections", JSON.valueToTree(graph.connectionRevisions()));
            snapshot.set("states", JSON.valueToTree(graph.states()));
            snapshot.set("queues", queueMetrics(graph));
            snapshot.set("services", serviceWitness(oldServices));
            String fingerprint = MappingEngine.hash(snapshot);
            if (!dryRun && !fingerprint.equals(expectedFingerprint)) {
                throw new MappingException("DRAFT_VERSION_CONFLICT", "原读取配置或失败队列已变化，请重新预检");
            }
            String previousReader = reader(converters.values().iterator().next());
            boolean alreadyApplied = converters.values().stream().allMatch(value -> previousReader.equals(reader(value)))
                    && WorkbookReaderConfiguration.TYPE.equals(oldServices.get(previousReader).path("component").path("type").asText());
            String existing = previousReader;
            if (!dryRun && !alreadyApplied) {
                existing = replace(pipeline, tenant, source, graph, converters, oldServices, retainedFailure, properties);
            }
            return Map.of("fingerprint", fingerprint, "converterIds", converters.keySet(), "readerId", existing,
                    "retainedFailureConnectionId", retainedFailure, "alreadyApplied", alreadyApplied, "dryRun", dryRun,
                    "applied", !dryRun && !alreadyApplied, "started", false);
        }
    }

    private static void requireCompatibleNormalization(Pipeline pipeline, String source, LegacyNativeGraph.Graph graph,
                                                       JsonNode shape) {
        boolean present = false;
        for (var edge : pipeline.dsl().edges()) {
            if (!source.equals(edge.source())) continue;
            var link = pipeline.nodeMapping().edgeProcessorEndpoints().get(edge.id());
            if (link == null) throw changed();
            JsonNode node = graph.processors().get(link.sourceProcessorId());
            if (node == null || !NORMALIZER.equals(node.path("component").path("type").asText())) throw changed();
            JsonNode previous = parse(node.path("component").path("config").path("properties").path("source-record-shape").asText());
            if (!shape.path("fieldPaths").equals(previous.path("fieldPaths"))) throw changed();
            present = true;
        }
        if (!present) throw changed();
    }

    static void requireLocalFailure(LegacyNativeGraph.Graph graph, Set<String> converters, String retainedFailure) {
        if (retainedFailure.isEmpty()) return;
        JsonNode connection = graph.connections().get(retainedFailure).path("component");
        JsonNode relationships = connection.path("selectedRelationships");
        String converter = connection.path("source").path("id").asText();
        JsonNode destination = connection.path("destination");
        boolean heldAtConverter = "PROCESSOR".equals(destination.path("type").asText())
                && converter.equals(destination.path("id").asText());
        if (relationships.size() != 1 || !"failure".equals(relationships.get(0).asText())
                || !converters.contains(converter) || !(heldAtConverter || "FUNNEL".equals(destination.path("type").asText()))) throw changed();
    }

    private String replace(Pipeline pipeline, String tenant, Pipeline.Node source, LegacyNativeGraph.Graph graph,
                           Map<String, JsonNode> converters, Map<String, JsonNode> oldServices, String retainedFailure,
                           Map<String, String> properties) {
        String group = pipeline.nifiProcessGroupId();
        List<String> attempted = new ArrayList<>();
        String created = "";
        try {
            created = nifi.createControllerService(group, WorkbookReaderConfiguration.TYPE,
                    source.label() + "/登记工作簿读取器", properties).id();
            if (created == null || created.isBlank() || oldServices.containsKey(created)) throw changed();
            nifi.enableControllerServices(group, Set.of(created), Set.of(), 30_000);
            if (!readerMatchesScope(service(created), group, "", properties)) throw changed();
            for (var entry : converters.entrySet()) {
                attempted.add(entry.getKey());
                writeReader(entry.getValue(), created);
            }
            nifi.requireValidProcessors(group, converters.keySet());
            verify(pipeline, tenant, source, graph, oldServices, retainedFailure, converters.keySet(), created);
            return created;
        } catch (RuntimeException failure) {
            boolean restored = false;
            try {
                for (String id : attempted.reversed()) {
                    JsonNode original = converters.get(id);
                    JsonNode actual = nifi.get("/processors/" + id, JsonNode.class);
                    if (stableComponent(original).equals(stableComponent(actual))) continue;
                    compareProcessor(original, actual, created);
                    writeReader(actual, reader(original));
                }
                verify(pipeline, tenant, source, graph, oldServices, retainedFailure, Set.of(), "");
                if (created != null && !created.isBlank() && !oldServices.containsKey(created)) {
                    nifi.setControllerServiceRunStatus(created, "DISABLED");
                    if (!nifi.waitForControllerServiceState(created, "DISABLED", 10_000)) throw changed();
                    nifi.delete(SERVICES + created + "?version=" + revision(service(created)));
                }
                restored = true;
            } catch (RuntimeException recovery) {
                failure.addSuppressed(recovery);
            }
            throw new MappingException(restored ? "TRANSPORT_UPGRADE_FAILED" : "TRANSPORT_UPGRADE_RECOVERY_REQUIRED",
                    restored ? "工作簿读取升级失败，原引用已恢复" : "须核对工作簿读取服务，流程保持停止", failure);
        }
    }

    private void verify(Pipeline pipeline, String tenant, Pipeline.Node source, LegacyNativeGraph.Graph before,
                        Map<String, JsonNode> services, String retainedFailure, Set<String> converters, String reader) {
        var after = sources.readOwnedGraph(pipeline, tenant, source, retainedFailure);
        if (!before.states().equals(after.states()) || !queueMetrics(before).equals(queueMetrics(after))
                || !before.connectionRevisions().equals(after.connectionRevisions())
                || !sameComponents(before.connections(), after.connections()) || !sameComponents(before.funnels(), after.funnels())
                || !before.processors().keySet().equals(after.processors().keySet())) throw changed();
        before.processors().forEach((id, original) -> compareProcessor(original, after.processors().get(id),
                converters.contains(id) ? reader : ""));
        services.forEach((id, original) -> {
            JsonNode observed = service(id);
            if (revision(original) != revision(observed) || !stableComponent(original).equals(stableComponent(observed))) throw changed();
        });
    }

    private static boolean sameComponents(Map<String, JsonNode> before, Map<String, JsonNode> after) {
        return before.keySet().equals(after.keySet()) && before.entrySet().stream().allMatch(item ->
                stableComponent(item.getValue()).equals(stableComponent(after.get(item.getKey()))));
    }

    private static ObjectNode queueMetrics(LegacyNativeGraph.Graph graph) {
        var queues = JSON.createObjectNode();
        graph.connections().forEach((id, edge) -> {
            var status = edge.path("status").path("aggregateSnapshot");
            queues.putObject(id).put("items", status.path("flowFilesQueued").asLong()).put("bytes", status.path("bytesQueued").asLong());
        });
        return queues;
    }

    private static ObjectNode serviceWitness(Map<String, JsonNode> services) {
        var settings = JSON.createObjectNode();
        services.forEach((id, service) -> settings.putObject(id).put("revision", revision(service)).set("component", stableComponent(service)));
        return settings;
    }

    private static boolean readerMatchesScope(JsonNode service, String group, String sheet, Map<String, String> settings) {
        JsonNode component = service.path("component");
        JsonNode properties = component.path("properties");
        if (!group.equals(component.path("parentGroupId").asText()) || !"ENABLED".equals(component.path("state").asText())) return false;
        String type = component.path("type").asText();
        if (WorkbookReaderConfiguration.TYPE.equals(type)) {
            return parse(settings.get(WorkbookReaderConfiguration.LAYOUT)).equals(parse(properties.path(WorkbookReaderConfiguration.LAYOUT).asText()));
        }
        return EXCEL_READER.equals(type) && "Use Starting Row".equals(properties.path("Schema Access Strategy").asText())
                && Set.of("XLS", "XLSX").contains(properties.path("Input File Type").asText())
                && (sheet.equals(properties.path("Required Sheets").asText()) || sheet.replace("$", "$$").equals(properties.path("Required Sheets").asText()));
    }

    private void writeReader(JsonNode processor, String replacement) {
        String id = processor.path("id").asText();
        var body = JSON.createObjectNode().put("disconnectedNodeAcknowledged", false);
        body.putObject("revision").put("version", revision(processor)).put("clientId", nifi.clientId());
        body.putObject("component").put("id", id).putObject("config").putObject("properties").put(READER, replacement);
        nifi.put("/processors/" + id, body, JsonNode.class);
    }

    private static void compareProcessor(JsonNode original, JsonNode actual, String replacement) {
        var expected = stableComponent(original);
        if (!replacement.isEmpty()) ((ObjectNode) expected.path("config").path("properties")).put(READER, replacement);
        if (!expected.equals(stableComponent(actual))) throw changed();
    }

    private JsonNode service(String id) {
        return nifi.get(SERVICES + id, JsonNode.class);
    }

    private static String reader(JsonNode processor) {
        return processor.path("component").path("config").path("properties").path(READER).asText("");
    }

    private static JsonNode parse(String definition) {
        try {
            return JSON.readTree(definition);
        } catch (java.io.IOException error) {
            throw changed();
        }
    }

    private static MappingException changed() {
        return new MappingException("MIGRATION_VALIDATION_FAILED", "原工作簿转换、登记范围或失败队列无法安全核验");
    }
}
