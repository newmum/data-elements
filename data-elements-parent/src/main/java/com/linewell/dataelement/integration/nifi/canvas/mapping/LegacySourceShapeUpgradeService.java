package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.mapping.LegacyNativeGraph.Graph;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.mapping.core.MappingEngine;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static com.linewell.dataelement.integration.nifi.canvas.mapping.LegacyNativeGraph.revision;
import static com.linewell.dataelement.integration.nifi.canvas.mapping.LegacyNativeGraph.stableComponent;

/** Inserts registered source normalization while leaving the ListS3 identity and persisted watermarks in place. */
@Service
public class LegacySourceShapeUpgradeService {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final String NORMALIZER = "com.linewell.dataelement.nifi.HaitongNormalizeJsonRecords";
    private static final String GOVERNANCE = "com.linewell.dataelement.nifi.HaitongTransformRecord";
    private static final String LIST_S3 = "org.apache.nifi.processors.aws.s3.ListS3";
    private static final String SHAPE = "source-record-shape";
    private static final String EXECUTION_PLAN = "execution-plan";

    private final NifiClient nifi;
    private final LegacyNativeGraph nativeGraph;
    private final DslCompiler compiler;
    private final UnifiedMappingService mapping;
    private final MappingVersionStore versions;
    private final PipelineRepository pipelines;
    private final TransactionTemplate transaction;

    public LegacySourceShapeUpgradeService(NifiClient nifi, DslCompiler compiler, UnifiedMappingService mapping,
                                          MappingVersionStore versions, PipelineRepository pipelines,
                                          PlatformTransactionManager manager) {
        this.nifi = nifi;
        this.nativeGraph = new LegacyNativeGraph(nifi);
        this.compiler = compiler;
        this.mapping = mapping;
        this.versions = versions;
        this.pipelines = pipelines;
        this.transaction = new TransactionTemplate(manager);
    }

    public Map<String, Object> upgrade(Pipeline pipeline, String tenant, String sourceNodeId,
                                       boolean dryRun, String expectedFingerprint) {
        requireStopped(pipeline, tenant);
        NifiNodeMapping indexes = pipeline.nodeMapping();
        try (var ignored = nifi.bindAssignedNode(indexes.runtimeNodeId())) {
            Plan plan = prepare(pipeline, tenant, sourceNodeId);
            if (!dryRun && !plan.fingerprint().equals(expectedFingerprint)) {
                throw failure("DRAFT_VERSION_CONFLICT", "来源水位、原生连接或已保存治理计划发生变化，请重新预检");
            }
            if (dryRun || plan.alreadyApplied()) return result(plan, dryRun, Map.of(), Map.of());
            return apply(plan);
        }
    }

    private void requireStopped(Pipeline pipeline, String tenant) {
        NifiNodeMapping indexes = pipeline.nodeMapping();
        if (!Objects.equals(tenant, TenantContext.requireTenantId())) {
            throw failure("RESOURCE_FORBIDDEN", "流程必须属于当前租户");
        }
        if (pipeline.status() != PipelineStatus.STOPPED || pipeline.nifiProcessGroupId() == null
                || indexes == null || indexes.candidate() != null || indexes.primaryProcessorIds() == null
                || indexes.processorIdToCanvasNode() == null || indexes.edgeProcessorEndpoints() == null
                || pipeline.dsl() == null || pipeline.dsl().nodes() == null || pipeline.dsl().edges() == null) {
            throw failure("MIGRATION_UNAVAILABLE", "流程必须停止、具有完整原生映射且无候选部署");
        }
    }

    private Plan prepare(Pipeline pipeline, String tenant, String sourceNodeId) {
        Pipeline.Node source = minioSource(pipeline, sourceNodeId);
        Graph graph = readOwnedGraph(pipeline, tenant, source);
        List<Pipeline.Edge> edges = pipeline.dsl().edges().stream().filter(edge -> sourceNodeId.equals(edge.source())).toList();
        if (edges.isEmpty() || edges.size() > 32) {
            throw failure("MIGRATION_SCOPE_UNSUPPORTED", "来源的正式治理分支数须在一至三十二之间");
        }
        JsonNode shape = compiler.registeredSourceShape(pipeline, sourceNodeId);
        return planBranches(pipeline, source, graph, edges, shape);
    }

