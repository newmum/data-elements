package com.linewell.dataelement.dataassets.runtime;

import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** Cross-database persistence primitives used by metadata Magic APIs. */
@Component
@MagicModule("metadataAsset")
public class MetadataAssetPersistenceModule {

    private static final String TABLE_NAME = "db_table_column_t";
    private static final List<String> COLUMN_NAMES = List.of(
            "tenant_id",
            "charset",
            "created_time",
            "updated_time",
            "source_table_column_id",
            "nullable",
            "auto_increment",
            "indexed",
            "column_name",
            "length",
            "scale",
            "is_unique",
            "default_value",
            "table_id",
            "primary_key",
            "tid",
            "precision_length",
            "ordinal_position",
            "extra",
            "column_comment",
            "data_type",
            "is_del",
            "collation",
            "column_type");

    private final DataSource dataSource;

    public MetadataAssetPersistenceModule(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Comment("按当前租户全部有效表的登记状态同步数据源；finish=true 表示结束登记")
    public Map<String, Object> refreshRegistrationStatus(String datasourceId, boolean finish) {
        if (TenantContext.isIgnored()) throw new BizException(401, "无法识别当前租户");
        return DatasourceRegistrationStatus.refresh(dataSource, TenantContext.getTenantId(), datasourceId, finish);
    }

    @Comment("只读预检结束登记后的数据源状态")
    public Map<String, Object> reviewRegistrationStatus(String datasourceId) {
        if (TenantContext.isIgnored()) throw new BizException(401, "无法识别当前租户");
        return DatasourceRegistrationStatus.evaluate(dataSource, TenantContext.getTenantId(), datasourceId, true, true);
    }

    @Comment("批量同步当前租户数据源状态；最多100个ID，集合外一次聚合查询")
    public List<Map<String, Object>> refreshRegistrationStatuses(List<String> datasourceIds) {
        if (TenantContext.isIgnored()) throw new BizException(401, "无法识别当前租户");
        return DatasourceRegistrationStatus.evaluateMany(dataSource, TenantContext.getTenantId(), datasourceIds, false, false);
    }

    @Comment("跨数据库批量保存数据表字段元数据")
    public int batchInsertTableColumns(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        List<Map<String, Object>> scopedRows = scopeTenants(rows);
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int affected = executeBatch(connection, scopedRows);
                connection.commit();
                return affected;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "批量保存数据表字段元数据失败: " + rootMessage(e));
        }
    }

    private int executeBatch(Connection connection, List<Map<String, Object>> rows) throws Exception {
        String quote = identifierQuote(connection);
        List<String> quotedColumns = new ArrayList<>(COLUMN_NAMES.size());
        for (String column : COLUMN_NAMES) {
            quotedColumns.add(quote + column + quote);
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(COLUMN_NAMES.size(), "?"));
        String sql = "INSERT INTO " + TABLE_NAME + " (" + String.join(", ", quotedColumns)
                + ") VALUES (" + placeholders + ")";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map<String, Object> row : rows) {
                for (int index = 0; index < COLUMN_NAMES.size(); index++) {
                    statement.setObject(index + 1, normalizeValue(row.get(COLUMN_NAMES.get(index))));
                }
                statement.addBatch();
            }
            int affected = 0;
            for (int result : statement.executeBatch()) {
                if (result > 0) {
                    affected += result;
                } else if (result == Statement.SUCCESS_NO_INFO) {
                    affected++;
                }
            }
            return affected;
        }
    }

    private List<Map<String, Object>> scopeTenants(List<Map<String, Object>> rows) {
        String currentTenant = TenantContext.getTenantId();
        if (currentTenant == null || currentTenant.isBlank() || TenantContext.isIgnored()) {
            throw new BizException(401, "无法识别当前租户");
        }
        List<Map<String, Object>> scopedRows = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> scopedRow = new LinkedHashMap<>(row);
            scopedRow.put("tenant_id", currentTenant);
            scopedRows.add(scopedRow);
        }
        return scopedRows;
    }

    private Object normalizeValue(Object value) {
        return value instanceof Boolean bool ? (bool ? 1 : 0) : value;
    }

    private String identifierQuote(Connection connection) throws Exception {
        String quote = connection.getMetaData().getIdentifierQuoteString();
        return quote == null ? "" : quote.trim();
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? error.getClass().getSimpleName() : current.getMessage();
    }
}
