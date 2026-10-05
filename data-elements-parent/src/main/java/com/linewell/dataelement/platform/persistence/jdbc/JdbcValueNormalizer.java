package com.linewell.dataelement.platform.persistence.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalizes vendor-specific JDBC large-object values before business use or JSON serialization. */
public final class JdbcValueNormalizer {

    private JdbcValueNormalizer() {
    }

    public static String text(Object value) {
        Object normalized = normalize(value);
        return normalized == null ? null : String.valueOf(normalized);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, ?> value) {
        return value == null ? null : (Map<String, Object>) normalize(value);
    }

    public static List<Map<String, Object>> maps(List<? extends Map<String, ?>> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(JdbcValueNormalizer::map).toList();
    }

    public static Object normalize(Object value) {
        if (value == null || value instanceof CharSequence || value instanceof Number
                || value instanceof Boolean || value instanceof java.time.temporal.Temporal
                || value instanceof java.util.Date) {
            return value;
        }
        try {
            if (value instanceof Clob clob) {
                return read(clob.getCharacterStream());
            }
            if (value instanceof Blob blob) {
                return read(blob.getBinaryStream());
            }
            if (value instanceof SQLXML sqlxml) {
                return sqlxml.getString();
            }
            if (value instanceof InputStream inputStream) {
                return read(inputStream);
            }
            if (value instanceof Reader reader) {
                return read(reader);
            }
            if (value instanceof byte[] bytes) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            Object vendorTemporal = normalizeVendorTemporal(value);
            if (vendorTemporal != value) {
                return vendorTemporal;
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, item) -> normalized.put(String.valueOf(key), normalize(item)));
                return normalized;
            }
            if (value instanceof Iterable<?> iterable) {
                List<Object> normalized = new ArrayList<>();
                iterable.forEach(item -> normalized.add(normalize(item)));
                return normalized;
            }
            if (value.getClass().isArray()) {
                int length = java.lang.reflect.Array.getLength(value);
                List<Object> normalized = new ArrayList<>(length);
                for (int index = 0; index < length; index++) {
                    normalized.add(normalize(java.lang.reflect.Array.get(value, index)));
                }
                return normalized;
            }
            return value;
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("Failed to read JDBC large-object value", exception);
        }
    }

    private static String read(Reader reader) throws IOException {
        try (reader) {
            StringBuilder value = new StringBuilder();
            char[] buffer = new char[4096];
            int length;
            while ((length = reader.read(buffer)) >= 0) {
                value.append(buffer, 0, length);
            }
            return value.toString();
        }
    }

    private static String read(InputStream input) throws IOException {
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Object normalizeVendorTemporal(Object value) {
        String className = value.getClass().getName();
        if (!className.startsWith("oracle.sql.TIMESTAMP")) {
            return value;
        }
        try {
            return value.getClass().getMethod("timestampValue").invoke(value);
        } catch (ReflectiveOperationException ignored) {
            return String.valueOf(value);
        }
    }
}
