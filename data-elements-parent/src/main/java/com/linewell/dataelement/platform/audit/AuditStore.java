package com.linewell.dataelement.platform.audit;

import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Explicit physical datasource selection. Tenant identifiers come only from trusted framework context. */
@Component
public class AuditStore {
    private final DataSource control;
    private final TenantDataSourceRegistry registry;
    private final com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties properties;
    public AuditStore(@Qualifier("controlDataSource") DataSource control, TenantDataSourceRegistry registry,com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties properties) {
        this.control=control; this.registry=registry;this.properties=properties;
    }
    public DataSource dataSource(String tenantId) {
        if(tenantId==null)return control;
        if(!properties.isEnabled()||!properties.getTenantBindings().containsKey(tenantId))throw new SecurityException("日志租户未绑定真实业务数据库");
        return registry.dataSourceForTenant(tenantId);
    }
    public JdbcTemplate jdbc(String tenantId) { return new JdbcTemplate(dataSource(tenantId)); }
    public NamedParameterJdbcTemplate named(String tenantId) { return new NamedParameterJdbcTemplate(dataSource(tenantId)); }
    public void insert(String tenantId, String table, Map<String,Object> values) {
        // Table and column names are internal constants, never browser input.
        if (!table.matches("(?:log_[a-z_]+_t|iam_audit_event_t)")) throw new IllegalArgumentException("日志存储类型无效");
        String columns=String.join(",",values.keySet());
        String params=String.join(",",values.keySet().stream().map(k->":"+k).toList());
        named(tenantId).update("insert into "+table+" ("+columns+") values ("+params+")",values);
    }
    public static Map<String,Object> lower(Map<String,Object> row) {
        var out = new LinkedHashMap<String,Object>();
        row.forEach((key,value)->out.put(key.toLowerCase(Locale.ROOT),value)); return out;
    }
}
