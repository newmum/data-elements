package com.linewell.dataelement.platform.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AuditRecorderParametersTest {
    private final AuditRecorder recorder = new AuditRecorder(mock(AuditStore.class), mock(AuditIdentityResolver.class),
            new AuditSanitizer(new ObjectMapper()), mock(AuditEmergencyBuffer.class), mock(AuditPolicyCache.class));
    private final AuditIdentity tenant = new AuditIdentity("validation-tenant", "TENANT_USER", "validation-user", "validation-account",
            "验证人员", null, null, "validation-app", "validation-session", null);
    private final AuditIdentity platform = new AuditIdentity(null, "OPERATOR", "validation-operator", null, null, null, null, "idaas", "validation-session", "workforce");

    @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); recorder.close(); }

    private MockHttpServletRequest bind() {
        var request = new MockHttpServletRequest("POST", "/validation/parameters");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request)); return request;
    }

    @Test void onlyIndustryRequestAndOperationWriteTheThreeColumnsIncludingFailedBusinessResults() {
        var request = bind(); AuditRequestParameters.capture(request, Map.of("taskId", "validation-id"), Map.of(), Map.of("name", "参数中文", "enabled", false));
        for (String category : new String[] {"REQUEST", "OPERATION"}) {
            for (String result : new String[] {"SUCCESS", "FAILED", "PARTIAL", "UNKNOWN"}) {
                var prepared = recorder.prepare(tenant, category, "VALIDATE", result, null, null, null, null, null);
                assertEquals(category.equals("REQUEST") ? "log_request_t" : "log_system_t", prepared.table());
                assertEquals("CAPTURED", prepared.row().get("parameter_capture_status"));
                assertEquals(AuditRequestParameters.safeSnapshot(request).json(), prepared.row().get("parameter_snapshot"));
                assertEquals(AuditRequestParameters.safeSnapshot(request).searchText(), prepared.row().get("parameter_search_text"));
                assertEquals(result, prepared.row().get("result"));
                assertTrue(String.valueOf(prepared.row().get("detail_json")).contains("requestParameterIndex"));
            }
        }
    }

    @Test void platformAuthSecurityAndTasksDoNotGainColumnsAndDenialNeverIncludesParameters() {
        var request = bind(); AuditRequestParameters.capture(request, Map.of("taskId", "validation-id"), Map.of(), Map.of("name", "禁止带入拒绝事件"));
        for (String category : new String[] {"AUTH", "REQUEST", "OPERATION", "SECURITY", "TASK"}) {
            var row = recorder.prepare(platform, category, "VALIDATE", "SUCCESS", null, null, null, null, null).row();
            assertNoParameterColumns(row);
            assertFalse(String.valueOf(row.get("snapshot_json")).contains("validation-id"));
        }
        for (String category : new String[] {"AUTH", "SECURITY", "TASK"}) assertNoParameterColumns(
                recorder.prepare(tenant, category, "VALIDATE", "SUCCESS", null, null, null, null, null).row());
        var denied = recorder.prepare(tenant, "REQUEST", "VALIDATE", "DENIED", null, null, null, null, null).row();
        assertEquals("OMITTED", denied.get("parameter_capture_status")); assertEquals("", denied.get("parameter_search_text"));
        assertFalse(String.valueOf(denied).contains("validation-id")); assertFalse(String.valueOf(denied).contains("禁止带入"));
    }

    @Test void missingCaptureIsLegacyAndPreparedSnapshotsAreFrozenBeforeAnyAsyncWrite() {
        var request = bind();
        assertEquals("LEGACY", recorder.prepare(tenant, "REQUEST", "VALIDATE", "SUCCESS", null, null, null, null, null).row().get("parameter_capture_status"));
        var body = new java.util.LinkedHashMap<String, Object>(); body.put("name", "原始参数");
        AuditRequestParameters.capture(request, Map.of(), Map.of(), body);
        var event = recorder.prepare(tenant, "REQUEST", "VALIDATE", "FAILED", null, null, null, null, null);
        String json = String.valueOf(event.row().get("parameter_snapshot"));
        body.put("name", "请求对象后续修改"); AuditRequestParameters.enrich(request, Map.of("taskName", "后续核验"));
        assertEquals(json, event.row().get("parameter_snapshot"));
        assertFalse(String.valueOf(event.row().get("parameter_search_text")).contains("后续"));
    }

    @Test void policyExcludedCurrentRequestIsOmittedRatherThanHistoricalCollection() {
        for (String path : new String[] {"/sym/logs/requests", "/portal/login", "/auth/validation"}) {
            var request = new MockHttpServletRequest("POST", path);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            AuditRequestParameters.capture(request, Map.of("name", "不得采集"), Map.of(), null);
            var event = recorder.prepare(tenant, "REQUEST", "VALIDATE", "SUCCESS", null, null, null, null, null);
            assertEquals("OMITTED", event.row().get("parameter_capture_status"));
            assertEquals("", event.row().get("parameter_search_text"));
            assertFalse(String.valueOf(event.row().get("parameter_snapshot")).contains("不得采集"));
        }
    }

    @Test void platformAndAuthenticationRoutesNeverCollectGeneralRequestSnapshots() {
        for (String path : new String[] {"/idaas/validation", "/portal/login", "/auth/validation", "/security/validation", "/api/web/test"}) {
            var request = new MockHttpServletRequest("POST", path);
            AuditRequestParameters.capture(request, Map.of("name", "不得采集"), Map.of(), null);
            AuditRequestParameters.enrich(request, Map.of("taskName", "不得核验"));
            assertEquals("LEGACY", AuditRequestParameters.safeSnapshot(request).status());
        }
        var request = bind(); request.setAttribute(AuditIdentityResolver.ATTRIBUTE, platform);
        AuditRequestParameters.capture(request, Map.of("name", "不得采集"), Map.of(), null);
        assertEquals("LEGACY", AuditRequestParameters.safeSnapshot(request).status());
    }

    private void assertNoParameterColumns(Map<String, Object> row) {
        assertFalse(row.containsKey("parameter_snapshot")); assertFalse(row.containsKey("parameter_search_text"));
        assertFalse(row.containsKey("parameter_capture_status"));
    }
}