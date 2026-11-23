package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.metautil.ddl.DdlDialect;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.mapping.core.JdbcTemporalTypes;
import com.linewell.dataelement.mapping.core.ValueTypes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HexFormat;

/** Keep registered source field identities across JDBC metadata and complex-value representations. */
final class JdbcSourceProjection {
    private JdbcSourceProjection() { }

    static String columns(Map<String, Object> config) {
        String type = String.valueOf(config.getOrDefault("dbType", "")).replaceAll("[\\s_-]", "").toLowerCase(Locale.ROOT);
        boolean clickHouse = List.of("clickhouse", "ch", "mrsclickhouse").contains(type);
        boolean hiveAliases = type.equals("hive") && "open-source".equalsIgnoreCase(String.valueOf(config.get("hiveConnectionMode")));
        Map<String, String> aliases = aliases(config);
        if ((!clickHouse && !hiveAliases && aliases.isEmpty())
                || !(config.get("sourceColumns") instanceof List<?> columns) || columns.isEmpty()) return "";
        DatabaseType database = DatabaseType.fromCode(type);
        var projections = new ArrayList<String>();
        var names = new HashSet<String>();
        boolean complex = false;
        for (Object raw : columns) {
            if (!(raw instanceof Map<?, ?> field)) throw new IllegalStateException("JDBC来源字段快照不完整，请重新确认登记字段");
            String name = first(field, "columnName", "column_name", "name");
            if (name.isBlank() || !names.add(name)) throw new IllegalStateException("JDBC来源字段快照有空名称或重复字段");
            String quoted = DdlDialect.quote(database, name);
            String output = DdlDialect.quote(database, aliases.getOrDefault(name, name));
            String nativeType = first(field, "sourceColumnType", "columnType", "column_type", "typeName", "dataType", "data_type", "type");
            if (clickHouse && isComplex(nativeType)) {
                // SQL NULL stays NULL; empty maps/arrays retain their native JSON representation.
                projections.add("if(isNull(" + quoted + "), NULL, toJSONString(" + quoted + ")) AS " + output);
                complex = true;
            } else if (!aliases.isEmpty() && !Boolean.TRUE.equals(config.get("_runtimeHiveTargetInput"))
                    && JdbcTemporalTypes.oracleDateHasTime(config) && ValueTypes.type(nativeType).equals("DATE")) {
                projections.add("CAST(" + quoted + " AS TIMESTAMP) AS " + output);
            } else projections.add(hiveAliases || aliases.containsKey(name) ? quoted + " AS " + output : quoted);
        }
        return complex || hiveAliases || !aliases.isEmpty() ? String.join(",", projections) : "";
    }

    /** NiFi JDBC creates an Avro schema even when the configured output is JSON.
     * Derive reversible wire names from the already loaded registered snapshot;
     * reserve all physical names first so a real column cannot be overwritten. */
    static Map<String, String> aliases(Map<String, Object> config) {
        String code = String.valueOf(config.getOrDefault("dbType", ""));
        if (code.isBlank()) return Map.of();
        DatabaseType database;
        try { database = DatabaseType.fromCode(code); }
        catch (IllegalArgumentException unsupported) { return Map.of(); }
        if (database == null || !database.isRelational()
                || !(config.get("sourceColumns") instanceof List<?> columns)) return Map.of();
        var names = new ArrayList<String>();
        var physical = new HashSet<String>();
        var reserved = new HashSet<String>();
        for (Object raw : columns) {
            if (!(raw instanceof Map<?, ?> field)) throw new IllegalStateException("JDBC来源字段快照不完整，请重新确认登记字段");
            String name = first(field, "columnName", "column_name", "name");
            if (name.isBlank() || !physical.add(name)) throw new IllegalStateException("JDBC来源字段快照有空名称或重复字段");
            names.add(name); reserved.add(name.toLowerCase(Locale.ROOT));
        }
        var aliases = new LinkedHashMap<String, String>();
        for (String name : names) {
            if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) continue;
            try {
                String base = "_ht_" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(name.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
                String wire = base;
                for (int suffix = 1; !reserved.add(wire.toLowerCase(Locale.ROOT)); suffix++) wire = base + "_" + suffix;
                aliases.put(name, wire);
            } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        }
        return aliases;
    }

    private static boolean isComplex(String type) {
        String value = type.trim();
        for (int i = 0; i < 8 && value.matches("(?is)^(Nullable|LowCardinality)\\s*\\(.*\\)$"); i++)
            value = value.substring(value.indexOf('(') + 1, value.length() - 1).trim();
        return value.matches("(?is)^(Map|Array)\\s*\\(.*\\)$");
    }

    private static String first(Map<?, ?> field, String... keys) {
        for (String key : keys) if (field.get(key) != null && !field.get(key).toString().isBlank()) return field.get(key).toString().trim();
        return "";
    }
}
