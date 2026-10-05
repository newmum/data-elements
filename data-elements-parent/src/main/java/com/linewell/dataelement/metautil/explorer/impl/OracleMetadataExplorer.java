package com.linewell.dataelement.metautil.explorer.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Oracle 元数据探查器
 * 
 * @author MetaUtil
 */
@Slf4j
public class OracleMetadataExplorer extends AbstractMetadataExplorer {

    /**
     * Oracle supplied owners that can be visible from {@code ALL_TABLES} and
     * {@code ALL_VIEWS}.  Discovery deliberately uses ALL_* instead of USER_*
     * so a business account can register a table granted by another business
     * account; consequently, it must explicitly keep database-maintenance
     * schemas such as EXFSYS out of the business metadata scope.
     *
     * <p>This list contains only Oracle-maintained accounts that occur across
     * supported Oracle versions.  It does not contain ordinary application
     * owners, so cross-schema business objects remain discoverable.</p>
     */
    private static final java.util.Set<String> SYSTEM_SCHEMAS = java.util.Set.of(
            "ANONYMOUS", "APEX_LISTENER", "APEX_PUBLIC_USER", "APEX_REST_PUBLIC_USER",
            "APPQOSSYS", "AUDSYS", "CTXSYS", "DBSNMP", "DIP", "DMSYS", "DVF", "DVSYS",
            "EXFSYS", "FLOWS_FILES", "GGSYS",
            "GSMADMIN_INTERNAL", "GSMCATUSER", "GSMUSER", "LBACSYS", "MDDATA", "MDSYS",
            "MGMT_VIEW", "OJVMSYS", "OLAPSYS", "ORACLE_OCM", "ORDDATA", "ORDPLUGINS",
            "ORDS_METADATA", "ORDS_PUBLIC_USER", "ORDSYS", "OUTLN", "OWBSYS", "OWBSYS_AUDIT",
            "PDBADMIN", "PERFSTAT",
            "REMOTE_SCHEDULER_AGENT", "SCOTT", "SCHEDULER$_AGENT", "SI_INFORMTN_SCHEMA",
            "SPATIAL_CSW_ADMIN_USR", "SPATIAL_WFS_ADMIN_USR", "SYS", "SYSBACKUP",
            "SYSDG", "SYSKM", "SYSMAN", "SYSTEM", "SYSRAC", "WMSYS", "XDB", "XS$NULL"
    );

    @Override
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        DatabaseInfo info = new DatabaseInfo();
        info.setDatabaseName(config.getDatabase());
        info.setDatabaseType("Oracle");
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            
            // 获取版本
            try (ResultSet rs = stmt.executeQuery("SELECT BANNER FROM V$VERSION WHERE ROWNUM = 1")) {
                if (rs.next()) {
                    info.setVersion(rs.getString(1));
                }
            }
            
