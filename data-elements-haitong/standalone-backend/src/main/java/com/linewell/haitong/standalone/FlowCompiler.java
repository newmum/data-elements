package com.linewell.haitong.standalone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class FlowCompiler {
    private static final String STANDARD = "org.apache.nifi.processors.standard.";
    private static final String MONGO = "org.apache.nifi.processors.mongodb.";
    private static final String MONGO_SERVICE = "org.apache.nifi.mongodb.MongoDBControllerService";
    private static final String POOL = "org.apache.nifi.dbcp.DBCPConnectionPool";
    private static final String READER = "org.apache.nifi.json.JsonTreeReader";
    private static final String WRITER = "org.apache.nifi.json.JsonRecordSetWriter";
    private static final Set<String> FAILURE_RELATIONSHIPS = Set.of("failure", "retry", "no retry", "nonzero status", "unmatched");
    static final Map<String, String> CATEGORIES = Map.of("source.jdbc", "source", "source.mongodb", "source",
            "transform.java", "transform", "sink.mongodb", "sink", "sink.jdbc", "sink");
    private static final Map<String, String> DATABASE_DRIVERS = Map.of(
            "MariaDB", "org.mariadb.jdbc.Driver", "MySQL", "com.mysql.cj.jdbc.Driver",
            "PostgreSQL", "org.postgresql.Driver", "Generic", "org.h2.Driver");
    private final Nifi nifi;
    private final Map<String, JsonNode> processors;
    private final Map<String, JsonNode> services;
    private final String group;
    private final Set<String> serviceIds = new LinkedHashSet<>();
    private final Map<String, Set<String>> linked = new LinkedHashMap<>();
    private final ObjectNode primary = Nifi.object();
    private final ObjectNode reverse = Nifi.object();
    private final ObjectNode edgeEndpoints = Nifi.object();
    private String reader;
    private String writer;

    private record Node(String id, String key, String label, double x, double y, JsonNode config) {}
    private record Part(String first, String last, String relationship) {}
    private record Edge(String id, String source, String target) {}
    private record Graph(Map<String, Node> nodes, List<Edge> edges, Node source) {}
    record Deployment(String groupId, ObjectNode mapping) {}

    private FlowCompiler(Nifi nifi, String group, Map<String, JsonNode> processors, Map<String, JsonNode> services) {
        this.nifi = nifi;
        this.group = group;
        this.processors = processors;
        this.services = services;
    }

    static Deployment deploy(Nifi nifi, FlowStore.Flow flow) throws Exception {
        var graph = readGraph(flow.dsl());
        var processors = nifi.types("processor-types");
        var services = nifi.types("controller-service-types");
        String group = nifi.createGroup("海通 " + flow.name() + " [" + flow.id().substring(0, 8) + "]");
        try {
            var compiler = new FlowCompiler(nifi, group, processors, services);
            return new Deployment(group, compiler.compile(graph));
        } catch (Exception failure) {
            try { nifi.deleteGroup(group); }
            catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private ObjectNode compile(Graph graph) throws Exception {
        var parts = new LinkedHashMap<String, Part>();
        for (Node node : graph.nodes.values()) parts.put(node.id, create(node));
        for (Edge edge : graph.edges) {
            var source = parts.get(edge.source);
            var target = parts.get(edge.target);
            String connection = link(source.last, target.first, "PROCESSOR", source.relationship);
            edgeEndpoints.set(edge.id, Nifi.object().put("sourceProcessorId", source.last)
                    .put("targetProcessorId", target.first).put("connectionId", connection));
        }
        String failureFunnel = null;
        for (JsonNode processor : nifi.flow(group).path("processors")) {
            String id = processor.path("id").asText();
            var terminated = new ArrayList<String>();
            for (var relation : processor.path("component").path("relationships")) {
                String name = relation.path("name").asText();
                if (linked.getOrDefault(id, Set.of()).contains(name)) continue;
                if (FAILURE_RELATIONSHIPS.contains(name)) {
                    if (failureFunnel == null) failureFunnel = nifi.funnel(group, 360, 530);
                    link(id, failureFunnel, "FUNNEL", name);
                } else terminated.add(name);
            }
            if (!terminated.isEmpty()) nifi.terminate(processor, terminated);
        }
        nifi.enable(group, serviceIds);
        for (int attempt = 0; attempt < 12; attempt++) {
            var invalid = new ArrayList<String>();
            for (var processor : nifi.flow(group).path("processors")) {
                var component = processor.path("component");
                if (!"VALID".equals(component.path("validationStatus").asText()))
                    invalid.add(component.path("name").asText());
            }
            if (invalid.isEmpty()) break;
            if (attempt == 11) throw error("NiFi 组件未通过校验：" + String.join("、", invalid));
            Thread.sleep(300);
        }
        var mapping = Nifi.object().put("sourceProcessorId", parts.get(graph.source.id).first)
                .put("once", graph.source.config.path("runOnce").asBoolean(true));
        mapping.set("primaryProcessorIds", primary);
        mapping.set("processorIdToCanvasNode", reverse);
        mapping.set("edgeProcessorEndpoints", edgeEndpoints);
        return mapping;
    }

    private Part create(Node node) throws Exception {
        JsonNode config = node.config;
        String interval = CATEGORIES.get(node.key).equals("source") ? sourceInterval(config) : "0 sec";
        String processor;
        String relationship = "success";
        switch (node.key) {
            case "source.mongodb" -> {
                var properties = mongoProperties(config, mongoService(node));
                String query = optional(config, "query", "{}");
                if (!LiteApplication.JSON.readTree(query).isObject()) throw error("MongoDB 查询须填写 JSON 对象");
                properties.put("Query", query);
                properties.put("Results Per FlowFile", "500");
                processor = processor(node, MONGO + "GetMongo", "", interval, properties);
            }
            case "sink.mongodb" -> {
                var properties = mongoProperties(config, mongoService(node));
                properties.put("Record Reader", reader());
                putIfPresent(properties, "Update Key Fields", text(config, "updateKeys"));
                processor = processor(node, MONGO + "PutMongoRecord", "", interval, properties);
            }
            case "source.jdbc" -> {
                var properties = new LinkedHashMap<String, String>();
                properties.put("Database Connection Pooling Service", jdbcService(node));
                properties.put("Record Writer", writer());
                properties.put("SQL Query", required(config, "query", "来源查询 SQL"));
                properties.put("Max Rows Per FlowFile", "500");
                processor = processor(node, STANDARD + "ExecuteSQLRecord", "", interval, properties);
            }
            case "sink.jdbc" -> {
                var properties = new LinkedHashMap<String, String>();
                properties.put("Database Connection Pooling Service", jdbcService(node));
                properties.put("Record Reader", reader());
                String databaseType = optional(config, "databaseType", "Generic");
                properties.put("Database Type", "MariaDB".equals(databaseType) ? "MySQL" : databaseType);
                properties.put("Statement Type", "INSERT");
                properties.put("Table Name", required(config, "table", "目标表"));
                putIfPresent(properties, "Schema Name", text(config, "schema"));
                processor = processor(node, STANDARD + "PutDatabaseRecord", "", interval, properties);
            }
            case "transform.java" -> {
                var args = new ArrayList<>(List.of("-jar", literal(required(config, "programPath", "JAR 路径"))));
                if (args.get(1).startsWith("-") || !args.get(1).toLowerCase(java.util.Locale.ROOT).endsWith(".jar"))
                    throw error("JAVA 加工须填写 NiFi 执行主机上的 JAR 路径");
                var arguments = config.path("arguments");
                if (!arguments.isMissingNode() && (!arguments.isArray() || arguments.size() > 64))
                    throw error("JAVA 加工最多接受 64 个文本参数");
                for (JsonNode arg : arguments) {
                    if (!arg.isTextual() || arg.asText().isBlank()) throw error("JAVA 加工参数须为非空文本");
                    args.add(literal(arg.asText()));
                }
                var properties = new LinkedHashMap<String, String>();
                properties.put("Command Path", literal(optional(config, "executable", "java")));
                properties.put("Command Arguments Strategy", "Dynamic Property Arguments");
                properties.put("Ignore STDIN", "false");
                putIfPresent(properties, "Working Directory", literal(text(config, "workingDirectory")));
                for (int i = 0; i < args.size(); i++) properties.put("command.argument." + i, args.get(i));
                processor = processor(node, STANDARD + "ExecuteStreamCommand", "", interval, properties);
                relationship = "output stream";
            }
            default -> throw error("当前组件不可用");
        }
        primary.put(node.id, processor);
        return new Part(processor, processor, relationship);
    }

    private String jdbcService(Node node) throws Exception {
        JsonNode config = node.config;
        String url = required(config, "jdbcUrl", "JDBC URL");
        if (!url.startsWith("jdbc:") || url.contains("${")) throw error("请填写有效的 JDBC URL");
        String driver = optional(config, "driverClass",
                DATABASE_DRIVERS.getOrDefault(optional(config, "databaseType", "Generic"), ""));
        if (driver.isBlank()) throw error("请填写 JDBC 驱动类名");
        var properties = new LinkedHashMap<String, String>();
        properties.put("Database Connection URL", url);
        properties.put("Database Driver Class Name", driver);
        putIfPresent(properties, "Database Driver Locations", text(config, "driverPath"));
        putIfPresent(properties, "Database User", text(config, "username"));
        putIfPresent(properties, "Password", config.path("password").asText(""));
        return service(POOL, node.label + " JDBC", properties);
    }

    private String mongoService(Node node) throws Exception {
        JsonNode config = node.config;
        String host = required(config, "host", "MongoDB 主机");
        if (!host.matches("[A-Za-z0-9.\\-]+|\\[[A-Fa-f0-9:]+\\]")) throw error("MongoDB 主机须为域名或 IP 地址");
        int port = config.path("port").asInt(27017);
        if (port < 1 || port > 65535) throw error("MongoDB 端口无效");
        String uri = "mongodb://" + host + ":" + port + "/?authSource=" + Nifi.encode(optional(config, "authSource", "admin"))
                + (config.path("ssl").asBoolean() ? "&tls=true" : "");
        var properties = new LinkedHashMap<String, String>();
        properties.put("Mongo URI", uri);
        putIfPresent(properties, "Database User", text(config, "username"));
        putIfPresent(properties, "Password", config.path("password").asText(""));
        return service(MONGO_SERVICE, node.label + " MongoDB", properties);
    }

    private Map<String, String> mongoProperties(JsonNode config, String service) {
        var properties = new LinkedHashMap<String, String>();
        properties.put("Client Service", service);
        properties.put("Mongo Database Name", required(config, "database", "MongoDB 数据库"));
        properties.put("Mongo Collection Name", required(config, "table", "MongoDB 集合"));
        return properties;
    }

    private String reader() throws Exception {
        if (reader == null) reader = service(READER, "JSON 记录读取器", Map.of());
        return reader;
    }

    private String writer() throws Exception {
        if (writer == null) writer = service(WRITER, "JSON 记录输出器", Map.of());
        return writer;
    }

    private String service(String type, String label, Map<String, String> properties) throws Exception {
        String id = nifi.createService(group, type, bundle(services, type), label, properties);
        serviceIds.add(id);
        return id;
    }

    private String processor(Node node, String type, String suffix, String interval, Map<String, String> properties) throws Exception {
        String label = suffix.isEmpty() ? node.label : node.label + " / " + suffix;
        double offset = switch (suffix) { case "请求" -> 300; case "提取" -> 600; default -> 0; };
        String id = nifi.createProcessor(group, type, bundle(processors, type), label, node.x * 2 + offset, node.y, interval, properties);
        reverse.put(id, node.id);
        return id;
    }

    private String link(String source, String target, String type, String relationship) throws Exception {
        linked.computeIfAbsent(source, key -> new HashSet<>()).add(relationship);
        return nifi.connect(group, source, target, type, List.of(relationship));
    }

    private JsonNode bundle(Map<String, JsonNode> types, String type) {
        JsonNode bundle = types.get(type);
        if (bundle == null || !bundle.isObject()) throw error("NiFi 缺少组件 " + type + "，请按部署手册安装对应 NAR");
        return bundle;
    }

    private static Graph readGraph(JsonNode dsl) {
        var nodes = new LinkedHashMap<String, Node>();
        var edges = new ArrayList<Edge>();
        Node root = null;
        for (var value : dsl.path("nodes")) {
            String key = value.path("manifestKey").asText();
            var node = new Node(value.path("id").asText(), key, value.path("label").asText(key),
                    value.path("x").asDouble(), value.path("y").asDouble(), value.path("config"));
            if (nodes.putIfAbsent(node.id, node) != null) throw error("节点标识重复");
            if ("source".equals(CATEGORIES.get(key))) {
                if (root != null) throw error("每条流程需要一个来源节点");
                root = node;
            }
        }
        if (root == null || nodes.size() < 2) throw error("请连接来源与目标节点");
        var outbound = new HashMap<String, List<String>>();
        var inbound = new HashSet<String>();
        var edgeIds = new HashSet<String>();
        for (var value : dsl.path("edges")) {
            var edge = new Edge(value.path("id").asText(), value.path("source").asText(), value.path("target").asText());
            Node source = nodes.get(edge.source);
            Node target = nodes.get(edge.target);
            if (source == null || target == null || "sink".equals(CATEGORIES.get(source.key))
                    || "source".equals(CATEGORIES.get(target.key)) || !inbound.add(edge.target)
                    || !edgeIds.add(edge.id) || !value.path("outlet").asText("default").equals("default"))
                throw error("请按来源、加工、目标的顺序连接，每个下游节点只连接一个上游");
            outbound.computeIfAbsent(edge.source, id -> new ArrayList<>()).add(edge.target);
            edges.add(edge);
        }
        var toVisit = new ArrayDeque<>(List.of(root.id));
        var visited = new HashSet<String>();
        while (!toVisit.isEmpty()) {
            String id = toVisit.remove();
            if (!visited.add(id)) throw error("流程不能包含循环");
            var next = outbound.getOrDefault(id, List.of());
            if (next.isEmpty() && !"sink".equals(CATEGORIES.get(nodes.get(id).key))) throw error("每个分支须接入目标节点");
            toVisit.addAll(next);
        }
        if (visited.size() != nodes.size()) throw error("请连接所有节点");
        return new Graph(nodes, edges, root);
    }

    private static String sourceInterval(JsonNode config) {
        if (config.path("runOnce").asBoolean(true)) return "60 sec";
        int seconds = config.path("intervalSeconds").asInt(60);
        if (seconds < 5 || seconds > 86400) throw error("调度间隔须为 5 至 86400 秒");
        return seconds + " sec";
    }

    static String text(JsonNode object, String key) { return object.path(key).asText("").trim(); }
    private static String required(JsonNode object, String key, String label) {
        String value = text(object, key);
        if (value.isBlank()) throw error("请填写" + label);
        return value;
    }
    private static String optional(JsonNode object, String key, String fallback) {
        String value = text(object, key);
        return value.isBlank() ? fallback : value;
    }
    private static void putIfPresent(Map<String, String> properties, String key, String value) {
        if (!value.isBlank()) properties.put(key, value);
    }
    private static String literal(String value) {
        if (value.length() > 8192 || value.indexOf('\0') >= 0 || value.contains("${"))
            throw error("JAVA 加工的路径及参数须填写普通文本，每项最多 8192 字符");
        return value;
    }
    private static LiteApplication.Failure error(String message) { return new LiteApplication.Failure(422, message); }
}
