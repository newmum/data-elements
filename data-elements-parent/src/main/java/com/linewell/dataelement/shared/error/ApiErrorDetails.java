package com.linewell.dataelement.shared.error;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stable error contract shared by Magic API and Java runtime adapters.
 */
public record ApiErrorDetails(
        String code,
        String message,
        String detail,
        String traceId,
        String type
) {

    public Map<String, Object> toMap(String method, String path) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("message", message);
        result.put("detail", detail);
        result.put("traceId", traceId);
        result.put("type", type);
        result.put("method", method == null ? "" : method);
        result.put("path", path == null ? "" : path);
        return result;
    }
}
