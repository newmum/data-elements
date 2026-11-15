package com.haitong.api;

import com.haitong.config.Secrets;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Map.entry;

/** HTTP allowlist for the optional unauthenticated producer protocol. */
public final class PushAccess {
    public static final String OWNER = "unvalidated-publisher";
    public static final String TENANT = "unvalidated-push";
    static final String DEBUG_PUSH = "/push/debug/send";
    static final String DEBUG_STATUS = "/push/message/status";
    static final String DEBUG_TEMPLATE = "/push/template/test";
    private static final String OPERATIONS = "/internal/v1";
    private static final String LEGACY_FILES = "/api/v1/file-uploads";
    private static final String FILES = "/api/file";
    private static final String LEGACY_PUSH = "/api/v1/data-push";
    private static final String LEGACY_PUSH_UPLOADS = LEGACY_PUSH + "/uploads";
    private static final String FILE_PUSH = "/api/push";
    private static final Map<String, String> EXACT_ALIASES = Map.ofEntries(
            entry("GET /gateway/config-check", "/gateway/config/check"),
            entry("POST /gateway/token-status", "/gateway/token/status"),
            entry("POST /gateway/data-push", "/gateway/data/push"),
            entry("POST /ftp-diagnostics/probe", "/ftp/connect/probe"),
            entry("POST /push-operations/data-push", DEBUG_PUSH),
            entry("POST /push-operations/message-status", DEBUG_STATUS),
            entry("POST /push-operations/template-test", DEBUG_TEMPLATE),
            entry("GET /internal/v1/config-summary", OPERATIONS + "/config"),
            entry("GET /internal/v1/exchange-logs", OPERATIONS + "/logs"),
            entry("GET /internal/v1/reconciliation/summary", OPERATIONS + "/reconcile"),
            entry("POST " + LEGACY_FILES, FILES + "/init"),
            entry("GET " + LEGACY_FILES, FILES + "/page"),
            entry("POST " + LEGACY_FILES + "/single", FILES + "/single"),
            entry("POST " + LEGACY_FILES + "/status-batch", FILES + "/batch"),
            entry("PUT " + LEGACY_PUSH, FILE_PUSH + "/single"),
            entry("PUT " + LEGACY_PUSH + "/single", FILE_PUSH + "/single"),
            entry("POST " + LEGACY_PUSH_UPLOADS, FILE_PUSH + "/init"));
    private record PathAlias(String method, Pattern path, String target, List<String> parameters) {
        PathAlias(String method, String pattern, String target, String... parameters) {
            this(method, Pattern.compile(pattern), target, List.of(parameters));
        }
    }
    private static final List<PathAlias> OPERATION_ALIASES = List.of(
            new PathAlias("POST", OPERATIONS+"/deliveries/([^/]+)/retry", OPERATIONS+"/retry", "id"),
            new PathAlias("POST", OPERATIONS+"/deliveries/([^/]+)/resolve", OPERATIONS+"/resolve", "id"),
            new PathAlias("POST", OPERATIONS+"/files/([^/]+)/retry", OPERATIONS+"/fileretry", "id"),
            new PathAlias("POST", OPERATIONS+"/ftp-scans/([^/]+)", OPERATIONS+"/scan", "route"),
            new PathAlias("GET", OPERATIONS+"/connectors/([^/]+)/health", OPERATIONS+"/health", "name"),
            new PathAlias("GET", OPERATIONS+"/messages/([^/]+)/payload", OPERATIONS+"/payload", "id"));
    private static final List<PathAlias> TRANSFER_ALIASES = List.of(
            new PathAlias("PUT", LEGACY_FILES+"/([^/]+)/parts/([^/]+)", FILES+"/part", "id", "part"),
            new PathAlias("GET", LEGACY_FILES+"/([^/]+)/parts", FILES+"/parts", "id"),
            new PathAlias("POST", LEGACY_FILES+"/([^/]+)/complete", FILES+"/complete", "id"),
            new PathAlias("POST", LEGACY_FILES+"/([^/]+)/submit", FILES+"/submit", "id"),
            new PathAlias("DELETE", LEGACY_FILES+"/([^/]+)", FILES+"/abort", "id"),
            new PathAlias("GET", LEGACY_FILES+"/([^/]+)", FILES+"/status", "id"),
            new PathAlias("POST", LEGACY_FILES+"/([^/]+)/receipts", FILES+"/receipt", "id"),
            new PathAlias("GET", LEGACY_FILES+"/([^/]+)/content", FILES+"/content", "id"),
            new PathAlias("HEAD", LEGACY_FILES+"/([^/]+)/content", FILES+"/content", "id"),
            new PathAlias("PUT", LEGACY_PUSH_UPLOADS+"/([^/]+)/parts/([^/]+)", FILE_PUSH+"/part", "id", "part"),
            new PathAlias("GET", LEGACY_PUSH_UPLOADS+"/([^/]+)/parts", FILE_PUSH+"/parts", "id"),
            new PathAlias("POST", LEGACY_PUSH_UPLOADS+"/([^/]+)/complete", FILE_PUSH+"/complete", "id"),
            new PathAlias("GET", LEGACY_PUSH_UPLOADS+"/([^/]+)", FILE_PUSH+"/status", "id"));

