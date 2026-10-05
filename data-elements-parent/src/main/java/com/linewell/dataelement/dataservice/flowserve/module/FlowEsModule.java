package com.linewell.dataelement.dataservice.flowserve.module;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataservice.flowserve.FlowServeDataSourceRuntime;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** 云梯编排 Elasticsearch 节点运行模块，避免覆盖平台 Magic ES 插件。 */
@Component
@MagicModule("flowEs")
public class FlowEsModule {
    private final FlowServeDataSourceRuntime dataSources;
    private final ObjectMapper mapper;
    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public FlowEsModule(FlowServeDataSourceRuntime dataSources, ObjectMapper mapper) {
        this.dataSources = dataSources;
        this.mapper = mapper;
    }

    @Comment("执行 Elasticsearch DSL 查询")
    public Map<String, Object> search(String datasource, String index, Object dsl) {
        if (index == null || index.isBlank()) {
            throw new IllegalArgumentException("ES index 不能为空");
        }
        Map<String, Object> config = dataSources.configByName(datasource);
        String baseUrl = String.valueOf(
                config.getOrDefault("url", config.getOrDefault("baseUrl", config.getOrDefault("nodes", ""))));
        baseUrl = baseUrl.split(",")[0].trim().replaceAll("/+$", "");
        if (baseUrl.isBlank()) {
            throw new IllegalArgumentException("ES 数据源缺少 url/baseUrl/nodes");
        }
        try {
            String body = mapper.writeValueAsString(
                    dsl == null ? Map.of("query", Map.of("match_all", Map.of())) : dsl);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + index + "/_search"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            applyAuth(builder, config);
            HttpResponse<String> response =
                    client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP " + response.statusCode() + "：" + response.body());
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode hitsNode = root.path("hits");
            List<Object> hits = new ArrayList<>();
            for (JsonNode hit : hitsNode.path("hits")) {
                hits.add(mapper.convertValue(hit.path("_source"), Object.class));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(
                    "total",
                    hitsNode.path("total").isObject()
                            ? hitsNode.path("total").path("value").asLong()
                            : hitsNode.path("total").asLong(hits.size()));
            result.put("hits", hits);
            result.put("aggregations", mapper.convertValue(root.path("aggregations"), Object.class));
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("ES 查询失败：" + exception.getMessage(), exception);
        }
    }

    private void applyAuth(HttpRequest.Builder builder, Map<String, Object> config) {
        Object apiKey = config.get("apiKey");
        if (apiKey != null && !String.valueOf(apiKey).isBlank()) {
            builder.header("Authorization", "ApiKey " + apiKey);
            return;
        }
        Object username = config.get("username");
        Object password = config.get("password");
        if (username != null && password != null && !String.valueOf(username).isBlank()) {
            String raw = username + ":" + password;
            builder.header(
                    "Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
