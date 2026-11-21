package com.linewell.dataelement.platform.web.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff;
import cn.dev33.satoken.servlet.util.SaTokenContextJakartaServletUtil;
import com.linewell.dataelement.magic.runtime.result.MagicApiResultProvider;
import com.linewell.dataelement.platform.audit.AuditWebInterceptor;
import com.linewell.dataelement.platform.tenant.web.TenantWebInterceptor;
import com.linewell.dataelement.platform.web.security.GlobalInterceptor;
import jakarta.servlet.DispatcherType;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.ssssssss.magicapi.core.config.MagicAPIProperties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebAsyncConfigurationTest {
    private final WebConfiguration web = new WebConfiguration(null, null, null, new MagicAPIProperties(), "/api/**");

    @Test void registersSharedCorsWithTheConfiguredPatternAndExistingTransportContract() {
        var runner = new WebApplicationContextRunner().withUserConfiguration(WebConfiguration.class, CorsValidationMvc.class)
                .withBean(GlobalInterceptor.class, () -> mock(GlobalInterceptor.class))
                .withBean(TenantWebInterceptor.class, () -> mock(TenantWebInterceptor.class))
                .withBean(AuditWebInterceptor.class, () -> mock(AuditWebInterceptor.class))
                .withBean(MagicAPIProperties.class, MagicAPIProperties::new)
                .withBean(CorsValidationController.class);
        for (String pattern : java.util.List.of("/api/**", "/validation-api/**")) {
            var configured = pattern.equals("/api/**") ? runner
                    : runner.withPropertyValues("data-element.web.mvc-cors-pattern=" + pattern);
            configured.run(context -> {
                var registry = new CorsRegistry() {
                    java.util.Map<String, CorsConfiguration> mappings() { return getCorsConfigurations(); }
                };
                context.getBean(WebConfiguration.class).addCorsMappings(registry);
                assertThat(registry.mappings()).containsOnlyKeys(pattern);
                var cors = registry.mappings().get(pattern);
                assertThat(cors.getAllowedOriginPatterns()).containsExactly("*");
                assertThat(cors.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
                assertThat(cors.getAllowedHeaders()).containsExactly("*");
                assertThat(cors.getAllowCredentials()).isTrue();
                assertThat(cors.getMaxAge()).isEqualTo(3600);
                var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).build();
                var allowed = pattern.replace("/**", "/validation-check");
                var other = pattern.equals("/api/**") ? "/validation-api/validation-check" : "/api/validation-check";
                var origin = "https://validation.example";
                mvc.perform(options(allowed).header("Origin", origin).header("Access-Control-Request-Method", "GET"))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Access-Control-Allow-Origin", origin))
                        .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                        .andExpect(header().string("Access-Control-Max-Age", "3600"));
                mvc.perform(options(other).header("Origin", origin).header("Access-Control-Request-Method", "GET"))
                        .andExpect(status().isForbidden());
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class CorsValidationMvc { }

    @RestController
    static class CorsValidationController {
        @GetMapping({"/api/validation-check", "/validation-api/validation-check"})
        String check() { return "validation"; }
    }

    @Test void restoresContextDuringAsyncCompletionAndCleansTheWorkerThread() throws Exception {
        var registration = web.magicEventStreamAsyncContext();
        var request = streamRequest();
        var response = new MockHttpServletResponse();
        assertThat(registration.getUrlPatterns()).containsExactly("/*");
        assertThat(registration.isAsyncSupported()).isTrue();
        registration.getFilter().doFilter(request, response, (req, res) -> {
            assertThat(SaTokenContextForThreadLocalStaff.getModelBoxOrNull()).isNotNull();
            assertThat(SaHolder.getRequest().getRequestPath()).isEqualTo(request.getRequestURI());
        });
        assertThat(SaTokenContextForThreadLocalStaff.getModelBoxOrNull()).isNull();
    }

    @Test void preservesAnExistingContextAndCleansUpOnFailure() throws Exception {
        var filter = web.magicEventStreamAsyncContext().getFilter();
        var request = streamRequest();
        var response = new MockHttpServletResponse();
        SaTokenContextJakartaServletUtil.setContext(request, response);
        var prior = SaTokenContextForThreadLocalStaff.getModelBoxOrNull();
        try {
            filter.doFilter(request, response, (req, res) -> assertThat(SaTokenContextForThreadLocalStaff.getModelBoxOrNull()).isSameAs(prior));
            assertThat(SaTokenContextForThreadLocalStaff.getModelBoxOrNull()).isSameAs(prior);
        } finally { SaTokenContextForThreadLocalStaff.clearModelBox(); }
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> { throw new jakarta.servlet.ServletException("test"); })).isInstanceOf(jakarta.servlet.ServletException.class);
        assertThat(SaTokenContextForThreadLocalStaff.getModelBoxOrNull()).isNull();
    }

    @Test void ignoresUnmarkedRequestsAndOnlyConsumesAsyncStreamDisconnects() throws Exception {
        var registration = web.magicEventStreamAsyncContext();
        var request = streamRequest();
        var response = new MockHttpServletResponse();
        var resolvers = new ArrayList<HandlerExceptionResolver>();
        web.extendHandlerExceptionResolvers(resolvers);
        var resolver = resolvers.getFirst();
        assertThat(resolver.resolveException(request, response, null, new IOException())).isNotNull();
        assertThat(resolver.resolveException(request, response, null, new IllegalArgumentException())).isNull();
        request.setDispatcherType(DispatcherType.REQUEST);
        assertThat(resolver.resolveException(request, response, null, new IOException())).isNull();
        request.setDispatcherType(DispatcherType.ASYNC);
        request.removeAttribute(MagicApiResultProvider.EVENT_STREAM);
        assertThat(resolver.resolveException(request, response, null, new IOException())).isNull();
        registration.getFilter().doFilter(request, response, (req, res) ->
                assertThat(SaTokenContextForThreadLocalStaff.getModelBoxOrNull()).isNull());
    }

    private static MockHttpServletRequest streamRequest() {
        var request = new MockHttpServletRequest("GET", "/validation/events");
        request.setDispatcherType(DispatcherType.ASYNC);
        request.setAttribute(MagicApiResultProvider.EVENT_STREAM, true);
        return request;
    }
}
