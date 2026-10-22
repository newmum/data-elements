package com.haitong.api;

import com.haitong.config.*;
import com.haitong.core.*;
import com.haitong.security.PushCredentialStore;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+10)
public final class AuthenticationFilter extends OncePerRequestFilter {
    public static final String IDENTITY=AuthenticationFilter.class.getName()+".identity";
    private record Window(long minute,int count) {}
    private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
    private final Secrets secrets;private final RelayProperties p;private final Json json;private final PushCredentialStore pushCredentials;
    public AuthenticationFilter(Secrets secrets,RelayProperties p,Json json,PushCredentialStore pushCredentials) { this.secrets=secrets;this.p=p;this.json=json;this.pushCredentials=pushCredentials; }
    @Override protected boolean shouldNotFilter(HttpServletRequest r) {
        String path=r.getRequestURI();
        // Magic-API owns its editor session/login protocol below this path.  Do
        // not require a relay X-Api-Key before the editor can present its own
        // authenticated login screen.  Business Magic routes remain protected
        // by the normal relay filter.
        return (r.getMethod().equals("GET") && path.equals("/actuator/health"))
            || path.equals("/api/web") || path.startsWith("/api/web/");
    }
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        res.setHeader("Cache-Control","no-store");res.setHeader("X-Content-Type-Options","nosniff");
        String path=req.getRequestURI();
        // Paths in this API are ASCII constants/IDs. Reject ambiguous servlet/matrix/encoded paths
        // before deciding whether admin authentication is required.
        if(path.contains("%") || path.contains(";") || path.contains("\\") || path.contains("//") || path.contains("/../") || path.contains("/./")) {
            error(res,400,"INVALID_PATH","接口路径不能包含编码分隔符或路径参数");return;
        }
        boolean internal=req.getRequestURI().startsWith("/internal/") || req.getRequestURI().startsWith("/actuator/");
        Secrets.Identity identity=secrets.admin(req.getHeader("X-Admin-Key"));
        boolean platformCredentialIssue = "/internal/push-credentials/issue".equals(path);
        // The Magic operations endpoint is a controlled alias of the same
        // REGISTRY_SYNC service flow, so it accepts the same service-to-service
        // credential. It cannot use that credential to publish business data:
        // RelayController still checks the route/channel after JSON parsing.
        boolean standaloneRegistrySync = ("/api/v1/data-push".equals(path) || "/push-operations/data-push".equals(path))
                && p.getStandaloneRegistry().isSyncEnabled();
        if (identity == null && platformCredentialIssue && p.getRegistrationRegistry().isEnabled()) {
            String registryKey = secrets.resolve(p.getRegistrationRegistry().getSharedKey());
            if (!registryKey.isBlank() && Rules.constantEquals(registryKey, req.getHeader("X-Relay-Registry-Key")))
                identity = new Secrets.Identity("platform-credential-issuer", true, Set.of("*"), Set.of("*"));
        }
        if (identity == null && standaloneRegistrySync) {
            String syncKey = secrets.resolve(p.getStandaloneRegistry().getSyncKey());
            if (!syncKey.isBlank() && Rules.constantEquals(syncKey, req.getHeader("X-Standalone-Registry-Key")))
                identity = new Secrets.Identity("platform-standalone-registry", false, Set.of("standalone-registry-sync"), Set.of());
        }
        if(identity==null && !internal) {
            String key=req.getHeader("X-Api-Key");String authorization=req.getHeader("Authorization");
            if((key==null || key.isBlank()) && authorization!=null && authorization.startsWith("Bearer ")) key=authorization.substring(7);
            identity=secrets.client(key);
            if(identity==null) identity=pushCredentials.authenticate(key);
        }
        if(identity==null) { error(res,401,"UNAUTHENTICATED","缺少或无效的访问密钥");return; }
        if(req.getRequestURI().startsWith("/api/") && !p.getGateway().getSharedKey().isBlank() &&
           !Rules.constantEquals(secrets.resolve(p.getGateway().getSharedKey()),req.getHeader("X-Gateway-Key"))) {
            error(res,403,"GATEWAY_REQUIRED","必须通过已授权网关访问");return;
        }
        if(!identity.admin()) {
            long minute=System.currentTimeMillis()/60000;
            Window w=windows.compute(identity.id(),(key,old)->new Window(minute,old!=null && old.minute()==minute?old.count()+1:1));
            if(w.count()>p.getSecurity().getRequestsPerMinute()) {res.setHeader("Retry-After","60");error(res,429,"RATE_LIMITED","请求过于频繁");return;}
        }
        req.setAttribute(IDENTITY,identity);chain.doFilter(req,res);
    }
    private void error(HttpServletResponse response,int code,String name,String message) throws IOException {
        response.setStatus(code);response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(json.write(Map.of("code",name,"message",message)));
    }
    public static Secrets.Identity identity(HttpServletRequest req) {
        var value=req.getAttribute(IDENTITY);
        if(!(value instanceof Secrets.Identity id)) throw new RelayException(401,"UNAUTHENTICATED","未认证");
        return id;
    }
}
