package com.linewell.dataelement.platform.configuration;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssssssss.magicapi.core.resource.Resource;
import org.ssssssss.magicapi.core.service.MagicResourceService;

/**
 * Administrative operations for the node-configuration low-code page.
 *
 * <p>Service-node health checks are intentionally performed here instead of
 * Magic scripts so configuration users cannot execute arbitrary script modules.
 * Magic resource refresh is explicit because API resources have their own
 * in-memory cache, independent from the low-code component cache.</p>
 */
@RestController
@RequestMapping("/sym/node-config")
public class NodeConfigurationController {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final MagicResourceService magicResourceService;
    private final Resource magicDatabaseResource;
    private final NifiClient nifiClient;

    public NodeConfigurationController(MagicResourceService magicResourceService,
            @Qualifier("magicDatabaseResource") Resource magicDatabaseResource,
            NifiClient nifiClient) {
        this.magicResourceService = magicResourceService;
        this.magicDatabaseResource = magicDatabaseResource;
        this.nifiClient = nifiClient;
    }

    /** Refresh the in-memory Magic resource tree after an api_file_t publish. */
    @PostMapping("/magic-resource/refresh")
    public CommonResponse<Map<String, Object>> refreshMagicResources() {
        long started = System.currentTimeMillis();
        // The database-backed Magic resource keeps a content snapshot. Refreshing
        // only MagicResourceService rebuilds the endpoint tree from that old
        // snapshot, so a directly published api_file_t script would still run
        // its previous body. Reload the resource content first, then rebuild.
        if (magicDatabaseResource != null) {
            magicDatabaseResource.readAll();
        }
        magicResourceService.refresh();
        return CommonResponse.success(Map.of(
                "success", true,
                "elapsedMs", System.currentTimeMillis() - started));
    }

    /**
     * Executes a bounded, unauthenticated GET against a configured service node.
     * No credential value is accepted or logged. Local nodes are served by this
     * backend and are therefore reported healthy without a loopback request.
     */
    @PostMapping("/service/health")
    public CommonResponse<Map<String, Object>> checkServiceNodeHealth(
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        if (booleanValue(request.get("localNode"))) {
            return CommonResponse.success(healthResult(true, 200, 0,
                    "本机 data-elements 数据服务节点可用", "local"));
        }
        if ("sync".equalsIgnoreCase(text(request.get("nodeType")))) {
            return checkSyncNode(text(request.get("nodeId")), text(request.get("baseUrl")));
        }
        String managementUrl = text(request.get("managementUrl"));
        String baseUrl = text(request.get("baseUrl"));
        String target = managementUrl.isBlank() ? baseUrl : managementUrl;
        if (target.isBlank()) {
            return CommonResponse.success(healthResult(false, 0, 0,
                    "请填写服务访问地址或管理部署地址", ""));
        }
        URI uri;
        try {
            uri = URI.create(normalizeHealthUrl(target));
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("协议必须为 HTTP 或 HTTPS");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("地址缺少主机名");
            }
        } catch (Exception exception) {
            return CommonResponse.success(healthResult(false, 0, 0,
                    "服务地址无效：" + rootMessage(exception), target));
        }

        long started = System.currentTimeMillis();
        try {
            HttpRequest requestEntity = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build()
                    .send(requestEntity, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            boolean success = status >= 200 && status < 300;
            return CommonResponse.success(healthResult(success, status, System.currentTimeMillis() - started,
                    success ? "节点连通正常" : "节点响应 HTTP " + status, uri.toString()));
        } catch (Exception exception) {
            return CommonResponse.success(healthResult(false, 0, System.currentTimeMillis() - started,
                    rootMessage(exception), uri.toString()));
        }
    }

    /** Return only bounded JVM/system metrics for a node owned by the current tenant. */
    @PostMapping("/service/diagnostics")
    public CommonResponse<Map<String, Object>> systemDiagnostics(
            @RequestBody(required = false) Map<String, Object> body) {
        if (TenantContext.getTenantId() == null || TenantContext.getTenantId().isBlank()) {
            throw new IllegalStateException("登录租户上下文已失效，请重新登录");
        }
        String nodeId = text(body == null ? null : body.get("nodeId"));
        if (nodeId.isBlank()) {
            throw new IllegalArgumentException("请选择需要查询的 NiFi 节点");
        }
        try {
            JsonNode entity = nifiClient.systemDiagnostics(nodeId);
            JsonNode snapshot = entity.path("systemDiagnostics").path("aggregateSnapshot");
            if (snapshot.isMissingNode() || snapshot.isNull()) {
                return CommonResponse.success(Map.of("success", false, "message", "NiFi 未返回系统诊断快照"));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("heapUtilization", snapshot.path("heapUtilization").asText(""));
            result.put("usedHeapBytes", numberOrNull(snapshot.path("usedHeapBytes")));
            result.put("maxHeapBytes", numberOrNull(snapshot.path("maxHeapBytes")));
            result.put("processorLoadAverage", numberOrNull(snapshot.path("processorLoadAverage")));
            result.put("availableProcessors", numberOrNull(snapshot.path("availableProcessors")));
            result.put("totalThreads", numberOrNull(snapshot.path("totalThreads")));
            result.put("statsLastRefreshed", snapshot.path("statsLastRefreshed").asText(""));
            return CommonResponse.success(result);
        } catch (Exception exception) {
            return CommonResponse.success(Map.of("success", false, "message", rootMessage(exception)));
        }
    }

    private Number numberOrNull(JsonNode value) {
        return value.isNumber() ? value.numberValue() : null;
    }

    private CommonResponse<Map<String, Object>> checkSyncNode(String nodeId, String baseUrl) {
        long started = System.currentTimeMillis();
        try {
            nifiClient.testConnection(nodeId);
            return CommonResponse.success(healthResult(true, 200, System.currentTimeMillis() - started,
                    "NiFi 节点认证与根流程组访问正常", normalizeNifiApiUrl(baseUrl)));
        } catch (Exception exception) {
            return CommonResponse.success(healthResult(false, 0, System.currentTimeMillis() - started,
                    rootMessage(exception), normalizeNifiApiUrl(baseUrl)));
        }
    }

    private Map<String, Object> healthResult(
            boolean success, int status, long latencyMs, String message, String url) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("status", status);
        result.put("latencyMs", latencyMs);
        result.put("message", message);
        result.put("url", url);
        return result;
    }

    private String normalizeHealthUrl(String value) {
        String normalized = value.trim().replaceAll("/+$", "");
        URI uri = URI.create(normalized);
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (path.isEmpty() || "/".equals(path)) {
            return normalized + "/api/v1/runtime/health";
        }
        return normalized;
    }

    private String normalizeNifiApiUrl(String value) {
        return value == null || value.isBlank() ? "" : value.trim().replaceAll("/+$", "") + "/nifi-api/flow/process-groups/root";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean booleanValue(Object value) {
        return value != null && (Boolean.TRUE.equals(value) || "1".equals(String.valueOf(value))
                || Boolean.parseBoolean(String.valueOf(value)));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
