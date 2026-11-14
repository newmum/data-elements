package com.linewell.dataelement.platform.magic;

import com.linewell.dataelement.platform.persistence.jdbc.JdbcValueNormalizer;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** 为 Magic 的现有租户写事务提供有界行锁和参数化更新。 */
@Component
@MagicModule("tableWrite")
public class StructuredTableWriteMagicModule {
    private static final String TENANT_COLUMN = "tenant_id";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");
    private static final int MAX_IN_VALUES = 1000;
    private static final Set<Class<?>> BIND_NUMBERS = Set.of(Byte.class, Short.class, Integer.class, Long.class,
            Float.class, Double.class, BigInteger.class, BigDecimal.class);
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public StructuredTableWriteMagicModule(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.dataSource = Objects.requireNonNull(jdbc.getDataSource());
    }

    @Comment("在当前写事务中按租户、主键和附加条件锁定单行，找不到时返回空值")
    public Map<String, Object> lockRow(String table, String tenantId, String keyColumn, Object keyValue,
            List<String> columns, Map<String, ?> conditions) {
        requireTenant(tenantId);
        String target = identifier(table);
        String key = identifier(keyColumn);
        var projection = columns(columns);
        var filters = tenantFilters(tenantId, conditions, Set.of(key));
        filters.put(key, keyValue(keyValue));
        var rows = lockMatchingRows(target, tenantId, projection, filters, null, 1);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Comment("在当前写事务中一次锁定指定租户的有界键集合，并返回现行记录")
    public List<Map<String, Object>> lockRows(String table, String tenantId, String keyColumn, List<?> keyValues,
            List<String> columns, Map<String, ?> conditions) {
        requireTenant(tenantId);
        String target = identifier(table);
        String key = identifier(keyColumn);
        var projection = columns(columns);
        var filters = tenantFilters(tenantId, conditions, Set.of(key));
        if (keyValues.size() > MAX_IN_VALUES) {
            throw new IllegalArgumentException("批量行锁最多接受 1000 个行键");
        }
        var keys = keyValues.stream().map(value -> jdbcScalar(keyValue(value))).distinct().toList();
        if (keys.size() != keyValues.size()) {
            throw new IllegalArgumentException("批量行锁行键不能重复");
        }
        if (keys.isEmpty()) return List.of();
        filters.put(key, keys);
        validateJdbcFilters(filters);
        return lockMatchingRows(target, tenantId, projection, filters, key, keys.size());
    }

    private List<Map<String, Object>> lockMatchingRows(String target, String tenantId, List<String> projection,
            Map<String, Object> filters, String orderColumn, int maximumRows) {
        return inTransaction(tenantId, (connection, quote) -> {
            var arguments = new ArrayList<Object>();
            String fields = projection.stream().map(column -> quoted(quote, column)).collect(Collectors.joining(", "));
            String order = orderColumn == null ? "" : " ORDER BY " + quoted(quote, orderColumn);
            String sql = "SELECT " + fields + " FROM " + quoted(quote, target) + " WHERE "
                    + where(quote, filters, arguments) + order + " FOR UPDATE";
            try (var statement = connection.prepareStatement(sql)) {
                prepare(statement, arguments);
                try (var rows = statement.executeQuery()) {
                    var result = new ArrayList<Map<String, Object>>();
                    while (rows.next()) {
                        if (result.size() == maximumRows) {
                            throw new IllegalStateException(orderColumn == null ? "结构化行锁匹配多条记录"
                                    : "批量行锁匹配行数超过指定键数");
                        }
                        var row = new LinkedHashMap<String, Object>();
                        for (int index = 0; index < projection.size(); index++) {
                            row.put(projection.get(index), JdbcValueNormalizer.normalize(rows.getObject(index + 1)));
                        }
                        result.add(row);
                    }
                    return result;
                }
            }
        });
    }

    @Comment("在当前写事务中按各行键批量更新，返回成功条目数，由调用方处理未匹配记录")
    public int batchUpdate(String table, String tenantId, List<String> valueColumns, List<String> keyColumns,
            Map<String, ?> conditions, List<Map<String, ?>> rows) {
        requireTenant(tenantId);
        String target = identifier(table);
        var values = columns(valueColumns);
        var keys = columns(keyColumns);
        var fields = new HashSet<>(values);
        fields.addAll(keys);
        if (fields.size() != values.size() + keys.size()) {
            throw new IllegalArgumentException("结构化更新字段与行键重复");
        }
        var filters = tenantFilters(tenantId, conditions, fields);
        filters.values().forEach(StructuredTableWriteMagicModule::scalar);
        var rowIds = new HashSet<Object>();
        var valueArguments = new ArrayList<List<Object>>(rows.size());
        var keyArguments = new ArrayList<List<Object>>(rows.size());
        for (var row : rows) {
            if (!row.keySet().equals(fields) || !rowIds.add(keyValue(row.get(keys.getFirst())))) {
                throw new IllegalArgumentException("结构化更新行的字段或主键无效");
            }
            valueArguments.add(values.stream().map(column -> scalar(row.get(column))).toList());
            keyArguments.add(keys.stream().map(column -> keyValue(row.get(column))).toList());
        }
        if (rows.isEmpty()) return 0;
        return inTransaction(tenantId, (connection, quote) -> {
            var fixed = new ArrayList<Object>();
            String assignments = equalsMarkers(quote, values, ", ");
            String sql = "UPDATE " + quoted(quote, target) + " SET " + assignments + " WHERE "
                    + where(quote, filters, fixed) + " AND " + equalsMarkers(quote, keys, " AND ");
            try (var statement = connection.prepareStatement(sql)) {
                DataSourceUtils.applyTimeout(statement, dataSource, jdbc.getQueryTimeout());
                for (int index = 0; index < valueArguments.size(); index++) {
                    var arguments = new ArrayList<>(valueArguments.get(index));
                    arguments.addAll(fixed);
                    arguments.addAll(keyArguments.get(index));
                    bind(statement, arguments);
                    statement.addBatch();
                }
                return matchedRows(statement.executeBatch(), valueArguments.size());
            }
        });
    }

    @Comment("在当前事务中按租户、行键和附加条件更新，使用数据库当前时间写入指定时间列")
    public int updateWithDatabaseTimestamp(String table, String tenantId, String keyColumn, Object keyValue,
            Map<String, ?> values, Map<String, ?> conditions, String timestampColumn) {
        return updateWithTimestampExpression(table, tenantId, keyColumn, keyValue, values, conditions,
                timestampColumn, "CURRENT_TIMESTAMP");
    }

    @Comment("在当前事务中使用指定小数秒精度的数据库时间，精度范围为 0 至 6")
    public int updateWithDatabaseTimestamp(String table, String tenantId, String keyColumn, Object keyValue,
            Map<String, ?> values, Map<String, ?> conditions, String timestampColumn, int fractionalPrecision) {
        if (fractionalPrecision < 0 || fractionalPrecision > 6) {
            throw new IllegalArgumentException("数据库时间精度必须在 0 至 6 之间");
        }
        return updateWithTimestampExpression(table, tenantId, keyColumn, keyValue, values, conditions,
                timestampColumn, "CURRENT_TIMESTAMP(" + fractionalPrecision + ")");
    }

    private int updateWithTimestampExpression(String table, String tenantId, String keyColumn, Object keyValue,
            Map<String, ?> values, Map<String, ?> conditions, String timestampColumn, String clockExpression) {
        requireTenant(tenantId);
        String target = identifier(table);
        String key = identifier(keyColumn);
        String timestamp = identifier(timestampColumn);
        if (key.equalsIgnoreCase(timestamp)) {
            throw new IllegalArgumentException("数据库时间列不能作为行键");
        }
        var filters = tenantFilters(tenantId, conditions, Set.of(key, timestamp));
        filters.put(key, jdbcScalar(keyValue(keyValue)));
        validateJdbcFilters(filters);

        var fields = columns(new ArrayList<>(values.keySet()));
        if (fields.stream().anyMatch(field -> field.equalsIgnoreCase(TENANT_COLUMN)
                || field.equalsIgnoreCase(key) || field.equalsIgnoreCase(timestamp))) {
            throw new IllegalArgumentException("数据库时间更新不能直接修改租户、行键或时间列");
        }
        var boundValues = fields.stream().map(field -> jdbcScalar(values.get(field))).toList();

        return inTransaction(tenantId, (connection, quote) -> {
            var arguments = new ArrayList<>(boundValues);
            String sql = "UPDATE " + quoted(quote, target) + " SET " + equalsMarkers(quote, fields, ", ")
                    + ", " + quoted(quote, timestamp) + " = " + clockExpression
                    + " WHERE " + where(quote, filters, arguments);
            try (var statement = connection.prepareStatement(sql)) {
                prepare(statement, arguments);
                int count = statement.executeUpdate();
                if (count < 0 || count > 1) {
                    throw new IllegalStateException("数据库时间更新匹配行数无效");
                }
                return count;
            }
        });
    }

    @Comment("在当前事务中按主键更新；仅当同一租户、同一关联键下存在满足条件的行才写入")
    public int updateIfExists(String table, String tenantId, String keyColumn, Object keyValue,
            Map<String, ?> values, Map<String, ?> targetConditions, String relatedTable, String relatedKeyColumn,
            Map<String, ?> relatedConditions) {
        requireTenant(tenantId);
        String target = identifier(table);
        String key = identifier(keyColumn);
        String related = identifier(relatedTable);
        String relatedKey = identifier(relatedKeyColumn);
        var fields = columns(new ArrayList<>(values.keySet()));
        if (target.equalsIgnoreCase(related)
                || fields.stream().anyMatch(field -> field.equalsIgnoreCase(TENANT_COLUMN) || field.equalsIgnoreCase(key))) {
            throw new IllegalArgumentException("关联条件更新不能使用同一张表或修改租户及主键");
        }
        Object boundKey = jdbcScalar(keyValue(keyValue));
        var argumentsForValues = fields.stream().map(field -> jdbcScalar(values.get(field))).toList();
        var targetFilters = tenantFilters(tenantId, targetConditions, Set.of(key));
        var relatedFilters = tenantFilters(tenantId, relatedConditions, Set.of(relatedKey));
        targetFilters.put(key, boundKey);
        relatedFilters.put(relatedKey, boundKey);
        validateJdbcFilters(targetFilters);
        validateJdbcFilters(relatedFilters);
        return inTransaction(tenantId, (connection, quote) -> {
            var arguments = new ArrayList<>(argumentsForValues);
            // 两侧绑定同一主键值，避免更新语句使用各数据库不同的目标表别名语法。
            String sql = "UPDATE " + quoted(quote, target) + " SET " + equalsMarkers(quote, fields, ", ")
                    + " WHERE " + where(quote, targetFilters, arguments)
                    + " AND EXISTS (SELECT 1 FROM " + quoted(quote, related)
                    + " WHERE " + where(quote, relatedFilters, arguments) + ")";
            try (var statement = connection.prepareStatement(sql)) {
                prepare(statement, arguments);
                int count = statement.executeUpdate();
                if (count < 0 || count > 1) {
                    throw new IllegalStateException("关联条件更新匹配行数无效");
                }
                return count;
            }
        });
    }

    private <T> T inTransaction(String tenantId, WriteOperation<T> operation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalStateException("结构化写入需要当前可写事务");
        }
        return jdbc.execute((ConnectionCallback<T>) connection -> {
            requireTenant(tenantId);
            Connection transactionConnection = DataSourceUtils.getTargetConnection(connection);
            if (!DataSourceUtils.isConnectionTransactional(transactionConnection, dataSource)
                    || connection.getAutoCommit() || connection.isReadOnly()) {
                throw new IllegalStateException("结构化写入连接未加入当前可写事务");
            }
            var metadata = connection.getMetaData();
            String quote = metadata.getURL().toLowerCase(Locale.ROOT).startsWith("jdbc:dm:")
                    ? "\"" : metadata.getIdentifierQuoteString().trim();
            if (!"\"".equals(quote) && !"`".equals(quote)) {
                throw new IllegalStateException("当前数据库不支持结构化写入标识符");
            }
            return operation.run(connection, quote);
        });
    }

