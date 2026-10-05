package com.linewell.dataelement.metautil.explorer.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.*;

import lombok.extern.slf4j.Slf4j;

/**
 * MySQL 元数据探查器
 * 
 * @author MetaUtil
 */
@Slf4j
public class MySqlMetadataExplorer extends AbstractMetadataExplorer {

    @Override
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        DatabaseInfo info = new DatabaseInfo();
        info.setDatabaseName(config.getDatabase());
        info.setDatabaseType("MySQL");
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            
            // 获取版本
            try (ResultSet rs = stmt.executeQuery("SELECT VERSION()")) {
                if (rs.next()) {
                    info.setVersion(rs.getString(1));
                }
            }
            
            // 获取字符集和排序规则
            String dbInfoSql = String.format(
                "SELECT DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME " +
                "FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '%s'",
                config.getDatabase()
            );
            try (ResultSet rs = stmt.executeQuery(dbInfoSql)) {
                if (rs.next()) {
                    info.setCharset(rs.getString("DEFAULT_CHARACTER_SET_NAME"));
                    info.setCollation(rs.getString("DEFAULT_COLLATION_NAME"));
                }
            }
            
            // 获取数据库大小
            String sizeSql = String.format(
                "SELECT SUM(data_length) as data_size, " +
                "SUM(index_length) as index_size, " +
                "SUM(data_length + index_length) as total_size, " +
                "COUNT(*) as table_count " +
                "FROM information_schema.TABLES WHERE table_schema = '%s'",
                config.getDatabase()
            );
            try (ResultSet rs = stmt.executeQuery(sizeSql)) {
                if (rs.next()) {
                    info.setDataSizeBytes(rs.getLong("data_size"));
                    info.setIndexSizeBytes(rs.getLong("index_size"));
                    info.setTotalSizeBytes(rs.getLong("total_size"));
                    info.setTableCount(rs.getInt("table_count"));
                    
                    info.setDataSizeFormatted(formatBytes(info.getDataSizeBytes()));
                    info.setIndexSizeFormatted(formatBytes(info.getIndexSizeBytes()));
                    info.setTotalSizeFormatted(formatBytes(info.getTotalSizeBytes()));
                }
            }

            // 获取视图数量
            String viewCountSql = String.format(
                "SELECT COUNT(*) AS view_count FROM information_schema.TABLES " +
                    "WHERE TABLE_SCHEMA = '%s' AND TABLE_TYPE = 'VIEW'",
                config.getDatabase()
            );
            try (ResultSet rs = stmt.executeQuery(viewCountSql)) {
                if (rs.next()) {
                    info.setViewCount(rs.getInt("view_count"));
                }
            } catch (Exception e) {
                log.warn("获取MySQL视图数量失败: {}", e.getMessage());
            }

            // 获取存储过程数量
            String procedureCountSql = String.format(
                "SELECT COUNT(*) AS procedure_count FROM information_schema.ROUTINES " +
                    "WHERE ROUTINE_SCHEMA = '%s' AND ROUTINE_TYPE = 'PROCEDURE'",
                config.getDatabase()
            );
            try (ResultSet rs = stmt.executeQuery(procedureCountSql)) {
                if (rs.next()) {
                    info.setProcedureCount(rs.getInt("procedure_count"));
                }
            } catch (Exception e) {
                log.warn("获取MySQL存储过程数量失败: {}", e.getMessage());
            }

