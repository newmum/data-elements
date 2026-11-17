package com.linewell.dataelement.platform.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.linewell.dataelement.config.DataPushRegistryProperties;
import com.linewell.dataelement.feature.identity.application.TenantIdentityQueryService;
import com.linewell.dataelement.integration.transfer.haitong.RelayRequestAuthentication;
import com.linewell.dataelement.magic.runtime.result.CustomJsonValueProvider;
import com.linewell.dataelement.magic.runtime.route.HeadFallbackMagicRegistry;
import com.linewell.dataelement.magic.runtime.security.MagicAccessPolicy;
import com.linewell.dataelement.magic.runtime.security.PolicyAwareApiResourceStorage;
import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.tenant.api.PlatformSessionMagicModule;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.web.TenantWebInterceptor;
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
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
    private DataPushRegistryProperties relayProperties;
    private GlobalInterceptor access;

    @BeforeEach
    void configureRuntimeRegistry() throws Exception {
        storage = new PolicyAwareApiResourceStorage("");
        var resources = mock(MagicResourceService.class);
        when(resources.getGroupPath("validation")).thenReturn("/validation");
        when(resources.getGroupPath("idaas")).thenReturn("/idaas");
        when(resources.getGroupPath("published")).thenReturn("/dws/flowserve/published");
        storage.setMagicResourceService(resources);
        registry = new HeadFallbackMagicRegistry(storage, Mapping.create(new RequestMappingHandlerMapping()), false, "");
        var handler = new RequestHandler(new MagicConfiguration(), registry);
        registry.setHandler(handler);
        magicHandler = new HandlerMethod(handler, RequestHandler.class.getMethod("invoke",
                MagicHttpServletRequest.class, MagicHttpServletResponse.class, Map.class, Map.class, Map.class));
        var whiteList = new WhiteListProp();
        whiteList.setWhiteList(List.of("/validation/legacy/**"));
        platformSession = mock(PlatformSessionMagicModule.class);
        relayProperties = new DataPushRegistryProperties();
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("registry", registry);
        access = new GlobalInterceptor(whiteList, platformSession, beans.getBeanProvider(RequestMagicDynamicRegistry.class),
                new GlobalExceptionHandler(), relayProperties);
    }

    @Test
    void readsTheMethodSpecificPolicyAndRequiresTheDeclaredRealm() {
        register("read", "GET", "/resource", "PUBLIC");
        register("head", "HEAD", "/resource", "PUBLIC");
        register("write", "POST", "/resource", "TENANT_SESSION");
        register("operator", "GET", "/legacy/operator", "IDAAS_PLATFORM_SESSION");
        register("default", "GET", "/business", null);

        try (var tenant = mockStatic(StpUtil.class)) {
            assertThat(preHandle("GET", "/validation/resource")).isTrue();
            assertThat(preHandle("HEAD", "/validation/resource")).isTrue();
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

    @ParameterizedTest
    @ValueSource(strings = {"SESSION_LOGIN", "SESSION_LOGOUT"})
    void sessionLifecycleStartsOnControlWithNoSessionLookup(String policy) {
        var api = register("session", "POST", "/session", policy);
        var request = request("POST", "/validation/session");
        request.addHeader("token", "obsolete-test-token");
        var response = new MockHttpServletResponse();
        var tenantAccess = mock(TenantAccessService.class);
        var accounts = mock(TenantIdentityQueryService.class);
        var tenantWeb = new TenantWebInterceptor(tenantAccess, new TenantProperties(), accounts);
        try (var session = mockStatic(StpUtil.class)) {
            TenantContext.bind("previous-request");
            assertThat(access.matchedMagicAccess(request, magicHandler)).isEqualTo(MagicAccessPolicy.valueOf(policy));
            assertThat(GlobalInterceptor.verifiedMagicAccess(request)).isNull();
            assertThat(access.preHandle(request, response, magicHandler)).isTrue();
            assertThat(tenantWeb.preHandle(request, response, magicHandler)).isTrue();
            assertThat(TenantContext.usesControlDatabase()).isTrue();
            assertThat(TenantContext.getTenantId()).isNull();
            var execution = RequestEntity.create().info(api)
                    .request(new MagicJakartaHttpServletRequest(request, null));
            assertThat(access.preHandle(execution)).isNull();
            assertThat(GlobalInterceptor.hasVerifiedMagicPolicy(execution)).isTrue();
            session.verifyNoInteractions();
            verifyNoInteractions(tenantAccess, accounts, platformSession);
            api.setMethod("GET");
            assertThatThrownBy(() -> storage.write(api)).isInstanceOf(IllegalArgumentException.class);
        } finally {
            tenantWeb.afterCompletion(request, response, magicHandler, null);
        }
        assertThat(TenantContext.usesControlDatabase()).isFalse();
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

    @ParameterizedTest
    @ValueSource(strings = {"PUBLIC", "IDAAS_PLATFORM_SESSION", "TENANT_SESSION"})
    void declaredPolicyCannotExecuteWithoutMvcVerification(String policy) {
        var resource = register("without-mvc", "GET", "/withoutmvc", policy);
        var request = new MagicJakartaHttpServletRequest(request("GET", "/validation/withoutmvc"), null);
        var execution = RequestEntity.create().info(resource).request(request);
        when(platformSession.isLogin()).thenReturn(true);
        try (var tenant = mockStatic(StpUtil.class)) {
            tenant.when(StpUtil::isLogin).thenReturn(true);
            assertFailureEnvelope(access.preHandle(execution), 403);
            assertThat(GlobalInterceptor.hasVerifiedMagicPolicy(execution)).isFalse();
        }
    }

    @Test
    void platformPolicyOutsideIdaasUsesControlScopeEvenWithAnIndustrySession() {
        register("operator-scope", "GET", "/operator", "IDAAS_PLATFORM_SESSION");
        var request = request("GET", "/validation/operator");
        var response = new MockHttpServletResponse();
        var accounts = mock(TenantIdentityQueryService.class);
        var routing = new TenantWebInterceptor(mock(TenantAccessService.class), new TenantProperties(), accounts);
        when(platformSession.isLogin()).thenReturn(true);
        try (var tenant = mockStatic(StpUtil.class)) {
            tenant.when(StpUtil::isLogin).thenReturn(true);
            assertThat(access.preHandle(request, response, magicHandler)).isTrue();
            try {
                assertThat(routing.preHandle(request, response, magicHandler)).isTrue();
                assertThat(TenantContext.usesControlDatabase()).isTrue();
                assertThat(TenantContext.getTenantId()).isNull();
                verifyNoInteractions(accounts);
                tenant.verifyNoInteractions();
            } finally {
                routing.afterCompletion(request, response, magicHandler, null);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
            "idaas, /idaas, /industry",
            "published, /dws/flowserve/published, /__tenant/other-tenant/industry"
    })
    void industryPolicyUsesTheAuthenticatedTenantDespiteLegacyRouteRules(String group, String prefix, String path) {
        var api = api("industry-scope", "GET", path, "TENANT_SESSION");
        api.setGroupId(group);
        assertThat(registry.register(storage.read(storage.write(api)))).isTrue();
        var request = request("GET", prefix + path);
        var response = new MockHttpServletResponse();
        var tenantAccess = mock(TenantAccessService.class);
        var accounts = mock(TenantIdentityQueryService.class);
        var routing = new TenantWebInterceptor(tenantAccess, new TenantProperties(), accounts);
        var token = new SaSession("validation-token");
        token.set("authRealm", "tenant-local-v1").set("tenantId", "session-tenant");
        when(accounts.isLocal("session-tenant")).thenReturn(true);
        when(accounts.find("session-tenant", "validation-user")).thenReturn(Map.of("status", 0));
        try (var tenant = mockStatic(StpUtil.class)) {
            tenant.when(StpUtil::isLogin).thenReturn(true);
            tenant.when(StpUtil::getTokenSession).thenReturn(token);
            tenant.when(StpUtil::getLoginId).thenReturn("validation-user");
            assertThat(access.preHandle(request, response, magicHandler)).isTrue();
            try {
                assertThat(routing.preHandle(request, response, magicHandler)).isTrue();
                assertThat(TenantContext.usesControlDatabase()).isFalse();
                assertThat(TenantContext.getTenantId()).isEqualTo("session-tenant");
                verifyNoInteractions(tenantAccess, platformSession);
            } finally {
                routing.afterCompletion(request, response, magicHandler, null);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"IDAAS_PLATFORM_SESSION", "TENANT_SESSION"})
    void changingTheSessionRealmDuringReloadRequiresANewRequest(String originalPolicy) {
        String replacementPolicy = "TENANT_SESSION".equals(originalPolicy) ? "IDAAS_PLATFORM_SESSION" : "TENANT_SESSION";
        register("session-scope", "GET", "/session", originalPolicy);
        var request = request("GET", "/validation/session");
        var response = new MockHttpServletResponse();
        when(platformSession.isLogin()).thenReturn(true);
        try (var tenant = mockStatic(StpUtil.class)) {
            tenant.when(StpUtil::isLogin).thenReturn(true);
            assertThat(access.preHandle(request, response, magicHandler)).isTrue();
            var replacement = register("session-scope", "GET", "/session", replacementPolicy);
            var servlet = new MagicJakartaHttpServletRequest(request, null);
            var execution = RequestEntity.create().info(replacement).request(servlet);
            assertFailureEnvelope(access.preHandle(execution), 403);
            assertThat(GlobalInterceptor.hasVerifiedMagicPolicy(execution)).isFalse();

            var nextRequest = request("GET", "/validation/session");
            assertThat(access.preHandle(nextRequest, response, magicHandler)).isTrue();
            var nextServlet = new MagicJakartaHttpServletRequest(nextRequest, null);
            assertThat(access.preHandle(RequestEntity.create().info(replacement).request(nextServlet))).isNull();
        }
    }

    @Test
    void verifiedPublicResourceUsesControlScopeAndCannotExecuteAProtectedReplacementInThatScope() {
        String path = "/validation/public";
        var resource = register("public-scope", "GET", "/public", "PUBLIC");
        var request = request("GET", path);
        var response = new MockHttpServletResponse();
        var accounts = mock(TenantIdentityQueryService.class);
        var routing = new TenantWebInterceptor(mock(TenantAccessService.class), new TenantProperties(), accounts);
        try (var tenant = mockStatic(StpUtil.class)) {
            tenant.when(StpUtil::isLogin).thenThrow(new IllegalStateException("stale session"));
            assertThat(access.preHandle(request, response, magicHandler)).isTrue();
            assertThat(routing.preHandle(request, response, magicHandler)).isTrue();
            try {
                assertThat(TenantContext.usesControlDatabase()).isTrue();
                assertThat(TenantContext.getTenantId()).isNull();
                verifyNoInteractions(accounts);
                tenant.verifyNoInteractions();
                tenant.reset();
                tenant.when(StpUtil::isLogin).thenReturn(true);
                var servlet = new MagicJakartaHttpServletRequest(request, null);
                var execution = RequestEntity.create().info(resource).request(servlet);
                assertThat(access.preHandle(execution)).isNull();
                var protectedEntry = register("public-scope", "GET", "/public", "TENANT_SESSION");
                assertFailureEnvelope(access.preHandle(execution.info(protectedEntry)), 403);
            } finally {
                routing.afterCompletion(request, response, magicHandler, null);
            }
        }
    }

    @Test
    void resultProviderUsesVerifiedExecutionWhileLegacyTokenCheckIsEnabled() {
        var resource = register("brand", "GET", "/brand", "PUBLIC");
        var request = request("GET", "/validation/brand");
        var magicRequest = new MagicJakartaHttpServletRequest(request, null);
        var execution = RequestEntity.create().info(resource).request(magicRequest);
        var otherExecution = RequestEntity.create().info(resource).request(magicRequest);
        var provider = new CustomJsonValueProvider();
        ReflectionTestUtils.setField(provider, "whiteListProp", new WhiteListProp());
        var payload = Map.of("title", "测试品牌");

        try (var spring = mockStatic(SpringUtil.class); var tenant = mockStatic(StpUtil.class)) {
            spring.when(() -> SpringUtil.getProperty("data-trading-parent.config.checkToken")).thenReturn("true");
            assertThat(access.preHandle(request, new MockHttpServletResponse(), magicHandler)).isTrue();
            assertThat(GlobalInterceptor.hasVerifiedMagicPolicy(execution)).isFalse();
            assertThat(provider.buildResult(execution, 0, "success", payload)).isInstanceOfSatisfying(
                    CommonResponse.class, result -> assertThat(result.getCode()).isEqualTo(100120));

            assertThat(access.preHandle(execution)).isNull();
            assertThat(GlobalInterceptor.hasVerifiedMagicPolicy(otherExecution)).isFalse();
            assertThat(provider.buildResult(execution, 0, "success", payload)).isInstanceOfSatisfying(
                    Map.class, result -> {
                        assertThat(result.get("code")).isEqualTo(0);
                        assertThat(result.get("data")).isEqualTo(payload);
                    });

            var replacement = register("brand", "GET", "/brand", "TENANT_SESSION");
            assertFailureEnvelope(access.preHandle(execution.info(replacement)), 100120);
            assertThat(GlobalInterceptor.hasVerifiedMagicPolicy(execution)).isFalse();
        }
    }

    @Test
    void relayPolicyChecksOnlyTheRelayCredentialBeforeMagicExecutes() {
        String route = "/validation/legacy/callback";
        var resource = register("callback", "POST", "/legacy/callback", "HAITONG_RELAY");
        var request = request("POST", route);
        var response = new MockHttpServletResponse();

        assertRelayError(() -> access.preHandle(request, response, magicHandler), HttpStatus.SERVICE_UNAVAILABLE);
        relayProperties.setSharedKey("validation-key");
        try (var tenant = mockStatic(StpUtil.class)) {
            tenant.when(StpUtil::isLogin).thenReturn(true);
            when(platformSession.isLogin()).thenReturn(true);
            assertRelayError(() -> access.preHandle(request, response, magicHandler), HttpStatus.UNAUTHORIZED);
            assertThat(GlobalInterceptor.verifiedMagicAccess(request)).isNull();
        }

        request.addHeader(RelayRequestAuthentication.KEY_HEADER, "validation-key");
        assertThat(access.preHandle(request, response, magicHandler)).isTrue();
        assertThat(GlobalInterceptor.verifiedMagicAccess(request)).isEqualTo(MagicAccessPolicy.HAITONG_RELAY);
        var magicRequest = new MagicJakartaHttpServletRequest(request, null);
        var execution = RequestEntity.create().info(resource).request(magicRequest);
        assertThat(access.preHandle(execution)).isNull();

        var tenantAccess = mock(TenantAccessService.class);
        var accounts = mock(TenantIdentityQueryService.class);
        var routing = new TenantWebInterceptor(tenantAccess, new TenantProperties(), accounts);
        try (var tenant = mockStatic(StpUtil.class)) {
            tenant.when(StpUtil::isLogin).thenReturn(true);
            TenantContext.bind("unrelated-tenant");
            try {
                assertThat(routing.preHandle(request, response, magicHandler)).isTrue();
                assertThat(TenantContext.usesControlDatabase()).isTrue();
                assertThat(TenantContext.getTenantId()).isNull();
                verifyNoInteractions(tenantAccess, accounts);
            } finally {
                routing.afterCompletion(request, response, magicHandler, null);
            }
            assertThat(TenantContext.usesControlDatabase()).isFalse();
        }

        var replacement = register("callback", "POST", "/legacy/callback", "TENANT_SESSION");
        assertFailureEnvelope(access.preHandle(execution.info(replacement)), 403);

        var unverifiedRequest = new MagicJakartaHttpServletRequest(request("POST", route), null);
        assertFailureEnvelope(access.preHandle(RequestEntity.create().info(resource).request(unverifiedRequest)), 403);
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

        var relay = api("callback", "POST", "/callback", "HAITONG_RELAY");
        assertThat(MagicAccessPolicy.declared(storage.read(storage.write(relay)))).isEqualTo(MagicAccessPolicy.HAITONG_RELAY);
        relay.setMethod("GET");
        assertThatThrownBy(() -> storage.write(relay)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void implicitHeadUsesTheGetResourceAndItsPolicyWhenNoExplicitHeadIsRegistered() {
        var get = api("read", "GET", "/resource", "TENANT_SESSION");
        get.setOption(List.of(new Option("access", "TENANT_SESSION"), new Option("head", "true")));
        assertThat(registry.register(storage.read(storage.write(get)))).isTrue();
        var request = request("HEAD", "/validation/resource");
        var magicRequest = new MagicJakartaHttpServletRequest(request, null);
        assertThat(registry.getApiInfoFromRequest(magicRequest).getId()).isEqualTo("read");
        try (var tenant = mockStatic(StpUtil.class)) {
            assertDenied(() -> preHandle("HEAD", "/validation/resource"));
            tenant.when(StpUtil::isLogin).thenReturn(true);
            assertThat(access.preHandle(request, new MockHttpServletResponse(), magicHandler)).isTrue();
            assertThat(GlobalInterceptor.verifiedMagicAccess(request)).isEqualTo(MagicAccessPolicy.TENANT_SESSION);
        }

        register("explicit", "HEAD", "/resource", "PUBLIC");
        assertThat(registry.getApiInfoFromRequest(magicRequest).getId()).isEqualTo("explicit");
        request.removeAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        assertThat(registry.getApiInfoFromRequest(magicRequest)).isNull();
    }

    @Test
    void resourceStorageOnlyAcceptsOneHeadOptionOnGet() {
        var api = api("read", "GET", "/resource", null);
        api.setOption(List.of(new Option("head", "yes")));
        assertThatThrownBy(() -> storage.write(api)).isInstanceOf(IllegalArgumentException.class);
        api.setOption(List.of(new Option("head", "true"), new Option("head", "false")));
        assertThatThrownBy(() -> storage.write(api)).isInstanceOf(IllegalArgumentException.class);
        api.setOption(List.of(new Option("head", "true")));
        api.setMethod("HEAD");
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

    private static void assertRelayError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(status));
    }
}
