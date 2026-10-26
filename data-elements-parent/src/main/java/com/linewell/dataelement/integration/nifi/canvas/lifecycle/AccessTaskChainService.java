package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.DslHasher;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineResourceReferences;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Read-only, bounded view of independently stored pipelines belonging to one materialization chain. */
@Service
public class AccessTaskChainService {
    static final int MAX_TASKS = 100;
    static final int MAX_DEPTH = 16;
    private static final String RESOURCE = "MENU:2081000000000000002";
    private final JdbcTemplate jdbc;
    private final DataScopeAuthorizationService scopes;
    private final ObjectMapper json;
    private final NifiClient nifi;

    public AccessTaskChainService(JdbcTemplate jdbc, DataScopeAuthorizationService scopes,
                                 ObjectMapper json, NifiClient nifi) {
        this.jdbc = jdbc;
        this.scopes = scopes;
        this.json = json;
        this.nifi = nifi;
    }

    public Map<String, Object> byTask(String taskId) { return load(required(taskId), null); }
    public Map<String, Object> byPipeline(String pipelineId) { return load(null, required(pipelineId)); }

    private Map<String, Object> load(String taskId, String pipelineId) {
        try {
            return loadChain(taskId, pipelineId);
        } catch (ResponseStatusException conflict) {
            // A broken related chain must not hide an authorized, independently saved design.
            // Authentication/authorization and malformed or oversized selected DSL still fail closed.
            if (conflict.getStatusCode() != HttpStatus.CONFLICT) throw conflict;
            return savedTask(taskId, pipelineId, conflict.getReason());
        }
    }

    private Map<String, Object> savedTask(String taskId, String pipelineId, String reason) {
        String tenant = TenantContext.requireTenantId();
        if (TenantContext.usesControlDatabase()) throw failure(HttpStatus.FORBIDDEN, "流程必须在当前租户数据库中读取");
        List<Object> args = List.of(tenant, tenant, tenant, taskId == null ? pipelineId : taskId);
        List<Map<String, Object>> rows = query("""
                select a.tid as task_id, a.task_name, a.pipeline_id, a.source_db_id, a.target_db_id,
                       a.source_table_id, a.target_table_id,
                       s.table_name as source_table_name, s.datasource_id as actual_source_db_id,
                       t.table_name as target_table_name, t.datasource_id as actual_target_db_id
                  from data_access_agg_task_t a
                  left join db_table_t s on s.tid=a.source_table_id and s.tenant_id=? and s.is_del=0
                  left join db_table_t t on t.tid=a.target_table_id and t.tenant_id=? and t.is_del=0
                 where a.tenant_id=? and a.is_del=0 and a.
                """ + (taskId == null ? "pipeline_id" : "tid") + "=?", args, 2);
        if (rows.size() != 1) throw failure(HttpStatus.NOT_FOUND, "接入任务不存在或关联不唯一");
        Map<String, Object> task = rows.getFirst();
        String id = required(text(task, "pipeline_id"));
        Pipeline pipeline = pipelines(tenant, Set.of(id)).get(id);
        var references = PipelineResourceReferences.from(pipeline);
        PipelineResourceReferences.addTask(pipeline, task, references);
        Set<String> datasourceIds = references.datasourceIds();
        Set<String> tableIds = references.tableIds();
        for (String key : List.of("actual_source_db_id", "actual_target_db_id")) {
            String value = text(task, key); if (!value.isBlank()) datasourceIds.add(value);
        }
        boolean standalone = PipelineResourceReferences.standalone(pipeline, task);
        if (tableIds.size() > 200) throw failure(HttpStatus.CONFLICT, "任务引用表超过200张");
        if (!tableIds.isEmpty()) {
            List<Object> tableArgs = new ArrayList<>(tableIds); tableArgs.add(tenant);
            // A same-tenant soft-deleted registration can authorize historical design inspection.
            // Never resolve an unknown or foreign ID, and authorize every actual table's datasource.
            List<Map<String, Object>> tables = query("select tid, datasource_id from db_table_t where tid in ("
                    + placeholders(tableIds.size()) + ") and tenant_id=?", tableArgs, 201);
            Set<String> resolved = new LinkedHashSet<>();
            for (Map<String, Object> table : tables) {
                resolved.add(text(table, "tid")); datasourceIds.add(text(table, "datasource_id"));
            }
            if (!resolved.equals(tableIds)) throw failure(HttpStatus.FORBIDDEN, "任务引用的数据表不存在或无访问权限");
        }
        Map<String, Map<String, Object>> sources = datasourceIds.isEmpty()
                ? Map.of() : authorizedSources(tenant, datasourceIds);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("taskId", text(task, "task_id")); item.put("taskName", text(task, "task_name"));
        item.put("sourceTableId", text(task, "source_table_id")); item.put("targetTableId", text(task, "target_table_id"));
        item.put("sourceDbId", text(task, "source_db_id")); item.put("targetDbId", text(task, "target_db_id"));
        item.put("sourceDbName", text(sources.get(text(task, "source_db_id")), "db_name"));
        item.put("targetDbName", text(sources.get(text(task, "target_db_id")), "db_name"));
        item.put("sourceTableName", text(task, "source_table_name")); item.put("targetTableName", text(task, "target_table_name"));
        item.put("pipelineId", id); item.put("pipeline", pipeline);
        if (standalone && nativeReady(pipeline)) {
            item.put("processGroupId", pipeline.nifiProcessGroupId());
            item.put("nativeUrl", "/nifi-ui/#/process-groups/" + pipeline.nifiProcessGroupId());
        } else if (!standalone) item.put("unavailableReason", "关联链路暂不可用，可继续编辑当前任务保存的画布");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rootSourceTableId", text(task, "source_table_id")); result.put("selectedTaskId", text(task, "task_id"));
        result.put("selectedPipelineId", id); result.put("items", List.of(item)); result.put("links", List.of());
        result.put("standalone", standalone);
        result.put("readOnly", false);
        result.put("relationshipUnavailable", !standalone);
        result.put("warnings", standalone ? List.of() : List.of(reason == null ? "关联链路暂不可用" : reason));
        result.put("nativeCanvas", standalone ? nativeSelection(List.of(item), "可打开当前独立流程的原生画布")
                : Map.of("mode", "NONE", "reason", "请先核对任务关联后重新打开"));
        return result;
    }

