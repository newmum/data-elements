package com.linewell.dataelement.metautil.explorer.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.DatabaseInfo;
import com.linewell.dataelement.metautil.model.dto.SampleDataResult;
import com.linewell.dataelement.metautil.model.dto.SampleRequest;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import com.linewell.dataelement.metautil.model.dto.TablePageResult;

/**
 * KingBase（人大金仓）元数据探查器。
 *
 * <p>KingBase 兼容 PostgreSQL 的系统目录，但在大库上逐表调用
 * {@code DatabaseMetaData} 或逐表统计会非常慢。本实现尽量使用 pg_catalog
 * 批量读取表、字段数、注释和统计信息，避免登记详情页探查超时。</p>
 */
@Slf4j
public class KingBaseMetadataExplorer extends PostgreSqlMetadataExplorer {

    private static final String COLLECT_ALL_VISIBLE_OWNERS = "metadataCollectAllVisibleOwners";

    private String getSchema(DataSourceConfig config) {
        String schema = config.getSchema();
        return schema == null || schema.trim().isEmpty() ? "public" : schema.trim();
    }

    @Override
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        DatabaseInfo info = super.getDatabaseInfo(config);
        info.setDatabaseType("KingBase");
        String schema = getSchema(config);

        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(30);

            try (ResultSet rs = stmt.executeQuery("SELECT version()")) {
                if (rs.next()) {
                    info.setVersion(rs.getString(1));
                }
            }

