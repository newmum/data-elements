package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.mapping.core.MappingException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registration is optional; explicit registered resources still require authorization. */
public final class PipelineResourceReferences {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> SOURCES = List.of("registeredDatasourceId", "selectedDatabaseId", "sourceDbId",
            "targetDbId", "dbId", "datasourceId", "dataSourceId", "source_db_id", "target_db_id", "datasource_id");
    private static final List<String> TABLES = List.of("sourceTableId", "targetTableId", "registeredTableId",
            "tableId", "source_table_id", "target_table_id", "table_id");

    private PipelineResourceReferences() { }

    public record References(Set<String> datasourceIds, Set<String> tableIds) { }

    public static References from(Pipeline pipeline) {
        References result = new References(new LinkedHashSet<>(), new LinkedHashSet<>());
        if (pipeline.dsl() != null && pipeline.dsl().nodes() != null) for (var node : pipeline.dsl().nodes()) {
            if (node != null && node.config() != null) collect(JSON.valueToTree(node.config()), result, 0);
        }
        return result;
    }

    public static boolean standalone(Pipeline pipeline, Map<String, Object> task) {
        if (!text(task, "source_table_id").isBlank()) return false;
        if (pipeline.dsl() != null && pipeline.dsl().nodes() != null) for (var node : pipeline.dsl().nodes()) {
            if (node != null && "source".equals(node.category()) && node.config() != null
                    && List.of("sourceTableId", "registeredTableId", "tableId").stream()
                    .anyMatch(key -> !text(node.config(), key).isBlank())) return false;
        }
        return true;
    }

    /** Legacy canvas task rows sometimes stored a JDBC URL/table name in registration-ID columns. */
    public static void addTask(Pipeline pipeline, Map<String, Object> task, References references) {
        for (String key : List.of("source_db_id", "target_db_id")) {
            String value = text(task, key);
            if (value.startsWith("jdbc:") && inlineValue(pipeline, value, List.of("jdbcUrl", "jdbcURL"))) continue;
            add(references.datasourceIds(), value);
        }
        add(references.tableIds(), text(task, "source_table_id"));
        String target = text(task, "target_table_id");
        if (!standalone(pipeline, task) || !inlineValue(pipeline, target, List.of("table", "tableName")))
            add(references.tableIds(), target);
    }

    private static boolean inlineValue(Pipeline pipeline, String value, List<String> keys) {
        if (value.isBlank() || pipeline.dsl() == null || pipeline.dsl().nodes() == null) return false;
        return pipeline.dsl().nodes().stream().filter(node -> node != null && node.config() != null)
                .anyMatch(node -> keys.stream().anyMatch(key -> value.equals(text(node.config(), key))));
    }

    private static void collect(JsonNode node, References references, int depth) {
        if (depth > 32) throw new MappingException("BUDGET_EXCEEDED", "流程资源引用嵌套过深");
        if (node.isArray()) { node.forEach(child -> collect(child, references, depth + 1)); return; }
        if (!node.isObject()) return;
        for (String key : SOURCES) if (node.hasNonNull(key)) add(references.datasourceIds(), node.path(key).asText());
        for (String key : TABLES) if (node.hasNonNull(key)) add(references.tableIds(), node.path(key).asText());
        node.fields().forEachRemaining(entry -> {
            if (entry.getKey().equals("value") && Set.of("LITERAL", "CONSTANT").contains(node.path("kind").asText())) return;
            if (entry.getKey().equals("defaultValue") && (node.has("ruleId") || node.has("stepId") || node.has("onError") || node.has("missPolicy"))) return;
            if (Set.of("sampleRows", "snapshots").contains(entry.getKey()) && node.has("schemaVersion")) return;
            JsonNode child = entry.getValue();
            if (entry.getKey().equals("mappings") && child.isTextual()) {
                try { child = JSON.readTree(child.asText()); }
                catch (Exception invalid) { throw new MappingException("INVALID_JSON", "字段规则JSON无效"); }
            }
            if (child != null && child.isContainerNode()) collect(child, references, depth + 1);
        });
    }

    private static void add(Set<String> values, String value) { if (!value.isBlank()) values.add(value); }
    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        String text = value == null ? "" : value.toString().trim();
        return text.equals("null") ? "" : text;
    }
}
