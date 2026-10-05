package com.linewell.dataelement.platform.web.config;

import com.linewell.dataelement.platform.tenant.web.TenantWebInterceptor;
import com.linewell.dataelement.platform.web.security.GlobalInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;
import com.linewell.dataelement.model.Global;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Autowired
    private GlobalInterceptor globalInterceptor;

    @Autowired
    private TenantWebInterceptor tenantWebInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(Global.USER_FILES_BASE_URL + "**").addResourceLocations("file:" + Global.getDir() + Global.USER_FILES_BASE_URL);
    }

    // 兼容接口多个"/"，比如//system/xxx
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.setUrlPathHelper(new UrlPathHelper());
    }

    // 注册自定义拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册PermissionInterceptor拦截器，对所有路径生效
        registry.addInterceptor(globalInterceptor)
            .excludePathPatterns(
                "/api/web/**",
                "/nifi-ui/**",
                "/nifi-api/**",
                "/portal/index",
                "/portal/login",
                "/portal/logout",
                "/sym/tenant/login-options",
                "/sym/tenant/login-settings",
                "/dws/push/internal/**"
            );
        registry.addInterceptor(tenantWebInterceptor)
            .excludePathPatterns(
                "/api/web/**",
                "/nifi-ui/**",
                "/nifi-api/**",
                "/portal/index",
                "/portal/login",
                "/portal/logout",
                "/dws/push/internal/**"
            );
            //.addPathPatterns("/**"); // 根据需要调整路径匹配规则
    }

}
