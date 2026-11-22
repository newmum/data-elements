package com.linewell.dataelement.integration.nifi.canvas.nifi;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Short lived, session-bound, read-only native view. NiFi credentials and tokens remain server side. */
@Component
public class NifiNativeView {
    public static final String COOKIE = "haitong_nifi_view";
    private final Map<String, View> views = new ConcurrentHashMap<>();
    private final NifiClient nifi;
    private final ObjectMapper mapper;
    private static final Set<String> METADATA = Set.of("/flow/about", "/flow/config", "/flow/current-user",
            "/flow/client-id", "/flow/processor-types", "/flow/controller-service-types", "/flow/cluster/summary",
            "/authentication/configuration", "/access/status", "/controller/config", "/flow/reporting-task-types",
            "/flow/prioritizers", "/flow/flow-analysis-rule-types", "/flow/parameter-provider-types", "/flow/connector-types", "/flow/banners");

    private record View(String tenant, String token, Object login, String node, String group, Instant expires,
                        Set<String> objects, Set<String> groups) {}
    public NifiNativeView(NifiClient nifi, ObjectMapper mapper) { this.nifi = nifi; this.mapper = mapper; }

    public String open(String nodeId, String groupId) {
        StpUtil.checkLogin();
        String tenant = TenantContext.requireTenantId();
        if (groupId == null || !groupId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("流程组标识无效");
        views.entrySet().removeIf(entry -> entry.getValue().expires().isBefore(Instant.now()));
        if (views.size() >= 1000) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "原生详情会话较多，请稍后重试");
        String key = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        Set<String> objects = ConcurrentHashMap.newKeySet(); objects.add(groupId);
        Set<String> groups = ConcurrentHashMap.newKeySet(); groups.add(groupId);
        views.put(key, new View(tenant, StpUtil.getTokenValue(), StpUtil.getLoginId(), nodeId, groupId,
                Instant.now().plusSeconds(600), objects, groups));
        // This is an opaque platform view cookie, never a NiFi authentication token.
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        var request = attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet ? servlet.getRequest() : null;
        boolean secure = request != null && (request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")));
        return COOKIE + "=" + key + "; Path=/; Max-Age=600; HttpOnly; SameSite=Strict" + (secure ? "; Secure" : "");
    }

    public ResponseEntity<byte[]> proxy(HttpServletRequest request, String upstreamPath, boolean ui) {
        String key = null;
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies())
            if (COOKIE.equals(cookie.getName())) key = cookie.getValue();
        View view = key == null ? null : views.get(key);
        if (view == null || view.expires().isBefore(Instant.now())
                || !Objects.equals(String.valueOf(view.login()), String.valueOf(StpUtil.getLoginIdByToken(view.token()))))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请从海通画布重新打开原生详情");
        var session = StpUtil.getTokenSessionByToken(view.token());
        if (session == null || !view.tenant().equals(session.get("tenantId")))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "行业会话已切换，请重新打开原生详情");
        if (!Set.of("GET", "HEAD").contains(request.getMethod()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "原生详情为只读查看，请在海通画布维护流程");
        String path = upstreamPath;
        if (path.contains("..") || path.contains("%") || path.contains("\\"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (!ui) path = authorize(view, upstreamPath);
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) path += "?" + request.getQueryString();
        try (var tenant = TenantContext.use(view.tenant());
             var runtime = view.node() == null || view.node().isBlank() ? nifi.bindRuntimeNode() : nifi.bindAssignedNode(view.node())) {
            // The native header normally reads controller-wide counts and bulletins.
            // Project only this authorized group; never relay another flow's state.
            if (!ui && upstreamPath.equals("/nifi-api/flow/registries")) {
                var empty = mapper.createObjectNode(); empty.putArray("registries");
                return json(empty);
            }
            if (!ui && upstreamPath.equals("/nifi-api/flow/controller/bulletins")) {
                var empty = mapper.createObjectNode();
                for (String name : List.of("controllerBulletins", "controllerServiceBulletins", "reportingTaskBulletins")) empty.putArray(name);
                return json(empty);
            }
            HttpHeaders forwarded = new HttpHeaders();
            String accept = request.getHeader(HttpHeaders.ACCEPT);
            if (accept != null) forwarded.set(HttpHeaders.ACCEPT, accept);
            ResponseEntity<byte[]> response = nifi.proxy(HttpMethod.valueOf(request.getMethod()), path, forwarded, null);
            byte[] bytes = response.getBody();
            HttpHeaders headers = new HttpHeaders(); headers.putAll(response.getHeaders());
            headers.remove(HttpHeaders.SET_COOKIE); headers.remove(HttpHeaders.LOCATION);
            headers.setCacheControl("no-store");
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            if (!ui && response.getStatusCode().is2xxSuccessful() && bytes != null
                    && headers.getContentType() != null && headers.getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)) {
                try {
                    JsonNode document = mapper.readTree(bytes);
                    if (upstreamPath.equals("/nifi-api/flow/status")) document = flowStatus(document);
                    scopeRoot(view, document);
                    collect(view, document.path("processGroupFlow").path("flow"));
                    if (document.path("controllerServices").isArray()) {
                        var selected = mapper.createArrayNode();
                        for (JsonNode service : document.path("controllerServices"))
                            if (view.objects().contains(service.path("component").path("id").asText(service.path("id").asText())))
                                selected.add(service);
                        ((com.fasterxml.jackson.databind.node.ObjectNode) document).set("controllerServices", selected);
                    }
                    readOnly(document);
                    bytes = mapper.writeValueAsBytes(document);
                } catch (java.io.IOException invalid) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "NiFi 原生响应格式无效"); }
            }
            return new ResponseEntity<>(bytes, headers, response.getStatusCode());
        }
    }

    private String authorize(View view, String fullPath) {
        if (!fullPath.startsWith("/nifi-api/")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        String path = fullPath.substring("/nifi-api".length());
        if (METADATA.contains(path)) return fullPath;
        if (path.equals("/flow/status")) return "/nifi-api/flow/process-groups/" + view.group() + "/status";
        if (path.equals("/flow/controller/bulletins")) return fullPath;
        if (path.equals("/flow/registries")) return fullPath;
        var analysis = java.util.regex.Pattern.compile("^/flow/flow-analysis/results/([A-Za-z0-9_-]+)$").matcher(path);
        if (analysis.matches()) {
            String id = analysis.group(1).equals("root") ? view.group() : analysis.group(1);
            if (view.groups().contains(id)) return "/nifi-api/flow/flow-analysis/results/" + id;
        }
        var group = java.util.regex.Pattern.compile("^/(flow/)?process-groups/([A-Za-z0-9_-]+)(/(?:status|processors|connections|controller-services))?$").matcher(path);
        if (group.matches()) {
            String id = group.group(2).equals("root") ? view.group() : group.group(2);
            if (view.groups().contains(id)) return "/nifi-api/" + (group.group(1) == null ? "" : group.group(1))
                    + "process-groups/" + id + (group.group(3) == null ? "" : group.group(3));
        }
        var object = java.util.regex.Pattern.compile("^/(?:processors|connections|controller-services|input-ports|output-ports|funnels|labels)/([A-Za-z0-9_-]+)(?:/(?:state|status))?$").matcher(path);
        if (object.matches() && view.objects().contains(object.group(1))) return fullPath;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "原生详情仅可访问当前流程及其组件");
    }

    private ResponseEntity<byte[]> json(JsonNode value) {
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                    .body(mapper.writeValueAsBytes(value));
        } catch (java.io.IOException invalid) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "NiFi 原生响应格式无效"); }
    }

    private JsonNode flowStatus(JsonNode document) {
        JsonNode snapshot = document.path("processGroupStatus").path("aggregateSnapshot");
        var result = mapper.createObjectNode();
        var status = result.putObject("controllerStatus");
        for (String name : List.of("activeThreadCount", "terminatedThreadCount", "runningCount", "stoppedCount",
                "invalidCount", "disabledCount", "activeRemotePortCount", "inactiveRemotePortCount",
                "upToDateCount", "locallyModifiedCount", "staleCount", "locallyModifiedAndStaleCount", "syncFailureCount"))
            status.put(name, snapshot.path(name).asLong(0));
        status.put("queued", snapshot.path("queued").asText("0 (0 bytes)"));
        status.put("hasPendingAccounts", false);
        for (String wrapper : List.of("processorStatusSnapshot", "inputPortStatusSnapshot", "outputPortStatusSnapshot")) {
            for (JsonNode component : snapshot.findValues(wrapper)) {
                String counter = switch (component.path("runStatus").asText("").toUpperCase(Locale.ROOT)) {
                    case "RUNNING", "STARTING" -> "runningCount";
                    case "STOPPED" -> "stoppedCount";
                    case "INVALID" -> "invalidCount";
                    case "DISABLED" -> "disabledCount";
                    default -> null;
                };
                if (counter != null && !snapshot.has(counter)) status.put(counter, status.path(counter).asLong() + 1);
            }
        }
        return result;
    }

    private void scopeRoot(View view, JsonNode document) {
        JsonNode flow = document.path("processGroupFlow");
        if (flow.isObject() && view.group().equals(flow.path("id").asText())) {
            var root = (com.fasterxml.jackson.databind.node.ObjectNode) flow;
            root.remove("parentGroupId");
            // NiFi embeds its full ancestor chain in breadcrumbs, including names.
            if (root.path("breadcrumb").isObject())
                ((com.fasterxml.jackson.databind.node.ObjectNode) root.path("breadcrumb")).remove("parentBreadcrumb");
        }
        JsonNode component = document.path("component");
        if (component.isObject() && view.group().equals(component.path("id").asText()))
            ((com.fasterxml.jackson.databind.node.ObjectNode) component).remove("parentGroupId");
    }

    private void collect(View view, JsonNode flow) {
        if (!flow.isObject()) return;
        for (String kind : List.of("processors", "connections", "processGroups", "inputPorts", "outputPorts", "funnels", "labels")) {
            for (JsonNode entry : flow.path(kind)) {
                String id = entry.path("component").path("id").asText(entry.path("id").asText());
                if (!id.isBlank()) { view.objects().add(id); if (kind.equals("processGroups")) view.groups().add(id); }
                // Controller service references are visible to the authorized native processor already.
                JsonNode config = entry.path("component").path("config");
                config.path("descriptors").fields().forEachRemaining(descriptor -> {
                    JsonNode value = config.path("properties").path(descriptor.getKey());
                    if (!descriptor.getValue().path("identifiesControllerService").asText().isBlank()
                            && value.isTextual() && value.asText().matches("[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))
                        view.objects().add(value.asText());
                });
            }
        }
    }

    private void readOnly(JsonNode value) {
        if (value.isObject()) {
            var object = (com.fasterxml.jackson.databind.node.ObjectNode) value;
            if (object.has("canWrite")) object.put("canWrite", false);
            object.elements().forEachRemaining(this::readOnly);
        } else if (value.isArray()) value.elements().forEachRemaining(this::readOnly);
    }
}
