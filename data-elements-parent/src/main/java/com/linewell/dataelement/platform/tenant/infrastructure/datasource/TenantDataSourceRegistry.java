package com.linewell.dataelement.platform.tenant.infrastructure.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import com.linewell.dataelement.platform.persistence.config.DruidDataSourcePropertiesBinder;
import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

public class TenantDataSourceRegistry {

    private final Map<Object, Object> targets = new LinkedHashMap<>();
    private final Map<String, DruidDataSource> managed = new LinkedHashMap<>();
    private final TenantDatabaseProperties properties;

    public TenantDataSourceRegistry(
            DataSource controlDataSource,
            TenantDatabaseProperties properties
    ) {
        this.properties = properties;
        targets.put(TenantDatabaseProperties.CONTROL_KEY, controlDataSource);
        properties.getDataSources().forEach((key, definition) -> {
            DruidDataSource dataSource = new DruidDataSource();
            dataSource.setName("tenant-" + key);
            dataSource.setDriverClassName(definition.getDriverClassName());
            dataSource.setUrl(definition.getUrl());
            dataSource.setUsername(definition.getUsername());
            dataSource.setPassword(definition.getPassword());
            configureSchema(dataSource, definition);
            dataSource.setInitialSize(0);
            dataSource.setMinIdle(0);
            dataSource.setMaxActive(20);
            dataSource.setTestWhileIdle(true);
            dataSource.setValidationQuery(validationQuery(definition.getUrl()));
            DruidDataSourcePropertiesBinder.applyMySqlNetworkTimeouts(dataSource);
            managed.put(key, dataSource);
            targets.put(key, dataSource);
        });
    }

    public Map<Object, Object> targets() {
        return Map.copyOf(targets);
    }

    public DataSource dataSourceForTenant(String tenantId) {
        String dataSourceKey = properties.resolve(tenantId);
        Object dataSource = targets.get(dataSourceKey);
        if (!(dataSource instanceof DataSource resolved)) {
            throw new IllegalArgumentException(
                    "No datasource is configured for tenant " + tenantId
            );
        }
        return resolved;
    }

    @PreDestroy
    public void close() {
        managed.values().forEach(DruidDataSource::close);
    }

    private String validationQuery(String url) {
        return url != null && url.toLowerCase().startsWith("jdbc:dm:")
                ? "select 1"
                : "select 1";
    }

    private void configureSchema(
            DruidDataSource dataSource,
            TenantDatabaseProperties.Definition definition
    ) {
        String schema = definition.getSchema();
        String url = definition.getUrl();
        if (schema == null || schema.isBlank() || url == null || !url.toLowerCase().startsWith("jdbc:dm:")) {
            return;
        }
        if (!schema.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid tenant datasource schema: " + schema);
        }
        dataSource.setConnectionInitSqls(List.of("SET SCHEMA " + schema));
    }
}
