package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Types;
import java.util.*;
import java.util.regex.Pattern;

/** Database-independent token handling; dictionaries are read in bounded JDBC batches. */
public final class MultiValueTranslation {
    public static final int RECORD_BATCH_SIZE = 100;
    public static final int CODE_BATCH_SIZE = 500;
    private MultiValueTranslation() {}

    public record Rule(String separator, Map<String, String> values, String query) {
        public boolean inline() { return query == null || query.isBlank(); }
    }

    public static boolean isRecordLookup(JsonNode lookup) {
        return lookup.path("multiValue").asBoolean(false)
                && (lookup.path("values").isObject() || lookup.hasNonNull("query"));
    }

    public static Rule rule(JsonNode lookup) {
        String separator = lookup.path("multiValueSeparator").asText(",");
        if (separator.isEmpty() || separator.length() > 8) {
            throw new IllegalStateException("多值翻译分隔符须为 1–8 个字符");
        }
        Map<String, String> values = new LinkedHashMap<>();
        if (lookup.path("values").isObject()) {
            lookup.path("values").fields().forEachRemaining(entry -> {
                if (!entry.getValue().isNull()) values.put(entry.getKey(), entry.getValue().asText());
            });
        }
        String query = lookup.path("query").asText("").trim();
        if (!query.isBlank()) {
            // The marker is expanded into bound parameters, never into raw input codes.
            if (!query.regionMatches(true, 0, "SELECT ", 0, 7)
                    || query.indexOf(":codes") < 0 || query.indexOf(":codes") != query.lastIndexOf(":codes")
                    || query.contains(";") || query.contains("--") || query.contains("/*")
                    || query.contains("?") || query.contains("${")) {
                throw new IllegalStateException("多值字典查询须为包含一个 :codes 参数列表的 SELECT");
            }
        } else if (values.isEmpty()) {
            throw new IllegalStateException("多值翻译缺少已登记的编码映射或字典查询");
        }
        return new Rule(separator, Collections.unmodifiableMap(values), query);
    }

    public static List<String> tokens(Object raw, String separator) {
        if (raw == null) return List.of();
        return Arrays.stream(String.valueOf(raw).split(Pattern.quote(separator), -1))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    /** Retains the qualified table and category/status filters of the old generated query. */
    public static String legacyDictionaryQuery(String sql, String table, String key, String label) {
        if (sql != null && sql.toUpperCase(Locale.ROOT).contains("JSON_TABLE")) {
            var matcher = Pattern.compile("(?is)\\(SELECT\\s+[^()]+?\\s+AS\\s+dict_code\\s*,\\s*MAX\\([^()]+?\\)"
                    + "\\s+AS\\s+dict_name\\s+FROM\\s+(.+?)\\s+GROUP\\s+BY\\s+[^()]+?\\)\\s+dict\\s+ON").matcher(sql);
            if (matcher.find()) {
                String from = matcher.group(1).trim();
                return "SELECT " + key + ", " + label + " FROM " + from
                        + (from.toUpperCase(Locale.ROOT).contains(" WHERE ") ? " AND " : " WHERE ")
                        + key + " IN (:codes)";
            }
            throw new IllegalStateException("旧多值字典配置缺少完整关联信息，请重新保存该字段的字典关联");
        }
        return "SELECT " + key + ", " + label + " FROM " + table + " WHERE " + key + " IN (:codes)";
    }

    public static String translate(Object raw, Rule rule, Map<String, String> dictionary) {
        List<String> tokens = tokens(raw, rule.separator());
        boolean matched = tokens.stream().anyMatch(code -> {
            String label = dictionary.get(code);
            return label != null && !label.isEmpty();
        });
        if (!matched) return null;
        return String.join(rule.separator(), tokens.stream().map(code -> {
            String label = dictionary.get(code);
            return label == null || label.isEmpty() ? code : label;
        }).toList());
    }

    /** The zero-row query also supplies the real key type for PostgreSQL/numeric dictionaries. */
    public static Map<String, String> dictionary(Connection connection, Rule rule, Collection<String> codes) throws Exception {
        if (rule.inline()) return rule.values();
        if (codes.isEmpty()) return Map.of();
        int keyType;
        try (var statement = connection.prepareStatement(rule.query().replace(":codes", "NULL"))) {
            statement.setQueryTimeout(60);
            try (var rows = statement.executeQuery()) { keyType = rows.getMetaData().getColumnType(1); }
        }
        List<Object> parameters = new ArrayList<>();
        Map<String, List<String>> originals = new LinkedHashMap<>();
        for (String code : new LinkedHashSet<>(codes)) {
            Object value = parameter(code, keyType);
            if (value != null) {
                parameters.add(value);
                originals.computeIfAbsent(canonical(value), ignored -> new ArrayList<>()).add(code);
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int offset = 0; offset < parameters.size(); offset += CODE_BATCH_SIZE) {
            List<Object> batch = parameters.subList(offset, Math.min(offset + CODE_BATCH_SIZE, parameters.size()));
            String sql = rule.query().replace(":codes", String.join(",", Collections.nCopies(batch.size(), "?")));
            try (var statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(60);
                for (int index = 0; index < batch.size(); index++) statement.setObject(index + 1, batch.get(index));
                try (var rows = statement.executeQuery()) {
                    int count = 0;
                    while (rows.next()) {
                        if (++count > 100_000) throw new IllegalStateException("字典编码重复记录过多，请检查字典数据");
                        String code = rows.getString(1), label = rows.getString(2);
                        if (code != null && label != null) {
                            Object nativeCode = parameter(code, keyType);
                            for (String original : originals.getOrDefault(canonical(nativeCode), List.of())) {
                                values.merge(original, label, (left, right) -> left.compareTo(right) >= 0 ? left : right);
                            }
                        }
                    }
                }
            }
        }
        return values;
    }

    private static Object parameter(String code, int type) {
        try {
            return switch (type) {
                case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                     Types.NUMERIC, Types.DECIMAL, Types.FLOAT, Types.REAL, Types.DOUBLE -> new BigDecimal(code);
                case Types.BOOLEAN, Types.BIT -> switch (code.toLowerCase(Locale.ROOT)) {
                    case "true", "1" -> true;
                    case "false", "0" -> false;
                    default -> null;
                };
                default -> code;
            };
        } catch (NumberFormatException ignored) {
            // A nonnumeric token in a numeric dictionary is an unknown code, not a query failure.
            return null;
        }
    }

    private static String canonical(Object value) {
        return value instanceof BigDecimal number ? number.stripTrailingZeros().toPlainString() : String.valueOf(value);
    }
}
