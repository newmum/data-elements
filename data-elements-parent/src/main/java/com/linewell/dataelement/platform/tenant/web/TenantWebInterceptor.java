package com.linewell.dataelement.platform.tenant.web;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.identity.application.TenantIdentityQueryService;
import com.linewell.dataelement.magic.runtime.route.RegisteredMagicFunctionInvoker;
import com.linewell.dataelement.magic.runtime.security.MagicAccessPolicy;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.application.TenantSessions;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.web.security.GlobalInterceptor;
import com.linewell.dataelement.platform.web.security.ProtocolAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

@Component
public class TenantWebInterceptor implements AsyncHandlerInterceptor {

    public static final String SESSION_TENANT_ID = "tenantId";
    private static final String RECEIVER_ACCOUNT_CHECK = "275494036ca45d1ea4eb856c7a5bef94";

    private final TenantAccessService tenantAccessService;
    private final TenantProperties properties;
    private final TenantIdentityQueryService accounts;
    private final RegisteredMagicFunctionInvoker functions;

    public TenantWebInterceptor(
            TenantAccessService tenantAccessService,
            TenantProperties properties,
            TenantIdentityQueryService accounts,
            @Lazy RegisteredMagicFunctionInvoker functions
    ) {
        this.tenantAccessService = tenantAccessService;
        this.properties = properties;
        this.accounts = accounts;
        this.functions = functions;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        TenantContext.clear();
        if (handler instanceof HandlerMethod method) {
            var protocol = method.getBeanType().getAnnotation(ProtocolAccess.class);
            if (protocol != null && !protocol.bindTenant()) {
                return true;
            }
        }
        MagicAccessPolicy access = GlobalInterceptor.verifiedMagicAccess(request);
        // Magic 已声明入口按通过鉴权的策略选库，登录脚本从控制库开始建立新会话。
        boolean control = access == null
                ? GlobalInterceptor.verifiedIdentityGroup(request)
                : switch (access) {
                    case PUBLIC, PUBLIC_ACTION, SESSION_LOGIN, SESSION_LOGOUT, IDAAS_PLATFORM_SESSION,
                            IDAAS_ACCOUNT_SESSION, IDAAS_API_BEARER, IDAAS_RECEIVER_SIGNATURE, MFA_SESSION,
                            MFA_LOGIN_CHALLENGE, LEGACY_SSO_REDEEM -> true;
                    case TENANT_SESSION, LEGACY_SSO_TICKET -> false;
                };
        if (control) {
            TenantContext.control();
            return true;
        }
        if (!properties.isEnabled()) {
            return true;
        }
        // 旧发布协议可由地址选租户；显式行业会话只使用会话中的租户。
        var publishedFlow = access == null ? GlobalInterceptor.verifiedPublishedFlow(request) : null;
        if (publishedFlow != null) {
            TenantAccessService.TenantIdentity identity =
                    tenantAccessService.requireActiveTenant(publishedFlow.tenantId() == null
                            ? properties.getDefaultTenantId() : publishedFlow.tenantId());
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
            String userId = String.valueOf(StpUtil.getLoginId());
            var account = accounts.find(identity.tenantId(), userId);
            if (account == null || !"0".equals(String.valueOf(account.get("status")))) {
                throw new TenantAccessException("TENANT-ACCOUNT-UNAVAILABLE", "本地账号已停用或不存在，请重新登录");
            }
            Map<String, Object> decision = identity.tenantId().equals(TenantContext.getTenantId())
                    ? functions.invoke(RECEIVER_ACCOUNT_CHECK, Map.of("userId", userId)) : null;
            if (decision == null || !Boolean.TRUE.equals(decision.get("allowed"))) {
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

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        TenantContext.clear();
    }

}
