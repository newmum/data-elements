package com.linewell.dataelement.metautil.explorer.impl;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.*;
import lombok.extern.slf4j.Slf4j;

/**
 * 达梦（DM8）元数据探查器
 * <p>
 * 说明：
 * - 主要通过 JDBC DatabaseMetaData 获取库表字段索引等信息，避免强依赖 DM 系统表结构。
 * - 空间统计能力依赖系统视图/权限，当前实现返回 0（不阻断核心探查功能）。
 */
@Slf4j
public class DamengMetadataExplorer extends AbstractMetadataExplorer {

    private static final int QUERY_TIMEOUT_SECONDS = 15;
    private static final String OWNER_SEGMENT_STATS_SQL =
            "SELECT "
                    + "COALESCE(SUM(BYTES), 0) AS TOTAL_SIZE, "
                    + "COALESCE(SUM(CASE WHEN UPPER(SEGMENT_TYPE) LIKE '%INDEX%' THEN BYTES ELSE 0 END), 0) AS INDEX_SIZE "
                    + "FROM DBA_SEGMENTS WHERE OWNER = ?";

    private static String schemaCandidate(DataSourceConfig config) {
        if (config.getSchema() != null && !config.getSchema().isBlank()) {
            return config.getSchema().trim();
        }
        if (config.getDatabase() != null && !config.getDatabase().isBlank()) {
            return config.getDatabase().trim();
        }
        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            return config.getUsername().trim();
        }
        return null;
    }

    /**
     * 达梦既支持普通大写标识符，也支持迁移工具创建的带引号小写标识符。
     * 必须从系统视图取回真实 owner 大小写，不能直接把配置值强制转成大写。
     */
    private static String resolveSchema(DataSourceConfig config, Connection conn) {
        String candidate = schemaCandidate(config);
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String sql = "SELECT USERNAME FROM ALL_USERS WHERE LOWER(USERNAME) = LOWER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, candidate);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            log.debug("解析达梦Schema真实大小写失败，使用配置值: candidate={}, err={}", candidate, e.getMessage());
        }
        return candidate;
    }

    private static String resolveTableName(Connection conn, String schema, String tableName) {
        if (tableName == null || tableName.isBlank() || schema == null || schema.isBlank()) {
            return tableName;
        }
        String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS "
                + "WHERE OWNER = ? AND LOWER(OBJECT_NAME) = LOWER(?) "
                + "AND OBJECT_TYPE IN ('TABLE', 'VIEW') FETCH FIRST 1 ROWS ONLY";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setString(1, schema);
            ps.setString(2, tableName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            log.debug("解析达梦表名真实大小写失败，使用传入值: table={}, err={}", tableName, e.getMessage());
        }
        return tableName.trim();
    }

    private static String q(String identifier) {
        return "\"" + String.valueOf(identifier).replace("\"", "\"\"") + "\"";
    }

    private static String qualifyTable(String schema, String tableName) {
        String normalizedTable = tableName == null ? null : tableName.trim();
        if (normalizedTable == null || normalizedTable.isBlank()) {
            return q("");
        }
        if (schema == null || schema.isBlank()) {
            return q(normalizedTable);
        }
        return q(schema.trim()) + "." + q(normalizedTable);
    }

    @Override
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        DatabaseInfo info = new DatabaseInfo();
        info.setDatabaseName(config.getDatabase() == null ? "" : config.getDatabase());
        info.setDatabaseType("Dameng");
        info.setVersion("");
        info.setCharset("");
        info.setCollation("");
        info.setTotalSizeBytes(0L);
        info.setDataSizeBytes(0L);
        info.setIndexSizeBytes(0L);
        info.setTotalSizeFormatted(formatBytes(0L));
        info.setDataSizeFormatted(formatBytes(0L));
        info.setIndexSizeFormatted(formatBytes(0L));
        info.setTableCount(0);
        info.setViewCount(0);
        info.setProcedureCount(0);
        info.setUserCount(0);

        try (Connection conn = getConnection(config)) {
            DatabaseMetaData meta = conn.getMetaData();
            String version = meta.getDatabaseProductVersion();
            info.setVersion(version == null ? "" : version);
            info.setSchemas(getSchemas(config));

            String schema = resolveSchema(config, conn);
            int tableCount = 0;
            int viewCount = 0;
            try (ResultSet rs = meta.getTables(null, schema, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    String type = rs.getString("TABLE_TYPE");
                    if (type != null && type.toUpperCase().contains("VIEW")) {
                        viewCount++;
                    } else {
                        tableCount++;
                    }
                }
            }
            info.setTableCount(tableCount);
            info.setViewCount(viewCount);

            // 优先从系统视图统计，失败时保留元数据统计结果
            if (schema != null && !schema.isBlank()) {
                Integer tableCountBySql = queryInt(conn, "SELECT COUNT(*) FROM ALL_TABLES WHERE OWNER = ?", schema);
                if (tableCountBySql != null) {
                    info.setTableCount(tableCountBySql);
                }
                Integer viewCountBySql = queryInt(conn, "SELECT COUNT(*) FROM ALL_VIEWS WHERE OWNER = ?", schema);
                if (viewCountBySql != null) {
                    info.setViewCount(viewCountBySql);
                }
                Integer procedureCount = queryInt(conn, "SELECT COUNT(*) FROM ALL_OBJECTS WHERE OWNER = ? AND OBJECT_TYPE = 'PROCEDURE'", schema);
                if (procedureCount != null) {
                    info.setProcedureCount(procedureCount);
                }
            }

            Integer userCount = queryInt(conn, "SELECT COUNT(*) FROM ALL_USERS");
            if (userCount != null) {
                info.setUserCount(userCount);
            }

            SegmentStats segmentStats = queryOwnerSegmentStats(conn, schema);
            if (segmentStats != null) {
                long totalSize = Math.max(segmentStats.totalSize(), 0L);
                long indexSize = Math.max(segmentStats.indexSize(), 0L);
                long dataSize = Math.max(totalSize - indexSize, 0L);

                info.setTotalSizeBytes(totalSize);
                info.setIndexSizeBytes(indexSize);
                info.setDataSizeBytes(dataSize);
                info.setTotalSizeFormatted(formatBytes(totalSize));
                info.setIndexSizeFormatted(formatBytes(indexSize));
                info.setDataSizeFormatted(formatBytes(dataSize));
            }
        } catch (Exception e) {
            log.error("获取达梦数据库信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库信息失败: " + e.getMessage(), e);
        }

        return info;
    }

    private Integer queryInt(Connection conn, String sql, Object... params) {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int value = rs.getInt(1);
                    return rs.wasNull() ? null : value;
                }
            }
        } catch (Exception e) {
            log.warn("执行统计SQL失败: {}, err={}", sql, e.getMessage());
        }
        return null;
    }

    private SegmentStats queryOwnerSegmentStats(Connection conn, String schema) {
        if (schema == null || schema.isBlank()) {
            return new SegmentStats(0L, 0L);
        }
        try (PreparedStatement ps = conn.prepareStatement(OWNER_SEGMENT_STATS_SQL)) {
            ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            ps.setObject(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long totalSize = rs.getLong("TOTAL_SIZE");
                    long indexSize = rs.getLong("INDEX_SIZE");
                    return new SegmentStats(totalSize, indexSize);
                }
            }
        } catch (Exception e) {
            log.warn("执行达梦段统计SQL失败: {}, schema={}, err={}", OWNER_SEGMENT_STATS_SQL, schema, e.getMessage());
        }
        return new SegmentStats(0L, 0L);
    }

    private record SegmentStats(long totalSize, long indexSize) {
    }

    @Override
    public List<String> getDatabases(DataSourceConfig config) {
        // 达梦没有 MySQL 那种 database 概念，这里返回 schema 列表更符合使用习惯
        return getSchemas(config);
    }

    @Override
    public List<String> getSchemas(DataSourceConfig config) {
        List<String> schemas = new ArrayList<>();
        try (Connection conn = getConnection(config)) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getSchemas()) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_SCHEM");
                    if (name != null && !name.isBlank()) {
                        schemas.add(name);
                    }
                }
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

        try (Connection conn = getConnection(config)) {
            String schema = resolveSchema(config, conn);
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, schema, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    TableInfo table = new TableInfo();
                    table.setTableName(rs.getString("TABLE_NAME"));
                    table.setSchemaName(rs.getString("TABLE_SCHEM"));
                    table.setTableType(rs.getString("TABLE_TYPE"));
                    try {
                        table.setTableComment(rs.getString("REMARKS"));
                    } catch (SQLException ignored) {
                    }
                    tables.add(table);
                }
            }

            Map<String, java.util.Date> updateTimeMap = new HashMap<>();
            Map<String, java.util.Date> dataUpdateTimeMap = new HashMap<>();

            try (Statement stmt = conn.createStatement()) {
                String ddlTimeSql = "SELECT OBJECT_NAME, LAST_DDL_TIME FROM ALL_OBJECTS WHERE OWNER = '" + schema + "' AND OBJECT_TYPE = 'TABLE'";
                try (ResultSet rs = stmt.executeQuery(ddlTimeSql)) {
                    while (rs.next()) {
                        updateTimeMap.put(rs.getString(1), rs.getTimestamp(2));
                    }
                } catch (SQLException e) {
                    log.warn("Failed to get LAST_DDL_TIME for schema {}: {}", schema, e.getMessage());
                }

                String modTimeSql = "SELECT TABLE_NAME, TIMESTAMP FROM ALL_TAB_MODIFICATIONS WHERE TABLE_OWNER = '" + schema + "'";
                try (ResultSet rs = stmt.executeQuery(modTimeSql)) {
                    while (rs.next()) {
                        dataUpdateTimeMap.put(rs.getString(1), rs.getTimestamp(2));
                    }
                } catch (SQLException e) {
                    log.warn("Failed to get data modification time for schema {}: {}", schema, e.getMessage());
                }
            } catch (Exception e) {
                log.warn("Failed to get timestamps for schema {}: {}", schema, e.getMessage());
            }

            for (TableInfo table : tables) {
                table.setUpdateTime(updateTimeMap.get(table.getTableName()));
                table.setDataUpdateTime(dataUpdateTimeMap.get(table.getTableName()));
                if (table.getDataUpdateTime() == null) {
                    table.setDataUpdateTime(table.getUpdateTime());
                }
            }

            Map<String, Integer> columnCountMap = new HashMap<>();
            String colCountSql = "SELECT TABLE_NAME, COUNT(*) AS column_count " +
                    "FROM ALL_TAB_COLUMNS WHERE OWNER = ? GROUP BY TABLE_NAME";
            try (PreparedStatement ps = conn.prepareStatement(colCountSql)) {
                ps.setString(1, schema);
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
        int startRow = (safePageNo - 1) * safePageSize + 1;
        int endRow = safePageNo * safePageSize;
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty()
                ? null
                : "%" + keyword.trim().toLowerCase() + "%";

        TablePageResult page = new TablePageResult();
        page.setPageNo(safePageNo);
        page.setPageSize(safePageSize);
        List<TableInfo> rows = new ArrayList<>();

        try (Connection conn = getConnection(config)) {
            String schema = resolveSchema(config, conn);
            String baseSql = "SELECT x.TABLE_NAME, x.TABLE_COMMENT, x.TABLE_TYPE, x.NUM_ROWS, x.LAST_ANALYZED, "
                    + "COALESCE(col.COLUMN_COUNT, 0) AS COLUMN_COUNT "
                    + "FROM ("
                    + "  SELECT t.TABLE_NAME, c.COMMENTS AS TABLE_COMMENT, 'BASE TABLE' AS TABLE_TYPE, "
                    + "         COALESCE(t.NUM_ROWS, 0) AS NUM_ROWS, t.LAST_ANALYZED "
                    + "  FROM ALL_TABLES t "
                    + "  LEFT JOIN ALL_TAB_COMMENTS c ON c.OWNER = t.OWNER AND c.TABLE_NAME = t.TABLE_NAME AND c.TABLE_TYPE = 'TABLE' "
                    + "  WHERE t.OWNER = ? "
                    + "  UNION ALL "
                    + "  SELECT v.VIEW_NAME AS TABLE_NAME, c.COMMENTS AS TABLE_COMMENT, 'VIEW' AS TABLE_TYPE, "
                    + "         0 AS NUM_ROWS, CAST(NULL AS DATETIME) AS LAST_ANALYZED "
                    + "  FROM ALL_VIEWS v "
                    + "  LEFT JOIN ALL_TAB_COMMENTS c ON c.OWNER = v.OWNER AND c.TABLE_NAME = v.VIEW_NAME AND c.TABLE_TYPE = 'VIEW' "
                    + "  WHERE v.OWNER = ? "
                    + ") x "
                    + "LEFT JOIN ("
                    + "  SELECT OWNER, TABLE_NAME, COUNT(*) AS COLUMN_COUNT "
                    + "  FROM ALL_TAB_COLUMNS WHERE OWNER = ? GROUP BY OWNER, TABLE_NAME"
                    + ") col ON col.OWNER = ? AND col.TABLE_NAME = x.TABLE_NAME";
            String filterSql = " WHERE (? IS NULL OR LOWER(q.TABLE_NAME) LIKE ? OR LOWER(COALESCE(q.TABLE_COMMENT, '')) LIKE ?) ";
            String countSql = "SELECT COUNT(1) FROM (" + baseSql + ") q " + filterSql;
            String pageSql = "SELECT * FROM ("
                    + "SELECT q.*, ROW_NUMBER() OVER (ORDER BY q.TABLE_NAME) AS RN FROM (" + baseSql + ") q " + filterSql
                    + ") WHERE RN BETWEEN ? AND ? ORDER BY RN";

            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                int idx = bindDamengBaseParams(ps, 1, schema);
                bindDamengKeywordParams(ps, idx, normalizedKeyword);
                try (ResultSet rs = ps.executeQuery()) {
                    page.setTotal(rs.next() ? rs.getLong(1) : 0L);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(pageSql)) {
                ps.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                int idx = bindDamengBaseParams(ps, 1, schema);
                idx = bindDamengKeywordParams(ps, idx, normalizedKeyword);
                ps.setInt(idx++, startRow);
                ps.setInt(idx, endRow);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(readDamengTableSummary(rs, schema));
                    }
                }
            }
            page.setRows(rows);
            return page;
        } catch (Exception e) {
            log.error("达梦分页获取表列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("达梦分页获取表列表失败: " + e.getMessage(), e);
        }
    }

    private int bindDamengBaseParams(PreparedStatement ps, int index, String schema) throws SQLException {
        ps.setString(index++, schema);
        ps.setString(index++, schema);
        ps.setString(index++, schema);
        ps.setString(index++, schema);
        return index;
    }

    private int bindDamengKeywordParams(PreparedStatement ps, int index, String normalizedKeyword) throws SQLException {
        ps.setString(index++, normalizedKeyword);
        ps.setString(index++, normalizedKeyword == null ? "" : normalizedKeyword);
        ps.setString(index++, normalizedKeyword == null ? "" : normalizedKeyword);
        return index;
    }

    private TableInfo readDamengTableSummary(ResultSet rs, String schema) throws SQLException {
        TableInfo table = new TableInfo();
        table.setTableName(rs.getString("TABLE_NAME"));
        table.setTableComment(rs.getString("TABLE_COMMENT"));
        table.setTableType(rs.getString("TABLE_TYPE"));
        table.setSchemaName(schema);
        table.setRowCount(rs.getLong("NUM_ROWS"));
        table.setUpdateTime(rs.getTimestamp("LAST_ANALYZED"));
        table.setDataUpdateTime(table.getUpdateTime());
        table.setColumnCount(rs.getInt("COLUMN_COUNT"));
        table.setTotalSizeBytes(0L);
        table.setDataSizeBytes(0L);
        table.setIndexSizeBytes(0L);
        table.setTotalSizeFormatted(formatBytes(0L));
        table.setDataSizeFormatted(formatBytes(0L));
        table.setIndexSizeFormatted(formatBytes(0L));
        return table;
    }

    @Override
    public TableInfo getTableInfo(DataSourceConfig config, String tableName) {
        TableInfo info = getTables(config).stream()
                .filter(t -> t.getTableName() != null && t.getTableName().equalsIgnoreCase(tableName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("表不存在: " + tableName));

        info.setColumns(getColumns(config, tableName));
        info.setIndexes(getIndexes(config, tableName));
        try {
            info.setRowCount(getTableRowCount(config, tableName));
        } catch (Exception e) {
            log.warn("获取表 {} 记录数失败: {}", tableName, e.getMessage());
        }
        return info;
    }

    @Override
    public List<ColumnInfo> getColumns(DataSourceConfig config, String tableName) {
        List<ColumnInfo> columns = new ArrayList<>();

        // 主键信息
        Set<String> pkSet = new HashSet<>();
        try (Connection conn = getConnection(config)) {
            String schema = resolveSchema(config, conn);
            String actualTableName = resolveTableName(conn, schema, tableName);
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet pk = meta.getPrimaryKeys(null, schema, actualTableName)) {
                while (pk.next()) {
                    String col = pk.getString("COLUMN_NAME");
                    if (col != null) {
                        pkSet.add(col);
                    }
                }
            }

            try (ResultSet rs = meta.getColumns(null, schema, actualTableName, "%")) {
                while (rs.next()) {
                    ColumnInfo column = new ColumnInfo();
                    String name = rs.getString("COLUMN_NAME");
                    column.setColumnName(name);
                    column.setDataType(rs.getString("TYPE_NAME"));

                    long size = rs.getLong("COLUMN_SIZE");
                    if (!rs.wasNull()) {
                        column.setLength(size);
                    }
                    int precision = rs.getInt("COLUMN_SIZE");
                    if (!rs.wasNull()) {
                        column.setPrecision(precision);
                    }
                    int scale = rs.getInt("DECIMAL_DIGITS");
                    if (!rs.wasNull()) {
                        column.setScale(scale);
                    }

                    column.setNullable(DatabaseMetaData.columnNullable == rs.getInt("NULLABLE"));
                    column.setDefaultValue(rs.getString("COLUMN_DEF"));
                    column.setColumnComment(rs.getString("REMARKS"));

                    int pos = rs.getInt("ORDINAL_POSITION");
                    if (!rs.wasNull()) {
                        column.setOrdinalPosition(pos);
                    }

                    column.setPrimaryKey(pkSet.contains(name));

                    // 构建完整类型（尽量通用）
                    String typeName = column.getDataType() != null ? column.getDataType() : "";
                    String typeUpper = typeName.toUpperCase();
                    if (typeUpper.startsWith("TIMESTAMP") && column.getScale() != null && column.getScale() > 0) {
                        // 达梦 TIMESTAMP(p) 使用小数秒精度，来源应为 scale，避免错误拼成 TIMESTAMP(precision,scale)
                        column.setColumnType(typeName + "(" + column.getScale() + ")");
                    } else if (column.getScale() != null && column.getScale() > 0 && column.getPrecision() != null && column.getPrecision() > 0) {
                        column.setColumnType(typeName + "(" + column.getPrecision() + "," + column.getScale() + ")");
                    } else if (column.getLength() != null && column.getLength() > 0) {
                        column.setColumnType(typeName + "(" + column.getLength() + ")");
                    } else {
                        column.setColumnType(typeName);
                    }

                    columns.add(column);
                }
            }
        } catch (Exception e) {
            log.error("获取字段列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取字段列表失败: " + e.getMessage(), e);
        }

        // 索引标识（可选）
        try {
            List<IndexInfo> idx = getIndexes(config, tableName);
            Set<String> indexedCols = new HashSet<>();
            Set<String> uniqueCols = new HashSet<>();
            for (IndexInfo i : idx) {
                if (i.getColumns() != null) {
                    indexedCols.addAll(i.getColumns());
                    if (Boolean.TRUE.equals(i.getUnique())) {
                        uniqueCols.addAll(i.getColumns());
                    }
                }
            }
            for (ColumnInfo c : columns) {
                if (c.getColumnName() != null) {
                    c.setIndexed(indexedCols.contains(c.getColumnName()));
                    c.setUnique(uniqueCols.contains(c.getColumnName()) || Boolean.TRUE.equals(c.getPrimaryKey()));
                }
            }
        } catch (Exception e) {
            log.debug("补充索引标识失败（忽略）: {}", e.getMessage());
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
        Set<String> requested = new HashSet<>();
        for (String tableName : tableNames) {
            if (tableName != null && !tableName.isBlank()) {
                String key = tableName.toLowerCase(Locale.ROOT);
                requested.add(key);
                result.put(key, new ArrayList<>());
            }
        }
        if (requested.isEmpty()) {
            return result;
        }

        try (Connection conn = getConnection(config)) {
            String schema = resolveSchema(config, conn);
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, schema, "%", "%")) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    String key = tableName == null ? "" : tableName.toLowerCase(Locale.ROOT);
                    if (!requested.contains(key)) {
                        continue;
                    }
                    result.get(key).add(readDiffColumn(rs));
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Batch column lookup failed: {}", e.getMessage(), e);
            throw new RuntimeException("Batch column lookup failed: " + e.getMessage(), e);
        }
    }

    private ColumnInfo readDiffColumn(ResultSet rs) throws SQLException {
        ColumnInfo column = new ColumnInfo();
        column.setColumnName(rs.getString("COLUMN_NAME"));
        column.setDataType(rs.getString("TYPE_NAME"));
        long size = rs.getLong("COLUMN_SIZE");
        if (!rs.wasNull()) {
            column.setLength(size);
            column.setPrecision((int) Math.min(size, Integer.MAX_VALUE));
        }
        int scale = rs.getInt("DECIMAL_DIGITS");
        if (!rs.wasNull()) {
            column.setScale(scale);
        }
        column.setNullable(DatabaseMetaData.columnNullable == rs.getInt("NULLABLE"));
        column.setDefaultValue(rs.getString("COLUMN_DEF"));
        column.setColumnComment(rs.getString("REMARKS"));
        int position = rs.getInt("ORDINAL_POSITION");
        if (!rs.wasNull()) {
            column.setOrdinalPosition(position);
        }
        String typeName = column.getDataType() == null ? "" : column.getDataType();
        if (typeName.toUpperCase(Locale.ROOT).startsWith("TIMESTAMP") && scale > 0) {
            column.setColumnType(typeName + "(" + scale + ")");
        } else if (scale > 0 && size > 0) {
            column.setColumnType(typeName + "(" + size + "," + scale + ")");
        } else if (size > 0) {
            column.setColumnType(typeName + "(" + size + ")");
        } else {
            column.setColumnType(typeName);
        }
        return column;
    }

    @Override
    public List<IndexInfo> getIndexes(DataSourceConfig config, String tableName) {
        List<IndexInfo> indexes = new ArrayList<>();

        try (Connection conn = getConnection(config)) {
            String schema = resolveSchema(config, conn);
            String actualTableName = resolveTableName(conn, schema, tableName);
            DatabaseMetaData meta = conn.getMetaData();

            // 主键索引名（可选）
            String pkIndexName = null;
            try (ResultSet pk = meta.getPrimaryKeys(null, schema, actualTableName)) {
                while (pk.next()) {
                    pkIndexName = pk.getString("PK_NAME");
                    if (pkIndexName != null && !pkIndexName.isBlank()) {
                        break;
                    }
                }
            } catch (Exception ignored) {
            }

            Map<String, IndexInfo> indexMap = new LinkedHashMap<>();
            try (ResultSet rs = meta.getIndexInfo(null, schema, actualTableName, false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
                    if (indexName == null || indexName.isBlank() || columnName == null || columnName.isBlank()) {
                        continue;
                    }

                    IndexInfo index = indexMap.get(indexName);
                    if (index == null) {
                        index = new IndexInfo();
                        index.setIndexName(indexName);
                        index.setColumns(new ArrayList<>());
                        boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                        index.setUnique(!nonUnique);
                        index.setPrimaryKey(pkIndexName != null && pkIndexName.equalsIgnoreCase(indexName));
                        try {
                            index.setIndexType(rs.getString("TYPE"));
                        } catch (Exception ignored) {
                        }
                        indexMap.put(indexName, index);
                    }
                    index.getColumns().add(columnName);
                }
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
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            String schema = resolveSchema(config, conn);
            String actualTableName = resolveTableName(conn, schema, tableName);
            String sql = "SELECT COUNT(*) FROM " + qualifyTable(schema, actualTableName);
            try (ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception e) {
            log.error("获取表记录数失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表记录数失败: " + e.getMessage(), e);
        }
        return 0L;
    }

    @Override
    public Long getTableSize(DataSourceConfig config, String tableName) {
        // 依赖系统视图与权限，暂不实现（返回 0，不影响核心探查功能）
        return 0L;
    }

    @Override
    public Long getDatabaseSize(DataSourceConfig config) {
        // 依赖系统视图与权限，暂不实现（返回 0，不影响核心探查功能）
        return 0L;
    }

    @Override
    public SampleDataResult sampleData(SampleRequest request) {
        DataSourceConfig config = request.getDataSource();
        String columns = buildColumnSelection(request.getColumns());
        String where = buildWhereClause(request.getWhereClause());
        int sampleSize = request.getSampleSize();

        try (Connection conn = getConnection(config)) {
            String schema = resolveSchema(config, conn);
            String tableName = resolveTableName(conn, schema, request.getTableName());
            String qualifiedTable = qualifyTable(schema, tableName);

            // 采用 Oracle 风格 ROWNUM；随机抽样尝试使用 DBMS_RANDOM（若环境未启用会报错）
            String inner;
            switch (request.getSampleMethod()) {
                case RANDOM:
                    inner = String.format("SELECT %s FROM %s %s ORDER BY DBMS_RANDOM.VALUE", columns, qualifiedTable, where);
                    break;
                case LAST:
                    inner = String.format("SELECT %s FROM %s %s ORDER BY ROWID DESC", columns, qualifiedTable, where);
                    break;
                case FIRST:
                default:
                    inner = String.format("SELECT %s FROM %s %s", columns, qualifiedTable, where);
                    break;
            }

            String sql = String.format("SELECT %s FROM (%s) WHERE ROWNUM <= %d", columns, inner, sampleSize);
            return executeSampleQuery(config, request, sql);
        } catch (Exception e) {
            log.error("达梦样例数据查询失败: {}", e.getMessage(), e);
            throw new RuntimeException("达梦样例数据查询失败: " + e.getMessage(), e);
        }
    }
}

