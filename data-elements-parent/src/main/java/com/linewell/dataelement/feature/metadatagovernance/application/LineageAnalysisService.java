package com.linewell.dataelement.feature.metadatagovernance.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.DslHasher;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant-scoped, read-only traversal of declared processing dependencies. */
@Service
public class LineageAnalysisService {
    private static final int MAX_NODES = 180;
    private static final int MAX_EDGES = 360;
    private final JdbcTemplate jdbc;
    private final PipelineRepository pipelines;
    private final ObjectMapper json;

    public LineageAnalysisService(JdbcTemplate jdbc, PipelineRepository pipelines, ObjectMapper json) {
        this.jdbc = jdbc;
        this.pipelines = pipelines;
        this.json = json;
    }

    public Map<String, Object> objects(Map<String, Object> request) {
        String tenant = TenantContext.requireTenantId();
        String keyword = value(request.get("keyword")).trim().toLowerCase(Locale.ROOT);
        if (keyword.length() > 100) throw new IllegalArgumentException("关键词最多 100 字");
        String kind = value(request.get("kind")).toUpperCase(Locale.ROOT);
        String sourceId = value(request.get("datasourceId"));
        boolean linkedOnly = Boolean.TRUE.equals(request.get("linkedOnly"));
        int page = bounded(request.get("pageNo"), 1, 1, 100000);
        int size = bounded(request.get("pageSize"), 20, 1, 50);
        Registry registry = registry(tenant);
        Set<String> linked = new HashSet<>();
        if (linkedOnly) {
            for (Arc arc : declared(tenant, registry)) { linked.add(arc.source); linked.add(arc.target); }
            for (Map<String, Object> candidate : registry.nodes.values())
                if ("MODEL".equals(candidate.get("kind")) && candidate.get("tableIds") instanceof List<?> bound
                        && bound.stream().anyMatch(tableId -> linked.contains("TABLE:" + value(tableId))))
                    linked.add(value(candidate.get("key")));
        }
        List<Map<String, Object>> found = new ArrayList<>();
        for (Map<String, Object> node : registry.nodes.values()) {
            if (!kind.isBlank() && !kind.equals(node.get("kind"))) continue;
            if (linkedOnly && !linked.contains(value(node.get("key")))) continue;
            if (!linkedOnly && kind.isBlank() && keyword.isBlank() && "FIELD".equals(node.get("kind"))) continue;
            if (!sourceId.isBlank() && !sourceId.equals(value(node.get("datasourceId")))) continue;
            if (!keyword.isBlank() && !(value(node.get("name")) + " " + value(node.get("code")) + " " + value(node.get("tableName")))
                    .toLowerCase(Locale.ROOT).contains(keyword)) continue;
            found.add(node);
        }
        found.sort(Comparator.comparingInt((Map<String, Object> n) -> "TABLE".equals(n.get("kind")) ? 0 : "MODEL".equals(n.get("kind")) ? 1 : 2)
                .thenComparing(n -> value(n.get("name")), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(n -> value(n.get("kind"))).thenComparing(n -> value(n.get("id"))));
        int from = (int) Math.min(found.size(), (long) (page - 1) * size);
        return Map.of("items", found.subList(from, Math.min(found.size(), from + size)), "total", found.size(),
                "pageNo", page, "pageSize", size);
    }

    public Map<String, Object> analyze(Map<String, Object> request) {
        String tenant = TenantContext.requireTenantId();
        String kind = value(request.get("kind")).toUpperCase(Locale.ROOT);
        String id = value(request.get("id"));
        String mode = value(request.get("mode")).toUpperCase(Locale.ROOT);
        String scope = value(request.get("scope")).toUpperCase(Locale.ROOT);
        if (!Set.of("TABLE", "FIELD", "MODEL").contains(kind) || id.isBlank())
            throw new IllegalArgumentException("请选择已登记的数据表、字段或模型");
        if (!Set.of("FULL", "ATTRIBUTION", "IMPACT").contains(mode)) throw new IllegalArgumentException("分析模式无效");
        if (!Set.of("DESIGN", "DEPLOYED").contains(scope)) throw new IllegalArgumentException("链路依据无效");
        int depth = bounded(request.get("depth"), 3, 1, 8);
        Registry registry = registry(tenant);
        String root = kind + ":" + id;
        Map<String, Object> selected = registry.nodes.get(root);
        if (selected == null) throw new IllegalArgumentException("对象不存在或不属于当前租户");
        List<String> anchors = new ArrayList<>();
        boolean anchorTruncated = false;
        if ("MODEL".equals(kind)) {
            if (selected.get("tableIds") instanceof List<?> bound)
                for (Object tableId : bound) if (registry.nodes.containsKey("TABLE:" + value(tableId))) {
                    if (anchors.size() >= MAX_NODES) { anchorTruncated = true; break; }
                    anchors.add("TABLE:" + value(tableId));
                }
            if (anchors.isEmpty()) return emptyAnalysis(selected, mode, scope, "模型尚未关联已登记物理表，暂无可追溯加工链路");
        } else anchors.add(root);
        List<Arc> arcs = "DEPLOYED".equals(scope) ? deployed(tenant) : declared(tenant, registry);
        Map<String, List<Arc>> outgoing = new HashMap<>(), incoming = new HashMap<>();
        for (Arc arc : arcs) {
            if (!registry.nodes.containsKey(arc.source) || !registry.nodes.containsKey(arc.target)) continue;
            outgoing.computeIfAbsent(arc.source, ignored -> new ArrayList<>()).add(arc);
            incoming.computeIfAbsent(arc.target, ignored -> new ArrayList<>()).add(arc);
        }
        LinkedHashSet<String> visible = new LinkedHashSet<>();
        LinkedHashMap<String, Arc> links = new LinkedHashMap<>();
        Map<String, Integer> distances = new HashMap<>();
        for (String anchor : anchors) { visible.add(anchor); distances.put(anchor, 0); }
        boolean more = anchorTruncated;
        for (String anchor : anchors) {
            if (!"IMPACT".equals(mode)) more |= traverse(anchor, depth, -1, incoming, visible, links, distances);
            if (!"ATTRIBUTION".equals(mode)) more |= traverse(anchor, depth, 1, outgoing, visible, links, distances);
        }
        List<Map<String, Object>> nodes = visible.stream().map(key -> {
            Map<String, Object> item = new LinkedHashMap<>(registry.nodes.get(key));
            item.put("distance", distances.getOrDefault(key, 0));
            return item;
        }).toList();
        List<Map<String, Object>> edges = links.values().stream().map(Arc::asMap).toList();
        String note = "DEPLOYED".equals(scope) && links.isEmpty()
                ? "当前方向没有可验证的部署依赖；无法校验旧版本的流程需要重新部署后才会归档血缘" : "";
        return Map.of("root", selected, "nodes", nodes, "edges", edges, "mode", mode,
                "scope", scope, "depth", depth, "hasMore", more, "note", note,
                "coverage", Map.of("knownEdges", arcs.size(), "visibleNodes", nodes.size(), "visibleEdges", edges.size()));
    }

    public Map<String, Object> evidence(Map<String, Object> request) {
        String tenant = TenantContext.requireTenantId();
        String edgeId = value(request.get("edgeId"));
        if (edgeId.isBlank()) throw new IllegalArgumentException("缺少血缘关系标识");
        Registry registry = registry(tenant);
        String scope = value(request.get("scope"));
        List<Arc> arcs = "DEPLOYED".equalsIgnoreCase(scope) ? deployed(tenant) : declared(tenant, registry);
        return arcs.stream().filter(arc -> arc.id.equals(edgeId)).findFirst().map(arc -> {
            Map<String, Object> result = new LinkedHashMap<>(arc.asMap());
            result.put("sourceObject", registry.nodes.get(arc.source));
            result.put("targetObject", registry.nodes.get(arc.target));
            if (!arc.pipelineId.isBlank()) pipelines.findById(arc.pipelineId).ifPresent(pipeline -> {
                result.put("pipelineName", pipeline.name());
                result.put("pipelineStatus", pipeline.status() == null ? "" : pipeline.status().name());
            });
            if ("DEPLOYED".equalsIgnoreCase(scope) && !arc.pipelineId.isBlank()) {
                List<Long> times = jdbc.query("SELECT deployed_at FROM dwm_lineage_snapshot_t WHERE tenant_id=? AND pipeline_id=? AND snapshot_kind='DEPLOYED' AND is_current=1",
                        (rs, ignored) -> rs.getLong(1), tenant, arc.pipelineId);
                if (!times.isEmpty()) result.put("deployedAt", times.get(0));
            }
            result.put("explanation", "DEPLOYED".equalsIgnoreCase(scope)
                    ? "此关系来自成功部署时归档的依赖版本；运行事件仍需单独查询"
                    : "此关系来自已保存的接入任务或字段规则，不代表已实际运行");
            return result;
        }).orElseThrow(() -> new IllegalArgumentException("关系不存在或不属于当前租户"));
    }

    public Map<String, Object> coverage() {
        String tenant = TenantContext.requireTenantId();
        Registry registry = registry(tenant);
        List<Arc> design = declared(tenant, registry), actual = deployed(tenant);
        long tableEdges = design.stream().filter(arc -> arc.source.startsWith("TABLE:")).count();
        long fieldEdges = design.stream().filter(arc -> arc.source.startsWith("FIELD:")).count();
        List<Pipeline> allPipelines = pipelines.findAll();
        long deployedPipelines = allPipelines.stream().filter(pipeline -> pipeline.nifiProcessGroupId() != null
                && !pipeline.nifiProcessGroupId().isBlank() && pipeline.lastDeployedAt() != null).count();
        Integer indexedPipelines = jdbc.queryForObject("SELECT COUNT(*) FROM dwm_lineage_snapshot_t s JOIN nifi_pipeline_t p ON p.id=s.pipeline_id AND p.tenant_id=s.tenant_id WHERE s.tenant_id=? AND s.snapshot_kind='DEPLOYED' AND s.is_current=1 AND p.is_del=0 AND p.nifi_process_group_id IS NOT NULL AND p.nifi_process_group_id<>'' AND p.last_deployed_at=s.deployed_at", Integer.class, tenant);
        List<Map<String, Object>> datasources = registry.sources.values().stream()
                .map(source -> Map.<String, Object>of("id", value(source.get("tid")), "name", value(source.get("db_name")),
                        "type", value(source.get("db_type"))))
                .sorted(Comparator.comparing(source -> value(source.get("name")), String.CASE_INSENSITIVE_ORDER)).toList();
        return Map.of("pipelines", allPipelines.size(), "tables", registry.tables.size(),
                "fields", registry.fieldKeys.size(), "designTableEdges", tableEdges,
                "designFieldEdges", fieldEdges, "deployedEdges", actual.size(), "datasources", datasources,
                "deployedPipelines", deployedPipelines, "indexedPipelines", indexedPipelines == null ? 0 : indexedPipelines);
    }

    /** Capture the effective declared dependency after a successful deployment. */
    @Transactional
    public void captureDeployment(Pipeline pipeline) {
        if (pipeline == null || pipeline.dsl() == null || pipeline.lastDeployedHash() == null || pipeline.lastDeployedAt() == null) return;
        String tenant = TenantContext.requireTenantId();
        captureDeployment(pipeline, registry(tenant), tenant);
    }

    /** Recover only legacy deployments whose saved DSL still exactly matches the deployed fingerprint. */
    @Transactional
    public Map<String, Integer> reconcileCurrentDeployments() {
        String tenant = TenantContext.requireTenantId();
        Registry registry = registry(tenant);
        Map<String, Long> indexed = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT pipeline_id,deployed_at FROM dwm_lineage_snapshot_t WHERE tenant_id=? AND snapshot_kind='DEPLOYED' AND is_current=1", tenant))
            indexed.put(value(row.get("pipeline_id")), row.get("deployed_at") instanceof Number time ? time.longValue() : 0L);
        int captured = 0, unverifiable = 0;
        for (Pipeline pipeline : pipelines.findAll()) {
            if (pipeline.dsl() == null || pipeline.nifiProcessGroupId() == null || pipeline.nifiProcessGroupId().isBlank()
                    || pipeline.lastDeployedAt() == null || pipeline.lastDeployedHash() == null) continue;
            if (pipeline.lastDeployedAt().equals(indexed.get(pipeline.id()))) continue;
            String current = DslHasher.deploymentHash(pipeline.dsl(), DslHasher.CURRENT_COMPILER_REVISION);
            if (!pipeline.lastDeployedHash().equals(current)) { unverifiable++; continue; }
            captureDeployment(pipeline, registry, tenant);
            captured++;
        }
        return Map.of("captured", captured, "unverifiable", unverifiable);
    }

    private void captureDeployment(Pipeline pipeline, Registry registry, String tenant) {
        List<Arc> edges = pipelineArcs(pipeline, registry);
        String snapshot = UUID.nameUUIDFromBytes((tenant + ":DEPLOYED:" + pipeline.id() + ":" + pipeline.lastDeployedAt())
                .getBytes(StandardCharsets.UTF_8)).toString();
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM dwm_lineage_snapshot_t WHERE tenant_id=? AND tid=?", Integer.class, tenant, snapshot);
        if (existing != null && existing > 0) return;
        jdbc.update("UPDATE dwm_lineage_snapshot_t SET is_current=0 WHERE tenant_id=? AND pipeline_id=? AND snapshot_kind='DEPLOYED' AND is_current=1", tenant, pipeline.id());
        jdbc.update("INSERT INTO dwm_lineage_snapshot_t(tid,tenant_id,pipeline_id,snapshot_kind,source_hash,captured_at,deployed_at,edge_count,unresolved_count,is_current) VALUES(?,?,?,?,?,?,?,?,?,1)",
                snapshot, tenant, pipeline.id(), "DEPLOYED", pipeline.lastDeployedHash(), LocalDateTime.now(), pipeline.lastDeployedAt(), edges.size(), 0);
        for (Arc edge : edges) jdbc.update("INSERT INTO dwm_lineage_edge_t(tid,tenant_id,snapshot_id,pipeline_id,source_kind,source_id,target_kind,target_id,evidence_kind,evidence_ref_id,rule_summary,created_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), tenant, snapshot, pipeline.id(), edge.sourceKind(), edge.sourceId(), edge.targetKind(), edge.targetId(),
                edge.evidenceKind, edge.evidenceRef, edge.ruleSummary, LocalDateTime.now());
    }

    @Transactional
    public void retireDeployment(String pipelineId) {
        jdbc.update("UPDATE dwm_lineage_snapshot_t SET is_current=0 WHERE tenant_id=? AND pipeline_id=? AND snapshot_kind='DEPLOYED' AND is_current=1",
                TenantContext.requireTenantId(), pipelineId);
    }

    private List<Arc> deployed(String tenant) {
        return jdbc.query("SELECT e.* FROM dwm_lineage_edge_t e JOIN dwm_lineage_snapshot_t s ON s.tid=e.snapshot_id AND s.tenant_id=e.tenant_id JOIN nifi_pipeline_t p ON p.id=s.pipeline_id AND p.tenant_id=e.tenant_id WHERE e.tenant_id=? AND s.snapshot_kind='DEPLOYED' AND s.is_current=1 AND p.is_del=0 AND p.nifi_process_group_id IS NOT NULL AND p.nifi_process_group_id<>'' AND p.last_deployed_at=s.deployed_at",
                (rs, ignored) -> arc(rs.getString("source_kind") + ":" + rs.getString("source_id"),
                        rs.getString("target_kind") + ":" + rs.getString("target_id"), rs.getString("pipeline_id"),
                        rs.getString("evidence_kind"), rs.getString("evidence_ref_id"), rs.getString("rule_summary")), tenant);
    }

    private List<Arc> declared(String tenant, Registry registry) {
        LinkedHashMap<String, Arc> arcs = new LinkedHashMap<>();
        for (Pipeline pipeline : pipelines.findAll()) for (Arc arc : pipelineArcs(pipeline, registry)) arcs.putIfAbsent(arc.dedupe(), arc);
        List<Map<String, Object>> tasks = jdbc.queryForList("SELECT tid,pipeline_id,source_table_id,target_table_id FROM data_access_agg_task_t WHERE tenant_id=? AND is_del=0", tenant);
        Map<String, Map<String, Object>> byTask = new HashMap<>();
        for (Map<String, Object> task : tasks) {
            byTask.put(value(task.get("tid")), task);
            String source = "TABLE:" + value(task.get("source_table_id")), target = "TABLE:" + value(task.get("target_table_id"));
            if (registry.nodes.containsKey(source) && registry.nodes.containsKey(target) && !source.equals(target)) {
                Arc arc = arc(source, target, value(task.get("pipeline_id")), "REGISTERED_TASK", value(task.get("tid")), "登记任务中的明确源表与目标表");
                arcs.putIfAbsent(arc.dedupe(), arc);
            }
        }
        for (Map<String, Object> rule : jdbc.queryForList("SELECT tid,task_id,source_field,target_field,func_enable,dict_enable FROM data_access_field_mapping WHERE tenant_id=? AND is_del=0", tenant)) {
            Map<String, Object> task = byTask.get(value(rule.get("task_id")));
            if (task == null) continue;
            String source = registry.fieldKeys.get(fieldKey(value(task.get("source_table_id")), value(rule.get("source_field"))));
            String target = registry.fieldKeys.get(fieldKey(value(task.get("target_table_id")), value(rule.get("target_field"))));
            if (source == null || target == null) continue;
            String label = number(rule.get("dict_enable")) != 0 ? "登记字段映射 · 字典查询"
                    : number(rule.get("func_enable")) != 0 ? "登记字段映射 · 转换函数" : "登记字段映射 · 直接映射";
            Arc arc = arc(source, target, value(task.get("pipeline_id")), "REGISTERED_FIELD_RULE", value(rule.get("tid")), label);
            arcs.putIfAbsent(arc.dedupe(), arc);
        }
        return new ArrayList<>(arcs.values());
    }

    private List<Arc> pipelineArcs(Pipeline pipeline, Registry registry) {
        if (pipeline.dsl() == null || pipeline.dsl().nodes() == null || pipeline.dsl().edges() == null) return List.of();
        LinkedHashMap<String, Arc> result = new LinkedHashMap<>();
        Map<String, Pipeline.Node> nodes = new HashMap<>();
        Map<String, List<String>> next = new HashMap<>();
        for (Pipeline.Node node : pipeline.dsl().nodes()) nodes.put(node.id(), node);
        for (Pipeline.Edge edge : pipeline.dsl().edges()) next.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge.target());
        for (Pipeline.Node source : nodes.values()) {
            if (!"source".equalsIgnoreCase(source.category())) continue;
            String sourceId = tableId(source.config(), true);
            if (!registry.tables.containsKey(sourceId)) continue;
            ArrayDeque<String> queue = new ArrayDeque<>(next.getOrDefault(source.id(), List.of()));
            Set<String> visited = new HashSet<>();
            while (!queue.isEmpty()) {
                String nodeId = queue.removeFirst();
                if (!visited.add(nodeId)) continue;
                Pipeline.Node node = nodes.get(nodeId);
                if (node == null) continue;
                if ("sink".equalsIgnoreCase(node.category())) {
                    String targetId = tableId(node.config(), false);
                    if (registry.tables.containsKey(targetId) && !sourceId.equals(targetId)) {
                        Arc arc = arc("TABLE:" + sourceId, "TABLE:" + targetId, pipeline.id(), "PIPELINE_PATH", node.id(), "加工图中已连接的来源与目标");
                        result.putIfAbsent(arc.dedupe(), arc);
                    }
                }
                queue.addAll(next.getOrDefault(nodeId, List.of()));
            }
        }
        for (Pipeline.Node node : nodes.values()) {
            if (node.config() == null) continue;
            String sourceId = value(node.config().get("sourceTableId")), targetId = value(node.config().get("targetTableId"));
            if (!registry.tables.containsKey(sourceId) || !registry.tables.containsKey(targetId)) continue;
            Object raw = node.config().get("mappings");
            Map<String, Object> spec = mapping(raw);
            Object mappings = spec.get("mappings");
            if (!(mappings instanceof List<?> rules)) continue;
            for (int index = 0; index < rules.size(); index++) {
                if (!(rules.get(index) instanceof Map<?, ?> rule)) continue;
                String target = registry.fieldKeys.get(fieldKey(targetId, value(rule.get("to"))));
                if (target == null) continue;
                List<String> sources = new ArrayList<>();
                if (rule.get("from") != null) sources.add(value(rule.get("from")));
                if (rule.get("fromList") instanceof List<?> list) for (Object item : list) sources.add(value(item));
                for (String from : sources) {
                    String source = registry.fieldKeys.get(fieldKey(sourceId, from));
                    if (source == null) continue;
                    Arc arc = arc(source, target, pipeline.id(), "EXPLICIT_FIELD_RULE", node.id() + ":" + index, "加工图显式字段映射");
                    result.putIfAbsent(arc.dedupe(), arc);
                }
            }
        }
        return new ArrayList<>(result.values());
    }

