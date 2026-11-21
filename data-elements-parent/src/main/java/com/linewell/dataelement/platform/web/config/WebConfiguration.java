package com.linewell.dataelement.platform.web.config;

import cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff;
import cn.dev33.satoken.servlet.util.SaTokenContextJakartaServletUtil;
import com.linewell.dataelement.magic.runtime.result.MagicApiResultProvider;
import com.linewell.dataelement.model.Global;
import com.linewell.dataelement.platform.audit.AuditWebInterceptor;
import com.linewell.dataelement.platform.tenant.web.TenantWebInterceptor;
import com.linewell.dataelement.platform.web.security.GlobalInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;
import org.ssssssss.magicapi.core.config.MagicAPIProperties;

@Configuration
@PropertySource("classpath:shared-web.properties")
public class WebConfiguration implements WebMvcConfigurer {

    private final GlobalInterceptor globalInterceptor;
    private final TenantWebInterceptor tenantWebInterceptor;
    private final AuditWebInterceptor auditWebInterceptor;
    private final MagicAPIProperties magicProperties;
    private final String corsPattern;

    public WebConfiguration(GlobalInterceptor globalInterceptor, TenantWebInterceptor tenantWebInterceptor,
                            AuditWebInterceptor auditWebInterceptor, MagicAPIProperties magicProperties,
                            @Value("${data-element.web.mvc-cors-pattern}") String corsPattern) {
        this.globalInterceptor = globalInterceptor;
        this.tenantWebInterceptor = tenantWebInterceptor;
        this.auditWebInterceptor = auditWebInterceptor;
        this.magicProperties = magicProperties;
        this.corsPattern = corsPattern;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping(corsPattern)
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(Global.USER_FILES_BASE_URL + "**").addResourceLocations("file:" + Global.getDir() + Global.USER_FILES_BASE_URL);
    }

    // 兼容接口多个"/"，比如//system/xxx
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.setUrlPathHelper(new UrlPathHelper());
    }

    @Bean
    public FilterRegistrationBean<Filter> magicEventStreamAsyncContext() {
        Filter filter = (request, response, chain) -> {
            if (!Boolean.TRUE.equals(request.getAttribute(MagicApiResultProvider.EVENT_STREAM))
                    || SaTokenContextForThreadLocalStaff.getModelBoxOrNull() != null) {
                chain.doFilter(request, response);
                return;
            }
            SaTokenContextJakartaServletUtil.setContext((HttpServletRequest) request, (HttpServletResponse) response);
            try {
                chain.doFilter(request, response);
            } finally {
                SaTokenContextForThreadLocalStaff.clearModelBox();
            }
        };
        var registration = new FilterRegistrationBean<>(filter);
        registration.setDispatcherTypes(DispatcherType.ASYNC);
        registration.addUrlPatterns("/*");
        registration.setAsyncSupported(true);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Override
    public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
        resolvers.addFirst((request, response, handler, failure) -> {
            if (request.getDispatcherType() != DispatcherType.ASYNC
                    || !Boolean.TRUE.equals(request.getAttribute(MagicApiResultProvider.EVENT_STREAM))) {
                return null;
            }
            // A disconnected stream cannot carry a JSON error envelope.
            return failure instanceof IOException || failure instanceof AsyncRequestTimeoutException
                    ? new ModelAndView() : null;
        });
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditWebInterceptor);
        var session = registry.addInterceptor(globalInterceptor);
        var tenant = registry.addInterceptor(tenantWebInterceptor);
        String editor = magicProperties.getWeb();
        if (editor != null && !editor.isBlank()) {
            // Magic API 启动时注册编辑器并处理编辑器认证。
            String editorTree = editor + "/**";
            session.excludePathPatterns(editorTree);
            tenant.excludePathPatterns(editorTree);
        }
    }
}
