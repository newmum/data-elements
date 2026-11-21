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
    public void decorate(List<Map<String, Object>> rows, Map<String, Object> snapshot) {
        for (Map<String, Object> row : rows) {
            row.put("storedTaskStatus", row.get("taskStatus"));
            String pg = Objects.toString(row.get("runtimeProcessGroupId"), "");
            String assigned = Objects.toString(row.get("runtimeNodeId"), "");
            Map<String, Object> selected = snapshot;
            if (snapshot.get("byNode") instanceof Map<?, ?> nodes) {
                String nodeId = assigned.isBlank() ? Objects.toString(snapshot.get("defaultNodeId"), "") : assigned;
                Object node = nodes.get(nodeId);
                selected = node instanceof Map<?, ?> value ? StructuredValues.objectMap(value)
                        : Map.of("fresh", false, "observedAt", 0L, "message", "任务绑定节点已停用、删除或不可用");
                row.put("nifiNodeId", nodeId);
                row.put("nifiNodeName", selected.get("nodeName"));
                row.put("nifiNetworkCode", selected.get("networkCode"));
                row.put("runtimeLegacyNode", assigned.isBlank());
            }
            row.putAll(service.describe(selected, pg, Objects.toString(row.get("lifecycleStatus"), "")));
            Object tables = row.get("syncTables");
            if (tables instanceof List<?> list) for (Object item : list) if (item instanceof Map<?, ?>) { var map = StructuredValues.objectMap(item); map.put("syncStatus", row.get("taskStatus")); map.put("syncStatusText", row.get("runtimeLabel")); }
        }
    }
}
