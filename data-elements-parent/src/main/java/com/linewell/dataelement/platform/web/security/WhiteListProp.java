package com.linewell.dataelement.platform.web.security;


import cn.hutool.core.text.AntPathMatcher;
import java.util.List;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "data-trading-parent")
@RefreshScope
public class WhiteListProp {
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final Set<String> FRAMEWORK_PUBLIC_PATHS = Set.of(
            "/portal/index",
            "/portal/login",
            "/portal/logout",
            "/idaas/auth/login",
            "/idaas/auth/public-settings",
            "/idaas/auth-account/login",
            "/idaas/auth-account/register",
            "/idaas/auth-account/me",
            "/idaas/auth-account/logout",
            "/idaas/auth-account/password",
            "/idaas/auth-account/sessions",
            "/idaas/auth-account/revoke-session",
            "/idaas/auth-account/select-entity",
            "/idaas/self-entities/register",
            "/idaas/self-entities/mine",
            "/idaas/api/whoami",
            "/idaas/mfa/verify-login",
            "/idaas/mfa/status",
            "/idaas/mfa/setup",
            "/idaas/mfa/bind",
            "/idaas/mfa/disable",
            "/idaas/verifications/mine",
            "/idaas/verifications/submit",
            "/idaas/receiver/v2",
            "/idaas/receiver/admin",
            "/sym/tenant/login-options",
            "/sym/tenant/login-settings",
            "/identity/pingao/sso/status",
            "/identity/pingao/sso/login",
            "/identity/pingao/sso/callback",
            "/identity/pingao/sso/exchange"
    );

    private List<String> whiteList;

    public boolean isWhiteListed(String requestUri) {
        if (requestUri == null) {
            return false;
        }
        if(requestUri.startsWith("/idaas/oauth2/") || requestUri.startsWith("/idaas/connect/") || requestUri.equals("/.well-known/openid-configuration") || requestUri.startsWith("/.well-known/oauth-authorization-server")) return true;
        if (FRAMEWORK_PUBLIC_PATHS.contains(requestUri)) {
            return true;
        }
        return whiteList != null
                && whiteList.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestUri));
    }
}
