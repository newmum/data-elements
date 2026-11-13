package com.linewell.dataelement.platform.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuditSanitizerTest {
    private final AuditSanitizer sanitizer=new AuditSanitizer(new ObjectMapper());
    @Test void credentialsAndCredentialChangesNeverEnterEventDetails() {
        var input=new LinkedHashMap<String,Object>();input.put("password","validation-secret");input.put("token","validation-token");
        input.put("changes",List.of(Map.of("field","password","before","validation-secret","after","validation-next"),Map.of("field","status","before","ACTIVE","after","DISABLED")));
        String json=sanitizer.json(input);
        assertFalse(json.contains("validation-secret"));assertFalse(json.contains("validation-token"));assertFalse(json.contains("validation-next"));assertTrue(json.contains("DISABLED"));
    }
    @Test void untrustedTextIsBoundedWithoutSplittingUnicodeOrInjectingLines() {
        assertEquals("甲😀",AuditSanitizer.text("甲😀乙",2));
        assertEquals("x  y",AuditSanitizer.text("x\r\ny",20));
    }
    @Test void arbitraryRequestFieldsAreDropped() {
        String json=sanitizer.json(Map.of("name","验证对象","sql","select private_data","headers",Map.of("Authorization","validation-token")));
        assertTrue(json.contains("验证对象"));assertFalse(json.contains("private_data"));assertFalse(json.contains("Authorization"));
    }

    @Test void requestParametersOnlyKeepApprovedScalarsAndNamesResolvedByBusiness() throws Exception {
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/ods/dataAggReset");
        AuditRequestParameters.capture(request, Map.of("taskId", "validation-id", "dryRun", true,
                "taskName", "伪造任务", "token", "validation-secret", "password", "validation-password",
                "headers", Map.of("authorization", "validation-authorization")));
        AuditRequestParameters.enrich(request, Map.of("taskName", List.of("验证任务_%"), "taskId", "validation-id"));
        String json = sanitizer.json(Map.of("requestParameterIndex", Map.of("injected", true)),
                AuditRequestParameters.snapshot(request));
        assertFalse(json.contains("validation-secret"));
        assertFalse(json.contains("validation-password"));
        assertFalse(json.contains("validation-authorization"));
        assertFalse(json.contains("伪造任务"));
        assertFalse(json.contains("injected"));
        var saved = new ObjectMapper().readTree(json);
        assertEquals("验证任务_%", saved.path("requestParameters").path("taskName").get(0).asText());
        assertEquals("true", saved.path("requestParameters").path("dryRun").asText());
        assertFalse(saved.path("requestParameters").has("deletedTaskName"));
        assertTrue(json.contains(AuditRequestParameters.exactMatchPattern("taskName", "验证任务_%")
                .replace("%", "")));
    }
}
