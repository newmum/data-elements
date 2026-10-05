package com.linewell.dataelement.feature.identity.application;

import com.linewell.dataelement.feature.identity.domain.IdentityUser;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserLookupMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IdentityUserLookupServiceImpl implements IdentityUserLookupService {

    private final IdentityUserLookupMapper mapper;
    private final TenantIdentityQueryService controlIdentityService;

    public IdentityUserLookupServiceImpl(
            IdentityUserLookupMapper mapper,
            TenantIdentityQueryService controlIdentityService
    ) {
        this.mapper = mapper;
        this.controlIdentityService = controlIdentityService;
    }

    @Override
    public List<IdentityUser> listUsersByOrgIds(List<String> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Collections.emptyList();
        }
        return users(mapper.selectUserIdsByOrgIds(orgIds, TenantContext.requireTenantId()));
    }

    @Override
    public List<IdentityUser> listUsersByRoleCode(String appId, String roleCode) {
        if (appId == null || appId.isBlank() || roleCode == null || roleCode.isBlank()) {
            return Collections.emptyList();
        }
        return users(mapper.selectUserIdsByRoleCode(
                appId, roleCode, TenantContext.requireTenantId()
        ));
    }

    @Override
    public IdentityUser getUserById(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        String tenantId = TenantContext.requireTenantId();
        if (!controlIdentityService.hasMembership(tenantId, userId)) {
            return null;
        }
        return users(List.of(userId)).stream().findFirst().orElse(null);
    }

    private List<IdentityUser> users(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, String> names = controlIdentityService.names(ids);
        return ids.stream().distinct()
                .filter(names::containsKey)
                .map(id -> user(id, names.get(id)))
                .toList();
    }

    private IdentityUser user(String id, String name) {
        IdentityUser result = new IdentityUser();
        result.setId(id);
        result.setUserId(id);
        result.setRealName(name);
        return result;
    }
}