            // 获取字符集
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT VALUE FROM NLS_DATABASE_PARAMETERS WHERE PARAMETER = 'NLS_CHARACTERSET'")) {
                if (rs.next()) {
                    info.setCharset(rs.getString(1));
                }
            }
            
            // 获取数据库大小（用户表空间大小）
            String schema = config.getSchema() != null ? config.getSchema() : config.getUsername().toUpperCase();
            String sizeSql = String.format(
                "SELECT SUM(bytes) as total_size FROM dba_segments WHERE owner = '%s'", schema
            );
            try (ResultSet rs = stmt.executeQuery(sizeSql)) {
                if (rs.next()) {
                    info.setTotalSizeBytes(rs.getLong("total_size"));
                    info.setTotalSizeFormatted(formatBytes(info.getTotalSizeBytes()));
                    info.setDataSizeBytes(info.getTotalSizeBytes());
                    info.setDataSizeFormatted(info.getTotalSizeFormatted());
                }
            } catch (Exception e) {
                // 如果没有权限查询dba_segments，使用user_segments
                try (ResultSet rs = stmt.executeQuery("SELECT SUM(bytes) as total_size FROM user_segments")) {
                    if (rs.next()) {
                        info.setTotalSizeBytes(rs.getLong("total_size"));
                        info.setTotalSizeFormatted(formatBytes(info.getTotalSizeBytes()));
                        info.setDataSizeBytes(info.getTotalSizeBytes());
                        info.setDataSizeFormatted(info.getTotalSizeFormatted());
                    }
                }
            }
            
            // 获取表数量
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM user_tables")) {
                if (rs.next()) {
                    info.setTableCount(rs.getInt(1));
                }
            }

            // 获取视图数量
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM user_views")) {
                if (rs.next()) {
                    info.setViewCount(rs.getInt(1));
                }
            } catch (Exception e) {
                log.warn("获取Oracle视图数量失败: {}", e.getMessage());
            }

            // 获取存储过程数量
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM user_procedures WHERE object_type = 'PROCEDURE'")) {
                if (rs.next()) {
                    info.setProcedureCount(rs.getInt(1));
                }
            } catch (Exception e) {
                log.warn("获取Oracle存储过程数量失败: {}", e.getMessage());
            }

            // 获取用户数量
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM all_users")) {
                if (rs.next()) {
                    info.setUserCount(rs.getInt(1));
                }
            } catch (Exception e) {
                log.warn("获取Oracle用户数量失败: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("获取Oracle数据库信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取数据库信息失败: " + e.getMessage(), e);
        }
        
        return info;
    }

    @Override
    public List<String> getDatabases(DataSourceConfig config) {
        // Oracle没有传统意义上的数据库列表，返回表空间列表
        List<String> tablespaces = new ArrayList<>();
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT TABLESPACE_NAME FROM user_tablespaces")) {
            
            while (rs.next()) {
                tablespaces.add(rs.getString(1));
            }
            
        } catch (Exception e) {
            log.error("获取表空间列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表空间列表失败: " + e.getMessage(), e);
        }
        
        return tablespaces;
    }

    @Override
    public List<String> getSchemas(DataSourceConfig config) {
        List<String> schemas = new ArrayList<>();
        
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT USERNAME FROM all_users ORDER BY USERNAME")) {
            
            while (rs.next()) {
                String username = rs.getString(1);
                if (!isSystemSchema(username)) {
                    schemas.add(username);
                }
            }
            
        } catch (Exception e) {
            log.error("获取Schema列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取Schema列表失败: " + e.getMessage(), e);
        }
        
        return schemas;
    }

    /**
     * The registration page must only offer owners that are visible through
     * ALL_TABLES / ALL_VIEWS.  ALL_USERS includes accounts that the current
     * connection cannot inspect and accounts with no explorable objects, which
     * previously produced a selectable-but-invalid Oracle schema.
     *
     * <p>This is intentionally a single metadata query.  Do not replace it
     * with one table-page probe per owner: that approach is prohibitively
     * expensive on Oracle instances with many accounts.</p>
     */
    @Override
    public List<String> getSchemasWithTables(DataSourceConfig config) {
        List<String> schemas = new ArrayList<>();
        String sql = "SELECT OWNER FROM ("
                + " SELECT t.OWNER FROM ALL_TABLES t "
                + oracleOwnerRestriction("t.OWNER", false)
                + oracleMigrationRepositoryObjectFilter("t.OWNER", "t.TABLE_NAME")
                + " UNION "
                + " SELECT v.OWNER FROM ALL_VIEWS v "
                + oracleOwnerRestriction("v.OWNER", false)
                + oracleMigrationRepositoryObjectFilter("v.OWNER", "v.VIEW_NAME")
                + ") ORDER BY OWNER";
        try (Connection conn = getConnection(config);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(30);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String owner = rs.getString(1);
                    if (!isSystemSchema(owner)) {
                        schemas.add(owner);
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取可探查Oracle Schema列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取可探查Oracle Schema列表失败: " + e.getMessage(), e);
        }
        return schemas;
    }

    @Override
    public List<TableInfo> getTables(DataSourceConfig config) {
        if (useFastOracleTableScan()) {
            return getTablesFast(config);
        }

        List<TableInfo> tables = new ArrayList<>();
        String schema = config.getSchema() != null ? config.getSchema() : config.getUsername().toUpperCase();
        
        String sql = String.format(
            "SELECT t.TABLE_NAME, c.COMMENTS as TABLE_COMMENT, " +
            "'TABLE' as TABLE_TYPE, t.NUM_ROWS, t.LAST_ANALYZED, m.TIMESTAMP as DATA_UPDATE_TIME " +
            "FROM all_tables t " +
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

            // 获取每个表的大小信息
            for (TableInfo table : tables) {
                try {
                    Long size = getTableSize(config, table.getTableName());
                    table.setTotalSizeBytes(size);
                    table.setTotalSizeFormatted(formatBytes(size));
                } catch (Exception e) {
                    log.warn("获取表 {} 大小失败: {}", table.getTableName(), e.getMessage());
                }
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

    private boolean useFastOracleTableScan() {
        return true;
    }

    private String resolveSchema(DataSourceConfig config) {
        String schema = config.getSchema();
        if (schema == null || schema.trim().isEmpty()) {
            schema = config.getUsername();
        }
        return schema == null ? null : schema.trim().toUpperCase();
    }

    /**
     * An empty schema means "all objects this account can access", not just
     * objects owned by the login account.  Oracle's USER_* views intentionally
     * hide objects granted by another owner; ALL_* views are the correct
     * visibility boundary for metadata discovery.
     */
    private boolean hasExplicitSchema(DataSourceConfig config) {
        return config.getSchema() != null && !config.getSchema().trim().isEmpty();
    }

    private String oracleOwnerRestriction(String column, boolean explicitSchema) {
        String suppliedOwnerFilter = oracleSuppliedOwnerFilter(column);
        if (explicitSchema) {
            return "    WHERE " + column + " = ? " + suppliedOwnerFilter;
        }
        return "    WHERE " + column + " NOT IN (" + oracleSystemSchemaLiterals() + ") "
                + suppliedOwnerFilter;
    }

    /**
     * APEX installs a version-specific parsing schema, for example
     * {@code APEX_030200}, {@code APEX_220200}, or {@code APEX_240100}.
     * Those names cannot be exhaustively represented by a fixed allow/deny
     * list.  They expose thousands of Oracle product views through ALL_* and
     * must never be presented as business metadata.  FLOWS_ schemas are the
     * equivalent legacy APEX naming convention.
     *
     * <p>Apply the predicate even when a schema was configured explicitly so
     * an accidentally selected APEX parsing schema yields no registerable
     * objects instead of system views.</p>
     */
    private String oracleSuppliedOwnerFilter(String column) {
        return " AND NOT REGEXP_LIKE(" + column + ", '^(APEX|FLOWS)_[0-9]+$') ";
    }

    /**
     * Oracle SQL Developer / Migration Workbench can install a migration
     * repository into an ordinary user schema.  In the affected environment
     * that repository is owned by {@code SYY}; its {@code MD_*}, {@code MGV_*},
     * {@code MIGR_*}, and SQL Server 2005 capture ({@code SS2K5_*}) objects
     * plus the migration log tables describe database metadata rather than
     * business data. {@code SYY} itself must not be excluded because it may
     * also own real business tables.
     *
     * <p>The filter is intentionally scoped to this repository signature, not
     * to every owner or to generic MD/MGV object names in other schemas.  That
     * preserves granted cross-schema business tables in {@code ALL_TABLES}
     * and {@code ALL_VIEWS}.</p>
     */
    private String oracleMigrationRepositoryObjectFilter(String ownerColumn, String objectColumn) {
        return " AND NOT (UPPER(" + ownerColumn + ") = 'SYY' AND ("
                + "SUBSTR(UPPER(" + objectColumn + "), 1, 3) = 'MD_' "
                + "OR SUBSTR(UPPER(" + objectColumn + "), 1, 4) = 'MGV_' "
                + "OR SUBSTR(UPPER(" + objectColumn + "), 1, 5) = 'MIGR_' "
                + "OR SUBSTR(UPPER(" + objectColumn + "), 1, 6) = 'SS2K5_' "
                + "OR UPPER(" + objectColumn + ") IN ('MIGRLOG', 'MIGRATION_RESERVED_WORDS')"
                + ")) ";
    }

    private String oracleSystemSchemaLiterals() {
        return SYSTEM_SCHEMAS.stream()
                .sorted()
                .map(schema -> "'" + schema.replace("'", "''") + "'")
                .collect(java.util.stream.Collectors.joining(","));
    }

    /**
     * Keep same-owner table names backward compatible, and make foreign-owner
     * table names unambiguous and reusable by column/sample/NiFi operations.
     */
    private String externalTableName(DataSourceConfig config, String owner, String tableName) {
        if (owner == null || owner.isBlank() || tableName == null || tableName.isBlank()) {
            return tableName;
        }
        String configuredOwner = resolveSchema(config);
        return owner.equalsIgnoreCase(configuredOwner) ? tableName : owner + "." + tableName;
    }

    private OracleObjectReference resolveOracleObject(Connection conn, DataSourceConfig config, String requestedName) throws Exception {
        OracleObjectReference requested = parseOracleObjectReference(config, requestedName);
        String resolvedName = resolveOracleObjectName(conn, requested.owner(), requested.name());
        return new OracleObjectReference(requested.owner(), resolvedName);
    }

    private OracleObjectReference parseOracleObjectReference(DataSourceConfig config, String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            throw new IllegalArgumentException("Oracle对象名不能为空");
        }
        String value = requestedName.trim();
        int separator = value.indexOf('.');
        if (separator > 0 && separator < value.length() - 1
                && value.indexOf('.', separator + 1) < 0) {
            return new OracleObjectReference(
                    normalizeOracleIdentifier(value.substring(0, separator)),
                    value.substring(separator + 1).trim());
        }
        return new OracleObjectReference(resolveSchema(config), value);
    }

    private String normalizeOracleIdentifier(String value) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException("Oracle所有者不能为空");
        }
        return normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2
                ? normalized.substring(1, normalized.length() - 1).replace("\"\"", "\"")
                : normalized.toUpperCase(Locale.ROOT);
    }

    private record OracleObjectReference(String owner, String name) { }

    private boolean isSystemSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            return false;
        }
        String normalized = schema.trim().toUpperCase(Locale.ROOT);
        return SYSTEM_SCHEMAS.contains(normalized)
                || normalized.matches("^(APEX|FLOWS)_[0-9]+$");
    }

    private List<TableInfo> getTablesFast(DataSourceConfig config) {
        List<TableInfo> tables = new ArrayList<>();
        String schema = resolveSchema(config);
        boolean explicitSchema = hasExplicitSchema(config);
        if (explicitSchema && isSystemSchema(schema)) {
            return tables;
        }

        /*
         * 登记流程和详情页只需要当前 schema 的表级元数据。Oracle 大库如果使用
         * DatabaseMetaData 或逐表查询行数/容量，会在几千张表时明显超时。
         * 这里一次性批量读取表/视图、中文注释、字段数和统计行数。
         *
         * 注意不要依赖 ALL_SEGMENTS：普通业务账号常常没有该数据字典权限，
         * 会直接 ORA-00942，导致页面一直处于探查加载态。
         */
        String sql = "SELECT x.OWNER, x.TABLE_NAME, x.TABLE_COMMENT, x.TABLE_TYPE, x.NUM_ROWS, x.LAST_ANALYZED, " +
              "       COALESCE(col.COLUMN_COUNT, 0) AS COLUMN_COUNT " +
              "FROM ( " +
              "    SELECT t.OWNER, t.TABLE_NAME, c.COMMENTS AS TABLE_COMMENT, 'BASE TABLE' AS TABLE_TYPE, " +
              "           COALESCE(t.NUM_ROWS, 0) AS NUM_ROWS, t.LAST_ANALYZED " +
              "    FROM ALL_TABLES t " +
              "    LEFT JOIN ALL_TAB_COMMENTS c ON c.OWNER = t.OWNER AND c.TABLE_NAME = t.TABLE_NAME AND c.TABLE_TYPE = 'TABLE' " +
              oracleOwnerRestriction("t.OWNER", explicitSchema) +
              oracleMigrationRepositoryObjectFilter("t.OWNER", "t.TABLE_NAME") +
              "    UNION ALL " +
              "    SELECT v.OWNER, v.VIEW_NAME AS TABLE_NAME, c.COMMENTS AS TABLE_COMMENT, 'VIEW' AS TABLE_TYPE, " +
              "           0 AS NUM_ROWS, CAST(NULL AS DATE) AS LAST_ANALYZED " +
              "    FROM ALL_VIEWS v " +
              "    LEFT JOIN ALL_TAB_COMMENTS c ON c.OWNER = v.OWNER AND c.TABLE_NAME = v.VIEW_NAME AND c.TABLE_TYPE = 'VIEW' " +
              oracleOwnerRestriction("v.OWNER", explicitSchema) +
              oracleMigrationRepositoryObjectFilter("v.OWNER", "v.VIEW_NAME") +
              ") x " +
              "LEFT JOIN ( " +
              "    SELECT OWNER, TABLE_NAME, COUNT(*) AS COLUMN_COUNT " +
              "    FROM ALL_TAB_COLUMNS " +
              "    GROUP BY OWNER, TABLE_NAME " +
              ") col ON col.OWNER = x.OWNER AND col.TABLE_NAME = x.TABLE_NAME " +
              "ORDER BY x.OWNER, x.TABLE_NAME";

        try (Connection conn = getConnection(config);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(30);
            if (explicitSchema) {
                ps.setString(1, schema);
                ps.setString(2, schema);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TableInfo table = new TableInfo();
                    String owner = rs.getString("OWNER");
                    table.setTableName(externalTableName(config, owner, rs.getString("TABLE_NAME")));
                    table.setTableComment(rs.getString("TABLE_COMMENT"));
                    table.setTableType(rs.getString("TABLE_TYPE"));
                    table.setSchemaName(owner);
                    table.setRowCount(rs.getLong("NUM_ROWS"));
                    table.setUpdateTime(rs.getTimestamp("LAST_ANALYZED"));
                    table.setColumnCount(rs.getInt("COLUMN_COUNT"));

                    table.setTotalSizeBytes(0L);
                    table.setDataSizeBytes(0L);
                    table.setTotalSizeFormatted(formatBytes(0L));
                    table.setDataSizeFormatted(formatBytes(0L));

                    tables.add(table);
                }
            }
        } catch (Exception e) {
            log.error("鑾峰彇Oracle琛ㄥ垪琛ㄥけ璐? {}", e.getMessage(), e);
            throw new RuntimeException("鑾峰彇琛ㄥ垪琛ㄥけ璐? " + e.getMessage(), e);
        }

        return tables;
    }

    @Override
    public TablePageResult getTablesPage(DataSourceConfig config, Integer pageNo, Integer pageSize, String keyword) {
        return getTablesFastPage(config, pageNo, pageSize, keyword, List.of());
    }

    @Override
    public TablePageResult getTablesPageExcluding(
            DataSourceConfig config,
            Integer pageNo,
            Integer pageSize,
            String keyword,
            List<String> excludedTableNames
    ) {
        return getTablesFastPage(config, pageNo, pageSize, keyword, excludedTableNames);
    }

    private TablePageResult getTablesFastPage(
            DataSourceConfig config,
            Integer pageNo,
            Integer pageSize,
            String keyword,
            List<String> excludedTableNames
    ) {
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 500);
        int startRow = (safePageNo - 1) * safePageSize + 1;
        int endRow = safePageNo * safePageSize;

        String schema = resolveSchema(config);
        boolean explicitSchema = hasExplicitSchema(config);
        if (explicitSchema && isSystemSchema(schema)) {
            TablePageResult emptyPage = new TablePageResult();
            emptyPage.setPageNo(pageNo == null || pageNo < 1 ? 1 : pageNo);
            emptyPage.setPageSize(pageSize == null || pageSize < 1 ? 100 : Math.min(pageSize, 500));
            emptyPage.setTotal(0L);
            return emptyPage;
        }
        String normalizedKeyword = keyword == null || keyword.trim().isEmpty()
                ? null
                : "%" + keyword.trim().toLowerCase() + "%";
        List<String> exclusions = normalizeExcludedTableNames(excludedTableNames);

        /*
         * Oracle 大库分页探查：登记第二步只需要当前页表/视图，不能为了 UI 一次取几千张表。
         * 这里把总数和当前页都下推到数据库执行，前端每次只渲染 50 条，避免页面空白和超时。
         */
        String baseSql = oracleTableBaseSql(explicitSchema);
        String filterSql = " WHERE (? IS NULL OR LOWER(q.TABLE_NAME) LIKE ? OR LOWER(COALESCE(q.TABLE_COMMENT, '')) LIKE ?) "
                + oracleExclusionSql(exclusions.size());
        String countSql = "SELECT COUNT(1) FROM (" + baseSql + ") q " + filterSql;
        // Do not use OFFSET/FETCH or analytic pagination here.  The nested
        // ROWNUM form works on Oracle 11g and older Oracle-compatible
        // OceanBase clusters while keeping ORDER BY inside the inner query.
        String pageSql = "SELECT p.*, (SELECT COUNT(*) FROM ALL_TAB_COLUMNS col "
                + "WHERE col.OWNER = p.OWNER AND col.TABLE_NAME = p.TABLE_NAME) AS COLUMN_COUNT FROM ("
                + "SELECT page_inner.*, ROWNUM AS RN FROM ("
                + "SELECT q.* FROM (" + baseSql + ") q " + filterSql + " ORDER BY q.TABLE_NAME"
                + ") page_inner WHERE ROWNUM <= ?"
                + ") p WHERE p.RN >= ? ORDER BY p.RN";

        TablePageResult page = new TablePageResult();
        page.setPageNo(safePageNo);
        page.setPageSize(safePageSize);
        List<TableInfo> rows = new ArrayList<>();

        try (Connection conn = getConnection(config)) {
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setQueryTimeout(30);
                int idx = bindOracleBaseParams(ps, 1, explicitSchema, schema);
                idx = bindOracleKeywordParams(ps, idx, normalizedKeyword);
                bindOracleExclusions(ps, idx, exclusions);
                try (ResultSet rs = ps.executeQuery()) {
                    page.setTotal(rs.next() ? rs.getLong(1) : 0L);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(pageSql)) {
                ps.setQueryTimeout(30);
                int idx = bindOracleBaseParams(ps, 1, explicitSchema, schema);
                idx = bindOracleKeywordParams(ps, idx, normalizedKeyword);
                idx = bindOracleExclusions(ps, idx, exclusions);
                ps.setInt(idx++, endRow);
                ps.setInt(idx, startRow);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(readFastTableInfo(rs, config));
                    }
                }
            }

            page.setRows(rows);
            return page;
        } catch (Exception e) {
            log.error("Oracle分页获取表列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("Oracle分页获取表列表失败: " + e.getMessage(), e);
        }
    }

    private String oracleTableBaseSql(boolean explicitSchema) {
        return "SELECT t.OWNER, t.TABLE_NAME, c.COMMENTS AS TABLE_COMMENT, 'BASE TABLE' AS TABLE_TYPE, " +
                "           COALESCE(t.NUM_ROWS, 0) AS NUM_ROWS, t.LAST_ANALYZED " +
                "    FROM ALL_TABLES t " +
                "    LEFT JOIN ALL_TAB_COMMENTS c ON c.OWNER = t.OWNER AND c.TABLE_NAME = t.TABLE_NAME AND c.TABLE_TYPE = 'TABLE' " +
                oracleOwnerRestriction("t.OWNER", explicitSchema) +
                oracleMigrationRepositoryObjectFilter("t.OWNER", "t.TABLE_NAME") +
                "    UNION ALL " +
                "    SELECT v.OWNER, v.VIEW_NAME AS TABLE_NAME, c.COMMENTS AS TABLE_COMMENT, 'VIEW' AS TABLE_TYPE, " +
                "           0 AS NUM_ROWS, CAST(NULL AS DATE) AS LAST_ANALYZED " +
                "    FROM ALL_VIEWS v " +
                "    LEFT JOIN ALL_TAB_COMMENTS c ON c.OWNER = v.OWNER AND c.TABLE_NAME = v.VIEW_NAME AND c.TABLE_TYPE = 'VIEW' " +
                oracleOwnerRestriction("v.OWNER", explicitSchema) +
                oracleMigrationRepositoryObjectFilter("v.OWNER", "v.VIEW_NAME");
    }

    private int bindOracleBaseParams(PreparedStatement ps, int index, boolean explicitSchema, String schema) throws Exception {
        if (explicitSchema) {
            ps.setString(index++, schema);
            ps.setString(index++, schema);
        }
        return index;
    }

    private int bindOracleKeywordParams(PreparedStatement ps, int index, String normalizedKeyword) throws Exception {
        ps.setString(index++, normalizedKeyword);
        ps.setString(index++, normalizedKeyword == null ? "" : normalizedKeyword);
        ps.setString(index++, normalizedKeyword == null ? "" : normalizedKeyword);
        return index;
    }

    private List<String> normalizeExcludedTableNames(List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tableName : tableNames) {
            if (tableName != null && !tableName.isBlank()) {
                normalized.add(tableName.trim().toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(normalized);
    }

    private String oracleExclusionSql(int exclusionCount) {
        if (exclusionCount <= 0) {
            return "";
        }
        StringBuilder sql = new StringBuilder();
        int remaining = exclusionCount;
        while (remaining > 0) {
            int chunkSize = Math.min(remaining, 900);
            sql.append(" AND LOWER(q.OWNER || '.' || q.TABLE_NAME) NOT IN (");
            for (int i = 0; i < chunkSize; i++) {
                if (i > 0) {
                    sql.append(',');
                }
                sql.append('?');
            }
            sql.append(") AND LOWER(q.TABLE_NAME) NOT IN (");
            for (int i = 0; i < chunkSize; i++) {
                if (i > 0) {
                    sql.append(',');
                }
                sql.append('?');
            }
            sql.append(')');
            remaining -= chunkSize;
        }
        return sql.toString();
    }

    private int bindOracleExclusions(
            PreparedStatement ps,
            int index,
            List<String> exclusions
    ) throws Exception {
        for (int from = 0; from < exclusions.size(); from += 900) {
            int to = Math.min(from + 900, exclusions.size());
            for (int i = from; i < to; i++) {
                ps.setString(index++, exclusions.get(i));
            }
            for (int i = from; i < to; i++) {
                ps.setString(index++, exclusions.get(i));
            }
        }
        return index;
    }

    private TableInfo readFastTableInfo(ResultSet rs, DataSourceConfig config) throws Exception {
        TableInfo table = new TableInfo();
        String owner = rs.getString("OWNER");
        table.setTableName(externalTableName(config, owner, rs.getString("TABLE_NAME")));
        table.setTableComment(rs.getString("TABLE_COMMENT"));
        table.setTableType(rs.getString("TABLE_TYPE"));
        table.setSchemaName(owner);
        table.setRowCount(rs.getLong("NUM_ROWS"));
        table.setUpdateTime(rs.getTimestamp("LAST_ANALYZED"));
        table.setColumnCount(rs.getInt("COLUMN_COUNT"));
        table.setTotalSizeBytes(0L);
        table.setDataSizeBytes(0L);
        table.setTotalSizeFormatted(formatBytes(0L));
        table.setDataSizeFormatted(formatBytes(0L));
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
        String sql = "SELECT c.COLUMN_NAME, cc.COMMENTS AS COLUMN_COMMENT, c.DATA_TYPE, "
                + "c.DATA_LENGTH, c.DATA_PRECISION, c.DATA_SCALE, c.NULLABLE, "
                + "c.DATA_DEFAULT, c.COLUMN_ID "
                + "FROM all_tab_columns c "
                + "LEFT JOIN all_col_comments cc ON c.TABLE_NAME = cc.TABLE_NAME "
                + "AND c.COLUMN_NAME = cc.COLUMN_NAME AND c.OWNER = cc.OWNER "
                + "WHERE c.OWNER = ? AND c.TABLE_NAME = ? ORDER BY c.COLUMN_ID";

        try (Connection conn = getConnection(config)) {
            OracleObjectReference object = resolveOracleObject(conn, config, tableName);
            List<String> pkColumns = getPrimaryKeyColumns(conn, object.owner(), object.name());
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, object.owner());
                ps.setString(2, object.name());
                try (ResultSet rs = ps.executeQuery()) {
            
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
                            column.setColumnType(dataType);
                        }

                        columns.add(column);
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取字段列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取字段列表失败: " + e.getMessage(), e);
        }
        
        return columns;
    }

    private List<String> getPrimaryKeyColumns(Connection conn, String schema, String tableName) {
        List<String> pkColumns = new ArrayList<>();
        String sql = "SELECT cols.COLUMN_NAME FROM all_constraints cons "
                + "JOIN all_cons_columns cols ON cons.CONSTRAINT_NAME = cols.CONSTRAINT_NAME AND cons.OWNER = cols.OWNER "
                + "WHERE cons.CONSTRAINT_TYPE = 'P' AND cons.OWNER = ? AND cons.TABLE_NAME = ? "
                + "ORDER BY cols.POSITION";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pkColumns.add(rs.getString("COLUMN_NAME"));
                }
            }
        } catch (Exception e) {
            log.warn("获取主键列失败: {}", e.getMessage());
        }
        
        return pkColumns;
    }

    @Override
    public List<IndexInfo> getIndexes(DataSourceConfig config, String tableName) {
        List<IndexInfo> indexes = new ArrayList<>();
        String sql = "SELECT i.INDEX_NAME, i.UNIQUENESS, i.INDEX_TYPE, ic.COLUMN_NAME, ic.COLUMN_POSITION "
                + "FROM all_indexes i "
                + "JOIN all_ind_columns ic ON i.INDEX_NAME = ic.INDEX_NAME AND i.OWNER = ic.INDEX_OWNER "
                + "WHERE i.OWNER = ? AND i.TABLE_NAME = ? "
                + "ORDER BY i.INDEX_NAME, ic.COLUMN_POSITION";

        try (Connection conn = getConnection(config)) {
            OracleObjectReference object = resolveOracleObject(conn, config, tableName);
            Map<String, IndexInfo> indexMap = new LinkedHashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, object.owner());
                ps.setString(2, object.name());
                try (ResultSet rs = ps.executeQuery()) {
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
        try (Connection conn = getConnection(config)) {
            OracleObjectReference object = resolveOracleObject(conn, config, tableName);
            String sql = "SELECT COUNT(*) FROM " + qualifyOracleObject(object.owner(), object.name());
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
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
        String sql = "SELECT SUM(bytes) AS total_size FROM all_segments WHERE owner = ? AND segment_name = ?";

        try (Connection conn = getConnection(config)) {
            OracleObjectReference object = resolveOracleObject(conn, config, tableName);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, object.owner());
                ps.setString(2, object.name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("total_size");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取Oracle表大小失败，返回0: {}", e.getMessage());
        }
        
        return 0L;
    }

    @Override
    public Long getDatabaseSize(DataSourceConfig config) {
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

    @Override
    public SampleDataResult sampleData(SampleRequest request) {
        String where = buildWhereClause(request.getWhereClause());
        int sampleSize = request.getSampleSize();

        try (Connection conn = getConnection(request.getDataSource())) {
            OracleObjectReference object = resolveOracleObject(conn, request.getDataSource(), request.getTableName());
            String qualifiedTable = qualifyOracleObject(object.owner(), object.name());
            String columns = buildOracleColumnSelection(request.getColumns());
            String rowLimit = where.isBlank() ? " WHERE ROWNUM <= " : " AND ROWNUM <= ";

            String sql;
            switch (request.getSampleMethod()) {
                case RANDOM:
                    sql = "SELECT " + columns + " FROM " + qualifiedTable + " SAMPLE(10)"
                            + where + rowLimit + sampleSize;
                    break;
                case LAST:
                    sql = "SELECT * FROM (SELECT " + columns + " FROM " + qualifiedTable
                            + where + " ORDER BY ROWID DESC) WHERE ROWNUM <= " + sampleSize;
                    break;
                case FIRST:
                default:
                    sql = "SELECT " + columns + " FROM " + qualifiedTable + where + rowLimit + sampleSize;
                    break;
            }
            return executeSampleQuery(request.getDataSource(), request, sql);
        } catch (Exception e) {
            log.error("Oracle样例数据查询失败: {}", e.getMessage(), e);
            throw new RuntimeException("Oracle样例数据查询失败: " + e.getMessage(), e);
        }
    }

    private String resolveOracleObjectName(Connection conn, String schema, String requestedName) throws Exception {
        String sql = "SELECT OBJECT_NAME FROM ("
                + "SELECT OBJECT_NAME, CASE WHEN OBJECT_NAME = ? THEN 0 ELSE 1 END AS MATCH_ORDER "
                + "FROM ALL_OBJECTS WHERE OWNER = ? "
                + "AND OBJECT_TYPE IN ('TABLE', 'VIEW', 'MATERIALIZED VIEW') "
                + "AND (OBJECT_NAME = ? OR UPPER(OBJECT_NAME) = UPPER(?)) "
                + "ORDER BY MATCH_ORDER) WHERE ROWNUM = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestedName);
            ps.setString(2, schema);
            ps.setString(3, requestedName);
            ps.setString(4, requestedName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("OBJECT_NAME");
                }
            }
        }
        throw new IllegalArgumentException("Oracle对象不存在: " + schema + "." + requestedName);
    }

    private String qualifyOracleObject(String schema, String objectName) {
        return quoteOracleIdentifier(schema) + "." + quoteOracleIdentifier(objectName);
    }

    private String quoteOracleIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Oracle标识符不能为空");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String buildOracleColumnSelection(String[] columns) {
        if (columns == null || columns.length == 0) {
            return "*";
        }
        List<String> quotedColumns = new ArrayList<>();
        for (String column : columns) {
            quotedColumns.add(quoteOracleIdentifier(column));
        }
        return String.join(", ", quotedColumns);
    }
}
