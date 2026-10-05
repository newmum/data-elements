package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

class TargetTableCleanupServiceTest {
    private final DataSourceConnectionPropertyResolver connections = mock(DataSourceConnectionPropertyResolver.class);
    private final HiveModule hive = mock(HiveModule.class);
    private final TargetTableCleanupService cleanup = new TargetTableCleanupService(connections, hive);

    @ParameterizedTest
    @ValueSource(strings = {"INCREMENTAL", "INCR", "PERIODIC_FULL"})
    void ignoresCleanupFlagOutsideFullLoadModes(String mode) {
        cleanup.clear(pipeline(mode, true, managedSink("events")));
        verifyNoInteractions(connections, hive);
    }

    @Test
    void uncheckedFullLoadNeverOpensTarget() {
        cleanup.clear(pipeline("FULL", false, managedSink("events")));
        verifyNoInteractions(connections, hive);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FULL", "FULL_THEN_INCR"})
    void managedHiveTruncatesWholeTableAndVerifiesEmptyBeforeReturning(String mode) throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = prepare(connection, "MANAGED_TABLE");
        when(hive.getConnection()).thenReturn(connection);

        cleanup.clear(pipeline(mode, true, managedSink("events")));

        var order = inOrder(statement, connection);
        order.verify(statement).executeQuery("DESCRIBE FORMATTED `analytics`.`events`");
        // Omitting PARTITION deliberately clears all partitions as well as unpartitioned tables.
        order.verify(statement).execute("TRUNCATE TABLE `analytics`.`events`");
        order.verify(statement).executeQuery("SELECT 1 FROM `analytics`.`events` LIMIT 1");
        order.verify(connection).close();
        verify(statement, never()).execute(startsWith("DELETE"));
        verifyNoInteractions(connections);
    }

