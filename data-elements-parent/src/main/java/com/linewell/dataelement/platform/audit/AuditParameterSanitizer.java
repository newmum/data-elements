package com.linewell.dataelement.platform.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Part;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/** Request-thread-only traversal. Never invokes arbitrary serializers, getters or toString methods. */
final class AuditParameterSanitizer {
    static final int MAX_DEPTH = 6;
    static final int MAX_NODES = 512;
    static final int MAX_ITEMS = 64;
    static final int MAX_STRING = 1024;
    static final int MAX_KEY = 128;
    static final int MAX_NOTICES = 12;
    static final int MAX_BYTES = 32768;
    static final String REDACTED = "[REDACTED]";
    static final Set<String> NOTICE_CODES = Set.of("SECTION_OMITTED", "NODE_LIMIT", "DEPTH_LIMIT", "UNSAFE_TYPE_OMITTED",
            "CYCLE_OMITTED", "ITEM_LIMIT", "VALUE_CAPTURE_FAILED", "NUMBER_LIMIT", "PERSONAL_DATA_MASKED", "STRING_LIMIT",
            "SECRET_TEXT_REDACTED", "TEXT_NORMALIZED_OR_MASKED", "KEY_OMITTED", "SECRET_FIELD_OMITTED",
            "HIGH_RISK_FIELD_OMITTED", "PERSONAL_FIELD_REDACTED", "PRIVATE_FIELD_OMITTED", "UNSUPPORTED_TYPE_OMITTED",
            "FILE_METADATA_ONLY", "BODY_CONVERSION_FAILED", "CAPTURE_FAILED", "BODY_NOT_CAPTURED", "QUERY_LIMIT",
            "INVALID_STORED_SNAPSHOT", "TOTAL_BYTE_LIMIT", "ENCODING_FAILED", "ACCESS_DENIED", "HIGH_RISK_TEXT_OMITTED",
                "BODY_LIMIT", "FORM_PARAMETERS_PARTIAL", "LEGACY_SOURCE_NOT_RECORDED", "LEGACY_PARAMETERS_NOT_RECORDED",
                "INVALID_LEGACY_DETAILS");
    private static final Object OMIT = new Object();
    private static final Pattern SECRET_KEY = Pattern.compile("(?i).*(password|passwd|pwd|secret|token|cookie|authorization|credential|private.?key|api.?key|access.?key|keytab|signature|salt|hash|验证码|密码|口令|密钥).*");
            // Business/status/region codes are ordinary values; standalone code and source-code fields are not.
            private static final Pattern RISK_KEY = Pattern.compile("(?i)(?:.*(sql|script|connection|config|statement|queryText|command|expression|headers|源码|脚本|配置|连接串).*|(?:source[ _-]?)?code)");
    private static final Pattern PERSONAL_KEY = Pattern.compile("(?i)^(realName|fullName|contactName|personName|address|身份证|姓名|住址|bankAccount|passport|idCard|identityCard|phone|mobile|telephone|tel|email)(No|Number)?$");
    private static final Pattern PERSONAL_TEXT = Pattern.compile("(?i)(?:realName|fullName|contactName|personName|address|身份证|姓名|住址|bankAccount|passport|idCard|identityCard|phone|mobile|telephone|email)[\\s\"'\\\\]*[:=：]");
    private static final Pattern SECRET_TEXT = Pattern.compile("(?i)password|passwd|\\bpwd\\b|secret|token|cookie|authorization|credential|private[ _-]?key|api[ _-]?key|access[ _-]?key|密码|口令|密钥|\\b(?:bearer|basic)\\s+\\S+|-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----|\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+|\\bAKIA[A-Z0-9]{16}\\b|[a-z][a-z0-9+.-]*://[^\\s/@]+@");
    private static final Pattern RISK_TEXT = Pattern.compile("(?is)^\\s*(?:select\\s+.+\\bfrom\\b|insert\\s+into\\b|update\\s+.+\\bset\\b|delete\\s+from\\b|(?:drop|alter|create)\\s+table\\b|jdbc:|javascript:|<script\\b|function\\s*[a-z0-9_$]*\\s*\\(|(?:import|package)\\s+[a-z0-9_.]+[;\\s])");
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(?:\\+?86[- ]?)?(1[3-9][0-9])([0-9]{4})([0-9]{4})(?![0-9])");
    private static final Pattern EMAIL = Pattern.compile("(?i)([A-Z0-9._%+-])[A-Z0-9._%+-]*@([A-Z0-9.-]+\\.[A-Z]{2,})");
    private static final Pattern ID_CARD = Pattern.compile("(?<![A-Za-z0-9])([1-9][0-9]{2})[0-9]{3}(?:18|19|20)[0-9]{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12][0-9]|3[01])([0-9]{3}[0-9Xx])(?![A-Za-z0-9])");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("(?i)(?:银行卡|银行账号|bank\\s*account)[\\s:=：]*[0-9]{12,19}");
    private static final Pattern ENCODED = Pattern.compile("%[0-9a-fA-F]{2}");
    final LinkedHashSet<String> notices = new LinkedHashSet<>();
    private final Set<Object> ancestors = Collections.newSetFromMap(new IdentityHashMap<>());
    private int nodes;
    int kept;
    boolean failed;

