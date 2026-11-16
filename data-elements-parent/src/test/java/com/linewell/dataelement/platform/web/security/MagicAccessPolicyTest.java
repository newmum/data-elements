package com.linewell.dataelement.platform.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.magic.runtime.security.MagicAccessPolicy;
import com.linewell.dataelement.magic.runtime.security.PolicyAwareApiResourceStorage;
import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.tenant.api.PlatformSessionMagicModule;
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.ssssssss.magicapi.core.config.MagicConfiguration;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.model.Option;
import org.ssssssss.magicapi.core.service.MagicResourceService;
import org.ssssssss.magicapi.core.service.impl.RequestMagicDynamicRegistry;
import org.ssssssss.magicapi.core.servlet.MagicHttpServletRequest;
import org.ssssssss.magicapi.core.servlet.MagicHttpServletResponse;
import org.ssssssss.magicapi.core.web.RequestHandler;
import org.ssssssss.magicapi.servlet.jakarta.MagicJakartaHttpServletRequest;
import org.ssssssss.magicapi.utils.Mapping;

class MagicAccessPolicyTest {
    private PolicyAwareApiResourceStorage storage;
    private RequestMagicDynamicRegistry registry;
    private HandlerMethod magicHandler;
    private PlatformSessionMagicModule platformSession;
    private GlobalInterceptor access;

    @BeforeEach
    void configureRuntimeRegistry() throws Exception {
        storage = new PolicyAwareApiResourceStorage("");
        var resources = mock(MagicResourceService.class);
        when(resources.getGroupPath("validation")).thenReturn("/validation");
        storage.setMagicResourceService(resources);
        registry = new RequestMagicDynamicRegistry(storage, Mapping.create(new RequestMappingHandlerMapping()), false, "");
        var handler = new RequestHandler(new MagicConfiguration(), registry);
        registry.setHandler(handler);
        magicHandler = new HandlerMethod(handler, RequestHandler.class.getMethod("invoke",
                MagicHttpServletRequest.class, MagicHttpServletResponse.class, Map.class, Map.class, Map.class));
        var whiteList = new WhiteListProp();
        whiteList.setWhiteList(List.of("/validation/legacy/**"));
        platformSession = mock(PlatformSessionMagicModule.class);
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("registry", registry);
        access = new GlobalInterceptor(whiteList, platformSession, beans.getBeanProvider(RequestMagicDynamicRegistry.class),
                new GlobalExceptionHandler());
    }

    @Test
    void readsTheMethodSpecificPolicyAndRequiresTheDeclaredRealm() {
        register("read", "GET", "/resource", "PUBLIC");
        register("write", "POST", "/resource", "TENANT_SESSION");
        register("operator", "GET", "/legacy/operator", "IDAAS_PLATFORM_SESSION");
        register("default", "GET", "/business", null);

        try (var tenant = mockStatic(StpUtil.class)) {
            assertThat(preHandle("GET", "/validation/resource")).isTrue();
            assertDenied(() -> preHandle("POST", "/validation/resource"));
            assertDenied(() -> preHandle("GET", "/validation/business"));

            tenant.when(StpUtil::isLogin).thenReturn(true);
            assertThat(preHandle("POST", "/validation/resource")).isTrue();
            assertDenied(() -> preHandle("GET", "/validation/legacy/operator"));

            tenant.when(StpUtil::isLogin).thenReturn(false);
            when(platformSession.isLogin()).thenReturn(true);
            assertThat(preHandle("GET", "/validation/legacy/operator")).isTrue();
            assertDenied(() -> preHandle("POST", "/validation/resource"));
        }
    }

    @Test
    void checksTheExecutionResourceAgainWhenReloadChangesThePolicyAfterMvc() {
        var original = register("reload", "GET", "/reload", "PUBLIC");
        var request = request("GET", "/validation/reload");
        assertThat(access.preHandle(request, new MockHttpServletResponse(), magicHandler)).isTrue();

        var replacement = register("reload", "GET", "/reload", "TENANT_SESSION");
        var magicRequest = new MagicJakartaHttpServletRequest(request, null);
        var execution = RequestEntity.create().info(registry.getApiInfoFromRequest(magicRequest)).request(magicRequest);
        assertThat(execution.getApiInfo()).isNotSameAs(original).isSameAs(replacement);
        try (var tenant = mockStatic(StpUtil.class)) {
            assertFailureEnvelope(access.preHandle(execution), 100120);
        }
    }

    @Test
    void invalidExecutionPolicyFailsClosedWithTheBusinessErrorEnvelope() {
        var request = new MagicJakartaHttpServletRequest(request("GET", "/validation/invalid"), null);
        var execution = RequestEntity.create().info(api("invalid", "GET", "/invalid", "unknown")).request(request);

        assertFailureEnvelope(access.preHandle(execution), 403);
    }

    @Test
    void resourceStorageRejectsDuplicateOrUnsafeDeclarationsAndReadsValidOnesBack() {
        var api = api("brand", "GET", "/brand", "PUBLIC");
        assertThat(MagicAccessPolicy.declared(storage.read(storage.write(api)))).isEqualTo(MagicAccessPolicy.PUBLIC);

        api.setMethod("POST");
        assertThatThrownBy(() -> storage.write(api)).isInstanceOf(IllegalArgumentException.class);
        api.setMethod("GET");
        api.setOption(List.of(new Option("access", "PUBLIC"), new Option("access", "TENANT_SESSION")));
        assertThatThrownBy(() -> storage.write(api)).isInstanceOf(IllegalArgumentException.class);
    }

    private ApiInfo register(String id, String method, String path, String policy) {
        var api = storage.read(storage.write(api(id, method, path, policy)));
        assertThat(registry.register(api)).isTrue();
        return api;
    }

    private static ApiInfo api(String id, String method, String path, String policy) {
        var api = new ApiInfo();
        api.setId(id);
        api.setName(id);
        api.setMethod(method);
        api.setPath(path);
        api.setGroupId("validation");
        api.setScript("return true;");
        api.setOption(policy == null ? List.of() : List.of(new Option("access", policy)));
        return api;
    }

    private boolean preHandle(String method, String path) {
        return access.preHandle(request(method, path), new MockHttpServletResponse(), magicHandler);
    }

    private static MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, path);
        return request;
    }

    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BizException.class,
                error -> assertThat(error.getCode()).isEqualTo(100120));
    }

    private static void assertFailureEnvelope(Object result, int code) {
        assertThat(result).isInstanceOfSatisfying(ResponseEntity.class, response -> {
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isInstanceOfSatisfying(CommonResponse.class, body -> {
                assertThat(body.getCode()).isEqualTo(code);
                assertThat(body.getData()).isNull();
            });
        });
    }
}
