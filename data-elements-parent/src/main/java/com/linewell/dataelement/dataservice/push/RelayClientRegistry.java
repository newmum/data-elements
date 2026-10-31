package com.linewell.dataelement.dataservice.push;

import com.linewell.dataelement.platform.tenant.infrastructure.datasource.UseControlDataSource;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Cross-tenant client routing. The external caller never chooses a tenant. */
@Service
@UseControlDataSource
public class RelayClientRegistry {
    private final JdbcTemplate jdbc;
    public RelayClientRegistry(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Local generation never calls the relay and never persists the plaintext. */
    @Transactional
    public LocalCredential prepareCredential(String clientId, String tenantId, String datasourceId, boolean rotate) {
        String client = requiredCode(clientId, "relay client");
        String tenant = requiredCode(tenantId, "tenantId");
        String datasource = requiredCode(datasourceId, "datasourceId");
        // The existing unique scope arbitrates simultaneous first visits across instances.
        var now = Timestamp.valueOf(LocalDateTime.now());
        String candidateId = NumericId.nextId();
        jdbc.update("""
                insert into data_push_relay_client_t(tid,relay_client_id,tenant_id,datasource_id,is_enable,created_time,updated_time,is_del)
                values (?,?,?,?,1,?,?,0)
                on duplicate key update tid=tid
                """, candidateId, client, tenant, datasource, now, now);
        var rows = jdbc.queryForList("""
                select tid,api_key_sha256,previous_api_key_sha256,is_enable,is_del from data_push_relay_client_t
                 where relay_client_id=? and tenant_id=? and datasource_id=? for update
                """, client, tenant, datasource);
        var row = rows.getFirst();
        String prior = text(row.get("api_key_sha256"));
        boolean inserted = candidateId.equals(text(row.get("tid")));
        if (!inserted && (!rotate || prior.isBlank())) {
            if (!"1".equals(text(row.get("is_enable"))) || !"0".equals(text(row.get("is_del"))))
                throw new IllegalStateException("当前数据源的推送凭据已停用，请联系管理员");
            return new LocalCredential(client, "", prior, text(row.get("previous_api_key_sha256")), false);
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String hash;
        try { hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("安全摘要组件不可用"); }
        String previous = prior;
        jdbc.update("update data_push_relay_client_t set api_key_sha256=?,previous_api_key_sha256=?,is_enable=1,is_del=0,updated_time=? where tid=?", hash, previous, now, row.get("tid"));
        return new LocalCredential(client, key, hash, previous, true);
    }

    public record LocalCredential(String clientId, String apiKey, String hash, String previousHash, boolean revealed) {
        @Override public String toString() { return "LocalCredential[clientId=" + clientId + ",revealed=" + revealed + "]"; }
    }

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
