package com.linewell.dataelement.platform.persistence.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.linewell.dataelement.platform.tenant.infrastructure.mybatis.PlatformTenantLineHandler;
import java.util.Properties;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    private final PlatformTenantLineHandler tenantLineHandler;

    public MybatisPlusConfig(PlatformTenantLineHandler tenantLineHandler) {
        this.tenantLineHandler = tenantLineHandler;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));
        // The primary datasource is a tenant router. Supplying one fixed DbType
        // here makes every tenant use whichever dialect was seen at startup.
        // MyBatis-Plus resolves the dialect from the current executor connection
        // when no DbType is provided, so MySQL, DM, and future tenant databases
        // receive their own pagination syntax.
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        Properties vendors = new Properties();
        vendors.setProperty("MySQL", "mysql");
        vendors.setProperty("DM DBMS", "dm");
        vendors.setProperty("Oracle", "oracle");
        vendors.setProperty("PostgreSQL", "postgresql");
        vendors.setProperty("KingbaseES", "kingbase");
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        provider.setProperties(vendors);
        return provider;
    }

}
