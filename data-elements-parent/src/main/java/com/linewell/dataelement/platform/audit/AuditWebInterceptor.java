package com.linewell.dataelement.platform.audit;

import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuditWebInterceptor implements HandlerInterceptor {
    public static final String BUSINESS_CODE="audit.businessCode";
    public static final String BUSINESS_RESULT="audit.businessResult";
    private final AuditIdentityResolver identities;
    private final AuditRecorder recorder;
    public AuditWebInterceptor(AuditIdentityResolver identities,AuditRecorder recorder) { this.identities=identities; this.recorder=recorder; }
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
        request.setAttribute("audit.started",System.nanoTime());
        if(!excluded(request) && !request.getRequestURI().equals("/portal/login") && !request.getRequestURI().equals("/idaas/auth/login")) {
            var actor=identities.current();if(actor!=null){if(actor.tenantId()!=null)org.slf4j.MDC.put("tenant_id",actor.tenantId());if(actor.id()!=null)org.slf4j.MDC.put("actor_id",actor.id());}
        }
        return true;
    }
    @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception exception) {
        if(excluded(request)) return;
        var actor=request.getAttribute(AuditIdentityResolver.ATTRIBUTE) instanceof AuditIdentity i?i:null;
        String result=String.valueOf(request.getAttribute(BUSINESS_RESULT));
        if(!AuditRecorder.RESULTS.contains(result)) result=exception!=null||response.getStatus()>=400?"FAILED":"SUCCESS";
        long started=request.getAttribute("audit.started") instanceof Long value?value:System.nanoTime();
        Object code=request.getAttribute(BUSINESS_CODE);
        recorder.request(actor,response.getStatus(),code==null?null:String.valueOf(code),result,(System.nanoTime()-started)/1_000_000);
        String path=request.getRequestURI();
        if(actor!=null && !actor.platform() && !path.startsWith("/sym/logs/") && !path.equals("/portal/login") && !path.equals("/portal/logout")) {
            boolean mutation=!java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod()) && !path.matches("(?i).*/(?:page|list|query|find|detail|preview|statistics|batch|tree|me|userMenu|routes|load).*" );
            if(result.equals("DENIED") || mutation && (!Boolean.TRUE.equals(request.getAttribute("audit.operationRecorded")) || !result.equals("SUCCESS"))) {
                recorder.observedOperation(actor,result,path,request.getAttribute("audit.functionName"));
            }
        }
    }
    private boolean excluded(HttpServletRequest r) {
        String path=r.getRequestURI();
        return path.startsWith("/api/web/") || path.startsWith("/log-console/") || path.startsWith("/actuator/") || path.contains("/component/cache/") || path.matches(".*\\.(js|css|png|svg|ico|woff2?)$");
    }
}
