package com.linewell.dataelement.metautil.explorer.impl;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.*;
import lombok.extern.slf4j.Slf4j;

/**
 * PostgreSQL 元数据探查器
 * 
 * @author MetaUtil
 */
@Slf4j
public class PostgreSqlMetadataExplorer extends AbstractMetadataExplorer {

    private String getSchema(DataSourceConfig config) {
        return config.getSchema() != null ? config.getSchema() : "public";
    }

    @Override
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        DatabaseInfo info = new DatabaseInfo();
        info.setDatabaseName(config.getDatabase());
        info.setDatabaseType("PostgreSQL");
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            
            // 获取版本
            try (ResultSet rs = stmt.executeQuery("SELECT version()")) {
                if (rs.next()) {
                    info.setVersion(rs.getString(1));
                }
            }
            
            // 获取字符集
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT pg_encoding_to_char(encoding) FROM pg_database WHERE datname = current_database()")) {
                if (rs.next()) {
                    info.setCharset(rs.getString(1));
                }
            }
            
            // 获取排序规则
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT datcollate FROM pg_database WHERE datname = current_database()")) {
                if (rs.next()) {
                    info.setCollation(rs.getString(1));
                }
            }
            
            // 获取数据库大小
            String sizeSql = String.format(
                "SELECT pg_database_size('%s') as total_size", config.getDatabase()
            );
            try (ResultSet rs = stmt.executeQuery(sizeSql)) {
                if (rs.next()) {
                    info.setTotalSizeBytes(rs.getLong("total_size"));
                    info.setTotalSizeFormatted(formatBytes(info.getTotalSizeBytes()));
                    info.setDataSizeBytes(info.getTotalSizeBytes());
                    info.setDataSizeFormatted(info.getTotalSizeFormatted());
                }
            }
            
            // 获取表数量
            String countSql = String.format(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '%s' AND table_type = 'BASE TABLE'",
                getSchema(config)
            );
            try (ResultSet rs = stmt.executeQuery(countSql)) {
                if (rs.next()) {
                    info.setTableCount(rs.getInt(1));
                }
            }

            // 获取视图数量
            String viewCountSql = String.format(
                "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_schema = '%s' AND table_type = 'VIEW'",
                getSchema(config)
            );
            try (ResultSet rs = stmt.executeQuery(viewCountSql)) {
                if (rs.next()) {
                    info.setViewCount(rs.getInt(1));
                }
            } catch (Exception e) {
                log.warn("获取PostgreSQL视图数量失败: {}", e.getMessage());
            }

            // 获取存储过程数量（PG11+支持 PROCEDURE）
            String procedureCountSql = String.format(
                "SELECT COUNT(*) FROM information_schema.routines " +
                    "WHERE routine_schema = '%s' AND routine_type = 'PROCEDURE'",
                getSchema(config)
            );
            try (ResultSet rs = stmt.executeQuery(procedureCountSql)) {
                if (rs.next()) {
                    info.setProcedureCount(rs.getInt(1));
                }
            } catch (Exception e) {
                log.warn("获取PostgreSQL存储过程数量失败: {}", e.getMessage());
            }

            // 获取用户数量（角色数）
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM pg_roles")) {
                if (rs.next()) {
                    info.setUserCount(rs.getInt(1));
                }
            } catch (Exception e) {
                log.warn("获取PostgreSQL用户数量失败: {}", e.getMessage());
            }
            
            // 获取Schema列表
            info.setSchemas(getSchemas(config));
            
        } catch (Exception e) {
            log.error("获取PostgreSQL数据库信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库信息失败: " + e.getMessage(), e);
        }
        
        return info;
    }

    @Override
    public List<String> getDatabases(DataSourceConfig config) {
        List<String> databases = new ArrayList<>();
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname")) {
            
            while (rs.next()) {
                databases.add(rs.getString(1));
            }
            
        } catch (Exception e) {
            log.error("获取数据库列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库列表失败: " + e.getMessage(), e);
        }
        
        return databases;
    }

    @Override
    public List<String> getSchemas(DataSourceConfig config) {
        List<String> schemas = new ArrayList<>();
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT schema_name FROM information_schema.schemata " +
                 "WHERE schema_name NOT IN ('pg_catalog', 'information_schema', 'pg_toast') " +
                 "ORDER BY schema_name")) {
            
            while (rs.next()) {
                schemas.add(rs.getString(1));
            }
            
        } catch (Exception e) {
            log.error("获取Schema列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取Schema列表失败: " + e.getMessage(), e);
        }
        
        return schemas;
    }

    @Override
    public List<TableInfo> getTables(DataSourceConfig config) {
        List<TableInfo> tables = new ArrayList<>();
        String schema = getSchema(config);
        
        String sql = String.format(
            "SELECT t.table_name, " +
            "  obj_description((quote_ident(t.table_schema) || '.' || quote_ident(t.table_name))::regclass) as table_comment, " +
            "  t.table_type, " +
            "  pg_total_relation_size(quote_ident(t.table_schema) || '.' || quote_ident(t.table_name)) as total_size, " +
            "  pg_relation_size(quote_ident(t.table_schema) || '.' || quote_ident(t.table_name)) as data_size, " +
            "  pg_indexes_size(quote_ident(t.table_schema) || '.' || quote_ident(t.table_name)) as index_size " +
            "FROM information_schema.tables t " +
            "WHERE t.table_schema = '%s' AND t.table_type IN ('BASE TABLE', 'VIEW') " +
            "ORDER BY t.table_name", schema
        );
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                TableInfo table = new TableInfo();
                table.setTableName(rs.getString("table_name"));
                table.setTableComment(rs.getString("table_comment"));
                table.setTableType(rs.getString("table_type"));
                table.setSchemaName(schema);
                table.setTotalSizeBytes(rs.getLong("total_size"));
                table.setDataSizeBytes(rs.getLong("data_size"));
                table.setIndexSizeBytes(rs.getLong("index_size"));
                
                table.setTotalSizeFormatted(formatBytes(table.getTotalSizeBytes()));
                table.setDataSizeFormatted(formatBytes(table.getDataSizeBytes()));
                table.setIndexSizeFormatted(formatBytes(table.getIndexSizeBytes()));
                
                tables.add(table);
            }
            
            // 获取每个表的行数（使用统计信息，速度较快）
            for (TableInfo table : tables) {
                try {
                    String countSql = String.format(
                        "SELECT reltuples::bigint AS estimate FROM pg_class c " +
                        "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                        "WHERE n.nspname = '%s' AND c.relname = '%s'",
                        schema, table.getTableName()
                    );
                    try (ResultSet countRs = stmt.executeQuery(countSql)) {
                        if (countRs.next()) {
                            table.setRowCount(Math.max(0, countRs.getLong("estimate")));
                        }
                    }
                } catch (Exception e) {
                    log.warn("获取表 {} 行数估计失败: {}", table.getTableName(), e.getMessage());
                }
            }

            Map<String, Integer> columnCountMap = new HashMap<>();
            String colCountSql = "SELECT table_name, COUNT(*) AS column_count " +
                "FROM information_schema.columns WHERE table_schema = ? GROUP BY table_name";
            try (PreparedStatement ps = conn.prepareStatement(colCountSql)) {
                ps.setString(1, schema);
                try (ResultSet crs = ps.executeQuery()) {
                    while (crs.next()) {
                        columnCountMap.put(crs.getString("table_name"), crs.getInt("column_count"));
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
        String schema = getSchema(config);
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty()
            ? null
            : "%" + keyword.trim().toLowerCase() + "%";

        String fromSql =
            "FROM information_schema.tables t " +
            "LEFT JOIN ( " +
            "    SELECT table_schema, table_name, COUNT(*) AS column_count " +
            "    FROM information_schema.columns " +
            "    WHERE table_schema = ? " +
            "    GROUP BY table_schema, table_name " +
            ") c ON c.table_schema = t.table_schema AND c.table_name = t.table_name " +
            "LEFT JOIN pg_class cls ON cls.oid = (quote_ident(t.table_schema) || '.' || quote_ident(t.table_name))::regclass " +
            "WHERE t.table_schema = ? " +
            "  AND t.table_type IN ('BASE TABLE', 'VIEW') " +
            "  AND (? IS NULL OR LOWER(t.table_name) LIKE ? OR LOWER(COALESCE(obj_description(cls.oid), '')) LIKE ?) ";
        String countSql = "SELECT COUNT(1) " + fromSql;
        String pageSql =
            "SELECT t.table_name, " +
            "       obj_description(cls.oid) AS table_comment, " +
            "       t.table_type, " +
            "       pg_total_relation_size(cls.oid) AS total_size, " +
            "       pg_relation_size(cls.oid) AS data_size, " +
            "       pg_indexes_size(cls.oid) AS index_size, " +
            "       COALESCE(cls.reltuples::bigint, 0) AS estimate_rows, " +
            "       COALESCE(c.column_count, 0) AS column_count " +
            fromSql +
            "ORDER BY t.table_name LIMIT ? OFFSET ?";

        TablePageResult result = new TablePageResult();
        result.setPageNo(safePageNo);
        result.setPageSize(safePageSize);
        List<TableInfo> rows = new ArrayList<>();

        try (Connection conn = getConnection(config)) {
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setQueryTimeout(30);
                bindPageParams(ps, schema, normalizedKeyword);
                try (ResultSet rs = ps.executeQuery()) {
                    result.setTotal(rs.next() ? rs.getLong(1) : 0L);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(pageSql)) {
                ps.setQueryTimeout(30);
                int index = bindPageParams(ps, schema, normalizedKeyword);
                ps.setInt(index++, safePageSize);
                ps.setInt(index, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(readTableSummary(rs, schema));
                    }
                }
            }
            result.setRows(rows);
            return result;
        } catch (Exception e) {
            log.error("PostgreSQL鍒嗛〉鑾峰彇琛ㄥ垪琛ㄥけ璐? {}", e.getMessage(), e);
            throw new RuntimeException("PostgreSQL鍒嗛〉鑾峰彇琛ㄥ垪琛ㄥけ璐? " + e.getMessage(), e);
        }
    }

    private int bindPageParams(PreparedStatement ps, String schema, String normalizedKeyword) throws Exception {
        ps.setString(1, schema);
        ps.setString(2, schema);
        ps.setString(3, normalizedKeyword);
        ps.setString(4, normalizedKeyword == null ? "" : normalizedKeyword);
        ps.setString(5, normalizedKeyword == null ? "" : normalizedKeyword);
        return 6;
    }

    private TableInfo readTableSummary(ResultSet rs, String schema) throws Exception {
        TableInfo table = new TableInfo();
        table.setTableName(rs.getString("table_name"));
        table.setTableComment(rs.getString("table_comment"));
        table.setTableType(rs.getString("table_type"));
        table.setSchemaName(schema);
        table.setRowCount(Math.max(0, rs.getLong("estimate_rows")));
        table.setTotalSizeBytes(rs.getLong("total_size"));
        table.setDataSizeBytes(rs.getLong("data_size"));
        table.setIndexSizeBytes(rs.getLong("index_size"));
        table.setColumnCount(rs.getInt("column_count"));
        table.setTotalSizeFormatted(formatBytes(table.getTotalSizeBytes()));
        table.setDataSizeFormatted(formatBytes(table.getDataSizeBytes()));
        table.setIndexSizeFormatted(formatBytes(table.getIndexSizeBytes()));
        return table;
    }

    @Override
    public TableInfo getTableInfo(DataSourceConfig config, String tableName) {
        List<TableInfo> tables = getTables(config);
        TableInfo tableInfo = tables.stream()
                .filter(t -> t.getTableName().equalsIgnoreCase(tableName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("表不存在: " + tableName));
        
        tableInfo.setColumns(getColumns(config, tableName));
        tableInfo.setIndexes(getIndexes(config, tableName));
        
        return tableInfo;
    }

    @Override
    public List<ColumnInfo> getColumns(DataSourceConfig config, String tableName) {
        List<ColumnInfo> columns = new ArrayList<>();
        String schema = getSchema(config);
        
        String sql = String.format(
            "SELECT c.column_name, " +
            "  col_description((quote_ident(c.table_schema) || '.' || quote_ident(c.table_name))::regclass, c.ordinal_position) as column_comment, " +
            "  c.data_type, c.udt_name, " +
            "  c.character_maximum_length, c.numeric_precision, c.numeric_scale, " +
            "  c.is_nullable, c.column_default, c.ordinal_position " +
            "FROM information_schema.columns c " +
            "WHERE c.table_schema = '%s' AND c.table_name = '%s' " +
            "ORDER BY c.ordinal_position", schema, tableName
        );
        
        // 获取主键列
        List<String> pkColumns = getPrimaryKeyColumns(config, tableName);
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                ColumnInfo column = new ColumnInfo();
                column.setColumnName(rs.getString("column_name"));
                column.setColumnComment(rs.getString("column_comment"));
                column.setDataType(rs.getString("data_type"));
                column.setLength(rs.getLong("character_maximum_length"));
                column.setPrecision(rs.getInt("numeric_precision"));
                column.setScale(rs.getInt("numeric_scale"));
                column.setNullable("YES".equals(rs.getString("is_nullable")));
                column.setDefaultValue(rs.getString("column_default"));
                column.setOrdinalPosition(rs.getInt("ordinal_position"));
                column.setPrimaryKey(pkColumns.contains(column.getColumnName()));
                
                // 判断是否自增
                String defaultValue = column.getDefaultValue();
                column.setAutoIncrement(defaultValue != null && 
                    (defaultValue.contains("nextval") || defaultValue.contains("_seq")));
                
                // 构建完整类型
                String udtName = rs.getString("udt_name");
                if (column.getLength() != null && column.getLength() > 0) {
                    column.setColumnType(udtName + "(" + column.getLength() + ")");
                } else if (column.getPrecision() != null && column.getPrecision() > 0) {
                    if (column.getScale() != null && column.getScale() > 0) {
                        column.setColumnType(udtName + "(" + column.getPrecision() + "," + column.getScale() + ")");
                    } else {
                        column.setColumnType(udtName + "(" + column.getPrecision() + ")");
                    }
                } else {
                    column.setColumnType(udtName);
                }
                
                columns.add(column);
            }
            
        } catch (Exception e) {
            log.error("获取字段列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取字段列表失败: " + e.getMessage(), e);
        }
        
        return columns;
    }

    private List<String> getPrimaryKeyColumns(DataSourceConfig config, String tableName) {
        List<String> pkColumns = new ArrayList<>();
        String schema = getSchema(config);
        
        String sql = String.format(
            "SELECT a.attname FROM pg_index i " +
            "JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) " +
            "WHERE i.indrelid = '%s.%s'::regclass AND i.indisprimary",
            schema, tableName
        );
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                pkColumns.add(rs.getString("attname"));
            }
            
        } catch (Exception e) {
            log.warn("获取主键列失败: {}", e.getMessage());
        }
        
        return pkColumns;
    }

    @Override
    public List<IndexInfo> getIndexes(DataSourceConfig config, String tableName) {
        List<IndexInfo> indexes = new ArrayList<>();
        String schema = getSchema(config);
        
        String sql = String.format(
            "SELECT i.relname as index_name, " +
            "  ix.indisunique as is_unique, " +
            "  ix.indisprimary as is_primary, " +
            "  am.amname as index_type, " +
            "  array_agg(a.attname ORDER BY c.ordinality) as columns " +
            "FROM pg_index ix " +
            "JOIN pg_class t ON t.oid = ix.indrelid " +
            "JOIN pg_class i ON i.oid = ix.indexrelid " +
            "JOIN pg_namespace n ON n.oid = t.relnamespace " +
            "JOIN pg_am am ON am.oid = i.relam " +
            "CROSS JOIN LATERAL unnest(ix.indkey) WITH ORDINALITY AS c(attnum, ordinality) " +
            "JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = c.attnum " +
            "WHERE n.nspname = '%s' AND t.relname = '%s' " +
            "GROUP BY i.relname, ix.indisunique, ix.indisprimary, am.amname",
            schema, tableName
        );
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                IndexInfo index = new IndexInfo();
                index.setIndexName(rs.getString("index_name"));
                index.setUnique(rs.getBoolean("is_unique"));
                index.setPrimaryKey(rs.getBoolean("is_primary"));
                index.setIndexType(rs.getString("index_type"));
                
                // 解析PostgreSQL数组格式
                Array columnsArray = rs.getArray("columns");
                if (columnsArray != null) {
                    String[] cols = (String[]) columnsArray.getArray();
                    List<String> columnList = new ArrayList<>();
                    for (String col : cols) {
                        columnList.add(col);
                    }
                    index.setColumns(columnList);
                }
                
                indexes.add(index);
            }
            
        } catch (Exception e) {
            log.error("获取索引列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取索引列表失败: " + e.getMessage(), e);
        }
        
        return indexes;
    }

    @Override
    public Long getTableRowCount(DataSourceConfig config, String tableName) {
        String schema = getSchema(config);
        String sql = String.format("SELECT COUNT(*) FROM \"%s\".\"%s\"", schema, tableName);
        
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
        String schema = getSchema(config);
        String sql = String.format(
            "SELECT pg_total_relation_size('%s.%s') as total_size",
            schema, tableName
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
            "SELECT pg_database_size('%s') as total_size", config.getDatabase()
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
        String schema = getSchema(request.getDataSource());
        String tableName = request.getTableName();
        int sampleSize = request.getSampleSize();
        
        String sql;
        switch (request.getSampleMethod()) {
            case RANDOM:
                sql = String.format(
                    "SELECT %s FROM \"%s\".\"%s\" %s ORDER BY RANDOM() LIMIT %d",
                    columns, schema, tableName, where, sampleSize
                );
                break;
            case LAST:
                sql = String.format(
                    "SELECT * FROM (SELECT %s FROM \"%s\".\"%s\" %s ORDER BY 1 DESC LIMIT %d) t ORDER BY 1 ASC",
                    columns, schema, tableName, where, sampleSize
                );
                break;
            case FIRST:
            default:
                sql = String.format(
                    "SELECT %s FROM \"%s\".\"%s\" %s LIMIT %d",
                    columns, schema, tableName, where, sampleSize
                );
                break;
        }
        
        return executeSampleQuery(request.getDataSource(), request, sql);
    }
}
