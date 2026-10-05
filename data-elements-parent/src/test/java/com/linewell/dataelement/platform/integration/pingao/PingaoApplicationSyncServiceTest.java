package com.linewell.dataelement.platform.integration.pingao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

class PingaoApplicationSyncServiceTest {

    private static final String TENANT = "tenant-a";

    private JdbcTemplate jdbcTemplate;
    private AtomicReference<List<PingaoAbility>> remote;
    private AtomicReference<PingaoApplicationGateway> gateway;
    private PingaoApplicationRepository repository;
    private PingaoApplicationSyncService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:pingao-application-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                create table sym_application_t (
                    tid varchar(32) primary key,
                    app_name varchar(200),
                    is_del integer,
                    tenant_id varchar(64),
                    data_origin varchar(16),
                    source_key varchar(64),
                    sync_active integer,
                    last_synced_at timestamp,
                    unique (tenant_id, data_origin, source_key)
                )
                """);
        jdbcTemplate.execute("""
                create table db_datasource_t (
                    tid varchar(32), app_id varchar(32), tenant_id varchar(64), is_del integer
                )
                """);
        jdbcTemplate.execute("""
                create table pingao_application_sync_state_t (
                    tenant_id varchar(64) primary key,
                    has_success integer not null,
                    last_status varchar(16) not null,
                    last_attempt_at timestamp,
                    last_success_at timestamp,
                    last_fetched_count bigint not null,
                    last_online_count bigint not null,
                    updated_time timestamp not null
                )
                """);

        PingaoApplicationProperties properties = new PingaoApplicationProperties();
        properties.setEnabled(true);
        properties.setTokenUrl("https://token.example.test");
        properties.setApplicationUrl("https://application.example.test/fjrbAbility");
        properties.setClientId("client");
        properties.setClientSecret("secret");

        remote = new AtomicReference<>(List.of(
                new PingaoAbility("ability-1", "品高应用一", "1"),
                new PingaoAbility("ability-2", "品高应用二", "0")));
        gateway = new AtomicReference<>(() -> remote.get());

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        repository = new PingaoApplicationRepository(jdbcTemplate);
        service = new PingaoApplicationSyncService(properties, () -> gateway.get().listApplications(), repository, redisTemplate);
    }

    @Test
    void syncSeparatesPingaoRecordsAndOnlyReturnsOnlineOptions() {
        Map<String, Object> result = service.synchronize(TENANT);

        assertEquals(2, result.get("fetched"));
        assertEquals(1, TenantContext.call(TENANT, service::selectableOptions).size());
        assertEquals("品高应用一", TenantContext.call(TENANT, service::selectableOptions).getFirst().get("label"));
        assertEquals(2, jdbcTemplate.queryForObject(
                "select count(*) from sym_application_t where tenant_id=? and data_origin='PINGAO'", Integer.class, TENANT));

        remote.set(List.of(new PingaoAbility("ability-2", "改名后的品高应用", "1")));
        service.synchronize(TENANT);

        assertEquals(1, TenantContext.call(TENANT, service::selectableOptions).size());
        assertEquals("改名后的品高应用", TenantContext.call(TENANT, service::selectableOptions).getFirst().get("label"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select sync_active from sym_application_t where source_key='ability-1'", Integer.class));
        assertEquals("SUCCESS", TenantContext.call(TENANT, service::syncState).lastStatus());
    }

    @Test
    void registrationFallsBackToMarkedLegacyOnlyBeforeFirstSuccessfulSync() {
        jdbcTemplate.update("""
                insert into sym_application_t
                    (tid, app_name, is_del, tenant_id, data_origin, sync_active)
                values ('legacy-option', '品高应用一', 0, ?, 'LEGACY', 0)
                """, TENANT);

        List<Map<String, Object>> beforeSync = TenantContext.call(TENANT, service::selectableOptions);
        assertEquals(1, beforeSync.size());
        assertEquals("品高应用一（历史）", beforeSync.getFirst().get("label"));

        service.synchronize(TENANT);

        List<Map<String, Object>> afterSync = TenantContext.call(TENANT, service::selectableOptions);
        assertEquals(1, afterSync.size());
        assertEquals("品高应用一", afterSync.getFirst().get("label"));
        assertEquals(1, TenantContext.call(TENANT, service::duplicatePreview).total());
        assertEquals("ability-1", TenantContext.call(TENANT, service::duplicatePreview)
                .candidates().getFirst().get("PINGAO_SOURCE_KEY"));
    }

    @Test
    void failedSyncAfterSuccessKeepsPingaoOptionsAndRecordsFailure() {
        service.synchronize(TENANT);
        gateway.set(() -> { throw new IllegalStateException("remote unavailable"); });

        assertThrows(IllegalStateException.class, () -> service.synchronize(TENANT));

        PingaoApplicationRepository.SyncState state = TenantContext.call(TENANT, service::syncState);
        assertEquals(true, state.hasSuccess());
        assertEquals("FAILED", state.lastStatus());
        assertEquals("品高应用一", TenantContext.call(TENANT, service::selectableOptions)
                .getFirst().get("label"));
    }

    @Test
    void purgeOnlyDeletesSelectedUnreferencedLegacyRecords() {
        jdbcTemplate.update("""
                insert into sym_application_t
                    (tid, app_name, is_del, tenant_id, data_origin, sync_active)
                values ('legacy-free', '可清理历史应用', 0, ?, 'LEGACY', 0)
                """, TENANT);
        jdbcTemplate.update("""
                insert into sym_application_t
                    (tid, app_name, is_del, tenant_id, data_origin, sync_active)
                values ('legacy-used', '被引用历史应用', 0, ?, 'LEGACY', 0)
                """, TENANT);
        jdbcTemplate.update("insert into db_datasource_t values ('source-1', 'legacy-used', ?, 0)", TENANT);

        PingaoApplicationRepository.LegacyPurgePreview preview =
                TenantContext.call(TENANT, service::legacyPurgePreview);
        assertEquals(1, preview.removable().size());
        assertEquals(1, preview.referenced().size());

        assertEquals(1, TenantContext.call(TENANT, () -> service.purgeLegacy(List.of("legacy-free"))).get("deleted"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select is_del from sym_application_t where tid='legacy-free'", Integer.class));
        assertThrows(IllegalStateException.class,
                () -> TenantContext.call(TENANT, () -> service.purgeLegacy(List.of("legacy-used"))));
    }
}
