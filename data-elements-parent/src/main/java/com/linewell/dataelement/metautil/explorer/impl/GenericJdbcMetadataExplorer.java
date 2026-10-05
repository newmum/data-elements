package com.linewell.dataelement.metautil.explorer.impl;

import com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.DatabaseInfo;
import com.linewell.dataelement.metautil.model.dto.IndexInfo;
import com.linewell.dataelement.metautil.model.dto.SampleDataResult;
import com.linewell.dataelement.metautil.model.dto.SampleRequest;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import com.linewell.dataelement.metautil.model.dto.TablePageResult;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generic JDBC metadata explorer.
 *
 * <p>Used for datasource types whose drivers expose enough standard
 * {@link DatabaseMetaData}: SQL Server, GBase 8a, Vertica and MaxCompute. Vendor
 * specific explorers remain preferable when a database needs special SQL.</p>
 */
@Slf4j
public class GenericJdbcMetadataExplorer extends AbstractMetadataExplorer {

    @Override
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        DatabaseInfo info = new DatabaseInfo();
        info.setDatabaseName(config.getDatabase());
        info.setDatabaseType(config.getDatabaseType().getDisplayName());
        try (Connection connection = getConnection(config)) {
            DatabaseMetaData meta = connection.getMetaData();
            info.setVersion(meta.getDatabaseProductVersion());
            List<String> schemas = getSchemas(config);
            info.setSchemas(schemas);
            List<TableInfo> tables = getTables(config);
            info.setTableCount((int) tables.stream()
                    .filter(t -> !"VIEW".equalsIgnoreCase(t.getTableType()))
                    .count());
            info.setViewCount((int) tables.stream()
                    .filter(t -> "VIEW".equalsIgnoreCase(t.getTableType()))
                    .count());
            info.setTotalSizeBytes(0L);
            info.setTotalSizeFormatted(formatBytes(0L));
        } catch (Exception e) {
            log.error("获取通用 JDBC 数据库信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库信息失败: " + e.getMessage(), e);
        }
        return info;
    }

    @Override
    public List<String> getDatabases(DataSourceConfig config) {
        List<String> databases = new ArrayList<>();
        try (Connection connection = getConnection(config);
             ResultSet rs = connection.getMetaData().getCatalogs()) {
            while (rs.next()) {
                String name = rs.getString("TABLE_CAT");
                if (name != null && !name.isBlank()) {
                    databases.add(name);
                }
            }
        } catch (Exception e) {
            log.warn("获取数据库列表失败: {}", e.getMessage());
        }
        if (databases.isEmpty() && config.getDatabase() != null && !config.getDatabase().isBlank()) {
            databases.add(config.getDatabase());
        }
        return databases;
    }

    @Override
    public List<String> getSchemas(DataSourceConfig config) {
        Set<String> schemas = new LinkedHashSet<>();
        try (Connection connection = getConnection(config)) {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getSchemas()) {
                while (rs.next()) {
                    String schema = rs.getString("TABLE_SCHEM");
                    if (isBusinessSchema(schema)) {
                        schemas.add(schema);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取 Schema 列表失败: {}", e.getMessage());
        }
        if (schemas.isEmpty() && config.getSchema() != null && !config.getSchema().isBlank()) {
            schemas.add(config.getSchema());
        }
        return new ArrayList<>(schemas);
    }

    @Override
    public List<TableInfo> getTables(DataSourceConfig config) {
        List<TableInfo> tables = new ArrayList<>();
        try (Connection connection = getConnection(config)) {
            DatabaseMetaData meta = connection.getMetaData();
            TableScope scope = scope(config, connection);
            collectTables(meta, scope.catalog(), scope.schema(), tables);
            if (tables.isEmpty() && scope.schema() != null) {
                collectTables(meta, scope.catalog(), scope.schema().toUpperCase(Locale.ROOT), tables);
            }
            if (tables.isEmpty()) {
                collectTables(meta, scope.catalog(), null, tables);
            }
            for (TableInfo table : tables) {
                try {
                    table.setColumnCount(getColumns(config, qualifiedName(table)).size());
                } catch (Exception e) {
                    log.warn("获取字段数失败: table={}, error={}", table.getTableName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("获取通用 JDBC 表列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表列表失败: " + e.getMessage(), e);
        }
        return tables;
    }

    @Override
    public TablePageResult getTablesPage(DataSourceConfig config, Integer pageNo, Integer pageSize, String keyword) {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 100 : Math.min(pageSize, 500);
        int start = (safePageNo - 1) * safePageSize;
        int end = start + safePageSize;
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        TablePageResult page = new TablePageResult();
        page.setPageNo(safePageNo);
        page.setPageSize(safePageSize);
        List<TableInfo> rows = new ArrayList<>();
        long matched = 0L;

        try (Connection connection = getConnection(config)) {
            DatabaseMetaData meta = connection.getMetaData();
            TableScope scope = scope(config, connection);
            matched += collectTablesPage(meta, scope.catalog(), scope.schema(), normalizedKeyword, start, end, rows, matched);
            if (matched == 0 && scope.schema() != null
                    && !scope.schema().equals(scope.schema().toUpperCase(Locale.ROOT))) {
                matched += collectTablesPage(
                        meta,
                        scope.catalog(),
                        scope.schema().toUpperCase(Locale.ROOT),
                        normalizedKeyword,
                        start,
                        end,
                        rows,
                        matched
                );
            }
            if (matched == 0 && scope.schema() == null) {
                matched += collectTablesPage(meta, scope.catalog(), null, normalizedKeyword, start, end, rows, matched);
            }
            page.setTotal(matched);
            page.setRows(rows);
            return page;
        } catch (Exception e) {
            log.error("通用 JDBC 分页获取表列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("分页获取表列表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public TableInfo getTableInfo(DataSourceConfig config, String tableName) {
        TableInfo info = getTables(config).stream()
                .filter(t -> sameTable(t, tableName))
                .findFirst()
                .orElseGet(() -> {
                    TableName name = parseTableName(config, tableName);
                    TableInfo fallback = new TableInfo();
                    fallback.setSchemaName(name.schema());
                    fallback.setTableName(name.table());
                    fallback.setTableType("TABLE");
                    return fallback;
                });
        info.setColumns(getColumns(config, tableName));
        info.setIndexes(getIndexes(config, tableName));
        return info;
    }

    @Override
    public List<ColumnInfo> getColumns(DataSourceConfig config, String tableName) {
        List<ColumnInfo> columns = new ArrayList<>();
        try (Connection connection = getConnection(config)) {
            DatabaseMetaData meta = connection.getMetaData();
            TableName name = resolveTableName(connection, config, tableName);
            collectColumns(meta, name.catalog(), name.schema(), name.table(), columns);
            if (columns.isEmpty() && name.schema() != null) {
                collectColumns(meta, name.catalog(), name.schema().toUpperCase(Locale.ROOT), name.table().toUpperCase(Locale.ROOT), columns);
            }
            markPrimaryKeys(meta, name, columns);
            markIndexedColumns(meta, name, columns);
        } catch (Exception e) {
            log.error("获取通用 JDBC 字段列表失败: table={}, error={}", tableName, e.getMessage(), e);
            throw new RuntimeException("获取字段列表失败: " + e.getMessage(), e);
        }
        return columns;
    }

    @Override
    public List<IndexInfo> getIndexes(DataSourceConfig config, String tableName) {
        Map<String, IndexInfo> indexes = new LinkedHashMap<>();
        try (Connection connection = getConnection(config)) {
            DatabaseMetaData meta = connection.getMetaData();
            TableName name = resolveTableName(connection, config, tableName);
            try (ResultSet rs = meta.getIndexInfo(name.catalog(), name.schema(), name.table(), false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    if (indexName == null || indexName.isBlank() || columnName == null || columnName.isBlank()) {
                        continue;
                    }
                    IndexInfo index = indexes.computeIfAbsent(indexName, k -> {
                        IndexInfo item = new IndexInfo();
                        item.setIndexName(k);
                        item.setUnique(!safeBoolean(rs, "NON_UNIQUE"));
                        item.setPrimaryKey("PRIMARY".equalsIgnoreCase(k) || "PK".equalsIgnoreCase(k));
                        item.setIndexType(String.valueOf(safeShort(rs, "TYPE")));
                        item.setColumns(new ArrayList<>());
                        return item;
                    });
                    index.getColumns().add(columnName);
                }
            }
        } catch (Exception e) {
            log.warn("获取通用 JDBC 索引失败: table={}, error={}", tableName, e.getMessage());
        }
        return new ArrayList<>(indexes.values());
    }

    @Override
    public Long getTableRowCount(DataSourceConfig config, String tableName) {
        String sql = "SELECT COUNT(1) FROM " + qualifiedSqlName(config, tableName);
        try (Connection connection = getConnection(config);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (Exception e) {
            log.warn("获取通用 JDBC 行数失败: table={}, error={}", tableName, e.getMessage());
            return 0L;
        }
    }

    @Override
    public Long getTableSize(DataSourceConfig config, String tableName) {
        return 0L;
    }

    @Override
    public Long getDatabaseSize(DataSourceConfig config) {
        return 0L;
    }

    @Override
    public SampleDataResult sampleData(SampleRequest request) {
        int size = request.getSampleSize() == null ? 20 : Math.max(1, Math.min(request.getSampleSize(), 1000));
        String baseSql = "SELECT " + buildColumnSelection(request.getColumns())
                + " FROM " + qualifiedSqlName(request.getDataSource(), request.getTableName())
                + buildWhereClause(request.getWhereClause());
        String sql = switch (request.getDataSource().getDatabaseType()) {
            case SQLSERVER -> baseSql.replaceFirst("(?i)^SELECT\\s+", "SELECT TOP " + size + " ");
            default -> baseSql + " LIMIT " + size;
        };
        return executeSampleQuery(request.getDataSource(), request, sql);
    }

    private void collectTables(DatabaseMetaData meta, String catalog, String schema, List<TableInfo> tables) throws Exception {
        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (tableName == null || tableName.isBlank()) {
                    continue;
                }
                TableInfo table = new TableInfo();
                table.setTableName(tableName);
                table.setTableComment(rs.getString("REMARKS"));
                table.setSchemaName(rs.getString("TABLE_SCHEM"));
                table.setTableType(normalizeTableType(rs.getString("TABLE_TYPE")));
                table.setRowCount(0L);
                table.setDataSizeBytes(0L);
                table.setIndexSizeBytes(0L);
                table.setTotalSizeBytes(0L);
                table.setDataSizeFormatted(formatBytes(0L));
                table.setIndexSizeFormatted(formatBytes(0L));
                table.setTotalSizeFormatted(formatBytes(0L));
                tables.add(table);
            }
        }
    }

    private long collectTablesPage(
            DatabaseMetaData meta,
            String catalog,
            String schema,
            String normalizedKeyword,
            int start,
            int end,
            List<TableInfo> rows,
            long existingMatched
    ) throws Exception {
        long matched = existingMatched;
        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (tableName == null || tableName.isBlank()) {
                    continue;
                }
                String comment = rs.getString("REMARKS");
                if (!normalizedKeyword.isEmpty()) {
                    String text = (tableName + " " + String.valueOf(comment)).toLowerCase(Locale.ROOT);
                    if (!text.contains(normalizedKeyword)) {
                        continue;
                    }
                }
                long currentIndex = matched++;
                if (currentIndex < start || currentIndex >= end) {
                    continue;
                }
                TableInfo table = new TableInfo();
                table.setTableName(tableName);
                table.setTableComment(comment);
                table.setSchemaName(rs.getString("TABLE_SCHEM"));
                table.setTableType(normalizeTableType(rs.getString("TABLE_TYPE")));
                table.setRowCount(0L);
                table.setColumnCount(0);
                table.setDataSizeBytes(0L);
                table.setIndexSizeBytes(0L);
                table.setTotalSizeBytes(0L);
                table.setDataSizeFormatted(formatBytes(0L));
                table.setIndexSizeFormatted(formatBytes(0L));
                table.setTotalSizeFormatted(formatBytes(0L));
                rows.add(table);
            }
        }
        return matched - existingMatched;
    }

    private void collectColumns(DatabaseMetaData meta, String catalog, String schema, String table, List<ColumnInfo> columns) throws Exception {
        try (ResultSet rs = meta.getColumns(catalog, schema, table, "%")) {
            while (rs.next()) {
                ColumnInfo column = new ColumnInfo();
                column.setColumnName(rs.getString("COLUMN_NAME"));
                column.setColumnComment(rs.getString("REMARKS"));
                column.setDataType(rs.getString("TYPE_NAME"));
                column.setColumnType(buildColumnType(rs));
                column.setLength(safeLong(rs, "COLUMN_SIZE"));
                column.setPrecision(safeInteger(rs, "COLUMN_SIZE"));
                column.setScale(safeInteger(rs, "DECIMAL_DIGITS"));
                column.setNullable(rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls);
                column.setDefaultValue(rs.getString("COLUMN_DEF"));
                column.setOrdinalPosition(safeInteger(rs, "ORDINAL_POSITION"));
                column.setPrimaryKey(false);
                column.setIndexed(false);
                columns.add(column);
            }
        }
    }

    private void markPrimaryKeys(DatabaseMetaData meta, TableName name, List<ColumnInfo> columns) throws Exception {
        Set<String> pkColumns = new LinkedHashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(name.catalog(), name.schema(), name.table())) {
            while (rs.next()) {
                pkColumns.add(lower(rs.getString("COLUMN_NAME")));
            }
        }
        for (ColumnInfo column : columns) {
            column.setPrimaryKey(pkColumns.contains(lower(column.getColumnName())));
        }
    }

    private void markIndexedColumns(DatabaseMetaData meta, TableName name, List<ColumnInfo> columns) throws Exception {
        Set<String> indexedColumns = new LinkedHashSet<>();
        try (ResultSet rs = meta.getIndexInfo(name.catalog(), name.schema(), name.table(), false, false)) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                if (columnName != null && !columnName.isBlank()) {
                    indexedColumns.add(lower(columnName));
                }
            }
        }
        for (ColumnInfo column : columns) {
            column.setIndexed(indexedColumns.contains(lower(column.getColumnName())));
        }
    }

    private TableScope scope(DataSourceConfig config, Connection connection) throws Exception {
        String catalog = config.getDatabase();
        if (config.getDatabaseType() == DatabaseType.VERTICA
                || config.getDatabaseType() == DatabaseType.POSTGRESQL
                || config.getDatabaseType() == DatabaseType.HETU) {
            catalog = null;
        }
        String schema = config.getSchema();
        if (schema == null || schema.isBlank()) {
            schema = connection.getSchema();
        }
        return new TableScope(blankToNull(catalog), blankToNull(schema));
    }

    private TableName resolveTableName(Connection connection, DataSourceConfig config, String tableName) throws Exception {
        TableName parsed = parseTableName(config, tableName);
        if (parsed.schema() != null) {
            return parsed;
        }
        TableScope scope = scope(config, connection);
        return new TableName(scope.catalog(), scope.schema(), parsed.table());
    }

    private TableName parseTableName(DataSourceConfig config, String tableName) {
        String clean = tableName == null ? "" : tableName.trim();
        String catalog = config.getDatabase();
        String schema = config.getSchema();
        if (clean.contains(".")) {
            String[] parts = clean.split("\\.");
            if (parts.length >= 2) {
                schema = stripQuote(parts[parts.length - 2]);
                clean = stripQuote(parts[parts.length - 1]);
            }
        }
        return new TableName(blankToNull(catalog), blankToNull(schema), stripQuote(clean));
    }

    private String qualifiedSqlName(DataSourceConfig config, String tableName) {
        TableName name = parseTableName(config, tableName);
        String table = quote(config, name.table());
        if (name.schema() == null || name.schema().isBlank()
                || config.getDatabaseType() == DatabaseType.MYSQL
                || config.getDatabaseType() == DatabaseType.GBASE8A
                || config.getDatabaseType() == DatabaseType.OCEANBASE_MYSQL
                || config.getDatabaseType() == DatabaseType.DORIS
                || config.getDatabaseType() == DatabaseType.STARROCKS
                || config.getDatabaseType() == DatabaseType.CLICKHOUSE
                || config.getDatabaseType() == DatabaseType.IOTDB) {
            return table;
        }
        return quote(config, name.schema()) + "." + table;
    }

    private String quote(DataSourceConfig config, String identifier) {
        String id = stripQuote(identifier);
        if (config.getDatabaseType() == DatabaseType.SQLSERVER) {
            return "[" + id.replace("]", "]]") + "]";
        }
        if (config.getDatabaseType() == DatabaseType.MYSQL
                || config.getDatabaseType() == DatabaseType.GBASE8A
                || config.getDatabaseType() == DatabaseType.OCEANBASE_MYSQL
                || config.getDatabaseType() == DatabaseType.DORIS
                || config.getDatabaseType() == DatabaseType.STARROCKS
                || config.getDatabaseType() == DatabaseType.CLICKHOUSE
                || config.getDatabaseType() == DatabaseType.IOTDB) {
            return "`" + id.replace("`", "``") + "`";
        }
        return "\"" + id.replace("\"", "\"\"") + "\"";
    }

    private String qualifiedName(TableInfo table) {
        if (table.getSchemaName() == null || table.getSchemaName().isBlank()) {
            return table.getTableName();
        }
        return table.getSchemaName() + "." + table.getTableName();
    }

    private boolean sameTable(TableInfo table, String tableName) {
        String expected = stripQuote(tableName);
        return table.getTableName().equalsIgnoreCase(expected)
                || qualifiedName(table).equalsIgnoreCase(expected);
    }

    private boolean isBusinessSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            return false;
        }
        String lower = schema.toLowerCase(Locale.ROOT);
        return !Set.of("information_schema", "sys", "mysql", "pg_catalog", "db_owner",
                "db_datareader", "db_datawriter", "guest").contains(lower);
    }

    private String normalizeTableType(String type) {
        if (type == null) {
            return "TABLE";
        }
        return type.toUpperCase(Locale.ROOT).contains("VIEW") ? "VIEW" : "TABLE";
    }

    private String buildColumnType(ResultSet rs) {
        String type = safeString(rs, "TYPE_NAME");
        int jdbcType = safeInteger(rs, "DATA_TYPE") == null ? Types.VARCHAR : safeInteger(rs, "DATA_TYPE");
        Integer size = safeInteger(rs, "COLUMN_SIZE");
        Integer scale = safeInteger(rs, "DECIMAL_DIGITS");
        if (type == null || type.isBlank()) {
            return "VARCHAR";
        }
        if (scale != null && scale > 0 && isNumberType(jdbcType)) {
            return type + "(" + size + "," + scale + ")";
        }
        if (size != null && size > 0 && supportsLength(jdbcType)) {
            return type + "(" + size + ")";
        }
        return type;
    }

    private boolean supportsLength(int jdbcType) {
        return switch (jdbcType) {
            case Types.CHAR, Types.VARCHAR, Types.NCHAR, Types.NVARCHAR, Types.BINARY, Types.VARBINARY -> true;
            default -> false;
        };
    }

    private boolean isNumberType(int jdbcType) {
        return switch (jdbcType) {
            case Types.NUMERIC, Types.DECIMAL, Types.DOUBLE, Types.FLOAT, Types.REAL -> true;
            default -> false;
        };
    }

    private String safeString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer safeInteger(ResultSet rs, String column) {
        try {
            int value = rs.getInt(column);
            return rs.wasNull() ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long safeLong(ResultSet rs, String column) {
        try {
            long value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean safeBoolean(ResultSet rs, String column) {
        try {
            return rs.getBoolean(column);
        } catch (Exception ignored) {
            return false;
        }
    }

    private short safeShort(ResultSet rs, String column) {
        try {
            return rs.getShort(column);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String stripQuote(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        if ((result.startsWith("\"") && result.endsWith("\""))
                || (result.startsWith("[") && result.endsWith("]"))
                || (result.startsWith("`") && result.endsWith("`"))) {
            result = result.substring(1, result.length() - 1);
        }
        return result;
    }

    private record TableScope(String catalog, String schema) {
    }

    private record TableName(String catalog, String schema, String table) {
    }
}
