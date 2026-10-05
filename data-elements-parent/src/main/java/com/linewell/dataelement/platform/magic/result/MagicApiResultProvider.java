package com.linewell.dataelement.platform.magic.result;

import com.linewell.dataelement.shared.error.ApiErrorDetails;
import com.linewell.dataelement.shared.error.ApiErrorResolver;
import lombok.extern.slf4j.Slf4j;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.ResultProvider;

import java.lang.reflect.Array;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unified Magic API response provider.
 *
 * <p>Success responses keep the existing code/msg/success/data contract. Error responses additionally expose
 * a stable error code, a user-friendly reason, a trace id and the complete technical detail.</p>
 */
@Slf4j
public class MagicApiResultProvider implements ResultProvider {

    private static final int SUCCESS_CODE = 0;
    private static final int ERROR_CODE = 500;

    @Override
    public Object buildResult(RequestEntity requestEntity, int code, String message, Object data) {
        boolean success = code == 0 || code == 1 || code == 200;
        String displayMessage = success
                ? defaultText(message, "success")
                : friendlyBusinessMessage(message);
        // MagicScript 的 JSON.parse 会产生 Hutool JSONObject/JSONNull。先递归转换成标准
        // Java Map/List/null，避免 Jackson 在输出流程 DSL 等含空值的数据时序列化失败。
        Object serializableData = normalizeJsonValue(data);
        if (success && isPublishedFlowService(requestEntity)
                && serializableData instanceof Map<?, ?> publishedResult
                && publishedResult.containsKey("code")
                && publishedResult.containsKey("data")) {
            // 编排的“响应”节点在 wrap=true 时已经生成标准业务信封。已发布服务直接
            // 使用这层结果，避免 data 中再嵌一层 code/message/data。
            return serializableData;
        }
        Map<String, Object> result = baseResult(
                success ? SUCCESS_CODE : code,
                displayMessage,
                success,
                serializableData
        );
        if (!success) {
            ApiErrorDetails error = ApiErrorResolver.business(
                    "MAGIC-BUSINESS-" + Math.abs(code),
                    displayMessage,
                    defaultText(message, "Magic API business error")
            );
            result.put("error", error.toMap(
                    requestMethod(requestEntity),
                    requestPath(requestEntity)
            ));
        }
        return result;
    }

    private boolean isPublishedFlowService(RequestEntity requestEntity) {
        return requestPath(requestEntity).startsWith("/dws/flowserve/published/");
    }

    @Override
    public Object buildException(RequestEntity requestEntity, Throwable throwable) {
        Throwable actual = throwable == null ? new IllegalStateException("Unknown Magic API error") : throwable;
        ApiErrorDetails error = ApiErrorResolver.resolve(actual);

        log.error(
                "Magic API request failed, traceId={}, errorCode={}, path={}",
                error.traceId(),
                error.code(),
                requestPath(requestEntity),
                actual
        );

        Map<String, Object> result = baseResult(ERROR_CODE, error.message(), false, null);
        result.put("error", error.toMap(
                requestMethod(requestEntity),
                requestPath(requestEntity)
        ));
        return result;
    }

    private Map<String, Object> baseResult(int code, String message, boolean success, Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("msg", message);
        result.put("message", message);
        result.put("success", success);
        result.put("data", data);
        result.put("timestamp", Instant.now().toEpochMilli());
        return result;
    }

    /**
     * Converts Magic/Hutool JSON containers into values Jackson can serialize reliably.
     */
    private Object normalizeJsonValue(Object value) {
        Set<Object> visiting = Collections.newSetFromMap(new IdentityHashMap<>());
        return normalizeJsonValue(value, visiting);
    }

    private Object normalizeJsonValue(Object value, Set<Object> visiting) {
        if (value == null || "cn.hutool.json.JSONNull".equals(value.getClass().getName())) {
            return null;
        }
        if (value instanceof Map<?, ?> source) {
            if (!visiting.add(value)) {
                return null;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, item) ->
                    result.put(String.valueOf(key), normalizeJsonValue(item, visiting)));
            visiting.remove(value);
            return result;
        }
        if (value instanceof Iterable<?> source) {
            if (!visiting.add(value)) {
                return null;
            }
            List<Object> result = new ArrayList<>();
            source.forEach(item -> result.add(normalizeJsonValue(item, visiting)));
            visiting.remove(value);
            return result;
        }
        if (value.getClass().isArray()) {
            if (!visiting.add(value)) {
                return null;
            }
            int length = Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                result.add(normalizeJsonValue(Array.get(value, index), visiting));
            }
            visiting.remove(value);
            return result;
        }
        return value;
    }

    private String friendlyBusinessMessage(String message) {
        String text = defaultText(message, "请求处理失败");
        if (text.contains("org.ssssssss.") || text.contains(" at Row:") || text.length() > 180) {
            return "接口处理失败，请点击“查看详情”查看完整原因。";
        }
        return text;
    }

    private String requestPath(RequestEntity requestEntity) {
        try {
            return requestEntity == null || requestEntity.getRequest() == null
                    ? ""
                    : defaultText(requestEntity.getRequest().getRequestURI(), "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String requestMethod(RequestEntity requestEntity) {
        try {
            return requestEntity == null || requestEntity.getRequest() == null
                    ? ""
                    : defaultText(requestEntity.getRequest().getMethod(), "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
