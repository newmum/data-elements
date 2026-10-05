package com.linewell.dataelement.feature.approval.infrastructure.warmflow.handler;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.approval.application.ApprovalPrincipalService;
import java.util.List;
import org.dromara.warm.flow.core.handler.PermissionHandler;

/**
 * Resolves handlers and permission keys from the authenticated local identity.
 */
public class CustomPermissionHandler implements PermissionHandler {

    private final ApprovalPrincipalService principalService;

    public CustomPermissionHandler(ApprovalPrincipalService principalService) {
        this.principalService = principalService;
    }

    @Override
    public List<String> permissions() {
        Object appId = com.linewell.dataelement.platform.tenant.application.TenantSessions.session().get("appId");
        return principalService.permissionKeys(
                getHandler(),
                appId == null ? null : String.valueOf(appId)
        );
    }

    @Override
    public String getHandler() {
        return String.valueOf(StpUtil.getLoginId());
    }
}
