package com.linewell.dataelement.platform.magic;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** Owns only the JDBC counter transaction; eligibility and publication rules remain in Magic. */
@Component
@MagicModule("tableCounter")
public class StructuredCounterMagicModule {
    private static final String TABLE = "da_catalog_t";
    private static final String COLUMN = "market_view_count";
    private static final List<String> GUARDS = List.of("is_del", "asset_status", "flow_status");
    private static final int QUERY_TIMEOUT_SECONDS = 15;
    private static final int MAX_ATTEMPTS = 8;
    private final TenantDataSourceRegistry dataSources;
    private final RetryDelay retryDelay;

    @Autowired
    public StructuredCounterMagicModule(TenantDataSourceRegistry dataSources) {
        this(dataSources, Thread::sleep);
    }

    StructuredCounterMagicModule(TenantDataSourceRegistry dataSources, RetryDelay retryDelay) {
        this.dataSources = Objects.requireNonNull(dataSources);
        this.retryDelay = Objects.requireNonNull(retryDelay);
    }

    @Comment("为当前租户的白名单计数列递增并返回值；Magic显式传入状态条件，不能在外层事务内调用")
    public Map<String, Object> incrementAndGet(String table, String column, String rowId,
            Map<String, Object> expectedFields) {
        if (!TABLE.equals(table) || !COLUMN.equals(column) || rowId == null
                || !rowId.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("计数目标无效");
        }
        Map<String, Long> guards = guards(expectedFields);
        String tenant = requireTenant(null);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("计数必须使用独立事务，不能嵌套在业务事务中");
        }
        DataSource source = dataSources.dataSourceForTenant(tenant);
        if (TransactionSynchronizationManager.hasResource(source)) {
            throw new IllegalStateException("计数数据源已参与外层事务");
        }
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            requireTenant(tenant);
            Attempt outcome = attempt(source, tenant, rowId, guards);
            if (outcome.conflict() == null) return outcome.result();
            if (attempt == MAX_ATTEMPTS - 1) {
                throw new IllegalStateException("计数并发冲突，请稍后重试", outcome.conflict());
            }
            try {
                retryDelay.pause((attempt + 1L) * 10L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("计数重试已中断", interrupted);
            }
        }
        throw new IllegalStateException("计数重试未完成");
    }

    private Attempt attempt(DataSource source, String tenant, String rowId, Map<String, Long> guards) {
        // A fresh physical tenant connection gives this operation sole rollback/commit ownership.
        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            String url = Objects.toString(connection.getMetaData().getURL(), "").toLowerCase(Locale.ROOT);
            boolean dameng = url.startsWith("jdbc:dm:");
            Map<String, Object> result;
            try {
                requireTenant(tenant);
                String quote = dameng ? "\"" : connection.getMetaData().getIdentifierQuoteString().trim();
                if (!"\"".equals(quote) && !"`".equals(quote)) {
                    throw new IllegalStateException("当前计数数据库标识符格式不受支持");
                }
                result = lockAndIncrement(connection, quote, tenant, rowId, guards);
                requireTenant(tenant);
            } catch (SQLException | RuntimeException failure) {
                // Only a confirmed rollback of the known DM conflict permits another attempt.
                rollback(connection, failure);
                if (dameng && failure instanceof SQLException sql && retryableConflict(sql)) {
                    return new Attempt(null, sql);
                }
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("计数更新失败", failure);
            }
            // Never place commit inside the retryable execution block: its outcome may be unknown.
            try {
                connection.commit();
            } catch (SQLException | RuntimeException uncertain) {
                try {
                    connection.rollback();
                } catch (SQLException | RuntimeException cleanup) {
                    uncertain.addSuppressed(cleanup);
                }
                throw new IllegalStateException("计数提交结果无法确认，请勿自动重复计数", uncertain);
            }
            return new Attempt(result, null);
        } catch (SQLException connectionFailure) {
            // Connection acquisition, setup and close errors are not confirmed rolled-back conflicts.
            throw new IllegalStateException("计数连接操作失败，请勿自动重复计数", connectionFailure);
        }
    }

    private Map<String, Object> lockAndIncrement(Connection connection, String quote, String tenant,
            String rowId, Map<String, Long> guards) throws SQLException {
        String counter = quoted(quote, COLUMN);
        String target = quoted(quote, TABLE);
        StringBuilder scope = new StringBuilder(quoted(quote, "tenant_id") + " = ? AND "
                + quoted(quote, "tid") + " = ?");
        List<Object> arguments = new ArrayList<>();
        arguments.add(tenant);
        arguments.add(rowId);
        guards.forEach((field, expected) -> {
            scope.append(" AND ").append(quoted(quote, field));
            if (expected == null) {
                scope.append(" IS NULL");
            } else {
                scope.append(" = ?");
                arguments.add(expected);
            }
        });
        long value;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT " + counter + " FROM " + target + " WHERE " + scope + " FOR UPDATE")) {
            bind(select, arguments);
            try (var rows = select.executeQuery()) {
                if (!rows.next()) return result(false, null, false, false);
                value = rows.getLong(1);
                if (rows.wasNull() || value < 0 || rows.next()) {
                    throw new IllegalStateException("计数存储值或主键约束无效");
                }
            }
        }
        requireTenant(tenant);
        if (value == Long.MAX_VALUE) return result(true, value, false, true);
        // The WHERE bound avoids DM evaluating an overflowing addition in a CASE expression.
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + target + " SET " + counter
                + " = " + counter + " + 1 WHERE " + scope + " AND " + counter + " < ?")) {
            bind(update, arguments);
            update.setLong(arguments.size() + 1, Long.MAX_VALUE);
            if (update.executeUpdate() != 1) throw new IllegalStateException("计数记录状态已变化");
        }
        return result(true, value + 1L, true, false);
    }

    private static void bind(PreparedStatement statement, List<Object> arguments) throws SQLException {
        statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        for (int index = 0; index < arguments.size(); index++) {
            Object value = arguments.get(index);
            if (value instanceof Long number) statement.setLong(index + 1, number);
            else statement.setString(index + 1, (String) value);
        }
    }

    private static Map<String, Long> guards(Map<String, Object> expectedFields) {
        if (expectedFields == null || expectedFields.keySet().stream().anyMatch(key -> !GUARDS.contains(key))) {
            throw new IllegalArgumentException("计数条件列无效");
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (String key : GUARDS) {
            if (!expectedFields.containsKey(key)) continue;
            Object value = expectedFields.get(key);
            if (value == null) {
                result.put(key, null);
            } else if (value instanceof Number number) {
                try {
                    result.put(key, new BigDecimal(number.toString()).longValueExact());
                } catch (NumberFormatException | ArithmeticException invalid) {
                    throw new IllegalArgumentException("计数条件必须是有效整数", invalid);
                }
            } else {
                throw new IllegalArgumentException("计数条件必须是整数或空值");
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static String requireTenant(String expected) {
        String tenant = TenantContext.requireTenantId();
        if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()
                || expected != null && !expected.equals(tenant)) {
            throw new IllegalArgumentException("计数租户上下文无效或已变化");
        }
        return tenant;
    }

    private static void rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException | RuntimeException uncertain) {
            uncertain.addSuppressed(original);
            throw new IllegalStateException("计数回滚结果无法确认，请勿自动重复计数", uncertain);
        }
    }

    private static boolean retryableConflict(SQLException error) {
        Set<SQLException> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean conflict = false;
        for (SQLException item = error; item != null && visited.add(item); item = item.getNextException()) {
            String state = item.getSQLState();
            if (state != null && state.startsWith("08") || item.getErrorCode() != 0 && item.getErrorCode() != -7067) {
                return false;
            }
            conflict |= item.getErrorCode() == -7067;
        }
        return conflict;
    }

    private static String quoted(String quote, String identifier) {
        return quote + identifier + quote;
    }

    private static Map<String, Object> result(boolean matched, Long value, boolean incremented, boolean saturated) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", matched);
        result.put("value", value);
        result.put("incremented", incremented);
        result.put("saturated", saturated);
        return result;
    }

    private record Attempt(Map<String, Object> result, SQLException conflict) { }

    @FunctionalInterface
    interface RetryDelay {
        void pause(long milliseconds) throws InterruptedException;
    }
}