            // 获取用户数量（可能受权限影响）
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS user_count FROM mysql.user")) {
                if (rs.next()) {
                    info.setUserCount(rs.getInt("user_count"));
                }
            } catch (Exception e) {
                log.warn("获取MySQL用户数量失败: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("获取MySQL数据库信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库信息失败: " + e.getMessage(), e);
        }
        
        return info;
    }

    @Override
    public List<String> getDatabases(DataSourceConfig config) {
        List<String> databases = new ArrayList<>();
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
            
            while (rs.next()) {
                String dbName = rs.getString(1);
                // 过滤系统数据库
                if (!"information_schema".equals(dbName) && 
                    !"mysql".equals(dbName) && 
                    !"performance_schema".equals(dbName) &&
                    !"sys".equals(dbName)) {
                    databases.add(dbName);
                }
            }
            
        } catch (Exception e) {
            log.error("获取数据库列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库列表失败: " + e.getMessage(), e);
        }
        
        return databases;
    }

    @Override
    public List<String> getSchemas(DataSourceConfig config) {
        // MySQL 中 schema 等同于 database
        return getDatabases(config);
    }

    @Override
    public List<TableInfo> getTables(DataSourceConfig config) {
        List<TableInfo> tables = new ArrayList<>();
        
        String sql = String.format(
            "SELECT TABLE_NAME, TABLE_COMMENT, TABLE_TYPE, ENGINE, " +
            "TABLE_COLLATION, TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH, " +
            "AUTO_INCREMENT, CREATE_TIME, UPDATE_TIME " +
            "FROM information_schema.TABLES WHERE TABLE_SCHEMA = '%s' " +
            "ORDER BY TABLE_NAME",
            config.getDatabase()
        );
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                TableInfo table = new TableInfo();
                table.setTableName(rs.getString("TABLE_NAME"));
                table.setTableComment(rs.getString("TABLE_COMMENT"));
                table.setTableType(rs.getString("TABLE_TYPE"));
                table.setEngine(rs.getString("ENGINE"));
                table.setCollation(rs.getString("TABLE_COLLATION"));
                table.setRowCount(rs.getLong("TABLE_ROWS"));
                table.setDataSizeBytes(rs.getLong("DATA_LENGTH"));
                table.setIndexSizeBytes(rs.getLong("INDEX_LENGTH"));
                table.setTotalSizeBytes(table.getDataSizeBytes() + table.getIndexSizeBytes());
                table.setAutoIncrement(rs.getLong("AUTO_INCREMENT"));
                table.setCreateTime(rs.getTimestamp("CREATE_TIME"));
                table.setUpdateTime(rs.getTimestamp("UPDATE_TIME"));
                table.setDataUpdateTime(rs.getTimestamp("UPDATE_TIME"));
                
                table.setDataSizeFormatted(formatBytes(table.getDataSizeBytes()));
                table.setIndexSizeFormatted(formatBytes(table.getIndexSizeBytes()));
                table.setTotalSizeFormatted(formatBytes(table.getTotalSizeBytes()));

                tables.add(table);
            }

            Map<String, Integer> columnCountMap = new HashMap<>();
            String columnCountSql = "SELECT TABLE_NAME, COUNT(*) AS column_count " +
                "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? GROUP BY TABLE_NAME";
            try (PreparedStatement ps = conn.prepareStatement(columnCountSql)) {
                ps.setString(1, config.getDatabase());
                try (ResultSet crs = ps.executeQuery()) {
                    while (crs.next()) {
                        columnCountMap.put(crs.getString("TABLE_NAME"), crs.getInt("column_count"));
                    }
                }
            } catch (Exception e) {
                log.warn("批量获取字段数失败: {}", e.getMessage());
            }

            for (TableInfo table : tables) {
                Integer cnt = columnCountMap.get(table.getTableName());
                if (cnt != null) {
                    table.setColumnCount(cnt);
                }
            }
            
        } catch (Exception e) {
            log.error("获取表列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表列表失败: " + e.getMessage(), e);
        }
        
        return tables;
    }

    @Override
    public TablePageResult getTablesPage(DataSourceConfig config, Integer pageNo, Integer pageSize, String keyword) {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 500);
        int offset = (safePageNo - 1) * safePageSize;
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty()
            ? null
            : "%" + keyword.trim().toLowerCase() + "%";

        String whereSql =
            "FROM information_schema.TABLES t " +
            "LEFT JOIN ( " +
            "    SELECT TABLE_SCHEMA, TABLE_NAME, COUNT(*) AS column_count " +
            "    FROM information_schema.COLUMNS " +
            "    WHERE TABLE_SCHEMA = ? " +
            "    GROUP BY TABLE_SCHEMA, TABLE_NAME " +
            ") c ON c.TABLE_SCHEMA = t.TABLE_SCHEMA AND c.TABLE_NAME = t.TABLE_NAME " +
            "WHERE t.TABLE_SCHEMA = ? " +
            "  AND (? IS NULL OR LOWER(t.TABLE_NAME) LIKE ? OR LOWER(COALESCE(t.TABLE_COMMENT, '')) LIKE ?) ";
        String countSql = "SELECT COUNT(1) " + whereSql;
        String pageSql =
            "SELECT t.TABLE_NAME, t.TABLE_COMMENT, t.TABLE_TYPE, t.ENGINE, " +
            "       t.TABLE_COLLATION, t.TABLE_ROWS, t.DATA_LENGTH, t.INDEX_LENGTH, " +
            "       t.AUTO_INCREMENT, t.CREATE_TIME, t.UPDATE_TIME, COALESCE(c.column_count, 0) AS column_count " +
            whereSql +
            "ORDER BY t.TABLE_NAME LIMIT ? OFFSET ?";

        TablePageResult result = new TablePageResult();
        result.setPageNo(safePageNo);
        result.setPageSize(safePageSize);
        List<TableInfo> rows = new ArrayList<>();

        try (Connection conn = getConnection(config)) {
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                int index = bindPageParams(ps, config.getDatabase(), normalizedKeyword);
                try (ResultSet rs = ps.executeQuery()) {
                    result.setTotal(rs.next() ? rs.getLong(1) : 0L);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(pageSql)) {
                int index = bindPageParams(ps, config.getDatabase(), normalizedKeyword);
                ps.setInt(index++, safePageSize);
                ps.setInt(index, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(readTableSummary(rs));
                    }
                }
            }
            result.setRows(rows);
            return result;
        } catch (Exception e) {
            log.error("MySQL鍒嗛〉鑾峰彇琛ㄥ垪琛ㄥけ璐? {}", e.getMessage(), e);
            throw new RuntimeException("MySQL鍒嗛〉鑾峰彇琛ㄥ垪琛ㄥけ璐? " + e.getMessage(), e);
        }
    }

    private int bindPageParams(PreparedStatement ps, String database, String normalizedKeyword) throws Exception {
        ps.setString(1, database);
        ps.setString(2, database);
        ps.setString(3, normalizedKeyword);
        ps.setString(4, normalizedKeyword == null ? "" : normalizedKeyword);
        ps.setString(5, normalizedKeyword == null ? "" : normalizedKeyword);
        return 6;
    }

    private TableInfo readTableSummary(ResultSet rs) throws Exception {
        TableInfo table = new TableInfo();
        table.setTableName(rs.getString("TABLE_NAME"));
        table.setTableComment(rs.getString("TABLE_COMMENT"));
        table.setTableType(rs.getString("TABLE_TYPE"));
        table.setEngine(rs.getString("ENGINE"));
        table.setCollation(rs.getString("TABLE_COLLATION"));
        table.setRowCount(rs.getLong("TABLE_ROWS"));
        table.setDataSizeBytes(rs.getLong("DATA_LENGTH"));
        table.setIndexSizeBytes(rs.getLong("INDEX_LENGTH"));
        table.setTotalSizeBytes(table.getDataSizeBytes() + table.getIndexSizeBytes());
        table.setAutoIncrement(rs.getLong("AUTO_INCREMENT"));
        table.setCreateTime(rs.getTimestamp("CREATE_TIME"));
        table.setUpdateTime(rs.getTimestamp("UPDATE_TIME"));
        table.setDataUpdateTime(rs.getTimestamp("UPDATE_TIME"));
        table.setColumnCount(rs.getInt("column_count"));
        table.setDataSizeFormatted(formatBytes(table.getDataSizeBytes()));
        table.setIndexSizeFormatted(formatBytes(table.getIndexSizeBytes()));
        table.setTotalSizeFormatted(formatBytes(table.getTotalSizeBytes()));
        return table;
    }

    @Override
    public TableInfo getTableInfo(DataSourceConfig config, String tableName) {
        List<TableInfo> tables = getTables(config);
        TableInfo tableInfo = tables.stream()
                .filter(t -> t.getTableName().equalsIgnoreCase(tableName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("表不存在: " + tableName));
        
        // 获取字段信息
        tableInfo.setColumns(getColumns(config, tableName));
        // 获取索引信息
        tableInfo.setIndexes(getIndexes(config, tableName));
        
        return tableInfo;
    }

    @Override
    public List<ColumnInfo> getColumns(DataSourceConfig config, String tableName) {
        List<ColumnInfo> columns = new ArrayList<>();
        
        String sql = String.format(
            "SELECT COLUMN_NAME, COLUMN_COMMENT, DATA_TYPE, COLUMN_TYPE, " +
            "CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE, " +
            "IS_NULLABLE, COLUMN_DEFAULT, COLUMN_KEY, EXTRA, " +
            "ORDINAL_POSITION, CHARACTER_SET_NAME, COLLATION_NAME " +
            "FROM information_schema.COLUMNS " +
            "WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s' " +
            "ORDER BY ORDINAL_POSITION",
            config.getDatabase(), tableName
        );
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                ColumnInfo column = new ColumnInfo();
                column.setColumnName(rs.getString("COLUMN_NAME"));
                column.setColumnComment(rs.getString("COLUMN_COMMENT"));
                column.setDataType(rs.getString("DATA_TYPE"));
                column.setColumnType(rs.getString("COLUMN_TYPE"));
                column.setLength(rs.getLong("CHARACTER_MAXIMUM_LENGTH"));
                column.setPrecision(rs.getInt("NUMERIC_PRECISION"));
                column.setScale(rs.getInt("NUMERIC_SCALE"));
                column.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
                column.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
                
                String columnKey = rs.getString("COLUMN_KEY");
                column.setPrimaryKey("PRI".equals(columnKey));
                column.setUnique("UNI".equals(columnKey) || "PRI".equals(columnKey));
                column.setIndexed(columnKey != null && !columnKey.isEmpty());
                
                String extra = rs.getString("EXTRA");
                column.setAutoIncrement(extra != null && extra.contains("auto_increment"));
                column.setExtra(extra);
                
                column.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
                column.setCharset(rs.getString("CHARACTER_SET_NAME"));
                column.setCollation(rs.getString("COLLATION_NAME"));
                
                columns.add(column);
            }
            
        } catch (Exception e) {
            log.error("获取字段列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取字段列表失败: " + e.getMessage(), e);
        }
        
        return columns;
    }

    @Override
    public Map<String, List<ColumnInfo>> getColumnsByTables(
            DataSourceConfig config, List<String> tableNames) {
        Map<String, List<ColumnInfo>> result = new LinkedHashMap<>();
        if (tableNames == null || tableNames.isEmpty()) {
            return result;
        }
        List<String> requestedNames = new ArrayList<>();
        for (String tableName : tableNames) {
            if (tableName != null && !tableName.isBlank()) {
                String key = tableName.toLowerCase(Locale.ROOT);
                if (!result.containsKey(key)) {
                    result.put(key, new ArrayList<>());
                    requestedNames.add(tableName);
                }
            }
        }
        if (result.isEmpty()) {
            return result;
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(result.size(), "?"));
        String sql = "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT, DATA_TYPE, COLUMN_TYPE, "
                + "CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE, IS_NULLABLE, "
                + "COLUMN_DEFAULT, COLUMN_KEY, EXTRA, ORDINAL_POSITION, CHARACTER_SET_NAME, COLLATION_NAME "
                + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME IN ("
                + placeholders + ") ORDER BY TABLE_NAME, ORDINAL_POSITION";
        try (Connection conn = getConnection(config);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, config.getDatabase());
            int parameter = 2;
            for (String tableName : requestedNames) {
                ps.setString(parameter++, tableName);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ColumnInfo column = readColumnInfo(rs);
                    result.computeIfAbsent(
                            rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT),
                            ignored -> new ArrayList<>()).add(column);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Batch column lookup failed: {}", e.getMessage(), e);
            throw new RuntimeException("Batch column lookup failed: " + e.getMessage(), e);
        }
    }

    private ColumnInfo readColumnInfo(ResultSet rs) throws Exception {
        ColumnInfo column = new ColumnInfo();
        column.setColumnName(rs.getString("COLUMN_NAME"));
        column.setColumnComment(rs.getString("COLUMN_COMMENT"));
        column.setDataType(rs.getString("DATA_TYPE"));
        column.setColumnType(rs.getString("COLUMN_TYPE"));
        column.setLength(rs.getLong("CHARACTER_MAXIMUM_LENGTH"));
        column.setPrecision(rs.getInt("NUMERIC_PRECISION"));
        column.setScale(rs.getInt("NUMERIC_SCALE"));
        column.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
        column.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
        String columnKey = rs.getString("COLUMN_KEY");
        column.setPrimaryKey("PRI".equals(columnKey));
        column.setUnique("UNI".equals(columnKey) || "PRI".equals(columnKey));
        column.setIndexed(columnKey != null && !columnKey.isEmpty());
        String extra = rs.getString("EXTRA");
        column.setAutoIncrement(extra != null && extra.contains("auto_increment"));
        column.setExtra(extra);
        column.setOrdinalPosition(rs.getInt("ORDINAL_POSITION"));
        column.setCharset(rs.getString("CHARACTER_SET_NAME"));
        column.setCollation(rs.getString("COLLATION_NAME"));
        return column;
    }

    @Override
    public List<IndexInfo> getIndexes(DataSourceConfig config, String tableName) {
        List<IndexInfo> indexes = new ArrayList<>();
        
        String sql = String.format("SHOW INDEX FROM `%s`", tableName);
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            // 按索引名分组
            java.util.Map<String, IndexInfo> indexMap = new java.util.LinkedHashMap<>();
            
            while (rs.next()) {
                String indexName = rs.getString("Key_name");
                IndexInfo index = indexMap.get(indexName);
                
                if (index == null) {
                    index = new IndexInfo();
                    index.setIndexName(indexName);
                    index.setUnique(rs.getInt("Non_unique") == 0);
                    index.setPrimaryKey("PRIMARY".equals(indexName));
                    index.setIndexType(rs.getString("Index_type"));
                    index.setComment(rs.getString("Index_comment"));
                    index.setColumns(new ArrayList<>());
                    indexMap.put(indexName, index);
                }
                
                index.getColumns().add(rs.getString("Column_name"));
            }
            
            indexes.addAll(indexMap.values());
            
        } catch (Exception e) {
            log.error("获取索引列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取索引列表失败: " + e.getMessage(), e);
        }
        
        return indexes;
    }

    @Override
    public Long getTableRowCount(DataSourceConfig config, String tableName) {
        String sql = String.format("SELECT COUNT(*) FROM `%s`", tableName);
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong(1);
            }
            
        } catch (Exception e) {
            log.error("获取表记录数失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表记录数失败: " + e.getMessage(), e);
        }
        
        return 0L;
    }

    @Override
    public Long getTableSize(DataSourceConfig config, String tableName) {
        String sql = String.format(
            "SELECT DATA_LENGTH + INDEX_LENGTH as total_size " +
            "FROM information_schema.TABLES " +
            "WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'",
            config.getDatabase(), tableName
        );
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong("total_size");
            }
            
        } catch (Exception e) {
            log.error("获取表大小失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表大小失败: " + e.getMessage(), e);
        }
        
        return 0L;
    }

    @Override
    public Long getDatabaseSize(DataSourceConfig config) {
        String sql = String.format(
            "SELECT SUM(DATA_LENGTH + INDEX_LENGTH) as total_size " +
            "FROM information_schema.TABLES WHERE TABLE_SCHEMA = '%s'",
            config.getDatabase()
        );
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getLong("total_size");
            }
            
        } catch (Exception e) {
            log.error("获取数据库大小失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库大小失败: " + e.getMessage(), e);
        }
        
        return 0L;
    }

    @Override
    public SampleDataResult sampleData(SampleRequest request) {
        String columns = buildColumnSelection(request.getColumns());
        String where = buildWhereClause(request.getWhereClause());
        String tableName = request.getTableName();
        int sampleSize = request.getSampleSize();
        
        String sql;
        switch (request.getSampleMethod()) {
            case RANDOM:
                sql = String.format(
                    "SELECT %s FROM `%s` %s ORDER BY RAND() LIMIT %d",
                    columns, tableName, where, sampleSize
                );
                break;
            case LAST:
                sql = String.format(
                    "SELECT * FROM (SELECT %s FROM `%s` %s ORDER BY 1 DESC LIMIT %d) t ORDER BY 1 ASC",
                    columns, tableName, where, sampleSize
                );
                break;
            case FIRST:
            default:
                sql = String.format(
                    "SELECT %s FROM `%s` %s LIMIT %d",
                    columns, tableName, where, sampleSize
                );
                break;
        }
        
        return executeSampleQuery(request.getDataSource(), request, sql);
    }
}
