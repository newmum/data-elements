package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.mapping.LegacyNativeGraph.Graph;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.metautil.ddl.DdlDialect;
import com.linewell.dataelement.metautil.jdbc.MySqlIntegerReadPolicy;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

import static com.linewell.dataelement.integration.nifi.canvas.mapping.LegacyNativeGraph.revision;
import static com.linewell.dataelement.integration.nifi.canvas.mapping.LegacyNativeGraph.stableComponent;

/** Preserves native component identities while introducing a task-local legacy reader dependency. */
@Service
public class LegacyTransportUpgradeService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String POOL_TYPE = "org.apache.nifi.dbcp.DBCPConnectionPool";
    private static final String PROCESSOR_POOL = "Database Connection Pooling Service";
    private static final String URL = "Database Connection URL";
    private static final String USER = "Database User";
    private static final String DRIVER = "Database Driver Class Name";
    private static final String SERVICES = "/controller-services";
    private static final Set<String> SOURCE_PROCESSORS = Set.of(
            "org.apache.nifi.processors.standard.GenerateTableFetch",
            "org.apache.nifi.processors.standard.ExecuteSQLRecord",
            "org.apache.nifi.processors.standard.QueryDatabaseTableRecord");
    private static final List<String> POOL_SETTINGS = List.of("Max Total Connections", "Max Wait Time",
            "Maximum Connection Lifetime", "Maximum Idle Connections", "Minimum Evictable Idle Time",
            "Minimum Idle Connections", "Soft Minimum Evictable Idle Time", "Time Between Eviction Runs");

    private final NifiClient nifi;
    private final LegacyNativeGraph nativeGraph;
    private final RegisteredMappingSchemaService schemas;
    private final DslCompiler compiler;

    public LegacyTransportUpgradeService(NifiClient nifi, RegisteredMappingSchemaService schemas, DslCompiler compiler) {
        this.nifi = nifi;
        this.nativeGraph = new LegacyNativeGraph(nifi);
        this.schemas = schemas;
        this.compiler = compiler;
    }

    public Map<String, Object> upgradeMySqlReadPool(Pipeline pipeline, String tenant, String sourceNodeId,
                                                     boolean dryRun, String expectedFingerprint) {
        if (!Objects.equals(tenant, TenantContext.requireTenantId())) {
            throw failure("RESOURCE_FORBIDDEN", "流程必须属于当前租户");
        }
        if (pipeline.nodeMapping() == null
                || pipeline.nifiProcessGroupId() == null || pipeline.status() != PipelineStatus.STOPPED
                || pipeline.nodeMapping().candidate() != null) {
            throw failure("MIGRATION_UNAVAILABLE", "流程必须属于当前租户并处于无候选部署的停止状态");
        }
        try (var ignored = nifi.bindAssignedNode(pipeline.nodeMapping().runtimeNodeId())) {
            var plan = prepare(pipeline, tenant, sourceNodeId);
            if (dryRun) return result(plan, null, true, false);
            if (!plan.fingerprint().equals(expectedFingerprint)) {
                throw failure("DRAFT_VERSION_CONFLICT", "来源组件或共享连接池在预检后发生变化，请重新预检");
            }
            if (plan.alreadyApplied()) return result(plan, plan.originalPool().path("id").asText(), false, false);
            return migrate(plan);
        }
    }

    private Plan prepare(Pipeline pipeline, String tenant, String sourceNodeId) {
        if (pipeline.dsl() == null || pipeline.dsl().nodes() == null
                || pipeline.nodeMapping().primaryProcessorIds() == null || pipeline.nodeMapping().processorIdToCanvasNode() == null) {
            throw failure("MIGRATION_UNAVAILABLE", "旧流程缺少来源的原生组件映射");
        }
        Pipeline.Node source = pipeline.dsl().nodes().stream()
                .filter(node -> node.id().equals(sourceNodeId) && "source.mysql".equals(node.manifestKey()))
                .findFirst().orElseThrow(() -> failure("RESOURCE_FORBIDDEN", "未找到当前流程中的登记 MySQL 来源节点"));
        String table = required(source.config(), "sourceTableId");
        String datasource = required(source.config(), "registeredDatasourceId");
        var reference = JSON.createObjectNode().put("tableId", table).put("datasourceId", datasource);
        var registered = schemas.resolve(Map.of(source.id(), reference)).get(source.id());
        if (registered == null || !table.equals(registered.tableId()) || !datasource.equals(registered.datasourceId())) {
            throw failure("RESOURCE_FORBIDDEN", "已授权来源登记与画布引用不一致");
        }
        var registeredConnection = registered.connections().get(datasource);
        if (registeredConnection == null) throw failure("RESOURCE_FORBIDDEN", "已授权来源没有可用连接");
        var poolProperties = new LinkedHashMap<>(compiler.registeredMySqlReadPool(registeredConnection));
        String group = pipeline.nifiProcessGroupId();
        var owner = nifi.getProcessGroup(group);
        String marker = "data-elements:v1:" + tenant + ":pipeline:" + pipeline.id();
        if (!marker.equals(owner.path("component").path("comments").asText())) {
            throw failure("RESOURCE_FORBIDDEN", "原生流程组的租户和流程标记不匹配");
        }
        Graph graph = nativeGraph.read(group);
        var readers = new LinkedHashMap<String, JsonNode>();
        graph.processors().forEach((id, entity) -> {
            JsonNode component = entity.path("component");
            if (source.id().equals(pipeline.nodeMapping().processorIdToCanvasNode().get(id))
                    && SOURCE_PROCESSORS.contains(component.path("type").asText())) {
                readers.put(id, entity);
            }
        });
        String sourcePrimary = pipeline.nodeMapping().primaryProcessorIds().get(source.id());
        if (readers.isEmpty() || readers.size() > 8 || !readers.containsKey(sourcePrimary)) {
            throw failure("MIGRATION_UNAVAILABLE", "不能唯一核验 MySQL 来源读取组件");
        }
        String nativeTable = property(readers.get(sourcePrimary), "Table Name").asText();
        if (!sameTable(nativeTable, registered.schema(), registered.table())) {
            throw failure("RESOURCE_FORBIDDEN", "原生来源表与当前已授权登记不一致");
        }
        var poolIds = new HashSet<String>();
        readers.values().forEach(reader -> poolIds.add(property(reader, PROCESSOR_POOL).asText()));
        if (poolIds.size() != 1 || poolIds.contains("")) {
            throw failure("MIGRATION_UNAVAILABLE", "原来源读取组件未使用同一个可核验连接池");
        }
        String poolId = poolIds.iterator().next();
        var originalPool = nifi.get(SERVICES + "/" + poolId, JsonNode.class);
        var pool = originalPool.path("component");
        var oldProperties = pool.path("properties");
        if (!poolId.equals(originalPool.path("id").asText()) || !POOL_TYPE.equals(pool.path("type").asText())
                || !"ENABLED".equals(pool.path("state").asText()) || !sameEndpoint(oldProperties.path(URL).asText(), poolProperties.get(URL))
                || !Objects.equals(oldProperties.path(USER).asText(""), Objects.toString(poolProperties.get(USER), ""))
                || !Objects.equals(oldProperties.path(DRIVER).asText(), poolProperties.get(DRIVER))) {
            throw failure("RESOURCE_FORBIDDEN", "原连接池与当前已授权登记连接不一致");
        }
        String oldUrl = oldProperties.path(URL).asText();
        String newUrl = MySqlIntegerReadPolicy.url(oldUrl);
        poolProperties.put(URL, newUrl);
        for (String key : POOL_SETTINGS) {
            if (oldProperties.path(key).isTextual()) poolProperties.put(key, oldProperties.path(key).asText());
        }
        return new Plan(group, source.id(), graph, readers, originalPool, poolProperties,
                fingerprint(pipeline, source.id(), graph, originalPool),
                oldUrl.equals(newUrl) && group.equals(pool.path("parentGroupId").asText()));
    }

    private Map<String, Object> migrate(Plan plan) {
        String poolName = "MySQL来源整数读取连接池/" + plan.source();
        boolean staged = nifi.listControllerServices(plan.group()).stream().map(service -> service.path("component"))
                .anyMatch(service -> plan.group().equals(service.path("parentGroupId").asText())
                        && poolName.equals(service.path("name").asText()));
        if (staged) {
            throw failure("TRANSPORT_UPGRADE_RECOVERY_REQUIRED", "当前来源已有未完成的连接池候选，须先核对候选及引用");
        }
        String newId = null;
        var attempted = new ArrayList<String>();
        try {
            newId = nifi.createControllerService(plan.group(), POOL_TYPE, poolName, plan.properties()).id();
            if (newId == null || newId.isBlank()) throw failure("MIGRATION_VALIDATION_FAILED", "未取得新的连接池标识");
            nifi.enableControllerServices(plan.group(), Set.of(newId), Set.of(), 20_000);
            var targetPool = nifi.get(SERVICES + "/" + newId, JsonNode.class).path("component");
            if (!POOL_TYPE.equals(targetPool.path("type").asText()) || !plan.group().equals(targetPool.path("parentGroupId").asText())
                    || !plan.properties().get(URL).equals(targetPool.path("properties").path(URL).asText())
                    || !"ENABLED".equals(targetPool.path("state").asText())) {
                throw failure("MIGRATION_VALIDATION_FAILED", "新连接池未在当前流程组按整数读取配置启用");
            }
            for (var reader : plan.readers().entrySet()) {
                attempted.add(reader.getKey());
                writeReference(reader.getKey(), reader.getValue(), newId);
            }
            requireSameGraph(plan, nativeGraph.read(plan.group()), newId);
            return result(plan, newId, false, true);
        } catch (RuntimeException error) {
            boolean recovered = newId != null && restore(plan, attempted, newId, error);
            throw new MappingException(recovered ? "TRANSPORT_UPGRADE_FAILED" : "TRANSPORT_UPGRADE_RECOVERY_REQUIRED",
                    recovered ? "未完成升级，已恢复所选来源连接引用" : "升级响应需核对，流程保持停止", error);
        }
    }

    private boolean restore(Plan plan, List<String> attempted, String newId, RuntimeException failure) {
        try {
            String previous = plan.originalPool().path("id").asText();
            for (String id : attempted.reversed()) {
                JsonNode current = nifi.get("/processors/" + id, JsonNode.class);
                JsonNode actual = stableComponent(current);
                if (actual.equals(stableComponent(plan.readers().get(id)))) continue;
                if (!actual.equals(withReference(plan.readers().get(id), newId))) {
                    throw failure("DRAFT_VERSION_CONFLICT", "原生读取组件已变化，未覆盖并发配置");
                }
                writeReference(id, current, previous);
            }
            requireSameGraph(plan, nativeGraph.read(plan.group()), previous);
            nifi.setControllerServiceRunStatus(newId, "DISABLED");
            if (!nifi.waitForControllerServiceState(newId, "DISABLED", 10_000)) return false;
            var recovery = nifi.get(SERVICES + "/" + newId, JsonNode.class);
            nifi.delete(SERVICES + "/" + newId + "?version=" + revision(recovery));
            return true;
        } catch (RuntimeException recovery) {
            failure.addSuppressed(recovery);
            return false;
        }
    }

    private void writeReference(String id, JsonNode current, String poolId) {
        ObjectNode body = JSON.createObjectNode();
        body.putObject("revision").put("version", revision(current)).put("clientId", nifi.clientId());
        body.putObject("component").put("id", id).putObject("config").putObject("properties").put(PROCESSOR_POOL, poolId);
        body.put("disconnectedNodeAcknowledged", false);
        nifi.put("/processors/" + id, body, JsonNode.class);
    }

    private void requireSameGraph(Plan plan, Graph current, String readerPool) {
        Graph before = plan.graph();
        if (!current.states().equals(before.states()) || !current.processors().keySet().equals(before.processors().keySet())
                || !current.connections().keySet().equals(before.connections().keySet())
                || current.connections().entrySet().stream().anyMatch(edge ->
                        !edge.getValue().path("component").equals(before.connections().get(edge.getKey()).path("component")))) {
            throw failure("MIGRATION_VALIDATION_FAILED", "原生连接或完整状态在升级期间发生变化");
        }
        for (var processor : before.processors().entrySet()) {
            String id = processor.getKey();
            JsonNode expected = plan.readers().containsKey(id) ? withReference(processor.getValue(), readerPool) : stableComponent(processor.getValue());
            if (!expected.equals(stableComponent(current.processors().get(id)))) {
                throw failure("MIGRATION_VALIDATION_FAILED", "处理器的非升级配置发生变化");
            }
        }
        var pool = nifi.get(SERVICES + "/" + plan.originalPool().path("id").asText(), JsonNode.class);
        if (revision(pool) != revision(plan.originalPool()) || !stableComponent(pool).equals(stableComponent(plan.originalPool()))) {
            throw failure("MIGRATION_VALIDATION_FAILED", "共享连接池发生了并发变化");
        }
    }

    private static ObjectNode withReference(JsonNode entity, String pool) {
        ObjectNode component = stableComponent(entity);
        ((ObjectNode) component.path("config").path("properties")).put(PROCESSOR_POOL, pool);
        return component;
    }

    private static boolean sameEndpoint(String original, String registered) {
        try {
            URI before = URI.create(original.substring("jdbc:".length()));
            URI after = URI.create(registered.substring("jdbc:".length()));
            return original.startsWith("jdbc:mysql:") && registered.startsWith("jdbc:mysql:") && before.getHost() != null
                    && before.getHost().equalsIgnoreCase(after.getHost()) && before.getPort() == after.getPort()
                    && Objects.equals(before.getRawPath(), after.getRawPath()) && before.getUserInfo() == null && after.getUserInfo() == null;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static boolean sameTable(String nativeName, String schema, String table) {
        try {
            var names = DdlDialect.names(nativeName);
            return !names.isEmpty() && names.size() <= 2 && names.getLast().value().equals(table)
                    && (names.size() == 1 || names.getFirst().value().equals(schema));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static String fingerprint(Pipeline pipeline, String source, Graph graph, JsonNode pool) {
        String basis = pipeline.id() + "|" + pipeline.updatedAt() + "|" + pipeline.nifiProcessGroupId() + "|" + source
                + "|" + pool.path("id").asText() + ":" + revision(pool) + "|"
                + graph.processors().entrySet().stream().map(edge -> edge.getKey() + ":" + revision(edge.getValue())).sorted().toList()
                + "|" + graph.connections().entrySet().stream().map(edge -> edge.getKey() + ":" + revision(edge.getValue())).sorted().toList();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(basis.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static JsonNode property(JsonNode entity, String key) {
        return entity.path("component").path("config").path("properties").path(key);
    }

    private static String required(Map<String, Object> config, String key) {
        String value = config == null ? "" : Objects.toString(config.get(key), "").trim();
        if (value.isBlank()) throw failure("RESOURCE_FORBIDDEN", "来源必须绑定已登记的数据源和表");
        return value;
    }

    private static MappingException failure(String code, String message) {
        return new MappingException(code, message);
    }

    private static Map<String, Object> result(Plan plan, String replacement, boolean dryRun, boolean applied) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "mysql-read-pool");
        result.put("groupId", plan.group());
        result.put("sourceNodeId", plan.source());
        result.put("sourceProcessorIds", plan.readers().keySet());
        result.put("oldPoolId", plan.originalPool().path("id").asText());
        result.put("newPoolId", replacement);
        result.put("fingerprint", plan.fingerprint());
        result.put("dryRun", dryRun);
        result.put("applied", applied);
        result.put("alreadyApplied", plan.alreadyApplied());
        result.put("started", false);
        return result;
    }

    private record Plan(String group, String source, Graph graph, Map<String, JsonNode> readers, JsonNode originalPool,
                        Map<String, String> properties, String fingerprint, boolean alreadyApplied) {}
}
