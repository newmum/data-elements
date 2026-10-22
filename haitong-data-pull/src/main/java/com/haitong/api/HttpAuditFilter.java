package com.haitong.api;
import com.haitong.config.Secrets;
import com.haitong.store.ExchangeLog;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+5)
public final class HttpAuditFilter extends OncePerRequestFilter {
    private final ExchangeLog ledger;private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(HttpAuditFilter.class);
    public HttpAuditFilter(ExchangeLog ledger){this.ledger=ledger;}
    @Override protected boolean shouldNotFilter(HttpServletRequest r){return r.getRequestURI().equals("/actuator/health");}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        String id=UUID.randomUUID().toString();req.setAttribute("haitong.requestId",id);res.setHeader("X-Request-Id",id);
        long started=System.currentTimeMillis();Map<String,Object> c=new LinkedHashMap<>();c.put("requestId",id);c.put("attemptId",id);c.put("startedAt",started);
        // Only known ASCII path shapes, never a query string or header containing credentials.
        String path=req.getRequestURI();if(!path.matches("/(api/v1|internal/v1|actuator)/[A-Za-z0-9_./-]{1,200}"))path="[unrecognized-path]";
        c.put("sourceRef",req.getMethod()+" "+path);safe("START","RUNNING",c);
        boolean thrown=false;
        try{chain.doFilter(req,res);}catch(ServletException|IOException|RuntimeException ex){thrown=true;throw ex;}
        finally{
            Object identity=req.getAttribute(AuthenticationFilter.IDENTITY);if(identity instanceof Secrets.Identity who)c.put("owner",who.id());
            c.put("messageId",req.getAttribute("haitong.messageId"));c.put("fileId",req.getAttribute("haitong.fileId"));
            int status=thrown?500:res.getStatus();c.put("httpStatus",status);c.put("durationMs",Math.max(0,System.currentTimeMillis()-started));
            safe("FINISH",status<400?"RESPONDED":"REJECTED_OR_FAILED",c);
        }
    }
    private void safe(String phase,String outcome,Map<String,Object> c){
        try{ledger.append("HTTP","IN","HTTP_ACCESS",phase,outcome,c);}
        catch(RuntimeException ex){log.error("HTTP_ACCESS_AUDIT_UNAVAILABLE requestId={} phase={}; business acceptance still requires its transactional ledger",c.get("requestId"),phase);}
    }
}
