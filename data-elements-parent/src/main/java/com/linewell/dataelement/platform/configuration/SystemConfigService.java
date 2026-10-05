package com.linewell.dataelement.platform.configuration;

import com.linewell.dataelement.platform.persistence.id.NumericId;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.UseControlDataSource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Unified read-through cache for runtime system configuration.
 *
 * <p>Infrastructure required before the database is available, including the
 * datasource, Redis and Nacos connection settings, deliberately stays outside
 * this service.</p>
 */
@Service
@UseControlDataSource
public class SystemConfigService {

    public static final String PORTAL_GROUP = "PORTAL";
    public static final String RUNTIME_GROUP = "RUNTIME";

    private static final Logger log = LoggerFactory.getLogger(SystemConfigService.class);
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
            new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SystemConfigProperties properties;
    private final TenantDataSourceRegistry tenantDataSourceRegistry;
    private final ConcurrentMap<String, Object> loadLocks = new ConcurrentHashMap<>();

    public SystemConfigService(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            SystemConfigProperties properties,
            TenantDataSourceRegistry tenantDataSourceRegistry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.tenantDataSourceRegistry = tenantDataSourceRegistry;
    }

    public Map<String, Object> publicSettings() {
        return group(PORTAL_GROUP, false, true);
    }

    public Map<String, Object> publicSettings(String tenantId) {
        String normalizedTenantId = normalizeTenantId(tenantId);
        validateTenant(normalizedTenantId);
        return group(PORTAL_GROUP, false, true, normalizedTenantId);
    }

    public Map<String, Object> runtimeSettings() {
        Map<String, Object> result = new LinkedHashMap<>(publicSettings());
        result.putAll(group(RUNTIME_GROUP, false));
        return result;
    }

    public Map<String, Object> group(String group, boolean includeSensitive) {
        return group(group, includeSensitive, false);
    }

    private Map<String, Object> group(
            String group,
            boolean includeSensitive,
            boolean publicOnly
    ) {
        return group(group, includeSensitive, publicOnly, TenantContext.getTenantId());
    }

    private Map<String, Object> group(
            String group,
            boolean includeSensitive,
            boolean publicOnly,
            String tenantId
    ) {
        String normalizedGroup = normalizeGroup(group);
        Map<String, Object> result = new LinkedHashMap<>(
                loadScope(
                        properties.getGlobalTenantId(),
                        normalizedGroup,
                        includeSensitive,
                        publicOnly
                )
        );
        if (tenantId != null
                && !tenantId.isBlank()
                && !tenantId.equals(properties.getGlobalTenantId())) {
            result.putAll(loadScope(
                    tenantId,
                    normalizedGroup,
                    includeSensitive,
                    publicOnly,
                    tenantJdbcTemplate(tenantId)
            ));
        }
        return result;
    }

    public Object value(String group, String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return group(group, false).get(code.trim());
    }

    /**
     * Transactional mutation boundary for cache-backed configuration.
     */
    @Transactional
    public String save(Map<String, Object> body, String operator) {
        String tenantId = normalizeTenantId(
                text(body.get("tenantId"), properties.getGlobalTenantId())
        );
        String group = normalizeGroup(text(body.get("configGroup"), null));
        String code = normalizeCode(text(body.get("configCode"), null));
        String valueType = normalizeEnum(
                text(body.get("valueType"), "STRING"),
                "valueType",
                List.of("STRING", "BOOLEAN", "INTEGER", "LONG", "DECIMAL", "JSON")
        );
        String scope = normalizeEnum(
                text(body.get("configScope"), "INTERNAL"),
                "configScope",
                List.of("PUBLIC", "INTERNAL")
        );
        String name = text(body.get("configName"), code);
        String description = text(body.get("configDesc"), null);
        String value = serializeValue(body.get("configValue"), valueType);
        int enabled = integer(body.get("isUse"), 1);
        int encrypted = integer(body.get("isEncrypt"), 0);
        int sortNo = integer(body.get("sortNo"), 0);
        int ttlSeconds = integer(body.get("cacheTtlSeconds"), 1800);
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("cacheTtlSeconds must be positive");
        }
        validateTenant(tenantId);
        JdbcTemplate targetTemplate = configJdbcTemplate(tenantId);

