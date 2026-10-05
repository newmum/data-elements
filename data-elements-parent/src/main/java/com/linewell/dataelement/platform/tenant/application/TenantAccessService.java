package com.linewell.dataelement.platform.tenant.application;

import com.linewell.dataelement.platform.persistence.id.NumericId;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.entity.SymTenantT;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.entity.RmUserTenantRelaT;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.mapper.SymTenantTMapper;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.mapper.RmUserTenantRelaTMapper;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.model.TenantMembership;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.UseControlDataSource;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

@Service
@UseControlDataSource
public class TenantAccessService {

    private final SymTenantTMapper tenantMapper;
    private final RmUserTenantRelaTMapper relationMapper;
    private final TenantProperties properties;
    @Value("${idaas.legacy-control-accounts.enabled:false}")
    private boolean legacyControlAccountsEnabled;

    private void requireLegacyCompatibility() {
        if (!legacyControlAccountsEnabled) throw new TenantAccessException("TENANT-ACCOUNT-SOURCE-RETIRED", "共享成员关系已退役，请使用所属应用的本地账号登录");
    }

    public TenantAccessService(
            SymTenantTMapper tenantMapper,
            RmUserTenantRelaTMapper relationMapper,
            TenantProperties properties
    ) {
        this.tenantMapper = tenantMapper;
        this.relationMapper = relationMapper;
        this.properties = properties;
    }

    @Transactional
    public TenantIdentity resolve(String loginId) {
        requireLegacyCompatibility();
        if (!properties.isEnabled()) {
            return new TenantIdentity(properties.getDefaultTenantId());
        }
        List<TenantMembership> memberships = relationMapper.selectAvailable(loginId);
        if (memberships.isEmpty() && properties.isCompatibilityAutoBind()) {
            autoBindDefault(loginId);
            memberships = relationMapper.selectCompatible(loginId);
        }
        if (memberships.isEmpty()) {
            throw new TenantAccessException("TENANT-NOT-ASSIGNED", "当前账号未分配可用租户");
        }
        TenantMembership membership = memberships.get(0);
        return new TenantIdentity(membership.getTenantId());
    }

    public TenantIdentity requireMembership(String loginId, String tenantId) {
        requireLegacyCompatibility();
        TenantMembership membership = relationMapper.selectMembership(loginId, tenantId);
        if (membership == null) {
            throw new TenantAccessException("TENANT-FORBIDDEN", "当前账号无权访问所选租户");
        }
        return new TenantIdentity(tenantId);
    }

    public TenantIdentity requireActiveTenant(String tenantId) {
        String activeTenantId = tenantMapper.selectActiveId(tenantId);
        if (activeTenantId == null) {
            throw new TenantAccessException(
                    "TENANT-NOT-AVAILABLE",
                    "Requested tenant is not available"
            );
        }
        return new TenantIdentity(activeTenantId);
    }

    public List<Map<String, Object>> availableTenants(String loginId) {
        requireLegacyCompatibility();
        return relationMapper.selectTenantOptions(loginId).stream()
                .map(this::membershipMap)
                .toList();
    }

    public List<Map<String, Object>> loginOptions() {
        return tenantMapper.selectLoginOptions().stream()
                .map(this::tenantMap)
                .toList();
    }

    /**
     * Assign a user only within the tenant carried by the authenticated request.
     * Keep this normal tenant-bound operation independent of role-based administrator gates.
     */
    @Transactional
    public void assignUserInCurrentTenant(String userId, String tenantId, String operator) {
        requireLegacyCompatibility();
        String currentTenantId = TenantContext.requireTenantId();
        if (!currentTenantId.equals(tenantId)) {
            throw new TenantAccessException("TENANT-FORBIDDEN", "禁止跨租户分配用户");
        }
        LocalDateTime now = LocalDateTime.now();
        String relationId = relationMapper.selectRelationId(userId, tenantId);
        RmUserTenantRelaT relation = assignment(
                relationId, userId, tenantId, false, operator, now
        );
        if (relationId == null) {
            relation.setTid(NumericId.nextId());
            relation.setCreatedBy(operator);
            relation.setCreatedTime(now);
            relation.setIsDel(0);
            relationMapper.insert(relation);
        } else {
            relationMapper.updateAssignment(relation);
        }
    }

    private void autoBindDefault(String loginId) {
        try {
            LocalDateTime now = LocalDateTime.now();
            RmUserTenantRelaT relation = assignment(
                    NumericId.nextId(), loginId, properties.getDefaultTenantId(),
                    true, loginId, now
            );
            relation.setCreatedBy(loginId);
            relation.setCreatedTime(now);
            relation.setIsDel(0);
            relationMapper.insert(relation);
        } catch (DuplicateKeyException ignored) {
            // Another request completed the compatibility binding.
        }
    }

    public Map<String, Object> currentTenant() {
        String tenantId = TenantContext.requireTenantId();
        SymTenantT tenant = tenantMapper.selectCurrent(tenantId);
        Map<String, Object> result = tenant == null
                ? new LinkedHashMap<>()
                : tenantMap(tenant);
        return result;
    }

    private RmUserTenantRelaT assignment(
            String relationId,
            String userId,
            String tenantId,
            boolean defaultTenant,
            String operator,
            LocalDateTime now
    ) {
        RmUserTenantRelaT relation = new RmUserTenantRelaT();
        relation.setTid(relationId);
        relation.setUserId(userId);
        relation.setTenantId(tenantId);
        relation.setIsDefault(defaultTenant ? 1 : 0);
        relation.setStatus(1);
        relation.setUpdatedBy(operator);
        relation.setUpdatedTime(now);
        return relation;
    }

    private Map<String, Object> membershipMap(TenantMembership membership) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tid", membership.getTenantId());
        result.put("code", membership.getCode());
        result.put("name", membership.getName());
        result.put("status", membership.getStatus());
        result.put("init_flag", membership.getInitFlag());
        result.put("is_default", membership.getIsDefault());
        return result;
    }

    private Map<String, Object> tenantMap(SymTenantT tenant) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tid", tenant.getTid());
        result.put("code", tenant.getCode());
        result.put("name", tenant.getName());
        if (tenant.getStatus() != null) {
            result.put("status", tenant.getStatus());
        }
        if (tenant.getInitFlag() != null) {
            result.put("init_flag", tenant.getInitFlag());
        }
        if (tenant.getIsolationMode() != null) {
            result.put("isolation_mode", tenant.getIsolationMode());
        }
        return result;
    }

    public record TenantIdentity(String tenantId) {
    }
}