    @Test
    void duplicateHiveSinksClearOnePhysicalTableOnce() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = prepare(connection, "MANAGED_TABLE");
        when(hive.getConnection()).thenReturn(connection);
        cleanup.clear(pipeline("FULL", true, managedSink("events"), managedSink("`analytics`.`events`")));
        verify(hive).getConnection();
        verify(statement).execute("TRUNCATE TABLE `analytics`.`events`");
    }

    @Test
    void legacyJdbcHiveUsesRegisteredConfigurationFromActiveTenant() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = prepare(connection, "MANAGED_TABLE");
        when(hive.getConnection()).thenReturn(connection);
        when(connections.resolve("tenant-one", "registered-hive")).thenReturn(Map.of(
                "dbType", "HIVE", "database", "analytics", "hiveProfile", "default"));
        Pipeline.Node sink = sink("sink.jdbc", Map.of("dbType", "HIVE", "targetDbId", "registered-hive",
                "hiveProfile", "default", "database", "analytics", "table", "events"));
        try (var ignored = TenantContext.use("tenant-one")) {
            cleanup.clear(pipeline("FULL", true, sink));
        }
        verify(connections).resolve("tenant-one", "registered-hive");
        verify(statement).execute("TRUNCATE TABLE `analytics`.`events`");
    }

    @Test
    void directHiveConnectionUsesJdbcAndNeverFallsBackToMrs() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = prepare(connection, "MANAGED_TABLE");
        String url = "jdbc:hive2://test-only.invalid:10000/analytics";
        try (var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection(eq(url), any(Properties.class))).thenReturn(connection);
            cleanup.clear(pipeline("FULL", true, sink("sink.jdbc", Map.of(
                    "dbType", "HIVE", "metadataAccessMode", "jdbc", "hiveProfile", "default",
                    "jdbcUrl", url, "database", "analytics", "table", "events", "username", "test-only"))));
            driver.verify(() -> DriverManager.getConnection(eq(url), argThat(p -> "test-only".equals(p.getProperty("user")))));
        }
        verify(statement).execute("TRUNCATE TABLE `analytics`.`events`");
        verifyNoInteractions(hive);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXTERNAL_TABLE", "VIRTUAL_VIEW", "MATERIALIZED_VIEW", ""})
    void unsupportedHiveTypeAbortsBeforeAnyTargetIsMutated(String tableType) throws Exception {
        Connection first = mock(Connection.class);
        Connection second = mock(Connection.class);
        Statement firstSql = prepare(first, "MANAGED_TABLE");
        Statement secondSql = prepare(second, tableType);
        when(hive.getConnection()).thenReturn(first, second);
        assertThatThrownBy(() -> cleanup.clear(pipeline("FULL", true, managedSink("events"), managedSink("other"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("已中止启动");
        verify(firstSql, never()).execute(anyString());
        verify(secondSql, never()).execute(anyString());
        verify(first).close();
        verify(second).close();
    }

    @Test
    void missingHiveMetadataAbortsWithoutTruncating() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = prepare(connection, "MANAGED_TABLE");
        ResultSet missing = mock(ResultSet.class);
        when(statement.executeQuery(startsWith("DESCRIBE FORMATTED"))).thenReturn(missing);
        when(hive.getConnection()).thenReturn(connection);
        assertThatThrownBy(() -> cleanup.clear(pipeline("FULL", true, managedSink("events"))))
                .hasMessageContaining("无法确认 Hive 目标表类型");
        verify(statement, never()).execute(anyString());
    }

    @Test
    void truncateFailureIsNotReportedAsSuccessfulCleanup() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = prepare(connection, "MANAGED_TABLE");
        when(statement.execute(startsWith("TRUNCATE"))).thenThrow(new SQLException("Permission denied"));
        when(hive.getConnection()).thenReturn(connection);
        assertThatThrownBy(() -> cleanup.clear(pipeline("FULL", true, managedSink("events"))))
                .hasMessageContaining("已中止启动").hasMessageContaining("Permission denied");
        verify(statement, never()).executeQuery(startsWith("SELECT 1"));
        verify(connection).close();
    }

    @Test
    void remainingRowsAfterTruncateBlockStart() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = prepare(connection, "MANAGED_TABLE");
        ResultSet remaining = mock(ResultSet.class);
        when(remaining.next()).thenReturn(true);
        when(statement.executeQuery(startsWith("SELECT 1"))).thenReturn(remaining);
        when(hive.getConnection()).thenReturn(connection);
        assertThatThrownBy(() -> cleanup.clear(pipeline("FULL", true, managedSink("events"))))
                .hasMessageContaining("清理后仍有数据");
    }

    @ParameterizedTest
    @ValueSource(strings = {"other.events", "events; DROP TABLE unrelated", "events WHERE 1=1", "", "db.schema.events"})
    void invalidOrCrossDatabaseTargetNeverOpensConnection(String table) {
        assertThatThrownBy(() -> cleanup.clear(pipeline("FULL", true, managedSink(table))))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(hive);
    }

    @Test
    void registeredDatabaseMismatchOrMissingTenantSourceBlocksCleanup() {
        Pipeline.Node sink = sink("sink.hive", Map.of("targetDbId", "registered-hive", "database", "analytics", "table", "events"));
        try (var ignored = TenantContext.use("tenant-one")) {
            when(connections.resolve("tenant-one", "registered-hive")).thenReturn(Map.of());
            assertThatThrownBy(() -> cleanup.clear(pipeline("FULL", true, sink))).hasMessageContaining("当前租户");
            when(connections.resolve("tenant-one", "registered-hive")).thenReturn(Map.of("dbType", "HIVE", "database", "other"));
            assertThatThrownBy(() -> cleanup.clear(pipeline("FULL", true, sink))).hasMessageContaining("不一致");
        }
        verifyNoInteractions(hive);
    }

    @Test
    void registeredEndpointChangeCannotClearADifferentHiveClusterThanTheWriter() {
        Pipeline.Node sink = sink("sink.jdbc", Map.of("dbType", "HIVE", "metadataAccessMode", "jdbc",
                "targetDbId", "registered-hive", "database", "analytics", "table", "events",
                "jdbcUrl", "jdbc:hive2://old-test.invalid:10000/analytics"));
        when(connections.resolve("tenant-one", "registered-hive")).thenReturn(Map.of("dbType", "HIVE",
                "metadataAccessMode", "jdbc", "database", "analytics", "jdbcURL", "jdbc:hive2://new-test.invalid:10000/analytics"));
        try (var tenant = TenantContext.use("tenant-one"); var driver = mockStatic(DriverManager.class)) {
            assertThatThrownBy(() -> cleanup.clear(pipeline("FULL", true, sink))).hasMessageContaining("连接与已登记目标库不一致");
            driver.verifyNoInteractions();
        }
        verifyNoInteractions(hive);
    }

    @Test
    void failedEmptyCheckCannotBeReportedAsSuccessfulCleanup() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = prepare(connection, "MANAGED_TABLE");
        when(statement.executeQuery(startsWith("SELECT 1"))).thenThrow(new SQLException("Read timed out"));
        when(hive.getConnection()).thenReturn(connection);
        assertThatThrownBy(() -> cleanup.clear(pipeline("FULL", true, managedSink("events"))))
                .hasMessageContaining("已中止启动").hasMessageContaining("Read timed out");
        verify(connection).close();
    }

    @Test
    void unsupportedMixedSinkIsNotSilentlySkipped() {
        assertThatThrownBy(() -> cleanup.clear(pipeline("FULL", true, managedSink("events"), sink("sink.kafka", Map.of()))))
                .hasMessageContaining("不支持启动前清理");
        verifyNoInteractions(hive);
    }

    @Test
    void isolatedDatabaseActuallyClearsOldRowsAndRetainsTwoSubsequentWriteBatches() throws Exception {
        // H2 verifies the real JDBC row lifecycle; mocked DESCRIBE supplies Hive's metadata shape.
        // This is deliberately not presented as a live Hive-server compatibility test.
        String url = "jdbc:h2:mem:cleanup_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        try (Connection observer = DriverManager.getConnection(url); Statement sql = observer.createStatement()) {
            sql.execute("CREATE SCHEMA analytics");
            sql.execute("CREATE TABLE analytics.events (id INT PRIMARY KEY, payload VARCHAR(30))");
            sql.execute("INSERT INTO analytics.events VALUES (1, 'old row')");
            Connection actual = DriverManager.getConnection(url);
            Connection wrapped = mock(Connection.class, delegatesTo(actual));
            when(wrapped.createStatement()).thenAnswer(i -> {
                Statement statement = mock(Statement.class, delegatesTo(actual.createStatement()));
                doReturn(metadata("MANAGED_TABLE")).when(statement).executeQuery(startsWith("DESCRIBE FORMATTED"));
                return statement;
            });
            when(hive.getConnection()).thenReturn(wrapped);
            cleanup.clear(pipeline("FULL", true, managedSink("events")));
            try (ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM analytics.events")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
            sql.execute("INSERT INTO analytics.events VALUES (2, 'batch one')");
            sql.execute("INSERT INTO analytics.events VALUES (3, 'batch two')");
            try (ResultSet rows = sql.executeQuery("SELECT id FROM analytics.events ORDER BY id")) {
                List<Integer> ids = new ArrayList<>();
                while (rows.next()) ids.add(rows.getInt(1));
                assertThat(ids).containsExactly(2, 3);
            }
            verify(hive).getConnection();
            verify(wrapped).close();
        }
    }

    private Statement prepare(Connection connection, String tableType) throws Exception {
        Statement statement = mock(Statement.class);
        ResultSet tableMetadata = metadata(tableType);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(startsWith("DESCRIBE FORMATTED"))).thenReturn(tableMetadata);
        when(statement.executeQuery(startsWith("SELECT 1"))).thenReturn(mock(ResultSet.class));
        return statement;
    }

    private ResultSet metadata(String type) throws SQLException {
        ResultSet metadata = mock(ResultSet.class);
        when(metadata.next()).thenReturn(true, false);
        when(metadata.getString(1)).thenReturn("Table Type:          ");
        when(metadata.getString(2)).thenReturn(type + "   ");
        return metadata;
    }

    private Pipeline.Node managedSink(String table) {
        return sink("sink.hive", Map.of("hiveProfile", "default", "database", "analytics", "table", table));
    }

    private Pipeline.Node sink(String manifest, Map<String, Object> config) {
        return new Pipeline.Node(UUID.randomUUID().toString(), manifest, "测试目标", "sink", 0, 0, config);
    }

    private Pipeline pipeline(String mode, boolean delete, Pipeline.Node... sinks) {
        List<Pipeline.Node> nodes = new ArrayList<>();
        nodes.add(new Pipeline.Node("source", "source.jdbc", "测试来源", "source", 0, 0,
                Map.of("syncMode", mode, "deleteTargetData", delete)));
        nodes.addAll(List.of(sinks));
        return new Pipeline("test-only", "测试流程", null, 1L, 1L, new Pipeline.Dsl(1, nodes, List.of()),
                null, PipelineStatus.STOPPED, null, null, null, null, null);
    }
}
