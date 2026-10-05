package com.linewell.dataelement.integration.nifi.canvas.config;

import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Resolves the enabled default NiFi node for the current tenant.
 *
 * <p>NiFi endpoint and credentials are deliberately read from {@code nifi_node_t} rather than
 * from Nacos. Nacos keeps only application-wide monitor and runtime settings. This lets the
 * selected tenant operate its own network node without leaking its credentials to the browser.</p>
 */
@Component
public class NifiNodeRuntimeResolver {

    private final TenantDataSourceRegistry tenantDataSourceRegistry;
    private final TenantProperties tenantProperties;

    public NifiNodeRuntimeResolver(
            TenantDataSourceRegistry tenantDataSourceRegistry,
            TenantProperties tenantProperties
    ) {
        this.tenantDataSourceRegistry = tenantDataSourceRegistry;
        this.tenantProperties = tenantProperties;
    }

    public RuntimeNode resolve() {
        String tenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            tenantId = tenantProperties.getDefaultTenantId();
        }
        JdbcTemplate jdbc = new JdbcTemplate(tenantDataSourceRegistry.dataSourceForTenant(tenantId));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select tid, node_code, base_url, root_process_group_id,
                       auth_username, auth_password, insecure_tls, jdbc_driver_locations
                  from nifi_node_t
                 where tenant_id = ? and ifnull(is_del, 0) = 0 and ifnull(enabled, 1) = 1
                 order by ifnull(is_default, 0) desc, updated_time desc
                 limit 1
                """, tenantId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("当前租户未配置可用的默认 NiFi 节点，请在节点管理中启用并设置一个默认节点");
        }
        return toRuntimeNode(tenantId, rows.getFirst());
    }

    /** Resolves one saved node for a server-side connectivity check without accepting credentials from the browser. */
    public RuntimeNode resolveById(String nodeId) {
        String tenantId = TenantContext.getTenantId();
        if (!StringUtils.hasText(tenantId)) {
            tenantId = tenantProperties.getDefaultTenantId();
        }
        if (!StringUtils.hasText(nodeId)) {
            throw new IllegalArgumentException("请选择需要检测的数据同步节点");
        }
        JdbcTemplate jdbc = new JdbcTemplate(tenantDataSourceRegistry.dataSourceForTenant(tenantId));
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select tid, node_code, base_url, root_process_group_id,
                       auth_username, auth_password, insecure_tls, jdbc_driver_locations
                  from nifi_node_t
                 where tenant_id = ? and tid = ? and ifnull(is_del, 0) = 0
                 limit 1
                """, tenantId, nodeId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("数据同步节点不存在或不属于当前租户");
        }
        return toRuntimeNode(tenantId, rows.getFirst());
    }

    private RuntimeNode toRuntimeNode(String tenantId, Map<String, Object> row) {
        String nodeCode = text(row.get("node_code"));
        String baseUrl = trimTrailingSlash(text(row.get("base_url")));
        if (!StringUtils.hasText(nodeCode) || !StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("默认 NiFi 节点缺少节点编码或发布地址，请在节点管理中补全");
        }
        String username = text(row.get("auth_username"));
        String password = text(row.get("auth_password"));
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalStateException("默认 NiFi 节点缺少认证信息，请在节点管理中补全");
        }
        return new RuntimeNode(
                tenantId,
                text(row.get("tid")),
                nodeCode,
                baseUrl,
                username,
                password,
                Boolean.TRUE.equals(row.get("insecure_tls")) || "1".equals(text(row.get("insecure_tls"))),
                text(row.get("root_process_group_id")),
                text(row.get("jdbc_driver_locations"))
        );
    }

    public RestClient restClient(RuntimeNode node) {
        return RestClient.builder()
                .baseUrl(node.baseUrl())
                .requestFactory(NifiClientConfig.createRequestFactory(node.insecureTls()))
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