    private void prepare(PreparedStatement statement, List<Object> arguments) throws SQLException {
        DataSourceUtils.applyTimeout(statement, dataSource, jdbc.getQueryTimeout());
        bind(statement, arguments);
    }

    private static void bind(PreparedStatement statement, List<Object> arguments) throws SQLException {
        new ArgumentPreparedStatementSetter(arguments.toArray()).setValues(statement);
    }

    private static int matchedRows(int[] counts, int expected) {
        if (counts.length != expected) throw new IllegalStateException("结构化更新未返回全部批次结果");
        int matched = 0;
        for (int count : counts) {
            if (count == 1 || count == Statement.SUCCESS_NO_INFO) matched++;
            else if (count != 0) throw new IllegalStateException("结构化更新行数无效");
        }
        return matched;
    }

    private static String where(String quote, Map<String, ?> filters, List<Object> arguments) {
        var predicates = new ArrayList<String>(filters.size());
        filters.forEach((key, value) -> {
            String column = quoted(quote, key);
            if (value instanceof List<?> list) {
                predicates.add(in(column, list, arguments));
            } else if (value == null) {
                predicates.add(column + " IS NULL");
            } else {
                predicates.add(column + " = ?");
                arguments.add(scalar(value));
            }
        });
        return String.join(" AND ", predicates);
    }