    private static Pipeline.Node minioSource(Pipeline pipeline, String sourceNodeId) {
        return pipeline.dsl().nodes().stream().filter(node -> sourceNodeId.equals(node.id()) && "source.minio".equals(node.manifestKey()))
                .findFirst().orElseThrow(() -> failure("RESOURCE_FORBIDDEN", "所选节点不是当前流程的已登记 MinIO 来源"));
    }

    private Graph readOwnedGraph(Pipeline pipeline, String tenant, Pipeline.Node source) {
        String group = pipeline.nifiProcessGroupId();
        String marker = "data-elements:v1:" + tenant + ":pipeline:" + pipeline.id();
        if (!marker.equals(nifi.getProcessGroup(group).path("component").path("comments").asText())) {
            throw failure("RESOURCE_FORBIDDEN", "原生流程组缺少当前租户和流程归属标记");
        }
        Graph graph = nativeGraph.read(group);
        String primary = pipeline.nodeMapping().primaryProcessorIds().get(source.id());
        JsonNode origin = graph.processors().get(primary);
        if (origin == null || !LIST_S3.equals(origin.path("component").path("type").asText())
                || !source.id().equals(pipeline.nodeMapping().processorIdToCanvasNode().get(primary))
                || !graph.states().containsKey(primary)
                || !sameSourceProperty(source, origin, "minioBucket", "Bucket")
                || !sameSourceProperty(source, origin, "minioEndpoint", "Endpoint Override URL")
                || !sameSourceProperty(source, origin, "minioObjectKey", "Prefix")) {
            throw failure("MIGRATION_UNAVAILABLE", "无法核验原生 ListS3、登记对象与完整水位");
        }
        return graph;
    }

    private Plan planBranches(Pipeline pipeline, Pipeline.Node source, Graph graph, List<Pipeline.Edge> edges, JsonNode shape) {
        String group = pipeline.nifiProcessGroupId();
        Map<String, Pipeline.Node> nodes = new LinkedHashMap<>();
        pipeline.dsl().nodes().forEach(node -> nodes.put(node.id(), node));
        Map<String, JsonNode> specs = new LinkedHashMap<>();
        List<Branch> branches = new ArrayList<>();
        for (Pipeline.Edge edge : edges) {
            Branch branch = branch(pipeline, graph, edge, nodes.get(edge.target()), shape);
            if (specs.putIfAbsent(edge.target(), savedSpec(nodes.get(edge.target()))) != null) {
                throw failure("MIGRATION_SCOPE_UNSUPPORTED", "多个来源边界指向同一治理节点，无法独立核验");
            }
            branches.add(branch);
        }
        boolean alreadyApplied = branches.stream().allMatch(Branch::normalized);
        if (!alreadyApplied && (branches.stream().anyMatch(Branch::normalized) || graph.processors().values().stream().anyMatch(
                processor -> processor.path("component").path("name").asText().startsWith(normalizerPrefix(source.id()))))) {
            throw failure("TRANSPORT_UPGRADE_RECOVERY_REQUIRED", "来源已存在部分规范化候选，请先核对原生边界");
        }
        if (!alreadyApplied) {
            var boundary = branches.stream().map(branch -> branch.connection().path("id").asText()).collect(java.util.stream.Collectors.toSet());
            Set<String> outlets = new LinkedHashSet<>();
            branches.forEach(branch -> outlets.add(branch.connection().path("component").path("source").path("id").asText()));
            for (var connection : graph.connections().entrySet()) {
                JsonNode component = connection.getValue().path("component");
                if (!boundary.contains(connection.getKey()) && outlets.contains(component.path("source").path("id").asText())) {
                    throw failure("MIGRATION_SCOPE_UNSUPPORTED", "来源出口还有未登记到画布的分支，无法确认规范化范围");
                }
            }
        }
        var compiled = mapping.compileBatch(specs);
        Map<String, MappingEngine.Plan> plans = new LinkedHashMap<>();
        compiled.forEach((nodeId, result) -> {
            if (!result.plan().dictionaries().isEmpty()) {
                throw failure("MIGRATION_SCOPE_UNSUPPORTED", "当前规范化升级保留原引用，暂不替换含字典的治理计划");
            }
            plans.put(nodeId, result.plan());
        });
        if (alreadyApplied) {
            for (Branch branch : branches) {
                JsonNode execution = parse(property(branch.governor(), EXECUTION_PLAN).asText());
                if (!(execution instanceof ObjectNode published)
                        || published.path("_deployment").path("planId").asText().isBlank()) {
                    throw failure("MIGRATION_UNAVAILABLE", "已规范化治理节点缺少可核验的正式计划");
                }
                published.remove("_deployment");
                if (!plans.get(branch.edge().target()).hash().equals(MappingEngine.compile(published).hash())) {
                    throw failure("DRAFT_VERSION_CONFLICT", "治理草稿与已发布计划不同，请使用字段节点升级");
                }
            }
        }
        nifi.requireMappingExtension();
        ObjectNode witness = JSON.createObjectNode().put("id", pipeline.id()).put("updatedAt", pipeline.updatedAt())
                .put("processGroupId", group).put("sourceNodeId", source.id());
        witness.set("shape", shape);
        witness.set("states", JSON.valueToTree(graph.states()));
        witness.set("processors", JSON.valueToTree(graph.processorRevisions()));
        witness.set("connections", JSON.valueToTree(graph.connectionRevisions()));
        var hashes = witness.putObject("plans");
        plans.forEach((id, plan) -> hashes.put(id, plan.hash()));
        return new Plan(pipeline, source, graph, shape, branches, plans, MappingEngine.hash(witness), alreadyApplied);
    }