            // 部分 KingBase 版本把过程能力体现为 FUNCTION，补充统计避免展示为 0。
            if (info.getProcedureCount() == null || info.getProcedureCount() == 0) {
                String fallbackProcedureSql =
                    "SELECT COUNT(*) FROM information_schema.routines " +
                    "WHERE routine_schema = ? AND routine_type IN ('PROCEDURE', 'FUNCTION')";
                try (PreparedStatement ps = conn.prepareStatement(fallbackProcedureSql)) {
                    ps.setQueryTimeout(30);
                    ps.setString(1, schema);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            info.setProcedureCount(rs.getInt(1));
                        }
                    }
                } catch (Exception e) {
                    log.warn("获取 KingBase 存储过程数量失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("获取 KingBase 版本信息失败: {}", e.getMessage());
        }

        return info;
    }

    @Override
    public List<TableInfo> getTables(DataSourceConfig config) {
        if (collectAllVisibleOwners(config)) {
            return getTablesForAllVisibleOwners(config);
        }
        List<TableInfo> tables = new ArrayList<>();
        String schema = getSchema(config);

        // 一次性批量读取表/视图、中文注释、字段数、估算行数和容量，避免 300+ 张表时探查超时。
        String sql =
            "SELECT c.relname AS table_name, " +
            "       COALESCE(d.description, '') AS table_comment, " +
            "       CASE c.relkind WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'VIEW' ELSE 'BASE TABLE' END AS table_type, " +
            "       COALESCE(c.reltuples::bigint, 0) AS estimate_rows, " +
            "       pg_total_relation_size(c.oid) AS total_size, " +
            "       pg_relation_size(c.oid) AS data_size, " +
            "       pg_indexes_size(c.oid) AS index_size, " +
            "       COALESCE(col.column_count, 0) AS column_count " +
            "FROM pg_class c " +
            "JOIN pg_namespace n ON n.oid = c.relnamespace " +
            "LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = 0 " +
            "LEFT JOIN ( " +
            "    SELECT table_schema, table_name, COUNT(*) AS column_count " +
            "    FROM information_schema.columns " +
            "    WHERE table_schema = ? " +
            "    GROUP BY table_schema, table_name " +
            ") col ON col.table_schema = n.nspname AND col.table_name = c.relname " +
            "WHERE n.nspname = ? " +
            "  AND c.relkind IN ('r', 'p', 'v', 'm') " +
            "  AND c.relname NOT LIKE 'pg_%' " +
            "ORDER BY c.relname";

        try (Connection conn = getConnection(config);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(30);
            ps.setString(1, schema);
            ps.setString(2, schema);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
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
                    tables.add(table);
                }
            }
        } catch (Exception e) {
            log.error("获取 KingBase 表列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表列表失败: " + e.getMessage(), e);
        }

        return tables;
    }

    @Override
    public List<String> getSchemasWithTables(DataSourceConfig config) {
        List<String> schemas = new ArrayList<>();
        String sql = "SELECT DISTINCT table_schema FROM information_schema.tables "
                + "WHERE table_schema NOT IN ('pg_catalog', 'information_schema', 'pg_toast') "
                + "  AND table_schema NOT LIKE 'pg_%' "
                + "  AND table_type IN ('BASE TABLE', 'VIEW') ORDER BY table_schema";
        try (Connection conn = getConnection(config);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(30);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) schemas.add(rs.getString(1));
            }
            return schemas;
        } catch (Exception e) {
            log.error("获取 KingBase 可访问 Schema 列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取可访问 Schema 列表失败: " + e.getMessage(), e);
        }
    }

    /** Reads every table/view exposed to the connection account in one query. */
    private List<TableInfo> getTablesForAllVisibleOwners(DataSourceConfig config) {
        List<TableInfo> tables = new ArrayList<>();
        String sql =
            "SELECT n.nspname AS schema_name, c.relname AS table_name, " +
            "       COALESCE(d.description, '') AS table_comment, " +
            "       CASE c.relkind WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'VIEW' ELSE 'BASE TABLE' END AS table_type, " +
            "       COALESCE(c.reltuples::bigint, 0) AS estimate_rows, " +
            "       pg_total_relation_size(c.oid) AS total_size, pg_relation_size(c.oid) AS data_size, " +
            "       pg_indexes_size(c.oid) AS index_size, COALESCE(col.column_count, 0) AS column_count " +
            "FROM information_schema.tables visible " +
            "JOIN pg_namespace n ON n.nspname = visible.table_schema " +
            "JOIN pg_class c ON c.relnamespace = n.oid AND c.relname = visible.table_name " +
            "LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = 0 " +
            "LEFT JOIN (SELECT table_schema, table_name, COUNT(*) AS column_count " +
            "             FROM information_schema.columns GROUP BY table_schema, table_name) col " +
            "       ON col.table_schema = n.nspname AND col.table_name = c.relname " +
            "WHERE visible.table_schema NOT IN ('pg_catalog', 'information_schema', 'pg_toast') " +
            "  AND visible.table_schema NOT LIKE 'pg_%' " +
            "  AND visible.table_type IN ('BASE TABLE', 'VIEW') " +
            "  AND c.relkind IN ('r', 'p', 'v', 'm') " +
            "ORDER BY n.nspname, c.relname";
        try (Connection conn = getConnection(config);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(60);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TableInfo table = new TableInfo();
                    table.setTableName(rs.getString("table_name"));
                    table.setTableComment(rs.getString("table_comment"));
                    table.setTableType(rs.getString("table_type"));
                    // A KingBase schema is the queryable owner prefix.  The
                    // collector persists foreign objects as schema.table.
                    table.setSchemaName(rs.getString("schema_name"));
                    table.setRowCount(Math.max(0, rs.getLong("estimate_rows")));
                    table.setTotalSizeBytes(rs.getLong("total_size"));
                    table.setDataSizeBytes(rs.getLong("data_size"));
                    table.setIndexSizeBytes(rs.getLong("index_size"));
                    table.setColumnCount(rs.getInt("column_count"));
                    table.setTotalSizeFormatted(formatBytes(table.getTotalSizeBytes()));
                    table.setDataSizeFormatted(formatBytes(table.getDataSizeBytes()));
                    table.setIndexSizeFormatted(formatBytes(table.getIndexSizeBytes()));
                    tables.add(table);
                }
            }
            return tables;
        } catch (Exception e) {
            log.error("获取 KingBase 全部可访问表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取全部可访问表失败: " + e.getMessage(), e);
        }
    }

    private boolean collectAllVisibleOwners(DataSourceConfig config) {
        Object value = config.getConnectorProperties() == null
                ? null : config.getConnectorProperties().get(COLLECT_ALL_VISIBLE_OWNERS);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
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
            "FROM pg_class c " +
            "JOIN pg_namespace n ON n.oid = c.relnamespace " +
            "LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = 0 " +
            "LEFT JOIN ( " +
            "    SELECT table_schema, table_name, COUNT(*) AS column_count " +
            "    FROM information_schema.columns " +
            "    WHERE table_schema = ? " +
            "    GROUP BY table_schema, table_name " +
            ") col ON col.table_schema = n.nspname AND col.table_name = c.relname " +
            "WHERE n.nspname = ? " +
            "  AND c.relkind IN ('r', 'p', 'v', 'm') " +
            "  AND c.relname NOT LIKE 'pg_%' " +
            "  AND (? IS NULL OR LOWER(c.relname) LIKE ? OR LOWER(COALESCE(d.description, '')) LIKE ?) ";
        String countSql = "SELECT COUNT(1) " + fromSql;
        String pageSql =
            "SELECT c.relname AS table_name, " +
            "       COALESCE(d.description, '') AS table_comment, " +
            "       CASE c.relkind WHEN 'v' THEN 'VIEW' WHEN 'm' THEN 'VIEW' ELSE 'BASE TABLE' END AS table_type, " +
            "       COALESCE(c.reltuples::bigint, 0) AS estimate_rows, " +
            "       pg_total_relation_size(c.oid) AS total_size, " +
            "       pg_relation_size(c.oid) AS data_size, " +
            "       pg_indexes_size(c.oid) AS index_size, " +
            "       COALESCE(col.column_count, 0) AS column_count " +
            fromSql +
            "ORDER BY c.relname LIMIT ? OFFSET ?";

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
            log.error("KingBase鍒嗛〉鑾峰彇琛ㄥ垪琛ㄥけ璐? {}", e.getMessage(), e);
            throw new RuntimeException("KingBase鍒嗛〉鑾峰彇琛ㄥ垪琛ㄥけ璐? " + e.getMessage(), e);
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
    public List<ColumnInfo> getColumns(DataSourceConfig config, String tableName) {
        List<ColumnInfo> columns = new ArrayList<>();
        QualifiedTable table = resolveQualifiedTable(config, tableName);
        String schema = table.schema();

        // 字段、注释和主键信息合并到一次查询中，避免每张表再额外新开连接查主键。
        String sql =
            "SELECT c.column_name, c.data_type, c.udt_name, " +
            "       c.character_maximum_length, c.numeric_precision, c.numeric_scale, " +
            "       c.is_nullable, c.column_default, c.ordinal_position, " +
            "       CASE WHEN pk.attname IS NULL THEN 0 ELSE 1 END AS is_primary_key, " +
            "       (SELECT col_description(cls.oid, c.ordinal_position::int) " +
            "          FROM pg_class cls " +
            "          JOIN pg_namespace ns ON ns.oid = cls.relnamespace " +
            "         WHERE ns.nspname = c.table_schema AND cls.relname = c.table_name LIMIT 1) AS column_comment " +
            "FROM information_schema.columns c " +
            "LEFT JOIN ( " +
            "    SELECT n.nspname AS table_schema, cls.relname AS table_name, a.attname " +
            "    FROM pg_index i " +
            "    JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) " +
            "    JOIN pg_class cls ON cls.oid = i.indrelid " +
            "    JOIN pg_namespace n ON n.oid = cls.relnamespace " +
            "    WHERE i.indisprimary " +
            ") pk ON pk.table_schema = c.table_schema AND pk.table_name = c.table_name AND pk.attname = c.column_name " +
            "WHERE c.table_schema = ? AND c.table_name = ? " +
            "ORDER BY c.ordinal_position";

        try (Connection conn = getConnection(config);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(30);
            ps.setString(1, schema);
            ps.setString(2, table.name());

            try (ResultSet rs = ps.executeQuery()) {
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
                    column.setPrimaryKey(rs.getInt("is_primary_key") == 1);

                    String defaultValue = column.getDefaultValue();
                    column.setAutoIncrement(defaultValue != null &&
                        (defaultValue.contains("nextval") || defaultValue.contains("_seq")));

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
            }
        } catch (Exception e) {
            log.error("获取 KingBase 字段列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取字段列表失败: " + e.getMessage(), e);
        }

        return columns;
    }

    @Override
    public SampleDataResult sampleData(SampleRequest request) {
        String columns = buildColumnSelection(request.getColumns());
        String where = buildWhereClause(request.getWhereClause());
        QualifiedTable table = resolveQualifiedTable(request.getDataSource(), request.getTableName());
        String schema = table.schema();
        String tableName = table.name();
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

    /** Splits the persisted schema.table identity without changing legacy bare names. */
    private QualifiedTable resolveQualifiedTable(DataSourceConfig config, String tableName) {
        String value = tableName == null ? "" : tableName.trim();
        int separator = value.indexOf('.');
        if (separator > 0 && separator < value.length() - 1
                && value.indexOf('.', separator + 1) < 0) {
            return new QualifiedTable(value.substring(0, separator), value.substring(separator + 1));
        }
        return new QualifiedTable(getSchema(config), value);
    }

    private record QualifiedTable(String schema, String name) { }
}
