package com.linewell.dataelement.platform.persistence;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Quotes fixed platform identifiers for the current tenant's case-sensitive DM schema. */
public final class TenantIdentifierSql {
    private static final Pattern IDENTIFIERS=Pattern.compile("\\b(db_datasource_t|db_table_t|db_table_column_t|data_access_agg_task_t|data_access_field_mapping|tid|tenant_id|is_del|asset_status|datasource_id|table_id|table_name|table_name_cn|column_name|data_type|ordinal_position|pipeline_id|task_id|target_field|source_field|target_data_type|func_code|func_value|func_enable|dict_enable|dict_code|dict_value|sort_no|created_time|updated_time)\\b");
    private final JdbcTemplate jdbc;
    private final Map<String,Boolean> dialects=new ConcurrentHashMap<>();
    public TenantIdentifierSql(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public String adapt(String sql){
        String tenant=TenantContext.requireTenantId();
        boolean dm=dialects.computeIfAbsent(tenant,key->Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection->connection.getMetaData().getURL().toLowerCase(Locale.ROOT).startsWith("jdbc:dm:"))));
        return dm?IDENTIFIERS.matcher(sql).replaceAll("\"$1\""):sql;
    }
}
