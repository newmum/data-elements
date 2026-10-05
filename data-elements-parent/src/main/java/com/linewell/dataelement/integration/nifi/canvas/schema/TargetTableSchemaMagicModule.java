package com.linewell.dataelement.integration.nifi.canvas.schema;

import com.linewell.dataelement.model.common.BizException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** Safely extends existing materialized target tables with generated fields. */
@Component
@MagicModule("targetTableSchema")
public class TargetTableSchemaMagicModule {

    @Comment("Ensure nullable VARCHAR translation columns exist on a materialized target table")
    public List<String> ensureTranslationColumns(Map<String, Object> connectionConfig,
                                                  String tableName,
                                                  Collection<String> columnNames) {
        if (columnNames == null || columnNames.isEmpty()) {
            return List.of();
        }
        String jdbcUrl = text(connectionConfig, "jdbcUrl");
        String username = text(connectionConfig, "username");
        String password = text(connectionConfig, "password");
        if (jdbcUrl.isBlank() || username.isBlank()) {
            throw new BizException(400, "Target JDBC connection is incomplete");
        }
        QualifiedName table = qualifiedName(tableName, "target table");
        Set<String> requested = new LinkedHashSet<>();
        for (String columnName : columnNames) {
            if (columnName != null && !columnName.isBlank()) {
                requested.add(identifier(columnName, "target column"));
            }
        }
        if (requested.isEmpty()) {
            return List.of();
        }

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            Set<String> existing = loadColumns(connection, table);
            List<String> added = new ArrayList<>();
            for (String column : requested) {
                if (existing.contains(column.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(addColumnSql(connection, jdbcUrl, table, column));
                }
                existing.add(column.toLowerCase(Locale.ROOT));
                added.add(column);
            }
            return List.copyOf(added);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "Failed to add target translation columns: " + rootMessage(e));
        }
    }

    private Set<String> loadColumns(Connection connection, QualifiedName table) throws Exception {
        Set<String> columns = new LinkedHashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        List<String> schemas = new ArrayList<>();
        schemas.add(table.schema());
        schemas.add(table.schema().toUpperCase(Locale.ROOT));
        schemas.add(null);
        List<String> names = List.of(table.name(), table.name().toUpperCase(Locale.ROOT));
        for (String schema : schemas) {
            for (String name : names) {
                try (ResultSet rows = metadata.getColumns(null, schema, name, null)) {
                    while (rows.next()) {
                        String column = rows.getString("COLUMN_NAME");
                        if (column != null) {
                            columns.add(column.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
        }
        return columns;
    }

    private String addColumnSql(Connection connection, String jdbcUrl, QualifiedName table, String column) throws Exception {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        // Materialized tables are created with unquoted identifiers. Keep the
        // same convention here so Oracle/Dameng resolve their uppercase names.
        String physicalTable = table.schema().isBlank() ? table.name() : table.schema() + "." + table.name();
        String normalizedUrl = jdbcUrl == null ? "" : jdbcUrl.toLowerCase(Locale.ROOT);
        boolean oracleStyle = product.contains("oracle") || normalizedUrl.contains(":oracle:");
        boolean parenthesizedAdd = oracleStyle || product.contains("dameng");
        String columnType = oracleStyle ? "VARCHAR2(255)" : "VARCHAR(255)";
        if (parenthesizedAdd) {
            return "ALTER TABLE " + physicalTable + " ADD (" + column + " " + columnType + ")";
        }
        return "ALTER TABLE " + physicalTable + " ADD COLUMN " + column + " VARCHAR(255)";
    }

    private QualifiedName qualifiedName(String value, String label) {
        String[] parts = value == null ? new String[0] : value.trim().split("\\.");
        if (parts.length < 1 || parts.length > 2) {
            throw new BizException(400, "Invalid " + label);
        }
        if (parts.length == 1) {
            return new QualifiedName("", identifier(parts[0], label));
        }
        return new QualifiedName(identifier(parts[0], label), identifier(parts[1], label));
    }

    private String identifier(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z_][A-Za-z0-9_$#]*")) {
            throw new BizException(400, "Invalid " + label);
        }
        return normalized;
    }

    private String text(Map<String, Object> config, String key) {
        Object value = config == null ? null : config.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? error.getClass().getSimpleName() : current.getMessage();
    }

    private record QualifiedName(String schema, String name) { }
}
