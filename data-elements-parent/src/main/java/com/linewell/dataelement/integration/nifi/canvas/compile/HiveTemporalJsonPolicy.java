package com.linewell.dataelement.integration.nifi.canvas.compile;

import java.util.Locale;
import java.util.Map;

/** Public JSON service names and Hive connection recognition. Codec settings belong to NiFi. */
final class HiveTemporalJsonPolicy {
    static final String READER_NAME = "PUBLIC-READER-JSON";
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

}
