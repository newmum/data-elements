package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class StructuredCounterMagicModuleTest {
    private static final String TENANT = "validation-a";
    private static final String OTHER_TENANT = "validation-b";
    private static final String TABLE = "da_catalog_t";
    private static final String COLUMN = "market_view_count";
    private static final String DM = "jdbc:dm://validation";
    private static final Map<String, Object> APPROVED = Map.of("is_del", 0, "asset_status", 2, "flow_status", 2);

    @Test
    void oneHundredConcurrentTransactionsPreserveEveryIncrementWithTwoSqlEach() throws Exception {
        var fixture = new Fixture();
        fixture.catalog("validation-counter", TENANT, 0, 2, 2, 41);
        var calls = new ArrayList<Callable<Long>>();
        for (int index = 0; index < 100; index++) {
            calls.add(() -> TenantContext.call(TENANT,
                    () -> (Long) fixture.increment("validation-counter", APPROVED).get("value")));
        }
        var returned = new ArrayList<Long>();
        try (var workers = Executors.newFixedThreadPool(8)) {
            for (var value : workers.invokeAll(calls)) returned.add(value.get());
        }
        assertThat(returned).containsExactlyInAnyOrderElementsOf(
                java.util.stream.LongStream.rangeClosed(42, 141).boxed().toList());
        assertThat(fixture.count("validation-counter")).isEqualTo(141);
        assertThat(fixture.source.reads.get()).isEqualTo(100);
        assertThat(fixture.source.updates.get()).isEqualTo(100);
        assertThat(fixture.source.connections.get()).isEqualTo(100);
        assertThat(fixture.source.closed.get()).isEqualTo(100);
        assertThat(fixture.source.timeouts).hasSize(200).containsOnly(15);
        assertThat(fixture.jdbc.queryForMap("SELECT resource_revision_no,updated_time FROM da_catalog_t WHERE tid='validation-counter'"))
                .containsEntry("resource_revision_no", 9)
                .containsEntry("updated_time", java.sql.Timestamp.valueOf("2026-11-01 12:00:00"));
    }

    @Test
    void callerSuppliedGuardsAndTenantRemainInEveryLockAndUpdate() {
        var fixture = new Fixture();
        fixture.catalog("validation-active", TENANT, 0, 2, 2, 9);
        fixture.catalog("validation-foreign", OTHER_TENANT, 0, 2, 2, 9);
        fixture.catalog("validation-deleted", TENANT, 1, 2, 2, 9);
        fixture.catalog("validation-draft", TENANT, 0, 1, 2, 9);
        fixture.catalog("validation-pending", TENANT, 0, 2, 1, 9);
        TenantContext.run(TENANT, () -> {
            for (String id : List.of("validation-foreign", "validation-deleted", "validation-draft", "validation-pending", "validation-missing")) {
                assertThat(fixture.increment(id, APPROVED)).isEqualTo(result(false, null, false, false));
            }
            assertThat(fixture.increment("validation-active", APPROVED)).isEqualTo(result(true, 10L, true, false));
        });
        assertThat(fixture.source.reads.get()).isEqualTo(6);
        assertThat(fixture.source.updates.get()).isEqualTo(1);
        assertThat(fixture.count("validation-foreign")).isEqualTo(9);
        assertThat(fixture.source.sql).allSatisfy(sql -> assertThat(sql).contains(
                "\"tenant_id\" = ?", "\"tid\" = ?", "\"is_del\" = ?", "\"asset_status\" = ?", "\"flow_status\" = ?"));
    }

    @Test
    void technicalPrimitiveDoesNotHardcodeMagicStatusValuesAndSupportsExplicitNullGuards() {
        var fixture = new Fixture();
        fixture.catalog("validation-other-state", TENANT, 0, 1, 1, 0);
        fixture.catalog("validation-null-state", TENANT, 0, 2, null, 0);
        TenantContext.run(TENANT, () -> {
            assertThat(fixture.increment("validation-other-state", Map.of("asset_status", new BigDecimal("1.0"), "flow_status", 1)))
                    .isEqualTo(result(true, 1L, true, false));
            var nullable = new LinkedHashMap<String, Object>();
            nullable.put("flow_status", null);
            assertThat(fixture.increment("validation-null-state", nullable)).isEqualTo(result(true, 1L, true, false));
        });
        assertThat(fixture.source.sql).anySatisfy(sql -> assertThat(sql).contains("\"flow_status\" IS NULL"));
    }

    @Test
    void saturatedCounterReturnsExactLongWithoutUpdateOrOverflow() {
        var fixture = new Fixture();
        fixture.catalog("validation-max", TENANT, 0, 2, 2, Long.MAX_VALUE);
        assertThat(TenantContext.call(TENANT, () -> fixture.increment("validation-max", APPROVED)))
                .isEqualTo(result(true, Long.MAX_VALUE, false, true));
        assertThat(fixture.source.reads.get()).isEqualTo(1);
        assertThat(fixture.source.updates.get()).isZero();
        assertThat(fixture.count("validation-max")).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void sameIdUsesThePhysicalDatasourceOfTheCurrentTenant() {
        var first = new Fixture();
        var second = new Fixture();
        first.catalog("validation-same", TENANT, 0, 2, 2, 10);
        second.catalog("validation-same", OTHER_TENANT, 0, 2, 2, 70);
        var registry = mock(TenantDataSourceRegistry.class);
        when(registry.dataSourceForTenant(TENANT)).thenReturn(first.source);
        when(registry.dataSourceForTenant(OTHER_TENANT)).thenReturn(second.source);
        var module = new StructuredCounterMagicModule(registry);
        assertThat(TenantContext.call(TENANT, () -> module.incrementAndGet(TABLE, COLUMN, "validation-same", APPROVED)))
                .containsEntry("value", 11L);
        assertThat(TenantContext.call(OTHER_TENANT, () -> module.incrementAndGet(TABLE, COLUMN, "validation-same", APPROVED)))
                .containsEntry("value", 71L);
        assertThat(first.count("validation-same")).isEqualTo(11);
        assertThat(second.count("validation-same")).isEqualTo(71);
    }

    @Test
    void invalidTargetsConditionsAndUntrustedContextsNeverOpenADatabaseConnection() {
        var registry = mock(TenantDataSourceRegistry.class);
        var module = new StructuredCounterMagicModule(registry);
        TenantContext.run(TENANT, () -> {
            for (String id : List.of("", "../catalog", "validation' OR 1=1", "x".repeat(33))) {
                assertThatThrownBy(() -> module.incrementAndGet(TABLE, COLUMN, id, APPROVED)).isInstanceOf(IllegalArgumentException.class);
            }
            assertThatThrownBy(() -> module.incrementAndGet(TABLE + " WHERE 1=1", COLUMN, "validation-id", APPROVED)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> module.incrementAndGet(TABLE, "updated_time", "validation-id", APPROVED)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> module.incrementAndGet(TABLE, COLUMN, "validation-id", null)).isInstanceOf(IllegalArgumentException.class);
            for (Map<String, Object> guards : List.<Map<String, Object>>of(Map.of("tenant_id", OTHER_TENANT), Map.of("tid", "other"),
                    Map.of("flow_status", "2"), Map.of("flow_status", 1.5), Map.of("flow_status", Double.NaN),
                    Map.of("flow_status", new BigDecimal("9223372036854775808")))) {
                assertThatThrownBy(() -> module.incrementAndGet(TABLE, COLUMN, "validation-id", guards)).isInstanceOf(IllegalArgumentException.class);
            }
            try (var ignored = TenantContext.control()) {
                assertThatThrownBy(() -> module.incrementAndGet(TABLE, COLUMN, "validation-id", APPROVED)).isInstanceOf(RuntimeException.class);
            }
            try (var ignored = TenantContext.ignore()) {
                assertThatThrownBy(() -> module.incrementAndGet(TABLE, COLUMN, "validation-id", APPROVED)).isInstanceOf(RuntimeException.class);
            }
        });
        assertThatThrownBy(() -> module.incrementAndGet(TABLE, COLUMN, "validation-id", APPROVED)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(registry);
    }

    @Test
    void rejectsAmbientTransactionsAndAlreadyBoundPhysicalConnections() {
        var registry = mock(TenantDataSourceRegistry.class);
        var source = mock(DataSource.class);
        when(registry.dataSourceForTenant(TENANT)).thenReturn(source);
        var module = new StructuredCounterMagicModule(registry);
        TenantContext.run(TENANT, () -> {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                assertThatThrownBy(() -> module.incrementAndGet(TABLE, COLUMN, "validation-id", APPROVED)).hasMessageContaining("不能嵌套");
                verifyNoInteractions(registry);
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
            TransactionSynchronizationManager.initSynchronization();
            try {
                assertThatThrownBy(() -> module.incrementAndGet(TABLE, COLUMN, "validation-id", APPROVED)).hasMessageContaining("不能嵌套");
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
            TransactionSynchronizationManager.bindResource(source, new Object());
            try {
                assertThatThrownBy(() -> module.incrementAndGet(TABLE, COLUMN, "validation-id", APPROVED)).hasMessageContaining("外层事务");
            } finally {
                TransactionSynchronizationManager.unbindResource(source);
            }
        });
        verifyNoInteractions(source);
    }

    @Test
    void negativeStoredValuesAndUnexpectedUpdateCountsRollBack() throws Exception {
        var negative = new MockAttempt(DM, -1);
        var fixture = new MockFixture(negative);
        assertThatThrownBy(fixture::increment).hasMessageContaining("存储值");
        verify(negative.connection).rollback();
        verify(negative.connection, never()).commit();
        verify(negative.update, never()).executeUpdate();
        var changed = new MockAttempt(DM, 7);
        when(changed.update.executeUpdate()).thenReturn(0);
        assertThatThrownBy(new MockFixture(changed)::increment).hasMessageContaining("状态已变化");
        verify(changed.connection).rollback();
        verify(changed.connection, never()).commit();
    }

    @Test
    void dmReadConflictRetriesOnlyAfterConfirmedRollbackAndClosesBothConnections() throws Exception {
        var first = new MockAttempt(DM, 7);
        when(first.select.executeQuery()).thenThrow(conflict());
        var second = new MockAttempt(DM, 7);
        var fixture = new MockFixture(first, second);
        assertThat(fixture.increment()).isEqualTo(result(true, 8L, true, false));
        verify(first.connection).rollback();
        verify(first.connection, never()).commit();
        verify(first.connection).close();
        verify(second.connection).commit();
        verify(second.connection).close();
        verify(first.select).setQueryTimeout(15);
        verify(second.select).setQueryTimeout(15);
        verify(second.update).setQueryTimeout(15);
        assertThat(fixture.delays).containsExactly(10L);
        verify(fixture.source, times(2)).getConnection();
    }

    @Test
    void dmUpdateConflictRollsBackBeforeTryingAgainWithoutCountingTheFailedAttempt() throws Exception {
        var first = new MockAttempt(DM, 7);
        when(first.update.executeUpdate()).thenThrow(conflict());
        var second = new MockAttempt(DM, 7);
        var fixture = new MockFixture(first, second);
        assertThat(fixture.increment()).containsEntry("value", 8L);
        verify(first.connection).rollback();
        verify(first.connection, never()).commit();
        verify(second.connection).commit();
        assertThat(fixture.delays).hasSize(1);
    }

    @Test
    void dmConflictRetriesAtMostEightTimes() throws Exception {
        var attempts = new ArrayList<MockAttempt>();
        for (int index = 0; index < 8; index++) {
            var attempt = new MockAttempt(DM, 7);
            when(attempt.select.executeQuery()).thenThrow(conflict());
            attempts.add(attempt);
        }
        var fixture = new MockFixture(attempts.toArray(MockAttempt[]::new));
        assertThatThrownBy(fixture::increment).hasMessageContaining("并发冲突");
        verify(fixture.source, times(8)).getConnection();
        assertThat(fixture.delays).containsExactly(10L, 20L, 30L, 40L, 50L, 60L, 70L);
        for (var attempt : attempts) {
            verify(attempt.connection).rollback();
            verify(attempt.connection).close();
            verify(attempt.connection, never()).commit();
        }
    }

    @Test
    void otherDialectsTimeoutsConnectionErrorsAndOtherDmErrorsAreNeverRetried() throws Exception {
        for (String url : List.of("jdbc:mysql://validation", "jdbc:kingbase8://validation")) {
            var attempt = new MockAttempt(url, 7);
            when(attempt.select.executeQuery()).thenThrow(conflict());
            var fixture = new MockFixture(attempt);
            assertThatThrownBy(fixture::increment).hasMessageContaining("更新失败");
            verify(fixture.source).getConnection();
            assertThat(fixture.delays).isEmpty();
        }
        for (SQLException error : List.of(new SQLException("validation-overflow", "22000", -6102),
                new SQLException("validation-network", "08006", -7067), new SQLTimeoutException("validation-timeout"))) {
            var attempt = new MockAttempt(DM, 7);
            when(attempt.select.executeQuery()).thenThrow(error);
            var fixture = new MockFixture(attempt);
            assertThatThrownBy(fixture::increment).hasMessageContaining("更新失败");
            verify(attempt.connection).rollback();
            verify(fixture.source).getConnection();
            assertThat(fixture.delays).isEmpty();
        }
    }

    @Test
    void rollbackFailurePreventsRetriesEvenForKnownDmConflict() throws Exception {
        var attempt = new MockAttempt(DM, 7);
        when(attempt.select.executeQuery()).thenThrow(conflict());
        doThrow(new SQLException("validation-rollback-unknown", "08006")).when(attempt.connection).rollback();
        var fixture = new MockFixture(attempt);
        assertThatThrownBy(fixture::increment).hasMessageContaining("回滚结果无法确认");
        verify(fixture.source).getConnection();
        verify(attempt.connection).close();
        verify(attempt.connection, never()).commit();
        assertThat(fixture.delays).isEmpty();
    }

    @Test
    void commitFailureIsNeverRetriedEvenWhenItsErrorCodeLooksLikeADmConflict() throws Exception {
        var attempt = new MockAttempt(DM, 7);
        doThrow(conflict()).when(attempt.connection).commit();
        var fixture = new MockFixture(attempt);
        assertThatThrownBy(fixture::increment).hasMessageContaining("提交结果无法确认");
        verify(attempt.update).executeUpdate();
        verify(attempt.connection).rollback();
        verify(attempt.connection).close();
        verify(fixture.source).getConnection();
        assertThat(fixture.delays).isEmpty();
    }

    @Test
    void connectionSetupAndReleaseFailuresAreNotRetried() throws Exception {
        var setup = new MockAttempt(DM, 7);
        doThrow(conflict()).when(setup.connection).setAutoCommit(false);
        var fixture = new MockFixture(setup);
        assertThatThrownBy(fixture::increment).hasMessageContaining("连接操作失败");
        verify(fixture.source).getConnection();
        verify(setup.select, never()).executeQuery();
        assertThat(fixture.delays).isEmpty();
        var release = new MockAttempt(DM, 7);
        when(release.select.executeQuery()).thenThrow(conflict());
        doThrow(conflict()).when(release.connection).close();
        var releaseFixture = new MockFixture(release);
        assertThatThrownBy(releaseFixture::increment).hasMessageContaining("连接操作失败");
        verify(release.connection).rollback();
        verify(releaseFixture.source).getConnection();
        assertThat(releaseFixture.delays).isEmpty();
    }

    @Test
    void interruptedBackoffPreservesInterruptAndDoesNotTryAgain() throws Exception {
        var attempt = new MockAttempt(DM, 7);
        when(attempt.select.executeQuery()).thenThrow(conflict());
        var fixture = new MockFixture(attempt);
        var module = new StructuredCounterMagicModule(fixture.registry, delay -> { throw new InterruptedException("validation-interrupted"); });
        try {
            assertThatThrownBy(() -> TenantContext.call(TENANT,
                    () -> module.incrementAndGet(TABLE, COLUMN, "validation-counter", APPROVED))).hasMessageContaining("已中断");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(fixture.source).getConnection();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void tenantRebindingDuringReadRollsBackWithoutWriting() throws Exception {
        var attempt = new MockAttempt(DM, 7);
        when(attempt.select.executeQuery()).thenAnswer(ignored -> {
            TenantContext.bind(OTHER_TENANT);
            return attempt.rows;
        });
        var fixture = new MockFixture(attempt);
        assertThatThrownBy(fixture::increment).hasMessageContaining("上下文");
        verify(attempt.connection).rollback();
        verify(attempt.connection, never()).commit();
        verify(attempt.update, never()).executeUpdate();
    }

    private static SQLException conflict() {
        return new SQLException("validation-mvcc-conflict", "22000", -7067);
    }

    private static Map<String, Object> result(boolean matched, Long value, boolean incremented, boolean saturated) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", matched);
        result.put("value", value);
        result.put("incremented", incremented);
        result.put("saturated", saturated);
        return result;
    }

    private static final class Fixture {
        private final DriverManagerDataSource actual = new DriverManagerDataSource("jdbc:h2:mem:validation_counter_"
                + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
        private final CountedSource source = new CountedSource(actual);
        private final JdbcTemplate jdbc = new JdbcTemplate(actual);
        private final TenantDataSourceRegistry registry = mock(TenantDataSourceRegistry.class);
        private final StructuredCounterMagicModule module = new StructuredCounterMagicModule(registry);

        private Fixture() {
            when(registry.dataSourceForTenant(TENANT)).thenReturn(source);
            jdbc.execute("""
                CREATE TABLE da_catalog_t (tid VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(64) NOT NULL,
                    is_del INT,asset_status INT,flow_status INT,market_view_count BIGINT DEFAULT 0 NOT NULL,
                    resource_revision_no INT DEFAULT 9,updated_time TIMESTAMP DEFAULT TIMESTAMP '2026-11-01 12:00:00')
                """);
        }

        private void catalog(String id, String tenant, Integer deleted, Integer asset, Integer flow, long count) {
            jdbc.update("INSERT INTO da_catalog_t(tid,tenant_id,is_del,asset_status,flow_status,market_view_count) VALUES(?,?,?,?,?,?)",
                    id, tenant, deleted, asset, flow, count);
        }

        private Map<String, Object> increment(String id, Map<String, Object> guards) {
            return module.incrementAndGet(TABLE, COLUMN, id, guards);
        }

        private long count(String id) {
            return jdbc.queryForObject("SELECT market_view_count FROM da_catalog_t WHERE tid=?", Long.class, id);
        }
    }

    private static final class MockFixture {
        private final TenantDataSourceRegistry registry = mock(TenantDataSourceRegistry.class);
        private final DataSource source = mock(DataSource.class);
        private final List<Long> delays = new ArrayList<>();
        private final StructuredCounterMagicModule module = new StructuredCounterMagicModule(registry, delays::add);

        private MockFixture(MockAttempt... attempts) throws Exception {
            when(registry.dataSourceForTenant(TENANT)).thenReturn(source);
            var remaining = new ArrayDeque<>(List.of(attempts));
            when(source.getConnection()).thenAnswer(ignored -> remaining.removeFirst().connection);
        }

        private Map<String, Object> increment() {
            return TenantContext.call(TENANT, () -> module.incrementAndGet(TABLE, COLUMN, "validation-counter", APPROVED));
        }
    }

    private static final class MockAttempt {
        private final Connection connection = mock(Connection.class);
        private final PreparedStatement select = mock(PreparedStatement.class);
        private final PreparedStatement update = mock(PreparedStatement.class);
        private final ResultSet rows = mock(ResultSet.class);

        private MockAttempt(String url, long value) throws Exception {
            var metadata = mock(DatabaseMetaData.class);
            when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getURL()).thenReturn(url);
            when(metadata.getIdentifierQuoteString()).thenReturn("\"");
            when(connection.prepareStatement(anyString())).thenAnswer(call ->
                    ((String) call.getArgument(0)).startsWith("SELECT") ? select : update);
            when(select.executeQuery()).thenReturn(rows);
            when(rows.next()).thenReturn(true, false);
            when(rows.getLong(1)).thenReturn(value);
            when(update.executeUpdate()).thenReturn(1);
        }
    }

    private static final class CountedSource extends AbstractDataSource {
        private final DataSource actual;
        private final AtomicInteger reads = new AtomicInteger();
        private final AtomicInteger updates = new AtomicInteger();
        private final AtomicInteger connections = new AtomicInteger();
        private final AtomicInteger closed = new AtomicInteger();
        private final List<Integer> timeouts = Collections.synchronizedList(new ArrayList<>());
        private final List<String> sql = Collections.synchronizedList(new ArrayList<>());

        private CountedSource(DataSource actual) { this.actual = actual; }

        @Override public Connection getConnection() throws SQLException {
            connections.incrementAndGet();
            Connection real = actual.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                try {
                    if (method.getName().equals("close")) closed.incrementAndGet();
                    Object value = method.invoke(real, args);
                    if (method.getName().equals("prepareStatement")) {
                        sql.add((String) args[0]);
                        PreparedStatement statement = (PreparedStatement) value;
                        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class}, (prepared, operation, parameters) -> {
                            if (operation.getName().equals("executeQuery")) reads.incrementAndGet();
                            if (operation.getName().equals("executeUpdate")) updates.incrementAndGet();
                            if (operation.getName().equals("setQueryTimeout")) timeouts.add((Integer) parameters[0]);
                            try { return operation.invoke(statement, parameters); }
                            catch (InvocationTargetException failure) { throw failure.getCause(); }
                        });
                    }
                    return value;
                } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                }
            });
        }

        @Override public Connection getConnection(String user, String password) throws SQLException { return getConnection(); }
    }
}
