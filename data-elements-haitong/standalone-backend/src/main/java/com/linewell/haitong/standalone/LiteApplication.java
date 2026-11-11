package com.linewell.haitong.standalone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public final class LiteApplication {
    static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_BODY = 2 * 1024 * 1024;
    private static final String API = "/nifi/api";
    private static final String FLOW_ID = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final System.Logger LOG = System.getLogger(LiteApplication.class.getName());
    private final FlowStore store;
    private final Nifi nifi;
    private final Path web;
    private final byte[] adminPassword;
    private final ArrayNode manifests;
    private final Object mutations = new Object();
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private int loginAttempts;
    private long loginWindow;

    static final class Failure extends RuntimeException {
        final int status;
        Failure(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private LiteApplication() throws Exception {
        String password = env("HAITONG_ADMIN_PASSWORD", "");
        if (password.length() < 12) throw new IllegalArgumentException("HAITONG_ADMIN_PASSWORD 至少 12 位");
        adminPassword = digest(password);
        String parent = env("NIFI_PARENT_GROUP", "root");
        if (!parent.equals("root") && !parent.matches(FLOW_ID)) throw new IllegalArgumentException("无效的 NIFI_PARENT_GROUP");
        nifi = new Nifi(env("NIFI_URL", "https://localhost:8443"), env("NIFI_USERNAME", ""),
                env("NIFI_PASSWORD", ""), parent);
        web = Path.of(env("HAITONG_WEB_DIR", "frontend")).toAbsolutePath().normalize();
        if (!Files.isRegularFile(web.resolve("index.html"))) throw new IllegalArgumentException("找不到前端 index.html");
        store = new FlowStore(Path.of(env("HAITONG_DATA_DIR", "data")), env("HAITONG_STORE_KEY", ""));
        manifests = loadManifests();
    }

    public static void main(String[] args) throws Exception {
        var app = new LiteApplication();
        String bind = env("HAITONG_BIND", "127.0.0.1");
        int port = Integer.parseInt(env("HAITONG_PORT", "8090"));
        var server = HttpServer.create(new InetSocketAddress(bind, port), 128);
        var workers = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(workers);
        server.createContext("/", app::handle);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(2);
            workers.close();
        }));
        server.start();
        LOG.log(System.Logger.Level.INFO, "海通流程轻量版已启动，端口 " + port);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        try {
            if (path.equals("/healthz") && exchange.getRequestMethod().equals("GET")) {
                json(exchange, 200, Nifi.object().put("status", "UP"));
            } else if (path.startsWith(API + "/")) {
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                json(exchange, 200, api(exchange, path.substring(API.length())));
            } else {
                serve(exchange, path);
            }
        } catch (Failure error) {
            json(exchange, error.status, Nifi.object().put("message", error.getMessage()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException error) {
            json(exchange, 400, Nifi.object().put("message", "请求中的 JSON 或参数格式不正确"));
        } catch (Exception error) {
            LOG.log(System.Logger.Level.ERROR, "请求处理失败 (" + error.getClass().getSimpleName() + ")");
            json(exchange, 500, Nifi.object().put("message", "服务处理失败，请检查运行配置和日志"));
        } finally {
            exchange.close();
        }
    }

    private Object api(HttpExchange exchange, String path) throws Exception {
        String method = exchange.getRequestMethod();
        if (path.equals("/session") && method.equals("POST")) return login(exchange);
        String token = authenticate(exchange);
        if (path.equals("/session") && method.equals("GET")) return Nifi.object().put("username", "admin");
        if (path.equals("/session") && method.equals("DELETE")) {
            sessions.remove(token);
            return Nifi.object().put("success", true);
        }
        if (path.equals("/manifests") && method.equals("GET")) return manifests;
        if (path.equals("/pipelines/page") && method.equals("GET")) {
            var query = exchange.getRequestURI().getRawQuery();
            return store.page(number(query, "page", 1, 100000), number(query, "size", 20, 100),
                    parameter(query, "search", "").strip());
        }
        if (path.equals("/pipelines") && method.equals("POST")) {
            var body = body(exchange);
            synchronized (mutations) { return view(save(null, body)); }
        }
        String[] route = path.split("/");
        if (route.length < 3 || !route[1].equals("pipelines") || !route[2].matches(FLOW_ID))
            throw new Failure(404, "接口不存在");
        String id = route[2];
        String action = path.substring(("/pipelines/" + id).length());
        if (method.equals("GET")) {
            var flow = store.get(id);
            return switch (action) {
                case "" -> view(flow);
                case "/status" -> status(flow);
                case "/bulletins/live" -> bulletins(flow);
                case "/ui-link" -> Nifi.object().put("url", nifi.uiLink(requireGroup(flow)));
                default -> throw new Failure(404, "接口不存在");
            };
        }
        if (method.equals("POST")) {
            var submitted = action.isEmpty() ? body(exchange) : null;
            synchronized (mutations) {
                if (action.isEmpty()) return view(save(id, submitted));
                var flow = store.get(id);
                switch (action) {
                    case "/deploy" -> deploy(flow);
                    case "/start" -> start(flow);
                    case "/stop" -> nifi.state(requireGroup(flow), "STOPPED");
                    default -> throw new Failure(404, "接口不存在");
                }
                return view(store.get(id));
            }
        }
        if (method.equals("DELETE") && action.isEmpty()) {
            synchronized (mutations) {
                var flow = store.get(id);
                cleanRetired(flow);
                removeIdleGroup(flow.groupId());
                store.delete(id);
            }
            return Nifi.object().put("success", true);
        }
        throw new Failure(405, "请求方法不受支持");
    }

    private synchronized ObjectNode login(HttpExchange exchange) throws Exception {
        String password = body(exchange).path("password").asText("");
        long now = System.currentTimeMillis();
        if (now >= loginWindow) {
            loginAttempts = 0;
            loginWindow = now + Duration.ofMinutes(1).toMillis();
        }
        if (loginAttempts >= 12) throw new Failure(429, "登录尝试过多，请稍后重试");
        if (!MessageDigest.isEqual(adminPassword, digest(password))) {
            loginAttempts++;
            throw new Failure(401, "管理员密码不正确");
        }
        loginAttempts = 0;
        byte[] entropy = new byte[32];
        RANDOM.nextBytes(entropy);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        sessions.values().removeIf(expiry -> expiry <= now);
        if (sessions.size() >= 100) throw new Failure(429, "当前登录会话已达上限");
        sessions.put(token, now + Duration.ofHours(8).toMillis());
        return Nifi.object().put("token", token);
    }

    private String authenticate(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) throw new Failure(401, "请登录海通流程轻量版");
        String token = header.substring(7);
        Long expiry = sessions.get(token);
        if (expiry == null || expiry <= System.currentTimeMillis()) throw new Failure(401, "海通登录已失效");
        return token;
    }

    private FlowStore.Flow save(String id, JsonNode body) throws Exception {
        String name = FlowCompiler.text(body, "name");
        JsonNode dsl = body.path("dsl");
        if (name.isBlank() || name.length() > 120) throw new Failure(400, "请填写 1 至 120 字的流程名称");
        if (!dsl.path("nodes").isArray() || !dsl.path("edges").isArray() || dsl.path("nodes").size() > 32
                || dsl.path("edges").size() > 64) throw new Failure(400, "画布最多支持 32 个节点、64 条连线");
        return store.save(id, name, dsl, body.path("designVersion").asLong(-1));
    }

    private void deploy(FlowStore.Flow flow) throws Exception {
        cleanRetired(flow);
        if (flow.groupId() != null) {
            try {
                if (Long.valueOf(flow.designVersion()).equals(flow.deployedVersion())) {
                    nifi.status(flow.groupId());
                    return;
                }
                nifi.requireIdle(flow.groupId());
            } catch (Failure error) {
                if (error.status != 404) throw error;
            }
        }
        var candidate = FlowCompiler.deploy(nifi, flow);
        try {
            if (flow.groupId() != null) {
                try { nifi.requireIdle(flow.groupId()); }
                catch (Failure error) { if (error.status != 404) throw error; }
            }
            store.setDeployment(flow, candidate.groupId(), candidate.mapping());
        } catch (Exception failure) {
            try { nifi.deleteGroup(candidate.groupId()); }
            catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        try { cleanRetired(store.get(flow.id())); }
        catch (Exception cleanup) {
            LOG.log(System.Logger.Level.WARNING, "旧流程组清理待重试，流程 ID " + flow.id());
        }
    }

    private void cleanRetired(FlowStore.Flow flow) throws Exception {
        if (flow.retiredGroupId() == null) return;
        removeIdleGroup(flow.retiredGroupId());
        store.clearRetired(flow.id());
    }

    private void removeIdleGroup(String group) throws Exception {
        if (group == null) return;
        try {
            nifi.requireIdle(group);
            nifi.deleteGroup(group);
        } catch (Failure error) {
            if (error.status != 404) throw error;
        }
    }

    private void start(FlowStore.Flow flow) throws Exception {
        String group = requireGroup(flow);
        if (!Long.valueOf(flow.designVersion()).equals(flow.deployedVersion()))
            throw new Failure(409, "画布已保存新版本，请先部署");
        for (var node : flow.dsl().path("nodes")) {
            if (!node.path("manifestKey").asText().startsWith("source.")) continue;
            if (node.path("config").path("runOnce").asBoolean(true)) {
                String processor = flow.nativeMapping().path("sourceProcessorId").asText();
                if (processor.isBlank()) throw new Failure(409, "部署缺少来源处理器，请重新部署");
                nifi.once(group, processor);
                return;
            }
            nifi.state(group, "RUNNING");
            return;
        }
        throw new Failure(409, "流程缺少来源节点");
    }

    private static String requireGroup(FlowStore.Flow flow) {
        if (flow.groupId() == null) throw new Failure(409, "请先部署流程");
        return flow.groupId();
    }

    private ObjectNode status(FlowStore.Flow flow) throws Exception {
        var result = view(flow);
        result.put("status", "DRAFT");
        result.set("errors", JSON.createArrayNode());
        if (flow.groupId() == null) return result;
        try {
            var nativeStatus = nifi.status(flow.groupId());
            result.set("nifiStatus", Nifi.object().set("processGroupStatus", nativeStatus));
            return result.put("status", Nifi.running(nativeStatus) ? "RUNNING" : "STOPPED");
        } catch (Failure error) {
            if (error.status != 404) throw error;
            return result.put("status", "RUN_ERROR").put("runtimeState", "MISSING");
        }
    }

    private ArrayNode bulletins(FlowStore.Flow flow) throws Exception {
        var result = JSON.createArrayNode();
        if (flow.groupId() == null) return result;
        try {
            for (var processor : nifi.flow(flow.groupId()).path("processors")) {
                for (var entry : processor.path("bulletins")) {
                    if (result.size() >= 100) return result;
                    var bulletin = entry.path("bulletin");
                    result.add(Nifi.object().put("id", bulletin.path("id").asText())
                            .put("sourceId", processor.path("id").asText())
                            .put("sourceName", processor.path("component").path("name").asText())
                            .put("level", bulletin.path("level").asText("ERROR"))
                            .put("timestamp", bulletin.path("timestamp").asText())
                            .put("message", "NiFi 节点有运行告警，请打开 NiFi 查看详情"));
                }
            }
        } catch (Failure error) {
            if (error.status != 404) throw error;
        }
        return result;
    }

    private ObjectNode view(FlowStore.Flow flow) {
        var result = Nifi.object().put("id", flow.id()).put("name", flow.name())
                .put("designVersion", flow.designVersion()).put("updatedAt", flow.updatedAt())
                .put("nifiProcessGroupId", flow.groupId()).put("deployed", flow.groupId() != null)
                .put("deployedVersion", flow.deployedVersion()).put("retiredGroupPending", flow.retiredGroupId() != null);
        result.set("dsl", flow.dsl());
        result.set("nodeMapping", flow.nativeMapping());
        result.put("processGroupId", flow.groupId());
        return result;
    }

    private ArrayNode loadManifests() throws IOException {
        var result = JSON.createArrayNode();
        for (String path : List.of("sources/mongodb", "sinks/mongodb", "transforms/java")) {
            try (var input = getClass().getResourceAsStream("/manifests/" + path + ".json")) {
                var manifest = (ObjectNode) JSON.readTree(input);
                if (path.startsWith("sources/")) {
                    var original = fields(manifest);
                    var retained = JSON.createArrayNode();
                    original.forEach(f -> { if (!"schedulingPeriod".equals(f.path("key").asText())) retained.add(f); });
                    retained.add(field("query", "MongoDB 查询条件（JSON）", "json-editor", false).put("default", "{}"));
                    manifest.set("fields", retained);
                    schedule(manifest);
                }
                result.add(manifest);
            }
        }
        for (String category : List.of("source", "sink")) {
            boolean source = category.equals("source");
            var jdbc = manifest(category + ".jdbc", category, source ? "JDBC 数据源" : "JDBC 写入", "database");
            var fields = fields(jdbc);
            var engine = field("databaseType", "数据库类型", "select", true).put("default", "MySQL");
            var choices = engine.putArray("options");
            for (String value : List.of("MariaDB", "MySQL", "PostgreSQL", "Oracle", "Generic"))
                choices.add(Nifi.object().put("label", value).put("value", value));
            fields.add(engine);
            fields.add(field("jdbcUrl", "JDBC URL", "text", true));
            fields.add(field("driverPath", "NiFi 主机上的 JDBC 驱动 JAR", "text", false));
            fields.add(field("driverClass", "驱动类名（使用数据库默认时留空）", "text", false));
            fields.add(field("username", "用户名", "text", false));
            fields.add(field("password", "密码", "password", false));
            if (source) {
                fields.add(field("query", "来源查询 SQL", "sql-editor", true));
                schedule(jdbc);
            } else {
                fields.add(field("schema", "Schema", "text", false));
                fields.add(field("table", "目标表", "text", true));
            }
            result.add(jdbc);
        }
        return result;
    }

    private static ObjectNode manifest(String key, String category, String label, String icon) {
        var result = Nifi.object().put("key", key).put("category", category).put("label", label).put("icon", icon);
        result.putArray("fields");
        return result;
    }
    private static ArrayNode fields(ObjectNode manifest) { return (ArrayNode) manifest.path("fields"); }
    private static ObjectNode field(String key, String label, String type, boolean required) {
        return Nifi.object().put("key", key).put("label", label).put("type", type).put("required", required);
    }
    private static void schedule(ObjectNode manifest) {
        fields(manifest).add(field("runOnce", "每次点击启动只读一轮", "switch", false).put("default", true));
        fields(manifest).add(field("intervalSeconds", "周期运行间隔（秒）", "number", false).put("default", 300));
    }

    private void serve(HttpExchange exchange, String path) throws IOException {
        if (!List.of("GET", "HEAD").contains(exchange.getRequestMethod())) throw new Failure(405, "请求方法不受支持");
        if (path.equals("/")) path = "/index.html";
        if (!path.equals("/index.html") && !path.matches("/assets/[A-Za-z0-9._-]+"))
            throw new Failure(404, "文件不存在");
        Path file = web.resolve(path.substring(1)).normalize();
        if (!file.startsWith(web) || !Files.isRegularFile(file)) throw new Failure(404, "文件不存在");
        String name = file.getFileName().toString();
        String extension = name.substring(name.lastIndexOf('.') + 1);
        String type = switch (extension) {
            case "html" -> "text/html; charset=utf-8";
            case "js" -> "text/javascript; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "png" -> "image/png";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", name.equals("index.html") ? "no-store" : "public, max-age=31536000, immutable");
        boolean head = exchange.getRequestMethod().equals("HEAD");
        exchange.sendResponseHeaders(200, head ? -1 : Files.size(file));
        if (!head) Files.copy(file, exchange.getResponseBody());
    }

    private static JsonNode body(HttpExchange exchange) throws IOException {
        byte[] data = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
        if (data.length > MAX_BODY) throw new Failure(413, "请求内容过大");
        JsonNode body = JSON.readTree(data);
        if (body == null || !body.isObject()) throw new Failure(400, "请求须为 JSON 对象");
        return body;
    }
    private static int number(String query, String key, int fallback, int max) {
        return Math.max(1, Math.min(max, Integer.parseInt(parameter(query, key, Integer.toString(fallback)))));
    }
    private static String parameter(String query, String key, String fallback) {
        if (query == null) return fallback;
        for (String part : query.split("&")) {
            if (part.startsWith(key + "=")) return URLDecoder.decode(part.substring(key.length() + 1), StandardCharsets.UTF_8);
        }
        return fallback;
    }
    private static void json(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = JSON.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
    private static byte[] digest(String value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    }
    private static String env(String key, String fallback) {
        return System.getenv().getOrDefault(key, fallback);
    }
}
