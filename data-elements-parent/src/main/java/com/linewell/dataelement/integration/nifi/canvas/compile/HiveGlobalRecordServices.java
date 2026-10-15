package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** Read the selected NiFi node's root scope once; never create or alter public codecs. */
record HiveGlobalRecordServices(String rootId, List<JsonNode> services, String writerId, String readerId) {
    static HiveGlobalRecordServices resolve(NifiClient nifi) {
        // The configured canvas root may be a tenant/application subgroup.
        // Public services belong to the actual NiFi Flow root instead.
        JsonNode root = nifi.get("/flow/process-groups/root",JsonNode.class);
        String rootId = root==null?null:root.path("processGroupFlow").path("id").asText();
        if (rootId == null || rootId.isBlank()) throw new IllegalStateException("无法确认 NiFi 根作用域，未创建 Hive 流程");
        List<JsonNode> services = nifi.listControllerServices(rootId);
        services = services == null ? List.of() : List.copyOf(services);
        String writer = require(services, rootId, HiveTemporalJsonPolicy.WRITER_NAME, "org.apache.nifi.json.JsonRecordSetWriter");
        String reader = require(services, rootId, HiveTemporalJsonPolicy.READER_NAME, "org.apache.nifi.json.JsonTreeReader");
        return new HiveGlobalRecordServices(rootId, services, writer, reader);
    }

    static String require(List<JsonNode> services, String rootId, String name, String type) {
        var matches = new LinkedHashMap<String, JsonNode>();
        for (JsonNode service : services) {
            JsonNode component = service.path("component");
            if (!name.equals(component.path("name").asText())) continue;
            String parent = component.path("parentGroupId").asText();
            if (!parent.isBlank() && !rootId.equals(parent)) continue;
            if (!type.equals(component.path("type").asText()))
                throw new IllegalStateException("NiFi 根作用域全局服务类型不正确：" + name);
            String id = component.path("id").asText(service.path("id").asText());
            if (id.isBlank()) throw new IllegalStateException("NiFi 根作用域全局服务缺少 ID：" + name);
            matches.put(id, component);
        }
        if (matches.isEmpty()) throw new IllegalStateException("NiFi 根作用域缺少全局服务：" + name + "；请配置并启用已有全局服务，不会自动创建任务级替代服务");
        if (matches.size() != 1) throw new IllegalStateException("NiFi 根作用域存在多个同名全局服务：" + name + "；无法唯一确定引用");
        var entry = matches.entrySet().iterator().next();
        if (!"ENABLED".equals(entry.getValue().path("state").asText()))
            throw new IllegalStateException("NiFi 根作用域全局服务尚未启用：" + name);
        if (!entry.getValue().path("validationErrors").isEmpty())
            throw new IllegalStateException("NiFi 根作用域全局服务校验未通过：" + name);
        return entry.getKey();
    }

    Set<String> ids() { return Set.of(writerId, readerId); }
}
