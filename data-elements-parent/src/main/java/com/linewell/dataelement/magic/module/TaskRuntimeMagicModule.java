package com.linewell.dataelement.magic.module;

import com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeStatusService;
import com.linewell.dataelement.utils.StructuredValues;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import java.util.*;

@Component
@MagicModule("taskRuntime")
public class TaskRuntimeMagicModule {
    private final TaskRuntimeStatusService service;
    public TaskRuntimeMagicModule(TaskRuntimeStatusService service) { this.service = service; }
    public Map<String, Object> snapshot() { return service.snapshotAll(); }
    public SseEmitter eventStream() { return service.subscribeAll(); }
    public SseEmitter eventStreamForNodes(String encodedIds) {
        try {
            var value = new com.fasterxml.jackson.databind.ObjectMapper().readTree(encodedIds);
            if (!value.isArray() || value.size() > 1000) throw new IllegalArgumentException();
            var ids = new LinkedHashSet<String>();
            for (var item : value) {
                if (!item.isTextual() || !item.asText().matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException();
                ids.add(item.asText());
            }
            return service.subscribeForNodes(ids);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException invalid) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "节点范围必须是最多 1000 个有效标识的数组");
        }
    }
    public Map<String, Object> snapshotForRows(List<Map<String, Object>> rows) {
        var ids = new LinkedHashSet<String>();
        boolean legacy = false;
        for (var row : rows) if (!Objects.toString(row.get("runtimeProcessGroupId"), "").isBlank()) {
            String id = Objects.toString(row.get("runtimeNodeId"), "");
            if (id.isBlank()) legacy = true; else ids.add(id);
        }
        return legacy ? service.snapshotAll() : service.snapshotForNodes(ids);
    }
    public String resolvedNode(Map<String, Object> snapshot, String assigned, String group) {
        if (assigned != null && !assigned.isBlank()) return assigned;
        if (group == null || group.isBlank()) return Objects.toString(snapshot.get("defaultNodeId"), "");
        return snapshot.get("legacyOwners") instanceof Map<?,?> owners ? Objects.toString(owners.get(group), "") : "";
    }
    /** Pure in-memory aggregation over already authorized page rows; never probes by row. */
    public Map<String, Object> summary(List<Map<String, Object>> rows, Map<String, Object> snapshot) {
        var ids = new TreeSet<String>();
        for (var row : rows) if (!Objects.toString(row.get("runtimeProcessGroupId"), "").isBlank()) {
            ids.add(resolvedNode(snapshot, Objects.toString(row.get("runtimeNodeId"), ""), Objects.toString(row.get("runtimeProcessGroupId"), "")));
        }
        Map<?, ?> nodes = snapshot.get("byNode") instanceof Map<?, ?> value ? value : Map.of();
        int available = 0;
        long observed = 0;
        var revisions = new ArrayList<String>();
        var details = new ArrayList<Map<String, Object>>();
        for (String id : ids) {
            Map<?, ?> node = nodes.get(id) instanceof Map<?, ?> value ? value : Map.of();
            boolean fresh = Boolean.TRUE.equals(node.get("fresh"));
            if (fresh) available++;
            long time = node.get("observedAt") instanceof Number n ? n.longValue() : 0;
            if (time > 0) observed = observed == 0 ? time : Math.min(observed, time);
            revisions.add(id + "=" + Objects.toString(node.get("revision"), "unavailable"));
            details.add(Map.of("nodeId", id, "nodeName", Objects.toString(node.get("nodeName"), id),
                    "fresh", fresh, "message", Objects.toString(node.get("message"), "任务绑定节点已停用、删除或不可用")));
        }
        var result = new HashMap<String, Object>();
        result.put("revision", String.join("|", revisions).isEmpty() ? "empty" : String.join("|", revisions));
        result.put("fresh", available == ids.size());
        result.put("partial", available > 0 && available < ids.size());
        result.put("observedAt", observed);
        result.put("nodeIds", new ArrayList<>(ids));
        result.put("nodes", details);
        result.put("message", ids.isEmpty() ? "本页暂无已部署任务节点" : available == ids.size() ? ""
                : "本页任务关联 " + ids.size() + " 个节点，" + available + " 个已确认，" + (ids.size() - available) + " 个待确认");
        return result;
    }
    /** Technical projection of one recursive snapshot, without I/O or business authorization. */
    public List<Map<String,Object>> nativeGroups(Map<String,Object> snapshot) {
        var result = new ArrayList<Map<String,Object>>();
        if (!(snapshot.get("nodes") instanceof List<?> nodes)) return result;
        for (var item : nodes) if (item instanceof Map<?,?> node && Boolean.TRUE.equals(node.get("fresh"))
                && node.get("index") instanceof com.linewell.dataelement.integration.nifi.canvas.runtime.RuntimeStatusIndex index) {
            var parents = new HashMap<String,String>();
            index.groups().forEach((id, group) -> group.snapshot().path("processGroupStatusSnapshots").forEach(child ->
                    parents.put(child.path("processGroupStatusSnapshot").path("id").asText(), id)));
            for (var entry : index.groups().entrySet()) {
                var group = entry.getValue(); var raw = group.snapshot();
                int direct = raw.path("processorStatusSnapshots").size() + raw.path("inputPortStatusSnapshots").size()
                        + raw.path("outputPortStatusSnapshots").size() + raw.path("remoteProcessGroupStatusSnapshots").size();
                if (direct == 0) continue;
                if (result.size() >= 10000) throw new IllegalStateException("原生流程超过10000个，请缩小节点范围");
                var row = new HashMap<String,Object>();
                row.put("nodeId", node.get("nodeId")); row.put("nodeName", node.get("nodeName"));
                row.put("networkCode", node.get("networkCode")); row.put("groupId", entry.getKey());
                row.put("name", raw.path("name").asText(entry.getKey())); row.put("parentGroupId", parents.get(entry.getKey()));
                var ancestors = new ArrayList<String>(); String parent = parents.get(entry.getKey());
                while (parent != null && ancestors.size()<64) { ancestors.add(parent); parent=parents.get(parent); }
                row.put("ancestors", ancestors); row.put("directComponentCount", direct);
                row.put("state", group.state()); row.put("running", group.running()); row.put("taskStatus", group.taskStatus());
                row.put("observedAt", node.get("observedAt")); row.put("nodeUrl", node.get("nodeUrl"));
                result.add(row);
            }
        }
        return result;
    }
    public void decorate(List<Map<String, Object>> rows, Map<String, Object> snapshot) {
        for (Map<String, Object> row : rows) {
            row.put("storedTaskStatus", row.get("taskStatus"));
            String pg = Objects.toString(row.get("runtimeProcessGroupId"), "");
            String assigned = Objects.toString(row.get("runtimeNodeId"), "");
            Map<String, Object> selected = snapshot;
            if (snapshot.get("byNode") instanceof Map<?, ?> nodes) {
                String nodeId = resolvedNode(snapshot, assigned, pg);
                Object node = nodes.get(nodeId);
                String missing = assigned.isBlank() && !pg.isBlank()
                        ? snapshot.get("ambiguousGroups") instanceof Set<?> duplicates && duplicates.contains(pg)
                        ? "多个节点存在相同流程组，无法确认历史任务所属节点" : "尚未在可达节点中找到历史流程，请核对节点和流程组"
                        : "任务绑定节点已停用、删除或不可用";
                selected = node instanceof Map<?, ?> value ? StructuredValues.objectMap(value)
                        : Map.of("fresh", false, "observedAt", 0L, "message", missing);
                row.put("nifiNodeId", nodeId);
                row.put("nifiNodeName", selected.get("nodeName"));
                row.put("nifiNetworkCode", selected.get("networkCode"));
                row.put("runtimeLegacyNode", assigned.isBlank());
                row.put("runtimeNodeResolved", !nodeId.isBlank());
            }
            row.putAll(service.describe(selected, pg, Objects.toString(row.get("lifecycleStatus"), "")));
            Object tables = row.get("syncTables");
            if (tables instanceof List<?> list) for (Object item : list) if (item instanceof Map<?, ?>) { var map = StructuredValues.objectMap(item); map.put("syncStatus", row.get("taskStatus")); map.put("syncStatusText", row.get("runtimeLabel")); }
        }
    }
}
