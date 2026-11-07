package com.linewell.dataelement.platform.audit.infrastructure.magic;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Caches the audit columns that are actually present on a table in the current
 * tenant/control datasource.  This lets the common write interceptor support
 * both the current {@code created_*} convention and older {@code create_*}
 * tables without making an assumption about every legacy table's schema.
 */
@Slf4j
@Component
public class AuditTableRegistry {

    private final DataSource dataSource;
    private final Map<TableKey, AuditColumns> tables = new ConcurrentHashMap<>();

    public AuditTableRegistry(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public AuditColumns columnsOf(String rawTableName) {
        TableName table = TableName.parse(rawTableName);
        if (table == null) {
            return AuditColumns.NONE;
        }
        String scope = TenantContext.usesControlDatabase()
                ? "control"
                : "tenant:" + String.valueOf(TenantContext.getTenantId());
        return tables.computeIfAbsent(new TableKey(scope, table), ignored -> inspect(table));
    }

    private AuditColumns inspect(TableName table) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = currentSchema(connection);
            if (table.catalog() != null) {
                catalog = table.catalog();
                schema = table.qualifier();
            } else if (table.qualifier() != null) {
                // A two-part name uses a database catalog on MySQL and a schema on Dameng/Kingbase.
                if (schema != null || metadata.supportsSchemasInDataManipulation()) {
                    schema = table.resolveQualifier(schema, metadata);
                } else {
                    catalog = table.resolveQualifier(catalog, metadata);
                }
            }

            Set<String> columns = readColumns(metadata, catalog, schema, table.name());
            String lower = table.name().toLowerCase(Locale.ROOT);
            String upper = table.name().toUpperCase(Locale.ROOT);
            if (columns.isEmpty() && !table.name().equals(lower)) {
                columns = readColumns(metadata, catalog, schema, lower);
            }
            if (columns.isEmpty() && !table.name().equals(upper)) {
                columns = readColumns(metadata, catalog, schema, upper);
            }
            return new AuditColumns(
                    firstPresent(columns, "created_by", "create_by"),
                    firstPresent(columns, "created_time", "create_time"),
                    firstPresent(columns, "updated_by", "update_by"),
                    firstPresent(columns, "updated_time", "update_time")
            );
        } catch (Exception e) {
            log.warn("Inspect audit columns failed for table {}: {}", table.name(), e.getMessage());
            return AuditColumns.NONE;
        }
    }

    private String currentSchema(Connection connection) throws SQLException {
        try {
            String schema = connection.getSchema();
            return schema == null || schema.isBlank() ? null : schema;
        } catch (SQLFeatureNotSupportedException | AbstractMethodError ignored) {
            // Catalog-based drivers can report an unsupported JDBC schema operation.
            return null;
        }
    }

    private Set<String> readColumns(DatabaseMetaData metadata, String catalog, String schema, String tableName)
            throws SQLException {
        Set<String> result = new HashSet<>();
        try (ResultSet columns = metadata.getColumns(catalog, schema, tableName, null)) {
            while (columns.next()) {
                // JDBC metadata accepts patterns. Check the physical owner and name before merging columns.
                if (!tableName.equals(columns.getString("TABLE_NAME"))
                        || !Objects.equals(schema, columns.getString("TABLE_SCHEM"))
                        || (schema == null && !Objects.equals(catalog, columns.getString("TABLE_CAT")))) {
                    continue;
                }
                result.add(columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private String firstPresent(Set<String> columns, String preferred, String legacy) {
        if (columns.contains(preferred)) {
            return preferred;
        }
        return columns.contains(legacy) ? legacy : null;
    }

    private record TableKey(String scope, TableName table) { }

    private record TableName(String catalog, String qualifier, boolean quotedQualifier, String name) {
        String resolveQualifier(String current, DatabaseMetaData metadata) throws SQLException {
            return !quotedQualifier && qualifier.equalsIgnoreCase(current) && !metadata.supportsMixedCaseIdentifiers()
                    ? current : qualifier;
        }

        static TableName parse(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String[] parts = value.split("\\.", -1);
            String qualifier = parts.length > 1 ? parts[parts.length - 2].stripLeading() : "";
            boolean quotedQualifier = qualifier.startsWith("`") || qualifier.startsWith("\"");
            for (int index = 0; index < parts.length; index++) {
                parts[index] = parts[index].replace("`", "").replace("\"", "").trim();
                if (parts[index].isEmpty()) {
                    return null;
                }
            }
            return switch (parts.length) {
                case 1 -> new TableName(null, null, false, parts[0]);
                case 2 -> new TableName(null, parts[0], quotedQualifier, parts[1]);
                case 3 -> new TableName(parts[0], parts[1], quotedQualifier, parts[2]);
                default -> null;
            };
        }
    }

    public record AuditColumns(
            String createdBy,
            String createdTime,
            String updatedBy,
            String updatedTime
    ) {
        static final AuditColumns NONE = new AuditColumns(null, null, null, null);

        boolean isEmpty() {
            return createdBy == null && createdTime == null && updatedBy == null && updatedTime == null;
        }
    }
}
