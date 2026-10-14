package com.linewell.dataelement.integration.nifi.canvas.compile;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Matching JSON codecs for JDBC temporal values read from Hive. */
final class HiveTemporalJsonPolicy {
    static final String WRITER_NAME = "PUBLIC-WRITER-JSON-TIME_FORMAT";

    private HiveTemporalJsonPolicy() {}

    static boolean isHive(String manifestKey, Map<String, ?> config) {
        if ("source.hive".equals(manifestKey)) return true;
        if (config == null) return false;
        Object type = config.get("dbType");
        if (type != null && "HIVE".equals(type.toString().trim().toUpperCase(Locale.ROOT))) return true;
        Object url = config.get("jdbcUrl");
        if (url == null) return false;
        String normalized = url.toString().trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("jdbc:hive2:") || normalized.startsWith("jdbc:hive:");
    }

    static Map<String, String> formats() {
        return Map.of("Date Format", "yyyy-MM-dd", "Time Format", "HH:mm:ss",
                "Timestamp Format", "yyyy-MM-dd HH:mm:ss.SSSSSSSSS");
    }

    static Map<String, String> writerProperties() {
        Map<String, String> result = new LinkedHashMap<>(formats());
        result.put("Schema Write Strategy", "full-schema-attribute");
        return result;
    }

    static Map<String, String> typedReaderProperties(boolean hive) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("Schema Access Strategy", "schema-text-property");
        result.put("Schema Text", "${avro.schema}");
        if (hive) result.putAll(formats());
        return result;
    }
}
