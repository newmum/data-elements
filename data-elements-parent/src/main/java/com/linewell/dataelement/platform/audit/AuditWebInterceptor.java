package com.linewell.dataelement.platform.audit;

import com.linewell.dataelement.magic.runtime.security.MagicAccessPolicy;
import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.platform.web.security.GlobalInterceptor;
import com.linewell.dataelement.platform.web.security.ProtocolAccess;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.ssssssss.magicapi.core.config.MagicAPIProperties;

@Component
public class AuditWebInterceptor implements HandlerInterceptor {
    public static final String BUSINESS_CODE="audit.businessCode";
    public static final String BUSINESS_RESULT="audit.businessResult";
    private final AuditIdentityResolver identities;
    private final AuditRecorder recorder;
    private final GlobalInterceptor access;
    private final String editorPath;

    public AuditWebInterceptor(AuditIdentityResolver identities, AuditRecorder recorder, GlobalInterceptor access,
                               MagicAPIProperties magicProperties) {
        this.identities = identities;
        this.recorder = recorder;
        this.access = access;
        this.editorPath = magicProperties.getWeb();
    }
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
        if (editorPath != null && !editorPath.isBlank()) {
            String base = request.getContextPath() + editorPath;
            String path = request.getRequestURI();
            if (path.equals(base) || path.startsWith(base + "/")) {
                request.setAttribute(AuditRequestParameters.SUPPRESSED, true);
                return true;
            }
        }
        request.setAttribute("audit.started",System.nanoTime());
        if (excluded(request)) return true;
        MagicAccessPolicy policy = selectResource(request, handler);
        AuditRequestParameters.captureMvc(request, handler);
        if (policy != MagicAccessPolicy.SESSION_LOGIN) {
            var actor=identities.current();if(actor!=null){if(actor.tenantId()!=null)org.slf4j.MDC.put("tenant_id",actor.tenantId());if(actor.id()!=null)org.slf4j.MDC.put("actor_id",actor.id());}
        }
        return true;
    }
    @Override public void afterCompletion(HttpServletRequest request,HttpServletResponse response,Object handler,Exception exception) {
        if(Boolean.TRUE.equals(request.getAttribute(AuditRequestParameters.SUPPRESSED)) || excluded(request)) return;
        AuditRequestParameters.complete(request);
        var actor=request.getAttribute(AuditIdentityResolver.ATTRIBUTE) instanceof AuditIdentity i?i:null;
        String result=String.valueOf(request.getAttribute(BUSINESS_RESULT));
        if(!AuditRecorder.RESULTS.contains(result)) result=exception!=null||response.getStatus()>=400?"FAILED":"SUCCESS";
        long started=request.getAttribute("audit.started") instanceof Long value?value:System.nanoTime();
        Object code=request.getAttribute(BUSINESS_CODE);
        recorder.request(actor,response.getStatus(),code==null?null:String.valueOf(code),result,(System.nanoTime()-started)/1_000_000);
        String path=request.getRequestURI();
        MagicAccessPolicy policy = GlobalInterceptor.verifiedMagicAccess(request);
        if(actor!=null && !actor.platform() && !AuditRequestParameters.isLogReaderRequest(request)
                && policy != MagicAccessPolicy.SESSION_LOGIN && policy != MagicAccessPolicy.SESSION_LOGOUT) {
            boolean mutation = !AuditRequestParameters.isReadAction(request)
                    && !java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod()) && !path.matches("(?i).*/(?:page|list|query|find|detail|preview|statistics|batch|tree|me|userMenu|routes|load).*" );
            if(result.equals("DENIED") || mutation && (!Boolean.TRUE.equals(request.getAttribute("audit.operationRecorded")) || !result.equals("SUCCESS"))) {
                recorder.observedOperation(actor,result,path,request.getAttribute("audit.functionName"));
            }
        }
    }

    private MagicAccessPolicy selectResource(HttpServletRequest request, Object handler) {
        try {
            var resource = access.matchedMagicResource(request, handler);
            AuditRequestParameters.selectResourceAudit(request, resource);
            MagicAccessPolicy policy = access.matchedMagicAccess(request, handler);
            if (handler instanceof HandlerMethod method) {
                var protocol = method.getBeanType().getAnnotation(ProtocolAccess.class);
                if (protocol != null && protocol.auditIdentityAccount()) {
                    policy = MagicAccessPolicy.IDAAS_ACCOUNT_SESSION;
                }
            }
            AuditIdentityResolver.selectRealm(request, policy, access.matchedIdentityGroup(request, handler));
            return policy;
        } catch (BizException | IllegalArgumentException invalidPolicy) {
            // 继续采集审计主体，由全局拦截器拒绝无效的访问策略。
            return null;
        }
    }

    private boolean excluded(HttpServletRequest r) {
        String path=r.getRequestURI();
        return path.startsWith("/log-console/") || path.startsWith("/actuator/") || path.matches(".*\\.(js|css|png|svg|ico|woff2?)$");
    }
}
