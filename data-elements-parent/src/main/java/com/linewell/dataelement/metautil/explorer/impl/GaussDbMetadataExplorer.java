package com.linewell.dataelement.metautil.explorer.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import com.linewell.dataelement.metautil.explorer.AbstractMetadataExplorer;
import com.linewell.dataelement.metautil.model.dto.*;
import lombok.extern.slf4j.Slf4j;

/**
 * GaussDB 元数据探查器
 *
 * GaussDB（openGauss / GaussDB(for PostgreSQL)）通常兼容 PostgreSQL 协议与系统视图，
 * 因此直接复用 PostgreSQL 的探查实现，并在数据库类型/版本展示上做适配。
 */
@Slf4j
public class GaussDbMetadataExplorer extends PostgreSqlMetadataExplorer {

    @Override
    public DatabaseInfo getDatabaseInfo(DataSourceConfig config) {
        DatabaseInfo info = super.getDatabaseInfo(config);
        info.setDatabaseType("GaussDB");
        String schema = config.getSchema() != null ? config.getSchema() : "public";

        // 尝试拿到更“像 GaussDB”的 version() 输出
        try (Connection conn = getConnection(config);
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT version()")) {
                if (rs.next()) {
                    info.setVersion(rs.getString(1));
                }
            }

            // openGauss/GaussDB 在部分版本上过程对象可能主要表现为 FUNCTION，做兼容兜底
            if (info.getProcedureCount() == null || info.getProcedureCount() == 0) {
                String fallbackProcedureSql = String.format(
                    "SELECT COUNT(*) FROM information_schema.routines " +
                        "WHERE routine_schema = '%s' AND routine_type IN ('PROCEDURE', 'FUNCTION')",
                    schema
                );
                try (ResultSet rs = stmt.executeQuery(fallbackProcedureSql)) {
                    if (rs.next()) {
                        info.setProcedureCount(rs.getInt(1));
                    }
                } catch (Exception e) {
                    log.warn("获取GaussDB存储过程数量失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("获取GaussDB版本信息失败（忽略）: {}", e.getMessage());
        }

        return info;
    }
}

