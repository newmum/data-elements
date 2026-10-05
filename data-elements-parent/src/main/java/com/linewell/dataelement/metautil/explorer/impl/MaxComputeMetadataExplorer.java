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
 * MaxCompute metadata explorer.
 *
 * <p>Newer MaxCompute JDBC deployments expose INFORMATION_SCHEMA. If a tenant's
 * driver/version does not support it, the generic JDBC fallback still keeps the
 * registration flow available.</p>
 */
@Slf4j
public class MaxComputeMetadataExplorer extends GenericJdbcMetadataExplorer {

    @Override
    public TablePageResult getTablesPage(DataSourceConfig config, Integer pageNo, Integer pageSize, String keyword) {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 100 : Math.min(pageSize, 500);
        int offset = (safePageNo - 1) * safePageSize;
        String schema = config.getSchema() == null || config.getSchema().isBlank()
                ? config.getDatabase()
                : config.getSchema();
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty()
                ? null
                : "%" + keyword.trim().toLowerCase() + "%";

        String filterSql = " WHERE (? IS NULL OR table_schema = ? OR table_catalog = ?) "
                + "AND (? IS NULL OR LOWER(table_name) LIKE ?) ";
        String countSql = "SELECT COUNT(1) FROM information_schema.tables" + filterSql;
        String pageSql = "SELECT table_schema, table_name, table_type FROM information_schema.tables "
                + filterSql
                + "ORDER BY table_schema, table_name LIMIT ? OFFSET ?";

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
                        table.setColumnCount(0);
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
            log.warn("MaxCompute INFORMATION_SCHEMA 分页不可用，回退通用 JDBC: {}", e.getMessage());
            return super.getTablesPage(config, pageNo, pageSize, keyword);
        }
    }

    private int bindFilter(PreparedStatement ps, String schema, String keyword) throws Exception {
        int idx = 1;
        ps.setString(idx++, schema);
        ps.setString(idx++, schema);
        ps.setString(idx++, schema);
        ps.setString(idx++, keyword);
        ps.setString(idx++, keyword == null ? "" : keyword);
        return idx;
    }
}
