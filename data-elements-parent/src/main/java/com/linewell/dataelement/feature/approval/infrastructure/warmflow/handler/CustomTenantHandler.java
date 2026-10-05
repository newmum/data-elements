package com.linewell.dataelement.feature.approval.infrastructure.warmflow.handler;

import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.dromara.warm.flow.core.handler.TenantHandler;

/**
 * Connects Warm-Flow to the platform tenant context.
 */
public class CustomTenantHandler implements TenantHandler {

    @Override
    public String getTenantId() {
        String tenantId = TenantContext.getTenantId();
        return tenantId == null ? TenantProperties.DEFAULT_TENANT_ID : tenantId;
    }
}
