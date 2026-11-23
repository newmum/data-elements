package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.mapping.LegacyNativeGraph.Graph;
import com.linewell.dataelement.integration.nifi.canvas.mapping.RegisteredMappingSchemaService.Resolved;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.mapping.core.MappingEngine;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.metautil.ddl.DdlDialect;
import com.linewell.dataelement.metautil.jdbc.NifiOracleJdbcDriver;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static com.linewell.dataelement.integration.nifi.canvas.mapping.LegacyNativeGraph.revision;
import static com.linewell.dataelement.integration.nifi.canvas.mapping.LegacyNativeGraph.stableComponent;

/** Upgrades codecs on stopped, registered JDBC branches without replacing their sources or connections. */
@Service
public class LegacyExactValueUpgradeService {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final String MODE = "exact-value-readers";
    private static final String GOV = "com.linewell.dataelement.nifi.HaitongTransformRecord";
    private static final String EXACT_READER = "com.linewell.dataelement.nifi.HaitongJdbcRecordReader";
    private static final String JSON_TREE_READER = "org.apache.nifi.json.JsonTreeReader";
    private static final String DBCP = "org.apache.nifi.dbcp.DBCPConnectionPool";
    private static final String SERVICES = "/controller-services";
    private static final String POOL = "put-db-record-dcbp-service";
    private static final String SOURCE_POOL = "Database Connection Pooling Service";
    private static final String INSERT_READER = "put-db-record-record-reader";
    private static final String READER = "Record Reader";
    private static final String PLAN = "execution-plan";
    private static final String URL = "Database Connection URL";
    private static final String USER = "Database User";
    private static final String DRIVER = "Database Driver Class Name";
    private static final String LOCATIONS = "Database Driver Locations";
    private static final Set<String> JDBC_SOURCES = Set.of("source.oracle", "source.postgresql", "source.mysql");
    private static final Set<String> NUMERIC_TYPES = Set.of("NUMBER", "NUMERIC", "DECIMAL", "BIGINT",
            "INTEGER", "INT", "SMALLINT", "TINYINT");
    private static final List<String> READER_FORMATS = List.of("Date Format", "Time Format", "Timestamp Format",
            "Max String Length", "Allow Comments");
    private static final List<String> POOL_LIMITS = List.of("Max Total Connections", "Max Wait Time",
            "Maximum Connection Lifetime", "Maximum Idle Connections", "Minimum Evictable Idle Time",
            "Minimum Idle Connections", "Soft Minimum Evictable Idle Time", "Time Between Eviction Runs");

    private final NifiClient nifi;
    private final LegacyNativeGraph graphs;
    private final DslCompiler compiler;
    private final RegisteredMappingSchemaService schemas;
    private final UnifiedMappingService mapping;
    private final MappingVersionStore versions;
    private final PipelineRepository pipelines;
    private final TransactionTemplate transaction;

    public LegacyExactValueUpgradeService(NifiClient nifi, DslCompiler compiler, RegisteredMappingSchemaService schemas,
                                          UnifiedMappingService mapping, MappingVersionStore versions, PipelineRepository pipelines,
                                          PlatformTransactionManager manager) {
        this.nifi = nifi;
        this.graphs = new LegacyNativeGraph(nifi);
        this.compiler = compiler;
        this.schemas = schemas;
        this.mapping = mapping;
        this.versions = versions;
        this.pipelines = pipelines;
        this.transaction = new TransactionTemplate(manager);
    }

    public Map<String, Object> upgrade(Pipeline pipeline, String tenant, String sourceNodeId,
                                       boolean dryRun, String expectedFingerprint) {
        var keys = pipeline.nodeMapping();
        if (!Objects.equals(tenant, TenantContext.requireTenantId())) {
            throw failure("RESOURCE_FORBIDDEN", "流程必须属于当前租户");
        }
        if (pipeline.status() != PipelineStatus.STOPPED || pipeline.nifiProcessGroupId() == null || keys == null
                || keys.candidate() != null || keys.primaryProcessorIds() == null || keys.processorIdToCanvasNode() == null
                || keys.edgeProcessorEndpoints() == null || pipeline.dsl() == null || pipeline.dsl().nodes() == null
                || pipeline.dsl().edges() == null || pipeline.dsl().nodes().size() > 64 || pipeline.dsl().edges().size() > 128) {
            throw failure("MIGRATION_UNAVAILABLE", "流程须停止、有完整原生映射且无候选部署");
        }
        try (var ignored = nifi.bindAssignedNode(keys.runtimeNodeId())) {
            Prepared prepared = prepare(pipeline, tenant, sourceNodeId);
            if (!dryRun && !prepared.fingerprint().equals(expectedFingerprint)) {
                throw failure("DRAFT_VERSION_CONFLICT", "登记字段、原生引用或流程版本在预检后变化");
            }
            if (dryRun || prepared.complete()) return response(prepared, dryRun, Map.of(), Map.of());
            return apply(prepared);
        }
    }