    private Registry registry(String tenant) {
        Registry result = new Registry();
        for (Map<String, Object> source : jdbc.queryForList("SELECT tid,db_name,COALESCE(NULLIF(db_type,''),database_type,'') AS db_type FROM db_datasource_t WHERE tenant_id=? AND is_del=0", tenant))
            result.sources.put(value(source.get("tid")), source);
        boolean hasModelBinding = hasColumn("db_table_t", "resource_model_id");
        boolean hasDomainBinding = hasColumn("db_table_t", "resource_domain_id");
        boolean hasBusinessType = hasColumn("db_table_t", "business_type");
        String tableSql = "SELECT tid,datasource_id,table_name,table_name_cn,table_comment,table_type,field_count,updated_time"
                + (hasModelBinding ? ",resource_model_id" : "") + (hasDomainBinding ? ",resource_domain_id" : "")
                + (hasBusinessType ? ",business_type" : "")
                + " FROM db_table_t WHERE tenant_id=? AND is_del=0";
        for (Map<String, Object> table : jdbc.queryForList(tableSql, tenant)) {
            String id = value(table.get("tid")), datasourceId = value(table.get("datasource_id"));
            if (!result.sources.containsKey(datasourceId)) continue;
            result.tables.put(id, table);
            Map<String, Object> source = result.sources.get(datasourceId);
            Map<String, Object> entry = node("TABLE", id, first(table, "table_name_cn", "table_comment", "table_name"),
                    value(table.get("table_name")), datasourceId, value(source.get("db_name")), value(source.get("db_type")),
                    value(table.get("resource_domain_id")), value(table.get("updated_time")), "", value(table.get("table_name")));
            entry.put("domainName", value(table.get("business_type")));
            entry.put("objectType", value(table.get("table_type")));
            result.nodes.put("TABLE:" + id, entry);
        }
        for (Map<String, Object> field : jdbc.queryForList("SELECT tid,table_id,column_name,column_comment,data_type,column_type,length,nullable,primary_key,updated_time FROM db_table_column_t WHERE tenant_id=? AND is_del=0", tenant)) {
            String tableId = value(field.get("table_id"));
            Map<String, Object> table = result.tables.get(tableId);
            if (table == null) continue;
            String id = value(field.get("tid"));
            Map<String, Object> owner = result.nodes.get("TABLE:" + tableId);
            Map<String, Object> entry = node("FIELD", id, first(field, "column_comment", "column_name"), value(field.get("column_name")),
                    value(table.get("datasource_id")), value(owner.get("datasourceName")), value(owner.get("datasourceType")),
                    value(owner.get("domainId")), value(field.get("updated_time")), tableId, value(owner.get("name")));
            entry.put("domainName", value(owner.get("domainName")));
            entry.put("dataType", first(field, "column_type", "data_type"));
            entry.put("length", field.get("length"));
            entry.put("nullable", field.get("nullable"));
            entry.put("primaryKey", field.get("primary_key"));
            result.nodes.put("FIELD:" + id, entry);
            result.fieldKeys.put(fieldKey(tableId, value(field.get("column_name"))), "FIELD:" + id);
        }
        if (hasTable("res_logical_model")) for (Map<String, Object> model : jdbc.queryForList("SELECT tid,model_name,model_code,model_type,domain_name,target_datasource_id,updated_time FROM res_logical_model WHERE tenant_id=? AND is_del=0", tenant)) {
            String id = value(model.get("tid"));
            String datasourceId = value(model.get("target_datasource_id"));
            Map<String, Object> datasource = result.sources.get(datasourceId);
            Map<String, Object> entry = node("MODEL", id, value(model.get("model_name")), value(model.get("model_code")),
                    datasourceId, datasource == null ? "" : value(datasource.get("db_name")),
                    datasource == null ? "" : value(datasource.get("db_type")), value(model.get("domain_name")),
                    value(model.get("updated_time")), "", "");
            entry.put("domainName", value(model.get("domain_name")));
            entry.put("objectType", value(model.get("model_type")));
            List<String> bound = result.tables.values().stream().filter(t -> id.equals(value(t.get("resource_model_id"))))
                    .map(t -> value(t.get("tid"))).toList();
            if (bound.size() == 1) entry.put("tableId", bound.get(0));
            entry.put("tableIds", bound);
            entry.put("boundTableCount", bound.size());
            result.nodes.put("MODEL:" + id, entry);
        }
        return result;
    }

