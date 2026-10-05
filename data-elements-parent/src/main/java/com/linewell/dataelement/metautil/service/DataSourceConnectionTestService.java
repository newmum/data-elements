package com.linewell.dataelement.metautil.service;

import com.linewell.dataelement.integration.nifi.canvas.controller.ConnectionTestController;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.metautil.jdbc.JdbcDriverPropertyResolver;
import com.linewell.dataelement.metautil.structured.StructuredSourceProbeService;
import com.linewell.dataelement.shared.error.ApiErrorDetails;
import com.linewell.dataelement.shared.error.ApiErrorResolver;
import com.linewell.dataelement.utils.MinioUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Java runtime adapter for datasource connectivity tests.
 *
 * <p>Magic APIs own orchestration and persistence. This service owns protocol
 * adaptation, connection configuration normalization and technical diagnostics.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceConnectionTestService {

    private static final int ENDPOINT_CONNECT_TIMEOUT_MILLIS = 10_000;

    private final MetadataExplorerService metadataExplorerService;
    private final ConnectionTestController connectionTestController;
    private final StructuredSourceProbeService structuredSourceProbeService;

    public Map<String, Object> test(Map<String, Object> source) {
        long startedAt = System.currentTimeMillis();
        if (source == null || source.isEmpty()) {
            return failure(
                    "数据源",
                    startedAt,
                    new IllegalArgumentException("请求参数不能为空")
            );
        }

        String sourceType = canonicalSourceType(
                first(source, "dbType", "databaseType", "dataSourceType"),
                source
        );
        String product = productName(sourceType);
        try {
            if (structuredSourceProbeService.supports(source)) {
                return structuredSourceProbeService.testAndProbe(source);
            }
            if (!blank(first(source, "manifestKey"))) {
                return normalizeControllerResult(
                        connectionTestController.test(source),
                        product,
                        startedAt
                );
            }
            if ("minio".equals(sourceType)) {
                return testMinio(source, startedAt);
            }
            if ("maxcompute".equals(sourceType)) {
                return testEndpoint(source, startedAt);
            }

            Map<String, Object> adapterRequest = buildAdapterRequest(sourceType, source);
            if (adapterRequest != null) {
                return normalizeControllerResult(
                        connectionTestController.test(adapterRequest),
                        product,
                        startedAt
                );
            }

            DataSourceConfig config = buildMetadataConfig(sourceType, source);
            boolean connected = metadataExplorerService.testConnection(config);
            if (!connected) {
                throw new IllegalStateException("目标服务未通过连通性校验");
            }
            return success(config.getDatabaseType().getDisplayName(), startedAt);
        } catch (Exception exception) {
            return failure(product, startedAt, exception);
        }
    }

    private Map<String, Object> testMinio(Map<String, Object> source, long startedAt) throws Exception {
        String endpoint = required(source, "MinIO 服务地址不能为空", "minioEndpoint");
        String accessKey = required(source, "MinIO Access Key 不能为空", "minioAccessKey");
        String secretKey = required(source, "MinIO Secret Key 不能为空", "minioSecretKey");
        String bucket = required(source, "MinIO 存储桶不能为空", "minioBucket");

        MinioUtils.getMinioClient(endpoint, accessKey, secretKey);
        if (!MinioUtils.bucketExists(bucket)) {
            throw new IllegalStateException("存储桶不存在或当前凭证无访问权限");
        }
        Map<String, Object> result = success("MinIO", startedAt);
        result.put("version", "存储桶可访问");
        return result;
    }

    private Map<String, Object> testEndpoint(Map<String, Object> source, long startedAt) throws Exception {
        String endpoint = required(source, "MaxCompute 服务端点不能为空", "maxcomputeEndpoint");
        String uriText = endpoint.contains("://") ? endpoint : "https://" + endpoint;
        URI uri = URI.create(uriText);
        if (blank(uri.getHost())) {
            throw new IllegalArgumentException("MaxCompute 服务端点格式不正确");
        }
        int port = uri.getPort() > 0
                ? uri.getPort()
                : ("http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443);
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(uri.getHost(), port),
                    ENDPOINT_CONNECT_TIMEOUT_MILLIS
            );
        }
        Map<String, Object> result = success("MaxCompute", startedAt);
        result.put("version", "服务端点可达");
        return result;
    }

    private Map<String, Object> buildAdapterRequest(
            String sourceType,
            Map<String, Object> source
    ) {
        Map<String, Object> config = new LinkedHashMap<>();
        String manifestKey;
        switch (sourceType) {
            case "kafka" -> {
                manifestKey = "source.kafka";
                put(config, "bootstrapServers", first(source, "kafkaBootstrapServers", "bootstrapServers"));
                put(config, "clientId", first(source, "kafkaClientId", "clientId"));
                put(config, "securityProtocol", defaultText(
                        first(source, "kafkaSecurityProtocol", "securityProtocol"),
                        "PLAINTEXT"
                ));
                put(config, "saslMechanism", defaultText(
                        first(source, "kafkaSaslMechanism", "saslMechanism"),
                        "PLAIN"
                ));
                put(config, "saslUsername", first(source, "kafkaUsername", "username"));
                put(config, "saslPassword", first(source, "kafkaPassword", "password"));
            }
            case "api" -> {
                manifestKey = "source.api";
                String authHeader = first(source, "authHeader", "authorization");
                String token = first(source, "apiToken", "token");
                if (blank(authHeader) && !blank(token)) {
                    authHeader = token.startsWith("Bearer ") ? token : "Bearer " + token;
                }
                put(config, "url", first(source, "apiUrl", "url"));
                put(config, "method", defaultText(first(source, "apiMethod", "method"), "GET"));
                put(config, "contentType", defaultText(first(source, "contentType"), "application/json"));
                put(config, "requestBody", first(source, "apiBody", "requestBody"));
                put(config, "authHeader", authHeader);
            }
            case "ftp" -> {
                manifestKey = "source.ftp";
                put(config, "protocol", defaultText(first(source, "ftpProtocol"), sourceType));
                put(config, "hostname", first(source, "ftpHost", "hostname", "host"));
                put(config, "port", defaultText(first(source, "ftpPort", "port"), "21"));
                put(config, "username", first(source, "ftpUsername", "username"));
                put(config, "password", first(source, "ftpPassword", "password"));
                put(config, "remotePath", defaultText(first(source, "ftpPath", "remotePath"), "/"));
                put(config, "passiveMode", first(source, "ftpPassiveMode", "passiveMode"));
            }
            case "elasticsearch" -> {
                manifestKey = "source.elasticsearch";
                put(config, "nodes", first(source, "nodes", "host", "apiUrl", "jdbcURL", "jdbcUrl"));
                put(config, "protocol", defaultText(first(source, "protocol"), "http"));
                put(config, "authMethod", defaultText(first(source, "authMethod"), "NONE"));
                put(config, "username", first(source, "username"));
                put(config, "password", first(source, "password"));
                put(config, "apiKey", first(source, "apiKey"));
            }
            case "hdfs" -> {
                // HDFS is a file system, not a Hive JDBC source. Delegate the
                // management-side check to the canvas adapter's NameNode TCP
                // reachability validation instead of trying to build a JDBC URL.
                manifestKey = "source.hdfs";
                put(config, "host", first(source, "hdfsHost", "host", "hostname", "nameNodeHost"));
                put(config, "port", defaultText(first(source, "hdfsPort", "port"), "8020"));
                put(config, "path", defaultText(first(source, "hdfsPath", "path", "remotePath"), "/"));
            }
            case "hbase" -> {
                // HBase is accessed through ZooKeeper/HBase client APIs. The
                // current shared adapter verifies ZooKeeper reachability only;
                // physical table discovery is intentionally not delegated to a
                // JDBC metadata explorer.
                manifestKey = "source.hbase";
                put(config, "zookeeperQuorum", first(source, "zookeeperQuorum", "zkQuorum", "host", "hostname"));
                put(config, "port", defaultText(first(source, "zookeeperPort", "port"), "2181"));
                put(config, "table", first(source, "hbaseTable", "table"));
            }
            case "hive" -> {
                String profile = first(source, "hiveProfile", "hive_profile");
                String accessMode = first(source, "metadataAccessMode", "metadata_access_mode");
                String hiveMode = first(source, "hiveConnectionMode", "hive_connection_mode");
                if (blank(profile)
                        && !"server-managed-mrs".equalsIgnoreCase(accessMode)
                        && !"huawei-mrs".equalsIgnoreCase(hiveMode)) {
                    return null;
                }
                manifestKey = "source.hive";
                put(config, "hiveProfile", defaultText(profile, "default"));
                put(config, "metadataAccessMode", "server-managed-mrs");
                put(config, "hiveConnectionMode", "huawei-mrs");
                put(config, "database", first(source, "database", "dbName", "dbMetaDbName"));
            }
            default -> {
                return null;
            }
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("manifestKey", manifestKey);
        request.put("config", config);
        return request;
    }

    private DataSourceConfig buildMetadataConfig(
            String sourceType,
            Map<String, Object> source
    ) {
        if (blank(sourceType)) {
            throw new IllegalArgumentException("数据源类型不能为空");
        }
        DatabaseType databaseType = DatabaseType.fromCode(sourceType);
        DataSourceConfig config = new DataSourceConfig();
        config.setDatabaseType(databaseType);
        String zookeeperQuorum = first(source, "zookeeperQuorum", "zkQuorum");
        config.setHost(metadataHost(databaseType, source, zookeeperQuorum));
        config.setPort(integer(first(source, "port", "dbPort"), databaseType.getDefaultPort()));
        config.setDatabase(required(
                source,
                "关系型数据源数据库名称不能为空",
                "database",
                "dbName",
                "dbMetaDbName",
                "schema",
                "defaultSchema",
                "currentSchema"
        ));
        config.setUsername(defaultText(
                first(source, "username", "dbUser", "dbMetaUser", "user"),
                ""
        ));
        config.setPassword(defaultText(
                first(source, "password", "dbPassword", "dbMetaPassword"),
                ""
        ));
        config.setJdbcUrl(first(source, "jdbcURL", "jdbcUrl", "jdbc_url"));
        config.setJdbcProperties(JdbcDriverPropertyResolver.resolve(source));
        config.setSchema(first(source, "schema", "defaultSchema", "currentSchema"));
        config.setAuthMode(first(source, "authMode", "auth"));
        config.setPrincipal(first(source, "principal", "hivePrincipal", "servicePrincipal"));
        config.setUserPrincipal(first(source, "userPrincipal", "clientPrincipal", "kerberosUserPrincipal"));
        config.setKeytabPath(first(source, "keytabPath", "keytab", "userKeytab"));
        config.setKrb5ConfPath(first(source, "krb5ConfPath", "krb5Conf", "krb5"));
        config.setJaasConfPath(first(source, "jaasConfPath", "jaasConfig"));
        config.setZookeeperQuorum(zookeeperQuorum);
        config.setZookeeperNamespace(first(source, "zookeeperNamespace", "zooKeeperNamespace"));
        config.setServiceDiscoveryMode(first(source, "serviceDiscoveryMode", "serviceDiscovery"));
        config.setSaslQop(first(source, "saslQop", "sasl.qop"));
        config.setSsl(bool(first(source, "ssl")));
        config.setZookeeperSsl(bool(first(source, "zookeeperSsl", "zkSsl", "zk.ssl.enable")));
        config.setClientConfigDir(first(source, "clientConfigDir", "mrsClientConfigDir"));
        config.setExtraParams(first(
                source,
                "extraParams",
                "connectParams",
                "jdbcParams"
        ));
        config.setConnectionTimeout(integer(first(source, "connectionTimeout"), null));
        return config;
    }

    /**
     * MRS Hive in ZooKeeper service-discovery mode does not expose a fixed
     * HiveServer2 host.  Its JDBC URL is built from the ZooKeeper quorum, so a
     * relational-host check would reject an otherwise complete connection
     * configuration before the Hive adapter is reached.
     */
    private String metadataHost(
            DatabaseType databaseType,
            Map<String, Object> source,
            String zookeeperQuorum
    ) {
        String host = first(source, "host", "dbIp");
        if (!blank(host)) {
            return host;
        }
        if (databaseType == DatabaseType.HIVE && !blank(zookeeperQuorum)) {
            return "";
        }
        throw new IllegalArgumentException("关系型数据源主机地址不能为空");
    }

    private Boolean bool(String value) {
        return blank(value) ? null : Boolean.parseBoolean(value);
    }

    private Map<String, Object> normalizeControllerResult(
            ResponseEntity<?> response,
            String product,
            long startedAt
    ) {
        Object body = response == null ? null : response.getBody();
        if (!(body instanceof Map<?, ?> source)) {
            throw new IllegalStateException("连接适配器未返回有效结果");
        }

        boolean success = Boolean.TRUE.equals(source.get("success"));
        if (!success) {
            String error = text(source.get("error"));
            throw new IllegalStateException(defaultText(error, "目标服务未通过连通性校验"));
        }

        Map<String, Object> result = success(
                defaultText(text(source.get("product")), product),
                startedAt
        );
        copy(source, result, "url", "version");
        Object elapsed = source.get("elapsedMs");
        if (elapsed != null) {
            result.put("elapsedMs", elapsed);
        }
        return result;
    }

    private Map<String, Object> success(String product, long startedAt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connected", true);
        result.put("success", true);
        result.put("product", product);
        result.put("elapsedMs", System.currentTimeMillis() - startedAt);
        return result;
    }

    private Map<String, Object> failure(String product, long startedAt, Throwable throwable) {
        ApiErrorDetails error = ApiErrorResolver.resolve(throwable);
        if ("MAGIC-RUNTIME-001".equals(error.code()) && !blank(throwable.getMessage())) {
            error = new ApiErrorDetails(
                    "CONNECTION-TEST-001",
                    throwable.getMessage(),
                    error.detail(),
                    error.traceId(),
                    error.type()
            );
        }
        log.error(
                "Datasource connection test failed, traceId={}, errorCode={}, product={}",
                error.traceId(),
                error.code(),
                product,
                throwable
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connected", false);
        result.put("success", false);
        result.put("product", product);
        result.put("elapsedMs", System.currentTimeMillis() - startedAt);
        result.put("error", error.message());
        result.put("errorCode", error.code());
        result.put("rawError", error.detail());
        result.put("traceId", error.traceId());
        result.put("errorDetail", error.toMap("POST", "/dst/database/metadata/test-connection"));
        return result;
    }

    private String productName(String sourceType) {
        if (blank(sourceType)) {
            return "数据源";
        }
        try {
            return DatabaseType.fromCode(sourceType).getDisplayName();
        } catch (IllegalArgumentException ignored) {
            return sourceType;
        }
    }

    private String required(Map<String, Object> source, String message, String... keys) {
        String value = first(source, keys);
        if (blank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String first(Map<String, Object> source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            String value = text(source.get(key));
            if (!blank(value)) {
                return value;
            }
        }
        return null;
    }

    private void put(Map<String, Object> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private void copy(Map<?, ?> source, Map<String, Object> target, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                target.put(key, value);
            }
        }
    }

    private Integer integer(String value, Integer fallback) {
        if (blank(value)) {
            return fallback;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("端口或超时参数格式不正确: " + value, exception);
        }
    }

    private String canonicalSourceType(String value, Map<String, Object> source) {
        if (blank(value)) {
            return "";
        }
        // The canvas exposes one OceanBase card but it can connect through
        // either compatibility protocol. Preserve that choice when source
        // management invokes its own connection test without a manifest key.
        if ("oceanbase".equalsIgnoreCase(value.trim())) {
            String compatibleMode = first(source, "compatibleMode", "mode", "databaseMode");
            return "ORACLE".equalsIgnoreCase(compatibleMode)
                    ? DatabaseType.OCEANBASE_ORACLE.getCode()
                    : DatabaseType.OCEANBASE_MYSQL.getCode();
        }
        try {
            return DatabaseType.fromCode(value).getCode();
        } catch (IllegalArgumentException ignored) {
            return value.trim().toLowerCase();
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String defaultText(String value, String fallback) {
        return blank(value) ? fallback : value;
    }
}