        List<Map<String, Object>> existing = targetTemplate.queryForList(
                """
                select tid, version_no
                  from sym_config_t
                 where tenant_id = ?
                   and config_group = ?
                   and config_code = ?
                   and (is_del = 0 or is_del is null)
                """,
                tenantId,
                group,
                code
        );
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        String id;
        if (existing.isEmpty()) {
            id = NumericId.nextId();
            targetTemplate.update(
                    """
                    insert into sym_config_t(
                        tid, config_code, config_name, config_value, is_use,
                        sort_no, config_type, config_desc, is_encrypt, json_data,
                        create_time, update_time, create_by, update_by, is_del,
                        tenant_id, config_group, value_type, config_scope,
                        cache_ttl_seconds, version_no
                    ) values (?, ?, ?, ?, ?, ?, null, ?, ?, null, ?, ?, ?, ?, 0,
                              ?, ?, ?, ?, ?, 1)
                    """,
                    id, code, name, value, enabled, sortNo, description, encrypted,
                    now, now, operator, operator, tenantId, group, valueType, scope,
                    ttlSeconds
            );
        } else {
            Map<String, Object> row = existing.get(0);
            id = String.valueOf(row.get("tid"));
            long currentVersion = ((Number) row.get("version_no")).longValue();
            Object submittedVersion = body.get("versionNo");
            if (submittedVersion != null
                    && longValue(submittedVersion, currentVersion) != currentVersion) {
                throw new IllegalStateException("System config was modified by another request");
            }
            int updated = targetTemplate.update(
                    """
                    update sym_config_t
                       set config_name = ?, config_value = ?, is_use = ?,
                           sort_no = ?, config_desc = ?, is_encrypt = ?,
                           update_time = ?, update_by = ?, is_del = 0,
                           value_type = ?, config_scope = ?,
                           cache_ttl_seconds = ?, version_no = version_no + 1
                     where tid = ? and version_no = ?
                    """,
                    name, value, enabled, sortNo, description, encrypted,
                    now, operator, valueType, scope, ttlSeconds, id, currentVersion
            );
            if (updated != 1) {
                throw new IllegalStateException("System config was modified by another request");
            }
        }
        evictAfterCommit(tenantId, group);
        return id;
    }

    @Transactional
    public boolean delete(Map<String, Object> body, String operator) {
        String tenantId = normalizeTenantId(
                text(body.get("tenantId"), properties.getGlobalTenantId())
        );
        String group = normalizeGroup(text(body.get("configGroup"), null));
        String code = normalizeCode(text(body.get("configCode"), null));
        validateTenant(tenantId);
        int updated = configJdbcTemplate(tenantId).update(
                """
                update sym_config_t
                   set is_del = 1, is_use = 0, update_time = ?,
                       update_by = ?, version_no = version_no + 1
                 where tenant_id = ? and config_group = ? and config_code = ?
                   and (is_del = 0 or is_del is null)
                """,
                Timestamp.valueOf(LocalDateTime.now()),
                operator,
                tenantId,
                group,
                code
        );
        if (updated > 0) {
            evictAfterCommit(tenantId, group);
        }
        return updated > 0;
    }

    public void evict(String group) {
        String normalizedGroup = normalizeGroup(group);
        evictScope(properties.getGlobalTenantId(), normalizedGroup);
        String tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            evictScope(tenantId, normalizedGroup);
        }
    }

    public void evict(String tenantId, String group) {
        evictScope(normalizeTenantId(tenantId), normalizeGroup(group));
    }

    private void evictScope(String tenantId, String group) {
        deleteCache(tenantId, group, false);
        deleteCache(tenantId, group, true);
        deleteCache(tenantId, group, false, true);
        deleteCache(tenantId, group, true, true);
    }

    private void evictAfterCommit(String tenantId, String group) {
        Runnable eviction = () -> evictScope(tenantId, group);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eviction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eviction.run();
                    }
                }
        );
    }

    private Map<String, Object> loadScope(
            String tenantId,
            String group,
            boolean includeSensitive,
            boolean publicOnly
    ) {
        return loadScope(tenantId, group, includeSensitive, publicOnly, jdbcTemplate);
    }

    private Map<String, Object> loadScope(
            String tenantId,
            String group,
            boolean includeSensitive,
            boolean publicOnly,
            JdbcTemplate sourceTemplate
    ) {
        String cacheKey = cacheKey(tenantId, group, includeSensitive, publicOnly);
        Map<String, Object> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        Object lock = loadLocks.computeIfAbsent(cacheKey, ignored -> new Object());
        try {
            synchronized (lock) {
                cached = readCache(cacheKey);
                if (cached != null) {
                    return cached;
                }
                LoadedGroup loaded = loadDatabase(
                        tenantId,
                        group,
                        includeSensitive,
                        publicOnly,
                        sourceTemplate
                );
                writeCache(cacheKey, loaded.values(), loaded.ttl());
                return loaded.values();
            }
        } finally {
            loadLocks.remove(cacheKey, lock);
        }
    }

    private LoadedGroup loadDatabase(
            String tenantId,
            String group,
            boolean includeSensitive,
            boolean publicOnly,
            JdbcTemplate sourceTemplate
    ) {
        String sql = """
                select config_code, config_value, value_type, is_encrypt,
                       cache_ttl_seconds, config_scope
                  from sym_config_t
                 where tenant_id = ?
                   and config_group = ?
                   and is_use = 1
                   and (is_del = 0 or is_del is null)
                """;
        List<ConfigRow> rows = sourceTemplate.query(
                sql,
                statement -> {
                    statement.setString(1, tenantId);
                    statement.setString(2, group);
                },
                (ResultSet resultSet) -> {
                    List<ConfigRow> items = new ArrayList<>();
                    while (resultSet.next()) {
                        items.add(new ConfigRow(
                                resultSet.getString(1),
                                readText(resultSet, 2),
                                resultSet.getString(3),
                                resultSet.getObject(4) != null && resultSet.getInt(4) == 1,
                                resultSet.getObject(5) == null ? null : resultSet.getLong(5),
                                resultSet.getString(6)
                        ));
                    }
                    return items;
                }
        );

        Map<String, Object> values = new LinkedHashMap<>();
        long ttlSeconds = positiveSeconds(properties.getCacheTtl());
        for (ConfigRow row : rows) {
            if (row.code() == null || row.code().isBlank()) {
                continue;
            }
            if (row.sensitive() && !includeSensitive) {
                continue;
            }
            if (publicOnly && !"PUBLIC".equalsIgnoreCase(row.scope())) {
                continue;
            }
            values.put(row.code(), convert(row.value(), row.valueType()));
            if (row.cacheTtlSeconds() != null && row.cacheTtlSeconds() > 0) {
                ttlSeconds = Math.min(ttlSeconds, row.cacheTtlSeconds());
            }
        }
        return new LoadedGroup(
                Collections.unmodifiableMap(new LinkedHashMap<>(values)),
                Duration.ofSeconds(ttlSeconds)
        );
    }

    private Object convert(String value, String valueType) {
        if (value == null) {
            return null;
        }
        String type = valueType == null
                ? "STRING"
                : valueType.trim().toUpperCase(Locale.ROOT);
        try {
            return switch (type) {
                case "BOOLEAN" -> parseBoolean(value);
                case "INTEGER" -> Integer.valueOf(value.trim());
                case "LONG" -> Long.valueOf(value.trim());
                case "DECIMAL" -> new BigDecimal(value.trim());
                case "JSON" -> objectMapper.readValue(value, Object.class);
                default -> value;
            };
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Invalid system config value for type " + type,
                    exception
            );
        }
    }

    private Boolean parseBoolean(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> throw new IllegalArgumentException("Unsupported boolean value: " + value);
        };
    }

    private Map<String, Object> readCache(String cacheKey) {
        if (!properties.isCacheEnabled()) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            log.warn("Read system config cache failed, key={}, fallback to database: {}",
                    cacheKey, exception.getMessage());
            return null;
        }
    }

    private void writeCache(String cacheKey, Map<String, Object> values, Duration ttl) {
        if (!properties.isCacheEnabled()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(values),
                    ttl
            );
        } catch (Exception exception) {
            log.warn("Write system config cache failed, key={}, continue without cache: {}",
                    cacheKey, exception.getMessage());
        }
    }

    private void deleteCache(
            String tenantId,
            String group,
            boolean includeSensitive
    ) {
        deleteCache(tenantId, group, includeSensitive, false);
    }

    private void deleteCache(
            String tenantId,
            String group,
            boolean includeSensitive,
            boolean publicOnly
    ) {
        if (!properties.isCacheEnabled()) {
            return;
        }
        String key = cacheKey(tenantId, group, includeSensitive, publicOnly);
        try {
            redisTemplate.delete(key);
        } catch (Exception exception) {
            log.warn("Delete system config cache failed, key={}: {}", key, exception.getMessage());
        }
    }

    private String cacheKey(
            String tenantId,
            String group,
            boolean includeSensitive,
            boolean publicOnly
    ) {
        return String.join(
                ":",
                properties.getRedisKeyPrefix(),
                tenantId,
                group.toLowerCase(Locale.ROOT),
                publicOnly ? "public-scope" : "all-scopes",
                includeSensitive ? "internal" : "public"
        );
    }

    private String normalizeGroup(String value) {
        String group = Objects.requireNonNullElse(value, "").trim().toUpperCase(Locale.ROOT);
        if (!group.matches("[A-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid system config group");
        }
        return group;
    }

    private String normalizeTenantId(String value) {
        String tenantId = Objects.requireNonNullElse(value, "").trim();
        if (!tenantId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid tenant id");
        }
        return tenantId;
    }

    private String normalizeCode(String value) {
        String code = Objects.requireNonNullElse(value, "").trim();
        if (!code.matches("[A-Za-z0-9_.-]{1,128}")) {
            throw new IllegalArgumentException("Invalid system config code");
        }
        return code;
    }

    private String normalizeEnum(String value, String field, List<String> allowed) {
        String normalized = Objects.requireNonNullElse(value, "")
                .trim()
                .toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return normalized;
    }

    private void validateTenant(String tenantId) {
        if (properties.getGlobalTenantId().equals(tenantId)) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(1) from sym_tenant_t
                 where tid = ? and status = 1 and is_del = 0
                """,
                Integer.class,
                tenantId
        );
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Tenant is not active");
        }
    }

    private JdbcTemplate configJdbcTemplate(String tenantId) {
        return properties.getGlobalTenantId().equals(tenantId)
                ? jdbcTemplate
                : tenantJdbcTemplate(tenantId);
    }

    private JdbcTemplate tenantJdbcTemplate(String tenantId) {
        return new JdbcTemplate(tenantDataSourceRegistry.dataSourceForTenant(tenantId));
    }

    private String serializeValue(Object value, String valueType) {
        if (value == null) {
            return null;
        }
        if ("JSON".equals(valueType) && !(value instanceof String)) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception exception) {
                throw new IllegalArgumentException("Invalid JSON config value", exception);
            }
        }
        return String.valueOf(value);
    }

    private String text(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }

    private int integer(Object value, int fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private long longValue(Object value, long fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private long positiveSeconds(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return Duration.ofMinutes(30).toSeconds();
        }
        return Math.max(1L, duration.toSeconds());
    }

    private String readText(ResultSet resultSet, int column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (value instanceof Clob clob) {
            return clob.getSubString(1, Math.toIntExact(clob.length()));
        }
        if (value instanceof Blob blob) {
            try (InputStream input = blob.getBinaryStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception exception) {
                throw new SQLException("Failed to read system config blob", exception);
            }
        }
        return String.valueOf(value);
    }

    private record ConfigRow(
            String code,
            String value,
            String valueType,
            boolean sensitive,
            Long cacheTtlSeconds,
            String scope
    ) {
    }

    private record LoadedGroup(Map<String, Object> values, Duration ttl) {
    }
}