    private static String in(String column, List<?> values, List<Object> arguments) {
        if (values.isEmpty()) return "1 = 0";
        var predicates = new ArrayList<String>();
        for (int start = 0; start < values.size(); start += MAX_IN_VALUES) {
            var batch = values.subList(start, Math.min(start + MAX_IN_VALUES, values.size()));
            predicates.add(column + " IN (" + String.join(", ", Collections.nCopies(batch.size(), "?")) + ")");
            batch.forEach(value -> arguments.add(scalar(value)));
        }
        return "(" + String.join(" OR ", predicates) + ")";
    }

    private static String equalsMarkers(String quote, List<String> columns, String separator) {
        return columns.stream().map(column -> quoted(quote, column) + " = ?").collect(Collectors.joining(separator));
    }

    private static Map<String, Object> tenantFilters(String tenantId, Map<String, ?> conditions, Set<String> rowFields) {
        var reserved = new HashSet<String>();
        reserved.add(TENANT_COLUMN);
        for (String field : rowFields) {
            if (!reserved.add(field.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("结构化写入字段或租户条件重复");
            }
        }
        var filters = new LinkedHashMap<String, Object>();
        filters.put(TENANT_COLUMN, tenantId);
        conditions.forEach((field, value) -> {
            if (!reserved.add(identifier(field).toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("结构化写入条件重复");
            }
            filters.put(field, value);
        });
        return filters;
    }

    private static List<String> columns(List<String> names) {
        var columns = names.stream().map(StructuredTableWriteMagicModule::identifier).toList();
        if (columns.isEmpty() || columns.stream().map(name -> name.toLowerCase(Locale.ROOT)).distinct().count() != columns.size()) {
            throw new IllegalArgumentException("结构化写入字段不能为空或重复");
        }
        return columns;
    }

    private static Object keyValue(Object value) {
        if (value == null || value instanceof String text && text.isBlank()) {
            throw new IllegalArgumentException("结构化写入行键不能为空");
        }
        return scalar(value);
    }

    private static Object scalar(Object value) {
        if (value instanceof Collection<?> || value instanceof Map<?, ?>
                || value != null && value.getClass().isArray() && !(value instanceof byte[])) {
            throw new IllegalArgumentException("结构化写入值必须为标量");
        }
        return value;
    }

    private static void validateJdbcFilters(Map<String, ?> filters) {
        for (Object value : filters.values()) {
            if (value instanceof List<?> list) {
                list.forEach(StructuredTableWriteMagicModule::jdbcScalar);
            } else {
                jdbcScalar(value);
            }
        }
    }

    private static Object jdbcScalar(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof byte[]
                || value instanceof Date || value instanceof LocalDate || value instanceof LocalDateTime
                || value instanceof LocalTime || value instanceof OffsetDateTime || value instanceof OffsetTime
                || BIND_NUMBERS.contains(value.getClass())) {
            return value;
        }
        throw new IllegalArgumentException("结构化更新只接受 JDBC 标量值");
    }

    private static String identifier(String value) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("结构化写入标识符无效");
        }
        return value;
    }

    private static String quoted(String quote, String identifier) {
        return quote + identifier + quote;
    }

    private static void requireTenant(String tenantId) {
        if (!TenantContext.requireTenantId().equals(tenantId) || TenantContext.usesControlDatabase()
                || TenantContext.isIgnored()) {
            throw new IllegalArgumentException("结构化写入租户无效");
        }
    }

    @FunctionalInterface
    private interface WriteOperation<T> {
        T run(Connection connection, String quote) throws SQLException;
    }
}
