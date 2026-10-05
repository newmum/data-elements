package com.linewell.dataelement.feature.identity.api;

import com.linewell.dataelement.feature.identity.application.IdentityAccountAdminService;
import com.linewell.dataelement.feature.identity.application.IdentityTenantRelationService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

@Component
@MagicModule("identityAdmin")
public class IdentityAccountAdminMagicModule {

    private final IdentityAccountAdminService service;
    private final IdentityTenantRelationService tenantRelationService;

    public IdentityAccountAdminMagicModule(
            IdentityAccountAdminService service,
            IdentityTenantRelationService tenantRelationService
    ) {
        this.service = service;
        this.tenantRelationService = tenantRelationService;
    }

    @Comment("Create or update a control-plane user and assign the current tenant")
    public Map<String, Object> save(
            Map<String, Object> body,
            String tenantId,
            String operator
    ) {
        return service.save(body, tenantId, operator);
    }

    @Comment("Page control-plane users available to the current tenant")
    public Map<String, Object> page(Map<String, Object> body, String tenantId) {
        return service.page(body, tenantId);
    }

    @Comment("List control-plane users available to the current tenant")
    public List<Map<String, Object>> list(String tenantId, String userName) {
        return service.list(tenantId, userName);
    }

    @Comment("Replace the user's organization relation inside the current tenant")
    public void saveOrganization(String userId, String organizationId, String tenantId) {
        tenantRelationService.saveOrganization(userId, organizationId, tenantId);
    }
}
