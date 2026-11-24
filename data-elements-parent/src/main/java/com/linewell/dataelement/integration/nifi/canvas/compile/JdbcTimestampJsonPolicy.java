package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.mapping.core.ValueTypes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Select an existing root codec for registered JDBC timestamps without changing public services. */
public final class JdbcTimestampJsonPolicy {
    public static final String WRITER_NAME = "PUBLIC-WRITER-JSON-TIMESTAMP9";
    public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSSSSSSSS";

    private JdbcTimestampJsonPolicy() { }

    /** Read only: the environment must already provide this distinct, enabled root service. */
    public static JsonNode requireWriter(NifiClient nifi) {
        String root = nifi.get("/flow/process-groups/root", JsonNode.class).path("processGroupFlow").path("id").asText();
        if (root.isBlank()) throw new IllegalStateException("无法确认 NiFi 根作用域，未修改时间编解码引用");
        var services = nifi.listControllerServices(root);
        String id = GlobalJsonRecordServices.require(services, root, WRITER_NAME, "org.apache.nifi.json.JsonRecordSetWriter");
        JsonNode service = services.stream().filter(row -> id.equals(row.path("component").path("id").asText(row.path("id").asText())))
                .findFirst().orElseThrow();
        requireFormat(service);
        return service;
    }

    static void requireFormat(JsonNode service) {
        JsonNode properties = service.path("component").path("properties");
        if (!TIMESTAMP_FORMAT.equals(properties.path("Timestamp Format").asText())
                || !"inherit-record-schema".equals(properties.path("Schema Access Strategy").asText()))
            throw new IllegalStateException("JDBC完整时间精度需要根服务 " + WRITER_NAME + " 使用继承Schema和9位小数秒格式；未修改公共服务");
    }

    static Set<String> writerNodes(Pipeline pipeline) {
        if (pipeline == null || pipeline.dsl() == null || pipeline.dsl().nodes() == null) return Set.of();
        var selected = new LinkedHashSet<String>();
        var outgoing = new HashMap<String, List<String>>();
        if (pipeline.dsl().edges() != null) pipeline.dsl().edges().forEach(edge ->
                outgoing.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge.target()));
        var pending = new ArrayDeque<String>();
        for (var node : pipeline.dsl().nodes()) {
            if ("source".equals(node.category()) && node.manifestKey().startsWith("source.")
                    && hasTimestamps(node.config()) && selected.add(node.id())) pending.add(node.id());
        }
        while (!pending.isEmpty()) for (String next : outgoing.getOrDefault(pending.removeFirst(), List.of()))
            if (selected.add(next)) pending.add(next);
        return Set.copyOf(selected);
    }

    static boolean hasTimestamps(Map<String, ?> config) {
        if (config == null || !(config.get("sourceColumns") instanceof List<?> fields)) return false;
        String code = String.valueOf(config.getOrDefault("dbType", null));
        com.linewell.dataelement.metautil.model.enums.DatabaseType database;
        try { database = com.linewell.dataelement.metautil.model.enums.DatabaseType.fromCode(code); }
        catch (IllegalArgumentException unsupported) { return false; }
        if (database == null || !database.isRelational()) return false;
        for (Object raw : fields) if (raw instanceof Map<?, ?> field) {
            String type = "";
            for (String key : List.of("sourceColumnType", "columnType", "column_type", "typeName", "dataType", "data_type", "type"))
                if (field.get(key) != null && !field.get(key).toString().isBlank()) { type = field.get(key).toString(); break; }
            if(database==com.linewell.dataelement.metautil.model.enums.DatabaseType.SQLSERVER
                    &&type.trim().matches("(?i)^(timestamp|rowversion)$"))continue;
            if (Set.of("DATETIME", "INSTANT").contains(ValueTypes.type(type))) return true;
        }
        return false;
    }
}
