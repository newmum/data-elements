package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/** Builds one native ExecuteGroovyScript for record translation across JDBC dialects. */
final class RecordLookupScript {
    static final String PROCESSOR_TYPE = "org.apache.nifi.processors.groovyx.ExecuteGroovyScript";
    private RecordLookupScript() {}

    static Map<String, String> properties(List<FieldMappingService.LookupPlan> lookups,
                                          String reader, String writer, Function<FieldMappingService.LookupPlan, String> service) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("RecordReader.input", reader);
        properties.put("RecordWriter.output", writer);
        Map<String, String> services = new LinkedHashMap<>();
        List<Map<String, Object>> rules = new ArrayList<>();
        for (var lookup : lookups) {
            var rule = lookup.recordLookup();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("source", lookup.parameterRecordFields().getFirst());
            data.put("target", lookup.targetField());
            data.put("separator", rule.separator());
            data.put("onMissing", lookup.onMissing());
            data.put("values", rule.values());
            data.put("query", rule.query());
            if (!rule.inline()) {
                String id = Objects.requireNonNull(service.apply(lookup), "字典连接服务未创建");
                String alias = services.computeIfAbsent(id, ignored -> "dictionary" + services.size());
                properties.put("SQL." + alias, id);
                data.put("service", alias);
            }
            rules.add(data);
        }
        try (var input = RecordLookupScript.class.getResourceAsStream("/nifi/multi-value-translation.groovy")) {
            if (input == null) throw new IllegalStateException("多值翻译脚本未打包");
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            String json = new ObjectMapper().writeValueAsString(rules);
            properties.put("Script Body", script.replace("__RULES_BASE64__",
                    Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8))));
            return properties;
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成多值记录翻译脚本", exception);
        }
    }
}
