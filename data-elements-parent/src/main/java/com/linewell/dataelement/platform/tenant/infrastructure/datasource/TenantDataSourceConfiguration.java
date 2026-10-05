package com.linewell.dataelement.platform.tenant.infrastructure.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class TenantDataSourceConfiguration {

    @Bean(name = "controlDataSource")
    public DataSource controlDataSource(DataSourceProperties properties) {
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setName("tenant-control");
        dataSource.setDriverClassName(properties.determineDriverClassName());
        dataSource.setUrl(properties.determineUrl());
        dataSource.setUsername(properties.determineUsername());
        dataSource.setPassword(properties.determinePassword());
        return dataSource;
    }

    @Bean
    public TenantDataSourceRegistry tenantDataSourceRegistry(
            @Qualifier("controlDataSource") DataSource controlDataSource,
            TenantDatabaseProperties properties
    ) {
        return new TenantDataSourceRegistry(controlDataSource, properties);
    }

    @Bean
    @Primary
    public DataSource dataSource(
            @Qualifier("controlDataSource") DataSource controlDataSource,
            TenantDataSourceRegistry registry,
            TenantDatabaseProperties properties
    ) {
        TenantRoutingDataSource routing = new TenantRoutingDataSource(properties);
        routing.setDefaultTargetDataSource(controlDataSource);
        routing.setTargetDataSources(registry.targets());
        routing.afterPropertiesSet();
        return routing;
    }
}
