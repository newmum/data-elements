package com.linewell.dataelement.platform.tenant.infrastructure.datasource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.alibaba.druid.pool.DruidDataSource;
import com.linewell.dataelement.platform.persistence.KingbaseSqlFilter;
import com.linewell.dataelement.platform.persistence.config.MybatisPlusConfig;
import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

class TenantDataSourceConfigurationTest {
    @Test
    void startupVendorProbeUsesControlEvenWhenDefaultTenantIsUnavailable() throws Exception {
        var control = mock(DataSource.class);
        var router = mock(DataSource.class);
        var connection = mock(Connection.class);
        var metadata = mock(DatabaseMetaData.class);
        when(control.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(router.getConnection()).thenThrow(new java.sql.SQLException("Default tenant is offline"));

        var provider = new MybatisPlusConfig(null).databaseIdProvider(control, new TenantDatabaseProperties());

        assertEquals("mysql", provider.getDatabaseId(router));
        verifyNoInteractions(router);
        verify(connection).close();
    }

    @Test
    void offlineTenantKeepsItsConfiguredMapperDialectWithoutOpeningAConnection() throws Exception {
        for (var vendor : java.util.Map.of("mysql", "mysql", "dm", "dm", "oracle", "oracle",
                "postgresql", "postgresql", "kingbase8", "kingbase").entrySet()) {
            var control = mock(DataSource.class);
            var router = mock(DataSource.class);
            var tenants = new TenantDatabaseProperties();
            var definition = new TenantDatabaseProperties.Definition();
            definition.setUrl("jdbc:" + vendor.getKey() + "://offline.invalid/validation");
            tenants.getDataSources().put(tenants.getDefaultDataSourceKey(), definition);
            var provider = new MybatisPlusConfig(null).databaseIdProvider(control, tenants);
            assertEquals(vendor.getValue(), provider.getDatabaseId(router));
            verifyNoInteractions(control, router);
        }
    }

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
