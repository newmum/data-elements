package com.haitong.api;

import com.haitong.config.Secrets;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

/** HTTP allowlist for the optional unauthenticated producer protocol. */
public final class PushAccess {
    public static final String OWNER = "unvalidated-publisher";
    public static final String TENANT = "unvalidated-push";
    public static String path(HttpServletRequest request) {
        String uri=request.getRequestURI(),context=request.getContextPath();
        // Servlet context is a server setting, never inferred from a proxy header.
        return context!=null&&!context.isEmpty()&&(uri.equals(context)||uri.startsWith(context+"/"))
                ? uri.substring(context.length()) : uri;
    }
    public static Secrets.Identity identity() {
        return new Secrets.Identity(OWNER, false,
                Set.of("table-to-ftp", "table-to-kafka", "table-to-log"), Set.of());
    }
    public static boolean endpoint(String method, String path) {
        if (method.equals("POST") && Set.of("/api/v1/data-push", "/api/v1/simple/ftp",
                "/api/v1/simple/kafka", "/api/v1/simple/log", "/push-operations/data-push",
                "/push-operations/template-test", "/push-operations/message-status").contains(path)) return true;
        if (method.equals("GET") && path.matches("/api/v1/messages/[A-Za-z0-9_-]+")) return true;
        String base = "/api/v1/file-uploads";
        if (path.equals(base)) return method.equals("POST") || method.equals("GET");
        if (method.equals("POST") && (path.equals(base+"/single") || path.equals(base+"/status-batch"))) return true;
        if (path.matches(base+"/[a-fA-F0-9-]{36}")) return method.equals("GET") || method.equals("DELETE");
        if (path.matches(base+"/[a-fA-F0-9-]{36}/parts")) return method.equals("GET");
        if (path.matches(base+"/[a-fA-F0-9-]{36}/parts/[0-9]+")) return method.equals("PUT");
        if (path.matches(base+"/[a-fA-F0-9-]{36}/(complete|submit)")) return method.equals("POST");
        if (path.matches(base+"/[a-fA-F0-9-]{36}/content")) return method.equals("GET") || method.equals("HEAD");
        return false;
    }
    private PushAccess() {}
}