    public Map<String, Object> upgradeWorkbookRows(Pipeline pipeline, String tenant, String sourceNodeId,
                                                   boolean dryRun, String expectedFingerprint) {
        requireStopped(pipeline, tenant);
        try (var ignored = nifi.bindAssignedNode(pipeline.nodeMapping().runtimeNodeId())) {
            Pipeline.Node source = minioSource(pipeline, sourceNodeId);
            Graph original = readOwnedGraph(pipeline, tenant, source);
            JsonNode completeShape = compiler.registeredSourceShape(pipeline, sourceNodeId);
            if (!(completeShape instanceof ObjectNode workbook) || !workbook.path("skipBlankRecords").asBoolean(false)) {
                throw failure("SOURCE_SHAPE_REQUIRED", "空行策略升级前须重新采集原工作簿的字段快照");
            }
            var eligible = new LinkedHashMap<String, JsonNode>();
            for (Pipeline.Edge edge : pipeline.dsl().edges()) {
                if (!sourceNodeId.equals(edge.source())) continue;
                var boundary = pipeline.nodeMapping().edgeProcessorEndpoints().get(edge.id());
                if (boundary == null) throw changed();
                String id = boundary.sourceProcessorId();
                JsonNode entity = original.processors().get(id);
                JsonNode link = original.connections().get(boundary.connectionId());
                if (entity == null || link == null || !id.equals(link.path("component").path("source").path("id").asText())
                        || !sourceNodeId.equals(pipeline.nodeMapping().processorIdToCanvasNode().get(id))
                        || !NORMALIZER.equals(entity.path("component").path("type").asText())) throw changed();
                eligible.put(id, entity);
            }
            if (eligible.isEmpty() || eligible.size() > 32) {
                throw failure("MIGRATION_SCOPE_UNSUPPORTED", "未找到有界的已发布工作簿规范化组件");
            }
            ObjectNode wantedPaths = workbook.deepCopy();
            wantedPaths.remove("skipBlankRecords");
            for (JsonNode entity : eligible.values()) {
                if (!(parse(property(entity, SHAPE).asText()) instanceof ObjectNode previous)
                        || !wantedPaths.equals(previous.without("skipBlankRecords"))
                        || !"STOPPED".equals(entity.path("component").path("state").asText())) throw changed();
            }
            var changes = new LinkedHashMap<>(eligible);
            changes.values().removeIf(entity -> bundle().equals(entity.path("component").path("bundle"))
                    && completeShape.equals(parse(property(entity, SHAPE).asText())));
            nifi.requireMappingExtension();
            ObjectNode witness = JSON.createObjectNode().put("pipelineId", pipeline.id()).put("updatedAt", pipeline.updatedAt())
                    .put("source", sourceNodeId);
            witness.set("states", JSON.valueToTree(original.states()));
            witness.set("components", JSON.valueToTree(original.processorRevisions()));
            witness.set("connections", JSON.valueToTree(original.connectionRevisions()));
            witness.set("shape", workbook);
            String fingerprint = MappingEngine.hash(witness);
            if (!dryRun && !fingerprint.equals(expectedFingerprint)) {
                throw failure("DRAFT_VERSION_CONFLICT", "原生工作簿范围或采集快照发生变化，请重新预检");
            }
            if (!dryRun && !changes.isEmpty()) {
                patchWorkbookPolicy(pipeline.nifiProcessGroupId(), original, changes, JSON.createObjectNode().put(SHAPE, workbook.toString()));
            }
            return Map.of("mode", "workbook-record-policy", "groupId", pipeline.nifiProcessGroupId(), "fingerprint", fingerprint,
                    "normalizerIds", eligible.keySet(), "dryRun", dryRun, "alreadyApplied", changes.isEmpty(),
                    "applied", !dryRun && !changes.isEmpty(), "started", false);
        }
    }

