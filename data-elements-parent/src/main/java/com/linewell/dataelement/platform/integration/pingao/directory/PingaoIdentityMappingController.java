package com.linewell.dataelement.platform.integration.pingao.directory;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.identity.application.IdentityOrganizationLookupService;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.integration.pingao.sso.PingaoSsoProperties;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/identity/pingao/mappings")
public class PingaoIdentityMappingController {
    private final PingaoDirectoryProperties properties;
    private final PingaoSsoProperties sso;
    private final PingaoDirectoryClient client;
    private final PingaoIdentityMappingRepository repository;
    private final TenantAccessService tenants;
    private final IdentityOrganizationLookupService organizations;

    public PingaoIdentityMappingController(PingaoDirectoryProperties properties, PingaoSsoProperties sso,
            PingaoDirectoryClient client, PingaoIdentityMappingRepository repository, TenantAccessService tenants,
            IdentityOrganizationLookupService organizations) {
        this.properties = properties; this.sso = sso; this.client = client; this.repository = repository;
        this.tenants = tenants; this.organizations = organizations;
    }

    @GetMapping
    public CommonResponse<?> list() {
        requireAdministrator();
        return CommonResponse.success(repository.mappings(properties.getLocalTenantId(), properties.getExternalTenantCode()));
    }

    @PostMapping("/confirm-user")
    public CommonResponse<?> confirm(@RequestBody Map<String, String> body) {
        String operator = requireAdministrator();
        String externalId = required(body, "externalUserId");
        String localId = required(body, "localUserId");
        if (sso.getLocalActiveAccountStatus() == null) throw new IllegalStateException("请先确认并配置本地账号启用状态枚举");
        if (!repository.activeAccount(properties.getLocalTenantId(), localId, sso.getLocalActiveAccountStatus())) {
            throw new IllegalStateException("目标本地账号未启用或不属于当前租户");
        }
        var snapshot = client.fetch(0);
        var remote = snapshot.users().stream().filter(u -> u.id().equals(externalId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("目录中不存在指定用户"));
        repository.confirmUser(properties.getLocalTenantId(), properties.getExternalTenantCode(), remote, localId,
                operator, Instant.ofEpochMilli(snapshot.startedAt()));
        return CommonResponse.success(Map.of("confirmed", true, "localUserId", localId));
    }

    @PostMapping("/confirm-organization")
    public CommonResponse<?> confirmOrganization(@RequestBody Map<String, String> body) {
        String operator = requireAdministrator();
        String externalId = required(body, "externalOrganizationId");
        String localId = required(body, "localOrganizationId");
        String tenant = properties.getLocalTenantId();
        if (TenantContext.call(tenant, () -> organizations.getById(localId)) == null) {
            throw new IllegalStateException("目标本地机构不存在或已停用");
        }
        var snapshot = client.fetch(0);
        var remote = snapshot.organizations().stream().filter(o -> o.id().equals(externalId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("目录中不存在指定机构"));
        repository.confirmOrganization(tenant, properties.getExternalTenantCode(), remote, localId, operator,
                Instant.ofEpochMilli(snapshot.startedAt()));
        return CommonResponse.success(Map.of("confirmed", true, "localOrganizationId", localId));
    }

    private String requireAdministrator() {
        properties.requireConfigured();
        StpUtil.checkLogin();
        String userId = StpUtil.getLoginIdAsString();
        String tenant = TenantContext.requireTenantId();
        if (!tenant.equals(properties.getLocalTenantId()) || !properties.getAdministratorUserIds().contains(userId)) {
            throw new IllegalStateException("当前账号无权维护统一身份映射");
        }
        tenants.requireActiveTenant(tenant);
        tenants.requireMembership(userId, tenant);
        return userId;
    }
    private static String required(Map<String, String> body, String key) {
        String value = body.get(key);
        if (value == null || value.isBlank() || value.length() > 128) throw new IllegalArgumentException("无效映射参数：" + key);
        return value.trim();
    }
}
