package com.linewell.dataelement.feature.approval.api;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.approval.application.ApprovalPrincipalService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

@Component
@MagicModule("approvalRuntime")
public class ApprovalRuntimeMagicModule {

    private final ApprovalPrincipalService principalService;

    public ApprovalRuntimeMagicModule(ApprovalPrincipalService principalService) {
        this.principalService = principalService;
    }

    @Comment("Return the current tenant-scoped approval principal")
    public Map<String, Object> principal() {
        return principalService.principal(currentUserId(), currentAppId());
    }

    @Comment("按当前登录人和指定应用读取审批角色、机构及权限；未指定应用时使用会话应用")
    public Map<String, Object> principalForApp(String appId) {
        return principalService.principal(currentUserId(), appId == null || appId.isBlank() ? currentAppId() : appId);
    }

    @Comment("Return Warm-Flow permission keys for the current user")
    public List<String> permissions() {
        return principalService.permissionKeys(currentUserId(), currentAppId());
    }

    private String currentUserId() {
        return String.valueOf(StpUtil.getLoginId());
    }

    private String currentAppId() {
        Object appId = com.linewell.dataelement.platform.tenant.application.TenantSessions.session().get("appId");
        return appId == null ? null : String.valueOf(appId);
    }
}
