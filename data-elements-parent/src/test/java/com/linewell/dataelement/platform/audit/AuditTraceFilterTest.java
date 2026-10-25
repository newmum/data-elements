package com.linewell.dataelement.platform.audit;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

class AuditTraceFilterTest {
    @Test void reusedThreadCannotCarryPreviousTenantOrActorIntoNextRequest() throws Exception {
        MDC.put("tenant_id","previous-tenant");MDC.put("actor_id","previous-actor");MDC.put("request_id","previous-request");MDC.put("service","validation");
        try {
            var response=new MockHttpServletResponse();
            new AuditTraceFilter().doFilter(new MockHttpServletRequest(),response,(request,out)->{
                assertNull(MDC.get("tenant_id"));assertNull(MDC.get("actor_id"));
                assertNotEquals("previous-request",MDC.get("request_id"));
                assertEquals(32,MDC.get("request_id").length());
                MDC.put("tenant_id","current-tenant");MDC.put("actor_id","current-actor");
            });
            assertNull(MDC.get("tenant_id"));assertNull(MDC.get("actor_id"));assertNull(MDC.get("request_id"));
            assertEquals("validation",MDC.get("service"));assertNotNull(response.getHeader("X-Request-Id"));
        } finally {MDC.clear();}
    }
}
