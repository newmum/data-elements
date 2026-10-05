package com.linewell.dataelement.dataservice.flowserve;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.script.annotation.Comment;

/**
 * Resolves FlowServe runtime connections from the platform datasource registry.
 *
 * <p>{@code db_datasource_t} is the only datasource master table. FlowServe keeps no
 * separate datasource CRUD model; it only adapts registered platform connections to
 * Magic API runtime modules.</p>
 */
@Component
@MagicModule("flowDataSource")
public class FlowServeDataSourceRuntime {
    private static final Logger log = LoggerFactory.getLogger(FlowServeDataSourceRuntime.class);
    private static final String ACTIVE_SOURCE_SQL = """
            select tid, tenant_id, db_name, db_type, driver_class_name, jdbc_url,
                   username, password, updated_time
              from db_datasource_t
             where coalesce(is_del, 0) = 0
               and coalesce(is_enable, 1) = 1
               and coalesce(show_connect, 0) = 1
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final DataSourceConnectionPropertyResolver connectionProperties;
    private final MagicDynamicDataSource dynamicDataSource;
    private final TenantProperties tenantProperties;
    private final boolean preloadDataSources;
    private final Map<String, String> registeredFingerprints = new ConcurrentHashMap<>();

    public FlowServeDataSourceRuntime(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            DataSourceConnectionPropertyResolver connectionProperties,
            MagicDynamicDataSource dynamicDataSource,
            TenantProperties tenantProperties,
            @Value("${flowserve.runtime.preload-data-sources:false}") boolean preloadDataSources) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.connectionProperties = connectionProperties;
        this.dynamicDataSource = dynamicDataSource;
        this.tenantProperties = tenantProperties;
        this.preloadDataSources = preloadDataSources;
    }

    /** Returns the normalized connection configuration for a datasource ID or unique name. */
    @Comment("读取主平台已登记数据源的真实运行配置")
    public Map<String, Object> configByName(String reference) {
        return resolve(reference).config();
    }

    /**
     * Ensures a JDBC datasource is registered and returns its tenant-aware Magic key.
     * New compiled flows use datasource IDs, so duplicate display names cannot collide.
     */
    @Comment("确保主平台 JDBC 数据源已注册到 Magic 运行时")
    public String ensureRuntime(String reference) {
        ResolvedDataSource source = resolve(reference);
        if (!isJdbc(source)) {
            throw new IllegalArgumentException(
                    "数据源不是 JDBC 类型：" + source.name() + "（" + source.type() + "）");
        }
        registerKey(source, source.id());
        return runtimeName(source.id());
    }

    @Comment("获取当前租户的 Magic 运行时数据源名称")
    public String runtimeName(String reference) {
        String tenantId = tenantId();
        if (tenantProperties.getDefaultTenantId().equals(tenantId)) {
            return reference;
        }
        return tenantId + "__" + reference;
    }

    /** Registers current platform JDBC sources at startup for existing published scripts. */
    @EventListener(ApplicationReadyEvent.class)
    public void registerAll() {
        if (!preloadDataSources) {
            log.info("FlowServe datasource preloading is disabled; runtime connections will be registered on demand");
            return;
        }
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(ACTIVE_SOURCE_SQL);
        } catch (Exception ignored) {
            // The platform can still start while an installation is completing its schema migration.
            return;
        }

        Map<String, Integer> nameCounts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String tenantId = normalizedTenant(value(row, "tenant_id"));
            String name = text(value(row, "db_name"));
            if (!name.isBlank()) {
                nameCounts.merge(tenantId + "\u0000" + name, 1, Integer::sum);
            }
        }

        for (Map<String, Object> row : rows) {
            ResolvedDataSource source = fromRow(row);
            if (!isJdbc(source)) {
                continue;
            }
            TenantContext.run(source.tenantId(), () -> {
                try {
                    registerKey(source, source.id());
                    if (!source.name().isBlank()
                            && nameCounts.getOrDefault(source.tenantId() + "\u0000" + source.name(), 0) == 1) {
                        // Compatibility alias for scripts published before datasource IDs became canonical.
                        registerKey(source, source.name());
                    }
                } catch (Exception ignored) {
                    // One unavailable external driver or source must not block platform startup.
                }
            });
        }
    }

    private ResolvedDataSource resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("数据源不能为空");
        }
        String tenantId = tenantId();
        String sql = ACTIVE_SOURCE_SQL + """
               and (tenant_id = ? or (? = ? and tenant_id is null))
               and (tid = ? or db_name = ?)
            """;
        List<Map<String, Object>> rows = jdbc.queryForList(
                sql,
                tenantId,
                tenantId,
                tenantProperties.getDefaultTenantId(),
                reference,
                reference);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("数据源不存在、未启用或未提供连接信息：" + reference);
        }

        for (Map<String, Object> row : rows) {
            if (reference.equals(text(value(row, "tid")))) {
                return fromRow(row);
            }
        }
        if (rows.size() > 1) {
            throw new IllegalArgumentException("数据源名称不唯一，请在编排中重新选择数据源：" + reference);
        }
        return fromRow(rows.getFirst());
    }

    private ResolvedDataSource fromRow(Map<String, Object> row) {
        String id = text(value(row, "tid"));
        String name = text(value(row, "db_name"));
        String tenantId = normalizedTenant(value(row, "tenant_id"));
        String type = normalizeType(text(value(row, "db_type")), text(value(row, "jdbc_url")));

        Map<String, Object> config = new LinkedHashMap<>(connectionProperties.resolve(tenantId, id));
        putCanonical(config, "url", first(config, "url", "jdbcURL", "jdbcUrl", "apiUrl", "nodes"));
        putCanonical(config, "url", value(row, "jdbc_url"));
        putCanonical(config, "baseUrl", first(config, "baseUrl", "apiUrl", "url"));
        putCanonical(config, "username", first(config, "username", "dbUser", "apiUsername"));
        putCanonical(config, "username", value(row, "username"));
        putCanonical(config, "password", first(config, "password", "dbPassword", "apiPassword"));
        putCanonical(config, "password", value(row, "password"));
        putCanonical(config, "driverClassName", first(config, "driverClassName"));
        putCanonical(config, "driverClassName", value(row, "driver_class_name"));
        putCanonical(
                config,
                "bootstrapServers",
                first(config, "bootstrapServers", "kafkaBootstrapServers", "bootstrap.servers"));
        putCanonical(config, "bootstrap.servers", first(config, "bootstrap.servers", "bootstrapServers"));
        putCanonical(config, "nodes", first(config, "nodes", "url"));
        config.put("id", id);
        config.put("name", name);
        config.put("type", type);
        return new ResolvedDataSource(id, name, type, tenantId, config);
    }

    private void registerKey(ResolvedDataSource source, String key) {
        String url = text(source.config().get("url"));
        String driver = text(source.config().get("driverClassName"));
        if (driver.isBlank()) {
            driver = inferDriver(url);
        }
        String runtimeKey = runtimeName(key);
        String fingerprint = write(source.config()) + "|" + driver;
        if (fingerprint.equals(registeredFingerprints.get(runtimeKey))) {
            return;
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driver);
        dataSource.setUrl(url);
        dataSource.setUsername(text(source.config().get("username")));
        dataSource.setPassword(text(source.config().get("password")));
        dynamicDataSource.put(runtimeKey, dataSource);
        registeredFingerprints.put(runtimeKey, fingerprint);
    }

    private boolean isJdbc(ResolvedDataSource source) {
        return text(source.config().get("url")).toLowerCase(Locale.ROOT).startsWith("jdbc:");
    }

    private String write(Map<String, Object> value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("数据源配置序列化失败", exception);
        }
    }

    private Object value(Map<String, Object> row, String name) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Object first(Map<String, Object> values, String... names) {
        for (String name : names) {
            Object value = value(values, name);
            if (value != null && !text(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void putCanonical(Map<String, Object> config, String key, Object value) {
        if (value != null && !text(value).isBlank()) {
            config.put(key, value);
        }
    }

    private String normalizedTenant(Object tenantId) {
        String value = text(tenantId);
        return value.isBlank() ? tenantProperties.getDefaultTenantId() : value;
    }

    private String tenantId() {
        return normalizedTenant(TenantContext.getTenantId());
    }

    private String normalizeType(String type, String url) {
        if (!type.isBlank()) {
            return switch (type.toLowerCase(Locale.ROOT)) {
                case "elasticsearch" -> "es";
                case "api" -> "http";
                default -> type.toLowerCase(Locale.ROOT);
            };
        }
        return url.toLowerCase(Locale.ROOT).startsWith("jdbc:") ? "jdbc" : "other";
    }

    private String inferDriver(String url) {
        String value = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (value.startsWith("jdbc:postgresql:")) return "org.postgresql.Driver";
        if (value.startsWith("jdbc:oracle:")) return "oracle.jdbc.OracleDriver";
        if (value.startsWith("jdbc:sqlserver:")) return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        if (value.startsWith("jdbc:mariadb:")) return "org.mariadb.jdbc.Driver";
        if (value.startsWith("jdbc:clickhouse:")) return "com.clickhouse.jdbc.ClickHouseDriver";
        if (value.startsWith("jdbc:dm:")) return "dm.jdbc.driver.DmDriver";
        if (value.startsWith("jdbc:kingbase8:")) return "com.kingbase8.Driver";
        if (value.startsWith("jdbc:hive2:")) return "org.apache.hive.jdbc.HiveDriver";
        return "com.mysql.cj.jdbc.Driver";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ResolvedDataSource(
            String id, String name, String type, String tenantId, Map<String, Object> config) {}
}
