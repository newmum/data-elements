package com.linewell.dataelement.metautil.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Hive 外部文件写入不会同步刷新统计或查询缓存。准确读取期间关闭这两项会话优化，
 * 归还池前恢复原值；恢复失败必须驱逐该物理连接，避免污染后续借用者。
 * 不修改全局配置、表统计或数据。旧版 Hive 未提供结果缓存时仅跳过该可选项。
 */
public final class HiveReadSession implements AutoCloseable {
    private static final String STATS = "hive.compute.query.using.stats";
    private static final String CACHE = "hive.query.results.cache.enabled";
    private static final HiveReadSession NONE = new HiveReadSession(null, null);
    private final Connection connection;
    private final EvictConnection evict;
    private final List<String> changed = new ArrayList<>(2);
    private boolean closed;

    @FunctionalInterface
    public interface EvictConnection {
        void evict() throws SQLException;
    }

    private HiveReadSession(Connection connection, EvictConnection evict) {
        this.connection = connection;
        this.evict = evict;
    }

    public static HiveReadSession none() {
        return NONE;
    }

    public static HiveReadSession open(Connection connection, EvictConnection evict) throws SQLException {
        var session = new HiveReadSession(connection, evict);
        // 先读取全部原值；权限/网络错误不能当作旧版缺少功能后继续返回假计数。
        boolean stats = session.enabled(STATS, false);
        boolean cache = session.enabled(CACHE, true);
        try {
            if (stats) session.disable(STATS);
            if (cache) session.disable(CACHE);
            return session;
        } catch (SQLException | RuntimeException failure) {
            try { session.close(); } catch (SQLException restore) { failure.addSuppressed(restore); }
            throw failure;
        }
    }

    private boolean enabled(String key, boolean optional) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (var rows = statement.executeQuery("SET " + key)) {
                if (rows.next()) {
                    String value = rows.getString(1);
                    if (value != null) {
                        value = value.trim();
                        if (optional && value.equals(key + " is undefined")) return false;
                        if (value.equalsIgnoreCase(key + "=true")) return true;
                        if (value.equalsIgnoreCase(key + "=false")) return false;
                    }
                }
            }
        }
        throw new SQLException("无法确认 Hive 准确读取所需的会话配置: " + key);
    }

    private void disable(String key) throws SQLException {
        // 即使服务端已执行 SET、客户端随后超时，也须尝试恢复。
        changed.add(key);
        set(key, false);
    }

    private void set(String key, boolean value) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            statement.execute("SET " + key + "=" + value);
        }
    }

    @Override
    public void close() throws SQLException {
        if (connection == null || closed) return;
        closed = true;
        SQLException failure = null;
        for (int i = changed.size() - 1; i >= 0; i--) {
            try {
                set(changed.get(i), true);
            } catch (SQLException | RuntimeException restore) {
                if (failure == null) failure = new SQLException("Hive 读取后恢复会话失败，连接已驱逐", restore);
                else failure.addSuppressed(restore);
            }
        }
        if (failure != null) {
            try { evict.evict(); } catch (SQLException | RuntimeException eviction) { failure.addSuppressed(eviction); }
            throw failure;
        }
    }
}
