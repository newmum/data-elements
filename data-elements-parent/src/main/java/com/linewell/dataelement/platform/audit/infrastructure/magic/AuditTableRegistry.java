package com.linewell.dataelement.platform.audit.infrastructure.magic;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
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
    private final Map<String, AuditColumns> tables = new ConcurrentHashMap<>();

    public AuditTableRegistry(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public AuditColumns columnsOf(String rawTableName) {
        String tableName = normalize(rawTableName);
        if (tableName == null) {
            return AuditColumns.NONE;
        }
        String scope = TenantContext.usesControlDatabase()
                ? "control"
                : "tenant:" + String.valueOf(TenantContext.getTenantId());
        return tables.computeIfAbsent(scope + ":" + tableName, ignored -> inspect(tableName));
    }

    private AuditColumns inspect(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            Set<String> columns = readColumns(connection, tableName);
            if (columns.isEmpty()) {
                columns = readColumns(connection, tableName.toUpperCase(Locale.ROOT));
            }
            return new AuditColumns(
                    firstPresent(columns, "created_by", "create_by"),
                    firstPresent(columns, "created_time", "create_time"),
                    firstPresent(columns, "updated_by", "update_by"),
                    firstPresent(columns, "updated_time", "update_time")
            );
        } catch (Exception e) {
            log.warn("Inspect audit columns failed for table {}: {}", tableName, e.getMessage());
            return AuditColumns.NONE;
        }
    }

    private Set<String> readColumns(Connection connection, String tableName) throws Exception {
        Set<String> result = new HashSet<>();
        try (ResultSet columns = connection.getMetaData().getColumns(
                connection.getCatalog(), null, tableName, null
        )) {
            while (columns.next()) {
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

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replace("`", "").replace("\"", "").trim();
        int dot = normalized.lastIndexOf('.');
        if (dot >= 0) {
            normalized = normalized.substring(dot + 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
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
