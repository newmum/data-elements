package com.linewell.dataelement.platform.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

class SystemConfigServiceTest {

    private final Map<String, String> redisValues = new HashMap<>();

    private JdbcTemplate jdbcTemplate;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SystemConfigService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource());
        jdbcTemplate.execute("""
                create table sym_config_t (
                    tid varchar(32),
                    tenant_id varchar(32) not null,
                    config_group varchar(64) not null,
                    config_code varchar(128) not null,
                    config_name varchar(255),
                    config_value clob,
                    value_type varchar(16),
                    is_encrypt integer,
                    cache_ttl_seconds bigint,
                    config_scope varchar(16),
                    is_use integer,
                    is_del integer,
                    sort_no integer,
                    config_type varchar(32),
                    config_desc varchar(500),
                    json_data clob,
                    create_time timestamp,
                    update_time timestamp,
                    create_by varchar(64),
                    update_by varchar(64),
                    version_no bigint default 1
                )
                """);

        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString()))
                .thenAnswer(invocation -> redisValues.get(invocation.getArgument(0)));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            redisValues.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            redisValues.remove(invocation.getArgument(0));
            return true;
        }).when(redisTemplate).delete(anyString());

        SystemConfigProperties properties = new SystemConfigProperties();
        properties.setCacheTtl(Duration.ofMinutes(30));
        TenantDataSourceRegistry tenantDataSourceRegistry = mock(TenantDataSourceRegistry.class);
        when(tenantDataSourceRegistry.dataSourceForTenant(anyString()))
                .thenReturn(jdbcTemplate.getDataSource());
        service = new SystemConfigService(
                jdbcTemplate,
                redisTemplate,
                new ObjectMapper(),
                properties,
                tenantDataSourceRegistry
        );
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void readsDatabaseOnMissThenUsesRedis() {
        insert("GLOBAL", "PORTAL", "systemName", "源目录管理平台", "STRING", 0);
        insert("GLOBAL", "PORTAL", "needLogin", "true", "BOOLEAN", 0);
        insert("GLOBAL", "PORTAL", "clientSecret", "hidden", "STRING", 1);
        insertInternal("GLOBAL", "PORTAL", "internalOnly", "hidden", "STRING", 0);

        Map<String, Object> first = service.publicSettings();

        assertEquals("源目录管理平台", first.get("systemName"));
        assertEquals(true, first.get("needLogin"));
        assertFalse(first.containsKey("clientSecret"));
        assertFalse(first.containsKey("internalOnly"));
        assertFalse(redisValues.isEmpty());

        jdbcTemplate.update(
                "update sym_config_t set config_value = '数据库已改变' where config_code = 'systemName'"
        );
        Map<String, Object> second = service.publicSettings();

        assertEquals("源目录管理平台", second.get("systemName"));
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void overlaysTenantValuesAndKeepsGlobalDefaults() {
        insert("GLOBAL", "PORTAL", "systemName", "默认平台", "STRING", 0);
        insert("GLOBAL", "PORTAL", "version", "1.0.0", "STRING", 0);
        insert("tenant-a", "PORTAL", "systemName", "租户平台", "STRING", 0);

        Map<String, Object> values = TenantContext.call(
                "tenant-a",
                service::publicSettings
        );

        assertEquals("租户平台", values.get("systemName"));
        assertEquals("1.0.0", values.get("version"));
    }

    @Test
    void acceptsNullValuesAndInvalidatesBothScopes() {
        insert("GLOBAL", "PORTAL", "watermarkContent", null, "STRING", 0);

        Map<String, Object> values = service.publicSettings();
        assertTrue(values.containsKey("watermarkContent"));
        assertNull(values.get("watermarkContent"));

        service.evict("PORTAL");
        assertTrue(redisValues.isEmpty());
    }

    @Test
    void fallsBackToDatabaseWhenRedisReadFails() {
        insert("GLOBAL", "PORTAL", "systemCode", "公安", "STRING", 0);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(valueOperations)
                .get(anyString());

        Map<String, Object> values = service.publicSettings();

        assertEquals("公安", values.get("systemCode"));
    }

    @Test
    void saveEvictsCachedGroupAndReturnsUpdatedValue() {
        insert("GLOBAL", "PORTAL", "systemName", "before", "STRING", 0);
        assertEquals("before", service.publicSettings().get("systemName"));
        assertFalse(redisValues.isEmpty());

        service.save(
                Map.of(
                        "tenantId", "GLOBAL",
                        "configGroup", "PORTAL",
                        "configCode", "systemName",
                        "configName", "System name",
                        "configValue", "after",
                        "valueType", "STRING",
                        "configScope", "PUBLIC"
                ),
                "test"
        );

        assertTrue(redisValues.isEmpty());
        assertEquals("after", service.publicSettings().get("systemName"));
    }

    private void insert(
            String tenantId,
            String group,
            String code,
            String value,
            String type,
            int encrypted
    ) {
        insertWithScope(tenantId, group, code, value, type, encrypted, "PUBLIC");
    }

    private void insertInternal(
            String tenantId,
            String group,
            String code,
            String value,
            String type,
            int encrypted
    ) {
        insertWithScope(tenantId, group, code, value, type, encrypted, "INTERNAL");
    }

    private void insertWithScope(
            String tenantId,
            String group,
            String code,
            String value,
            String type,
            int encrypted,
            String scope
    ) {
        jdbcTemplate.update(
                """
                insert into sym_config_t (
                    tid, tenant_id, config_group, config_code, config_value,
                    value_type, is_encrypt, cache_ttl_seconds, config_scope,
                    is_use, is_del
                ) values (?, ?, ?, ?, ?, ?, ?, 1800, ?, 1, 0)
                """,
                tenantId + "-" + group + "-" + code,
                tenantId,
                group,
                code,
                value,
                type,
                encrypted,
                scope
        );
    }

    private DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:system-config-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1"
        );
        dataSource.setUser("sa");
        return dataSource;
    }
}
