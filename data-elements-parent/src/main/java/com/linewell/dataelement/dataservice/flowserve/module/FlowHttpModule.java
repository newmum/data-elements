package com.linewell.dataelement.dataservice.flowserve.module;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataservice.flowserve.FlowServeDataSourceRuntime;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** 云梯编排 HTTP 节点运行模块。 */
@Component
@MagicModule("httpx")
public class FlowHttpModule {
    private final FlowServeDataSourceRuntime dataSources;
    private final ObjectMapper mapper;
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public FlowHttpModule(FlowServeDataSourceRuntime dataSources, ObjectMapper mapper) {
        this.dataSources = dataSources;
        this.mapper = mapper;
    }

    @Comment("执行云梯编排 HTTP 调用")
    public Map<String, Object> request(Object rawOptions) {
        Map<String, Object> options = asMap(rawOptions);
        String method = String.valueOf(options.getOrDefault("method", "GET")).toUpperCase();
        String url = endpoint(options);
        int timeout = number(options.get("timeoutMs"), 10000);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Accept", "application/json, text/plain, */*");
            asMap(options.get("headers")).forEach(
                    (key, value) -> builder.header(key, String.valueOf(value)));
            Object body = options.get("body");
            if (body != null && !"GET".equals(method)) {
                builder.header("Content-Type", "application/json");
                builder.method(
                        method,
                        HttpRequest.BodyPublishers.ofString(write(body), StandardCharsets.UTF_8));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response =
                    client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "HTTP " + response.statusCode() + "：" + response.body());
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", response.statusCode());
            result.put("headers", response.headers().map());
            result.put("body", parse(response.body()));
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("HTTP 调用失败：" + exception.getMessage(), exception);
        }
    }

    private String endpoint(Map<String, Object> options) {
        String url = String.valueOf(options.getOrDefault("url", ""));
        Object datasource = options.get("datasource");
        if ((url.isBlank() || "null".equals(url)) && datasource != null) {
            Map<String, Object> config = dataSources.configByName(String.valueOf(datasource));
            String base = String.valueOf(
                    config.getOrDefault("baseUrl", config.getOrDefault("url", "")))
                    .replaceAll("/+$", "");
            String path = String.valueOf(options.getOrDefault("path", "")).replaceAll("^/+", "");
            url = path.isBlank() ? base : base + "/" + path;
        }
        if (url.isBlank() || "null".equals(url)) {
            throw new IllegalArgumentException("HTTP 节点缺少 url 或 datasource");
        }
        return url;
    }

    private Object parse(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return mapper.readValue(text, Object.class);
        } catch (Exception ignored) {
            return text;
        }
    }

    private String write(Object value) {
        try {
            return value instanceof String text ? text : mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("HTTP body 不是合法 JSON", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return mapper.convertValue(value, new TypeReference<>() {});
    }

    private int number(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