    private boolean hasTable(String name) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?", Integer.class, name);
        return count != null && count > 0;
    }

    private boolean hasColumn(String table, String name) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?", Integer.class, table, name);
        return count != null && count > 0;
    }

    private static boolean traverse(String root, int depth, int direction, Map<String, List<Arc>> adjacency,
                                    LinkedHashSet<String> visible, LinkedHashMap<String, Arc> links, Map<String, Integer> distances) {
        ArrayDeque<String> queue = new ArrayDeque<>();
        Map<String, Integer> hops = new HashMap<>();
        queue.add(root); hops.put(root, 0);
        boolean more = false;
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            int step = hops.get(current);
            List<Arc> candidates = adjacency.getOrDefault(current, List.of());
            if (step >= depth) { if (candidates.stream().anyMatch(arc -> !links.containsKey(arc.id))) more = true; continue; }
            for (Arc arc : candidates) {
                String other = direction > 0 ? arc.target : arc.source;
                if ((!visible.contains(other) && visible.size() >= MAX_NODES) || (!links.containsKey(arc.id) && links.size() >= MAX_EDGES)) {
                    more = true; continue;
                }
                visible.add(other); links.put(arc.id, arc);
                distances.putIfAbsent(other, direction * (step + 1));
                if (hops.putIfAbsent(other, step + 1) == null) queue.add(other);
            }
        }
        return more;
    }

    private static Map<String, Object> emptyAnalysis(Map<String, Object> root, String mode, String scope, String note) {
        Map<String, Object> focus = new LinkedHashMap<>(root);
        focus.put("distance", 0);
        return Map.of("root", root, "nodes", List.of(focus), "edges", List.of(), "mode", mode,
                "scope", scope, "depth", 0, "hasMore", false, "note", note,
                "coverage", Map.of("knownEdges", 0, "visibleNodes", 1, "visibleEdges", 0));
    }

    private Map<String, Object> mapping(Object raw) {
        try {
            if (raw instanceof Map<?, ?> map) return json.convertValue(map, new TypeReference<>() {});
            if (raw instanceof String text && !text.isBlank()) return json.readValue(text, new TypeReference<>() {});
        } catch (Exception ignored) { /* Invalid legacy rules are not treated as evidence. */ }
        return Map.of();
    }

    private static Arc arc(String source, String target, String pipelineId, String evidenceKind, String evidenceRef, String summary) {
        String id = UUID.nameUUIDFromBytes((source + "|" + target + "|" + pipelineId + "|" + evidenceKind + "|" + evidenceRef)
                .getBytes(StandardCharsets.UTF_8)).toString();
        return new Arc(id, source, target, pipelineId, evidenceKind, evidenceRef, summary);
    }

    private record Arc(String id, String source, String target, String pipelineId, String evidenceKind,
                       String evidenceRef, String ruleSummary) {
        String sourceKind() { return source.substring(0, source.indexOf(':')); }
        String sourceId() { return source.substring(source.indexOf(':') + 1); }
        String targetKind() { return target.substring(0, target.indexOf(':')); }
        String targetId() { return target.substring(target.indexOf(':') + 1); }
        String dedupe() { return source + "|" + target + "|" + pipelineId; }
        Map<String, Object> asMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id); result.put("source", source); result.put("target", target);
            result.put("pipelineId", pipelineId); result.put("evidenceKind", evidenceKind);
            result.put("evidenceRefId", evidenceRef); result.put("ruleSummary", ruleSummary);
            return result;
        }
    }

    private static final class Registry {
        final Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> sources = new HashMap<>();
        final Map<String, Map<String, Object>> tables = new HashMap<>();
        final Map<String, String> fieldKeys = new HashMap<>();
    }

    private static Map<String, Object> node(String kind, String id, String name, String code, String sourceId,
                                            String sourceName, String sourceType, String domainId, String updatedAt,
                                            String tableId, String tableName) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("kind", kind); value.put("id", id); value.put("key", kind + ":" + id);
        value.put("name", name); value.put("code", code); value.put("datasourceId", sourceId);
        value.put("datasourceName", sourceName); value.put("datasourceType", sourceType);
        value.put("domainId", domainId); value.put("updatedAt", updatedAt);
        value.put("tableId", tableId); value.put("tableName", tableName);
        return value;
    }

    private static String tableId(Map<String, Object> config, boolean source) {
        if (config == null) return "";
        String canonical = source ? value(config.get("sourceTableId")) : value(config.get("targetTableId"));
        return canonical.isBlank() ? value(config.get("tableId")) : canonical;
    }

    private static String fieldKey(String tableId, String columnName) {
        String name = columnName.trim().replaceFirst("^/", "");
        return tableId + ":" + name.toLowerCase(Locale.ROOT);
    }

    private static String first(Map<String, Object> row, String... keys) {
        for (String key : keys) if (!value(row.get(key)).isBlank()) return value(row.get(key));
        return "";
    }

    private static String value(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static int number(Object value) { try { return Integer.parseInt(value(value)); } catch (Exception ignored) { return 0; } }
    private static int bounded(Object value, int fallback, int low, int high) {
        int result = value == null ? fallback : number(value);
        if (result < low || result > high) throw new IllegalArgumentException("查询范围超出允许值");
        return result;
    }
}
