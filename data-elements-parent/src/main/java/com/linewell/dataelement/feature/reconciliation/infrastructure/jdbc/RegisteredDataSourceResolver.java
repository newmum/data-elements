package com.linewell.dataelement.feature.reconciliation.infrastructure.jdbc;

import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.feature.reconciliation.config.ReconciliationProperties;
import com.linewell.dataelement.feature.reconciliation.domain.ReconciliationModels.Endpoint;
import com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.mapper.ReconciliationControlMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RegisteredDataSourceResolver {

    private final ReconciliationControlMapper controlMapper;
    private final DataSourceConnectionPropertyResolver connectionProperties;
    private final ReconciliationProperties properties;

    public RegisteredDataSourceResolver(
            ReconciliationControlMapper controlMapper,
            DataSourceConnectionPropertyResolver connectionProperties,
            ReconciliationProperties properties
    ) {
        this.controlMapper = controlMapper;
        this.connectionProperties = connectionProperties;
        this.properties = properties;
    }

    public Endpoint resolve(String tenantId, String datasourceId, String tableId) {
        Map<String, Object> row =
                controlMapper.selectEndpoint(tenantId, datasourceId, tableId);
        if (row == null || row.isEmpty()) {
            throw new TenantAccessException(
                    "RECONCILE-ENDPOINT-NOT-FOUND",
                    "对账数据源或数据表不存在，或者不属于当前租户"
            );
        }
        Map<String, Object> pool = connectionProperties.resolve(tenantId, datasourceId);
        String jdbcUrl = first(row, pool, "jdbc_url", "jdbcUrl", "jdbcURL", "url");
        String username = first(row, pool, "username", "user");
        String password = first(row, pool, "password");
        String driver = first(row, pool, "driver_class_name", "driverClassName", "driver");
        if (blank(jdbcUrl)) {
            throw new IllegalStateException("数据源未配置 JDBC URL");
        }
        return new Endpoint(
                text(row.get("datasource_id")),
                text(row.get("table_id")),
                jdbcUrl,
                username,
                password,
                driver,
                text(row.get("table_name"))
        );
    }

    public Connection open(Endpoint endpoint) throws SQLException {
        try {
            if (!blank(endpoint.driverClassName())) {
                Class.forName(endpoint.driverClassName());
            }
        } catch (ClassNotFoundException e) {
            throw new SQLException("找不到数据源驱动: " + endpoint.driverClassName(), e);
        }
        DriverManager.setLoginTimeout(
                Math.max(1, Math.toIntExact(properties.getConnectionTimeout().toSeconds()))
        );
        return DriverManager.getConnection(
                endpoint.jdbcUrl(),
                defaultText(endpoint.username()),
                defaultText(endpoint.password())
        );
    }

    private String first(
            Map<String, Object> row,
            Map<String, Object> pool,
            String... keys
    ) {
        for (String key : keys) {
            if (row.containsKey(key) && !blank(text(row.get(key)))) {
                return text(row.get(key));
            }
            if (pool.containsKey(key) && !blank(text(pool.get(key)))) {
                return text(pool.get(key));
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value);
    }

    private String defaultText(String value) {
        return value == null ? "" : value;
    }
}