    private Prepared prepare(Pipeline pipeline, String tenant, String sourceId) {
        String group = pipeline.nifiProcessGroupId();
        if (!("data-elements:v1:" + tenant + ":pipeline:" + pipeline.id())
                .equals(nifi.getProcessGroup(group).path("component").path("comments").asText())) {
            throw failure("RESOURCE_FORBIDDEN", "原生流程组与当前租户的流程标记不符");
        }
        Map<String, Pipeline.Node> nodes = new LinkedHashMap<>();
        pipeline.dsl().nodes().forEach(node -> nodes.put(node.id(), node));
        Pipeline.Node source = nodes.get(sourceId);
        if (source == null || !JDBC_SOURCES.contains(source.manifestKey())) {
            throw failure("RESOURCE_FORBIDDEN", "未找到可核验的登记 JDBC 来源");
        }
        List<Pipeline.Edge> sourceEdges = outgoing(pipeline, sourceId);
        if (sourceEdges.isEmpty() || sourceEdges.size() > 8) throw unsupported();
        Graph graph = graphs.read(group);
        String primary = pipeline.nodeMapping().primaryProcessorIds().get(sourceId);
        JsonNode sourceProcessor = ownedProcessor(graph, pipeline, sourceId, primary);
        if (!Set.of("GenerateTableFetch", "GenerateDateTableFetch", "QueryDatabaseTableRecord").contains(type(sourceProcessor))
                || !graph.states().containsKey(primary)) {
            throw failure("MIGRATION_UNAVAILABLE", "无法核验来源原生组件和完整水位");
        }
        Map<String, JsonNode> references = new LinkedHashMap<>();
        Map<String, JsonNode> specifications = new LinkedHashMap<>();
        List<Boundary> boundaries = new ArrayList<>();
        references.put(sourceId, reference(source, "registeredDatasourceId", "sourceTableId"));
        for (Pipeline.Edge sourceEdge : sourceEdges) {
            Pipeline.Node governor = nodes.get(sourceEdge.target());
            if (governor == null || !Set.of("transform.field-mapping", "transform.field-enrichment").contains(governor.manifestKey())) {
                throw unsupported();
            }
            List<Pipeline.Edge> targetEdges = outgoing(pipeline, governor.id());
            if (targetEdges.size() != 1) throw unsupported();
            Pipeline.Node target = nodes.get(targetEdges.getFirst().target());
            if (target == null || !Set.of("sink.jdbc", "sink.hive").contains(target.manifestKey())) throw unsupported();
            var intake = boundary(pipeline, graph, sourceEdge);
            var delivery = boundary(pipeline, graph, targetEdges.getFirst());
            if (!intake.targetProcessorId().equals(delivery.sourceProcessorId())) throw unsupported();
            JsonNode actualGovernor = ownedProcessor(graph, pipeline, governor.id(), intake.targetProcessorId());
            if (!GOV.equals(actualGovernor.path("component").path("type").asText())
                    || !"STOPPED".equals(actualGovernor.path("component").path("state").asText())) throw unsupported();
            if (references.putIfAbsent(target.id(), reference(target, "targetDbId", "targetTableId")) != null
                    || specifications.putIfAbsent(governor.id(), specification(governor)) != null) throw unsupported();
            boundaries.add(new Boundary(governor, target, actualGovernor));
        }
        Map<String, Resolved> registrations = schemas.resolve(references);
        if (!registrations.keySet().equals(references.keySet())) throw unauthorized();
        Resolved registeredSource = registrations.get(sourceId);
        if (!matchesTable(property(sourceProcessor, "Table Name").asText(), registeredSource)) throw unauthorized();
        Map<String, Resolved> targets = new LinkedHashMap<>(registrations);
        targets.remove(sourceId);
        var physical = schemas.physicalBatch(targets);
        if (!physical.keySet().equals(targets.keySet())) throw unauthorized();
        var compiled = mapping.compileBatch(specifications);
        var services = LegacyNativeGraph.indexed(nifi.get("/flow/process-groups/" + group
                + "/controller-services?includeAncestorGroups=true&includeDescendantGroups=false", JsonNode.class).path("controllerServices"));
        if (services.size() > 512) throw unsupported();
        Map<String, MappingEngine.Plan> plans = new LinkedHashMap<>();
        List<Branch> branches = new ArrayList<>();
        Map<String, JsonNode> oldServices = new LinkedHashMap<>();
        var sourceConnection = registeredSource.connections().get(registeredSource.datasourceId());
        String sourceDatabase = Objects.toString(sourceConnection.get("dbType"), "").toLowerCase(Locale.ROOT);
        if (!source.manifestKey().equals("source." + sourceDatabase)) throw unauthorized();
        var nativeSourcePool = requiredService(property(sourceProcessor, SOURCE_POOL).asText(), services, oldServices);
        requirePoolEndpoint(nativeSourcePool, compiler.registeredJdbcTransportPool(sourceConnection, null), sourceDatabase);
        for (Boundary boundary : boundaries) {
            MappingEngine.Plan plan = compiled.get(boundary.governor().id()).plan();
            if (!plan.dictionaries().isEmpty()) throw unsupported();
            plans.put(boundary.governor().id(), plan);
            Branch branch = prepareBranch(pipeline, graph, boundary, targets.get(boundary.target().id()),
                    physical.get(boundary.target().id()), plan, services, oldServices);
            branches.add(branch);
        }
        boolean complete = branches.stream().allMatch(Branch::complete);
        if (branches.stream().noneMatch(branch -> !branch.reader().isEmpty())) {
            throw failure("MIGRATION_UNAVAILABLE", "已登记目标没有需要精确读取的字段");
        }
        if (!complete && services.values().stream().map(service -> service.path("component")).anyMatch(
                service -> group.equals(service.path("parentGroupId").asText())
                        && service.path("name").asText().startsWith(servicePrefix(sourceId)))) {
            throw failure("TRANSPORT_UPGRADE_RECOVERY_REQUIRED", "旧流程已有部分精确读取候选，须核对候选引用");
        }
        nifi.requireMappingExtension();
        ObjectNode witness = JSON.createObjectNode().put("pipeline", pipeline.id()).put("updatedAt", pipeline.updatedAt())
                .put("node", sourceId);
        witness.set("states", JSON.valueToTree(graph.states()));
        witness.set("processors", JSON.valueToTree(graph.processorRevisions()));
        witness.set("connections", JSON.valueToTree(graph.connectionRevisions()));
        witness.set("targets", JSON.valueToTree(physical));
        witness.set("services", JSON.valueToTree(oldServices.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + revision(entry.getValue())).sorted().toList()));
        var plansNode = witness.putObject("plans");
        plans.forEach((key, value) -> plansNode.put(key, value.hash()));
        return new Prepared(pipeline, sourceId, graph, branches, plans, oldServices, MappingEngine.hash(witness), complete);
    }

    private Branch prepareBranch(Pipeline pipeline, Graph graph, Boundary boundary, Resolved target, JsonNode columns,
                                 MappingEngine.Plan plan, Map<String, JsonNode> services, Map<String, JsonNode> oldServices) {
        String group = pipeline.nifiProcessGroupId();
        boolean oracle = "sink.jdbc".equals(boundary.target().manifestKey());
        var connection = target.connections().get(target.datasourceId());
        if (!(oracle ? "ORACLE" : "HIVE").equalsIgnoreCase(Objects.toString(connection.get("dbType"), ""))) throw unauthorized();
        Map<String, JsonNode> consumers = new LinkedHashMap<>();
        Map<String, String> readerKeys = new LinkedHashMap<>();
        JsonNode writer = null;
        JsonNode file = null;
        for (var entry : graph.processors().entrySet()) {
            if (!boundary.target().id().equals(pipeline.nodeMapping().processorIdToCanvasNode().get(entry.getKey()))) continue;
            JsonNode processor = entry.getValue();
            String type = type(processor);
            if (oracle && "LinewellPutDatabaseRecord".equals(type)) {
                if (writer != null || !matchesTable(property(processor, "put-db-record-table-name").asText(), target)) throw unauthorized();
                writer = processor;
                consumers.put(entry.getKey(), processor);
                readerKeys.put(entry.getKey(), INSERT_READER);
            } else if (!oracle && "PutHDFS".equals(type)) {
                if (file != null || !matchesHivePath(processor, boundary.target(), target)) throw unauthorized();
                file = processor;
            } else if ("QueryRecord".equals(type) || !oracle && "ConvertRecord".equals(type)) {
                consumers.put(entry.getKey(), processor);
                readerKeys.put(entry.getKey(), READER);
            }
        }
        if (oracle ? writer == null : file == null || consumers.isEmpty()) throw unsupported();

        List<String> exactNumbers = new ArrayList<>();
        List<String> binary = new ArrayList<>();
        for (JsonNode column : columns) {
            String physical = column.path("physicalType").asText().replaceFirst("\\s*\\(.*$", "").trim().toUpperCase(Locale.ROOT);
            String name = outputName(plan, column.path("name").asText());
            if (name == null) continue;
            if (NUMERIC_TYPES.contains(physical)) exactNumbers.add(name);
            if (oracle && "BLOB".equals(physical)) {
                if (!"BINARY".equals(plan.outputContracts().get(name).logicalType())) throw unsupported();
                binary.add(name);
            }
        }
        if (oracle && exactNumbers.isEmpty() && binary.isEmpty()) throw unsupported();
        Map<String, String> reader = new LinkedHashMap<>();
        String assignedReader = "";
        if (!exactNumbers.isEmpty() || !binary.isEmpty()) {
            Set<String> existing = new LinkedHashSet<>();
            consumers.forEach((id, processor) -> existing.add(property(processor, readerKeys.get(id)).asText()));
            if (existing.size() != 1 || existing.contains("")) throw unsupported();
            assignedReader = existing.iterator().next();
            JsonNode originalReader = requiredService(assignedReader, services, oldServices);
            JsonNode readerProps = originalReader.path("component").path("properties");
            if (JSON_TREE_READER.equals(originalReader.path("component").path("type").asText())) {
                if (!"infer-schema".equals(readerProps.path("Schema Access Strategy").asText("infer-schema"))
                        || !"ROOT_NODE".equals(readerProps.path("Starting Field Strategy").asText("ROOT_NODE"))) throw unsupported();
            } else if (!EXACT_READER.equals(originalReader.path("component").path("type").asText())) throw unsupported();
            for (String key : READER_FORMATS) {
                if (readerProps.path(key).isTextual()) reader.put(key, readerProps.path(key).asText());
            }
            reader.put("number-fields", JSON.valueToTree(exactNumbers).toString());
            if (!binary.isEmpty()) reader.put("binary-fields", JSON.valueToTree(binary).toString());
        }
        Map<String, String> pool = Map.of();
        String oldPool = "";
        if (oracle) {
            oldPool = property(writer, POOL).asText();
            JsonNode existing = requiredService(oldPool, services, oldServices);
            var registeredPool = compiler.registeredJdbcTransportPool(connection, columns);
            requirePoolEndpoint(existing, registeredPool, "oracle");
            if (!binary.isEmpty()) {
                if (!NifiOracleJdbcDriver.class.getName().equals(registeredPool.get(DRIVER))
                        || !Objects.toString(registeredPool.get(LOCATIONS), "").contains(NifiOracleJdbcDriver.ASSET_NAME)) {
                    throw unauthorized();
                }
                pool = new LinkedHashMap<>(registeredPool);
                JsonNode previous = existing.path("component").path("properties");
                for (String key : POOL_LIMITS) {
                    if (previous.path(key).isTextual()) pool.put(key, previous.path(key).asText());
                }
            }
        }
        JsonNode governor = boundary.nativeGovernor();
        JsonNode governorReader = requiredService(property(governor, "record-reader").asText(), services, oldServices);
        if (!JSON_TREE_READER.equals(governorReader.path("component").path("type").asText())
                || !"infer-schema".equals(governorReader.path("component").path("properties").path("Schema Access Strategy").asText("infer-schema"))) {
            throw unsupported();
        }
        JsonNode governorWriter = requiredService(property(governor, "record-writer").asText(), services, oldServices);
        String binaryEncoding = !oracle && plan.outputContracts().values().stream().anyMatch(field -> "BINARY".equals(field.logicalType()))
                ? "BASE64_TEXT" : "NATIVE";
        boolean textTimestamps = !governorWriter.path("component").path("properties").path("Timestamp Format").asText("").isBlank();
        ObjectNode governorProperties = JSON.createObjectNode().put("preserve-json-numbers", "true")
                .put("binary-output-encoding", binaryEncoding).put("format-historical-timestamp-text", Boolean.toString(textTimestamps));
        boolean complete = currentGovernor(governor, governorProperties, plan)
                && (reader.isEmpty() || matchesService(services.get(assignedReader), EXACT_READER, group, reader))
                && (pool.isEmpty() || matchesService(services.get(oldPool), DBCP, group, publicPoolProperties(pool)));
        return new Branch(boundary, consumers, readerKeys, reader, pool, writer, governorProperties, complete);
    }

    private static boolean matchesHivePath(JsonNode processor, Pipeline.Node sink, Resolved target) {
        String base = Objects.toString(sink.config().get("hdfsDatabaseDirectory"), "").replaceFirst("/+$", "");
        return !base.isBlank() && property(processor, "Directory").asText().equals(base + "/" + target.table());
    }

    private static String nativeOracleUrl(String value) {
        if (value == null) return "";
        if (value.startsWith(NifiOracleJdbcDriver.URL_PREFIX)) {
            return NifiOracleJdbcDriver.NATIVE_URL_PREFIX + value.substring(NifiOracleJdbcDriver.URL_PREFIX.length());
        }
        return value.startsWith(NifiOracleJdbcDriver.NATIVE_URL_PREFIX) ? value : "";
    }

    private static void requirePoolEndpoint(JsonNode service, Map<String, String> registered, String dialect) {
        JsonNode component = service.path("component");
        JsonNode properties = component.path("properties");
        String expected = registered.get(URL);
        String actual = properties.path(URL).asText();
        if (!DBCP.equals(component.path("type").asText()) || expected == null
                || !properties.path(USER).asText("").equals(Objects.toString(registered.get(USER), ""))) throw unauthorized();
        if ("oracle".equals(dialect)) {
            String nativeUrl = nativeOracleUrl(expected);
            if (nativeUrl.isBlank() || !nativeUrl.equals(nativeOracleUrl(actual))) throw unauthorized();
            return;
        }
        try {
            URI registeredUri = URI.create(expected.substring("jdbc:".length()));
            URI nativeUri = URI.create(actual.substring("jdbc:".length()));
            if (!expected.startsWith("jdbc:" + dialect + "://") || !actual.startsWith("jdbc:" + dialect + "://")
                    || registeredUri.getHost() == null || !registeredUri.getHost().equalsIgnoreCase(nativeUri.getHost())
                    || registeredUri.getPort() != nativeUri.getPort() || !Objects.equals(registeredUri.getRawPath(), nativeUri.getRawPath())
                    || nativeUri.getUserInfo() != null || registeredUri.getUserInfo() != null) throw unauthorized();
        } catch (IllegalArgumentException | IndexOutOfBoundsException invalid) {
            throw unauthorized();
        }
    }

    private static Map<String, String> publicPoolProperties(Map<String, String> properties) {
        var expected = new LinkedHashMap<String, String>();
        for (String key : List.of(URL, USER, DRIVER, LOCATIONS)) {
            if (properties.containsKey(key)) expected.put(key, properties.get(key));
        }
        return expected;
    }

    private static boolean currentGovernor(JsonNode governor, ObjectNode settings, MappingEngine.Plan expected) {
        if (!bundle().equals(governor.path("component").path("bundle"))) return false;
        var fields = settings.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (!entry.getValue().equals(property(governor, entry.getKey()))) return false;
        }
        JsonNode execution = parse(property(governor, PLAN).asText());
        if (!(execution instanceof ObjectNode archived) || archived.path("_deployment").path("planId").asText().isBlank()) return false;
        archived.remove("_deployment");
        return expected.hash().equals(MappingEngine.compile(archived).hash());
    }

    private Map<String, Object> apply(Prepared prepared) {
        Pipeline pipeline = prepared.pipeline();
        String group = pipeline.nifiProcessGroupId();
        Map<String, String> planIds = versions.archive(pipeline, prepared.plans());
        Map<String, String> newReaders = new LinkedHashMap<>();
        Map<String, String> newPools = new LinkedHashMap<>();
        Map<String, Edit> changes = new LinkedHashMap<>();
        Set<String> created = new LinkedHashSet<>();
        boolean committing = false;
        try {
            for (Branch branch : prepared.branches()) {
                String target = branch.boundary().target().id();
                if (!branch.reader().isEmpty()) newReaders.put(target, createService(group, EXACT_READER,
                        servicePrefix(prepared.sourceId()) + target + " / Reader", branch.reader(), created));
                if (!branch.pool().isEmpty()) newPools.put(target, createService(group, DBCP,
                        servicePrefix(prepared.sourceId()) + target + " / Oracle", branch.pool(), created));
            }
            nifi.enableControllerServices(group, created, Set.of(), 30_000);
            for (Branch branch : prepared.branches()) {
                String target = branch.boundary().target().id();
                String node = branch.boundary().governor().id();
                if (newReaders.containsKey(target) && !matchesService(service(newReaders.get(target)), EXACT_READER, group, branch.reader())
                        || newPools.containsKey(target) && !matchesService(service(newPools.get(target)), DBCP, group, publicPoolProperties(branch.pool()))) {
                    throw failure("MIGRATION_VALIDATION_FAILED", "流程级精确服务配置未通过核验");
                }
                JsonNode governor = branch.boundary().nativeGovernor();
                ObjectNode settings = branch.governorProperties().deepCopy();
                ObjectNode execution = prepared.plans().get(node).document().deepCopy();
                execution.putObject("_deployment").put("contextNodeId", pipeline.id() + ":" + node).put("planId", planIds.get(node));
                settings.put(PLAN, execution.toString());
                Edit governance = new Edit(governor, settings, true);
                changes.put(governor.path("id").asText(), governance);
                applyEdit(governance);
                if (newReaders.containsKey(target)) {
                    for (var consumer : branch.consumers().entrySet()) {
                        var config = JSON.createObjectNode().put(branch.readerKeys().get(consumer.getKey()), newReaders.get(target));
                        if (newPools.containsKey(target) && consumer.getValue().equals(branch.writer())) config.put(POOL, newPools.get(target));
                        Edit edit = new Edit(consumer.getValue(), config, false);
                        changes.put(consumer.getKey(), edit);
                        applyEdit(edit);
                    }
                }
            }
            nifi.requireValidProcessors(group, changes.keySet());
            verify(prepared, changes);
            committing = true;
            transaction.executeWithoutResult(status -> {
                pipelines.update(pipeline.id(), saved -> {
                    if (!Objects.equals(saved.updatedAt(), pipeline.updatedAt()) || !Objects.equals(saved.nodeMapping(), pipeline.nodeMapping())) {
                        throw failure("DRAFT_VERSION_CONFLICT", "原流程在精确读取升级期间发生变化");
                    }
                    return saved;
                });
                versions.activateNodes(pipeline, planIds, group);
            });
            return response(prepared, false, newReaders, planIds);
        } catch (RuntimeException error) {
            boolean restored = restore(prepared, changes, created, committing, error);
            throw new MappingException(restored ? "TRANSPORT_UPGRADE_FAILED" : "TRANSPORT_UPGRADE_RECOVERY_REQUIRED",
                    restored ? "精确读取升级失败，原生引用和治理计划已恢复" : "精确读取升级需核对已暂存资源，流程保持停止", error);
        }
    }

    private void applyEdit(Edit edit) {
        JsonNode original = edit.original();
        if (!edit.governor() || bundle().equals(original.path("component").path("bundle"))) {
            writeProcessor(original, null, edit.properties());
            return;
        }
        // NiFi validates properties against the current bundle before it loads the replacement.
        writeProcessor(original, bundle(), null);
        JsonNode staged = processor(original.path("id").asText());
        LegacySourceShapeUpgradeService.requireChangedGovernor(original, staged, JSON.createObjectNode());
        writeProcessor(staged, null, edit.properties());
    }

    private boolean restore(Prepared prepared, Map<String, Edit> changes, Set<String> created, boolean committing, RuntimeException error) {
        try {
            if (committing && !Objects.equals(pipelines.findById(prepared.pipeline().id()).orElseThrow().updatedAt(),
                    prepared.pipeline().updatedAt())) return false;
            List<Map.Entry<String, Edit>> edits = new ArrayList<>(changes.entrySet());
            for (var entry : edits.reversed()) {
                Edit edit = entry.getValue();
                JsonNode actual = processor(entry.getKey());
                if (stableComponent(actual).equals(stableComponent(edit.original()))) continue;
                if (edit.governor()) {
                    try {
                        LegacySourceShapeUpgradeService.requireChangedGovernor(edit.original(), actual, edit.properties());
                    } catch (MappingException unfinished) {
                        LegacySourceShapeUpgradeService.requireChangedGovernor(edit.original(), actual, JSON.createObjectNode());
                    }
                } else requireExpectedProcessor(edit.original(), actual, edit.properties());
                ObjectNode restoredProperties = JSON.createObjectNode();
                edit.properties().fieldNames().forEachRemaining(key -> {
                    JsonNode previous = property(edit.original(), key);
                    if (previous.isMissingNode()) restoredProperties.putNull(key);
                    else restoredProperties.set(key, previous);
                });
                JsonNode oldBundle = edit.governor() ? edit.original().path("component").path("bundle") : null;
                writeProcessor(actual, oldBundle, restoredProperties);
            }
            verify(prepared, Map.of());
            for (String id : new ArrayList<>(created).reversed()) {
                JsonNode component = service(id).path("component");
                if (!"DISABLED".equals(component.path("state").asText())) {
                    nifi.setControllerServiceRunStatus(id, "DISABLED");
                    if (!nifi.waitForControllerServiceState(id, "DISABLED", 10_000)) return false;
                }
                nifi.delete(SERVICES + "/" + id + "?version=" + revision(service(id)));
            }
            return nifi.listControllerServices(prepared.pipeline().nifiProcessGroupId()).stream().noneMatch(
                    service -> service.path("component").path("name").asText().startsWith(servicePrefix(prepared.sourceId())));
        } catch (RuntimeException recovery) {
            error.addSuppressed(recovery);
            return false;
        }
    }

    private void verify(Prepared prepared, Map<String, Edit> changes) {
        Graph original = prepared.graph();
        Graph current = graphs.read(prepared.pipeline().nifiProcessGroupId());
        if (!original.states().equals(current.states()) || !original.processors().keySet().equals(current.processors().keySet())
                || !sameComponents(original.connections(), current.connections()) || !sameComponents(original.funnels(), current.funnels())) {
            throw failure("MIGRATION_VALIDATION_FAILED", "原节点、连接或完整本地和集群状态发生变化");
        }
        original.processors().forEach((id, stored) -> {
            JsonNode actual = current.processors().get(id);
            Edit edit = changes.get(id);
            if (edit == null) requireExpectedProcessor(stored, actual, JSON.createObjectNode());
            else if (edit.governor()) LegacySourceShapeUpgradeService.requireChangedGovernor(stored, actual, edit.properties());
            else requireExpectedProcessor(stored, actual, edit.properties());
        });
        prepared.oldServices().forEach((id, previous) -> {
            JsonNode actual = service(id);
            if (revision(previous) != revision(actual) || !stableComponent(previous).equals(stableComponent(actual))) {
                throw failure("MIGRATION_VALIDATION_FAILED", "原控制服务配置已变化");
            }
        });
    }

    private static boolean sameComponents(Map<String, JsonNode> original, Map<String, JsonNode> current) {
        return original.keySet().equals(current.keySet()) && original.entrySet().stream().allMatch(
                entry -> stableComponent(entry.getValue()).equals(stableComponent(current.get(entry.getKey()))));
    }

    private static void requireExpectedProcessor(JsonNode original, JsonNode current, ObjectNode props) {
        ObjectNode expected = stableComponent(original);
        if (!props.isEmpty()) ((ObjectNode) expected.path("config").path("properties")).setAll(props);
        if (!expected.equals(stableComponent(current))) {
            throw failure("MIGRATION_VALIDATION_FAILED", "原生处理器的受保护设置发生变化");
        }
    }

    private static boolean matchesService(JsonNode service, String type, String group, Map<String, String> settings) {
        if (service == null) return false;
        JsonNode component = service.path("component");
        if (!group.equals(component.path("parentGroupId").asText()) || !type.equals(component.path("type").asText())
                || !"ENABLED".equals(component.path("state").asText())
                || EXACT_READER.equals(type) && !bundle().equals(component.path("bundle"))) return false;
        return settings.entrySet().stream().allMatch(entry ->
                entry.getValue().equals(component.path("properties").path(entry.getKey()).asText()));
    }

    private String createService(String group, String type, String name, Map<String, String> properties, Set<String> created) {
        String id = nifi.createControllerService(group, type, name, properties).id();
        if (id == null || id.isBlank() || !created.add(id)) throw failure("MIGRATION_VALIDATION_FAILED", "原生服务标识不可核验");
        return id;
    }

    private JsonNode service(String id) {
        return nifi.get(SERVICES + "/" + id, JsonNode.class);
    }

    private JsonNode processor(String id) {
        return nifi.get("/processors/" + id, JsonNode.class);
    }

    private void writeProcessor(JsonNode original, JsonNode bundle, ObjectNode properties) {
        ObjectNode body = JSON.createObjectNode().put("disconnectedNodeAcknowledged", false);
        body.putObject("revision").put("version", revision(original)).put("clientId", nifi.clientId());
        String id = original.path("id").asText();
        ObjectNode component = body.putObject("component").put("id", id);
        if (bundle != null) component.set("bundle", bundle);
        if (properties != null) component.putObject("config").set("properties", properties);
        nifi.put("/processors/" + id, body, JsonNode.class);
    }

    private static JsonNode requiredService(String id, Map<String, JsonNode> services, Map<String, JsonNode> used) {
        JsonNode service = services.get(id);
        if (service == null || !"ENABLED".equals(service.path("component").path("state").asText())) throw unauthorized();
        used.put(id, service);
        return service;
    }

    private static JsonNode ownedProcessor(Graph graph, Pipeline pipeline, String node, String processor) {
        JsonNode entity = graph.processors().get(processor);
        if (entity == null || !node.equals(pipeline.nodeMapping().processorIdToCanvasNode().get(processor))) throw unauthorized();
        return entity;
    }

    private static List<Pipeline.Edge> outgoing(Pipeline pipeline, String node) {
        return pipeline.dsl().edges().stream().filter(edge -> node.equals(edge.source())).toList();
    }

    private static NifiNodeMapping.EdgeEndpoint boundary(
            Pipeline pipeline, Graph graph, Pipeline.Edge edge) {
        var endpoint = pipeline.nodeMapping().edgeProcessorEndpoints().get(edge.id());
        if (endpoint == null) throw unsupported();
        JsonNode link = graph.connections().get(endpoint.connectionId());
        if (link == null || !endpoint.sourceProcessorId().equals(link.path("component").path("source").path("id").asText())
                || !endpoint.targetProcessorId().equals(link.path("component").path("destination").path("id").asText())) throw unsupported();
        ownedProcessor(graph, pipeline, edge.source(), endpoint.sourceProcessorId());
        ownedProcessor(graph, pipeline, edge.target(), endpoint.targetProcessorId());
        return endpoint;
    }

    private static JsonNode reference(Pipeline.Node node, String sourceKey, String tableKey) {
        String datasource = node.config() == null ? "" : Objects.toString(node.config().get(sourceKey), "");
        String table = node.config() == null ? "" : Objects.toString(node.config().get(tableKey), "");
        if (datasource.isBlank() || table.isBlank()) throw unauthorized();
        return JSON.createObjectNode().put("datasourceId", datasource).put("tableId", table);
    }

    private static boolean matchesTable(String value, Resolved target) {
        try {
            var names = DdlDialect.names(value);
            return !names.isEmpty() && names.size() <= 2 && names.getLast().value().equals(target.table())
                    && (names.size() == 1 || names.getFirst().value().equals(target.schema()));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static String outputName(MappingEngine.Plan plan, String column) {
        var names = plan.outputContracts().keySet();
        if (names.contains(column)) return column;
        var aliases = names.stream().filter(name -> name.equalsIgnoreCase(column)).toList();
        if (aliases.size() > 1) throw unsupported();
        return aliases.isEmpty() ? null : aliases.getFirst();
    }

    private static String type(JsonNode entity) {
        String type = entity.path("component").path("type").asText();
        return type.substring(type.lastIndexOf('.') + 1);
    }

    private static JsonNode property(JsonNode entity, String key) {
        return entity.path("component").path("config").path("properties").path(key);
    }

    private static JsonNode specification(Pipeline.Node node) {
        Object content = node.config().get("mappings");
        return content instanceof String text ? parse(text) : JSON.valueToTree(content);
    }

    private static JsonNode parse(String document) {
        try { return JSON.readTree(document); }
        catch (JsonProcessingException invalid) { throw failure("INVALID_JSON", "已保存治理计划无法解析"); }
    }

    private static ObjectNode bundle() {
        return JSON.createObjectNode().put("group", "com.linewell.dataelement").put("artifact", "haitong-mapping-nar")
                .put("version", MappingEngine.engineVersion());
    }

    private static String servicePrefix(String source) {
        return "登记精确读取 / " + source + " / ";
    }

    private static Map<String, Object> response(Prepared prepared, boolean dryRun, Map<String, String> readers, Map<String, String> planIds) {
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        for (Branch branch : prepared.branches()) {
            fields.put(branch.boundary().target().id(), JSON.createObjectNode()
                    .put("binaryFieldCount", parse(branch.reader().getOrDefault("binary-fields", "[]")).size())
                    .put("numberFieldCount", parse(branch.reader().getOrDefault("number-fields", "[]")).size())
                    .put("dedicatedOraclePoolRequired", !branch.pool().isEmpty()));
        }
        return Map.of("mode", MODE, "groupId", prepared.pipeline().nifiProcessGroupId(), "fingerprint", prepared.fingerprint(),
                "dryRun", dryRun, "applied", !readers.isEmpty(), "alreadyApplied", prepared.complete(),
                "targetFields", fields, "readerIds", readers, "planIds", planIds, "started", false);
    }

    private static MappingException unauthorized() {
        return failure("RESOURCE_FORBIDDEN", "已保存登记、原生来源或目标范围无法相互核验");
    }

    private static MappingException unsupported() {
        return failure("MIGRATION_SCOPE_UNSUPPORTED", "精确读取升级需要边界清晰的登记治理及受支持的目标组件");
    }

    private static MappingException failure(String code, String message) {
        return new MappingException(code, message);
    }

    private record Boundary(Pipeline.Node governor, Pipeline.Node target, JsonNode nativeGovernor) {}
    private record Branch(Boundary boundary, Map<String, JsonNode> consumers, Map<String, String> readerKeys,
                          Map<String, String> reader, Map<String, String> pool, JsonNode writer, ObjectNode governorProperties,
                          boolean complete) {}
    private record Prepared(Pipeline pipeline, String sourceId, Graph graph, List<Branch> branches,
                            Map<String, MappingEngine.Plan> plans, Map<String, JsonNode> oldServices,
                            String fingerprint, boolean complete) {}
    private record Edit(JsonNode original, ObjectNode properties, boolean governor) {}
}
