package com.linewell.dataelement.feature.dataaccess.infrastructure.jdbc;

import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.feature.dataaccess.domain.RegisteredJdbcEndpoint;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper.RegisteredJdbcDataSourceMapper;
import com.linewell.dataelement.platform.persistence.jdbc.JdbcValueNormalizer;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RegisteredJdbcDataSourceResolver {

    private final RegisteredJdbcDataSourceMapper mapper;
    private final DataSourceConnectionPropertyResolver connectionProperties;

    public RegisteredJdbcDataSourceResolver(
            RegisteredJdbcDataSourceMapper mapper,
            DataSourceConnectionPropertyResolver connectionProperties
    ) {
        this.mapper = mapper;
        this.connectionProperties = connectionProperties;
    }

    public RegisteredJdbcEndpoint resolve(
            String tenantId,
            String datasourceId,
            String tableId
    ) {
        Map<String, Object> row = mapper.selectEndpoint(tenantId, datasourceId, tableId);
        if (row == null || row.isEmpty()) {
            throw new TenantAccessException(
                    "REGISTERED-JDBC-ENDPOINT-NOT-FOUND",
                    "数据源或数据表不存在，或者不属于当前租户"
            );
        }
        Map<String, Object> pool = connectionProperties.resolve(tenantId, datasourceId);
        String jdbcUrl = first(row, pool, "jdbc_url", "jdbcUrl", "jdbcURL", "url");
        if (blank(jdbcUrl)) {
            throw new IllegalStateException("数据源未配置 JDBC URL");
        }
        return new RegisteredJdbcEndpoint(
                text(row.get("datasource_id")),
                text(row.get("datasource_name")),
                text(row.get("table_id")),
                text(row.get("table_name")),
                jdbcUrl,
                first(row, pool, "username", "user"),
                first(row, pool, "password"),
                first(row, pool, "driver_class_name", "driverClassName", "driver")
        );
    }

    public Connection open(RegisteredJdbcEndpoint endpoint, int loginTimeoutSeconds)
            throws SQLException {
        try {
            if (!blank(endpoint.driverClassName())) {
                Class.forName(endpoint.driverClassName());
            }
        } catch (ClassNotFoundException exception) {
            throw new SQLException("找不到数据源驱动: " + endpoint.driverClassName(), exception);
        }
        DriverManager.setLoginTimeout(Math.max(1, loginTimeoutSeconds));
        return DriverManager.getConnection(
                endpoint.jdbcUrl(),
                endpoint.username() == null ? "" : endpoint.username(),
                endpoint.password() == null ? "" : endpoint.password()
        );
    }

    private String first(Map<String, Object> row, Map<String, Object> pool, String... keys) {
        for (String key : keys) {
            if (!blank(text(row.get(key)))) {
                return text(row.get(key));
            }
            if (!blank(text(pool.get(key)))) {
                return text(pool.get(key));
            }
        }
        return null;
    }

    private String text(Object value) {
        String text = JdbcValueNormalizer.text(value);
        return text == null ? null : text.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value);
    }
}
