package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.mapping.core.MappingException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads stopped and empty native graphs without changing their processor state or queues. */
final class LegacyNativeGraph {
    private final NifiClient nifi;

    LegacyNativeGraph(NifiClient nifi) {
        this.nifi = nifi;
    }

    record Graph(Map<String, JsonNode> processors, Map<String, JsonNode> connections, Map<String, JsonNode> states,
                 Map<String, JsonNode> funnels) {
        List<String> processorRevisions() {
            return revisions(processors);
        }

        List<String> connectionRevisions() {
            return revisions(connections);
        }

        private static List<String> revisions(Map<String, JsonNode> components) {
            return components.entrySet().stream().map(entry -> entry.getKey() + ":" + revision(entry.getValue())).sorted().toList();
        }
    }

    Graph read(String group) {
        return read(group, "");
    }

    Graph read(String group, String retainedFailure) {
        var flow = nifi.get("/flow/process-groups/" + group, JsonNode.class).path("processGroupFlow").path("flow");
        var processors = indexed(flow.path("processors"));
        var connections = indexed(flow.path("connections"));
        var funnels = flow.has("funnels") ? indexed(flow.path("funnels")) : Map.<String, JsonNode>of();
        if (!flow.path("processGroups").isArray() || !flow.path("processGroups").isEmpty() || processors.isEmpty()
                || processors.size() > 512 || connections.size() > 1024) {
            throw failure("MIGRATION_SCOPE_UNSUPPORTED", "当前流程组超出有界原生升级范围");
        }
        var states = new LinkedHashMap<String, JsonNode>();
        processors.forEach((id, node) -> {
            var component = node.path("component");
            if (!Set.of("STOPPED", "DISABLED").contains(component.path("state").asText())
                    || node.path("status").path("aggregateSnapshot").path("activeThreadCount").asLong(-1) != 0) {
                throw failure("MIGRATION_UNAVAILABLE", "全部处理器须停止且无线程活动");
            }
            if (component.path("persistsState").asBoolean()) {
                JsonNode state = nifi.get("/processors/" + id + "/state", JsonNode.class).path("componentState");
                if (!state.has("localState") || !state.has("clusterState")) {
                    throw failure("MIGRATION_UNAVAILABLE", "不能核验全部来源状态");
                }
                states.put(id, state);
            }
        });
        connections.forEach((id, edge) -> {
            var status = edge.path("status").path("aggregateSnapshot");
            long count = status.path("flowFilesQueued").asLong(-1);
            long bytes = status.path("bytesQueued").asLong(-1);
            if (id.equals(retainedFailure) && count == 1 && bytes > 0 && bytes <= 20L * 1024 * 1024) return;
            if (count != 0 || bytes != 0 || !retainedFailure.isEmpty() && id.equals(retainedFailure)) {
                throw failure("MIGRATION_QUEUE_NOT_EMPTY", "全部连接队列须确认为空");
            }
        });
        if (!retainedFailure.isEmpty() && !connections.containsKey(retainedFailure)) {
            throw failure("MIGRATION_QUEUE_NOT_EMPTY", "所选失败队列不在当前原生流程中");
        }
        return new Graph(processors, connections, states, funnels);
    }

    static ObjectNode stableComponent(JsonNode entity) {
        ObjectNode component = entity.path("component").deepCopy();
        component.remove(List.of("validationStatus", "validationErrors", "referencingComponents"));
        if (component.path("config") instanceof ObjectNode config) config.remove("descriptors");
        return component;
    }

    static Map<String, JsonNode> indexed(JsonNode values) {
        if (!values.isArray()) throw failure("MIGRATION_UNAVAILABLE", "原生组件范围不可核验");
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode value : values) {
            String id = value.path("id").asText();
            if (id.isBlank() || result.putIfAbsent(id, value) != null) {
                throw failure("MIGRATION_UNAVAILABLE", "原生组件标识不可核验");
            }
        }
        return result;
    }

    static long revision(JsonNode entity) {
        JsonNode version = entity.path("revision").path("version");
        if (!version.isIntegralNumber() || version.asLong() < 0) {
            throw failure("MIGRATION_UNAVAILABLE", "原生组件版本不可核验");
        }
        return version.asLong();
    }

    private static MappingException failure(String code, String message) {
        return new MappingException(code, message);
    }
}
