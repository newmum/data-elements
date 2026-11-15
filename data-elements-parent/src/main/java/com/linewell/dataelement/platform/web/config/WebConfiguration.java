package com.linewell.dataelement.platform.web.config;

import com.linewell.dataelement.platform.tenant.web.TenantWebInterceptor;
import com.linewell.dataelement.platform.web.security.GlobalInterceptor;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
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

    @Autowired
    private com.linewell.dataelement.platform.audit.AuditWebInterceptor auditWebInterceptor;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(Global.USER_FILES_BASE_URL + "**").addResourceLocations("file:" + Global.getDir() + Global.USER_FILES_BASE_URL);
    }

    @Bean
    FilterRegistrationBean<Filter> legacyAttachmentDownload() {
        String oldPath = "/data/download";
        String downloadPath = "/dst/file/download";
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>((request, response, chain) -> {
            HttpServletRequest http = (HttpServletRequest) request;
            if (!"GET".equals(http.getMethod()) || !http.getRequestURI().equals(http.getContextPath() + oldPath)) {
                chain.doFilter(request, response);
                return;
            }
            // 在会话过滤器前统一路径，使旧链接和新入口沿用同一次鉴权及租户解析。
            chain.doFilter(new HttpServletRequestWrapper(http) {
                @Override
                public String getRequestURI() {
                    return getContextPath() + downloadPath;
                }

                @Override
                public String getServletPath() {
                    return downloadPath;
                }

                @Override
                public StringBuffer getRequestURL() {
                    StringBuffer original = super.getRequestURL();
                    return original.replace(original.length() - oldPath.length(), original.length(), downloadPath);
                }
            }, response);
        });
        registration.addUrlPatterns(oldPath);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        return registration;
    }

    // 兼容接口多个"/"，比如//system/xxx
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.setUrlPathHelper(new UrlPathHelper());
    }

    // 注册自定义拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditWebInterceptor);
        // 注册PermissionInterceptor拦截器，对所有路径生效
        registry.addInterceptor(globalInterceptor)
            .excludePathPatterns(
                "/api/web/**",
                "/nifi-ui/**",
                "/nifi-api/**",
                "/portal/main/index",
                "/portal/sso/login",
                "/portal/sso/logout",
                "/portal/sso/options",
                "/portal/sso/settings",
                "/dws/push/internal/**"
            );
        registry.addInterceptor(tenantWebInterceptor)
            .excludePathPatterns(
                "/api/web/**",
                "/nifi-ui/**",
                "/nifi-api/**",
                "/portal/main/index",
                "/portal/sso/login",
                "/portal/sso/logout",
                "/dws/push/internal/**"
            );
            //.addPathPatterns("/**"); // 根据需要调整路径匹配规则
    }

}
