package com.linewell.dataelement.platform.web.security;

import com.linewell.dataelement.integration.workflow.WarmFlowDesignerAccess;
import org.dromara.warm.flow.ui.controller.WarmFlowController;
import org.dromara.warm.flow.ui.controller.WarmFlowUiController;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.dataservice.flowserve.FlowServeRuntimeAdapter;
import com.linewell.dataelement.feature.identity.application.TenantIdentityQueryService;
import com.linewell.dataelement.integration.flowserve.FlowServeRuntimeController;
import com.linewell.dataelement.integration.nifi.canvas.controller.NifiNativeUiProxyController;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.magic.runtime.result.MagicApiResultProvider;
import com.linewell.dataelement.magic.runtime.route.HeadFallbackMagicRegistry;
import com.linewell.dataelement.magic.runtime.route.MagicJsonBody;
import com.linewell.dataelement.magic.runtime.route.PublishedFlowRoutes;
import com.linewell.dataelement.magic.runtime.route.RegisteredMagicFunctionInvoker;
import com.linewell.dataelement.magic.runtime.security.MagicAccessPolicy;
import com.linewell.dataelement.magic.runtime.security.PolicyAwareApiResourceStorage;
import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.audit.AuditWebInterceptor;
import com.linewell.dataelement.platform.configuration.MagicBootConfiguration;
import com.linewell.dataelement.platform.tenant.api.PlatformSessionMagicModule;
import com.linewell.dataelement.platform.tenant.api.IamMfaMagicModule;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.protocol.IdentityApiMagicModule;
import com.linewell.dataelement.platform.tenant.protocol.IdentityReceiverAccess;
import com.linewell.dataelement.platform.tenant.protocol.LegacySsoAccess;
import com.linewell.dataelement.platform.tenant.web.TenantWebInterceptor;
import com.linewell.dataelement.platform.web.config.WebConfiguration;
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.UrlPathHelper;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.ssssssss.magicapi.core.config.MagicAPIProperties;
import org.ssssssss.magicapi.core.config.MagicConfiguration;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.model.Group;
import org.ssssssss.magicapi.core.model.Option;
import org.ssssssss.magicapi.core.service.MagicResourceService;
import org.ssssssss.magicapi.core.service.impl.RequestMagicDynamicRegistry;
import org.ssssssss.magicapi.core.servlet.MagicHttpServletRequest;
import org.ssssssss.magicapi.core.servlet.MagicHttpServletResponse;
import org.ssssssss.magicapi.core.web.RequestHandler;
import org.ssssssss.magicapi.servlet.jakarta.MagicJakartaHttpServletRequest;
import org.ssssssss.magicapi.utils.Mapping;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class MagicAccessPolicyTest {
    private PolicyAwareApiResourceStorage storage;
    private MagicResourceService resources;
    private RequestMagicDynamicRegistry registry;
    private HandlerMethod magicHandler;
    private PlatformSessionMagicModule platformSession;
    private RegisteredMagicFunctionInvoker functions;
    private IamMfaMagicModule mfa;
    private IdentityApiMagicModule identityApi;
    private IdentityReceiverAccess identityReceiver;
    private LegacySsoAccess legacySso;
    private GlobalInterceptor access;

    @BeforeEach
    void configureRuntimeRegistry() throws Exception {
        storage = new PolicyAwareApiResourceStorage("");
        resources = mock(MagicResourceService.class);
        when(resources.getGroupPath("validation")).thenReturn("/validation");
        when(resources.getGroupPath("idaas")).thenReturn("/idaas");
        when(resources.getGroupPath("published")).thenReturn("/dws/flowserve/published");
        var flowGroup = new Group();
        flowGroup.setAttribute("flowservePublishedSegment", "services");
        when(resources.getGroup(PublishedFlowRoutes.GROUP_ID)).thenReturn(flowGroup);
        when(resources.getGroupPath(PublishedFlowRoutes.GROUP_ID)).thenReturn("/validation/flow");
        storage.setMagicResourceService(resources);
        registry = new HeadFallbackMagicRegistry(storage, Mapping.create(new RequestMappingHandlerMapping()), false, "");
        var handler = new RequestHandler(new MagicConfiguration(), registry);
        registry.setHandler(handler);
        magicHandler = new HandlerMethod(handler, RequestHandler.class.getMethod("invoke",
                MagicHttpServletRequest.class, MagicHttpServletResponse.class, Map.class, Map.class, Map.class));
        var whiteList = new WhiteListProp();
        whiteList.setWhiteList(List.of("/validation/legacy/**"));
        platformSession = mock(PlatformSessionMagicModule.class);
        functions = mock(RegisteredMagicFunctionInvoker.class);
        mfa = new IamMfaMagicModule();
        identityApi = mock(IdentityApiMagicModule.class);
        identityReceiver = mock(IdentityReceiverAccess.class);
        legacySso = mock(LegacySsoAccess.class);
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("registry", registry);
        beans.registerSingleton("resources", resources);
        beans.registerSingleton("functions", functions);
        beans.registerSingleton("mfa", mfa);
        beans.registerSingleton("identityApi", identityApi);
        beans.registerSingleton("identityReceiver", identityReceiver);
        beans.registerSingleton("legacySso", legacySso);
        access = new GlobalInterceptor(whiteList, platformSession, beans.getBeanProvider(RequestMagicDynamicRegistry.class),
                beans.getBeanProvider(MagicResourceService.class), new GlobalExceptionHandler(),
                beans.getBeanProvider(RegisteredMagicFunctionInvoker.class), beans.getBeanProvider(IamMfaMagicModule.class),
                beans.getBeanProvider(IdentityApiMagicModule.class), beans.getBeanProvider(IdentityReceiverAccess.class),
                beans.getBeanProvider(LegacySsoAccess.class), new PublishedFlowRoutes(resources, "/old/published"),
                new MagicApiResultProvider(), new WarmFlowDesignerAccess(new MockEnvironment()));
    }

    @Test
    void warmFlowProtocolMatchesOnlyItsRegisteredControllersAndPackagedResources() throws Exception {
        var environment = new MockEnvironment();
        var workflow = new WarmFlowDesignerAccess(environment);
        var controller = new HandlerMethod(new WarmFlowController(), "handlerType");
        var config = new HandlerMethod(new WarmFlowUiController(), "config");
        var resource = new ResourceHttpRequestHandler();
        resource.setLocations(List.of(new ClassPathResource("META-INF/resources/warm-flow-ui/"),
                new ClassPathResource("warm-flow-ui/")));
        assertThat(workflow.matches(controller)).isFalse();
        assertThat(workflow.matches(resource)).isFalse();

        environment.setProperty("warm-flow.designer-protocol-access", "true");
        assertThat(workflow.matches(controller)).isTrue();
        assertThat(workflow.matches(config)).isTrue();
        assertThat(workflow.matches(resource)).isTrue();
        assertThat(workflow.matches(magicHandler)).isFalse();
        resource.setLocations(List.of(new ClassPathResource("META-INF/resources/warm-flow-ui/"),
                new ClassPathResource("static/")));
        assertThat(workflow.matches(resource)).isFalse();
        environment.setProperty("warm-flow.designer-protocol-access", "false");
        assertThat(workflow.matches(controller)).isFalse();
    }

    @Test
    void matchedRuntimeProtocolRequiresItsOwnTokenBeforeDeployment() throws Exception {
        var runtime = mock(FlowServeRuntimeAdapter.class);
        var mvc = MockMvcBuilders.standaloneSetup(new FlowServeRuntimeController(runtime, "validation-token"))
                .addPlaceholderValue("flowserve.runtime.protocol-base-path", "/validation/runtime")
                .addPlaceholderValue("flowserve.runtime.protocol-deploy-path", "/release")
                .addPlaceholderValue("flowserve.runtime.protocol-health-path", "/status")
                .addInterceptors(access)
                .build();
        var route = "/validation/runtime/release";
        var body = "{\"resourceId\":\"fixture\",\"path\":\"/fixture\"}";
        mvc.perform(get("/validation/runtime/status")).andExpect(status().isUnauthorized());
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(delete(route + "/fixture").header("X-FlowServe-Deploy-Token", "invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(runtime);

        mvc.perform(get("/validation/runtime/status").header("X-FlowServe-Deploy-Token", "validation-token"))
                .andExpect(status().isOk());
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-FlowServe-Deploy-Token", "validation-token")).andExpect(status().isOk());
        mvc.perform(delete(route + "/fixture").header("X-FlowServe-Deploy-Token", "validation-token"))
                .andExpect(status().isOk());
        verify(runtime).deployFromRequest(Map.of("resourceId", "fixture", "path", "/fixture"));
        verify(runtime).undeployFromRequest("fixture");
        mvc.perform(get("/validation/runtime/unmatched")).andExpect(status().isNotFound());
    }

    @Test
    void mvcUsesConfiguredEditorTreeAndKeepsProxyPathsInTheInterceptorChain() {
        var properties = new MagicAPIProperties();
        properties.setWeb("/tools/editor/**");
        var registry = new InterceptorRegistry() {
            List<Object> registered() { return getInterceptors(); }
        };
        new WebConfiguration(access, mock(TenantWebInterceptor.class), mock(AuditWebInterceptor.class), properties, "/api/**")
                .addInterceptors(registry);
        assertThat(registry.registered()).hasSize(3);
        for (Object handler : registry.registered().subList(1, 3)) {
            var mapped = (MappedInterceptor) handler;
            for (String path : List.of("/tools/editor/index.html", "/tools/editor/public", "/tools/editor-other/check",
                    "/api/web/index.html", "/nifi-ui/index", "/nifi-api/flow/about")) {
                var request = new MockHttpServletRequest("GET", path);
                UrlPathHelper.defaultInstance.resolveAndCacheLookupPath(request);
                assertThat(mapped.matches(request)).isEqualTo(!path.startsWith("/tools/editor/"));
            }
        }
    }

    @Test
    void nativeNifiProxyUsesMatchedProtocolAndConfiguredUpstreamPaths() throws Exception {
        var nifi = mock(NifiClient.class);
        when(nifi.proxy(any(), any(), any(), isNull())).thenReturn(ResponseEntity.ok(new byte[] {1, 2}));
        var tenants = mock(TenantAccessService.class);
        var accounts = mock(TenantIdentityQueryService.class);
        var tenant = new TenantWebInterceptor(tenants, new TenantProperties(), accounts, functions);
        var proxy = new NifiNativeUiProxyController(nifi, "/node-ui", "/node-api", "/native-ui", "/native-api");
        var mvc = MockMvcBuilders.standaloneSetup(proxy)
                .addPlaceholderValue("nifi.native-proxy.ui-path", "/node-ui")
                .addPlaceholderValue("nifi.native-proxy.api-path", "/node-api")
                .addInterceptors(access, tenant)
                .build();
        try (var session = mockStatic(StpUtil.class)) {
            TenantContext.bind("previous-request");
            mvc.perform(get("/host/node-ui/asset?version=1").contextPath("/host")).andExpect(status().isOk());
            mvc.perform(get("/node-api/flow/about")).andExpect(status().isOk());
            mvc.perform(get("/node-ui")).andExpect(status().isOk());
            assertThat(TenantContext.getTenantId()).isNull();
            session.verifyNoInteractions();
            verifyNoInteractions(tenants, accounts, platformSession);
        } finally {
            TenantContext.clear();
        }
        verify(nifi).proxy(eq(HttpMethod.GET), eq("/native-ui/asset?version=1"), any(), isNull());
        verify(nifi).proxy(eq(HttpMethod.GET), eq("/native-api/flow/about"), any(), isNull());
        verify(nifi).proxy(eq(HttpMethod.GET), eq("/native-ui"), any(), isNull());
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
        var tenantWeb = new TenantWebInterceptor(tenantAccess, new TenantProperties(), accounts, functions);
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

    @Test
    void bearerChecksSourceBeforeCredentialAndOnlyExposesTheVerifiedCaller() {
        var resource = register("bearer", "GET", "/partner", "IDAAS_API_BEARER");
        var request = request("GET", "/validation/partner");
        var execution = RequestEntity.create().info(resource).request(new MagicJakartaHttpServletRequest(request, null));
        assertThat(access.preHandle(request, new MockHttpServletResponse(), magicHandler)).isTrue();

        String checkId = "bf142250e1a35995a1041adb40ea5bf4";
        var arguments = Map.<String, Object>of("checkRequestSource", true);
        var denied = MagicScript.create("exit 429, '接口请求超过当前配额';", null).execute(new MagicScriptContext());
        when(functions.invoke(checkId, arguments)).thenAnswer(call -> {
            assertThat(TenantContext.usesControlDatabase()).isTrue();
            return denied;
        });
        assertThat(access.preHandle(execution)).isInstanceOfSatisfying(ResponseEntity.class, response ->
                assertThat(((Map<?, ?>) response.getBody()).get("code")).isEqualTo(429));
        verifyNoInteractions(identityApi);

        doReturn(true).when(functions).invoke(checkId, arguments);
        var caller = Map.<String, Object>of("allowed", true, "appId", "validation");
        when(identityApi.authorize(request)).thenReturn(caller).thenThrow(new AccessDeniedException("invalid"));
        assertThat(access.preHandle(execution)).isNull();
        assertThat(GlobalInterceptor.verifiedIdentityProof(request, MagicAccessPolicy.IDAAS_API_BEARER)).isSameAs(caller);
        assertThat(TenantContext.usesControlDatabase()).isFalse();
        assertThat(access.preHandle(execution)).isInstanceOfSatisfying(ResponseEntity.class, response ->
                assertThat(((Map<?, ?>) response.getBody()).get("code")).isEqualTo(403));
        assertThatThrownBy(() -> GlobalInterceptor.verifiedIdentityProof(request, MagicAccessPolicy.IDAAS_API_BEARER))
                .isInstanceOf(IllegalStateException.class);
        verify(identityApi, org.mockito.Mockito.times(2)).authorize(request);
    }

    @Test
    void bearerPolicyOnlyAcceptsReadMethods() {
        var api = api("bearer", "GET", "/partner", "IDAAS_API_BEARER");
        assertThat(MagicAccessPolicy.declared(storage.read(storage.write(api)))).isEqualTo(MagicAccessPolicy.IDAAS_API_BEARER);
        api.setMethod("POST");
        assertThatThrownBy(() -> storage.write(api)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signedReceiverUsesOnlyTheBoundRequestProofAndRejectsBeforeExecution() throws Exception {
        var resource = register("receiver", "POST", "/receiver", "IDAAS_RECEIVER_SIGNATURE");
        var request = request("POST", "/validation/receiver");
        var body = Map.of("protocol", "IDAAS/2");
        var execution = RequestEntity.create().info(resource).request(new MagicJakartaHttpServletRequest(request, null)).setRequestBody(body);
        when(platformSession.isLogin()).thenReturn(true);
        assertThat(access.preHandle(request, new MockHttpServletResponse(), magicHandler)).isTrue();
        var proof = Map.<String, Object>of("request", Map.of("keyId", "validation"));
        when(identityReceiver.authorize(body)).thenThrow(new BizException(403, "接收签名、有效期或接入状态无效"))
                .thenReturn(proof);
        assertThat(access.preHandle(execution)).isInstanceOfSatisfying(ResponseEntity.class, response ->
                assertThat(((Map<?, ?>) response.getBody()).get("code")).isEqualTo(403));
        assertThatThrownBy(() -> GlobalInterceptor.verifiedIdentityProof(request, MagicAccessPolicy.IDAAS_RECEIVER_SIGNATURE))
                .isInstanceOf(IllegalStateException.class);

        assertThat(access.preHandle(execution)).isNull();
        assertThat(GlobalInterceptor.verifiedIdentityProof(request, MagicAccessPolicy.IDAAS_RECEIVER_SIGNATURE)).isSameAs(proof);
        resource.setMethod("GET");
        assertThatThrownBy(() -> storage.write(resource)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ssoRedemptionMustHaveUpstreamProofBeforeTheLoginScript() {
        var resource = register("sso", "GET", "/sso", "LEGACY_SSO_REDEEM");
        var request = request("GET", "/validation/sso");
        var parameters = Map.<String, Object>of("code", "validation-code");
        var execution = RequestEntity.create().info(resource).request(new MagicJakartaHttpServletRequest(request, null))
                .parameters(parameters);
        assertThat(access.preHandle(request, new MockHttpServletResponse(), magicHandler)).isTrue();
        var proof = Map.<String, Object>of("user", Map.of("id", "validation"), "appId", "validation-app");
        when(legacySso.authorizeRedemption(parameters)).thenThrow(new BizException(500, "获取token令牌报错"))
                .thenReturn(proof);
        assertThat(access.preHandle(execution)).isInstanceOfSatisfying(ResponseEntity.class, response ->
                assertThat(((Map<?, ?>) response.getBody()).get("code")).isEqualTo(500));
        assertThat(access.preHandle(execution)).isNull();
        assertThat(GlobalInterceptor.verifiedIdentityProof(request, MagicAccessPolicy.LEGACY_SSO_REDEEM)).isSameAs(proof);
        assertThatThrownBy(() -> GlobalInterceptor.verifiedIdentityProof(request, MagicAccessPolicy.LEGACY_SSO_TICKET))
                .isInstanceOf(IllegalStateException.class);
        verify(legacySso, org.mockito.Mockito.times(2)).authorizeRedemption(parameters);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEGACY_SSO_TICKET", "LEGACY_SSO_REDEEM"})
    void legacySsoPoliciesAreReservedForGet(String policy) {
        var resource = api("sso", "GET", "/sso", policy);
        assertThat(MagicAccessPolicy.declared(storage.read(storage.write(resource))).name()).isEqualTo(policy);
        for (String method : List.of("HEAD", "POST")) {
            resource.setMethod(method);
            assertThatThrownBy(() -> storage.write(resource)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void declaredTenantSessionPreservesTheResourcesMagic401BeforeTheScriptRuns() {
        var api = api("session-required", "POST", "/session", "TENANT_SESSION");
        String message = "请使用当前行业会话";
        api.setOption(List.of(new Option("access", "TENANT_SESSION"), new Option("sessionRequiredMessage", message)));
        var stored = storage.read(storage.write(api));
        assertThat(registry.register(stored)).isTrue();
        var request = request("POST", "/validation/session");
        var execution = RequestEntity.create().info(stored).request(new MagicJakartaHttpServletRequest(request, null));
        try (var tenant = mockStatic(StpUtil.class)) {
            assertThat(access.preHandle(request, new MockHttpServletResponse(), magicHandler)).isTrue();
            assertThat(access.preHandle(execution)).isInstanceOfSatisfying(ResponseEntity.class, response -> {
                assertThat(response.getStatusCode().value()).isEqualTo(200);
                assertThat(response.getBody()).isInstanceOfSatisfying(Map.class, body -> {
                    assertThat(body.get("code")).isEqualTo(401);
                    assertThat(body.get("msg")).isEqualTo(message);
                    assertThat(body.get("data")).isNull();
                });
            });

            tenant.when(StpUtil::isLogin).thenReturn(true);
            assertThat(access.preHandle(execution)).isNull();

            var edited = api("session-required", "POST", "/session", "TENANT_SESSION");
            edited.setOption(List.of(new Option("access", "TENANT_SESSION"), new Option("sessionRequiredMessage", "提示已更新")));
            execution.info(edited);
            assertFailureEnvelope(access.preHandle(execution), 403);
        }
    }

    @Test
    void onlyTenantResourcesCanPreserveAnUnambiguousSessionRequiredMessage() {
        var api = api("session-required", "GET", "/session", "PUBLIC");
        api.setOption(List.of(new Option("access", "PUBLIC"), new Option("sessionRequiredMessage", "会话已过期")));
        assertThatThrownBy(() -> storage.write(api)).isInstanceOf(IllegalArgumentException.class);
        api.setOption(List.of(new Option("access", "TENANT_SESSION"), new Option("sessionRequiredMessage", " ")));
        assertThatThrownBy(() -> storage.write(api)).isInstanceOf(IllegalArgumentException.class);
        api.setOption(List.of(new Option("sessionRequiredMessage", "会话已过期")));
        assertThatThrownBy(() -> storage.write(api)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLIC", "IDAAS_PLATFORM_SESSION", "IDAAS_ACCOUNT_SESSION", "TENANT_SESSION"})
    void declaredPolicyCannotExecuteWithoutMvcVerification(String policy) {
        var resource = register("without-mvc", "GET", "/withoutmvc", policy);
        var request = new MagicJakartaHttpServletRequest(request("GET", "/validation/withoutmvc"), null);
        var execution = RequestEntity.create().info(resource).request(request);
        when(platformSession.isLogin()).thenReturn(true);
        try (var tenant = mockStatic(StpUtil.class)) {
            tenant.when(StpUtil::isLogin).thenReturn(true);
            assertFailureEnvelope(access.preHandle(execution), 403);
        }
    }

    @Test
    void accountSessionChecksControlScopeAndExposesContextOnlyAfterAuthorization() {
        var resource = register("account", "GET", "/account", "IDAAS_ACCOUNT_SESSION");
        var request = request("GET", "/validation/account");
        var execution = RequestEntity.create().info(resource)
                .request(new MagicJakartaHttpServletRequest(request, null));
        assertThat(access.preHandle(request, new MockHttpServletResponse(), magicHandler)).isTrue();
        String check = "4d54c6e215625d9ca6220ca6477b5d6c";
        var expected = Map.of("userId", "validation-account", "actorKind", "AUTH_ACCOUNT",
                "account", Map.of("id", "validation-account"));
        when(functions.invoke(check, Map.of())).thenAnswer(call -> {
            assertThat(TenantContext.usesControlDatabase()).isTrue();
            return expected;
        });
        assertThat(access.preHandle(execution)).isNull();
        assertThat(GlobalInterceptor.verifiedAuthAccountContext(request)).isSameAs(expected);
        assertThat(TenantContext.usesControlDatabase()).isFalse();

        var failure = MagicScript.create("exit 401, '请使用统一认证账号登录';", null).execute(new MagicScriptContext());
        doReturn(failure).when(functions).invoke(check, Map.of());
        assertThat(access.preHandle(execution)).isInstanceOfSatisfying(ResponseEntity.class, response -> {
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(((Map<?, ?>) response.getBody()).get("code")).isEqualTo(401);
        });
        assertThatThrownBy(() -> GlobalInterceptor.verifiedAuthAccountContext(request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mfaLoginChallengeCannotSubstituteForAnAuthenticatedMfaSession() {
        var resource = register("challenge", "POST", "/challenge", "MFA_LOGIN_CHALLENGE");
        var request = request("POST", "/validation/challenge");
        String challenge = mfa.challenge("AUTH_ACCOUNT", "validation-account", 3L, "LOGIN");
        var execution = RequestEntity.create().info(resource)
                .request(new MagicJakartaHttpServletRequest(request, null))
                .setRequestBody(Map.of("challenge", challenge, "realm", "platform"));
        assertThat(access.preHandle(request, new MockHttpServletResponse(), magicHandler)).isTrue();
        assertThat(access.preHandle(execution)).isNull();
        var proof = GlobalInterceptor.verifiedIdentityProof(request, MagicAccessPolicy.MFA_LOGIN_CHALLENGE);
        assertThat(proof.get("kind")).isEqualTo("AUTH_ACCOUNT");
        assertThat(proof.get("attempts")).isEqualTo(1);
        assertThatThrownBy(() -> GlobalInterceptor.verifiedIdentityProof(request, MagicAccessPolicy.MFA_SESSION))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mfa.consume(challenge)).isTrue();
        assertThat(access.preHandle(execution)).isInstanceOfSatisfying(ResponseEntity.class, response ->
                assertThat(((Map<?, ?>) response.getBody()).get("code")).isEqualTo(401));
    }

    @Test
    void platformPolicyOutsideIdaasUsesControlScopeEvenWithAnIndustrySession() {
        register("operator-scope", "GET", "/operator", "IDAAS_PLATFORM_SESSION");
        var request = request("GET", "/validation/operator");
        var response = new MockHttpServletResponse();
        var accounts = mock(TenantIdentityQueryService.class);
        var routing = new TenantWebInterceptor(mock(TenantAccessService.class), new TenantProperties(), accounts, functions);
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

    @Test
    void relocatedIdentityGroupKeepsThePlatformDefaultAndRejectsARelocatedReplacement() {
        String idaasRoot = "1ae52a0ba8565e9d820f00dec5d199af";
        var root = new Group();
        root.setParentId("0");
        var parent = new Group();
        parent.setParentId(idaasRoot);
        var leaf = new Group();
        leaf.setParentId("validation-identity-parent");
        when(resources.getGroup(idaasRoot)).thenReturn(root);
        when(resources.getGroup("validation-identity-parent")).thenReturn(parent);
        when(resources.getGroup("validation-relocated-identity")).thenReturn(leaf);
        when(resources.getGroupPath("validation-relocated-identity")).thenReturn("/validation/relocated");
        var candidate = api("legacy-identity", "GET", "/identity", null);
        candidate.setGroupId("validation-relocated-identity");
        assertThat(registry.register(storage.read(storage.write(candidate)))).isTrue();
        var path = "/validation/relocated/identity";
        var response = new MockHttpServletResponse();
        var tenantAccess = mock(TenantAccessService.class);
        var accounts = mock(TenantIdentityQueryService.class);
        var routing = new TenantWebInterceptor(tenantAccess, new TenantProperties(), accounts, functions);
        try (var tenant = mockStatic(StpUtil.class)) {
            tenant.when(StpUtil::isLogin).thenReturn(true);
            assertDenied(() -> access.preHandle(request("GET", path), response, magicHandler));
            when(platformSession.isLogin()).thenReturn(true);
            var route = request("GET", path);
            assertThat(access.matchedIdentityGroup(route, magicHandler)).isTrue();
            assertThat(GlobalInterceptor.verifiedIdentityGroup(route)).isFalse();
            assertThat(access.preHandle(route, response, magicHandler)).isTrue();
            assertThat(GlobalInterceptor.verifiedIdentityGroup(route)).isTrue();
            assertThat(GlobalInterceptor.verifiedMagicAccess(route)).isNull();
            try {
                assertThat(routing.preHandle(route, response, magicHandler)).isTrue();
                assertThat(TenantContext.usesControlDatabase()).isTrue();
                assertThat(TenantContext.getTenantId()).isNull();
                verifyNoInteractions(tenantAccess, accounts);
                tenant.verifyNoInteractions();
                var replacement = api("legacy-identity", "GET", "/identity", null);
                var execution = RequestEntity.create().info(replacement).request(new MagicJakartaHttpServletRequest(route, null));
                assertFailureEnvelope(access.preHandle(execution), 403);
            } finally {
                routing.afterCompletion(route, response, magicHandler, null);
            }
            tenant.when(StpUtil::isLogin).thenReturn(false);
            assertDenied(() -> access.preHandle(request("GET", "/idaas/nonregistered"), response, new Object()));
        }
    }

    @Test
    void cyclicIdentityGroupAncestryUsesTheOrdinarySessionDefault() {
        var loop = new Group();
        loop.setParentId("validation-cycle");
        when(resources.getGroup("validation-cycle")).thenReturn(loop);
        when(resources.getGroupPath("validation-cycle")).thenReturn("/idaas/validation-cycle");
        var candidate = api("cyclic-group", "GET", "/group", null);
        candidate.setGroupId("validation-cycle");
        assertThat(registry.register(storage.read(storage.write(candidate)))).isTrue();
        var route = request("GET", "/idaas/validation-cycle/group");
        assertThat(access.matchedIdentityGroup(route, magicHandler)).isFalse();
        try (var tenant = mockStatic(StpUtil.class)) {
            tenant.when(StpUtil::isLogin).thenReturn(true);
            assertThat(access.preHandle(route, new MockHttpServletResponse(), magicHandler)).isTrue();
            assertThat(GlobalInterceptor.verifiedIdentityGroup(route)).isFalse();
            verifyNoInteractions(platformSession);
        }
    }

    @ParameterizedTest
    @CsvSource({"/services/weather/latest, default-tenant", "/services/__tenant/target-tenant/weather/latest, target-tenant"})
    void publishedFlowUsesTheMatchedResourceForRoutingAndItsResponseEnvelope(String path, String tenantId) {
        var api = api("published-flow", "GET", path, null);
        api.setGroupId(PublishedFlowRoutes.GROUP_ID);
        assertThat(registry.register(storage.read(storage.write(api)))).isTrue();
        var request = request("GET", "/validation/flow" + path);
        var response = new MockHttpServletResponse();
        var tenantAccess = mock(TenantAccessService.class);
        when(tenantAccess.requireActiveTenant(tenantId)).thenReturn(new TenantAccessService.TenantIdentity(tenantId));
        var properties = new TenantProperties();
        properties.setDefaultTenantId("default-tenant");
        var tenantWeb = new TenantWebInterceptor(tenantAccess, properties, mock(TenantIdentityQueryService.class), functions);
        var execution = RequestEntity.create().info(api).request(new MagicJakartaHttpServletRequest(request, null));
        var results = new MagicApiResultProvider();
        var envelope = Map.of("code", 0, "data", "published");
        try (var tenant = mockStatic(StpUtil.class)) {
            assertDenied(() -> access.preHandle(request, response, magicHandler));
            assertThat(GlobalInterceptor.matchedPublishedFlow(request)).isTrue();
            assertThat(GlobalInterceptor.verifiedPublishedFlow(request)).isNull();
            tenant.when(StpUtil::isLogin).thenReturn(true);
            assertThat(access.preHandle(request, response, magicHandler)).isTrue();
            assertThat(tenantWeb.preHandle(request, response, magicHandler)).isTrue();
            assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);
            assertThat(access.preHandle(execution)).isNull();
            assertThat(results.buildResult(execution, 0, "ok", envelope)).isEqualTo(envelope);

            var replaced = api.copy();
            replaced.setId("hot-replacement");
            var different = RequestEntity.create().info(replaced).request(new MagicJakartaHttpServletRequest(request, null));
            assertThat(access.preHandle(different)).isInstanceOfSatisfying(ResponseEntity.class, denied ->
                    assertThat(denied.getBody()).isInstanceOfSatisfying(CommonResponse.class, result ->
                            assertThat(result.getCode()).isEqualTo(403)));
            assertThat(GlobalInterceptor.isPublishedFlowExecution(different)).isFalse();

            when(resources.getGroupPath(PublishedFlowRoutes.GROUP_ID)).thenReturn("/validation/refreshed");
            assertThat(access.preHandle(execution)).isInstanceOfSatisfying(ResponseEntity.class, denied ->
                    assertThat(denied.getBody()).isInstanceOfSatisfying(CommonResponse.class, result ->
                            assertThat(result.getCode()).isEqualTo(403)));
        } finally {
            tenantWeb.afterCompletion(request, response, magicHandler, null);
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
        var routing = new TenantWebInterceptor(tenantAccess, new TenantProperties(), accounts, functions);
        var token = new SaSession("validation-token");
        token.set("authRealm", "tenant-local-v1").set("tenantId", "session-tenant");
        when(accounts.isLocal("session-tenant")).thenReturn(true);
        when(accounts.find("session-tenant", "validation-user")).thenReturn(Map.of("status", 0));
        when(functions.invoke("275494036ca45d1ea4eb856c7a5bef94", Map.of("userId", "validation-user")))
                .thenReturn(Map.of("allowed", true));
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
        var routing = new TenantWebInterceptor(mock(TenantAccessService.class), new TenantProperties(), accounts, functions);
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
    void publicResourceResponseDoesNotConsultEitherSession() {
        var resource = register("brand", "GET", "/brand", "PUBLIC");
        var request = request("GET", "/validation/brand");
        var magicRequest = new MagicJakartaHttpServletRequest(request, null);
        var execution = RequestEntity.create().info(resource).request(magicRequest);
        var provider = new MagicApiResultProvider();
        var payload = Map.of("title", "测试品牌");

        try (var tenant = mockStatic(StpUtil.class)) {
            assertThat(access.preHandle(request, new MockHttpServletResponse(), magicHandler)).isTrue();
            assertThat(access.preHandle(execution)).isNull();
            assertThat(provider.buildResult(execution, 0, "success", payload)).isInstanceOfSatisfying(
                    Map.class, result -> {
                        assertThat(result.get("code")).isEqualTo(0);
                        assertThat(result.get("data")).isEqualTo(payload);
                    });
            tenant.verifyNoInteractions();
            verifyNoInteractions(platformSession);
        }
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

        var signedReceiver = api("receiver", "POST", "/receiver", "IDAAS_RECEIVER_SIGNATURE");
        assertThat(MagicAccessPolicy.declared(storage.read(storage.write(signedReceiver))))
                .isEqualTo(MagicAccessPolicy.IDAAS_RECEIVER_SIGNATURE);
        signedReceiver.setMethod("GET");
        assertThatThrownBy(() -> storage.write(signedReceiver)).isInstanceOf(IllegalArgumentException.class);
        var retired = api("retired-policy", "POST", "/retired-policy", "HAITONG_RELAY");
        assertThatThrownBy(() -> storage.write(retired)).isInstanceOf(IllegalArgumentException.class);
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

    @Test
    void jsonBodyDeclarationIsValidatedAndRetainsOnlyTheMatchedResourceBytes() throws Exception {
        var api = api("body", "POST", "/relocated/{id}", null);
        var declaration = new Option("jsonBody", "CACHED");
        api.setOption(List.of(declaration, declaration));
        assertThatThrownBy(() -> storage.write(api)).isInstanceOf(IllegalArgumentException.class);
        api.setOption(List.of(new Option("jsonBody", "UNKNOWN")));
        assertThatThrownBy(() -> storage.write(api)).isInstanceOf(IllegalArgumentException.class);
        api.setOption(List.of(declaration));
        api.setMethod("GET");
        assertThatThrownBy(() -> storage.write(api)).isInstanceOf(IllegalArgumentException.class);
        api.setMethod("POST");
        assertThat(registry.register(storage.read(storage.write(api)))).isTrue();

        var filter = new MagicBootConfiguration().magicJsonRequestCache();
        byte[] raw = new byte[70_000];
        var request = request("POST", "/app/validation/relocated/sample");
        request.setContextPath("/app");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/validation/relocated/{id}");
        request.setContentType("application/merge-patch+json");
        request.setContent(raw);
        filter.doFilter(request, new MockHttpServletResponse(), (incoming, ignored) -> {
            var cached = (ContentCachingRequestWrapper) incoming;
            var magic = new MagicJakartaHttpServletRequest(cached, null);
            assertThat(registry.getApiInfoFromRequest(magic).getId()).isEqualTo("body");
            assertThat(cached.getInputStream().readAllBytes()).isEqualTo(raw);
            assertThat(cached.getContentAsByteArray()).hasSize(65_536);
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/validation/removed");
            assertThat(registry.getApiInfoFromRequest(magic)).isNull();
            assertThat(MagicJsonBody.matched(cached)).isNull();
        });

        var missing = request("POST", "/nifi/api/connect");
        missing.setContentType("application/json");
        missing.setContent(raw);
        filter.doFilter(missing, new MockHttpServletResponse(), (incoming, ignored) -> {
            var cached = (ContentCachingRequestWrapper) incoming;
            assertThat(registry.getApiInfoFromRequest(new MagicJakartaHttpServletRequest(cached, null))).isNull();
            assertThat(cached.getInputStream().readAllBytes()).isEqualTo(raw);
            assertThat(cached.getContentAsByteArray()).isEmpty();
        });

        var form = request("POST", "/validation/relocated/sample");
        form.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        filter.doFilter(form, new MockHttpServletResponse(), (incoming, ignored) -> assertThat(incoming).isSameAs(form));
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
