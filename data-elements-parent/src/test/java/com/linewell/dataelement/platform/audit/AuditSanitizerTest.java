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
}
