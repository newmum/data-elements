package com.linewell.dataelement.platform.audit;

import com.linewell.dataelement.model.common.CommonResponse;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;

class AuditResponseAdviceTest {
    private final MockHttpServletRequest request=new MockHttpServletRequest();
    private final AuditResponseAdvice advice=new AuditResponseAdvice();
    @BeforeEach void bind(){RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));}
    @AfterEach void clear(){RequestContextHolder.resetRequestAttributes();}
    @Test void http200WithBusinessDenialNeverBecomesSuccessfulAudit(){
        var body=CommonResponse.fail(403,"没有日志权限","ACCESS_DENIED");
        assertSame(body,advice.beforeBodyWrite(body,null,null,null,null,null));
        assertEquals("DENIED",request.getAttribute(AuditWebInterceptor.BUSINESS_RESULT));
    }
    @Test void explicitFailedEnvelopeCannotBeMarkedSuccessfulByNumericCode(){
        advice.beforeBodyWrite(Map.of("code",0,"success",false),null,null,null,null,null);
        assertEquals("FAILED",request.getAttribute(AuditWebInterceptor.BUSINESS_RESULT));
    }
    @Test void successfulEnvelopeIsObservedWithoutModifyingPayload(){
        var body=Map.of("code",0,"data",List.of("validation"));
        assertSame(body,advice.beforeBodyWrite(body,null,null,null,null,null));
        assertEquals("SUCCESS",request.getAttribute(AuditWebInterceptor.BUSINESS_RESULT));
    }
}
