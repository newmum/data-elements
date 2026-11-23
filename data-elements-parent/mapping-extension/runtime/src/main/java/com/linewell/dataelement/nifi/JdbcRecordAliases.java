package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.mapping.core.ValueTypes;
import org.apache.nifi.serialization.SimpleRecordSchema;
import org.apache.nifi.serialization.record.RecordField;
import org.apache.nifi.serialization.record.RecordSchema;
import java.util.*;

/** Restore registered literal names after JDBC's mandatory Avro schema boundary.
 * Only top-level keys change: no parsing, flattening or value conversion occurs. */
final class JdbcRecordAliases {
    private static final ObjectMapper JSON = new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    static final JdbcRecordAliases NONE = new JdbcRecordAliases(Map.of());
    private final Map<String, String> originalByWire;

    private JdbcRecordAliases(Map<String, String> originalByWire) { this.originalByWire = Map.copyOf(originalByWire); }

    static JdbcRecordAliases parse(String text) {
        if (text == null || text.isBlank()) return NONE;
        if (text.length() > 1_048_576) throw invalid();
        try {
            JsonNode root = JSON.readTree(text);
            if (!root.isObject() || root.size() > 10_000) throw invalid();
            var reversed = new LinkedHashMap<String, String>();
            var originals = new HashSet<String>();
            var entries = root.fields();
            while (entries.hasNext()) {
                var entry = entries.next();
                String original = entry.getKey(), wire = entry.getValue().asText();
                if (!entry.getValue().isTextual() || !original.equals(ValueTypes.field(original))
                        || !wire.matches("_ht_[0-9a-f]{16}(?:_[0-9]+)?") || wire.length() > 128
                        || reversed.putIfAbsent(wire, original) != null) throw invalid();
                originals.add(original);
            }
            if (reversed.keySet().stream().anyMatch(originals::contains)) throw invalid();
            return reversed.isEmpty() ? NONE : new JdbcRecordAliases(reversed);
        } catch (Exception exception) { throw invalid(); }
    }

    RecordSchema restore(RecordSchema schema) {
        if (originalByWire.isEmpty()) return schema;
        var fields = new ArrayList<RecordField>();
        var names = new HashSet<String>();
        for (RecordField field : schema.getFields()) {
            String name = originalByWire.getOrDefault(field.getFieldName(), field.getFieldName());
            if (!names.add(name)) throw collision();
            fields.add(name.equals(field.getFieldName()) ? field : new RecordField(name, field.getDataType(),
                    field.getDefaultValue(), field.getAliases(), field.isNullable()));
        }
        // Do not retain the old Avro schema text with its wire field names.
        return new SimpleRecordSchema(fields);
    }

    Map<String, Object> restore(Map<String, Object> row) {
        if (originalByWire.isEmpty()) return row;
        var restored = new LinkedHashMap<String, Object>();
        for (var entry : row.entrySet()) {
            String name = originalByWire.getOrDefault(entry.getKey(), entry.getKey());
            if (restored.containsKey(name)) throw collision();
            restored.put(name, entry.getValue());
        }
        return restored;
    }

    private static MappingException invalid() { return new MappingException("INVALID_FIELD_ALIASES", "JDBC字段传输映射无效，请从登记字段重新部署"); }
    private static MappingException collision() { return new MappingException("AMBIGUOUS_FIELD", "JDBC传输名与原字段同时存在，已保留输入，未覆盖字段"); }
}
