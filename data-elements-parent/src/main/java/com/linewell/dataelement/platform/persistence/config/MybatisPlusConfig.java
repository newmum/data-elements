package com.linewell.dataelement.platform.persistence.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.linewell.dataelement.platform.tenant.infrastructure.mybatis.PlatformTenantLineHandler;
import java.util.Properties;
import java.util.Map;
import java.util.Locale;
import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

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
    public DatabaseIdProvider databaseIdProvider(@Qualifier("controlDataSource") DataSource controlDataSource,
            TenantDatabaseProperties tenantDatabases) {
        Properties vendors = new Properties();
        vendors.setProperty("MySQL", "mysql");
        vendors.setProperty("DM DBMS", "dm");
        vendors.setProperty("Oracle", "oracle");
        vendors.setProperty("PostgreSQL", "postgresql");
        vendors.setProperty("KingbaseES", "kingbase");
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        provider.setProperties(vendors);
        // 保持原先按默认行业选择 XML 方言的行为，使用已配置的 JDBC 协议识别，避免启动连接离线行业库。
        if (tenantDatabases.isEnabled()) {
            var definition = tenantDatabases.getDataSources().get(tenantDatabases.getDefaultDataSourceKey());
            if (definition != null && definition.getUrl() != null && definition.getUrl().startsWith("jdbc:")) {
                String protocol = definition.getUrl().toLowerCase(Locale.ROOT).split(":", 3)[1];
                String databaseId = Map.of("mysql", "mysql", "dm", "dm", "oracle", "oracle",
                        "postgresql", "postgresql", "kingbase8", "kingbase").get(protocol);
                if (databaseId != null) { return ignoredTenantRouter -> databaseId; }
            }
        }
        // 单库或未匹配的部署仍通过控制库完成 MyBatis 的产品识别。
        return ignoredTenantRouter -> provider.getDatabaseId(controlDataSource);
    }

}
