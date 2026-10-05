package com.linewell.dataelement.platform.tenant.api;

import java.util.Map;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.service.MagicAPIService;

/** Applies the editable Magic account decision to actual tenant requests. No business SQL here. */
@Component
public class IdentityPolicyInvoker {
    private final ObjectProvider<MagicAPIService> services;
    public IdentityPolicyInvoker(ObjectProvider<MagicAPIService> services) { this.services=services; }
    public boolean localAccountAllowed(String tenantId,String userId) {
        if (tenantId == null || !tenantId.equals(TenantContext.getTenantId()) || userId == null) {
            return false;
        }
        Map<String,Object> result=services.getObject().invoke("/idaas/receiver-account-check",Map.of("userId",userId));
        return result!=null && Boolean.TRUE.equals(result.get("allowed"));
    }
}
