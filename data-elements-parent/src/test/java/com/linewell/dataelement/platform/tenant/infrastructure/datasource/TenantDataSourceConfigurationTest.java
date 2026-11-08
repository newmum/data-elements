package com.linewell.dataelement.platform.tenant.infrastructure.datasource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.druid.pool.DruidDataSource;
import com.linewell.dataelement.platform.persistence.KingbaseSqlFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

class TenantDataSourceConfigurationTest {
    @Test
    void installsDialectFilterOnlyForKingbaseControlConnections() {
        var config = new TenantDataSourceConfiguration();
        var properties = new DataSourceProperties();
        properties.setUrl("jdbc:kingbase8://localhost:54321/validation");
        properties.setDriverClassName("com.kingbase8.Driver");
        try (var control = (DruidDataSource) config.controlDataSource(properties)) {
            assertTrue(control.getProxyFilters().stream().anyMatch(KingbaseSqlFilter.class::isInstance));
            assertTrue(control.getConnectionInitSqls().contains("SET ora_input_emptystr_isnull=off"));
        }
        properties.setUrl("jdbc:mysql://localhost:3306/validation");
        properties.setDriverClassName("com.mysql.cj.jdbc.Driver");
        try (var control = (DruidDataSource) config.controlDataSource(properties)) {
            assertFalse(control.getProxyFilters().stream().anyMatch(KingbaseSqlFilter.class::isInstance));
        }
    }
}
