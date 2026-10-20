package com.linewell.dataelement.feature.surveillance.application;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class SurveillanceKafkaConnectionService {
    private static final String ENGINE = "structured-surveillance";
    public static final String INPUT = "INPUT";
    public static final String OUTPUT = "OUTPUT";
    private static final Pattern TOPIC_NAME = Pattern.compile("[a-zA-Z0-9._-]{1,249}");
    private final JdbcTemplate jdbc;

    public SurveillanceKafkaConnectionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Connection get(String tenantId, String engineCode) {
        return get(tenantId, engineCode, INPUT);
    }

    public Connection get(String tenantId, String engineCode, String role) {
        return jdbc.query("""
                SELECT connection_name, bootstrap_servers, security_protocol, status
                  FROM surveillance_kafka_connection_t
                 WHERE tenant_id = ? AND engine_code = ? AND connection_role = ? AND is_del = 0
                """, (rs, rowNum) -> new Connection(rs.getString("connection_name"),
                rs.getString("bootstrap_servers"), rs.getString("security_protocol"), rs.getString("status")),
                tenantId, normalizeEngine(engineCode), normalizeRole(role)).stream().findFirst().orElse(null);
    }

    public Connection save(String tenantId, ConnectionCommand command) {
        return save(tenantId, INPUT, command);
    }

    public Connection save(String tenantId, String role, ConnectionCommand command) {
        String engineCode = normalizeEngine(command.engineCode());
        String connectionRole = normalizeRole(role);
        String name = required(command.connectionName(), "connectionName");
        String bootstrapServers = required(command.bootstrapServers(), "bootstrapServers");
        String protocol = command.securityProtocol() == null || command.securityProtocol().isBlank()
                ? "PLAINTEXT" : command.securityProtocol().trim().toUpperCase();
        if (!"PLAINTEXT".equals(protocol)) {
            throw new IllegalArgumentException("当前布控引擎演示连接仅支持 PLAINTEXT；SASL/SSL 需要先接入服务端凭据库");
        }
        String status = command.status() == null || command.status().isBlank()
                ? "ACTIVE" : command.status().trim().toUpperCase();
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new IllegalArgumentException("status 只支持 ACTIVE 或 DISABLED");
        }
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO surveillance_kafka_connection_t
                    (tid, tenant_id, engine_code, connection_role, connection_name, bootstrap_servers,
                     security_protocol, status, created_time, updated_time, is_del)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                ON DUPLICATE KEY UPDATE connection_name = VALUES(connection_name),
                    bootstrap_servers = VALUES(bootstrap_servers), security_protocol = VALUES(security_protocol),
                    status = VALUES(status), updated_time = VALUES(updated_time), is_del = 0
                """, UUID.randomUUID().toString().replace("-", ""), tenantId, engineCode, connectionRole, name,
                bootstrapServers, protocol, status, Timestamp.from(now), Timestamp.from(now));
        return get(tenantId, engineCode, connectionRole);
    }

    public Connection test(String tenantId, String engineCode) {
        return test(tenantId, engineCode, INPUT);
    }

    public Connection test(String tenantId, String engineCode, String role) {
        Connection connection = requireActive(tenantId, engineCode, role);
        try (AdminClient admin = AdminClient.create(properties(connection))) {
            admin.describeCluster().nodes().get(8, TimeUnit.SECONDS);
            return connection;
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka 集群连接失败：" + safeMessage(exception), exception);
        }
    }

    public List<String> topics(String tenantId, String engineCode) {
        return topics(tenantId, engineCode, INPUT);
    }

    public List<String> topics(String tenantId, String engineCode, String role) {
        Connection connection = requireActive(tenantId, engineCode, role);
        try (AdminClient admin = AdminClient.create(properties(connection))) {
            return admin.listTopics().names().get(10, TimeUnit.SECONDS).stream()
                    .filter(name -> !name.startsWith("__"))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("读取 Kafka Topic 列表失败：" + safeMessage(exception), exception);
        }
    }

    /** Creates a topic only after an explicit administrator action. Resource registration never creates one. */
    public TopicCreation createTopic(String tenantId, String engineCode, String topicName) {
        return createTopic(tenantId, engineCode, OUTPUT, topicName);
    }

    public void requireExistingTopic(String tenantId, String engineCode, String role, String topicName) {
        String topic = required(topicName, "topicName");
        if (!topics(tenantId, engineCode, role).contains(topic)) {
            throw new IllegalArgumentException(normalizeRole(role) + " Kafka 中不存在 Topic: " + topic);
        }
    }

    public TopicCreation createTopic(String tenantId, String engineCode, String role, String topicName) {
        String topic = required(topicName, "topicName");
        if (!TOPIC_NAME.matcher(topic).matches()) {
            throw new IllegalArgumentException("Topic 名称只允许字母、数字、点、下划线和连字符，长度不超过 249");
        }
        Connection connection = requireActive(tenantId, engineCode, role);
        try (AdminClient admin = AdminClient.create(properties(connection))) {
            if (admin.listTopics().names().get(10, TimeUnit.SECONDS).contains(topic)) {
                return new TopicCreation(topic, false);
            }
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1)))
                    .all().get(10, TimeUnit.SECONDS);
            return new TopicCreation(topic, true);
        } catch (Exception exception) {
            throw new IllegalStateException("创建结果 Topic 失败：" + safeMessage(exception), exception);
        }
    }

    private Connection requireActive(String tenantId, String engineCode, String role) {
        Connection connection = get(tenantId, engineCode, role);
        if (connection == null || !"ACTIVE".equalsIgnoreCase(connection.status())) {
            throw new IllegalStateException("请先配置并启用布控引擎 " + normalizeRole(role) + " Kafka 连接");
        }
        return connection;
    }

    private Properties properties(Connection connection) {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, connection.bootstrapServers());
        properties.put(AdminClientConfig.CLIENT_ID_CONFIG, "surveillance-engine-admin");
        properties.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "8000");
        properties.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "10000");
        properties.put("security.protocol", connection.securityProtocol());
        return properties;
    }

    private String normalizeEngine(String value) {
        String engine = value == null || value.isBlank() ? ENGINE : value.trim();
        if (!ENGINE.equals(engine)) throw new IllegalArgumentException("不支持的布控引擎: " + engine);
        return engine;
    }

    private String normalizeRole(String value) {
        String role = value == null || value.isBlank() ? INPUT : value.trim().toUpperCase();
        if (!INPUT.equals(role) && !OUTPUT.equals(role)) {
            throw new IllegalArgumentException("role 只支持 INPUT 或 OUTPUT");
        }
        return role;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return value.trim();
    }

    private String safeMessage(Exception exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    public record Connection(String connectionName, String bootstrapServers, String securityProtocol, String status) {}
    public record ConnectionCommand(String engineCode, String connectionName, String bootstrapServers,
                                   String securityProtocol, String status) {}
    public record TopicCreation(String topicName, boolean created) {}
}
