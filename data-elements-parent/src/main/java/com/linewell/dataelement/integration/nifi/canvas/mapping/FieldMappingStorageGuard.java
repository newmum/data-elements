package com.linewell.dataelement.integration.nifi.canvas.mapping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.ssssssss.magicapi.modules.db.SQLModule;

/** Checks the current tenant's real rule column before any task or canvas is written. */
public final class FieldMappingStorageGuard {
    private FieldMappingStorageGuard() {}

    /** Returns a safe, actionable message, never rule contents or connection details. */
    public static String validate(SQLModule db, String tenantId, List<Map<String,Object>> mappings) {
        if (mappings == null || mappings.stream().noneMatch(row -> row.get("func_value") != null
                && !row.get("func_value").toString().isEmpty())) return null;
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("Missing tenant context");
        // SQLModule exposes this accessor to Java, not to the Magic script method dispatcher.
        DataSource source = db.getJdbcTemplate().getDataSource();
        Connection connection = DataSourceUtils.getConnection(source);
        long capacity;
        String type;
        try {
            String quote = connection.getMetaData().getIdentifierQuoteString().trim();
            String sql = "SELECT " + quote + "func_value" + quote + " FROM " + quote
                    + "data_access_field_mapping" + quote + " WHERE " + quote + "tenant_id" + quote + " = ? AND 1 = 0";
            // One zero-row query for the whole batch. Exact table resolution preserves the
            // routing datasource/schema; the query cannot read another tenant's business rows.
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, tenantId);
                statement.setQueryTimeout(15);
                try (ResultSet rows = statement.executeQuery()) {
                    capacity = rows.getMetaData().getPrecision(1);
                    type = rows.getMetaData().getColumnTypeName(1).toLowerCase(Locale.ROOT);
                }
            }
        } catch (Exception error) {
            throw new IllegalStateException("无法检查当前租户的字段映射存储结构", error);
        } finally {
            DataSourceUtils.releaseConnection(connection, source);
        }
        boolean byteLimited = List.of("tinytext", "text", "mediumtext", "longtext").contains(type);
        // Connector/J reports TEXT precision in characters (16383 for utf8mb4),
        // while MySQL enforces the native TEXT type's byte limit.
        capacity = switch (type) {
            case "tinytext" -> 255L;
            case "text" -> 65535L;
            case "mediumtext" -> 16777215L;
            case "longtext" -> 4294967295L;
            default -> capacity;
        };
        if (capacity <= 0 && !type.contains("clob")) {
            return "当前租户的字段映射存储容量无法确认，任务未保存，请检查func_value列定义";
        }
        for (Map<String,Object> mapping : mappings) {
            String value = mapping.get("func_value") == null ? "" : mapping.get("func_value").toString();
            int size = byteLimited ? value.getBytes(StandardCharsets.UTF_8).length : value.codePointCount(0, value.length());
            if (capacity > 0 && size > capacity) {
                return "字段“" + mapping.get("target_field") + "”的完整映射规则超出当前租户func_value容量（"
                        + type + "，需要" + size + "，可用" + capacity + (byteLimited ? "字节" : "字符")
                        + "）。请按数据库结构基线升级为足够容量的大文本列后重试；规则未截断，任务和画布未保存";
            }
        }
        return null;
    }
}