    private Map<String, Object> loadChain(String taskId, String pipelineId) {
        String tenant = TenantContext.requireTenantId();
        if (TenantContext.usesControlDatabase()) throw failure(HttpStatus.FORBIDDEN, "流程必须在当前租户数据库中读取");
        List<Object> anchorArgs = new ArrayList<>();
        StringBuilder anchor = new StringBuilder("select a.tid as task_id, a.pipeline_id");
        for (int i = 0; i <= MAX_DEPTH; i++) {
            anchor.append(", s").append(i).append(".tid as ancestor_").append(i)
                    .append(", s").append(i).append(".datasource_id as datasource_").append(i)
                    .append(", s").append(i).append(".source_table_id as parent_").append(i);
        }
        anchor.append(" from data_access_agg_task_t a ").append(ancestors(anchorArgs, tenant));
        anchor.append(" where a.tenant_id = ? and a.is_del = 0 and a.")
                .append(taskId == null ? "pipeline_id" : "tid").append(" = ?");
        anchorArgs.add(tenant); anchorArgs.add(taskId == null ? pipelineId : taskId);
        List<Map<String, Object>> anchors = query(anchor.toString(), anchorArgs, 2);
        if (anchors.size() != 1) throw failure(HttpStatus.NOT_FOUND, "接入任务不存在或关联不唯一");
        Map<String, Object> selected = anchors.getFirst();
        Set<String> datasourceIds = new LinkedHashSet<>();
        Set<String> ancestry = new LinkedHashSet<>();
        String root = "";
        for (int i = 0; i <= MAX_DEPTH; i++) {
            String id = text(selected, "ancestor_" + i);
            if (id.isBlank()) {
                if (i == 0 || !text(selected, "parent_" + (i - 1)).isBlank())
                    throw failure(HttpStatus.CONFLICT, "来源表关联不完整，请检查登记记录");
                break;
            }
            if (!ancestry.add(id)) throw failure(HttpStatus.CONFLICT, "来源表存在循环关联");
            datasourceIds.add(text(selected, "datasource_" + i));
            root = id;
            // Registered business sources historically store their own ID as source_table_id.
            // This is a root marker, whereas A -> B -> A remains an invalid multi-table cycle.
            if (id.equals(text(selected, "parent_" + i))) break;
            if (i == MAX_DEPTH && !text(selected, "parent_" + i).isBlank())
                throw failure(HttpStatus.CONFLICT, "链路超过16级，请分段管理");
        }

        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                select a.tid as task_id, a.task_name, a.pipeline_id, a.source_db_id, a.target_db_id,
                       a.source_table_id, a.target_table_id, a.task_status,
                       s0.table_name as source_table_name, s0.table_name_cn as source_table_name_cn,
                       s0.datasource_id as actual_source_db_id,
                       target.table_name as target_table_name, target.table_name_cn as target_table_name_cn,
                       target.datasource_id as actual_target_db_id
                """);
        for (int i = 1; i <= MAX_DEPTH; i++) sql.append(", s").append(i).append(".datasource_id as ancestor_datasource_").append(i);
        sql.append("""
                  from data_access_agg_task_t a
                """);
        sql.append(ancestors(args, tenant));
        sql.append(" left join db_table_t target on target.tid = a.target_table_id and target.tenant_id = ? and target.is_del = 0");
        args.add(tenant);
        sql.append(" where a.tenant_id = ? and a.is_del = 0 and ("); args.add(tenant);
        for (int i = 0; i <= MAX_DEPTH; i++) {
            if (i > 0) sql.append(" or ");
            sql.append("s").append(i).append(".tid = ?"); args.add(root);
        }
        sql.append(") order by a.created_time, a.tid");
        List<Map<String, Object>> tasks = query(sql.toString(), args, MAX_TASKS + 1);
        if (tasks.size() > MAX_TASKS) throw failure(HttpStatus.CONFLICT, "链路超过100个任务，请分段管理");
        Set<String> pipelineIds = new LinkedHashSet<>();
        for (Map<String, Object> row : tasks) {
            String sourceDb = text(row, "actual_source_db_id");
            String targetDb = text(row, "actual_target_db_id");
            if (sourceDb.isBlank() || targetDb.isBlank()
                    || !sourceDb.equals(text(row, "source_db_id")) || !targetDb.equals(text(row, "target_db_id")))
                throw failure(HttpStatus.CONFLICT, "任务来源或目标关联不完整，请检查登记记录");
            datasourceIds.add(sourceDb); datasourceIds.add(targetDb);
            for (int i = 1; i <= MAX_DEPTH; i++) {
                String ancestorDatasource = text(row, "ancestor_datasource_" + i);
                if (!ancestorDatasource.isBlank()) datasourceIds.add(ancestorDatasource);
            }
            pipelineIds.add(required(text(row, "pipeline_id")));
        }
        if (pipelineIds.size() != tasks.size()) throw failure(HttpStatus.CONFLICT, "多个任务关联同一画布，请检查任务关联");
        if (tasks.stream().noneMatch(row -> text(row, "task_id").equals(text(selected, "task_id"))))
            throw failure(HttpStatus.CONFLICT, "选定任务不在来源链路中");
        Map<String, Pipeline> pipelines = pipelines(tenant, pipelineIds);
        Set<String> dslTableIds = new LinkedHashSet<>();
        for (Pipeline pipeline : pipelines.values()) {
            if (pipeline.dsl() == null || pipeline.dsl().nodes() == null) continue;
            for (Pipeline.Node node : pipeline.dsl().nodes()) {
                if (node == null || node.config() == null) continue;
                for (String key : List.of("registeredDatasourceId", "selectedDatabaseId", "sourceDbId", "targetDbId", "dbId", "datasourceId", "dataSourceId")) {
                    String value = text(node.config(), key); if (!value.isBlank()) datasourceIds.add(value);
                }
                for (String key : List.of("sourceTableId", "targetTableId", "registeredTableId")) {
                    String value = text(node.config(), key); if (!value.isBlank()) dslTableIds.add(value);
                }
            }
        }
        // Legacy multi-target DSL may reference targets in addition to the task's first target.
        // Resolve all referenced tables once, then authorize their source masters in one scope decision.
        if (!dslTableIds.isEmpty()) {
            if (dslTableIds.size() > 200) throw failure(HttpStatus.CONFLICT, "链路引用表超过200张，请分段管理");
            List<Object> tableArgs = new ArrayList<>(dslTableIds); tableArgs.add(tenant);
            List<Map<String, Object>> tables = query("select tid, datasource_id from db_table_t where tid in ("
                    + placeholders(dslTableIds.size()) + ") and tenant_id = ? and is_del = 0", tableArgs, 201);
            Set<String> resolved = new LinkedHashSet<>();
            for (Map<String, Object> table : tables) { resolved.add(text(table, "tid")); datasourceIds.add(text(table, "datasource_id")); }
            if (!resolved.equals(dslTableIds)) throw failure(HttpStatus.FORBIDDEN, "链路引用的数据表不存在或无访问权限");
        }
        Map<String, Map<String, Object>> sources = authorizedSources(tenant, datasourceIds);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : tasks) {
            String id = text(row, "pipeline_id");
            Pipeline pipeline = pipelines.get(id);
            if (pipeline == null) throw failure(HttpStatus.CONFLICT, "接入任务的画布不存在或已删除");
            validateTaskSource(row, pipeline);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("taskId", text(row, "task_id")); item.put("taskName", text(row, "task_name"));
            item.put("sourceTableId", text(row, "source_table_id")); item.put("targetTableId", text(row, "target_table_id"));
            item.put("sourceDbId", text(row, "source_db_id")); item.put("targetDbId", text(row, "target_db_id"));
            item.put("sourceDbName", text(sources.get(text(row, "source_db_id")), "db_name"));
            item.put("targetDbName", text(sources.get(text(row, "target_db_id")), "db_name"));
            item.put("sourceTableName", text(row, "source_table_name")); item.put("targetTableName", text(row, "target_table_name"));
            item.put("sourceTableNameCn", text(row, "source_table_name_cn")); item.put("targetTableNameCn", text(row, "target_table_name_cn"));
            item.put("pipeline", pipeline); item.put("pipelineId", id);
            if (nativeReady(pipeline)) {
                item.put("processGroupId", pipeline.nifiProcessGroupId());
                item.put("nativeUrl", "/nifi-ui/#/process-groups/" + pipeline.nifiProcessGroupId());
            } else item.put("unavailableReason", "当前任务尚未成功部署，请先保存并部署");
            items.add(item);
        }
        List<Map<String, String>> links = new ArrayList<>();
        for (Map<String, Object> from : items) for (Map<String, Object> to : items) {
            if (from != to && targetTableIds((Pipeline) from.get("pipeline"), String.valueOf(from.get("targetTableId")))
                    .contains(String.valueOf(to.get("sourceTableId")))) {
                links.add(Map.of("fromTaskId", String.valueOf(from.get("taskId")), "toTaskId", String.valueOf(to.get("taskId")),
                        "tableId", String.valueOf(to.get("sourceTableId"))));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rootSourceTableId", root); result.put("selectedPipelineId", text(selected, "pipeline_id"));
        result.put("selectedTaskId", text(selected, "task_id")); result.put("items", items); result.put("links", links);
        result.put("nativeCanvas", nativeSelection(items, "各任务保留独立的NiFi流程组，可选择任务打开原生画布"));
        return result;
    }

    private static Set<String> targetTableIds(Pipeline pipeline, String fallback) {
        Set<String> ids = new LinkedHashSet<>(); ids.add(fallback);
        if (pipeline.dsl() != null && pipeline.dsl().nodes() != null) for (Pipeline.Node node : pipeline.dsl().nodes()) {
            if (!"sink".equals(node.category()) || node.config() == null) continue;
            String id = text(node.config(), "targetTableId"); if (!id.isBlank()) ids.add(id);
        }
        return ids;
    }

    /** Fetch native hierarchy only after an explicit NiFi click; canvas loading never contacts NiFi. */
    public Map<String, Object> nativeByTask(String taskId) {
        Map<String, Object> graph = byTask(taskId);
        @SuppressWarnings("unchecked") List<Map<String, Object>> items = (List<Map<String, Object>>) graph.get("items");
        List<String> ids = items.stream().filter(item -> item.containsKey("nativeUrl"))
                .map(item -> String.valueOf(item.get("processGroupId"))).distinct().toList();
        if (ids.size() < 2) return graph;
        try {
            JsonNode root = nifi.getProcessGroupStatus(nifi.getRootProcessGroupId()).path("processGroupStatus").path("aggregateSnapshot");
            if (!root.isObject() || root.path("id").asText("").isBlank())
                throw new IllegalStateException("NiFi流程组层级不完整");
            Map<String, String> parents = new LinkedHashMap<>();
            collectParents(root, "", parents, 0);
            for (Map<String, Object> item : items) {
                if (item.containsKey("nativeUrl") && !parents.containsKey(String.valueOf(item.get("processGroupId")))) {
                    item.remove("nativeUrl"); item.remove("processGroupId");
                    item.put("unavailableReason", "当前NiFi节点未找到该任务流程组，请核对节点配置或重新部署");
                }
            }
            graph.put("nativeCanvas", nativeSelection(items, "各任务保留独立的NiFi流程组，可选择任务打开原生画布"));
            String common = parents.get(ids.getFirst());
            // A parent containing other tasks can reveal groups outside this authorized chain.
            // Offer a shared overview only when its immediate child groups are exactly this chain's groups.
            Set<String> siblingIds = new LinkedHashSet<>();
            if (common != null) parents.forEach((id, parent) -> { if (common.equals(parent)) siblingIds.add(id); });
            if (common != null && !common.isBlank() && siblingIds.equals(new LinkedHashSet<>(ids))
                    && ids.stream().allMatch(id -> common.equals(parents.get(id)))) {
                graph.put("nativeCanvas", Map.of("mode", "COMMON_PARENT", "url", "/nifi-ui/#/process-groups/" + common,
                        "processGroupId", common, "reason", "共同父流程组展示各任务组总览；进入任务组后查看内部处理器"));
            }
        } catch (Exception unavailable) {
            graph.put("nativeCanvas", nativeSelection(items, "暂时无法核验NiFi共同父流程组，请选择已部署的任务入口"));
        }
        return graph;
    }

    private static void collectParents(JsonNode snapshot, String parent, Map<String, String> parents, int depth) {
        if (depth > 32 || parents.size() >= 5000)
            throw new IllegalStateException("NiFi流程组层级超过本次核验范围");
        String id = snapshot.path("id").asText("");
        if (id.isBlank()) return;
        if (parents.containsKey(id)) throw new IllegalStateException("NiFi流程组层级存在重复标识");
        parents.put(id, parent);
        for (JsonNode child : snapshot.path("processGroupStatusSnapshots"))
            collectParents(child.path("processGroupStatusSnapshot"), id, parents, depth + 1);
    }

    private static Map<String, String> nativeSelection(List<Map<String, Object>> items, String reason) {
        return Map.of("mode", items.stream().anyMatch(item -> item.containsKey("nativeUrl")) ? "SELECT_TASK" : "NONE", "reason", reason);
    }

    private Map<String, Map<String, Object>> authorizedSources(String tenant, Set<String> ids) {
        if (ids.contains("") || ids.size() > 200) throw failure(HttpStatus.CONFLICT, "数据源关联不完整或超过200项");
        List<Object> args = new ArrayList<>(ids); args.add(tenant);
        List<Map<String, Object>> rows = query("select tid, db_name, created_by, org_id from db_datasource_t where tid in ("
                + placeholders(ids.size()) + ") and tenant_id = ? and is_del = 0", args, 201);
        Map<String, Map<String, Object>> authorized = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) authorized.put(text(row, "tid"), row);
        if (!authorized.keySet().equals(ids)) throw failure(HttpStatus.FORBIDDEN, "链路数据源不存在或无访问权限");
        return authorized;
    }

    private Map<String, Pipeline> pipelines(String tenant, Set<String> ids) {
        List<Object> args = new ArrayList<>(ids); args.add(tenant);
        List<Map<String, Object>> rows = query("select * from nifi_pipeline_t where id in (" + placeholders(ids.size())
                + ") and tenant_id = ? and is_del = 0", args, MAX_TASKS + 1);
        Map<String, Pipeline> result = new LinkedHashMap<>();
        long nodeCount = 0;
        long byteCount = 0;
        for (Map<String, Object> row : rows) {
            try {
                String dslJson = text(row, "dsl_json");
                byteCount += dslJson.getBytes(StandardCharsets.UTF_8).length;
                if (byteCount > 8 * 1024 * 1024) throw failure(HttpStatus.CONFLICT, "链路画布超过8MB，请分段管理");
                Pipeline.Dsl dsl = json.readValue(dslJson, Pipeline.Dsl.class);
                nodeCount += dsl.nodes() == null ? 0 : dsl.nodes().size();
                if (nodeCount > 5000) throw failure(HttpStatus.CONFLICT, "链路组件超过5000个，请分段管理");
                PipelineStatus status;
                try { status = PipelineStatus.valueOf(text(row, "status")); } catch (IllegalArgumentException old) { status = PipelineStatus.SAVED; }
                String mapping = text(row, "node_mapping_json");
                Pipeline pipeline = new Pipeline(text(row, "id"), text(row, "name"), text(row, "description"),
                        number(row, "created_at"), number(row, "updated_at"), dsl, text(row, "nifi_process_group_id"), status,
                        text(row, "last_deployed_hash"), number(row, "last_deployed_at"), number(row, "last_stopped_at"),
                        mapping.isBlank() ? null : json.readValue(mapping, NifiNodeMapping.class), number(row, "last_bulletin_id"));
                result.put(pipeline.id(), pipeline);
            } catch (ResponseStatusException bound) { throw bound;
            } catch (Exception invalid) { throw failure(HttpStatus.CONFLICT, "任务画布内容无法读取，请检查保存的配置"); }
        }
        if (!result.keySet().equals(ids)) throw failure(HttpStatus.FORBIDDEN, "链路画布不存在或不属于当前租户");
        return result;
    }

    private static void validateTaskSource(Map<String, Object> task, Pipeline pipeline) {
        if (pipeline.dsl() == null || pipeline.dsl().nodes() == null) return;
        for (Pipeline.Node node : pipeline.dsl().nodes()) {
            if (!"source".equals(node.category()) || node.config() == null) continue;
            String table = text(node.config(), "sourceTableId");
            if (!table.isBlank() && !table.equals(text(task, "source_table_id")))
                throw failure(HttpStatus.CONFLICT, "任务来源表与保存的画布不一致，请检查任务关联");
            for (String key : List.of("registeredDatasourceId", "sourceDbId", "selectedDatabaseId")) {
                String database = text(node.config(), key);
                if (!database.isBlank() && !database.equals(text(task, "source_db_id")))
                    throw failure(HttpStatus.CONFLICT, "任务来源库与保存的画布不一致，请检查任务关联");
            }
        }
    }

    private static boolean nativeReady(Pipeline pipeline) {
        return pipeline.nifiProcessGroupId() != null && !pipeline.nifiProcessGroupId().isBlank()
                && pipeline.lastDeployedHash() != null && pipeline.lastDeployedHash().equals(DslHasher.deploymentHash(pipeline.dsl(), DslHasher.CURRENT_COMPILER_REVISION))
                && pipeline.status() != PipelineStatus.DEPLOYING && pipeline.status() != PipelineStatus.DEPLOY_FAILED;
    }

    private static String ancestors(List<Object> args, String tenant) {
        StringBuilder joins = new StringBuilder(" left join db_table_t s0 on s0.tid = a.source_table_id and s0.tenant_id = ? and s0.is_del = 0 ");
        args.add(tenant);
        for (int i = 1; i <= MAX_DEPTH; i++) {
            joins.append(" left join db_table_t s").append(i).append(" on s").append(i).append(".tid = s")
                    .append(i - 1).append(".source_table_id and s").append(i).append(".tenant_id = ? and s")
                    .append(i).append(".is_del = 0 and s").append(i - 1).append(".source_table_id <> s")
                    .append(i - 1).append(".tid ");
            args.add(tenant);
        }
        return joins.toString();
    }

    private List<Map<String, Object>> query(String sql, List<Object> args, int maxRows) {
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement(sql);
            statement.setMaxRows(maxRows);
            statement.setQueryTimeout(10);
            for (int i = 0; i < args.size(); i++) statement.setObject(i + 1, args.get(i));
            return statement;
        }, new ColumnMapRowMapper());
    }

    private static String placeholders(int size) { return String.join(",", Collections.nCopies(size, "?")); }
    private static String text(Map<String, ?> row, String key) { Object value = row == null ? null : row.get(key); return value == null ? "" : String.valueOf(value).trim(); }
    private static Long number(Map<String, Object> row, String key) { Object value = row.get(key); return value instanceof Number number ? number.longValue() : null; }
    private static String required(String value) { if (value == null || value.isBlank()) throw failure(HttpStatus.BAD_REQUEST, "缺少任务或画布标识"); return value.trim(); }
    private static ResponseStatusException failure(HttpStatus status, String reason) { return new ResponseStatusException(status, reason); }
}
