package com.linewell.dataelement.feature.logmanagement;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LogParameterKeywordMagicTest {
    private Object invoke(String tenant, String kind, Object keyword) {
        var input = new LinkedHashMap<String,Object>();
        input.put("tenantId", tenant); input.put("kind", kind); input.put("keyword", keyword);
        return LogParameterMagicFixture.execute(input);
    }

    @Test void realFunctionKeepsOneCompleteLiteralAndNoPermissionsOrSql() throws Exception {
        var metadata = LogParameterMagicFixture.metadata();
        assertEquals(LogParameterMagicFixture.ID, metadata.get("id"));
        assertEquals("0f27d7cf99494ceca369c14f35fbb987", metadata.get("groupId"));
        assertEquals("/log-parameter-keyword-condition", metadata.get("path"));
        for (String kind : new String[]{"operations", "requests"}) {
            for (String value : new String[]{"MiXeD中文 %_!", ".*a[b]$", "false", "0", "null", "  完整 词  "}) {
                assertEquals(Map.of("operator", "contains", "column", "parameter_search_text", "value", value),
                        invoke("validation-tenant", kind, value));
            }
        }
    }

    @Test void lengthAndIdentityRulesAreExecutedInMagicNotJava() {
        assertNotNull(invoke("validation-tenant", "operations", "中".repeat(200)));
        assertThrows(RuntimeException.class, () -> invoke("validation-tenant", "operations", "中".repeat(201)));
        for (Object invalid : new Object[]{false, 0, Map.of("scope", "ALL")}) {
            assertThrows(RuntimeException.class, () -> invoke("validation-tenant", "operations", invalid));
        }
        for (String kind : new String[]{"auth", "security", "archives", "exports", "invalid"}) {
            assertThrows(RuntimeException.class, () -> invoke("validation-tenant", kind, "验证"));
        }
        assertThrows(RuntimeException.class, () -> invoke(null, "operations", "验证"));
        assertThrows(RuntimeException.class, () -> invoke("", "requests", "验证"));
        assertThrows(RuntimeException.class, () -> invoke(" ", "requests", "验证"));
        assertNull(invoke(null, "auth", null));
        assertNull(invoke(null, "auth", ""));
    }
}