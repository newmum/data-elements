package com.linewell.dataelement.metautil.explorer.impl;

import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.metautil.model.dto.TableInfo;
import com.linewell.dataelement.metautil.model.dto.TablePageResult;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL Server metadata explorer.
 *
 * <p>Only table preview pagination is specialized here. Detailed table, column,
 * index and sample-data behavior keeps using the generic JDBC implementation.</p>
 */
@Slf4j
public class SqlServerMetadataExplorer extends GenericJdbcMetadataExplorer {

    @Override
    public TablePageResult getTablesPage(DataSourceConfig config, Integer pageNo, Integer pageSize, String keyword) {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 100 : Math.min(pageSize, 500);
        int offset = (safePageNo - 1) * safePageSize;
        String schema = config.getSchema();
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty()
                ? null
                : "%" + keyword.trim().toLowerCase() + "%";

        String filterSql = " WHERE t.TABLE_TYPE IN ('BASE TABLE', 'VIEW') "
                + "AND (? IS NULL OR t.TABLE_SCHEMA = ?) "
                + "AND (? IS NULL OR LOWER(t.TABLE_NAME) LIKE ?) ";
        String countSql = "SELECT COUNT(1) FROM INFORMATION_SCHEMA.TABLES t" + filterSql;
        String pageSql = "SELECT t.TABLE_SCHEMA, t.TABLE_NAME, t.TABLE_TYPE, COALESCE(c.COLUMN_COUNT, 0) AS COLUMN_COUNT "
                + "FROM INFORMATION_SCHEMA.TABLES t "
                + "LEFT JOIN (SELECT TABLE_SCHEMA, TABLE_NAME, COUNT(*) AS COLUMN_COUNT "
                + "           FROM INFORMATION_SCHEMA.COLUMNS GROUP BY TABLE_SCHEMA, TABLE_NAME) c "
                + "  ON c.TABLE_SCHEMA = t.TABLE_SCHEMA AND c.TABLE_NAME = t.TABLE_NAME "
                + filterSql
                + "ORDER BY t.TABLE_SCHEMA, t.TABLE_NAME OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        try (Connection conn = getConnection(config)) {
            TablePageResult page = new TablePageResult();
            page.setPageNo(safePageNo);
            page.setPageSize(safePageSize);

            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                bindFilter(ps, schema, normalizedKeyword);
                try (ResultSet rs = ps.executeQuery()) {
                    page.setTotal(rs.next() ? rs.getLong(1) : 0L);
                }
            }

            List<TableInfo> rows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(pageSql)) {
                int idx = bindFilter(ps, schema, normalizedKeyword);
                ps.setInt(idx++, offset);
                ps.setInt(idx, safePageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        TableInfo table = new TableInfo();
                        table.setSchemaName(rs.getString("TABLE_SCHEMA"));
                        table.setTableName(rs.getString("TABLE_NAME"));
                        table.setTableType("VIEW".equalsIgnoreCase(rs.getString("TABLE_TYPE")) ? "VIEW" : "TABLE");
                        table.setColumnCount(rs.getInt("COLUMN_COUNT"));
                        table.setRowCount(0L);
                        table.setTotalSizeBytes(0L);
                        table.setTotalSizeFormatted(formatBytes(0L));
                        rows.add(table);
                    }
                }
            }
            page.setRows(rows);
            return page;
        } catch (Exception e) {
            log.warn("SQL Server 分页元数据查询失败，回退通用 JDBC: {}", e.getMessage());
            return super.getTablesPage(config, pageNo, pageSize, keyword);
        }
    }

    private int bindFilter(PreparedStatement ps, String schema, String keyword) throws Exception {
        int idx = 1;
        ps.setString(idx++, schema);
        ps.setString(idx++, schema);
        ps.setString(idx++, keyword);
        ps.setString(idx++, keyword == null ? "" : keyword);
        return idx;
    }
}
