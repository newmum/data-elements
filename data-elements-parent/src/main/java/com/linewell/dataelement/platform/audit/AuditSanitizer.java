package com.linewell.dataelement.platform.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.persistence.jdbc.JdbcValueNormalizer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Allowlisted event details; request bodies and exception messages are never copied wholesale. */
@Component
public class AuditSanitizer {
    private static final Pattern SECRET = Pattern.compile("(?i).*(password|passwd|secret|token|cookie|authorization|credential|private.?key|keytab|signature|salt|hash|验证码|密码|口令).*");
    private static final Set<String> FIELDS = Set.of("name", "title", "status", "enabled", "type", "count", "total", "successCount", "failureCount", "skippedCount", "roleIds", "orgIds", "appIds", "targetIds", "changes", "field", "label", "before", "after", "reasonCode", "taskId", "batchId", "source", "phase", "size", "format", "category", "result", "action", "errorCode", "objectType", "objectId", "id", "version", "mfaRequired", "scope", "hotDays", "retentionDays");
    private final ObjectMapper mapper;
    public AuditSanitizer(ObjectMapper mapper) { this.mapper = mapper; }
    public static String text(Object value, int max) {
        if (value == null) return null;
        String text = String.valueOf(value).replaceAll("[\\r\\n\\p{Cntrl}]", " ");
        return text.codePointCount(0,text.length()) <= max ? text : text.substring(0,text.offsetByCodePoints(0,max));
    }
    public Object clean(Object value) { return clean(value, 0); }
    private Object clean(Object value, int depth) {
        if (depth > 5 || value == null) return null;
        if (value instanceof Map<?,?> source) {
            Map<String,Object> out = new LinkedHashMap<>();
            source.entrySet().stream().limit(64).forEach(e -> {
                String key = String.valueOf(e.getKey());
                if (FIELDS.contains(key) && !SECRET.matcher(key).matches()) {
                    if (key.equals("changes") && e.getValue() instanceof Collection<?> changes) {
                        out.put(key, changes.stream().limit(50).filter(item -> !(item instanceof Map<?,?> m) || !SECRET.matcher(String.valueOf(m.get("field"))).matches()).map(item -> clean(item,depth+1)).toList());
                    } else out.put(key,clean(e.getValue(),depth+1));
                }
            });
            return out;
        }
        if (value instanceof Collection<?> values) return values.stream().limit(100).map(v->clean(v,depth+1)).toList();
        if (value instanceof Number || value instanceof Boolean) return value;
        return text(value,512);
    }
    /** 规范化存量事件时间与详情，兼容历史快照并过滤未脱敏内容。 */
    public Map<String,Object> formatStoredEvent(Map<String,Object> input,boolean detail) {
        Map<String,Object> row=AuditStore.lower(JdbcValueNormalizer.map(input));
        row.remove("parameter_search_text");
        if (detail && (row.containsKey("parameter_snapshot") || row.containsKey("parameter_capture_status"))) {
            var snapshot = AuditParameterSnapshot.parse(row.get("parameter_snapshot"));
            row.put("parameter_snapshot", snapshot);
            row.put("parameter_capture_status", snapshot.get("status"));
        } else if (row.containsKey("parameter_capture_status")) {
            Object status = row.get("parameter_capture_status");
            row.put("parameter_capture_status", status != null
                    && Set.of("EMPTY","CAPTURED","PARTIAL","OMITTED","FAILED","LEGACY").contains(status)
                    ? status : "LEGACY");
        }
        row.put("legacy",row.get("event_time")==null);
        Object time=row.get("event_time");if(time!=null)row.put("event_time",LogTimes.instant(time).toString());
        if(row.get("created_time")!=null) {
            Object created=row.get("created_time");
            if(time==null && created instanceof java.sql.Timestamp stamp)row.put("created_time",stamp.toLocalDateTime().atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toString());
            else if(time==null && created instanceof java.time.LocalDateTime date)row.put("created_time",date.atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toString());
            else row.put("created_time",LogTimes.instant(created).toString());
        }
        if(detail && row.get("details")!=null) {
            try {
                Object details = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(String.valueOf(row.get("details")), Object.class);
                if (details instanceof Map<?, ?> values) {
                    var parameters = AuditRequestParameters.display(values.get(AuditRequestParameters.DETAILS_KEY));
                    if (!parameters.isEmpty()) row.put("request_parameters", parameters);
                    row.put("details", clean(details));
                } else {
                    // A historical JSON string/array may be raw request content, not an allowlisted event summary.
                    row.put("details", Map.of("phase","LEGACY_DETAIL_NOT_EXPOSED"));
                }
            }
            catch(Exception ignored) { row.put("details",Map.of("phase","LEGACY_DETAIL_NOT_EXPOSED")); }
        }
        return row;
    }
    public String json(Object details) {
        return json(details, Map.of());
    }
    String json(Object details, Map<String, Object> requestParameters) {
        try {
            Object cleaned = clean(details);
            if (requestParameters.isEmpty()) {
                String json = compactJson(cleaned);
                return fits(json) ? json : "{\"phase\":\"DETAIL_LIMIT_EXCEEDED\"}";
            }
            Map<String, Object> event = new LinkedHashMap<>();
            if (cleaned instanceof Map<?, ?> map) map.forEach((key, value) -> event.put((String) key, value));
            else if (cleaned != null) event.put("details", cleaned);
            var index = AuditRequestParameters.index(requestParameters);
            event.put(AuditRequestParameters.DETAILS_KEY, requestParameters);
            event.put(AuditRequestParameters.INDEX_KEY, index);
            String json = compactJson(event);
            if (fits(json)) return json;
            return compactJson(Map.of("phase", "DETAIL_LIMIT_EXCEEDED",
                    AuditRequestParameters.DETAILS_KEY, requestParameters, AuditRequestParameters.INDEX_KEY, index));
        } catch (Exception e) { throw new IllegalArgumentException("日志摘要无法序列化", e); }
    }
    private String compactJson(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
        return mapper.writer().without(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT).writeValueAsString(value);
    }
    private boolean fits(String json) { return json.getBytes(StandardCharsets.UTF_8).length <= 32768; }
}
