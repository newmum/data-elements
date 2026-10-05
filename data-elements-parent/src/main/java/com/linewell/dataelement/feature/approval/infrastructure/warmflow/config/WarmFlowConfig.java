package com.linewell.dataelement.feature.approval.infrastructure.warmflow.config;

import com.linewell.dataelement.feature.approval.application.ApprovalPrincipalService;
import com.linewell.dataelement.feature.approval.infrastructure.warmflow.handler.CustomDataFillHandler;
import com.linewell.dataelement.feature.approval.infrastructure.warmflow.handler.CustomPermissionHandler;
import com.linewell.dataelement.feature.approval.infrastructure.warmflow.handler.CustomTenantHandler;
import org.dromara.warm.flow.core.handler.DataFillHandler;
import org.dromara.warm.flow.core.handler.PermissionHandler;
import org.dromara.warm.flow.core.handler.TenantHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * warm-flow配置文件
 *
 * @author warm
 */
@Configuration
public class WarmFlowConfig {

    /**
     * 自定义填充 （可配置文件注入，也可用@Bean/@Component方式）
     */
    @Bean
    public DataFillHandler dataFillHandler() {
        return new CustomDataFillHandler();
    }

    /**
     * 权限校验器 （可配置文件注入，也可用@Bean/@Component方式）
     */
    @Bean
    public PermissionHandler permissionHandler(ApprovalPrincipalService principalService) {
        return new CustomPermissionHandler(principalService);
    }

    @Bean
    public TenantHandler tenantHandler() {
        return new CustomTenantHandler();
    }
}
