package com.linewell.dataelement.integration.nifi.canvas.runtime;

import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import java.util.*;

@Component
@MagicModule("taskRuntime")
public class TaskRuntimeMagicModule {
    private final TaskRuntimeStatusService service;
    public TaskRuntimeMagicModule(TaskRuntimeStatusService service) { this.service = service; }
    public Map<String, Object> snapshot() { return service.snapshot(); }
    public void decorate(List<Map<String, Object>> rows, Map<String, Object> snapshot) {
        for (Map<String, Object> row : rows) {
            row.put("storedTaskStatus", row.get("taskStatus"));
            String pg = Objects.toString(row.get("runtimeProcessGroupId"), "");
            row.putAll(service.describe(snapshot, pg, Objects.toString(row.get("lifecycleStatus"), "")));
            Object tables = row.get("syncTables");
            if (tables instanceof List<?> list) for (Object item : list) if (item instanceof Map map) { map.put("syncStatus", row.get("taskStatus")); map.put("syncStatusText", row.get("runtimeLabel")); }
        }
    }
}
