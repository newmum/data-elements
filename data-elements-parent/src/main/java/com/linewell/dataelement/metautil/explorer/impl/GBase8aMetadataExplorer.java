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
 * GBase 8a metadata explorer.
 *
 * <p>GBase 8a exposes MySQL-compatible INFORMATION_SCHEMA in common deployments,
 * so the registration preview can page at database side instead of scanning all
 * tables and columns through DatabaseMetaData.</p>
 */
@Slf4j
public class GBase8aMetadataExplorer extends GenericJdbcMetadataExplorer {

    @Override
    public TablePageResult getTablesPage(DataSourceConfig config, Integer pageNo, Integer pageSize, String keyword) {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 100 : Math.min(pageSize, 500);
        int offset = (safePageNo - 1) * safePageSize;
        String schema = config.getDatabase();
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty()
                ? null
                : "%" + keyword.trim().toLowerCase() + "%";

        String filterSql = " WHERE t.TABLE_SCHEMA = ? "
                + "AND (? IS NULL OR LOWER(t.TABLE_NAME) LIKE ? OR LOWER(COALESCE(t.TABLE_COMMENT, '')) LIKE ?) ";
        String countSql = "SELECT COUNT(1) FROM information_schema.TABLES t" + filterSql;
        String pageSql = "SELECT t.TABLE_SCHEMA, t.TABLE_NAME, t.TABLE_COMMENT, t.TABLE_TYPE, "
                + "COALESCE(t.TABLE_ROWS, 0) AS TABLE_ROWS, COALESCE(t.DATA_LENGTH, 0) AS DATA_LENGTH, "
                + "COALESCE(t.INDEX_LENGTH, 0) AS INDEX_LENGTH, COALESCE(c.COLUMN_COUNT, 0) AS COLUMN_COUNT "
                + "FROM information_schema.TABLES t "
                + "LEFT JOIN (SELECT TABLE_SCHEMA, TABLE_NAME, COUNT(*) AS COLUMN_COUNT "
                + "           FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? GROUP BY TABLE_SCHEMA, TABLE_NAME) c "
                + "  ON c.TABLE_SCHEMA = t.TABLE_SCHEMA AND c.TABLE_NAME = t.TABLE_NAME "
                + filterSql
                + "ORDER BY t.TABLE_NAME LIMIT ?, ?";

        try (Connection conn = getConnection(config)) {
            TablePageResult page = new TablePageResult();
            page.setPageNo(safePageNo);
            page.setPageSize(safePageSize);

            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                bindFilter(ps, 1, schema, normalizedKeyword);
                try (ResultSet rs = ps.executeQuery()) {
                    page.setTotal(rs.next() ? rs.getLong(1) : 0L);
                }
            }

            List<TableInfo> rows = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(pageSql)) {
                int idx = 1;
                ps.setString(idx++, schema);
                idx = bindFilter(ps, idx, schema, normalizedKeyword);
                ps.setInt(idx++, offset);
                ps.setInt(idx, safePageSize);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(readTable(rs));
                    }
                }
            }
            page.setRows(rows);
            return page;
        } catch (Exception e) {
            log.warn("GBase 8a 分页元数据查询失败，回退通用 JDBC: {}", e.getMessage());
            return super.getTablesPage(config, pageNo, pageSize, keyword);
        }
    }

    private int bindFilter(PreparedStatement ps, int idx, String schema, String keyword) throws Exception {
        ps.setString(idx++, schema);
        ps.setString(idx++, keyword);
        ps.setString(idx++, keyword == null ? "" : keyword);
        ps.setString(idx++, keyword == null ? "" : keyword);
        return idx;
    }

    private TableInfo readTable(ResultSet rs) throws Exception {
        TableInfo table = new TableInfo();
        table.setSchemaName(rs.getString("TABLE_SCHEMA"));
        table.setTableName(rs.getString("TABLE_NAME"));
        table.setTableComment(rs.getString("TABLE_COMMENT"));
        table.setTableType(String.valueOf(rs.getString("TABLE_TYPE")).toUpperCase().contains("VIEW") ? "VIEW" : "TABLE");
        table.setRowCount(rs.getLong("TABLE_ROWS"));
        table.setDataSizeBytes(rs.getLong("DATA_LENGTH"));
        table.setIndexSizeBytes(rs.getLong("INDEX_LENGTH"));
        table.setTotalSizeBytes(table.getDataSizeBytes() + table.getIndexSizeBytes());
        table.setDataSizeFormatted(formatBytes(table.getDataSizeBytes()));
        table.setIndexSizeFormatted(formatBytes(table.getIndexSizeBytes()));
        table.setTotalSizeFormatted(formatBytes(table.getTotalSizeBytes()));
        table.setColumnCount(rs.getInt("COLUMN_COUNT"));
        return table;
    }
}