    void notice(String code) {
        if (NOTICE_CODES.contains(code) && notices.size() < MAX_NOTICES) notices.add(code);
    }

    Object body(Object input) {
        Object value = clean(input, 0);
        return value == OMIT ? null : value;
    }

    Map<String, Object> section(Object input) {
        if (input == null) return Map.of();
        Object value = clean(input, 0);
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked") var typed = (Map<String, Object>) map;
            return typed;
        }
        notice("SECTION_OMITTED");
        return Map.of();
    }

    private Object clean(Object value, int depth) {
        if (!visit(depth)) return OMIT;
        if (value == null) { kept++; return null; }
        if (value instanceof String text) return string(text);
        if (value instanceof Boolean) { kept++; return value; }
        if (value instanceof Character character) return string(character.toString());
        if (value instanceof Number number) return number(number);
        if (value instanceof Enum<?> item) return string(item.name());
        Class<?> scalarType = value.getClass();
        if (scalarType == UUID.class || scalarType == java.time.Instant.class || scalarType == java.time.LocalDate.class
                || scalarType == java.time.LocalTime.class || scalarType == java.time.LocalDateTime.class
                || scalarType == java.time.OffsetTime.class || scalarType == java.time.OffsetDateTime.class
                || scalarType == java.time.ZonedDateTime.class || scalarType == java.time.Duration.class || scalarType == java.time.Period.class) {
            return string(value.toString()); // Only known immutable JDK scalar implementations.
        }
        if (scalarType == java.util.Date.class || scalarType == java.sql.Date.class || scalarType == java.sql.Timestamp.class) {
            return string(java.time.Instant.ofEpochMilli(((java.util.Date) value).getTime()).toString());
        }
        if (value instanceof MultipartFile file) return file(file.getOriginalFilename(), file.getContentType(), file.getSize(), depth);
        if (value instanceof Part part) return file(part.getSubmittedFileName(), part.getContentType(), part.getSize(), depth);
        if (value instanceof File file) return file(file.getName(), null, null, depth);
        if (value instanceof Path path) return file(path.getFileName() == null ? null : path.getFileName().toString(), null, null, depth);
        if (value instanceof InputStream || value instanceof OutputStream || value instanceof Reader || value instanceof Writer
                || value instanceof ServletRequest || value instanceof ServletResponse || value instanceof Resource
                || value instanceof ByteBuffer || value instanceof byte[] || value instanceof char[]
                || value instanceof java.util.stream.BaseStream<?, ?> || value instanceof Class<?> || value instanceof ClassLoader
                || value instanceof Thread || value instanceof Throwable) {
            notice("UNSAFE_TYPE_OMITTED"); return OMIT;
        }
        if (!ancestors.add(value)) { notice("CYCLE_OMITTED"); return OMIT; }
        try {
            if (value instanceof JsonNode node) return jsonNode(node, depth);
            if (value instanceof Map<?, ?> map) return map(map.entrySet().iterator(), depth);
            if (value instanceof Collection<?> items) return list(items.iterator(), depth);
            if (value.getClass().isArray()) {
                List<Object> out = new ArrayList<>();
                int length = Array.getLength(value);
                for (int i = 0; i < Math.min(length, MAX_ITEMS) && nodes < MAX_NODES; i++) {
                    Object item = clean(Array.get(value, i), depth + 1);
                    out.add(item == OMIT ? "[OMITTED]" : item);
                }
                if (out.size() < length) notice("ITEM_LIMIT");
                return Collections.unmodifiableList(out);
            }
            return dto(value, depth);
        } catch (RuntimeException | ReflectiveOperationException error) {
            // No original values, exception messages or field paths are retained on failures.
            failed = true; notice("VALUE_CAPTURE_FAILED"); return OMIT;
        } finally {
            ancestors.remove(value);
        }
    }

    private Object number(Number value) {
        Class<?> type = value.getClass();
        if (!(type == Byte.class || type == Short.class || type == Integer.class || type == Long.class
                || type == Float.class || type == Double.class || type == BigInteger.class || type == BigDecimal.class)) {
            notice("UNSAFE_TYPE_OMITTED"); return OMIT;
        }
        if (value instanceof Double d && !Double.isFinite(d) || value instanceof Float f && !Float.isFinite(f)
                || value instanceof BigInteger i && i.bitLength() > 256
                || value instanceof BigDecimal d && (d.precision() > 80 || Math.abs((long) d.scale()) > 80)) {
            notice("NUMBER_LIMIT"); return OMIT;
        }
        // Numeric personal identifiers are subject to the same masking as string identifiers.
        String text = value.toString();
        String safe = safeText(text);
        kept++;
        if (!text.equals(safe)) { notice("PERSONAL_DATA_MASKED"); return safe; }
        return value;
    }

    private Object string(String text) {
        // Oversized strings are omitted, not cut through a possibly incomplete secret/URI.
        if (text.length() > MAX_STRING * 2 || text.codePointCount(0, text.length()) > MAX_STRING) {
            notice("STRING_LIMIT"); return OMIT;
        }
        String safe = safeText(text);
        if (REDACTED.equals(safe)) { notice("SECRET_TEXT_REDACTED"); return safe; }
        if (RISK_TEXT.matcher(safe).find()) { notice("HIGH_RISK_TEXT_OMITTED"); return OMIT; }
        if (!safe.equals(text)) notice("TEXT_NORMALIZED_OR_MASKED");
        kept++;
        return safe;
    }

    static String safeText(String text) {
        if (SECRET_TEXT.matcher(text).find() || PERSONAL_TEXT.matcher(text).find()) return REDACTED;
        // Decode only for detection; encoded credentials never escape via a benign free-text field.
        String probe = text;
        for (int i = 0; i < 2 && ENCODED.matcher(probe).find(); i++) {
            try { probe = java.net.URLDecoder.decode(probe.replaceAll("%(?![0-9a-fA-F]{2})", "%25"), java.nio.charset.StandardCharsets.UTF_8); }
            catch (IllegalArgumentException invalidEncoding) { return REDACTED; }
            if (SECRET_TEXT.matcher(probe).find() || PERSONAL_TEXT.matcher(probe).find()) return REDACTED;
        }
        if (!probe.equals(text) && (PHONE.matcher(probe).find() || EMAIL.matcher(probe).find() || ID_CARD.matcher(probe).find()
                || ACCOUNT_NUMBER.matcher(probe).find())) return REDACTED;
        String safe = PHONE.matcher(text).replaceAll("$1****$3");
        safe = EMAIL.matcher(safe).replaceAll("$1***@$2");
        safe = ID_CARD.matcher(safe).replaceAll("$1***********$2");
        safe = ACCOUNT_NUMBER.matcher(safe).replaceAll("[PERSONAL_NUMBER]");
        return safe.replaceAll("[\\r\\n\\p{Cntrl}]", " ");
    }

    private String key(Object input) {
        if (!(input instanceof String key) || key.length() > MAX_KEY || key.isBlank()) { notice("KEY_OMITTED"); return null; }
        if (SECRET_KEY.matcher(key).matches()) { notice("SECRET_FIELD_OMITTED"); return null; }
        if (RISK_KEY.matcher(key).matches()) { notice("HIGH_RISK_FIELD_OMITTED"); return null; }
        if (!safeText(key).equals(key)) { notice("KEY_OMITTED"); return null; }
        return key;
    }

    private Object field(String key, Object value, int depth) {
        if (value instanceof JsonNode node && node.isNull()) return clean(null, depth + 1);
        if (PERSONAL_KEY.matcher(key).matches() && value != null) {
            if (!visit(depth + 1)) return OMIT;
            notice("PERSONAL_FIELD_REDACTED"); return REDACTED;
        }
        return clean(value, depth + 1);
    }

    private boolean visit(int depth) {
        if (++nodes > MAX_NODES) { notice("NODE_LIMIT"); return false; }
        if (depth > MAX_DEPTH) { notice("DEPTH_LIMIT"); return false; }
        return true;
    }

    private Map<String, Object> map(Iterator<? extends Map.Entry<?, ?>> entries, int depth) {
        Map<String, Object> out = new LinkedHashMap<>();
        int count = 0;
        while (count < MAX_ITEMS && nodes < MAX_NODES && entries.hasNext()) {
            count++;
            var entry = entries.next();
            String key = key(entry.getKey());
            if (key == null) continue;
            Object item = field(key, entry.getValue(), depth);
            if (item != OMIT) out.put(key, item);
        }
        if (entries.hasNext()) notice("ITEM_LIMIT");
        return Collections.unmodifiableMap(out);
    }

    private List<Object> list(Iterator<?> items, int depth) {
        List<Object> out = new ArrayList<>();
        while (out.size() < MAX_ITEMS && nodes < MAX_NODES && items.hasNext()) {
            Object item = clean(items.next(), depth + 1);
            out.add(item == OMIT ? "[OMITTED]" : item);
        }
        if (items.hasNext()) notice("ITEM_LIMIT");
        return Collections.unmodifiableList(out);
    }

    private Object jsonNode(JsonNode node, int depth) {
        if (node.isObject()) return map(node.fields(), depth);
        if (node.isArray()) return list(node.elements(), depth);
        if (node.isNull()) { kept++; return null; }
        if (node.isTextual()) return string(node.textValue());
        if (node.isBoolean()) { kept++; return node.booleanValue(); }
        if (node.isNumber()) return number(node.numberValue());
        notice("UNSAFE_TYPE_OMITTED"); return OMIT;
    }

    private Object dto(Object value, int depth) throws ReflectiveOperationException {
        String packageName = value.getClass().getPackageName();
        if (packageName.startsWith("java.") || packageName.startsWith("javax.") || packageName.startsWith("jakarta.")
                || packageName.startsWith("org.springframework.") || value.getClass().isSynthetic()) {
            notice("UNSAFE_TYPE_OMITTED"); return OMIT;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        int count = 0;
        // Fields cover records/Lombok/normal DTOs without executing user getter code.
        for (Class<?> type = value.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (var member : type.getDeclaredFields()) {
                if (Modifier.isStatic(member.getModifiers()) || Modifier.isTransient(member.getModifiers()) || member.isSynthetic()) continue;
                if (++count > MAX_ITEMS || nodes >= MAX_NODES) { notice("ITEM_LIMIT"); return Collections.unmodifiableMap(out); }
                var ignored = member.getAnnotation(JsonIgnore.class);
                var property = member.getAnnotation(JsonProperty.class);
                if (ignored != null && ignored.value() || property != null && property.access() == JsonProperty.Access.WRITE_ONLY) {
                    notice("PRIVATE_FIELD_OMITTED"); continue;
                }
                String key = key(member.getName());
                if (key == null) continue;
                if (property != null && !property.value().isEmpty()) key = key(property.value());
                if (key == null) continue;
                if (!member.trySetAccessible()) { failed = true; notice("VALUE_CAPTURE_FAILED"); continue; }
                // A JSON alias cannot declassify the original personal-data field name.
                Object item = field(PERSONAL_KEY.matcher(member.getName()).matches() ? member.getName() : key, member.get(value), depth);
                if (item != OMIT) out.putIfAbsent(key, item);
            }
        }
        if (count == 0) { notice("UNSUPPORTED_TYPE_OMITTED"); return OMIT; }
        return Collections.unmodifiableMap(out);
    }

    private Object file(String name, String contentType, Long size, int depth) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (name != null) metadata.put("fileName", name.replace('\\', '/').replaceFirst("^.*/", ""));
        if (contentType != null) metadata.put("contentType", contentType);
        if (size != null) metadata.put("size", Math.max(0, size));
        notice("FILE_METADATA_ONLY");
        return clean(metadata, depth + 1);
    }
}