    record Route(String path, Map<String, String> parameters) {}

    static Route route(String method, String path) {
        String exact = EXACT_ALIASES.get(method + " " + path);
        if (exact != null) return new Route(exact, Map.of());
        List<PathAlias> candidates;
        if (path.startsWith(OPERATIONS + "/")) {
            candidates = OPERATION_ALIASES;
        } else if (path.startsWith(LEGACY_FILES + "/") || path.startsWith(LEGACY_PUSH_UPLOADS + "/")) {
            candidates = TRANSFER_ALIASES;
        } else {
            return new Route(path, Map.of());
        }
        for (PathAlias alias : candidates) {
            if (!alias.method().equals(method)) continue;
            var match = alias.path().matcher(path);
            if (match.matches()) {
                var values = new LinkedHashMap<String, String>();
                for (int i = 0; i < alias.parameters().size(); i++) {
                    values.put(alias.parameters().get(i), match.group(i + 1));
                }
                return new Route(alias.target(), Map.copyOf(values));
            }
        }
        return new Route(path, Map.of());
    }

    static boolean auditDiagnosticPath(String method, String path) {
        return EXACT_ALIASES.containsKey(method + " " + path) || EXACT_ALIASES.containsValue(path);
    }

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
    public static boolean endpoint(HttpServletRequest request, String path) {
        String method = request.getMethod();
        if (method.equals("POST") && Set.of("/api/v1/data-push", "/api/v1/simple/ftp",
                "/api/v1/simple/kafka", "/api/v1/simple/log", DEBUG_PUSH,
                DEBUG_TEMPLATE, DEBUG_STATUS).contains(path)) return true;
        if (method.equals("GET") && path.matches("/api/v1/messages/[A-Za-z0-9_-]+")) return true;
        boolean unified = path.startsWith(FILE_PUSH + "/");
        if (!unified && !path.startsWith(FILES+"/")) return false;
        String suffix = path.substring((unified ? FILE_PUSH : FILES).length() + 1);
        String operation = method + " " + suffix;
        if (operation.equals("POST init")) return true;
        if (unified ? operation.equals("PUT single") : Set.of("POST single", "GET page", "POST batch").contains(operation)) return true;
        String id = request.getParameter("id");
        if (id == null || !id.matches("[a-fA-F0-9-]{36}")) return false;
        if (method.equals("PUT") && suffix.equals("part")) {
            String part = request.getParameter("part");
            return part != null && part.matches("[0-9]+");
        }
        if (Set.of("GET status", "GET parts", "POST complete").contains(operation)) return true;
        return !unified && Set.of("DELETE abort", "POST submit", "GET content", "HEAD content").contains(operation);
    }
    private PushAccess() {}
}
