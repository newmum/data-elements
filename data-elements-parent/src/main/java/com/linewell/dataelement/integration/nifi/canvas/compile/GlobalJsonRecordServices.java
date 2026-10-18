package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashSet;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveSinkPublicationPolicy;

/** Read the selected NiFi node's root scope once; never create or alter public codecs. */
record GlobalJsonRecordServices(String rootId, List<JsonNode> services, Map<String,String> writerIds, String readerId, Set<String> hiveNodes) {
    static GlobalJsonRecordServices resolve(NifiClient nifi) {
        return resolve(nifi, Set.of(HiveTemporalJsonPolicy.DEFAULT_WRITER_NAME), Set.of());
    }
    static GlobalJsonRecordServices resolve(NifiClient nifi, Pipeline pipeline) {
        Pipeline published=HiveSinkPublicationPolicy.forPublication(pipeline);
        Set<String> hive=HiveTemporalJsonPolicy.hiveWriterNodes(published);
        Set<String> required=new LinkedHashSet<>();
        if(published!=null&&published.dsl()!=null&&published.dsl().nodes()!=null)
            published.dsl().nodes().forEach(node->required.add(hive.contains(node.id())?HiveTemporalJsonPolicy.WRITER_NAME:HiveTemporalJsonPolicy.DEFAULT_WRITER_NAME));
        if(required.isEmpty())required.add(HiveTemporalJsonPolicy.DEFAULT_WRITER_NAME);
        return resolve(nifi,required,hive);
    }
    private static GlobalJsonRecordServices resolve(NifiClient nifi,Set<String> required,Set<String> hiveNodes) {
        // The configured canvas root may be a tenant/application subgroup.
        // Public services belong to the actual NiFi Flow root instead.
        JsonNode root = nifi.get("/flow/process-groups/root",JsonNode.class);
        String rootId = root==null?null:root.path("processGroupFlow").path("id").asText();
        if (rootId == null || rootId.isBlank()) throw new IllegalStateException("无法确认 NiFi 根作用域，未创建任务流程");
        List<JsonNode> services = nifi.listControllerServices(rootId);
        services = services == null ? List.of() : List.copyOf(services);
        Map<String,String> writers=new LinkedHashMap<>();
        for(String name:required)writers.put(name,require(services,rootId,name,"org.apache.nifi.json.JsonRecordSetWriter"));
        String reader = require(services, rootId, HiveTemporalJsonPolicy.READER_NAME, "org.apache.nifi.json.JsonTreeReader");
        return new GlobalJsonRecordServices(rootId, services, Map.copyOf(writers), reader, hiveNodes);
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

    String writerId() { return writerIds.getOrDefault(HiveTemporalJsonPolicy.DEFAULT_WRITER_NAME,writerIds.get(HiveTemporalJsonPolicy.WRITER_NAME)); }
    String writerForNode(String nodeId) {
        String name=hiveNodes.contains(nodeId)?HiveTemporalJsonPolicy.WRITER_NAME:HiveTemporalJsonPolicy.DEFAULT_WRITER_NAME;
        String id=writerIds.get(name);
        if(id==null)throw new IllegalStateException("节点所需全局 Writer 尚未解析："+name);
        return id;
    }
    Set<String> ids() { var result=new LinkedHashSet<>(writerIds.values());result.add(readerId);return Set.copyOf(result); }
}