    private void patchWorkbookPolicy(String group, Graph original, Map<String, JsonNode> changes, ObjectNode properties) {
        List<String> attempted = new ArrayList<>();
        try {
            for (var change : changes.entrySet()) {
                attempted.add(change.getKey());
                setGovernor(change.getValue(), bundle(), properties);
            }
            nifi.requireValidProcessors(group, changes.keySet());
            verifyWorkbookPolicy(group, original, changes, properties);
        } catch (RuntimeException error) {
            boolean recovered = false;
            try {
                for (String id : attempted.reversed()) {
                    JsonNode previous = changes.get(id);
                    JsonNode current = nifi.get("/processors/" + id, JsonNode.class);
                    if (stableComponent(previous).equals(stableComponent(current))) continue;
                    requireChangedGovernor(previous, current, properties);
                    ObjectNode priorShape = JSON.createObjectNode().set(SHAPE, property(previous, SHAPE));
                    setGovernor(current, previous.path("component").path("bundle"), priorShape);
                }
                verifyWorkbookPolicy(group, original, Map.of(), JSON.createObjectNode());
                recovered = true;
            } catch (RuntimeException recovery) {
                error.addSuppressed(recovery);
            }
            throw new MappingException(recovered ? "TRANSPORT_UPGRADE_FAILED" : "TRANSPORT_UPGRADE_RECOVERY_REQUIRED",
                    recovered ? "工作簿空行策略升级失败，原配置已恢复" : "须核对工作簿原生组件，流程保持停止", error);
        }
    }

    private void verifyWorkbookPolicy(String group, Graph original, Map<String, JsonNode> changes, ObjectNode properties) {
        Graph current = nativeGraph.read(group);
        if (!original.states().equals(current.states()) || !unchanged(original.connections(), current.connections())
                || !unchanged(original.funnels(), current.funnels()) || !original.processors().keySet().equals(current.processors().keySet())) {
            throw changed();
        }
        original.processors().forEach((id, entity) -> {
            JsonNode after = current.processors().get(id);
            if (changes.containsKey(id)) requireChangedGovernor(entity, after, properties);
            else if (!stableComponent(entity).equals(stableComponent(after))) throw changed();
        });
    }

    private static boolean unchanged(Map<String, JsonNode> before, Map<String, JsonNode> after) {
        return before.keySet().equals(after.keySet()) && before.entrySet().stream().allMatch(item -> {
            JsonNode current = after.get(item.getKey());
            return revision(item.getValue()) == revision(current) && stableComponent(item.getValue()).equals(stableComponent(current));
        });
    }

