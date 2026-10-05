package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Resolves business ownership from tenant master data and organizes native NiFi groups. */
@Component
public class PipelineGroupOrganizer {
    private static final String SURVEILLANCE_FLOW_PREFIX = "结构化数据布控引擎流组";
    private static final String SURVEILLANCE_GROUP_NAME = "布控引擎组";
    private final NifiClient nifi;
    private final JdbcTemplate jdbc;

    public PipelineGroupOrganizer(NifiClient nifi, JdbcTemplate jdbc) {
        this.nifi = nifi;
        this.jdbc = jdbc;
    }

    public Layout prepare(Pipeline pipeline) {
        String tenant = TenantContext.requireTenantId();
        Map<String, Object> task = first(jdbc.queryForList("""
                select tid, task_name, source_db_id, process_group_id from data_access_agg_task_t
                 where tenant_id = ? and pipeline_id = ? and is_del = 0 order by created_time, tid
                """, tenant, pipeline.id()));
        String datasourceId = sourceDatasourceId(pipeline);
        if (datasourceId.isBlank()) datasourceId = text(task, "source_db_id");
        Map<String, Object> datasource = first(jdbc.queryForList("""
                select tid, db_name, app_id, org_id from db_datasource_t
                 where tenant_id = ? and tid = ? and ifnull(is_del, 0) = 0
                """, tenant, datasourceId));
        Map<String, Object> app = first(jdbc.queryForList("""
                select tid, app_name, org_id from sym_application_t
                 where tenant_id = ? and tid = ? and ifnull(is_del, 0) = 0
                """, tenant, text(datasource, "app_id")));
        String orgId = text(datasource, "org_id");
        if (orgId.isBlank()) orgId = text(app, "org_id");
        Map<String, Object> org = first(jdbc.queryForList("""
                select id, name from rm_org_t
                 where tenant_id = ? and id = ? and ifnull(deleted, 0) = 0
                """, tenant, orgId));

        // A native NiFi root is already the top-level container. Keep only the
        // business hierarchy that helps operators locate a flow:
        // department -> application -> task flow. The compiler creates the
        // task-flow Process Group itself, so do not create another task wrapper
        // around it. A task name often equals the pipeline name; keeping both
        // produces two consecutive, indistinguishable breadcrumb levels.
        // When an application is named the same as its task, the task flow is
        // placed directly under the department for the same reason.
        // Tenant, datasource and transfer
        // direction are represented by ownership markers and task metadata;
        // making each one a Process Group only adds empty navigation levels.
        String top = nifi.getRootProcessGroupId();
        String applicationName = label(app, "app_name", "未归属应用系统");
        String taskName = label(task, "task_name", pipeline.name());
        String flowParent;
        String controllerServiceScope = "";
        if (pipeline.name() != null && pipeline.name().startsWith(SURVEILLANCE_FLOW_PREFIX)) {
            // Surveillance resources are independently deployable flows, but
            // operators manage them together under one stable NiFi group.
            flowParent = ensure(top, SURVEILLANCE_GROUP_NAME, marker("application", "structured-surveillance"));
            controllerServiceScope = flowParent;
        } else {
            String department = ensure(top, label(org, "name", "未归属部门"), marker("department", key(org, "id")));
            flowParent = department;
            if (!sameGroupLabel(applicationName, taskName)) {
                flowParent = ensure(department, applicationName, marker("application", key(app, "tid")));
                // Services owned by an application are visible to every task PG
                // beneath it. A compact single-name task flow has no application
                // parent by design, so it remains fully self-contained.
                controllerServiceScope = flowParent;
            }
        }
        String flowMarker = marker("pipeline", pipeline.id());
        Set<String> previousIds = new LinkedHashSet<>();
        if (pipeline.nifiProcessGroupId() != null && !pipeline.nifiProcessGroupId().isBlank()) {
            previousIds.add(pipeline.nifiProcessGroupId());
        }
        // Recover the ID older releases wrote only to the task after creating a PG.
        for (Map<String, Object> row : jdbc.queryForList("""
                select process_group_id from data_access_agg_task_t
                 where tenant_id = ? and pipeline_id = ? and is_del = 0
                """, tenant, pipeline.id())) {
            if (!text(row, "process_group_id").isBlank()) previousIds.add(text(row, "process_group_id"));
        }
        // Covers a process crash between native creation and database persistence,
        // including a task whose department/datasource changed since deployment.
        collectOwnedFlows(top, flowMarker, previousIds);
        List<JsonNode> siblings = nifi.listProcessGroups(flowParent);
        Position position = nextPosition(siblings);
        for (JsonNode sibling : siblings) {
            if (previousIds.contains(sibling.path("id").asText())) {
                JsonNode pos = sibling.path("component").path("position");
                position = new Position(pos.path("x").asDouble(), pos.path("y").asDouble());
                break;
            }
        }
        return new Layout(top, flowParent, controllerServiceScope, pipeline.name(), flowMarker, position, new ArrayList<>(previousIds));
    }

    public boolean isCurrent(Layout layout, String id) {
        if (id == null || layout.previousIds().size() != 1 || !layout.previousIds().contains(id)) return false;
        for (JsonNode group : nifi.listProcessGroups(layout.parentId())) {
            if (id.equals(group.path("id").asText())
                    && layout.comments().equals(group.path("component").path("comments").asText())) {
                rename(group, layout.name(), layout.comments());
                return true;
            }
        }
        return false;
    }

