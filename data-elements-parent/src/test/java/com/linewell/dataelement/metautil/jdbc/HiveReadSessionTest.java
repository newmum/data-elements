package com.linewell.dataelement.metautil.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.linewell.dataelement.metautil.explorer.impl.HiveMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class HiveReadSessionTest {
    private static final String STATS = "hive.compute.query.using.stats";
    private static final String CACHE = "hive.query.results.cache.enabled";

    @Test
    void accurateCountReadsExternalFilesAndRestoresPooledSessionBeforeReturning() throws Exception {
        var db = new Session();
        var config = new DataSourceConfig(); config.setDatabaseType(DatabaseType.HIVE);
        var explorer = new HiveMetadataExplorer() {
            @Override protected Connection getConnection(DataSourceConfig ignored) { return db.connection; }
        };
        assertEquals(4L, explorer.getTableRowCount(config, "external_orders"));
        assertEquals(List.of("SET " + STATS, "SET " + CACHE,
            "SET " + STATS + "=false", "SET " + CACHE + "=false",
            "SELECT COUNT(*) FROM external_orders", "SET " + CACHE + "=true", "SET " + STATS + "=true"), db.sql);
        assertEquals("true", db.values.get(STATS)); assertEquals("true", db.values.get(CACHE));
        verify(db.connection).close();
    }

    @Test
    void preservesPreexistingDisabledOptionsWithoutWritingSessionSettings() throws Exception {
        var db = new Session(); db.values.replaceAll((key, value) -> "false");
        try (var ignored = HiveReadSession.open(db.connection, db::evict)) { }
        assertEquals(List.of("SET " + STATS, "SET " + CACHE), db.sql);
        assertEquals(0, db.evictions);
    }

    @Test
    void hiveWithoutQueryResultCacheStillUsesAccurateCount() throws Exception {
        var db = new Session(); db.values.remove(CACHE);
        try (var ignored = HiveReadSession.open(db.connection, db::evict)) {
            assertEquals("false", db.values.get(STATS));
            assertFalse(db.values.containsKey(CACHE));
        }
        assertEquals("true", db.values.get(STATS));
        assertEquals(0, db.evictions);
    }

    @Test
    void missingRequiredStatisticsSettingRejectsAccurateReadWithoutMutation() throws Exception {
        var db = new Session(); db.values.remove(STATS);
        assertThrows(SQLException.class, () -> HiveReadSession.open(db.connection, db::evict));
        assertEquals(List.of("SET " + STATS), db.sql);
        assertEquals("true", db.values.get(CACHE));
    }

    @Test
    void cachePermissionFailureIsNotMisclassifiedAsUnsupportedOldHive() throws Exception {
        var db = new Session(); db.fail = "SET " + CACHE;
        assertThrows(SQLException.class, () -> HiveReadSession.open(db.connection, db::evict));
        assertEquals("true", db.values.get(STATS));
        assertEquals(0, db.evictions);
    }

    @Test
    void ambiguousSetFailureRestoresBothAttemptedOptions() throws Exception {
        var db = new Session(); db.fail = "SET " + CACHE + "=false";
        assertThrows(SQLException.class, () -> HiveReadSession.open(db.connection, db::evict));
        assertEquals("true", db.values.get(STATS)); assertEquals("true", db.values.get(CACHE));
        assertEquals(0, db.evictions);
    }

    @Test
    void failedReadStillRestoresSettingsAndPreservesOriginalException() throws Exception {
        var db = new Session();
        var original = new SQLException("query timed out");
        var failure = assertThrows(SQLException.class, () -> {
            try (var ignored = HiveReadSession.open(db.connection, db::evict)) { throw original; }
        });
        assertSame(original, failure);
        assertEquals("true", db.values.get(STATS)); assertEquals("true", db.values.get(CACHE));
    }

    @Test
    void restoreFailureTriesRemainingOptionsEvictsDirtyConnectionAndClosesOnlyOnce() throws Exception {
        var db = new Session();
        var session = HiveReadSession.open(db.connection, db::evict);
        db.fail = "SET " + CACHE + "=true";
        assertThrows(SQLException.class, session::close);
        assertEquals("true", db.values.get(STATS));
        assertEquals(1, db.evictions);
        int commands = db.sql.size(); session.close();
        assertEquals(commands, db.sql.size()); assertEquals(1, db.evictions);
    }

    @Test
    void failedReadKeepsRestoreFailureAsSuppressedInsteadOfHidingTheCause() throws Exception {
        var db = new Session(); var original = new SQLException("read denied");
        var failure = assertThrows(SQLException.class, () -> {
            try (var ignored = HiveReadSession.open(db.connection, db::evict)) {
                db.fail = "SET " + STATS + "=true";
                throw original;
            }
        });
        assertSame(original, failure); assertEquals(1, failure.getSuppressed().length);
        assertEquals(1, db.evictions);
    }

    /** 模拟服务端陈旧 numRows=0 与真实4行，只在两项优化关闭后扫描实际数据。 */
    private static final class Session {
        final Connection connection = mock(Connection.class);
        final LinkedHashMap<String, String> values = new LinkedHashMap<>();
        final List<String> sql = new ArrayList<>();
        String fail;
        int evictions;

        Session() throws Exception {
            values.put(STATS, "true"); values.put(CACHE, "true");
            when(connection.createStatement()).thenAnswer(ignored -> {
                var statement = mock(Statement.class);
                when(statement.executeQuery(anyString())).thenAnswer(call -> {
                    String command = call.getArgument(0); record(command);
                    var rows = mock(ResultSet.class); when(rows.next()).thenReturn(true, false);
                    if (command.startsWith("SET ")) {
                        String key = command.substring(4);
                        when(rows.getString(1)).thenReturn(values.containsKey(key) ? key + "=" + values.get(key) : key + " is undefined");
                    } else {
                        when(rows.getLong(1)).thenReturn(values.containsValue("true") ? 0L : 4L);
                    }
                    return rows;
                });
                when(statement.execute(anyString())).thenAnswer(call -> {
                    String command = call.getArgument(0);
                    // 先应用再报错，覆盖服务端已执行但客户端失去确认的情形。
                    String[] parts = command.substring(4).split("=", 2);
                    values.put(parts[0], parts[1]); record(command); return false;
                });
                return statement;
            });
        }

        void record(String command) throws SQLException {
            sql.add(command);
            if (command.equals(fail)) throw new SQLException("session command denied");
        }

        void evict() { evictions++; }
    }
}
