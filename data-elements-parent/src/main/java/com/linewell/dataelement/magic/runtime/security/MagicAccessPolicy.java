package com.linewell.dataelement.magic.runtime.security;

import java.util.Set;
import org.ssssssss.magicapi.core.model.ApiInfo;

/** HTTP access declared by a Magic API resource. */
public enum MagicAccessPolicy {
    PUBLIC,
    PUBLIC_ACTION,
    SESSION_LOGIN,
    SESSION_LOGOUT,
    TENANT_SESSION,
    IDAAS_PLATFORM_SESSION,
    IDAAS_ACCOUNT_SESSION,
    IDAAS_API_BEARER,
    IDAAS_RECEIVER_SIGNATURE,
    LEGACY_SSO_TICKET,
    LEGACY_SSO_REDEEM,
    MFA_SESSION,
    MFA_LOGIN_CHALLENGE;

    private static final String ACCESS_OPTION = "access";
    private static final String SESSION_REQUIRED_MESSAGE = "sessionRequiredMessage";
    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    public static MagicAccessPolicy declared(ApiInfo api) {
        if (api == null || api.getOptions() == null) {
            return null;
        }
        var declarations = api.getOptions().stream()
                .filter(option -> option != null && ACCESS_OPTION.equals(option.getName()))
                .toList();
        if (declarations.isEmpty()) {
            sessionRequiredMessage(api, null);
            return null;
        }
        if (declarations.size() != 1 || !(declarations.getFirst().getValue() instanceof String value)) {
            throw new IllegalArgumentException("Magic API 访问策略必须为唯一的字符串选项");
        }
        MagicAccessPolicy policy = switch (value) {
            case "PUBLIC" -> PUBLIC;
            case "PUBLIC_ACTION" -> PUBLIC_ACTION;
            case "SESSION_LOGIN" -> SESSION_LOGIN;
            case "SESSION_LOGOUT" -> SESSION_LOGOUT;
            case "TENANT_SESSION" -> TENANT_SESSION;
            case "IDAAS_PLATFORM_SESSION" -> IDAAS_PLATFORM_SESSION;
            case "IDAAS_ACCOUNT_SESSION" -> IDAAS_ACCOUNT_SESSION;
            case "IDAAS_API_BEARER" -> IDAAS_API_BEARER;
            case "IDAAS_RECEIVER_SIGNATURE" -> IDAAS_RECEIVER_SIGNATURE;
            case "LEGACY_SSO_TICKET" -> LEGACY_SSO_TICKET;
            case "LEGACY_SSO_REDEEM" -> LEGACY_SSO_REDEEM;
            case "MFA_SESSION" -> MFA_SESSION;
            case "MFA_LOGIN_CHALLENGE" -> MFA_LOGIN_CHALLENGE;
            default -> throw new IllegalArgumentException("Magic API 访问策略类型无效");
        };
        String method = api.getMethod();
        boolean read = "GET".equals(method) || "HEAD".equals(method);
        boolean postOnly = policy == PUBLIC_ACTION || policy == SESSION_LOGIN || policy == SESSION_LOGOUT
                || policy == MFA_SESSION || policy == MFA_LOGIN_CHALLENGE
                || policy == IDAAS_RECEIVER_SIGNATURE;
        if (method == null || !METHODS.contains(method) || (policy == PUBLIC || policy == IDAAS_API_BEARER) && !read
                || (policy == LEGACY_SSO_TICKET || policy == LEGACY_SSO_REDEEM) && !"GET".equals(method)
                || postOnly && !"POST".equals(method)) {
            throw new IllegalArgumentException("Magic API 访问策略与 HTTP 方法不匹配");
        }
        sessionRequiredMessage(api, policy);
        return policy;
    }

    public static String sessionRequiredMessage(ApiInfo api, MagicAccessPolicy policy) {
        if (api == null || api.getOptions() == null) {
            return null;
        }
        var options = api.getOptions().stream()
                .filter(option -> option != null && SESSION_REQUIRED_MESSAGE.equals(option.getName()))
                .toList();
        if (options.isEmpty()) {
            return null;
        }
        if (policy != TENANT_SESSION || options.size() != 1
                || !(options.getFirst().getValue() instanceof String message) || message.isBlank()) {
            throw new IllegalArgumentException("行业会话提示必须为 TENANT_SESSION 资源上的唯一非空字符串选项");
        }
        return message;
    }
}