    private Branch branch(Pipeline pipeline, Graph graph, Pipeline.Edge edge, Pipeline.Node target, JsonNode shape) {
        NifiNodeMapping indexes = pipeline.nodeMapping();
        var endpoint = indexes.edgeProcessorEndpoints().get(edge.id());
        if (target == null || !Set.of("transform.field-mapping", "transform.field-enrichment").contains(target.manifestKey())
                || endpoint == null || !edge.source().equals(indexes.processorIdToCanvasNode().get(endpoint.sourceProcessorId()))
                || !Objects.equals(endpoint.targetProcessorId(), indexes.primaryProcessorIds().get(edge.target()))
                || !edge.target().equals(indexes.processorIdToCanvasNode().get(endpoint.targetProcessorId()))) {
            throw failure("MIGRATION_SCOPE_UNSUPPORTED", "只升级从本来源直达正式治理的已登记分支");
        }
        JsonNode link = graph.connections().get(endpoint.connectionId());
        JsonNode governor = graph.processors().get(endpoint.targetProcessorId());
        JsonNode outlet = graph.processors().get(endpoint.sourceProcessorId());
        if (link == null || governor == null || outlet == null
                || !endpoint.sourceProcessorId().equals(link.path("component").path("source").path("id").asText())
                || !endpoint.targetProcessorId().equals(link.path("component").path("destination").path("id").asText())
                || !GOVERNANCE.equals(governor.path("component").path("type").asText())
                || !"STOPPED".equals(governor.path("component").path("state").asText())) {
            throw failure("MIGRATION_UNAVAILABLE", "画布和当前原生治理边界不一致");
        }
        boolean normalized = NORMALIZER.equals(outlet.path("component").path("type").asText());
        if (normalized && (!shape.equals(parse(property(outlet, SHAPE).asText()))
                || !bundle().equals(outlet.path("component").path("bundle"))
                || !bundle().equals(governor.path("component").path("bundle")))) {
            throw failure("TRANSPORT_UPGRADE_RECOVERY_REQUIRED", "已安装的规范化分支版本或字段路径与预检不符");
        }
        return new Branch(edge, link, governor, normalized);
    }

    private Map<String, Object> apply(Plan plan) {
        Pipeline pipeline = plan.pipeline();
        String group = pipeline.nifiProcessGroupId();
        Map<String, String> planIds = versions.archive(pipeline, plan.plans());
        Map<String, Inserted> changes = new LinkedHashMap<>();
        Map<String, ObjectNode> governors = new LinkedHashMap<>();
        Map<String, String> destinations = new LinkedHashMap<>();
        List<String> undoCreates = new ArrayList<>();
        NifiNodeMapping next = null;
        try {
            for (Branch branch : plan.branches()) {
                JsonNode oldPosition = branch.governor().path("component").path("position");
                double x = oldPosition.path("x").asDouble() - 200;
                double y = oldPosition.path("y").asDouble();
                String newNode = created(nifi.createProcessor(group, NORMALIZER, normalizerPrefix(plan.source().id()) + branch.edge().id(),
                        x, y, Map.of(SHAPE, plan.shape().toString()), null, null).id(), "/processors", undoCreates);
                String funnel = created(nifi.createRetentionFunnel(group, x + 70, y + 120).id(), "/funnels", undoCreates);
                String failure = created(nifi.createBufferedConnection(group, newNode, funnel, "FUNNEL", List.of("failure")).id(),
                        "/connections", undoCreates);
                String downstream = created(nifi.createConnection(group, newNode, "PROCESSOR",
                        branch.governor().path("id").asText(), "PROCESSOR", List.of("success")).id(), "/connections", undoCreates);
                changes.put(branch.edge().id(), new Inserted(newNode, downstream, failure, funnel));
            }
            for (Branch branch : plan.branches()) {
                String governor = branch.governor().path("id").asText();
                String node = branch.edge().target();
                ObjectNode execution = plan.plans().get(node).document().deepCopy();
                execution.putObject("_deployment").put("contextNodeId", pipeline.id() + ":" + node)
                        .put("planId", planIds.get(node));
                ObjectNode patch = JSON.createObjectNode().put(EXECUTION_PLAN, execution.toString());
                governors.put(governor, patch);
                setGovernor(branch.governor(), bundle(), patch);
            }
            Set<String> validation = new LinkedHashSet<>(governors.keySet());
            changes.values().forEach(inserted -> validation.add(inserted.normalizer()));
            for (Branch branch : plan.branches()) {
                String destination = changes.get(branch.edge().id()).normalizer();
                destinations.put(branch.connection().path("id").asText(), destination);
                setDestination(branch.connection(), group, destination);
            }
            nifi.requireValidProcessors(group, validation);
            verifyGraph(plan, changes, governors, destinations);
            next = indexes(plan, changes);
            NifiNodeMapping committed = next;
            transaction.executeWithoutResult(status -> {
                versions.activateNodes(pipeline, planIds, group);
                pipelines.update(pipeline.id(), saved -> {
                    if (!Objects.equals(saved.updatedAt(), pipeline.updatedAt()) || !Objects.equals(saved.nodeMapping(), pipeline.nodeMapping())) {
                        throw failure("DRAFT_VERSION_CONFLICT", "画布在原生升级期间已修改");
                    }
                    return withIndexes(saved, committed);
                });
            });
            Map<String, String> inserted = new LinkedHashMap<>();
            changes.forEach((edge, change) -> inserted.put(edge, change.normalizer()));
            return result(plan, false, inserted, planIds);
        } catch (RuntimeException error) {
            boolean restored = restore(plan, next, governors, destinations, undoCreates, error);
            throw new MappingException(restored ? "TRANSPORT_UPGRADE_FAILED" : "TRANSPORT_UPGRADE_RECOVERY_REQUIRED",
                    restored ? "规范化升级失败，原生连接及治理已恢复" : "规范化升级需人工核对，流程保持停止", error);
        }
    }

