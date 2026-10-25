package com.linewell.dataelement.platform.audit;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+20)
public class AuditTraceFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var previous=MDC.getCopyOfContextMap();
        try {
            MDC.clear();
            // External correlation is untrusted and never used for identity/routing.
            String parent=request.getHeader("traceparent");
            String trace=parent!=null&&parent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")&&!parent.substring(3,35).equals("0".repeat(32))&&!parent.substring(36,52).equals("0".repeat(16))?parent.substring(3,35):AuditTrace.random();
            MDC.put("span_id",AuditTrace.random().substring(0,16));
            MDC.put("trace_id",trace); MDC.put("request_id",AuditTrace.random());
            response.setHeader("X-Request-Id",AuditTrace.requestId()); response.setHeader("X-Trace-Id",trace);
            chain.doFilter(request,response);
        } finally {
            MDC.clear();
            if(previous!=null){previous=new java.util.HashMap<>(previous);for(String key:java.util.List.of("tenant_id","actor_id","request_id","trace_id","span_id"))previous.remove(key);MDC.setContextMap(previous);}
        }
    }
}
