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
 * Vertica metadata explorer with database-side table preview pagination.
 */
@Slf4j
public class VerticaMetadataExplorer extends GenericJdbcMetadataExplorer {

    @Override
    public TablePageResult getTablesPage(DataSourceConfig config, Integer pageNo, Integer pageSize, String keyword) {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 100 : Math.min(pageSize, 500);
        int offset = (safePageNo - 1) * safePageSize;
        String schema = config.getSchema();
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty()
                ? null
                : "%" + keyword.trim().toLowerCase() + "%";

        String filterSql = " WHERE t.table_schema NOT IN ('v_catalog', 'v_monitor', 'v_internal') "
                + "AND (? IS NULL OR t.table_schema = ?) "
                + "AND (? IS NULL OR LOWER(t.table_name) LIKE ?) ";
        String countSql = "SELECT COUNT(1) FROM information_schema.tables t" + filterSql;
        String pageSql = "SELECT t.table_schema, t.table_name, t.table_type, COALESCE(c.column_count, 0) AS column_count "
                + "FROM information_schema.tables t "
                + "LEFT JOIN (SELECT table_schema, table_name, COUNT(*) AS column_count "
                + "           FROM information_schema.columns GROUP BY table_schema, table_name) c "
                + "  ON c.table_schema = t.table_schema AND c.table_name = t.table_name "
                + filterSql
                + "ORDER BY t.table_schema, t.table_name LIMIT ? OFFSET ?";

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
                ps.setInt(idx++, safePageSize);
                ps.setInt(idx, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        TableInfo table = new TableInfo();
                        table.setSchemaName(rs.getString("table_schema"));
                        table.setTableName(rs.getString("table_name"));
                        table.setTableType(String.valueOf(rs.getString("table_type")).toUpperCase().contains("VIEW") ? "VIEW" : "TABLE");
                        table.setColumnCount(rs.getInt("column_count"));
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
            log.warn("Vertica 分页元数据查询失败，回退通用 JDBC: {}", e.getMessage());
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