    private boolean restore(Plan plan, NifiNodeMapping next, Map<String, ObjectNode> governors,
                            Map<String, String> destinations, List<String> creates, RuntimeException error) {
        try {
            if (next != null && !Objects.equals(pipelines.findById(plan.pipeline().id()).orElseThrow().nodeMapping(), plan.pipeline().nodeMapping())) {
                return false;
            }
            String group = plan.pipeline().nifiProcessGroupId();
            for (Branch branch : plan.branches().reversed()) {
                String id = branch.connection().path("id").asText();
                if (destinations.containsKey(id)) {
                    JsonNode current = nifi.get("/connections/" + id, JsonNode.class);
                    if (!stableComponent(current).equals(stableComponent(branch.connection()))) {
                        requireChangedDestination(branch.connection(), current, destinations.get(id));
                        setDestination(current, group, branch.connection().path("component").path("destination").path("id").asText());
                    }
                }
                String governor = branch.governor().path("id").asText();
                if (governors.containsKey(governor)) {
                    JsonNode current = nifi.get("/processors/" + governor, JsonNode.class);
                    if (!comparableGovernor(current).equals(comparableGovernor(branch.governor()))) {
                        requireChangedGovernor(branch.governor(), current, governors.get(governor));
                        ObjectNode old = JSON.createObjectNode().set(EXECUTION_PLAN, property(branch.governor(), EXECUTION_PLAN));
                        setGovernor(current, branch.governor().path("component").path("bundle"), old);
                    }
                }
            }
            for (String path : creates.reversed()) {
                JsonNode entity = nifi.get(path, JsonNode.class);
                nifi.delete(path + "?version=" + revision(entity) + "&clientId=" + nifi.clientId());
            }
            verifyGraph(plan, Map.of(), Map.of(), Map.of());
            return true;
        } catch (RuntimeException recovery) {
            error.addSuppressed(recovery);
            return false;
        }
    }

    private void verifyGraph(Plan plan, Map<String, Inserted> changes, Map<String, ObjectNode> governors,
                             Map<String, String> destinations) {
        Graph after = nativeGraph.read(plan.pipeline().nifiProcessGroupId());
        Graph before = plan.graph();
        Set<String> expectedProcessors = new LinkedHashSet<>(before.processors().keySet());
        Set<String> expectedConnections = new LinkedHashSet<>(before.connections().keySet());
        Set<String> expectedFunnels = new LinkedHashSet<>(before.funnels().keySet());
        for (Inserted inserted : changes.values()) {
            expectedProcessors.add(inserted.normalizer());
            expectedConnections.add(inserted.downstream());
            expectedConnections.add(inserted.failure());
            expectedFunnels.add(inserted.funnel());
        }
        if (!before.states().equals(after.states()) || !expectedProcessors.equals(after.processors().keySet())
                || !expectedConnections.equals(after.connections().keySet())
                || !expectedFunnels.equals(after.funnels().keySet())) {
            throw failure("MIGRATION_VALIDATION_FAILED", "原生范围或完整持久状态在升级期间发生变化");
        }
        before.processors().forEach((id, original) -> {
            JsonNode current = after.processors().get(id);
            if (governors.containsKey(id)) requireChangedGovernor(original, current, governors.get(id));
            else if (!stableComponent(original).equals(stableComponent(current))) throw changed();
        });
        before.connections().forEach((id, original) -> {
            JsonNode current = after.connections().get(id);
            if (destinations.containsKey(id)) requireChangedDestination(original, current, destinations.get(id));
            else if (!stableComponent(original).equals(stableComponent(current))) throw changed();
        });
        for (Branch branch : plan.branches()) {
            Inserted inserted = changes.get(branch.edge().id());
            if (inserted == null) continue;
            JsonNode normalized = after.processors().get(inserted.normalizer());
            if (!bundle().equals(normalized.path("component").path("bundle"))
                    || !plan.shape().equals(parse(property(normalized, SHAPE).asText()))) throw changed();
            requireLink(after, inserted.downstream(), inserted.normalizer(), branch.governor().path("id").asText(), "PROCESSOR", "success");
            requireLink(after, inserted.failure(), inserted.normalizer(), inserted.funnel(), "FUNNEL", "failure");
        }
        before.funnels().forEach((id, original) -> {
            if (!stableComponent(original).equals(stableComponent(after.funnels().get(id)))) throw changed();
        });
    }

