package com.linewell.dataelement.integration.nifi.canvas.runtime;

import cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff;
import cn.dev33.satoken.servlet.util.SaTokenContextJakartaServletUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Re-establish request context when MVC finishes an authenticated SSE request. */
@Configuration
public class TaskRuntimeAsyncConfiguration {
    @Bean
    public FilterRegistrationBean<Filter> taskRuntimeAsyncContext() {
        Filter filter = (request, response, chain) -> {
            var prior = SaTokenContextForThreadLocalStaff.getModelBoxOrNull();
            if (prior == null) {
                SaTokenContextJakartaServletUtil.setContext(
                        (HttpServletRequest) request, (HttpServletResponse) response);
            }
            try {
                // The normal login and tenant interceptors still run on redispatch.
                chain.doFilter(request, response);
            } finally {
                if (prior == null) SaTokenContextForThreadLocalStaff.clearModelBox();
            }
        };
        var registration = new FilterRegistrationBean<>(filter);
        registration.setName("taskRuntimeAsyncContext");
        registration.setDispatcherTypes(DispatcherType.ASYNC);
        registration.addUrlPatterns("/nifi/api/task-runtime/events");
        registration.setAsyncSupported(true);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
