package com.linewell.dataelement.platform.tenant.web;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.identity.application.TenantIdentityQueryService;
import com.linewell.dataelement.magic.runtime.security.MagicAccessPolicy;
import com.linewell.dataelement.platform.tenant.api.IdentityPolicyInvoker;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.application.TenantSessions;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.web.security.GlobalInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantWebInterceptor implements HandlerInterceptor {

    public static final String SESSION_TENANT_ID = "tenantId";
    private static final String PUBLISHED_PREFIX = "/dws/flowserve/published/";
    private static final String PUBLISHED_TENANT_PREFIX =
            "/dws/flowserve/published/__tenant/";

    private final TenantAccessService tenantAccessService;
    private final TenantProperties properties;
    private final TenantIdentityQueryService accounts;
    @Autowired(required=false)
    private IdentityPolicyInvoker identityPolicy;

    public TenantWebInterceptor(
            TenantAccessService tenantAccessService,
            TenantProperties properties,
            TenantIdentityQueryService accounts
    ) {
        this.tenantAccessService = tenantAccessService;
        this.properties = properties;
        this.accounts = accounts;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        TenantContext.clear();
        MagicAccessPolicy access = GlobalInterceptor.verifiedMagicAccess(request);
        String path = request.getRequestURI();
        // Magic 已声明入口按通过鉴权的策略选库，登录脚本从控制库开始建立新会话。
        boolean control = access == null
                ? path.startsWith("/idaas/")
                : switch (access) {
                    case PUBLIC, SESSION_LOGIN, SESSION_LOGOUT, IDAAS_PLATFORM_SESSION, HAITONG_RELAY -> true;
                    case TENANT_SESSION -> false;
                };
        if (control) {
            TenantContext.control();
            return true;
        }
        if (!properties.isEnabled()) {
            return true;
        }
        // 旧发布协议可由地址选租户；显式行业会话只使用会话中的租户。
        String publishedTenantId = access == null ? publishedTenantId(path) : null;
        if (publishedTenantId != null || (access == null && path.startsWith(PUBLISHED_PREFIX))) {
            TenantAccessService.TenantIdentity identity =
                    tenantAccessService.requireActiveTenant(publishedTenantId == null
                            ? properties.getDefaultTenantId() : publishedTenantId);
            TenantContext.use(identity.tenantId());
            return true;
        }
        if (!StpUtil.isLogin()) {
            TenantContext.control();
            return true;
        }
        Object tenantId = StpUtil.getTokenSession().get(SESSION_TENANT_ID);
        TenantAccessService.TenantIdentity identity;
        if (tenantId == null || String.valueOf(tenantId).isBlank()) {
            throw new TenantAccessException("TENANT-CONTEXT-MISSING", "账号会话缺少所属应用，请选择应用后重新登录");
        } else {
            identity = new TenantAccessService.TenantIdentity(String.valueOf(tenantId));
        }
        TenantContext.use(identity.tenantId());
        boolean local = accounts.isLocal(identity.tenantId());
        if (local != TenantSessions.isLocal()) {
            throw new TenantAccessException("TENANT-ACCOUNT-REALM-MISMATCH", "当前租户账号体系已变更，请重新登录");
        }
        if (local) {
            var account = accounts.find(identity.tenantId(), String.valueOf(StpUtil.getLoginId()));
            if (account == null || !"0".equals(String.valueOf(account.get("status")))) {
                throw new TenantAccessException("TENANT-ACCOUNT-UNAVAILABLE", "本地账号已停用或不存在，请重新登录");
            }
            if (identityPolicy!=null && !identityPolicy.localAccountAllowed(identity.tenantId(),String.valueOf(StpUtil.getLoginId()))) {
                throw new TenantAccessException("TENANT-CENTRAL-SOURCE-REVOKED", "此账号的统一管理来源已停用，请联系管理员或办理脱管");
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        TenantContext.clear();
    }

    static String publishedTenantId(String requestUri) {
        int prefixIndex = requestUri.indexOf(PUBLISHED_TENANT_PREFIX);
        if (prefixIndex < 0) {
            return null;
        }
        int start = prefixIndex + PUBLISHED_TENANT_PREFIX.length();
        int end = requestUri.indexOf('/', start);
        String tenantId = end < 0
                ? requestUri.substring(start)
                : requestUri.substring(start, end);
        return tenantId.isBlank() ? null : tenantId;
    }

}
