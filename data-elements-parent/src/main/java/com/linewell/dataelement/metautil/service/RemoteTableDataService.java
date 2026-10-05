package com.linewell.dataelement.metautil.service;

import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import com.linewell.dataelement.metautil.jdbc.JdbcDriverPropertyResolver;
import com.linewell.dataelement.model.common.BizException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Magic API 使用的远程关系表底层读写能力。
 *
 * <p>参数校验、操作编排和接口响应由 Magic 脚本负责；本服务只处理 JDBC
 * 连接、标识符引用、参数绑定和事务，避免在脚本中拼接不安全的 SQL。</p>
 */
@Service
@RequiredArgsConstructor
public class RemoteTableDataService {

    private final MetadataExplorerService metadataExplorerService;

    public long count(DataSourceConfig config, String tableName) {
        Long count = metadataExplorerService.getTableRowCount(config, tableName);
        return count == null ? 0L : count;
    }

    public List<String> listTables(DataSourceConfig config) {
        List<TableInfo> tables = metadataExplorerService.getTables(config);
        if (tables == null || tables.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (TableInfo table : tables) {
            if (table != null && table.getTableName() != null && !table.getTableName().isBlank()) {
                names.add(table.getTableName());
            }
        }
        return names;
    }

    public int batchInsert(
        DataSourceConfig config,
        String tableName,
        List<Map<String, Object>> rows,
        Integer requestedBatchSize
    ) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int batchSize = requestedBatchSize == null || requestedBatchSize < 1
            ? 500
            : Math.min(requestedBatchSize, 2000);
        Set<String> columns = new LinkedHashSet<>(rows.get(0).keySet());
        if (columns.isEmpty()) {
            throw new BizException(500, "插入字段不能为空");
        }

        try (Connection connection = openConnection(config)) {
            String table = quoteQualifiedIdentifier(connection, tableName);
            List<String> quotedColumns = new ArrayList<>();
            for (String column : columns) {
                quotedColumns.add(quoteIdentifier(connection, column));
            }
            String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
            String sql = "INSERT INTO " + table + " (" + String.join(", ", quotedColumns)
                + ") VALUES (" + placeholders + ")";
            return executeBatches(connection, sql, new ArrayList<>(columns), rows, batchSize);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "批量写入远程表失败: " + e.getMessage());
        }
    }

    public int deleteByValues(
        DataSourceConfig config,
        String tableName,
        String columnName,
        List<?> values
    ) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        try (Connection connection = openConnection(config)) {
            String sql = "DELETE FROM " + quoteQualifiedIdentifier(connection, tableName)
                + " WHERE " + quoteIdentifier(connection, columnName)
                + " IN (" + String.join(", ", Collections.nCopies(values.size(), "?")) + ")";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, values);
                return statement.executeUpdate();
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "按字段值删除远程表数据失败: " + e.getMessage());
        }
    }

    public int deleteBefore(
        DataSourceConfig config,
        String tableName,
        String columnName,
        Object upperBound
    ) {
        try (Connection connection = openConnection(config)) {
            String sql = "DELETE FROM " + quoteQualifiedIdentifier(connection, tableName)
                + " WHERE " + quoteIdentifier(connection, columnName) + " < ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, upperBound);
                return statement.executeUpdate();
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "清理远程表历史数据失败: " + e.getMessage());
        }
    }

    public int deleteAll(DataSourceConfig config, List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return 0;
        }
        try (Connection connection = openConnection(config)) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            int affected = 0;
            try {
                for (String tableName : tableNames) {
                    String sql = "DELETE FROM " + quoteQualifiedIdentifier(connection, tableName);
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        affected += statement.executeUpdate();
                    }
                }
                connection.commit();
                return affected;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "清空远程表数据失败: " + e.getMessage());
        }
    }

    private int executeBatches(
        Connection connection,
        String sql,
        List<String> columns,
        List<Map<String, Object>> rows,
        int batchSize
    ) throws Exception {
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        int affected = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int pending = 0;
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    statement.setObject(i + 1, row.get(columns.get(i)));
                }
                statement.addBatch();
                pending++;
                if (pending >= batchSize) {
                    affected += sum(statement.executeBatch());
                    pending = 0;
                }
            }
            if (pending > 0) {
                affected += sum(statement.executeBatch());
            }
            connection.commit();
            return affected;
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private Connection openConnection(DataSourceConfig config) throws Exception {
        if (config == null || config.getDatabaseType() == null) {
            throw new BizException(500, "数据源配置和数据库类型不能为空");
        }
        if (config.getDatabaseType() == DatabaseType.MONGODB) {
            throw new BizException(500, "远程表写入不支持 MongoDB");
        }
        Properties properties = new Properties();
        properties.setProperty("user", config.getUsername());
        properties.setProperty("password", config.getPassword());
        JdbcDriverPropertyResolver.apply(properties, config.getJdbcProperties());
        return DriverManager.getConnection(config.buildJdbcUrl(), properties);
    }

    private String quoteQualifiedIdentifier(Connection connection, String identifier) throws Exception {
        if (identifier == null || identifier.isBlank()) {
            throw new BizException(500, "表名不能为空");
        }
        String[] parts = identifier.trim().split("\\.");
        List<String> quoted = new ArrayList<>();
        for (String part : parts) {
            quoted.add(quoteIdentifier(connection, part));
        }
        return String.join(".", quoted);
    }

    private String quoteIdentifier(Connection connection, String identifier) throws Exception {
        String value = identifier == null ? "" : identifier.trim();
        if (value.isEmpty() || !value.matches("[\\p{L}\\p{N}_$#]+")) {
            throw new BizException(500, "非法数据库标识符: " + identifier);
        }
        String quote = connection.getMetaData().getIdentifierQuoteString();
        quote = quote == null ? "" : quote.trim();
        if (quote.isEmpty()) {
            return value;
        }
        return quote + value.replace(quote, quote + quote) + quote;
    }

    private void bind(PreparedStatement statement, List<?> values) throws Exception {
        for (int i = 0; i < values.size(); i++) {
            statement.setObject(i + 1, values.get(i));
        }
    }

    private int sum(int[] results) {
        int total = 0;
        for (int result : results) {
            if (result > 0) {
                total += result;
            } else if (result == Statement.SUCCESS_NO_INFO) {
                total++;
            }
        }
        return total;
    }
}
