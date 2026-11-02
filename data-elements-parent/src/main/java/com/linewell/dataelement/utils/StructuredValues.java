package com.linewell.dataelement.utils;

import java.util.List;
import java.util.Map;

/** Validates erased JSON/container boundaries without copying mutable response objects. */
public final class StructuredValues {
    private StructuredValues() {}

    public static Map<String, Object> objectMap(Object value) {
        if (value == null) return null;
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("数据必须是对象");
        }
        for (Object key : map.keySet()) {
            if (!(key instanceof String)) {
                throw new IllegalArgumentException("对象字段名必须是字符串");
            }
        }
        // Every key is checked above; Object places no restriction on the values.
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) map;
        return result;
    }

    public static List<Map<String, Object>> objectRows(Object value) {
        if (value == null) return null;
        if (!(value instanceof List<?> rows)) {
            throw new IllegalArgumentException("数据必须是对象列表");
        }
        for (Object row : rows) {
            objectMap(row);
        }
        // Both the list elements and every map key have been validated.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) rows;
        return result;
    }
}
