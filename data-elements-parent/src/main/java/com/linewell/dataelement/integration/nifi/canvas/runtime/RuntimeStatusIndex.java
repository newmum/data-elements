package com.linewell.dataelement.integration.nifi.canvas.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** One recursive NiFi response, indexed once; inspecting rows never performs I/O. */
public final class RuntimeStatusIndex {
    private static final Set<String> KNOWN_STATES = Set.of("RUNNING", "STARTING", "STOPPED", "DISABLED", "INVALID", "TRANSMITTING", "NOTTRANSMITTING");
    public record Group(String state, boolean running, int invalidCount, JsonNode snapshot) {
        public int taskStatus() { return state.equals("INVALID") || state.equals("RUN_ERROR") ? 2 : running ? 1 : state.equals("UNKNOWN") ? 3 : 0; }
    }
    private final Map<String, Group> groups;
    private final String revision;
    private RuntimeStatusIndex(Map<String, Group> groups) {
        this.groups = Collections.unmodifiableMap(groups);
        StringBuilder value = new StringBuilder();
        new TreeMap<>(groups).forEach((id, group) -> value.append(id).append(':').append(group.state()).append(':').append(group.invalidCount()).append(';'));
        try { revision = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static RuntimeStatusIndex parse(JsonNode response) {
        JsonNode root = response.path("processGroupStatus").path("aggregateSnapshot");
        if (!root.isObject() || root.path("id").asText().isBlank()) throw new IllegalStateException("NiFi 状态响应缺少根流程快照");
        Map<String, Group> result = new LinkedHashMap<>();
        visit(root, result, 0, new int[]{0});
        return new RuntimeStatusIndex(result);
    }
    private static Group visit(JsonNode node, Map<String, Group> result, int depth, int[] processors) {
        if (depth > 64 || result.size() >= 10000) throw new IllegalStateException("NiFi 状态树超过安全读取上限");
        String id = node.path("id").asText();
        if (id.isBlank() || result.containsKey(id)) throw new IllegalStateException("NiFi 状态树包含无效或重复流程标识");
        result.put(id, null);
        boolean running = false, known = false, unknown = false;
        int invalid = 0;
        for (String collection : List.of("processorStatusSnapshots", "inputPortStatusSnapshots", "outputPortStatusSnapshots", "remoteProcessGroupStatusSnapshots")) {
          for (JsonNode item : node.path(collection)) {
            if (++processors[0] > 100000) throw new IllegalStateException("NiFi 组件数量超过安全读取上限");
            String wrapper = collection.substring(0, collection.length() - 1);
            JsonNode p = item.path(wrapper);
            String state = p.path("runStatus").asText(p.path("transmissionStatus").asText("")).toUpperCase(Locale.ROOT).replace(" ", "");
            if ((item.has("canRead") && !item.path("canRead").asBoolean())
                    || !KNOWN_STATES.contains(state)) { unknown = true; continue; }
            known = true;
            if (state.equals("RUNNING") || state.equals("STARTING") || state.equals("TRANSMITTING")) running = true;
            if (state.equals("INVALID")) invalid++;
        }
        }
        for (JsonNode item : node.path("processGroupStatusSnapshots")) {
            JsonNode child = item.path("processGroupStatusSnapshot");
            if (!child.isObject() || child.path("id").asText().isBlank()) { unknown = true; continue; }
            if (result.size() >= 10000 || result.containsKey(child.path("id").asText())) throw new IllegalStateException("NiFi 状态树超过上限或包含重复流程标识");
            Group group;
            if (item.has("canRead") && !item.path("canRead").asBoolean()) {
                group = new Group("UNKNOWN", false, 0, child); result.put(child.path("id").asText(), group);
            } else group = visit(child, result, depth + 1, processors);
            known = true; running |= group.running(); invalid += group.invalidCount(); unknown |= group.state().equals("UNKNOWN");
        }
        String state = running ? invalid > 0 ? "RUN_ERROR" : "RUNNING" : count(node.path("activeThreadCount")) > 0 ? "STOPPING"
                : unknown ? "UNKNOWN" : invalid > 0 ? "INVALID" : known ? "STOPPED" : "EMPTY";
        Group group = new Group(state, running || state.equals("STOPPING"), invalid, node);
        result.put(id, group);
        return group;
    }
    private static long count(JsonNode value) { try { return Long.parseLong(value.asText("0").replace(",", "").split("/")[0].trim()); } catch (Exception e) { return 0; } }
    public Map<String, Group> groups() { return groups; }
    public String revision() { return revision; }
    public JsonNode rawStatus(String groupId) {
        Group group = groups.get(groupId);
        return group == null ? null : JsonNodeFactory.instance.objectNode().set("processGroupStatus",
                JsonNodeFactory.instance.objectNode().set("aggregateSnapshot", group.snapshot()));
    }
}
