package com.linewell.dataelement.metautil.explorer.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.*;
import com.linewell.dataelement.metautil.model.enums.DatabaseType;
import lombok.extern.slf4j.Slf4j;

/**
 * OceanBase 元数据探查器
 * 支持 MySQL 兼容模式与 Oracle 兼容模式，根据数据源类型自动选择元数据查询方式。
 *
 * @author MetaUtil
 */
@Slf4j
public class OceanBaseMetadataExplorer extends AbstractMetadataExplorer {

    private static final String COLLECT_ALL_VISIBLE_OWNERS = "metadataCollectAllVisibleOwners";

    private static boolean isOracleMode(DataSourceConfig config) {
        return config.getDatabaseType() == DatabaseType.OCEANBASE_ORACLE;
    }

    @Override
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        if (isOracleMode(config)) {
            return getDatabaseInfoOracle(config);
        }
        return getDatabaseInfoMysql(config);
    }

    private DatabaseInfo getDatabaseInfoMysql(DataSourceConfig config) {
        DatabaseInfo info = new DatabaseInfo();
        info.setDatabaseName(config.getDatabase());
        info.setDatabaseType("OceanBase(MySQL)");

        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT VERSION()")) {
                if (rs.next()) {
                    info.setVersion(rs.getString(1));
                }
            }

            if (config.getDatabase() != null && !config.getDatabase().isEmpty()) {
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

                String sizeSql = String.format(
                    "SELECT SUM(data_length) as data_size, SUM(index_length) as index_size, " +
                    "SUM(data_length + index_length) as total_size, COUNT(*) as table_count " +
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

                String viewCountSql = String.format(
                    "SELECT COUNT(*) AS view_count FROM information_schema.TABLES " +
                        "WHERE table_schema = '%s' AND TABLE_TYPE = 'VIEW'",
                    config.getDatabase()
                );
                try (ResultSet rs = stmt.executeQuery(viewCountSql)) {
                    if (rs.next()) {
                        info.setViewCount(rs.getInt("view_count"));
                    }
                } catch (Exception e) {
                    log.warn("获取OceanBase(MySQL)视图数量失败: {}", e.getMessage());
                }

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
                    log.warn("获取OceanBase(MySQL)存储过程数量失败: {}", e.getMessage());
                }

                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS user_count FROM mysql.user")) {
                    if (rs.next()) {
                        info.setUserCount(rs.getInt("user_count"));
                    }
                } catch (Exception e) {
                    log.warn("获取OceanBase(MySQL)用户数量失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("获取OceanBase(MySQL)数据库信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库信息失败: " + e.getMessage(), e);
        }
        return info;
    }

    private DatabaseInfo getDatabaseInfoOracle(DataSourceConfig config) {
        DatabaseInfo info = new DatabaseInfo();
        info.setDatabaseName(config.getDatabase());
        info.setDatabaseType("OceanBase(Oracle)");

        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT BANNER FROM V$VERSION WHERE ROWNUM = 1")) {
                if (rs.next()) {
                    info.setVersion(rs.getString(1));
                }
            }

            try (ResultSet rs = stmt.executeQuery(
                "SELECT VALUE FROM NLS_DATABASE_PARAMETERS WHERE PARAMETER = 'NLS_CHARACTERSET'")) {
                if (rs.next()) {
                    info.setCharset(rs.getString(1));
                }
            }

            String schema = config.getSchema() != null ? config.getSchema() : config.getUsername().toUpperCase();
            try (ResultSet rs = stmt.executeQuery(
                "SELECT SUM(bytes) as total_size FROM user_segments")) {
                if (rs.next()) {
                    info.setTotalSizeBytes(rs.getLong("total_size"));
                    info.setTotalSizeFormatted(formatBytes(info.getTotalSizeBytes()));
                    info.setDataSizeBytes(info.getTotalSizeBytes());
                    info.setDataSizeFormatted(info.getTotalSizeFormatted());
                }
            }

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM user_tables")) {
                if (rs.next()) {
                    info.setTableCount(rs.getInt(1));
                }
            }

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM user_views")) {
                if (rs.next()) {
                    info.setViewCount(rs.getInt(1));
                }
            } catch (Exception e) {
                log.warn("获取OceanBase(Oracle)视图数量失败: {}", e.getMessage());
            }

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM user_procedures WHERE object_type = 'PROCEDURE'")) {
                if (rs.next()) {
                    info.setProcedureCount(rs.getInt(1));
                }
            } catch (Exception e) {
                log.warn("获取OceanBase(Oracle)存储过程数量失败: {}", e.getMessage());
            }

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM all_users")) {
                if (rs.next()) {
                    info.setUserCount(rs.getInt(1));
                }
            } catch (Exception e) {
                log.warn("获取OceanBase(Oracle)用户数量失败: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("获取OceanBase(Oracle)数据库信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库信息失败: " + e.getMessage(), e);
        }
        return info;
    }

    @Override
    public List<String> getDatabases(DataSourceConfig config) {
        if (isOracleMode(config)) {
            List<String> list = new ArrayList<>();
            try (Connection conn = getConnection(config);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT TABLESPACE_NAME FROM user_tablespaces")) {
                while (rs.next()) {
                    list.add(rs.getString(1));
                }
            } catch (Exception e) {
                log.error("获取表空间列表失败: {}", e.getMessage(), e);
                throw new RuntimeException("获取表空间列表失败: " + e.getMessage(), e);
            }
            return list;
        }
        List<String> databases = new ArrayList<>();
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {
            while (rs.next()) {
                String dbName = rs.getString(1);
                if (!"information_schema".equals(dbName) && !"mysql".equals(dbName)
                    && !"performance_schema".equals(dbName) && !"sys".equals(dbName)
                    && !"oceanbase".equalsIgnoreCase(dbName)) {
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
        if (isOracleMode(config)) {
            return new OracleMetadataExplorer().getSchemas(config);
        }
        return getDatabases(config);
    }

    @Override
    public List<String> getSchemasWithTables(DataSourceConfig config) {
        if (isOracleMode(config)) {
            return new OracleMetadataExplorer().getSchemasWithTables(config);
        }
        List<String> schemas = new ArrayList<>();
        String sql = "SELECT DISTINCT TABLE_SCHEMA FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys', 'oceanbase') "
                + "ORDER BY TABLE_SCHEMA";
        try (Connection conn = getConnection(config);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(30);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) schemas.add(rs.getString(1));
            }
            return schemas;
        } catch (Exception e) {
            log.error("获取 OceanBase 可访问数据库列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取可访问数据库列表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TableInfo> getTables(DataSourceConfig config) {
        if (isOracleMode(config)) {
            // Oracle-compatible OceanBase exposes the same ALL_* metadata
            // visibility contract. Delegate so granted cross-owner tables use
            // the same owner-qualified identity as native Oracle.
            return new OracleMetadataExplorer().getTables(config);
        }
        return getTablesMysql(config);
    }

    @Override
    public TablePageResult getTablesPage(DataSourceConfig config, Integer pageNo, Integer pageSize, String keyword) {
        if (isOracleMode(config)) {
            return new OracleMetadataExplorer().getTablesPage(config, pageNo, pageSize, keyword);
        }
        return new MySqlMetadataExplorer().getTablesPage(config, pageNo, pageSize, keyword);
    }

    private List<TableInfo> getTablesMysql(DataSourceConfig config) {
        if (collectAllVisibleOwners(config)) {
            return getTablesMysqlForAllVisibleOwners(config);
        }
        List<TableInfo> tables = new ArrayList<>();
        String sql = String.format(
            "SELECT TABLE_NAME, TABLE_COMMENT, TABLE_TYPE, ENGINE, TABLE_COLLATION, " +
            "TABLE_ROWS, DATA_LENGTH, INDEX_LENGTH, AUTO_INCREMENT, CREATE_TIME, UPDATE_TIME " +
            "FROM information_schema.TABLES WHERE TABLE_SCHEMA = '%s' ORDER BY TABLE_NAME",
            config.getDatabase()
        );
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                TableInfo table = new TableInfo();
                table.setTableName(rs.getString("TABLE_NAME"));
                table.setSchemaName(config.getDatabase());
                table.setTableComment(rs.getString("TABLE_COMMENT"));
                table.setTableType(rs.getString("TABLE_TYPE"));
                table.setEngine(rs.getString("ENGINE"));
                table.setCollation(rs.getString("TABLE_COLLATION"));
                table.setRowCount(rs.getLong("TABLE_ROWS"));
                table.setDataSizeBytes(rs.getLong("DATA_LENGTH"));
                table.setIndexSizeBytes(rs.getLong("INDEX_LENGTH"));
                table.setTotalSizeBytes(
                    (table.getDataSizeBytes() != null ? table.getDataSizeBytes() : 0L) +
                    (table.getIndexSizeBytes() != null ? table.getIndexSizeBytes() : 0L));
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

    /** Reads each database the account can inspect; database name is the MySQL-mode owner prefix. */
    private List<TableInfo> getTablesMysqlForAllVisibleOwners(DataSourceConfig config) {
        List<TableInfo> tables = new ArrayList<>();
        String sql =
            "SELECT t.TABLE_SCHEMA, t.TABLE_NAME, t.TABLE_COMMENT, t.TABLE_TYPE, t.ENGINE, t.TABLE_COLLATION, " +
            "       t.TABLE_ROWS, t.DATA_LENGTH, t.INDEX_LENGTH, t.AUTO_INCREMENT, t.CREATE_TIME, t.UPDATE_TIME, " +
            "       COALESCE(c.column_count, 0) AS column_count " +
            "FROM information_schema.TABLES t " +
            "LEFT JOIN (SELECT TABLE_SCHEMA, TABLE_NAME, COUNT(*) AS column_count " +
            "             FROM information_schema.COLUMNS GROUP BY TABLE_SCHEMA, TABLE_NAME) c " +
            "       ON c.TABLE_SCHEMA = t.TABLE_SCHEMA AND c.TABLE_NAME = t.TABLE_NAME " +
            "WHERE t.TABLE_SCHEMA NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys', 'oceanbase') " +
            "ORDER BY t.TABLE_SCHEMA, t.TABLE_NAME";
        try (Connection conn = getConnection(config);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(60);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TableInfo table = new TableInfo();
                    table.setTableName(rs.getString("TABLE_NAME"));
                    table.setSchemaName(rs.getString("TABLE_SCHEMA"));
                    table.setTableComment(rs.getString("TABLE_COMMENT"));
                    table.setTableType(rs.getString("TABLE_TYPE"));
                    table.setEngine(rs.getString("ENGINE"));
                    table.setCollation(rs.getString("TABLE_COLLATION"));
                    table.setRowCount(rs.getLong("TABLE_ROWS"));
                    table.setDataSizeBytes(rs.getLong("DATA_LENGTH"));
                    table.setIndexSizeBytes(rs.getLong("INDEX_LENGTH"));
                    table.setTotalSizeBytes(
                            (table.getDataSizeBytes() == null ? 0L : table.getDataSizeBytes())
                                    + (table.getIndexSizeBytes() == null ? 0L : table.getIndexSizeBytes()));
                    table.setAutoIncrement(rs.getLong("AUTO_INCREMENT"));
                    table.setCreateTime(rs.getTimestamp("CREATE_TIME"));
                    table.setUpdateTime(rs.getTimestamp("UPDATE_TIME"));
                    table.setDataUpdateTime(rs.getTimestamp("UPDATE_TIME"));
                    table.setColumnCount(rs.getInt("column_count"));
                    table.setDataSizeFormatted(formatBytes(table.getDataSizeBytes()));
                    table.setIndexSizeFormatted(formatBytes(table.getIndexSizeBytes()));
                    table.setTotalSizeFormatted(formatBytes(table.getTotalSizeBytes()));
                    tables.add(table);
                }
            }
            return tables;
        } catch (Exception e) {
            log.error("获取 OceanBase 全部可访问表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取全部可访问表失败: " + e.getMessage(), e);
        }
    }

    private boolean collectAllVisibleOwners(DataSourceConfig config) {
        Object value = config.getConnectorProperties() == null
                ? null : config.getConnectorProperties().get(COLLECT_ALL_VISIBLE_OWNERS);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private List<TableInfo> getTablesOracle(DataSourceConfig config) {
        List<TableInfo> tables = new ArrayList<>();
        String schema = config.getSchema() != null ? config.getSchema() : config.getUsername().toUpperCase();
        String sql = String.format(
            "SELECT t.TABLE_NAME, c.COMMENTS as TABLE_COMMENT, 'TABLE' as TABLE_TYPE, " +
            "t.NUM_ROWS, t.LAST_ANALYZED, m.TIMESTAMP as DATA_UPDATE_TIME FROM all_tables t " +
            "LEFT JOIN all_tab_comments c ON t.TABLE_NAME = c.TABLE_NAME AND t.OWNER = c.OWNER " +
            "LEFT JOIN all_tab_modifications m ON t.TABLE_NAME = m.TABLE_NAME AND t.OWNER = m.TABLE_OWNER " +
            "WHERE t.OWNER = '%s' ORDER BY t.TABLE_NAME", schema
        );
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                TableInfo table = new TableInfo();
                table.setTableName(rs.getString("TABLE_NAME"));
                table.setTableComment(rs.getString("TABLE_COMMENT"));
                table.setTableType(rs.getString("TABLE_TYPE"));
                table.setSchemaName(schema);
                table.setRowCount(rs.getLong("NUM_ROWS"));
                table.setUpdateTime(rs.getTimestamp("LAST_ANALYZED"));
                table.setDataUpdateTime(rs.getTimestamp("DATA_UPDATE_TIME"));
                tables.add(table);
            }
            for (TableInfo table : tables) {
                try {
                    Long size = getTableSize(config, table.getTableName());
                    table.setTotalSizeBytes(size);
                    table.setTotalSizeFormatted(formatBytes(size));
                } catch (Exception e) {
                    log.warn("获取表 {} 大小失败: {}", table.getTableName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("获取表列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表列表失败: " + e.getMessage(), e);
        }
        return tables;
    }

    @Override
    public TableInfo getTableInfo(DataSourceConfig config, String tableName) {
        if (isOracleMode(config)) {
            return new OracleMetadataExplorer().getTableInfo(config, tableName);
        }
        MysqlTable table = resolveMysqlTable(config, tableName);
        String originalDatabase = config.getDatabase();
        List<TableInfo> tables;
        try {
            config.setDatabase(table.database());
            tables = getTables(config);
        } finally {
            config.setDatabase(originalDatabase);
        }
        TableInfo tableInfo = tables.stream()
            .filter(t -> t.getTableName().equalsIgnoreCase(table.name()))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("表不存在: " + tableName));
        tableInfo.setColumns(getColumns(config, tableName));
        tableInfo.setIndexes(getIndexes(config, tableName));
        return tableInfo;
    }

    @Override
    public List<ColumnInfo> getColumns(DataSourceConfig config, String tableName) {
        if (isOracleMode(config)) {
            return new OracleMetadataExplorer().getColumns(config, tableName);
        }
        return getColumnsMysql(config, tableName);
    }

    private List<ColumnInfo> getColumnsMysql(DataSourceConfig config, String tableName) {
        List<ColumnInfo> columns = new ArrayList<>();
        MysqlTable table = resolveMysqlTable(config, tableName);
        String sql = String.format(
            "SELECT COLUMN_NAME, COLUMN_COMMENT, DATA_TYPE, COLUMN_TYPE, " +
            "CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE, IS_NULLABLE, " +
            "COLUMN_DEFAULT, COLUMN_KEY, EXTRA, ORDINAL_POSITION, CHARACTER_SET_NAME, COLLATION_NAME " +
            "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s' ORDER BY ORDINAL_POSITION",
            table.database(), table.name()
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

    private List<ColumnInfo> getColumnsOracle(DataSourceConfig config, String tableName) {
        List<ColumnInfo> columns = new ArrayList<>();
        String schema = config.getSchema() != null ? config.getSchema() : config.getUsername().toUpperCase();
        String sql = String.format(
            "SELECT c.COLUMN_NAME, cc.COMMENTS as COLUMN_COMMENT, c.DATA_TYPE, c.DATA_LENGTH, " +
            "c.DATA_PRECISION, c.DATA_SCALE, c.NULLABLE, c.DATA_DEFAULT, c.COLUMN_ID " +
            "FROM all_tab_columns c " +
            "LEFT JOIN all_col_comments cc ON c.TABLE_NAME = cc.TABLE_NAME AND c.COLUMN_NAME = cc.COLUMN_NAME AND c.OWNER = cc.OWNER " +
            "WHERE c.OWNER = '%s' AND c.TABLE_NAME = '%s' ORDER BY c.COLUMN_ID",
            schema, tableName.toUpperCase()
        );
        List<String> pkColumns = getPrimaryKeyColumnsOracle(config, tableName);
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ColumnInfo column = new ColumnInfo();
                column.setColumnName(rs.getString("COLUMN_NAME"));
                column.setColumnComment(rs.getString("COLUMN_COMMENT"));
                column.setDataType(rs.getString("DATA_TYPE"));
                column.setLength(rs.getLong("DATA_LENGTH"));
                column.setPrecision(rs.getInt("DATA_PRECISION"));
                column.setScale(rs.getInt("DATA_SCALE"));
                column.setNullable("Y".equals(rs.getString("NULLABLE")));
                column.setDefaultValue(rs.getString("DATA_DEFAULT"));
                column.setOrdinalPosition(rs.getInt("COLUMN_ID"));
                column.setPrimaryKey(pkColumns.contains(column.getColumnName()));
                String dataType = column.getDataType();
                if (column.getPrecision() != null && column.getPrecision() > 0) {
                    if (column.getScale() != null && column.getScale() > 0) {
                        column.setColumnType(dataType + "(" + column.getPrecision() + "," + column.getScale() + ")");
                    } else {
                        column.setColumnType(dataType + "(" + column.getPrecision() + ")");
                    }
                } else if (column.getLength() != null && column.getLength() > 0
                    && ("VARCHAR2".equals(dataType) || "CHAR".equals(dataType) || "NVARCHAR2".equals(dataType))) {
                    column.setColumnType(dataType + "(" + column.getLength() + ")");
                } else {
                    column.setColumnType(dataType != null ? dataType : "");
                }
                columns.add(column);
            }
        } catch (Exception e) {
            log.error("获取字段列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取字段列表失败: " + e.getMessage(), e);
        }
        return columns;
    }

    private List<String> getPrimaryKeyColumnsOracle(DataSourceConfig config, String tableName) {
        List<String> pkColumns = new ArrayList<>();
        String schema = config.getSchema() != null ? config.getSchema() : config.getUsername().toUpperCase();
        String sql = String.format(
            "SELECT cols.COLUMN_NAME FROM all_constraints cons " +
            "JOIN all_cons_columns cols ON cons.CONSTRAINT_NAME = cols.CONSTRAINT_NAME AND cons.OWNER = cols.OWNER " +
            "WHERE cons.CONSTRAINT_TYPE = 'P' AND cons.OWNER = '%s' AND cons.TABLE_NAME = '%s' ORDER BY cols.POSITION",
            schema, tableName.toUpperCase()
        );
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                pkColumns.add(rs.getString("COLUMN_NAME"));
            }
        } catch (Exception e) {
            log.warn("获取主键列失败: {}", e.getMessage());
        }
        return pkColumns;
    }

    @Override
    public List<IndexInfo> getIndexes(DataSourceConfig config, String tableName) {
        if (isOracleMode(config)) {
            return new OracleMetadataExplorer().getIndexes(config, tableName);
        }
        return getIndexesMysql(config, tableName);
    }

    private List<IndexInfo> getIndexesMysql(DataSourceConfig config, String tableName) {
        List<IndexInfo> indexes = new ArrayList<>();
        MysqlTable table = resolveMysqlTable(config, tableName);
        String sql = String.format("SHOW INDEX FROM `%s`.`%s`", table.database(), table.name());
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            Map<String, IndexInfo> indexMap = new LinkedHashMap<>();
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

    private List<IndexInfo> getIndexesOracle(DataSourceConfig config, String tableName) {
        List<IndexInfo> indexes = new ArrayList<>();
        String schema = config.getSchema() != null ? config.getSchema() : config.getUsername().toUpperCase();
        String sql = String.format(
            "SELECT i.INDEX_NAME, i.UNIQUENESS, i.INDEX_TYPE, ic.COLUMN_NAME, ic.COLUMN_POSITION " +
            "FROM all_indexes i " +
            "JOIN all_ind_columns ic ON i.INDEX_NAME = ic.INDEX_NAME AND i.OWNER = ic.INDEX_OWNER " +
            "WHERE i.OWNER = '%s' AND i.TABLE_NAME = '%s' ORDER BY i.INDEX_NAME, ic.COLUMN_POSITION",
            schema, tableName.toUpperCase()
        );
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            Map<String, IndexInfo> indexMap = new LinkedHashMap<>();
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                IndexInfo index = indexMap.get(indexName);
                if (index == null) {
                    index = new IndexInfo();
                    index.setIndexName(indexName);
                    index.setUnique("UNIQUE".equals(rs.getString("UNIQUENESS")));
                    index.setIndexType(rs.getString("INDEX_TYPE"));
                    index.setColumns(new ArrayList<>());
                    indexMap.put(indexName, index);
                }
                index.getColumns().add(rs.getString("COLUMN_NAME"));
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
        if (isOracleMode(config)) {
            return new OracleMetadataExplorer().getTableRowCount(config, tableName);
        }
        MysqlTable table = resolveMysqlTable(config, tableName);
        String sql = String.format("SELECT COUNT(*) FROM `%s`.`%s`", table.database(), table.name());
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
        if (isOracleMode(config)) {
            return new OracleMetadataExplorer().getTableSize(config, tableName);
        }
        MysqlTable table = resolveMysqlTable(config, tableName);
        String sql = String.format(
            "SELECT DATA_LENGTH + INDEX_LENGTH as total_size FROM information_schema.TABLES " +
            "WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'",
            table.database(), table.name()
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
        if (isOracleMode(config)) {
            try (Connection conn = getConnection(config);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT SUM(bytes) as total_size FROM user_segments")) {
                if (rs.next()) {
                    return rs.getLong("total_size");
                }
            } catch (Exception e) {
                log.error("获取数据库大小失败: {}", e.getMessage(), e);
                throw new RuntimeException("获取数据库大小失败: " + e.getMessage(), e);
            }
            return 0L;
        }
        String sql = String.format(
            "SELECT SUM(DATA_LENGTH + INDEX_LENGTH) as total_size FROM information_schema.TABLES WHERE TABLE_SCHEMA = '%s'",
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
        DataSourceConfig config = request.getDataSource();
        if (isOracleMode(config)) {
            return new OracleMetadataExplorer().sampleData(request);
        }
        String columns = buildColumnSelection(request.getColumns());
        String where = buildWhereClause(request.getWhereClause());
        int sampleSize = request.getSampleSize();

        MysqlTable table = resolveMysqlTable(config, request.getTableName());
        String tableName = String.format("`%s`.`%s`", table.database(), table.name());
        String sql;
        switch (request.getSampleMethod()) {
            case RANDOM:
                sql = String.format(
                    "SELECT %s FROM %s %s ORDER BY RAND() LIMIT %d",
                    columns, tableName, where, sampleSize
                );
                break;
            case LAST:
                sql = String.format(
                    "SELECT * FROM (SELECT %s FROM %s %s ORDER BY 1 DESC LIMIT %d) t ORDER BY 1 ASC",
                    columns, tableName, where, sampleSize
                );
                break;
            case FIRST:
            default:
                sql = String.format(
                    "SELECT %s FROM %s %s LIMIT %d",
                    columns, tableName, where, sampleSize
                );
                break;
        }
        return executeSampleQuery(config, request, sql);
    }

    /** Resolves a persisted database.table identity for MySQL-compatible OceanBase. */
    private MysqlTable resolveMysqlTable(DataSourceConfig config, String tableName) {
        String value = tableName == null ? "" : tableName.trim();
        int separator = value.indexOf('.');
        if (separator > 0 && separator < value.length() - 1
                && value.indexOf('.', separator + 1) < 0) {
            return new MysqlTable(value.substring(0, separator), value.substring(separator + 1));
        }
        return new MysqlTable(config.getDatabase(), value);
    }

    private record MysqlTable(String database, String name) { }
}
