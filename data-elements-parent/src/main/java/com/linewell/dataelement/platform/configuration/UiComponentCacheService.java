package com.linewell.dataelement.platform.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.UseControlDataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-through cache for the active low-code component bundle.
 */
@Service
@UseControlDataSource
public class UiComponentCacheService {

    private static final Logger log = LoggerFactory.getLogger(UiComponentCacheService.class);
    private static final TypeReference<List<LinkedHashMap<String, Object>>> LIST_TYPE =
            new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SystemConfigProperties properties;
    private final Object loadLock = new Object();
    private volatile List<Map<String, Object>> localCache;
    private volatile String localCacheVersion;

    @Value("${data-element.reconciliation.role:CONTROL}")
    private String runtimeRole = "CONTROL";

    public UiComponentCacheService(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            SystemConfigProperties properties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<Map<String, Object>> list() {
        String expectedVersion = cacheVersion();
        List<Map<String, Object>> local = localCache;
        if (local != null && Objects.equals(localCacheVersion, expectedVersion)) {
            return local;
        }
        List<Map<String, Object>> cached = readCache(expectedVersion);
        if (cached != null) {
            localCache = cached;
            localCacheVersion = expectedVersion;
            return cached;
        }
        synchronized (loadLock) {
            expectedVersion = cacheVersion();
            local = localCache;
            if (local != null && Objects.equals(localCacheVersion, expectedVersion)) {
                return local;
            }
            cached = readCache(expectedVersion);
            if (cached != null) {
                localCache = cached;
                localCacheVersion = expectedVersion;
                return cached;
            }
            List<Map<String, Object>> loaded = loadDatabase();
            writeCache(expectedVersion, loaded);
            localCache = loaded;
            localCacheVersion = expectedVersion;
            return loaded;
        }
    }

    public void evict() {
        invalidate();
    }

    /**
     * Invalidates the shared component package and changes its Redis version.
     * Every application instance compares that tiny version value before serving
     * its in-memory copy, so a direct control-database publish does not require
     * a process restart.
     */
    public Map<String, Object> refresh() {
        String version = invalidate();
        List<Map<String, Object>> components = list();
        return Map.of("version", version, "count", components.size());
    }

    private String invalidate() {
        synchronized (loadLock) {
            localCache = null;
            localCacheVersion = null;
            String version = nextCacheVersion();
            deleteBundle();
            return version;
        }
    }

    private void deleteBundle() {
        if (!sharedCacheEnabled()) {
            return;
        }
        try {
            redisTemplate.delete(properties.getUiComponentRedisKey());
        } catch (Exception exception) {
            log.warn("Delete UI component cache failed: {}", exception.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!sharedCacheEnabled()) {
            return;
        }
        try {
            long startedAt = System.currentTimeMillis();
            // Deployment tooling may publish a component directly to the
            // control database. On a restart, do not resurrect a stale Redis
            // bundle: use the same versioned invalidation path as the explicit
            // “refresh component cache” operation before warming this instance.
            int count = ((Number) refresh().get("count")).intValue();
            log.info("UI component cache warmed: count={}, elapsedMs={}",
                    count, System.currentTimeMillis() - startedAt);
        } catch (Exception exception) {
            log.warn("Warm UI component cache failed; first request will retry: {}",
                    exception.getMessage());
        }
    }

    private List<Map<String, Object>> loadDatabase() {
        String sql = """
                select name, compile_js, compile_css
                  from ui_component_t
                 where is_del = 0
                   and compile_js is not null
                   and type <> '2'
                """;
        return jdbcTemplate.query(sql, (ResultSet resultSet) -> {
            List<Map<String, Object>> items = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", resultSet.getString(1));
                item.put("compileJs", readText(resultSet, 2));
                item.put("compileCss", readText(resultSet, 3));
                items.add(Collections.unmodifiableMap(item));
            }
            return Collections.unmodifiableList(items);
        });
    }

    private List<Map<String, Object>> readCache(String expectedVersion) {
        if (!sharedCacheEnabled()) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(properties.getUiComponentRedisKey());
            if (json == null) {
                return null;
            }
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()
                    || !expectedVersion.equals(root.path("version").asText())
                    || !root.path("components").isArray()) {
                return null;
            }
            List<LinkedHashMap<String, Object>> parsed = objectMapper.convertValue(
                    root.path("components"), LIST_TYPE);
            return new ArrayList<>(parsed);
        } catch (Exception exception) {
            log.warn("Read UI component cache failed, fallback to database: {}",
                    exception.getMessage());
            return null;
        }
    }

    private void writeCache(String version, List<Map<String, Object>> values) {
        if (!properties.isUiComponentCacheEnabled()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    properties.getUiComponentRedisKey(),
                    objectMapper.writeValueAsString(Map.of(
                            "version", version,
                            "components", values
                    )),
                    positive(properties.getUiComponentCacheTtl())
            );
        } catch (Exception exception) {
            log.warn("Write UI component cache failed, continue without cache: {}",
                    exception.getMessage());
        }
    }

    private String cacheVersion() {
        if (!sharedCacheEnabled()) {
            return "local";
        }
        try {
            String key = properties.getUiComponentVersionRedisKey();
            String version = redisTemplate.opsForValue().get(key);
            if (version != null && !version.isBlank()) {
                return version;
            }
            redisTemplate.opsForValue().setIfAbsent(key, "1");
            return Objects.requireNonNullElse(redisTemplate.opsForValue().get(key), "1");
        } catch (Exception exception) {
            log.warn("Read UI component cache version failed, keep local version: {}", exception.getMessage());
            return Objects.requireNonNullElse(localCacheVersion, "local");
        }
    }

    private String nextCacheVersion() {
        if (!sharedCacheEnabled()) {
            return "local";
        }
        try {
            Long version = redisTemplate.opsForValue().increment(properties.getUiComponentVersionRedisKey());
            return version == null ? cacheVersion() : String.valueOf(version);
        } catch (Exception exception) {
            log.warn("Advance UI component cache version failed: {}", exception.getMessage());
            return cacheVersion();
        }
    }

    private Duration positive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            return Duration.ofMinutes(15);
        }
        return value;
    }

    private boolean sharedCacheEnabled() {
        // A missing or non-worker role is the control/API process. This keeps the
        // shared cache available in deployments that do not set the optional role.
        return properties.isUiComponentCacheEnabled()
                && !"WORKER".equalsIgnoreCase(Objects.toString(runtimeRole, "").trim());
    }

    private String readText(ResultSet resultSet, int column) throws SQLException {
        Object value = resultSet.getObject(column);
        if (value == null) {
            return "";
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
                throw new SQLException("Failed to read UI component blob", exception);
            }
        }
        return String.valueOf(value);
    }
}
