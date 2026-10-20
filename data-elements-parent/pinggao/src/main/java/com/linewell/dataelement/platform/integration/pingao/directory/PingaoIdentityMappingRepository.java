package com.linewell.dataelement.platform.integration.pingao.directory;

import com.linewell.dataelement.platform.persistence.id.NumericId;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.UseControlDataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@UseControlDataSource
public class PingaoIdentityMappingRepository {
    private final JdbcTemplate jdbc;
    public PingaoIdentityMappingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String resolveUser(String tenant, String externalTenant, String externalId, Instant observedAfter) {
        return resolve(tenant, externalTenant, "USER", externalId, observedAfter,
                "外部账号尚未确认映射、已停用或目录状态已过期");
    }

    public String resolveOrganization(String tenant, String externalTenant, String externalId, Instant observedAfter) {
        return resolve(tenant, externalTenant, "ORG", externalId, observedAfter,
                "外部机构尚未确认映射、已停用或目录状态已过期");
    }

    private String resolve(String tenant, String externalTenant, String type, String externalId, Instant observedAfter,
                           String message) {
        List<String> ids = jdbc.queryForList("""
                select local_id from pingao_identity_map_t
                 where tenant_id=? and external_tenant_code=? and entity_type=?
                   and external_id=? and external_enabled=1 and last_observed_at>=?
                """, String.class, tenant, externalTenant, type, externalId, Timestamp.from(observedAfter));
        if (ids.size() != 1) throw new IllegalStateException(message);
        return ids.getFirst();
    }

    @Transactional
    public void confirmUser(String tenant, String externalTenant, PingaoDirectorySnapshot.User external,
                            String localId, String operator, Instant observedAt) {
        if (!external.enabled() || external.deleted()) throw new IllegalStateException("外部账号已停用");
        List<String> existing = jdbc.queryForList("""
                select local_id from pingao_identity_map_t
                 where tenant_id=? and external_tenant_code=? and entity_type='USER' and external_id=?
                """, String.class, tenant, externalTenant, external.id());
        if (!existing.isEmpty() && !existing.getFirst().equals(localId)) {
            throw new IllegalStateException("外部账号已绑定其他本地账号，禁止覆盖");
        }
        if (existing.isEmpty()) {
            jdbc.update("""
                    insert into pingao_identity_map_t
                      (tenant_id,external_tenant_code,entity_type,external_id,local_id,external_enabled,
                       confirmed_by,confirmed_at,last_observed_at)
                    values (?,?,'USER',?,?,1,?,?,?)
                    """, tenant, externalTenant, external.id(), localId, operator,
                    Timestamp.from(Instant.now()), Timestamp.from(observedAt));
        } else {
            jdbc.update("""
                    update pingao_identity_map_t set external_enabled=1,last_observed_at=?
                     where tenant_id=? and external_tenant_code=? and entity_type='USER' and external_id=?
                    """, Timestamp.from(observedAt), tenant, externalTenant, external.id());
        }
        jdbc.update("""
                insert into pingao_identity_audit_t (id,tenant_id,operator_id,operation,external_id,local_id,created_at)
                values (?,?,?,'CONFIRM_USER',?,?,?)
                """, NumericId.nextId(), tenant, operator, external.id(), localId, Timestamp.from(Instant.now()));
    }

    @Transactional
    public void confirmOrganization(String tenant, String externalTenant, PingaoDirectorySnapshot.Organization external,
                                    String localId, String operator, Instant observedAt) {
        if (!external.enabled() || external.deleted()) throw new IllegalStateException("外部机构已停用");
        List<String> existing = jdbc.queryForList("""
                select local_id from pingao_identity_map_t
                 where tenant_id=? and external_tenant_code=? and entity_type='ORG' and external_id=?
                """, String.class, tenant, externalTenant, external.id());
        if (!existing.isEmpty() && !existing.getFirst().equals(localId)) {
            throw new IllegalStateException("外部机构已绑定其他本地机构，禁止覆盖");
        }
        if (existing.isEmpty()) {
            jdbc.update("""
                    insert into pingao_identity_map_t
                      (tenant_id,external_tenant_code,entity_type,external_id,local_id,external_code,external_enabled,
                       confirmed_by,confirmed_at,last_observed_at)
                    values (?,?,'ORG',?,?,?,1,?,?,?)
                    """, tenant, externalTenant, external.id(), localId, external.code(), operator,
                    Timestamp.from(Instant.now()), Timestamp.from(observedAt));
        } else {
            jdbc.update("""
                    update pingao_identity_map_t set external_code=?,external_enabled=1,last_observed_at=?
                     where tenant_id=? and external_tenant_code=? and entity_type='ORG' and external_id=?
                    """, external.code(), Timestamp.from(observedAt), tenant, externalTenant, external.id());
        }
        jdbc.update("""
                insert into pingao_identity_audit_t (id,tenant_id,operator_id,operation,external_id,local_id,created_at)
                values (?,?,?,'CONFIRM_ORG',?,?,?)
                """, NumericId.nextId(), tenant, operator, external.id(), localId, Timestamp.from(Instant.now()));
    }

    /** Refreshes status only for an already administrator-confirmed mapping. */
    public void observeUser(String tenant, String externalTenant, PingaoDirectorySnapshot.User external, Instant observedAt) {
        observe(tenant, externalTenant, "USER", external.id(), external.loginId(),
                external.enabled() && !external.deleted(), observedAt);
    }

    /** Refreshes status only for an already administrator-confirmed mapping. */
    public void observeOrganization(String tenant, String externalTenant, PingaoDirectorySnapshot.Organization external,
                                    Instant observedAt) {
        observe(tenant, externalTenant, "ORG", external.id(), external.code(),
                external.enabled() && !external.deleted(), observedAt);
    }

    private void observe(String tenant, String externalTenant, String type, String externalId, String externalCode,
                         boolean enabled, Instant observedAt) {
        jdbc.update("""
                update pingao_identity_map_t
                   set external_code=?, external_enabled=?, last_observed_at=?
                 where tenant_id=? and external_tenant_code=? and entity_type=? and external_id=?
                """, externalCode.isBlank() ? null : externalCode, enabled ? 1 : 0, Timestamp.from(observedAt),
                tenant, externalTenant, type, externalId);
    }

    public List<java.util.Map<String, Object>> mappings(String tenant, String externalTenant) {
        return jdbc.queryForList("""
                select entity_type,external_id,local_id,external_code,external_enabled,confirmed_by,confirmed_at,last_observed_at
                  from pingao_identity_map_t where tenant_id=? and external_tenant_code=?
                 order by entity_type,external_id limit 1000
                """, tenant, externalTenant);
    }

    public boolean activeAccount(String tenant, String userId, int activeStatus) {
        Integer count = jdbc.queryForObject("""
                select count(*) from rm_user_t u where u.id=? and u.deleted=0 and u.status=?
                  and exists (select 1 from rm_user_tenant_rela_t r where r.user_id=u.id
                    and r.tenant_id=? and r.status=1 and r.is_del=0)
                """, Integer.class, userId, activeStatus, tenant);
        return count != null && count == 1;
    }
}