    /** Read-only recovery for undeploy/delete; never creates missing hierarchy groups. */
    public List<String> existingIds(Pipeline pipeline) {
        String tenant = TenantContext.requireTenantId();
        Set<String> ids = new LinkedHashSet<>();
        if (pipeline.nifiProcessGroupId() != null && !pipeline.nifiProcessGroupId().isBlank()) ids.add(pipeline.nifiProcessGroupId());
        for (Map<String, Object> task : jdbc.queryForList("""
                select process_group_id from data_access_agg_task_t
                 where tenant_id = ? and pipeline_id = ? and is_del = 0
                """, tenant, pipeline.id())) {
            if (!text(task, "process_group_id").isBlank()) ids.add(text(task, "process_group_id"));
        }
        // Support both the compact hierarchy and legacy tenant/datasource
        // wrappers without creating any groups during recovery.
        collectOwnedFlows(nifi.getRootProcessGroupId(), marker("pipeline", pipeline.id()), ids);
        return new ArrayList<>(ids);
    }

    public List<String> parentIds(List<String> groupIds) {
        Set<String> parents = new LinkedHashSet<>();
        for (String id : groupIds) {
            try {
                String parent = nifi.getProcessGroup(id).path("component").path("parentGroupId").asText();
                if (!parent.isBlank()) parents.add(parent);
            } catch (NifiException e) {
                if (e.status() != 404) throw e;
            }
        }
        return new ArrayList<>(parents);
    }

    /** Remove only empty managed ancestors of a removed leaf, preserving the new destination. */
    public void pruneEmptyAncestors(List<String> parents, String retainedParent) {
        for (String first : parents) {
            String id = first;
            while (!id.isBlank() && !id.equals(retainedParent)) {
                try {
                    JsonNode component = nifi.getProcessGroup(id).path("component");
                    String comments = component.path("comments").asText();
                    if (!comments.startsWith(markerPrefix())) break;
                    JsonNode flow = nifi.get("/flow/process-groups/" + id, JsonNode.class).path("processGroupFlow").path("flow");
                    boolean occupied = false;
                    for (String type : List.of("processGroups", "processors", "connections", "inputPorts", "outputPorts", "remoteProcessGroups", "funnels", "labels")) {
                        if (!flow.path(type).isEmpty()) { occupied = true; break; }
                    }
                    if (occupied || !nifi.listControllerServices(id).isEmpty()) break;
                    nifi.deleteProcessGroup(id);
                    id = component.path("parentGroupId").asText();
                } catch (NifiException e) {
                    if (e.status() != 404) throw e;
                    break;
                }
            }
        }
    }

    private String ensure(String parent, String name, String comments) {
        List<JsonNode> siblings = nifi.listProcessGroups(parent);
        List<JsonNode> matches = siblings.stream()
                .filter(g -> comments.equals(g.path("component").path("comments").asText())).toList();
        if (matches.size() > 1) throw new IllegalStateException("存在重复的管理分组，请先整理：" + name);
        if (!matches.isEmpty()) {
            JsonNode group = matches.getFirst();
            rename(group, name, comments);
            return group.path("id").asText();
        }
        Position position = nextPosition(siblings);
        return nifi.createProcessGroup(parent, name, position.x(), position.y(), comments).id();
    }

    private void rename(JsonNode group, String name, String comments) {
        if (!name.equals(group.path("component").path("name").asText())) {
            nifi.updateProcessGroupMetadata(group.path("id").asText(), name, comments);
        }
    }

    private void collectOwnedFlows(String parent, String comments, Set<String> ids) {
        for (JsonNode group : nifi.listProcessGroups(parent)) {
            String marker = group.path("component").path("comments").asText();
            if (comments.equals(marker)) ids.add(group.path("id").asText());
            else if (marker.startsWith(markerPrefix()) && !marker.contains(":pipeline:")) {
                collectOwnedFlows(group.path("id").asText(), comments, ids);
            }
        }
    }

    public static Position nextPosition(List<JsonNode> siblings) {
        double right = -520;
        for (JsonNode group : siblings) {
            JsonNode pos = group.path("component").path("position");
            right = Math.max(right, pos.path("x").asDouble(0));
        }
        return new Position(right + 520, 0);
    }

    static String sourceDatasourceId(Pipeline pipeline) {
        if (pipeline.dsl() == null || pipeline.dsl().nodes() == null) return "";
        for (Pipeline.Node node : pipeline.dsl().nodes()) {
            if (!"source".equals(node.category()) || node.config() == null) continue;
            for (String key : List.of("registeredDatasourceId", "selectedDatabaseId", "sourceDbId", "dbId", "datasourceId", "dataSourceId")) {
                String id = text(node.config(), key);
                if (!id.isBlank()) return id;
            }
        }
        return "";
    }

    private String markerPrefix() { return "data-elements:v1:" + TenantContext.requireTenantId() + ":"; }
    private String marker(String kind, String id) { return markerPrefix() + kind + ":" + id; }
    private static boolean sameGroupLabel(String left, String right) {
        return !left.isBlank() && left.equals(right);
    }
    private static Map<String, Object> first(List<Map<String, Object>> rows) { return rows.isEmpty() ? Map.of() : rows.getFirst(); }
    private static String text(Map<String, Object> row, String key) { return row.get(key) == null ? "" : String.valueOf(row.get(key)).trim(); }
    private static String key(Map<String, Object> row, String key) { String id = text(row, key); return id.isBlank() ? "unassigned" : id; }
    private static String label(Map<String, Object> row, String key, String fallback) { String name = text(row, key); return name.isBlank() ? (fallback == null ? "未命名" : fallback) : name; }

    public record Position(double x, double y) {}
    public record Layout(String topId, String parentId, String controllerServiceScopeId,
                         String name, String comments, Position position, List<String> previousIds) {}
}
