package com.linewell.dataelement.platform.tenant.api;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

@Component
@MagicModule("tenantRuntime")
public class TenantMagicModule {

    private final TenantAccessService tenantAccessService;

    public TenantMagicModule(TenantAccessService tenantAccessService) {
        this.tenantAccessService = tenantAccessService;
    }

    @Comment("获取当前租户ID")
    public String id() {
        return TenantContext.requireTenantId();
    }

    @Comment("获取当前租户上下文")
    public Map<String, Object> current() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", TenantContext.requireTenantId());
        return result;
    }

    @Comment("校验传入租户与当前租户一致，防止越权")
    public String validate(Object tenantId) {
        String current = TenantContext.requireTenantId();
        if (tenantId != null
                && !String.valueOf(tenantId).isBlank()
                && !current.equals(String.valueOf(tenantId))) {
            throw new TenantAccessException("TENANT-FORBIDDEN", "禁止跨租户访问数据");
        }
        return current;
    }

    @Comment("Validate that the login account has at least one active tenant membership")
    public String requireLoginMembership(Object userId) {
        if (userId == null || String.valueOf(userId).isBlank()) {
            throw new TenantAccessException("TENANT-NOT-ASSIGNED", "当前账号未分配可用租户");
        }
        return tenantAccessService.resolve(String.valueOf(userId)).tenantId();
    }

    @Comment("登录成功后校验成员关系并绑定本请求的租户数据源")
    public Map<String, Object> activateLoginTenant(Object requestedTenantId) {
        if (!StpUtil.isLogin()) {
            throw new TenantAccessException("TENANT-LOGIN-REQUIRED", "登录成功后才能绑定租户");
        }
        if (requestedTenantId == null || String.valueOf(requestedTenantId).isBlank()) {
            throw new TenantAccessException("TENANT-ID-MISSING", "登录租户不能为空");
        }
        TenantAccessService.TenantIdentity identity = tenantAccessService.requireMembership(
                String.valueOf(StpUtil.getLoginId()),
                String.valueOf(requestedTenantId).trim()
        );
        TenantContext.bind(identity.tenantId());
        StpUtil.getTokenSession().set("tenantId", identity.tenantId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tenantId", identity.tenantId());
        return result;
    }
}
