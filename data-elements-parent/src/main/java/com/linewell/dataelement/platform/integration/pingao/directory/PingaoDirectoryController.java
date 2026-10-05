package com.linewell.dataelement.platform.integration.pingao.directory;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/identity/pingao/directory")
public class PingaoDirectoryController {
    private static final Logger log = LoggerFactory.getLogger(PingaoDirectoryController.class);
    private final PingaoDirectoryClient client;
    private final PingaoDirectoryProperties properties;
    private final TenantAccessService tenants;

    public PingaoDirectoryController(PingaoDirectoryClient client, PingaoDirectoryProperties properties,
                                     TenantAccessService tenants) {
        this.client = client;
        this.properties = properties;
        this.tenants = tenants;
    }

    @PostMapping("/preview")
    public CommonResponse<?> preview(@RequestBody(required = false) Map<String, Long> body) {
        StpUtil.checkLogin();
        String userId = StpUtil.getLoginIdAsString();
        String tenantId = TenantContext.requireTenantId();
        if (!tenantId.equals(properties.getLocalTenantId())
                || !properties.getAdministratorUserIds().contains(userId)) {
            throw new IllegalStateException("当前账号不在统一用户目录预检管理员名单内");
        }
        tenants.requireActiveTenant(tenantId);
        tenants.requireMembership(userId, tenantId);
        Long requested = body == null ? null : body.get("since");
        long since = requested == null ? 0 : requested;
        try {
            var preview = PingaoDirectoryPreview.inspect(client.fetch(since));
            log.info("Directory preview succeeded: tenant={}, operator={}, organizations={}, users={}, valid={}",
                    tenantId, userId, preview.organizations(), preview.users(), preview.structurallyValid());
            return CommonResponse.success(preview);
        } catch (RuntimeException exception) {
            log.warn("Directory preview failed: tenant={}, operator={}, errorType={}",
                    tenantId, userId, exception.getClass().getSimpleName());
            throw exception;
        }
    }
}
