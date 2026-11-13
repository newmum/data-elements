package com.linewell.dataelement.platform.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/** Legacy allowlisted exact-query fields plus independent, immutable general safe snapshots. */
public final class AuditRequestParameters {
    public static final String DETAILS_KEY = "requestParameters";
    static final String INDEX_KEY = "requestParameterIndex";
    private static final String ATTRIBUTE = AuditRequestParameters.class.getName();
    private static final String SAFE_ATTRIBUTE = ATTRIBUTE + ".safe";
    private static final String BODY_EXPECTED = ATTRIBUTE + ".bodyExpected";
    private static final String BODY_STARTED = ATTRIBUTE + ".bodyStarted";
    private static final String BODY_COMPLETED = ATTRIBUTE + ".bodyCompleted";
    private static final String PARTS_PENDING = ATTRIBUTE + ".partsPending";
    private static final String MVC_CAPTURED = ATTRIBUTE + ".mvcCaptured";
    private static final int MAX_VALUES = 10;
    private static final int MAX_NAME_LENGTH = 255;
    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z0-9_-]{1,64}");
    private static final Set<String> IDS = Set.of("id", "tid", "taskId", "sourceTableId", "targetTableId",
            "targetDbId", "tableId", "datasourceId", "pipelineId");
    private static final Set<String> FLAGS = Set.of("dryRun", "batchDryRun", "deleteTargetTable",
            "confirmUnlinkedTarget", "checkPhysicalExistence");
    private static final Set<String> RESOLVED = Set.of("taskName", "deletedTaskId", "deletedTaskName");

    private AuditRequestParameters() {}

    static void capture(HttpServletRequest request, Object... sources) {
        if (!eligible(request)) return;
        try {
            legacyCapture(request, sources);
            Object query = sources.length > 0 ? sources[0] : null;
            Object path = sources.length > 1 ? sources[1] : null;
            Object body = sources.length > 2 ? sources[2] : null;
            var previous = current(request);
            boolean form = request.getContentType() != null && (request.getContentType().startsWith("application/x-www-form-urlencoded")
                    || request.getContentType().startsWith("multipart/"));
            if (Boolean.TRUE.equals(request.getAttribute(MVC_CAPTURED))) {
                // Magic's ordinary @RequestParam Map loses repeated values and may contain form fields.
                // URL query captured by MVC is authoritative, not overwritten by that lossy adapter.
                if (form && body == null) body = query;
                query = previous.asMap().get("query");
                Map<String, Object> paths = new LinkedHashMap<>();
                ((Map<?, ?>) previous.asMap().get("path")).forEach((key, value) -> paths.put((String) key, value));
                var incomingPath = AuditParameterSnapshot.capture(path, null, null, null, List.of());
                ((Map<?, ?>) incomingPath.asMap().get("path")).forEach((key, value) -> paths.put((String) key, value));
                path = paths;
            }
            save(request, path, query, body, true);
            if (form) request.setAttribute(SAFE_ATTRIBUTE, current(request).withNotice("FORM_PARAMETERS_PARTIAL"));
            if (body == null && (request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null
                    || request.getContentLengthLong() < 0 && request.getContentType() != null)) {
                // Magic can swallow converter errors and return null; absence is not proof of an empty body.
                request.setAttribute(SAFE_ATTRIBUTE, current(request).withNotice("BODY_NOT_CAPTURED"));
            }
        } catch (RuntimeException error) {
            failed(request);
        }
    }

    /** Magic supplies already parsed values. This method never reads a servlet body/stream. */
    public static void capture(HttpServletRequest request, Object query, Object path, Object body) {
        capture(request, new Object[] {query, path, body});
    }

    private static void legacyCapture(HttpServletRequest request, Object... sources) {
        Map<String, Object> parameters = new LinkedHashMap<>(snapshot(request));
        for (Object source : sources) {
            if (!(source instanceof Map<?, ?> values)) continue;
            for (String key : IDS) put(parameters, key, values.get(key));
            for (String key : FLAGS) put(parameters, key, values.get(key));
        }
        if (!parameters.isEmpty()) request.setAttribute(ATTRIBUTE, Map.copyOf(parameters));
    }

    /** Only a server-side business verification entry point may populate verified facts. */
    public static void enrich(HttpServletRequest request, Map<String, Object> verified) {
        if (!eligible(request) || verified == null) return;
        try {
            Map<String, Object> parameters = new LinkedHashMap<>(snapshot(request));
            for (String key : RESOLVED) put(parameters, key, verified.get(key));
            for (String key : FLAGS) put(parameters, key, verified.get(key));
            put(parameters, "taskId", verified.get("taskId"));
            if (!parameters.isEmpty()) request.setAttribute(ATTRIBUTE, Map.copyOf(parameters));
            var previous = current(request);
            Map<String, Object> facts = new LinkedHashMap<>();
            if (previous.asMap().get("verified") instanceof Map<?, ?> old) old.forEach((key, value) -> facts.put((String) key, value));
            // Sanitize before merging, so no unsanitized business objects survive this method.
            var incoming = AuditParameterSnapshot.capture(null, null, null, verified, List.of());
            ((Map<?, ?>) incoming.asMap().get("verified")).forEach((key, value) -> facts.put((String) key, value));
            var notices = new java.util.ArrayList<>(previous.notices());
            notices.addAll(incoming.notices());
            var safe = AuditParameterSnapshot.capture(previous.asMap().get("path"), previous.asMap().get("query"),
                    previous.asMap().get("body"), facts, notices);
            request.setAttribute(SAFE_ATTRIBUTE, previous.status().equals("LEGACY") ? safe.withStatus("LEGACY") : safe);
        } catch (RuntimeException error) {
            failed(request);
        }
    }

    /** New Recorder contract; safe on missing capture, and never invents historical parameters. */
    public static AuditParameterSnapshot safeSnapshot(HttpServletRequest request) {
        try {
            var safe = current(request);
            if (request != null && Boolean.TRUE.equals(request.getAttribute(BODY_EXPECTED))
                    && !Boolean.TRUE.equals(request.getAttribute(BODY_COMPLETED))) return safe.withNotice("BODY_NOT_CAPTURED");
            return safe;
        } catch (RuntimeException error) {
            return AuditParameterSnapshot.empty("FAILED", "CAPTURE_FAILED");
        }
    }

    /** MVC query/path capture without getParameterMap(), which can consume an unparsed form body. */
    static void captureMvc(HttpServletRequest request, Object handler) {
        if (!eligible(request)) return;
        try {
            Map<String, Object> query = new LinkedHashMap<>();
            String raw = request.getQueryString();
            boolean invalid = raw != null && raw.length() > AuditParameterSnapshot.MAX_UTF8_BYTES;
            if (raw != null && !invalid) {
                int start = 0, count = 0;
                while (start < raw.length()) {
                    if (++count > AuditParameterSanitizer.MAX_ITEMS) { invalid = true; break; }
                    int end = raw.indexOf('&', start);
                    if (end < 0) end = raw.length();
                    String pair = raw.substring(start, end);
                    int equal = pair.indexOf('=');
                    String key = URLDecoder.decode(equal < 0 ? pair : pair.substring(0, equal), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(equal < 0 ? "" : pair.substring(equal + 1), StandardCharsets.UTF_8);
                    Object old = query.get(key);
                    if (old == null) query.put(key, value);
                    else if (old instanceof java.util.ArrayList<?> list) {
                        @SuppressWarnings("unchecked") var values = (java.util.ArrayList<String>) list;
                        values.add(value);
                    } else query.put(key, new java.util.ArrayList<>(List.of((String) old, value)));
                    start = end + 1;
                }
            }
            Object body = request instanceof MultipartHttpServletRequest multipart ? multipart.getMultiFileMap() : null;
            legacyCapture(request, query, request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE));
            save(request, request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE), query, body, false);
            request.setAttribute(MVC_CAPTURED, true);
            if (invalid) request.setAttribute(SAFE_ATTRIBUTE, current(request).withNotice("QUERY_LIMIT"));
            if (handler instanceof HandlerMethod method) {
                Set<String> pending = new java.util.LinkedHashSet<>();
                // Bodies handled directly or by an unsupported binding path must not look fully empty.
                boolean expected = request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null;
                for (var parameter : method.getMethodParameters()) {
                    if (parameter.hasParameterAnnotation(RequestBody.class) || org.springframework.http.HttpEntity.class.isAssignableFrom(parameter.getParameterType())) expected = true;
                    var part = parameter.getParameterAnnotation(RequestPart.class);
                    if (part != null && part.required()) {
                        String name = partName(parameter);
                        if (name != null && pending.size() < AuditParameterSanitizer.MAX_ITEMS) pending.add(name);
                    }
                }
                // MultipartFile arguments need no converter; their already-resolved metadata is complete.
                if (body instanceof Map<?, ?> files) {
                    for (var parameter : method.getMethodParameters()) {
                        String name = partName(parameter);
                        Class<?> type = parameter.getParameterType();
                        if (name != null && files.containsKey(name) && (org.springframework.web.multipart.MultipartFile.class.isAssignableFrom(type)
                                || jakarta.servlet.http.Part.class.isAssignableFrom(type))) pending.remove(name);
                    }
                }
                if (!pending.isEmpty()) expected = true;
                request.setAttribute(PARTS_PENDING, Set.copyOf(pending));
                if (expected) request.setAttribute(BODY_EXPECTED, true);
            }
        } catch (RuntimeException error) {
            failed(request);
        }
    }

    static void bodyStarted(HttpServletRequest request) {
        bodyStarted(request, null);
    }

    static void bodyStarted(HttpServletRequest request, String part) {
        if (!eligible(request)) return;
        try {
            request.setAttribute(BODY_EXPECTED, true);
            request.setAttribute(BODY_STARTED, true);
            request.setAttribute(BODY_COMPLETED, false);
            if (part != null) {
                Set<String> pending = new java.util.LinkedHashSet<>(pendingParts(request));
                if (pending.size() < AuditParameterSanitizer.MAX_ITEMS) pending.add(part);
                request.setAttribute(PARTS_PENDING, Set.copyOf(pending));
            }
        } catch (RuntimeException error) { failed(request); }
    }

    static void convertedBody(HttpServletRequest request, Object body) {
        if (!eligible(request)) return;
        try {
            var previous = current(request);
            legacyCapture(request, body);
            save(request, previous.asMap().get("path"), previous.asMap().get("query"), body, true);
        } catch (RuntimeException error) {
            failed(request);
        }
    }

    static void convertedPart(HttpServletRequest request, String name, Object body) {
        if (!eligible(request)) return;
        try {
            var previous = current(request);
            Map<String, Object> parts = new LinkedHashMap<>();
            if (previous.asMap().get("body") instanceof Map<?, ?> old) old.forEach((key, value) -> parts.put((String) key, value));
            var part = AuditParameterSnapshot.capture(null, null, body, null, List.of());
            parts.put(name, part.asMap().get("body"));
            var notices = new java.util.ArrayList<>(previous.notices()); notices.addAll(part.notices());
            request.setAttribute(SAFE_ATTRIBUTE, AuditParameterSnapshot.capture(previous.asMap().get("path"), previous.asMap().get("query"),
                    parts, previous.asMap().get("verified"), notices));
            Set<String> pending = new java.util.LinkedHashSet<>(pendingParts(request)); pending.remove(name);
            request.setAttribute(PARTS_PENDING, Set.copyOf(pending));
            request.setAttribute(BODY_COMPLETED, pending.isEmpty());
        } catch (RuntimeException error) { failed(request); }
    }

    static Object decodedBody(HttpServletRequest request, String body) {
        if (!eligible(request)) return null;
        try {
            if (body.length() > AuditParameterSnapshot.MAX_UTF8_BYTES || body.getBytes(StandardCharsets.UTF_8).length > AuditParameterSnapshot.MAX_UTF8_BYTES) {
                request.setAttribute(SAFE_ATTRIBUTE, current(request).withNotice("BODY_LIMIT"));
                return null;
            }
            return AuditParameterSnapshot.decodedJsonBody(body);
        } catch (Exception error) {
            if (request != null) request.setAttribute(SAFE_ATTRIBUTE, current(request).withNotice("BODY_CONVERSION_FAILED"));
            return null;
        }
    }

    static String partName(org.springframework.core.MethodParameter parameter) {
        if (parameter == null) return null;
        var part = parameter.getParameterAnnotation(RequestPart.class);
        if (part == null) return null;
        String name = !part.name().isEmpty() ? part.name() : !part.value().isEmpty() ? part.value() : parameter.getParameterName();
        return name == null ? "unnamedPart" : name;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> pendingParts(HttpServletRequest request) {
        return request.getAttribute(PARTS_PENDING) instanceof Set<?> parts ? (Set<String>) parts : Set.of();
    }

    static void complete(HttpServletRequest request) {
        if (!eligible(request)) return;
        try {
            if (Boolean.TRUE.equals(request.getAttribute(BODY_EXPECTED)) && !Boolean.TRUE.equals(request.getAttribute(BODY_COMPLETED))) {
                request.setAttribute(SAFE_ATTRIBUTE, current(request).withNotice(Boolean.TRUE.equals(request.getAttribute(BODY_STARTED))
                        ? "BODY_CONVERSION_FAILED" : "BODY_NOT_CAPTURED"));
                request.setAttribute(BODY_COMPLETED, true);
            }
        } catch (RuntimeException error) {
            failed(request);
        }
    }

    private static void save(HttpServletRequest request, Object path, Object query, Object body, boolean bodyCompleted) {
        var previous = current(request);
        request.setAttribute(SAFE_ATTRIBUTE, AuditParameterSnapshot.capture(path, query, body, previous.asMap().get("verified"), previous.notices()));
        if (bodyCompleted) request.setAttribute(BODY_COMPLETED, true);
    }

    private static AuditParameterSnapshot current(HttpServletRequest request) {
        return request != null && request.getAttribute(SAFE_ATTRIBUTE) instanceof AuditParameterSnapshot value
                ? value : AuditParameterSnapshot.empty("LEGACY");
    }

    private static void failed(HttpServletRequest request) {
        if (request == null) return;
        try { request.setAttribute(SAFE_ATTRIBUTE, current(request).withNotice("CAPTURE_FAILED")); }
        catch (RuntimeException ignored) { /* Audit collection must never replace a business error. */ }
    }

    static void captureFailed(HttpServletRequest request) {
        if (eligible(request)) failed(request);
    }

    static boolean eligible(HttpServletRequest request) {
        if (request == null) return false;
        try {
            String path = request.getRequestURI();
            if (path == null) return false;
            if (request.getAttribute(AuditIdentityResolver.ATTRIBUTE) instanceof AuditIdentity actor && actor.platform()) return false;
            return !(path.startsWith("/portal/") || path.startsWith("/idaas/") || path.startsWith("/sym/logs/")
                    || path.startsWith("/api/web/") || path.startsWith("/auth/") || path.startsWith("/security/"));
        } catch (RuntimeException error) { return false; }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> snapshot(HttpServletRequest request) {
        return request != null && request.getAttribute(ATTRIBUTE) instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
    }

    public static Map<String, Object> display(Object input) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (input instanceof Map<?, ?> values) {
            for (String key : IDS) put(result, key, values.get(key));
            for (String key : FLAGS) put(result, key, values.get(key));
            for (String key : RESOLVED) put(result, key, values.get(key));
        }
        return result;
    }

    private static void put(Map<String, Object> output, String key, Object value) {
        if (value instanceof Collection<?> values && RESOLVED.contains(key)) {
            List<String> items = values.stream().limit(MAX_VALUES)
                    .map(item -> normalized(key, item)).filter(item -> item != null).distinct().toList();
            if (!items.isEmpty()) output.put(key, items);
        } else {
            String item = normalized(key, value);
            if (item != null) output.put(key, item);
        }
    }

    private static String normalized(String key, Object value) {
        if (FLAGS.contains(key)) {
            return value instanceof Boolean || "true".equals(value) || "false".equals(value)
                    ? value.toString() : null;
        }
        if (!(value instanceof CharSequence || value instanceof Number)) return null;
        String raw = value.toString();
        // Keep legacy shapes/indexes, but do not let this compatibility path bypass safe text handling.
        if (raw.length() > AuditParameterSanitizer.MAX_STRING * 2) return null;
        String safe = AuditParameterSanitizer.safeText(raw);
        if (AuditParameterSanitizer.REDACTED.equals(safe)) return null;
        String text = AuditSanitizer.text(safe, MAX_NAME_LENGTH).strip();
        if (IDS.contains(key) || key.equals("deletedTaskId")) return IDENTIFIER.matcher(text).matches() ? text : null;
        return RESOLVED.contains(key) && !text.isEmpty() ? text : null;
    }

    static Map<String, Boolean> index(Map<String, Object> parameters) {
        Map<String, Boolean> index = new LinkedHashMap<>();
        parameters.forEach((key, value) -> {
            if (value instanceof List<?> items) {
                items.forEach(item -> index.put(indexKey(key, (String) item), true));
            } else index.put(indexKey(key, (String) value), true);
        });
        return index;
    }

    public static String exactMatchPattern(String key, String value) {
        // 查询不能把敏感原文先掩码再命中安全值，否则旧精确契约会成为原文检索旁路。
        if (value == null || !AuditParameterSanitizer.safeText(value).equals(value)) {
            throw new IllegalArgumentException("日志参数名称或参数值无效");
        }
        String normalized = normalized(key, value);
        if (normalized == null || value.codePointCount(0, value.length()) > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("日志参数名称或参数值无效");
        }
        // This property is emitted only by the audit writer, never by sanitized event details.
        return "%\"" + indexKey(key, normalized) + "\":true%";
    }

    private static String indexKey(String key, String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return "auditParam." + key + "." + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
