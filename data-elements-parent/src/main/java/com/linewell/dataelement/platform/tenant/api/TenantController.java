package com.linewell.dataelement.platform.tenant.api;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.configuration.SystemConfigService;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.application.TenantAccountPolicy;
import com.linewell.dataelement.platform.tenant.application.TenantSessions;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import java.util.List;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.web.TenantWebInterceptor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sym/tenant")
public class TenantController {

    private final TenantAccessService tenantAccessService;
    private final SystemConfigService systemConfigService;
    private final TenantAccountPolicy accountPolicy;

    public TenantController(
            TenantAccessService tenantAccessService,
            SystemConfigService systemConfigService,
            TenantAccountPolicy accountPolicy
    ) {
        this.tenantAccessService = tenantAccessService;
        this.systemConfigService = systemConfigService;
        this.accountPolicy = accountPolicy;
    }

    @GetMapping("/login-options")
    public CommonResponse<?> loginOptions() {
        return CommonResponse.success(tenantAccessService.loginOptions());
    }

    @GetMapping("/login-settings")
    public CommonResponse<?> loginSettings(@RequestParam String tenantId) {
        TenantAccessService.TenantIdentity identity =
                tenantAccessService.requireActiveTenant(tenantId);
        return CommonResponse.success(systemConfigService.publicSettings(identity.tenantId()));
    }

    @GetMapping("/current")
    public CommonResponse<?> current() {
        Map<String, Object> data = new LinkedHashMap<>(tenantAccessService.currentTenant());
        data.put("available", availableAccounts());
        data.put("accountSource", TenantSessions.isLocal() ? "LOCAL" : "CONTROL");
        return CommonResponse.success(data);
    }

    @GetMapping("/available")
    public CommonResponse<?> available() {
        return CommonResponse.success(availableAccounts());
    }

    @PostMapping("/switch")
    public CommonResponse<?> switchTenant(@RequestBody Map<String, Object> body) {
        String tenantId = String.valueOf(body.getOrDefault("tenantId", "")).trim();
        TenantAccessService.TenantIdentity identity;
        if (TenantSessions.isLocal()) {
            if (!TenantContext.requireTenantId().equals(tenantId)) {
                throw new TenantAccessException("TENANT-FORBIDDEN", "本地账号只能访问所属租户，请切换系统后重新登录");
            }
            identity = tenantAccessService.requireActiveTenant(tenantId);
        } else {
            if (accountPolicy.isLocal(tenantId)) {
                throw new TenantAccessException("TENANT-FORBIDDEN", "所选租户使用独立账号，请重新登录");
            }
            identity = tenantAccessService.requireMembership(loginId(), tenantId);
        }
        StpUtil.getTokenSession().set(TenantWebInterceptor.SESSION_TENANT_ID, identity.tenantId());
        try (TenantContext.Scope ignored =
                     TenantContext.use(identity.tenantId())) {
            return CommonResponse.success(tenantAccessService.currentTenant());
        }
    }

    private String loginId() {
        return String.valueOf(StpUtil.getLoginId());
    }

    private List<Map<String,Object>> availableAccounts() {
        if (TenantSessions.isLocal()) return List.of(tenantAccessService.currentTenant());
        return tenantAccessService.availableTenants(loginId()).stream()
                .filter(tenant -> !accountPolicy.isLocal(String.valueOf(tenant.get("tid"))))
                .toList();
    }
}
