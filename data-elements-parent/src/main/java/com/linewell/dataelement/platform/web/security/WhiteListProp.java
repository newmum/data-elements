package com.linewell.dataelement.platform.web.security;


import cn.hutool.core.text.AntPathMatcher;
import java.util.List;
import java.util.Set;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
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
            "/portal/sso/redeem",
            "/portal/sso/ticket",
            "/portal/profile/password",
            "/idaas/recovery/request",
            "/idaas/identity/login",
            "/idaas/identity/register",
            "/idaas/identity/me",
            "/idaas/identity/logout",
            "/idaas/identity/password",
            "/idaas/identity/sessions",
            "/idaas/identity/revoke",
            "/idaas/identity/entity",
            "/idaas/self/register",
            "/idaas/self/mine",
            "/idaas/api/whoami",
            "/idaas/mfa/verify",
            "/idaas/mfa/status",
            "/idaas/mfa/setup",
            "/idaas/mfa/bind",
            "/idaas/mfa/disable",
            "/idaas/verifications/mine",
            "/idaas/verifications/submit",
            "/idaas/receiver/v2",
            "/idaas/receiver/admin"
    );

    private List<String> whiteList;
    private List<AdditionalPublicEndpoints> additionalPublicEndpoints = List.of();

    @Autowired(required = false)
    public void setAdditionalPublicEndpoints(List<AdditionalPublicEndpoints> endpoints) {
        additionalPublicEndpoints = List.copyOf(endpoints);
    }

    public boolean isWhiteListed(String requestUri) {
        if (requestUri == null) {
            return false;
        }
        if(requestUri.startsWith("/idaas/oauth2/") || requestUri.startsWith("/idaas/connect/") || requestUri.equals("/.well-known/openid-configuration") || requestUri.startsWith("/.well-known/oauth-authorization-server")) return true;
        if (requestUri.startsWith("/portal/")) {
            return FRAMEWORK_PUBLIC_PATHS.contains(requestUri);
        }
        if (FRAMEWORK_PUBLIC_PATHS.contains(requestUri)) {
            return true;
        }
        if (additionalPublicEndpoints.stream().anyMatch(endpoints -> endpoints.paths().contains(requestUri))) {
            return true;
        }
        return whiteList != null
                && whiteList.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestUri));
    }
}
