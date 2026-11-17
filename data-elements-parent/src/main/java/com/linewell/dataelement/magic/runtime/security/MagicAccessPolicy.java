package com.linewell.dataelement.magic.runtime.security;

import java.util.Set;
import org.ssssssss.magicapi.core.model.ApiInfo;

/** HTTP access declared by a Magic API resource. */
public enum MagicAccessPolicy {
    PUBLIC,
    TENANT_SESSION,
    IDAAS_PLATFORM_SESSION,
    HAITONG_RELAY;

    private static final String ACCESS_OPTION = "access";
    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    public static MagicAccessPolicy declared(ApiInfo api) {
        if (api == null || api.getOptions() == null) {
            return null;
        }
        var declarations = api.getOptions().stream()
                .filter(option -> option != null && ACCESS_OPTION.equals(option.getName()))
                .toList();
        if (declarations.isEmpty()) {
            return null;
        }
        if (declarations.size() != 1 || !(declarations.getFirst().getValue() instanceof String value)) {
            throw new IllegalArgumentException("Magic API 访问策略必须为唯一的字符串选项");
        }
        MagicAccessPolicy policy = switch (value) {
            case "PUBLIC" -> PUBLIC;
            case "TENANT_SESSION" -> TENANT_SESSION;
            case "IDAAS_PLATFORM_SESSION" -> IDAAS_PLATFORM_SESSION;
            case "HAITONG_RELAY" -> HAITONG_RELAY;
            default -> throw new IllegalArgumentException("Magic API 访问策略类型无效");
        };
        String method = api.getMethod();
        boolean read = "GET".equals(method) || "HEAD".equals(method);
        if (method == null || !METHODS.contains(method) || policy == PUBLIC && !read
                || policy == HAITONG_RELAY && !"POST".equals(method)) {
            throw new IllegalArgumentException("Magic API 访问策略与 HTTP 方法不匹配");
        }
        return policy;
    }
}
