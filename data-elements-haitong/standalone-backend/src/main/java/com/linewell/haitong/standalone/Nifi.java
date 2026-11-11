package com.linewell.haitong.standalone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Revision-aware client for the Apache NiFi endpoints used by the standalone editor. */
final class Nifi {
    private final HttpClient client;
    private final URI base;
    private final String username;
    private final String password;
    private final String parent;
    private final String clientId = UUID.randomUUID().toString();
    private String token;

    Nifi(String url, String username, String password, String parent) throws Exception {
        base = URI.create(url.replaceAll("/+$", ""));
        if (!Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !base.getPath().isEmpty()) throw new IllegalArgumentException("NIFI_URL 须填写 NiFi 根地址");
        if (!username.isBlank() && !"https".equals(base.getScheme()))
            throw new IllegalArgumentException("配置 NiFi 登录凭据时须使用 HTTPS");
        if ("http".equals(base.getScheme()) && !Set.of("localhost", "127.0.0.1", "[::1]").contains(base.getHost()))
            throw new IllegalArgumentException("远程 NiFi 节点须使用 HTTPS");
        this.username = username;
        this.password = password;
        this.parent = parent;
        var builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8));
        String truststore = System.getenv("NIFI_TRUSTSTORE");
        if (truststore != null && !truststore.isBlank()) {
            var keys = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(Path.of(truststore))) {
                keys.load(input, System.getenv().getOrDefault("NIFI_TRUSTSTORE_PASSWORD", "").toCharArray());
            }
            var factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(keys);
            var ssl = SSLContext.getInstance("TLS");
            ssl.init(null, factory.getTrustManagers(), null);
            builder.sslContext(ssl);
        }
        client = builder.build();
    }

    JsonNode get(String path) throws Exception { return request("GET", path, null); }
    JsonNode post(String path, ObjectNode body) throws Exception { return request("POST", path, body); }
    JsonNode put(String path, ObjectNode body) throws Exception { return request("PUT", path, body); }

    private JsonNode request(String method, String path, ObjectNode body) throws Exception {
        String accessToken = accessToken(false);
        var response = send(method, path, body == null ? "" : body.toString(), "application/json", accessToken);
        if (response.statusCode() == 401 && !username.isBlank()) {
            response = send(method, path, body == null ? "" : body.toString(), "application/json", accessToken(true));
        }
        if (response.statusCode() >= 300) {
            int status = response.statusCode();
            throw new LiteApplication.Failure(status == 404 ? 404 : 502,
                    "NiFi 请求失败（HTTP " + status + "，" + method + " " + path.split("\\?")[0] + "）。请查看 NiFi 运行日志。");
        }
        return response.body().isBlank() ? LiteApplication.JSON.createObjectNode() : LiteApplication.JSON.readTree(response.body());
    }

    private synchronized String accessToken(boolean refresh) throws Exception {
        if (username.isBlank()) return "";
        if (token == null || refresh) {
            var response = send("POST", "/access/token",
                    "username=" + encode(username) + "&password=" + encode(password), "application/x-www-form-urlencoded", "");
            if (response.statusCode() >= 300) throw new LiteApplication.Failure(502, "NiFi 登录失败，请检查节点凭据及证书");
            token = response.body();
        }
        return token;
    }

    private HttpResponse<String> send(String method, String path, String body, String type, String accessToken) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + "/nifi-api" + path)).timeout(Duration.ofSeconds(40))
                .header("Content-Type", type).header("Accept", "application/json")
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (!accessToken.isEmpty()) builder.header("Authorization", "Bearer " + accessToken);
        try {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException unavailable) {
            throw new LiteApplication.Failure(502, "无法连接 NiFi，请检查节点地址、端口和 TLS 信任配置");
        }
    }

    static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    static ObjectNode object() { return LiteApplication.JSON.createObjectNode(); }

    ObjectNode createBody(ObjectNode component) {
        var body = object();
        body.set("component", component);
        body.set("revision", object().put("version", 0).put("clientId", clientId));
        return body;
    }

    ObjectNode updateBody(JsonNode entity, ObjectNode component) {
        var body = object();
        body.set("component", component);
        body.set("revision", entity.path("revision"));
        return body;
    }

    String createGroup(String name) throws Exception {
        var group = object().put("name", name);
        group.set("position", position(0, 0));
        return post("/process-groups/" + parent + "/process-groups", createBody(group)).path("id").asText();
    }

    Map<String, JsonNode> types(String collection) throws Exception {
        Map<String, JsonNode> types = new LinkedHashMap<>();
        var listing = get("/flow/" + collection).path(collection.equals("processor-types") ? "processorTypes" : "controllerServiceTypes");
        for (var item : listing) types.put(item.path("type").asText(), item.path("bundle"));
        return types;
    }

    String createProcessor(String group, String type, JsonNode bundle, String name, double x, double y,
                           String interval, Map<String, String> properties) throws Exception {
        var component = object().put("type", type).put("name", name);
        component.set("bundle", bundle);
        component.set("position", position(x, y));
        var created = post("/process-groups/" + group + "/processors", createBody(component));
        String id = created.path("id").asText();
        var config = object().put("schedulingStrategy", "TIMER_DRIVEN").put("schedulingPeriod", interval)
                .put("concurrentlySchedulableTaskCount", 1);
        config.set("properties", propertyValues(created.path("component").path("config").path("descriptors"), properties, type));
        put("/processors/" + id, updateBody(created, object().put("id", id).set("config", config)));
        return id;
    }

    String createService(String group, String type, JsonNode bundle, String name, Map<String, String> properties) throws Exception {
        var component = object().put("type", type).put("name", name);
        component.set("bundle", bundle);
        var created = post("/process-groups/" + group + "/controller-services", createBody(component));
        String id = created.path("id").asText();
        var update = object().put("id", id);
        update.set("properties", propertyValues(created.path("component").path("descriptors"), properties, type));
        put("/controller-services/" + id, updateBody(created, update));
        return id;
    }

    private ObjectNode propertyValues(JsonNode descriptors, Map<String, String> values, String type) {
        var properties = object();
        for (var entry : values.entrySet()) {
            String name = entry.getKey();
            if (!descriptors.has(name) && !name.startsWith("command.argument.")) {
                var names = descriptors.fields();
                while (names.hasNext()) {
                    var descriptor = names.next();
                    if (name.equals(descriptor.getValue().path("displayName").asText())) {
                        name = descriptor.getKey();
                        break;
                    }
                }
                if (!descriptors.has(name))
                    throw new LiteApplication.Failure(422, type.substring(type.lastIndexOf('.') + 1) + " 不支持属性 " + name);
            }
            properties.put(name, entry.getValue());
        }
        return properties;
    }

    void enable(String group, Set<String> ids) throws Exception {
        if (ids.isEmpty()) return;
        put("/flow/process-groups/" + group + "/controller-services",
                object().put("id", group).put("state", "ENABLED"));
        String url = "/flow/process-groups/" + group + "/controller-services?includeAncestorGroups=false&includeDescendantGroups=false";
        for (int attempt = 0; attempt < 40; attempt++) {
            var services = get(url).path("controllerServices");
            long enabled = 0;
            for (var service : services) {
                if (ids.contains(service.path("id").asText()) && "ENABLED".equals(service.path("component").path("state").asText())) enabled++;
            }
            if (enabled == ids.size()) return;
            Thread.sleep(250);
        }
        throw new LiteApplication.Failure(422, "NiFi 控制服务未能启用，请核对数据库驱动与连接配置");
    }

    String connect(String group, String source, String target, String type, List<String> relationships) throws Exception {
        var component = object().put("flowFileExpiration", "0 sec")
                .put("backPressureObjectThreshold", 10000).put("backPressureDataSizeThreshold", "1 GB");
        component.set("source", object().put("id", source).put("type", "PROCESSOR").put("groupId", group));
        component.set("destination", object().put("id", target).put("type", type).put("groupId", group));
        component.set("selectedRelationships", LiteApplication.JSON.valueToTree(relationships));
        return post("/process-groups/" + group + "/connections", createBody(component)).path("id").asText();
    }

    String funnel(String group, double x, double y) throws Exception {
        return post("/process-groups/" + group + "/funnels", createBody(object().set("position", position(x, y))))
                .path("id").asText();
    }

    private ObjectNode position(double x, double y) { return object().put("x", x).put("y", y); }

    JsonNode flow(String group) throws Exception {
        return get("/flow/process-groups/" + group).path("processGroupFlow").path("flow");
    }

    JsonNode status(String group) throws Exception {
        return get("/flow/process-groups/" + group + "/status?recursive=true").path("processGroupStatus");
    }

    static boolean running(JsonNode status) {
        for (var entry : status.path("aggregateSnapshot").path("processorStatusSnapshots")) {
            if ("Running".equalsIgnoreCase(entry.path("processorStatusSnapshot").path("runStatus").asText())) return true;
        }
        return false;
    }

    void requireIdle(String group) throws Exception {
        var status = status(group);
        var counts = status.path("aggregateSnapshot");
        if (running(status) || counts.path("queuedCount").asLong(-1) != 0 || counts.path("activeThreadCount").asInt(-1) != 0) {
            throw new LiteApplication.Failure(409, "请先在 NiFi 处理队列并停止当前流程");
        }
    }

    void terminate(JsonNode entity, List<String> names) throws Exception {
        var config = object();
        config.set("autoTerminatedRelationships", LiteApplication.JSON.valueToTree(names));
        String id = entity.path("id").asText();
        put("/processors/" + id, updateBody(entity, object().put("id", id).set("config", config)));
    }

    void state(String group, String state) throws Exception {
        put("/flow/process-groups/" + group, object().put("id", group).put("state", state));
    }

    void once(String group, String source) throws Exception {
        var snapshot = status(group).path("aggregateSnapshot");
        if (snapshot.path("queuedCount").asLong(-1) != 0 || snapshot.path("activeThreadCount").asInt(-1) != 0)
            throw new LiteApplication.Failure(409, "上一轮数据仍在处理，请等待队列清空后再次启动");
        var others = object();
        JsonNode sourceEntity = null;
        for (var node : flow(group).path("processors")) {
            String id = node.path("id").asText();
            if (id.equals(source)) sourceEntity = node;
            else others.set(id, node.path("revision"));
        }
        if (sourceEntity == null || !"STOPPED".equals(sourceEntity.path("component").path("state").asText())) {
            throw new LiteApplication.Failure(409, "单次读取处理器必须处于停止状态");
        }
        if (!others.isEmpty()) put("/flow/process-groups/" + group,
                object().put("id", group).put("state", "RUNNING").set("components", others));
        put("/processors/" + source + "/run-status",
                object().put("state", "RUN_ONCE").set("revision", sourceEntity.path("revision")));
    }

    void deleteGroup(String group) throws Exception {
        var entity = get("/process-groups/" + group);
        request("DELETE", "/process-groups/" + group + "?version=" + entity.path("revision").path("version").asLong()
                + "&clientId=" + clientId, null);
    }

    String uiLink(String group) { return base + "/nifi/#/process-groups/" + group; }
}
