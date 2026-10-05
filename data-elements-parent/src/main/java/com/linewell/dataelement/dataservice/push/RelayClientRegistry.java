package com.linewell.dataelement.dataservice.push;

import com.linewell.dataelement.platform.tenant.infrastructure.datasource.UseControlDataSource;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Cross-tenant client routing. The external caller never chooses a tenant. */
@Service
@UseControlDataSource
public class RelayClientRegistry {
    private final JdbcTemplate jdbc;
    public RelayClientRegistry(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ClientScope resolve(String clientId, String datasourceId) {
        String client = requiredCode(clientId, "relay client");
        String datasource = requiredCode(datasourceId, "datasourceId");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select tenant_id, datasource_id
                  from data_push_relay_client_t
                 where relay_client_id = ? and is_enable = 1 and is_del = 0
                   and (datasource_id is null or datasource_id = ?)
                 order by case when datasource_id = ? then 0 else 1 end
                """, client, datasource, datasource);
        if (rows.isEmpty()) throw new IllegalArgumentException("该调用方未绑定推送数据源");
        Map<String, Object> row = rows.getFirst();
        return new ClientScope(String.valueOf(row.get("tenant_id")), text(row.get("datasource_id")));
    }

    /** Bind a source-specific relay caller.  Its datasource scope covers every
     * registered and published table beneath that source, and nothing outside it. */
    public void bindDatasource(String clientId, String tenantId, String datasourceId) {
        String client = requiredCode(clientId, "relay client");
        String tenant = requiredCode(tenantId, "tenantId");
        String datasource = requiredCode(datasourceId, "datasourceId");
        int changed = jdbc.update("""
                update data_push_relay_client_t set is_enable=1, is_del=0, updated_time=?
                 where relay_client_id=? and tenant_id=? and datasource_id=?
                """, Timestamp.valueOf(LocalDateTime.now()), client, tenant, datasource);
        if (changed == 0) jdbc.update("""
                insert into data_push_relay_client_t(tid,relay_client_id,tenant_id,datasource_id,is_enable,created_time,updated_time,is_del)
                values (?,?,?,?,1,?,?,0)
                """, NumericId.nextId(), client, tenant, datasource, Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()));
    }

    private String requiredCode(String value, String field) {
        String text = text(value);
        if (!text.matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException(field + " 格式不正确");
        return text;
    }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    public record ClientScope(String tenantId, String datasourceId) { }
}
