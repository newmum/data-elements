package com.linewell.dataelement.dataservice.flowserve;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.model.Group;
import org.ssssssss.magicapi.core.service.MagicAPIService;
import org.ssssssss.magicapi.core.service.MagicResourceService;
import org.ssssssss.magicapi.core.service.impl.RequestMagicDynamicRegistry;
import org.ssssssss.script.annotation.Comment;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;

/**
 * 云梯流程发布与调试的框架适配器。
 *
 * <p>流程、版本和发布记录由 06.数据服务下的 Magic 接口维护；这里只处理 Magic
 * 动态路由注册以及远程运行节点通信。</p>
 */
@Component
@MagicModule("flowRuntime")
public class FlowServeRuntimeAdapter {
    public static final String ROOT_GROUP_ID = "5d290a9b3bca3b2dd9ffd715dc47ce56";
    public static final String PUBLISHED_GROUP_ID = "e5ce028f1797312d57c628b66ffcf657";

    private final ObjectMapper mapper;
    private final MagicResourceService resourceService;
    private final MagicAPIService magicApiService;
    private final RequestMagicDynamicRegistry registry;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String publicBaseUrl;
    private final TenantProperties tenantProperties;

    public FlowServeRuntimeAdapter(
            ObjectMapper mapper,
            MagicResourceService resourceService,
            MagicAPIService magicApiService,
            RequestMagicDynamicRegistry registry,
            TenantProperties tenantProperties,
            @Value("${flowserve.runtime.public-base-url:http://127.0.0.1:8088}") String publicBaseUrl) {
        this.mapper = mapper;
        this.resourceService = resourceService;
        this.magicApiService = magicApiService;
        this.registry = registry;
        this.tenantProperties = tenantProperties;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Comment("调试执行前端已编译的 Magic 脚本")
    public Map<String, Object> debug(Object rawFlow, String script) {
        Map<String, Object> flow = asMap(rawFlow);
        Map<String, Object> trigger = trigger(flow);
        String method = text(trigger, "method", "GET").toUpperCase();
        String path = "/__debug/" + text(flow, "flowId", "flow") + "/"
                + UUID.randomUUID().toString().replace("-", "");
        ApiInfo api = api(
                "debug_" + UUID.randomUUID().toString().replace("-", ""),
                text(flow, "name", "流程调试"),
                method,
                path,
                text(flow, "description", ""),
                script);
        api.setGroupId(PUBLISHED_GROUP_ID);
        registry.register(api);
        long started = System.currentTimeMillis();
        try {
            Map<String, Object> parameters = exampleParameters(trigger);
            String fullPath = "/dws/flowserve/published" + path;
            Object response = magicApiService.execute(method, fullPath, parameters);
            long elapsed = System.currentTimeMillis() - started;
            List<Map<String, Object>> trace = new ArrayList<>();
            trace.add(traceEntry("trigger", "API 触发器", "success", 0, parameters));
            trace.add(traceEntry("runtime", "Magic 脚本执行", "success", elapsed, response));
            return Map.of(
                    "ok", true,
                    "durationMs", elapsed,
                    "trace", trace,
                    "response", response == null ? Map.of() : response);
        } catch (Exception exception) {
            long elapsed = System.currentTimeMillis() - started;
            return Map.of(
                    "ok", false,
                    "durationMs", elapsed,
                    "trace", List.of(traceEntry(
                            "runtime", "Magic 脚本执行", "error", elapsed, rootMessage(exception))),
                    "error", rootMessage(exception));
        } finally {
            registry.unregister(api);
        }
    }

    @Comment("发布前端已编译脚本到本机或远程 Magic 运行节点")
    public Map<String, Object> deploy(
            String flowId, Object rawFlow, String script, Object rawRuntimeNode) {
        Map<String, Object> flow = asMap(rawFlow);
        Map<String, Object> node = asMap(rawRuntimeNode);
        Map<String, Object> trigger = trigger(flow);
        String tenantId = tenantId();
        String resourceId = stableResourceId(tenantId, flowId);
        String method = text(trigger, "method", "GET").toUpperCase();
        String path = publishedPath(tenantId, normalizePath(text(trigger, "path", "/" + flowId)));
        boolean local = node.isEmpty() || booleanValue(node.get("localNode"), true);
        if (local) {
            ApiInfo api = api(
                    resourceId,
                    safeName(text(flow, "name", flowId), flowId),
                    method,
                    path,
                    text(flow, "description", ""),
                    script);
            api.setGroupId(PUBLISHED_GROUP_ID);
            ensurePublishedGroup();
            resourceService.saveFile(api);
            resourceService.refresh();
            registry.register(api);
        } else {
            deployRemote(resourceId, flow, script, node, method, path);
        }
        String baseUrl = local ? publicBaseUrl : text(node, "baseUrl", publicBaseUrl).replaceAll("/+$", "");
        return Map.of(
                "resourceId", resourceId,
                "method", method,
                "path", path,
                "url", baseUrl + "/dws/flowserve/published" + path,
                "runtimeNodeId", text(node, "id", "local_magic_api"));
    }

    @Comment("下线本机或远程 Magic 服务")
    public boolean undeploy(String resourceId, Object rawRuntimeNode) {
        Map<String, Object> node = asMap(rawRuntimeNode);
        boolean local = node.isEmpty() || booleanValue(node.get("localNode"), true);
        if (local) {
            ApiInfo api = new ApiInfo();
            api.setId(resourceId);
            registry.unregister(api);
            try {
                resourceService.delete(resourceId);
                resourceService.refresh();
            } catch (Exception ignored) {
                // 动态路由注销已生效，历史资源清理失败不应使下线操作失效。
            }
            return true;
        }
        String managementUrl = text(node, "managementUrl", text(node, "baseUrl", ""));
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                            URI.create(managementUrl.replaceAll("/+$", "")
                                    + "/api/v1/runtime/deploy/" + resourceId))
                    .timeout(Duration.ofSeconds(10))
                    .DELETE();
            addToken(builder, node);
            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception exception) {
            throw new IllegalStateException("远程节点下线失败：" + rootMessage(exception), exception);
        }
    }

    @Comment("检查本机或远程运行节点健康状态")
    public Map<String, Object> health(Object rawRuntimeNode) {
        Map<String, Object> node = asMap(rawRuntimeNode);
        long started = System.currentTimeMillis();
        if (node.isEmpty() || booleanValue(node.get("localNode"), true)) {
            return Map.of(
                    "nodeId", text(node, "id", "local_magic_api"),
                    "ok", true,
                    "status", 200,
                    "latencyMs", System.currentTimeMillis() - started,
                    "message", "本机 data-elements 运行节点可用");
        }
        String managementUrl = text(node, "managementUrl", text(node, "baseUrl", ""));
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                            URI.create(managementUrl.replaceAll("/+$", "") + "/api/v1/runtime/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET();
            addToken(builder, node);
            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return Map.of(
                    "nodeId", text(node, "id", ""),
                    "ok", response.statusCode() >= 200 && response.statusCode() < 300,
                    "status", response.statusCode(),
                    "latencyMs", System.currentTimeMillis() - started,
                    "message", response.body());
        } catch (Exception exception) {
            return Map.of(
                    "nodeId", text(node, "id", ""),
                    "ok", false,
                    "status", 0,
                    "latencyMs", System.currentTimeMillis() - started,
                    "message", rootMessage(exception));
        }
    }

    public void deployFromRequest(Map<String, Object> request) {
        String resourceId = text(request, "resourceId", "");
        ApiInfo api = api(
                resourceId,
                safeName(text(request, "name", resourceId), resourceId),
                text(request, "method", "GET"),
                normalizePath(text(request, "path", "/" + resourceId)),
                text(request, "description", ""),
                text(request, "script", ""));
        api.setGroupId(PUBLISHED_GROUP_ID);
        ensurePublishedGroup();
        resourceService.saveFile(api);
        resourceService.refresh();
        registry.register(api);
    }

    public void undeployFromRequest(String resourceId) {
        undeploy(resourceId, Map.of("localNode", true));
    }

    private void deployRemote(
            String resourceId,
            Map<String, Object> flow,
            String script,
            Map<String, Object> node,
            String method,
            String path) {
        String managementUrl = text(node, "managementUrl", text(node, "baseUrl", ""));
        try {
            Map<String, Object> payload = Map.of(
                    "resourceId", resourceId,
                    "name", safeName(text(flow, "name", resourceId), resourceId),
                    "method", method,
                    "path", path,
                    "description", text(flow, "description", ""),
                    "script", script);
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                            URI.create(managementUrl.replaceAll("/+$", "") + "/api/v1/runtime/deploy"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)));
            addToken(builder, node);
            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "HTTP " + response.statusCode() + "：" + response.body());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("远程节点发布失败：" + rootMessage(exception), exception);
        }
    }

    private void ensurePublishedGroup() {
        Group group = resourceService.getGroup(PUBLISHED_GROUP_ID);
        if (group == null) {
            group = new Group();
            group.setId(PUBLISHED_GROUP_ID);
            group.setCreateTime(System.currentTimeMillis());
            group.setCreateBy("flowserve");
        }
        group.setName("07.已发布服务");
        group.setType("api");
        group.setParentId(ROOT_GROUP_ID);
        group.setPath("/published");
        group.setUpdateTime(System.currentTimeMillis());
        group.setUpdateBy("flowserve");
        resourceService.saveGroup(group);
    }

    private ApiInfo api(
            String id, String name, String method, String path, String description, String script) {
        ApiInfo api = new ApiInfo();
        api.setId(id);
        api.setName(name);
        api.setMethod(method.toUpperCase());
        api.setPath(path);
        api.setDescription(description);
        api.setScript(script);
        api.setCreateBy("flowserve");
        api.setUpdateBy("flowserve");
        api.setCreateTime(System.currentTimeMillis());
        api.setUpdateTime(System.currentTimeMillis());
        return api;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> trigger(Map<String, Object> flow) {
        Object nodes = flow.get("nodes");
        if (nodes instanceof List<?> list) {
            for (Object nodeValue : list) {
                Map<String, Object> node = asMap(nodeValue);
                if ("trigger".equals(String.valueOf(node.get("type")))) {
                    return asMap(node.get("data"));
                }
            }
        }
        return Map.of();
    }

    private Map<String, Object> exampleParameters(Map<String, Object> trigger) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> request = asMap(trigger.get("request"));
        for (String section : List.of("query", "path", "header")) {
            Object values = request.get(section);
            if (values instanceof List<?> list) {
                for (Object value : list) {
                    Map<String, Object> parameter = asMap(value);
                    String name = text(parameter, "name", "");
                    if (!name.isBlank()) {
                        result.put(name, parameter.getOrDefault("example", parameter.getOrDefault("defaultValue", "")));
                    }
                }
            }
        }
        Object body = request.get("body");
        if (body != null) {
            result.put("body", body);
        }
        return result;
    }

    private Map<String, Object> traceEntry(
            String nodeId, String name, String status, long duration, Object output) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("nodeId", nodeId);
        trace.put("nodeName", name);
        trace.put("status", status);
        trace.put("durationMs", duration);
        trace.put("startedAt", Instant.now().toString());
        trace.put("output", output);
        return trace;
    }

    private void addToken(HttpRequest.Builder builder, Map<String, Object> node) {
        String token = text(node, "deployToken", "");
        if (!token.isBlank() && !"******".equals(token)) {
            builder.header("X-FlowServe-Deploy-Token", token);
        }
    }

    private boolean booleanValue(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private String normalizePath(String value) {
        String path = value == null || value.isBlank() ? "/" : value.trim();
        return path.startsWith("/") ? path : "/" + path;
    }

    /**
     * Magic 的数据库资源主键使用稳定的 32 位十六进制 ID，确保重启后仍能恢复动态路由。
     */
    private String stableResourceId(String tenantId, String flowId) {
        String resourceKey = tenantProperties.getDefaultTenantId().equals(tenantId)
                ? "data-elements:flowserve:" + flowId
                : "data-elements:flowserve:" + tenantId + ":" + flowId;
        return UUID.nameUUIDFromBytes(resourceKey
                        .getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
    }

    private String publishedPath(String tenantId, String path) {
        if (tenantProperties.getDefaultTenantId().equals(tenantId)) {
            return path;
        }
        return "/__tenant/" + tenantId + path;
    }

    private String tenantId() {
        String tenantId = TenantContext.getTenantId();
        return tenantId == null || tenantId.isBlank()
                ? tenantProperties.getDefaultTenantId()
                : tenantId;
    }

    private String safeName(String value, String fallback) {
        String result = value == null ? "" : value.trim().replaceAll("\\s+", "_");
        result = result.replaceAll("[^\\u4e00-\\u9fa5A-Za-z0-9+_\\-.()]", "_");
        while (result.startsWith(".")) {
            result = result.substring(1);
        }
        return result.isBlank() ? fallback : result;
    }

    private String text(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return mapper.convertValue(value, new TypeReference<>() {});
    }
}
