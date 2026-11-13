package com.linewell.dataelement.platform.audit;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable safe JSON plus a search projection derived exclusively from that same safe value. */
public final class AuditParameterSnapshot {
    public static final int MAX_UTF8_BYTES = AuditParameterSanitizer.MAX_BYTES;
    private static final ObjectMapper JSON = new ObjectMapper();
    static {
        JSON.enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        JSON.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(16).maxStringLength(MAX_UTF8_BYTES).maxNumberLength(128).build());
    }
    private final Map<String, Object> value;
    private final String json;
    private final String searchText;
    private final boolean searchLimited;

    private AuditParameterSnapshot(Map<String, Object> value, String json) {
        this.value = Collections.unmodifiableMap(value);
        this.json = json;
        StringBuilder search = new StringBuilder();
        int[] bytes = {0};
        for (String section : List.of("path", "query", "body", "verified", "legacy")) {
            if (value.get(section) != null) project(section, value.get(section), search, bytes);
        }
        this.searchText = search.toString();
        this.searchLimited = bytes[0] > MAX_UTF8_BYTES;
    }

    public Map<String, Object> asMap() { return value; }
    public String json() { return json; }
    public String searchText() { return searchText; }
    public String status() { return (String) value.get("status"); }

    AuditParameterSnapshot withStatus(String status) {
        if (value.containsKey("legacy")) return this; // Unknown historical sources can never become complete capture.
        return encode(status, value.get("path"), value.get("query"), value.get("body"), value.get("verified"), notices());
    }

    static AuditParameterSnapshot capture(Object path, Object query, Object body, Object verified, Collection<String> priorNotices) {
        var sanitizer = new AuditParameterSanitizer();
        priorNotices.forEach(sanitizer::notice);
        try {
            Map<String, Object> safePath = sanitizer.section(path);
            Map<String, Object> safeQuery = sanitizer.section(query);
            Object safeBody = body == null ? null : sanitizer.body(body);
            Map<String, Object> safeVerified = sanitizer.section(verified);
            boolean failed = sanitizer.failed || sanitizer.notices.contains("BODY_CONVERSION_FAILED") || sanitizer.notices.contains("CAPTURE_FAILED");
            String status = failed ? sanitizer.kept > 0 ? "PARTIAL" : "FAILED"
                    : sanitizer.notices.isEmpty() ? sanitizer.kept == 0 ? "EMPTY" : "CAPTURED"
                    : sanitizer.kept > 0 ? "PARTIAL" : "OMITTED";
            if (sanitizer.notices.contains("BODY_NOT_CAPTURED")) status = "PARTIAL";
            return encode(status, safePath, safeQuery, safeBody, safeVerified, List.copyOf(sanitizer.notices));
        } catch (RuntimeException error) {
            return empty("FAILED", "CAPTURE_FAILED");
        }
    }

    static AuditParameterSnapshot empty(String status, String... notices) {
        return encode(status, Map.of(), Map.of(), null, Map.of(), List.of(notices));
    }

    AuditParameterSnapshot withNotice(String notice) {
        List<String> codes = new ArrayList<>(notices());
        // Failure evidence must not be lost when the ordinary notice budget was exhausted.
        if (!codes.contains(notice)) {
            if (codes.size() >= AuditParameterSanitizer.MAX_NOTICES) codes.removeLast();
            codes.add(notice);
        }
        if (value.containsKey("legacy")) return legacy(value.get("legacy"), codes);
        return capture(value.get("path"), value.get("query"), value.get("body"), value.get("verified"), codes);
    }

    @SuppressWarnings("unchecked")
    List<String> notices() { return (List<String>) value.get("notices"); }

    static Object decodedJsonBody(String body) throws java.io.IOException {
        return JSON.readValue(body, Object.class);
    }

    /** 仅从旧 details.requestParameters 白名单重建安全投影；来源未记录，始终为不完整的 LEGACY。 */
    public static AuditParameterSnapshot fromLegacy(Object storedDetails) {
        if (storedDetails == null) return empty("LEGACY", "LEGACY_PARAMETERS_NOT_RECORDED");
        try {
            Object input = storedDetails;
            if (input instanceof String text) {
                if (text.length() > MAX_UTF8_BYTES || text.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
                    return empty("LEGACY", "TOTAL_BYTE_LIMIT");
                }
                if (text.isBlank()) return empty("LEGACY", "LEGACY_PARAMETERS_NOT_RECORDED");
                input = JSON.readValue(text, Object.class);
            }
            if (!(input instanceof Map<?, ?> details)) return empty("LEGACY", "INVALID_LEGACY_DETAILS");
            // Never inspect raw fields/req_param or infer sources/facts from current business records.
            Object parameters = details.get(AuditRequestParameters.DETAILS_KEY);
            if (parameters == null) return empty("LEGACY", "LEGACY_PARAMETERS_NOT_RECORDED");
            return legacy(parameters, List.of());
        } catch (Exception error) {
            return empty("LEGACY", "INVALID_LEGACY_DETAILS");
        }
    }

    private static AuditParameterSnapshot legacy(Object parameters, Collection<String> priorNotices) {
        if (!(parameters instanceof Map<?, ?>)) return empty("LEGACY", "INVALID_LEGACY_DETAILS");
        var sanitizer = new AuditParameterSanitizer();
        sanitizer.notice("LEGACY_SOURCE_NOT_RECORDED");
        priorNotices.forEach(sanitizer::notice);
        try {
            // Reapply both boundaries even when parsing a previously persisted legacy snapshot.
            Map<String, Object> safe = sanitizer.section(AuditRequestParameters.display(parameters));
            if (sanitizer.failed) return empty("LEGACY", "VALUE_CAPTURE_FAILED");
            if (safe.isEmpty()) sanitizer.notice("LEGACY_PARAMETERS_NOT_RECORDED");
            return encode("LEGACY", Map.of(), Map.of(), null, Map.of(), safe.isEmpty() ? null : safe,
                    List.copyOf(sanitizer.notices));
        } catch (RuntimeException error) {
            return empty("LEGACY", "INVALID_LEGACY_DETAILS");
        }
    }

    /** Detail-service adapter: missing collection is LEGACY, corrupt new data is FAILED, never fabricated. */
    public static Map<String, Object> parse(Object stored) {
        if (stored == null || stored instanceof String text && text.isBlank()) return empty("LEGACY").asMap();
        if (!(stored instanceof String text) || text.length() > MAX_UTF8_BYTES || text.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            return empty("FAILED", "INVALID_STORED_SNAPSHOT").asMap();
        }
        try {
            Object input = JSON.readValue(text, Object.class);
            if (!(input instanceof Map<?, ?> map) || !Integer.valueOf(1).equals(map.get("version"))
                    || !(map.get("path") instanceof Map<?, ?>) || !(map.get("query") instanceof Map<?, ?>)
                    || !map.containsKey("body") || !(map.get("verified") instanceof Map<?, ?>) || !(map.get("notices") instanceof List<?>)
                    || !Set.of("EMPTY", "CAPTURED", "PARTIAL", "OMITTED", "FAILED", "LEGACY").contains(map.get("status"))) {
                return empty("FAILED", "INVALID_STORED_SNAPSHOT").asMap();
            }
            var notices = ((List<?>) map.get("notices")).stream().limit(AuditParameterSanitizer.MAX_NOTICES)
                    .filter(String.class::isInstance).map(String.class::cast).filter(AuditParameterSanitizer.NOTICE_CODES::contains).toList();
            if ("LEGACY".equals(map.get("status")) && map.containsKey("legacy")) {
                return legacy(map.get("legacy"), notices).asMap();
            }
            var safe = capture(map.get("path"), map.get("query"), map.get("body"), map.get("verified"), notices);
            // Re-sanitize untrusted stored content; never return arbitrary persisted notice text.
            String storedStatus = (String) map.get("status");
            boolean contradicted = !notices.isEmpty() && Set.of("CAPTURED", "EMPTY").contains(storedStatus)
                    || storedStatus.equals("EMPTY") && !safe.status().equals("EMPTY");
            return encode(!contradicted && safe.notices().equals(notices) ? storedStatus : safe.status(),
                    safe.value.get("path"), safe.value.get("query"), safe.value.get("body"), safe.value.get("verified"), safe.notices()).asMap();
        } catch (Exception error) {
            return empty("FAILED", "INVALID_STORED_SNAPSHOT").asMap();
        }
    }

    private static AuditParameterSnapshot encode(String status, Object path, Object query, Object body, Object verified, List<String> notices) {
        return encode(status, path, query, body, verified, null, notices);
    }

    private static AuditParameterSnapshot encode(String status, Object path, Object query, Object body, Object verified,
            Map<String, Object> legacy, List<String> notices) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1); root.put("status", status); root.put("path", path); root.put("query", query);
        root.put("body", body); root.put("verified", verified);
        if (legacy != null) root.put("legacy", legacy);
        root.put("notices", notices);
        try {
            String json = JSON.writeValueAsString(root);
            if (json.getBytes(StandardCharsets.UTF_8).length <= MAX_UTF8_BYTES) {
                var snapshot = new AuditParameterSnapshot(root, json);
                if (!snapshot.searchLimited) return snapshot;
            }
            return empty(legacy != null ? "LEGACY" : "OMITTED", "TOTAL_BYTE_LIMIT");
        } catch (Exception error) {
            // Constants and already sanitized JSON values only. Never include exception details.
            if (legacy != null) return empty("LEGACY", "ENCODING_FAILED");
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("version", 1); fallback.put("status", "FAILED"); fallback.put("path", Map.of()); fallback.put("query", Map.of());
            fallback.put("body", null); fallback.put("verified", Map.of()); fallback.put("notices", List.of("ENCODING_FAILED"));
            return new AuditParameterSnapshot(fallback, "{\"version\":1,\"status\":\"FAILED\",\"path\":{},\"query\":{},\"body\":null,\"verified\":{},\"notices\":[\"ENCODING_FAILED\"]}");
        }
    }

    private static void project(String path, Object value, StringBuilder out, int[] bytes) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> project(path + "." + key, item, out, bytes));
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) project(path + "[" + i + "]", list.get(i), out, bytes);
        } else if (!(value instanceof String text) || !Set.of(AuditParameterSanitizer.REDACTED, "[OMITTED]").contains(text)) {
            String line = path + "=" + Objects.toString(value, "null") + "\n";
            // A separate bound on repeated nested paths, cut only at whole safe-value boundaries.
            int length = line.getBytes(StandardCharsets.UTF_8).length;
            bytes[0] += length;
            if (bytes[0] <= MAX_UTF8_BYTES) out.append(line);
        }
    }
}