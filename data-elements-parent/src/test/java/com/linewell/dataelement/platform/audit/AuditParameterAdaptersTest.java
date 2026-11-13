package com.linewell.dataelement.platform.audit;

import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.ssssssss.magicapi.core.context.RequestEntity;
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
        RequestEntity entity = mock(RequestEntity.class, RETURNS_DEEP_STUBS);
        when(entity.getApiInfo().getName()).thenReturn("验证接口");
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
        RequestEntity entity = mock(RequestEntity.class, RETURNS_DEEP_STUBS);
        when(entity.isRequestedFromDebug()).thenReturn(true);
        new AuditMagicRequestInterceptor().preHandle(entity);
        assertEquals("LEGACY", AuditRequestParameters.safeSnapshot(request).status());
        verify(entity, never()).getRequestBody();
    }

    @Test void magicCaptureErrorsNeverReplaceBusinessExecutionOrRetainExceptionMessages() {
        var request = bind();
        RequestEntity entity = mock(RequestEntity.class, RETURNS_DEEP_STUBS);
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
        RequestEntity entity = mock(RequestEntity.class, RETURNS_DEEP_STUBS);
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

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new Controller()).setControllerAdvice(new AuditRequestBodyAdvice(), new AuditResponseAdvice())
                .addInterceptors(new AuditWebInterceptor(mock(AuditIdentityResolver.class), mock(AuditRecorder.class))).build();
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