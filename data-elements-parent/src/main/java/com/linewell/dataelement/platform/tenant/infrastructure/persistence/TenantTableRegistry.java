package com.linewell.dataelement.platform.tenant.infrastructure.persistence;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Protects SQL rewriting from adding tenant_id to legacy/global tables that do
 * not carry the column.
 */
@Slf4j
@Component
public class TenantTableRegistry {

    private final DataSource dataSource;
    private final Map<String, Boolean> tenantTables = new ConcurrentHashMap<>();

    public TenantTableRegistry(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean hasTenantColumn(String rawTableName) {
        String tableName = normalize(rawTableName);
        if (tableName == null) {
            return false;
        }
        String scope = TenantContext.usesControlDatabase()
                ? "control"
                : "tenant:" + String.valueOf(TenantContext.getTenantId());
        return tenantTables.computeIfAbsent(scope + ":" + tableName, ignored -> inspect(tableName));
    }

    public void evict(String tableName) {
        String normalized = normalize(tableName);
        if (normalized != null) {
            tenantTables.keySet().removeIf(key -> key.endsWith(":" + normalized));
        }
    }

    private boolean inspect(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            try (ResultSet columns = connection.getMetaData().getColumns(
                    connection.getCatalog(),
                    null,
                    tableName,
                    "tenant_id"
            )) {
                if (columns.next()) {
                    return true;
                }
            }
            try (ResultSet columns = connection.getMetaData().getColumns(
                    connection.getCatalog(),
                    null,
                    tableName.toUpperCase(Locale.ROOT),
                    "TENANT_ID"
            )) {
                return columns.next();
            }
        } catch (Exception e) {
            log.warn("Inspect tenant column failed for table {}: {}", tableName, e.getMessage());
            return false;
        }
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
}
