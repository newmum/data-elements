package com.linewell.dataelement.platform.audit;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.magic.runtime.security.MagicAccessPolicy;
import com.linewell.dataelement.magic.runtime.security.PolicyAwareApiResourceStorage;
import com.linewell.dataelement.platform.tenant.protocol.IdentityConsentController;
import com.linewell.dataelement.platform.web.security.GlobalInterceptor;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.config.MagicAPIProperties;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.model.Option;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditParameterAdaptersTest {
    @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); }

    private MockHttpServletRequest bind() {
        var request = new MockHttpServletRequest("POST", "/validation/parameters");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request)); return request;
    }

    @Test void magicUsesAlreadyParsedSourcesAndNeverConsumesServletInput() {
        var request = new MockHttpServletRequest("POST", "/validation/magic") {
            @Override public jakarta.servlet.ServletInputStream getInputStream() { throw new AssertionError("must not read"); }
            @Override public java.io.BufferedReader getReader() { throw new AssertionError("must not read"); }
        };
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestEntity entity = mock(RequestEntity.class);
        var resource = new ApiInfo();
        resource.setName("验证接口");
        when(entity.getApiInfo()).thenReturn(resource);
        when(entity.getParameters()).thenReturn(Map.of("keyword", "查询中文"));
        when(entity.getPathVariables()).thenReturn(Map.of("id", "validation-id"));
        when(entity.getRequestBody()).thenReturn(Map.of("enabled", false));
        assertNull(new AuditMagicRequestInterceptor().preHandle(entity));
        var safe = AuditRequestParameters.safeSnapshot(request);
        assertEquals("CAPTURED", safe.status());
        assertTrue(safe.searchText().contains("query.keyword=查询中文"));
        assertTrue(safe.searchText().contains("path.id=validation-id"));
        assertTrue(safe.searchText().contains("body.enabled=false"));
        assertEquals("验证接口", request.getAttribute("audit.functionName"));
    }

    @Test void magicDebugAndTestDoNotCollectParameters() {
        var request = bind();
        RequestEntity entity = mock(RequestEntity.class);
        when(entity.getApiInfo()).thenReturn(new ApiInfo());
        when(entity.isRequestedFromDebug()).thenReturn(true);
        new AuditMagicRequestInterceptor().preHandle(entity);
        assertEquals("LEGACY", AuditRequestParameters.safeSnapshot(request).status());
        verify(entity, never()).getRequestBody();
    }

    @Test void logReaderDeclarationStaysWithTheResourceAndKeepsOnlyTheRequestEvent() {
        var scope = new PolicyAwareApiResourceStorage("");
        var api = new ApiInfo();
        api.setId("audit-resource");
        api.setMethod("GET");
        api.setOption(List.of(new Option("auditMode", "LOG_READER")));
        scope.write(api);
        api.setMethod("POST");
        assertThrows(IllegalArgumentException.class, () -> scope.write(api));
        api.setMethod("GET");
        api.setOption(List.of(new Option("auditMode", "LOG_READER"), new Option("auditMode", "LOG_READER")));
        assertThrows(IllegalArgumentException.class, () -> scope.write(api));
        api.setOption(List.of(new Option("auditMode", "LOG_READER")));

        var request = new MockHttpServletRequest("HEAD", "/validation/relocated-log-reader");
        request.setQueryString("taskId=validation-id");
        var response = new MockHttpServletResponse();
        var auth = mock(GlobalInterceptor.class);
        var recorder = mock(AuditRecorder.class);
        var handler = new Object();
        when(auth.matchedMagicResource(request, handler)).thenReturn(api);
        var audit = new AuditWebInterceptor(mock(AuditIdentityResolver.class), recorder, auth, new MagicAPIProperties());
        assertTrue(audit.preHandle(request, response, handler));
        assertFalse(AuditRequestParameters.eligible(request));
        assertTrue(AuditRequestParameters.snapshot(request).isEmpty());
        var actor = new AuditIdentity("validation", "TENANT_USER", "user", null, null, null, null, null, null, null);
        request.setAttribute(AuditIdentityResolver.ATTRIBUTE, actor);
        request.setAttribute(AuditWebInterceptor.BUSINESS_RESULT, "DENIED");
        audit.afterCompletion(request, response, handler, null);
        verify(recorder).request(eq(actor), eq(200), isNull(), eq("DENIED"), anyLong());
        verifyNoMoreInteractions(recorder);

        var ordinary = new MockHttpServletRequest("GET", "/sym/logs/unrelated");
        assertTrue(AuditRequestParameters.eligible(ordinary));
    }

    @Test void actualLogReaderSuppressesParametersAlreadyCapturedByMvc() {
        var request = bind();
        AuditRequestParameters.capture(request, Map.of("taskId", "validation-id"), null, null);
        assertFalse(AuditRequestParameters.snapshot(request).isEmpty());
        var api = new ApiInfo();
        api.setMethod("GET");
        api.setOption(List.of(new Option("auditMode", "LOG_READER")));
        new AuditMagicRequestInterceptor().preHandle(RequestEntity.create().info(api));
        assertFalse(AuditRequestParameters.eligible(request));
        assertTrue(AuditRequestParameters.snapshot(request).isEmpty());
        assertFalse(AuditRequestParameters.safeSnapshot(request).json().contains("validation-id"));
    }

    @Test void declaredSessionChoosesAuditRealmBeforeCapturingParameters() {
        var industry = new MockHttpServletRequest("GET", "/idaas/validation-industry-action");
        var store = mock(AuditStore.class);
        when(store.jdbc("validation-tenant")).thenReturn(mock(JdbcTemplate.class));
        var resolver = new AuditIdentityResolver(store);
        AuditIdentityResolver.selectRealm(industry, MagicAccessPolicy.TENANT_SESSION, true);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(industry));
        try (var tokens = mockStatic(StpUtil.class)) {
            var session = new SaSession("validation-session").set("tenantId", "validation-tenant");
            tokens.when(StpUtil::isLogin).thenReturn(true);
            tokens.when(StpUtil::getTokenSession).thenReturn(session);
            tokens.when(StpUtil::getLoginIdAsString).thenReturn("validation-user");
            assertEquals("validation-tenant", resolver.current().tenantId());
            verify(store).jdbc("validation-tenant");
        }
        var platform = new MockHttpServletRequest("GET", "/validation/relocated-operator-resource");
        AuditIdentityResolver.selectRealm(platform, MagicAccessPolicy.IDAAS_PLATFORM_SESSION, false);
        assertFalse(AuditRequestParameters.eligible(platform));
        AuditIdentityResolver.selectRealm(platform, MagicAccessPolicy.IDAAS_ACCOUNT_SESSION, false);
        assertFalse(AuditRequestParameters.eligible(platform));
        AuditIdentityResolver.selectRealm(platform, null, true);
        assertFalse(AuditRequestParameters.eligible(platform));
    }

    @Test void consentParametersArePrivateAtItsConfiguredControllerRegardlessOfThePath() throws Exception {
        var request = new MockHttpServletRequest("GET", "/validation/relocated-consent");
        request.setQueryString("keyword=validation-consent-fallback");
        var handler = new HandlerMethod(new IdentityConsentController(null, null, null, null),
                "consent", String.class, String.class, jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class);
        var audit = new AuditWebInterceptor(mock(AuditIdentityResolver.class), mock(AuditRecorder.class),
                mock(GlobalInterceptor.class), new MagicAPIProperties());
        assertTrue(audit.preHandle(request, new MockHttpServletResponse(), handler));
        assertFalse(AuditRequestParameters.eligible(request));
        assertFalse(AuditRequestParameters.safeSnapshot(request).json().contains("validation-consent-fallback"));
    }

    @Test void privateResourceKeepsWriteAuditWhileItsDefaultReadOnlyWritesRequestAudit() {
        var reader = new ApiInfo();
        reader.setMethod("POST");
        reader.setOption(List.of(new Option("auditMode", "PRIVATE_PARAMETERS"), new Option("auditReadAction", "read")));
        var request = bind();
        new AuditMagicRequestInterceptor().preHandle(RequestEntity.create().info(reader).setRequestBody(Map.of("taskId", "validation")));
        assertFalse(AuditRequestParameters.eligible(request));
        assertTrue(AuditRequestParameters.isReadAction(request));
        assertFalse(AuditRequestParameters.isLogReaderRequest(request));

        var write = bind();
        new AuditMagicRequestInterceptor().preHandle(RequestEntity.create().info(reader).setRequestBody(Map.of("action", "save")));
        assertFalse(AuditRequestParameters.eligible(write));
        assertFalse(AuditRequestParameters.isReadAction(write));
        assertFalse(AuditRequestParameters.isLogReaderRequest(write));
    }

    @Test void magicCaptureErrorsNeverReplaceBusinessExecutionOrRetainExceptionMessages() {
        var request = bind();
        RequestEntity entity = mock(RequestEntity.class);
        when(entity.getApiInfo()).thenReturn(new ApiInfo());
        when(entity.getRequestBody()).thenThrow(new IllegalStateException("raw-body-error-value"));
        assertDoesNotThrow(() -> new AuditMagicRequestInterceptor().preHandle(entity));
        var safe = AuditRequestParameters.safeSnapshot(request);
        assertEquals("FAILED", safe.status());
        assertFalse(safe.json().contains("raw-body-error-value"));
    }

    @Test void magicNullForANonemptyBodyCannotClaimEmptyOrCompleteCaptureAndKeepsRepeatedQueryValues() {
        var request = bind(); request.setQueryString("tag=first&tag=second");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE); request.setContent("{broken".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        AuditRequestParameters.captureMvc(request, null);
        RequestEntity entity = mock(RequestEntity.class);
        when(entity.getApiInfo()).thenReturn(new ApiInfo());
        when(entity.getParameters()).thenReturn(Map.of("tag", "first"));
        when(entity.getRequestBody()).thenReturn(null);
        new AuditMagicRequestInterceptor().preHandle(entity);
        var safe = AuditRequestParameters.safeSnapshot(request);
        assertEquals("PARTIAL", safe.status());
        assertTrue(safe.searchText().contains("query.tag[1]=second"));
        assertTrue(safe.json().contains("BODY_NOT_CAPTURED"));
        assertFalse(safe.json().contains("broken"));
    }

    @Test void advicePassesThroughPayloadAndInputWithoutReadingAndRecordsConvertedDto() throws Exception {
        var request = bind();
        var input = new HttpInputMessage() {
            @Override public InputStream getBody() { throw new AssertionError("must not read"); }
            @Override public HttpHeaders getHeaders() { return new HttpHeaders(); }
        };
        var advice = new AuditRequestBodyAdvice();
        assertSame(input, advice.beforeBodyRead(input, null, Object.class, null));
        Object body = Map.of("enabled", false, "name", "转换后对象");
        assertSame(body, advice.afterBodyRead(body, input, null, Object.class, null));
        assertEquals("CAPTURED", AuditRequestParameters.safeSnapshot(request).status());
        assertTrue(AuditRequestParameters.safeSnapshot(request).searchText().contains("转换后对象"));
    }

    @Test void filePartsConvertedToStringStillOnlyContributeMetadata() {
        var request = bind();
        var input = new HttpInputMessage() {
            @Override public InputStream getBody() { throw new AssertionError("must not read"); }
            @Override public HttpHeaders getHeaders() {
                var headers = new HttpHeaders(); headers.setContentDisposition(ContentDisposition.formData().name("file").filename("validation.csv").build());
                headers.setContentType(MediaType.TEXT_PLAIN); headers.setContentLength(32); return headers;
            }
        };
        String body = "raw-file-content";
        assertSame(body, new AuditRequestBodyAdvice().afterBodyRead(body, input, null, String.class, null));
        var safe = AuditRequestParameters.safeSnapshot(request);
        assertTrue(safe.json().contains("validation.csv"));
        assertFalse((safe.json() + safe.searchText()).contains("raw-file-content"));
    }

    @Test void javaStringJsonBodiesAreParsedAfterConversionForFieldLevelMasking() {
        var request = bind();
        String raw = "{\"realName\":\"个人姓名乙\",\"address\":\"示例路1号\",\"businessName\":\"正常中文\",\"enabled\":false}";
        var input = jsonInput();
        assertSame(raw, new AuditRequestBodyAdvice().afterBodyRead(raw, input, null, String.class, null));
        var safe = AuditRequestParameters.safeSnapshot(request);
        assertFalse((safe.json() + safe.searchText()).contains("个人姓名乙"));
        assertFalse((safe.json() + safe.searchText()).contains("示例路1号"));
        assertTrue(safe.searchText().contains("正常中文"));
        assertTrue(safe.searchText().contains("body.enabled=false"));
    }

    @Test void multipartMetadataCannotCompleteOtherPartsAndConvertedPartsMergeInsteadOfOverwrite() throws Exception {
        var request = new org.springframework.mock.web.MockMultipartHttpServletRequest();
        request.setRequestURI("/validation-parts"); request.setMethod("POST");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var method = Controller.class.getDeclaredMethod("parts", Payload.class, Payload.class);
        AuditRequestParameters.captureMvc(request, new HandlerMethod(new Controller(), method));
        var advice = new AuditRequestBodyAdvice();
        var first = new MethodParameter(method, 0); var second = new MethodParameter(method, 1);
        advice.beforeBodyRead(jsonInput(), first, Payload.class, null);
        advice.afterBodyRead(Map.of("name", "第一个分部"), jsonInput(), first, Payload.class, null);
        assertEquals("PARTIAL", AuditRequestParameters.safeSnapshot(request).status());
        advice.beforeBodyRead(jsonInput(), second, Payload.class, null);
        AuditRequestParameters.complete(request); // Simulate a handled converter failure on the second part.
        assertEquals("PARTIAL", AuditRequestParameters.safeSnapshot(request).status());
        assertTrue(AuditRequestParameters.safeSnapshot(request).json().contains("BODY_CONVERSION_FAILED"));
        assertTrue(AuditRequestParameters.safeSnapshot(request).searchText().contains("body.first.name=第一个分部"));
        // A separate successful request must retain both parts.
        var successful = new org.springframework.mock.web.MockMultipartHttpServletRequest(); successful.setRequestURI("/validation-parts");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(successful));
        AuditRequestParameters.captureMvc(successful, new HandlerMethod(new Controller(), method));
        advice.beforeBodyRead(jsonInput(), first, Payload.class, null);
        advice.afterBodyRead(Map.of("name", "第一分部"), jsonInput(), first, Payload.class, null);
        advice.beforeBodyRead(jsonInput(), second, Payload.class, null);
        advice.afterBodyRead(Map.of("name", "第二分部"), jsonInput(), second, Payload.class, null);
        var safe = AuditRequestParameters.safeSnapshot(successful);
        assertEquals("CAPTURED", safe.status());
        assertTrue(safe.searchText().contains("body.first.name=第一分部"));
        assertTrue(safe.searchText().contains("body.second.name=第二分部"));
    }

    private HttpInputMessage jsonInput() {
        return new HttpInputMessage() {
            @Override public InputStream getBody() { throw new AssertionError("must not read"); }
            @Override public HttpHeaders getHeaders() { var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON); return headers; }
        };
    }

    @Test void javaMvcSeparatesPathQueryAndBodyAndValidationFailureKeepsSafeParameters() throws Exception {
        MockMvc mvc = mvc();
        var result = mvc.perform(post("/validation/validation-id?tag=中文&tag=数组&dryRun=false")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"转换中文\",\"enabled\":false,\"count\":0}"))
                .andExpect(status().isOk()).andReturn();
        var safe = AuditRequestParameters.safeSnapshot(result.getRequest());
        assertEquals("CAPTURED", safe.status());
        assertTrue(safe.searchText().contains("path.id=validation-id"));
        assertTrue(safe.searchText().contains("query.tag[1]=数组"));
        assertTrue(safe.searchText().contains("body.enabled=false"));
        assertEquals("FAILED", result.getRequest().getAttribute(AuditWebInterceptor.BUSINESS_RESULT));
        assertEquals("EMPTY", AuditRequestParameters.safeSnapshot(mvc.perform(post("/validation-empty")
                .contentType(MediaType.APPLICATION_JSON)).andReturn().getRequest()).status());
    }

    @Test void swallowedConverterFailureIsFailedOrPartialNotCompleteAndNeverCopiesErrorText() throws Exception {
        MockMvc mvc = mvc();
        var partial = mvc.perform(post("/validation/validation-id?keyword=可保留查询").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"raw-broken-body\"")).andExpect(status().isBadRequest()).andReturn();
        var safe = AuditRequestParameters.safeSnapshot(partial.getRequest());
        assertEquals("PARTIAL", safe.status());
        assertTrue(safe.json().contains("BODY_CONVERSION_FAILED"));
        assertTrue(safe.searchText().contains("可保留查询"));
        assertFalse((safe.json() + safe.searchText()).contains("raw-broken-body"));
        var failed = mvc.perform(post("/validation-body").contentType(MediaType.APPLICATION_JSON)
                .content("{broken")).andExpect(status().isBadRequest()).andReturn();
        assertEquals("FAILED", AuditRequestParameters.safeSnapshot(failed.getRequest()).status());
    }

    @Test void queryCaptureNeverCallsParameterMapAndMissingBodyCannotBeMarkedComplete() throws Exception {
        var request = new MockHttpServletRequest("POST", "/validation/parameters") {
            @Override public Map<String, String[]> getParameterMap() { throw new AssertionError("can consume form body"); }
        };
        request.setQueryString("name=%E4%B8%AD%E6%96%87&tag=1&tag=2");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", "validation-id"));
        var handler = new HandlerMethod(new Controller(), Controller.class.getDeclaredMethod("body", Payload.class));
        AuditRequestParameters.captureMvc(request, handler);
        assertEquals("PARTIAL", AuditRequestParameters.safeSnapshot(request).status());
        AuditRequestParameters.complete(request);
        assertTrue(AuditRequestParameters.safeSnapshot(request).json().contains("BODY_NOT_CAPTURED"));
        assertTrue(AuditRequestParameters.safeSnapshot(request).searchText().contains("query.name=中文"));
    }

    @Test void configuredEditorSuppressesAuditBodyEvenWithServletContext() throws Exception {
        var properties = new MagicAPIProperties();
        properties.setWeb("/validation/**");
        var recorder = mock(AuditRecorder.class);
        var mvc = mvc(recorder, properties);
        var result = mvc.perform(post("/host/validation/test").contextPath("/host")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"validation\"}"))
                .andExpect(status().isOk()).andReturn();
        assertFalse(AuditRequestParameters.eligible(result.getRequest()));
        assertEquals("LEGACY", AuditRequestParameters.safeSnapshot(result.getRequest()).status());
        verifyNoInteractions(recorder);
    }

    private MockMvc mvc() {
        return mvc(mock(AuditRecorder.class), new MagicAPIProperties());
    }

    private MockMvc mvc(AuditRecorder recorder, MagicAPIProperties properties) {
        return MockMvcBuilders.standaloneSetup(new Controller()).setControllerAdvice(new AuditRequestBodyAdvice(), new AuditResponseAdvice())
                .addInterceptors(new AuditWebInterceptor(mock(AuditIdentityResolver.class), recorder,
                        mock(GlobalInterceptor.class), properties)).build();
    }

    @RestController
    static class Controller {
        @PostMapping("/validation/{id}") public Map<String, Object> parameters(@PathVariable("id") String id, @RequestBody Payload body) {
            return Map.of("code", 422, "success", false);
        }
        @PostMapping("/validation-body") public Map<String, Object> body(@RequestBody Payload body) { return Map.of("code", 0); }
        @PostMapping("/validation-empty") public Map<String, Object> empty(@RequestBody(required = false) Payload body) { return Map.of("code", 0); }
        @PostMapping("/validation-parts") public Map<String, Object> parts(@RequestPart("first") Payload first, @RequestPart("second") Payload second) { return Map.of("code", 0); }
    }
    static class Payload { public String name; public boolean enabled; public int count; }
}