    private static void requireLink(Graph graph, String id, String source, String target, String targetType, String relation) {
        JsonNode edge = graph.connections().get(id).path("component");
        if (!source.equals(edge.path("source").path("id").asText())
                || !target.equals(edge.path("destination").path("id").asText())
                || !targetType.equals(edge.path("destination").path("type").asText())
                || !JSON.createArrayNode().add(relation).equals(edge.path("selectedRelationships"))) throw changed();
    }

    private static void requireChangedDestination(JsonNode original, JsonNode actual, String destination) {
        var before = stableComponent(original);
        var after = stableComponent(actual);
        JsonNode endpoint = after.remove("destination");
        if (endpoint == null || !destination.equals(endpoint.path("id").asText()) || !"PROCESSOR".equals(endpoint.path("type").asText())
                || !before.path("destination").path("groupId").equals(endpoint.path("groupId"))) throw changed();
        before.remove("destination");
        if (!before.equals(after)) throw changed();
    }

    static void requireChangedGovernor(JsonNode original, JsonNode actual, ObjectNode properties) {
        ObjectNode expected = comparableGovernor(original);
        expected.set("bundle", bundle());
        var expectedProperties = (ObjectNode) expected.path("config").path("properties");
        expectedProperties.setAll(properties);
        ObjectNode observed = comparableGovernor(actual);
        var observedProperties = (ObjectNode) observed.path("config").path("properties");
        var oldDescriptors = original.path("component").path("config").path("descriptors");
        var newDescriptors = actual.path("component").path("config").path("descriptors");
        List<String> implicitDefaults = new ArrayList<>();
        observedProperties.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!expectedProperties.has(key) && !oldDescriptors.has(key)
                    && entry.getValue().equals(newDescriptors.path(key).path("defaultValue"))) {
                implicitDefaults.add(key);
            }
        });
        observedProperties.remove(implicitDefaults);
        if (!expected.equals(observed)) throw changed();
    }

    private static ObjectNode comparableGovernor(JsonNode entity) {
        ObjectNode component = stableComponent(entity);
        var props = (ObjectNode) component.path("config").path("properties");
        List<String> empty = new ArrayList<>();
        props.fields().forEachRemaining(entry -> { if (entry.getValue().isNull()) empty.add(entry.getKey()); });
        props.remove(empty);
        component.path("relationships").forEach(relationship -> ((ObjectNode) relationship).remove("description"));
        return component;
    }

    private void setDestination(JsonNode original, String group, String destination) {
        ObjectNode body = revisionBody(original);
        body.putObject("component").put("id", original.path("id").asText()).putObject("destination")
                .put("id", destination).put("type", "PROCESSOR").put("groupId", group);
        nifi.put("/connections/" + original.path("id").asText(), body, JsonNode.class);
    }

    private void setGovernor(JsonNode original, JsonNode bundle, ObjectNode properties) {
        ObjectNode body = revisionBody(original);
        var component = body.putObject("component").put("id", original.path("id").asText());
        component.set("bundle", bundle);
        component.putObject("config").set("properties", properties);
        nifi.put("/processors/" + original.path("id").asText(), body, JsonNode.class);
    }

    private ObjectNode revisionBody(JsonNode existing) {
        ObjectNode body = JSON.createObjectNode().put("disconnectedNodeAcknowledged", false);
        body.putObject("revision").put("version", revision(existing)).put("clientId", nifi.clientId());
        return body;
    }

    private static NifiNodeMapping indexes(Plan plan, Map<String, Inserted> changes) {
        NifiNodeMapping previous = plan.pipeline().nodeMapping();
        Map<String, String> reverse = new LinkedHashMap<>(previous.processorIdToCanvasNode());
        var edges = new LinkedHashMap<>(previous.edgeProcessorEndpoints());
        changes.forEach((edge, change) -> {
            reverse.put(change.normalizer(), plan.source().id());
            var existing = edges.get(edge);
            edges.put(edge, new NifiNodeMapping.EdgeEndpoint(change.normalizer(), existing.targetProcessorId(), change.downstream()));
        });
        return previous.withEndpoints(previous.primaryProcessorIds(), reverse, edges);
    }

    private static Pipeline withIndexes(Pipeline saved, NifiNodeMapping next) {
        return new Pipeline(saved.id(), saved.name(), saved.description(), saved.createdAt(), saved.updatedAt(), saved.dsl(),
                saved.nifiProcessGroupId(), saved.status(), saved.lastDeployedHash(), saved.lastDeployedAt(),
                saved.lastStoppedAt(), next, saved.lastBulletinId(), saved.storedDesignHash());
    }

    private static boolean sameSourceProperty(Pipeline.Node source, JsonNode processor, String saved, String nativeKey) {
        String value = source.config() == null ? "" : Objects.toString(source.config().get(saved), "");
        return !value.isBlank() && value.equals(property(processor, nativeKey).asText());
    }

    private static JsonNode property(JsonNode entity, String key) {
        return entity.path("component").path("config").path("properties").path(key);
    }

    private static JsonNode savedSpec(Pipeline.Node node) {
        Object saved = node.config().get("mappings");
        return saved instanceof String text ? parse(text) : JSON.valueToTree(saved);
    }

    private static JsonNode parse(String value) {
        try { return JSON.readTree(value); }
        catch (JsonProcessingException invalid) { throw failure("INVALID_JSON", "原生组件或已保存计划的 JSON 无法解析"); }
    }

    private static String created(String id, String path, List<String> undo) {
        if (id == null || id.isBlank()) throw failure("MIGRATION_VALIDATION_FAILED", "原生候选未返回组件标识");
        undo.add(path + "/" + id);
        return id;
    }

    private static String normalizerPrefix(String node) {
        return "登记字段规范化 / " + node + " / ";
    }

    private static ObjectNode bundle() {
        return JSON.createObjectNode().put("group", "com.linewell.dataelement").put("artifact", "haitong-mapping-nar")
                .put("version", MappingEngine.engineVersion());
    }

    private static MappingException changed() {
        return failure("MIGRATION_VALIDATION_FAILED", "原生处理器或连接的受保护配置发生变化");
    }

    private static MappingException failure(String code, String message) {
        return new MappingException(code, message);
    }

    private static Map<String, Object> result(Plan plan, boolean dryRun, Map<String, String> inserted, Map<String, String> planIds) {
        Map<String, String> hashes = new LinkedHashMap<>();
        plan.plans().forEach((node, compiled) -> hashes.put(node, compiled.hash()));
        return Map.of("mode", "source-normalization", "groupId", plan.pipeline().nifiProcessGroupId(),
                "fingerprint", plan.fingerprint(), "planHashes", hashes, "alreadyApplied", plan.alreadyApplied(),
                "dryRun", dryRun, "applied", !inserted.isEmpty(), "sourceNormalizedByEdge", inserted,
                "planIds", planIds, "started", false);
    }

    private record Branch(Pipeline.Edge edge, JsonNode connection, JsonNode governor, boolean normalized) {}
    private record Inserted(String normalizer, String downstream, String failure, String funnel) {}
    private record Plan(Pipeline pipeline, Pipeline.Node source, Graph graph, JsonNode shape, List<Branch> branches,
                        Map<String, MappingEngine.Plan> plans, String fingerprint, boolean alreadyApplied) {}
}
