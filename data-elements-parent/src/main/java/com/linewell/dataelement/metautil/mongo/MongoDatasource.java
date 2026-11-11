package com.linewell.dataelement.metautil.mongo;

import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bson.Document;

/** Connection and collection commands shared by registration, materialization, and NiFi. */
public final class MongoDatasource {
    private MongoDatasource() {}

    public static MongoClientSettings settings(DataSourceConfig config) {
        var properties = config.getConnectorProperties();
        String authSource = authDatabase(properties == null ? null : properties.get("authSource"));
        boolean tls = Boolean.TRUE.equals(config.getSsl())
                || (properties != null && Boolean.parseBoolean(String.valueOf(properties.get("ssl"))));
        var endpoint = new ConnectionString(endpoint(config.getHost(), String.valueOf(config.getPortOrDefault()),
                authSource, tls));
        var settings = MongoClientSettings.builder().applyConnectionString(endpoint);
        String user = config.getUsername();
        String password = config.getPassword();
        if (user != null && !user.isBlank()) {
            if (password == null || password.isEmpty()) throw new IllegalArgumentException("请填写 MongoDB 密码");
            settings.credential(MongoCredential.createCredential(user, authSource, password.toCharArray()));
        } else if (password != null && !password.isEmpty()) {
            throw new IllegalArgumentException("请填写 MongoDB 用户名");
        }
        return settings.build();
    }

    public static String endpoint(String host, String rawPort, String authSource, boolean tls) {
        if (host == null || !host.matches("(?:[A-Za-z0-9_.-]+|\\[[0-9A-Fa-f:]+\\])")) {
            throw new IllegalArgumentException("请填写 MongoDB 主机名或 IP");
        }
        int port = rawPort == null || rawPort.isBlank() ? 27017 : Integer.parseInt(rawPort);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("MongoDB 端口超出有效范围");
        // NiFi supplies credentials separately and reads the authentication database from the URI path.
        return "mongodb://" + host + ":" + port + "/" + URLEncoder.encode(authDatabase(authSource), StandardCharsets.UTF_8)
                + "?serverSelectionTimeoutMS=8000&connectTimeoutMS=5000" + (tls ? "&tls=true" : "");
    }

    private static String authDatabase(Object value) {
        return value == null || value.toString().isBlank() ? "admin" : value.toString().trim();
    }

    public static String createCommand(String collection, List<String> primaryKeys) {
        var command = validate(collection, primaryKeys);
        return new Document("create", command.collection()).append("uniqueKeys", command.uniqueKeys()).toJson();
    }

    public static CreateCollection parseCreateCommand(String input) {
        try {
            Document doc = Document.parse(input);
            if (!doc.keySet().equals(java.util.Set.of("create", "uniqueKeys"))) throw new IllegalArgumentException();
            return validate(doc.getString("create"), doc.getList("uniqueKeys", String.class));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("MongoDB 创建集合指令无效，请重新生成");
        }
    }

    private static CreateCollection validate(String collection, List<String> keys) {
        if (collection == null || !collection.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
            throw new IllegalArgumentException("MongoDB 集合名须以字母或下划线开头，且仅包含字母、数字或下划线");
        }
        if (keys == null || keys.size() > 32 || keys.stream().anyMatch(key ->
                key == null || !key.matches("[A-Za-z_][A-Za-z0-9_]*"))) {
            throw new IllegalArgumentException("MongoDB 唯一键须使用有效的顶层字段名");
        }
        var businessKeys = keys.stream().filter(key -> !"ods_uuid".equalsIgnoreCase(key)).distinct().toList();
        return new CreateCollection(collection, businessKeys.contains("_id") ? List.of() : businessKeys);
    }

    public record CreateCollection(String collection, List<String> uniqueKeys) {}
}
