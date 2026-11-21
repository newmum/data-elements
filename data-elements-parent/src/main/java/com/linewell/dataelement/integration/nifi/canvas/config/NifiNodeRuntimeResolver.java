package com.linewell.dataelement.integration.nifi.canvas.config;

import com.linewell.dataelement.magic.runtime.route.RegisteredMagicFunctionInvoker;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

/**
 * Resolves the enabled default NiFi node for the current tenant.
 *
 * <p>NiFi endpoint and credentials are deliberately read from {@code nifi_node_t} rather than
 * from Nacos. Nacos keeps only application-wide monitor and runtime settings. This lets the
 * selected tenant operate its own network node without leaking its credentials to the browser.</p>
 */
@Component
public class NifiNodeRuntimeResolver {

    private static final String NODE_CONFIGURATION = "nifi_runtime_node_read_v1";
    private final RegisteredMagicFunctionInvoker magic;
    private final TenantProperties tenantProperties;

    public NifiNodeRuntimeResolver(
            @Lazy RegisteredMagicFunctionInvoker magic,
            TenantProperties tenantProperties
    ) {
        this.magic = magic;
        this.tenantProperties = tenantProperties;
    }

    public RuntimeNode resolve() {
        String tenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            // The native NiFi proxy has no industry session. Its configured default is the protocol binding.
            return TenantContext.call(tenantProperties.getDefaultTenantId(), this::defaultNode);
        }
        return defaultNode();
    }

    private RuntimeNode defaultNode() {
        Map<String, Object> row = readConfiguration(null);
        if (row == null) {
            throw new NodeNotConfiguredException();
        }
        return runtimeNode(row).requireValidConnection();
    }

    /** A new tenant can browse its access tasks before connecting a NiFi node. */
    public static final class NodeNotConfiguredException extends IllegalStateException {
        public NodeNotConfiguredException() {
            super("当前租户尚未配置 NiFi 节点，请在节点管理中配置并启用后查看实时状态");
        }
    }

    public RuntimeNode resolveAssignedNode(String nodeId) {
        if (!StringUtils.hasText(TenantContext.getTenantId()) || TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
            throw new IllegalStateException("接入节点必须在当前行业租户中解析");
        }
        Map<String, Object> row = StringUtils.hasText(nodeId) ? readConfiguration(nodeId) : null;
        if (row == null) {
            throw new IllegalStateException("任务绑定的数据接入节点不存在或不属于当前租户",
                    new IllegalArgumentException("数据同步节点不存在或不属于当前租户"));
        }
        if (!Boolean.TRUE.equals(row.get("enabled")) && !"1".equals(text(row.get("enabled")))) {
            throw new IllegalStateException("任务绑定的数据接入节点已停用，请在节点管理中启用该节点");
        }
        return runtimeNode(row).requireValidConnection();
    }

    private Map<String, Object> readConfiguration(String nodeId) {
        var inputs = new LinkedHashMap<String, Object>();
        inputs.put("nodeId", nodeId);
        inputs.put("consume", (MagicScriptLambdaFunction) (context, arguments) -> arguments[0]);
        return magic.invoke(NODE_CONFIGURATION, inputs);
    }

    /** Converts a trusted configuration row into the protocol client input. */
    public RuntimeNode runtimeNode(Map<String, Object> row) {
        return new RuntimeNode(
                TenantContext.requireTenantId(),
                text(row.get("tid")),
                text(row.get("node_code")),
                trimTrailingSlash(text(row.get("base_url"))),
                text(row.get("auth_username")),
                text(row.get("auth_password")),
                Boolean.TRUE.equals(row.get("insecure_tls")) || "1".equals(text(row.get("insecure_tls"))),
                text(row.get("root_process_group_id")),
                text(row.get("jdbc_driver_locations"))
        );
    }

    public RestClient restClient(RuntimeNode node) {
        return RestClient.builder()
                .baseUrl(node.baseUrl())
                .requestFactory(NifiClientConfig.createRequestFactory(node.skipTlsVerification()))
                .build();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    public record RuntimeNode(
            String tenantId,
            String id,
            String code,
            String baseUrl,
            String username,
            String password,
            boolean insecureTls,
            String rootProcessGroupId,
            String jdbcDriverLocations
    ) {
        /** Compatibility constructor for callers that do not need node JDBC mappings. */
        public RuntimeNode(String tenantId, String id, String code, String baseUrl, String username,
                           String password, boolean insecureTls, String rootProcessGroupId) {
            this(tenantId, id, code, baseUrl, username, password, insecureTls, rootProcessGroupId, "");
        }

        public String tokenCacheKey() {
            return tenantId + ":" + id + ":" + baseUrl;
        }

        public RuntimeNode requireValidConnection() {
            if (!StringUtils.hasText(code) || !StringUtils.hasText(baseUrl)) {
                throw new IllegalStateException("NiFi 节点缺少节点编码或发布地址，请在节点管理中补全");
            }
            if (!isAnonymousHttp() && (!StringUtils.hasText(username) || !StringUtils.hasText(password))) {
                throw new IllegalStateException("NiFi 节点缺少认证信息，仅 HTTP 节点可同时留空用户名和密码");
            }
            return this;
        }

        public boolean isAnonymousHttp() {
            return baseUrl.startsWith("http://") && !StringUtils.hasText(username) && !StringUtils.hasText(password);
        }

        public boolean skipTlsVerification() {
            return insecureTls && baseUrl.startsWith("https://");
        }

        /**
         * Returns the node-local JDBC driver JAR path for the requested database type.
         * Values may be a single path or DB_TYPE=/path mappings; non-Hive DBCP services must
         * receive a complete JAR path because directory discovery differs across NiFi/Leefy
         * versions and class-loader configurations.
         */
        public String jdbcDriverLocation(String dbType) {
            if (!StringUtils.hasText(jdbcDriverLocations)) {
                return "";
            }
            String configured = jdbcDriverLocations.trim();
            if (!configured.contains("=")) {
                return configured;
            }
            String expected = normalizeDbType(dbType);
            String fallback = "";
            for (String line : jdbcDriverLocations.split("[\\r\\n;]+")) {
                int separator = line.indexOf('=');
                if (separator < 1) {
                    continue;
                }
                String key = normalizeDbType(line.substring(0, separator));
                String value = line.substring(separator + 1).trim();
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                if (expected.equals(key)) {
                    return value;
                }
                if ("DEFAULT".equals(key) || "*".equals(key)) {
                    fallback = value;
                }
            }
            return fallback;
        }

        private static String normalizeDbType(String value) {
            String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT)
                    .replace('-', '_').replace(' ', '_');
            return switch (normalized) {
                case "DAMENG" -> "DM";
                case "POSTGRES", "POSTGRESQL", "TDSQL_PG" -> "POSTGRESQL";
                case "OPENGAUSS" -> "GAUSSDB";
                case "MSSQL", "MS_SQL" -> "SQLSERVER";
                case "OCEANBASEMYSQL" -> "OCEANBASE_MYSQL";
                case "OCEANBASEORACLE" -> "OCEANBASE_ORACLE";
                default -> normalized;
            };
        }
    }
}
