package com.linewell.dataelement.feature.approval.application;

import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserLookupMapper;
import com.linewell.dataelement.feature.approval.infrastructure.persistence.mapper.ApprovalDelegationMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ApprovalPrincipalService {

    private final IdentityUserLookupMapper identityMapper;
    private final ApprovalDelegationMapper delegationMapper;

    public ApprovalPrincipalService(
            IdentityUserLookupMapper identityMapper,
            ApprovalDelegationMapper delegationMapper
    ) {
        this.identityMapper = identityMapper;
        this.delegationMapper = delegationMapper;
    }

    public List<String> permissionKeys(String userId, String appId) {
        String tenantId = TenantContext.requireTenantId();
        List<String> keys = new ArrayList<>();
        LinkedHashSet<String> principalIds = new LinkedHashSet<>();
        principalIds.add(userId);
        principalIds.addAll(delegationMapper.selectActiveDelegatorIds(tenantId, userId, LocalDateTime.now()));
        principalIds.forEach(id -> add(keys, id));
        List<String> ids = new ArrayList<>(principalIds);
        // Delegations may be numerous; each query covers a bounded group, never one query per delegator.
        for (int offset = 0; offset < ids.size(); offset += 500) {
            List<String> group = ids.subList(offset, Math.min(offset + 500, ids.size()));
            identityMapper.selectRoleCodesForUsers(group, tenantId, appId)
                    .forEach(roleCode -> add(keys, "role:" + roleCode));
            identityMapper.selectOrganizationIdsForUsers(group, tenantId)
                    .forEach(orgId -> add(keys, "org:" + orgId));
        }
        return keys;
    }

    public Map<String, Object> principal(String userId, String appId) {
        String tenantId = TenantContext.requireTenantId();
        List<String> roleCodes = identityMapper.selectRoleCodes(userId, tenantId, appId);
        List<String> organizationIds = identityMapper.selectOrganizationIds(userId, tenantId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("tenantId", tenantId);
        result.put("roleCodes", roleCodes);
        result.put("organizationIds", organizationIds);
        result.put("permissionKeys", permissionKeys(userId, appId));
        return result;
    }

    private void add(List<String> values, String value) {
        if (value != null && !value.isBlank() && !values.contains(value)) {
            values.add(value);
        }
    }
}
