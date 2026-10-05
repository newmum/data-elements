package com.linewell.dataelement.platform.integration.pingao.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Authorization-code user tokens are intentionally never cached with application tokens. */
@Component
public class PingaoSsoClient {
    private final PingaoSsoProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http;

    @Autowired
    public PingaoSsoClient(PingaoSsoProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getTimeout()).followRedirects(HttpClient.Redirect.NEVER).build());
    }

    PingaoSsoClient(PingaoSsoProperties properties, ObjectMapper mapper, HttpClient http) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = http;
    }

    public URI authorizationUri(String state) {
        properties.requireConfigured();
        requireOpaqueValue(state);
        return URI.create(properties.getAuthorizeUrl() + "?response_type=code&client_id=" + encode(properties.getClientId())
                + "&redirect_uri=" + encode(properties.getCallbackUrl()) + "&state=" + encode(state));
    }

    /** Caller must atomically consume browser-bound state BEFORE calling this method. */
    public Identity exchangeCode(String code) {
        properties.requireConfigured();
        requireOpaqueValue(code);
        String basic = Base64.getEncoder().encodeToString((properties.getClientId() + ":"
                + properties.getClientSecret()).getBytes(StandardCharsets.UTF_8));
        String form = "grant_type=authorization_code&code=" + encode(code)
                + "&redirect_uri=" + encode(properties.getCallbackUrl());
        JsonNode token = send(HttpRequest.newBuilder(URI.create(properties.getTokenUrl()))
                .timeout(properties.getTimeout()).header("Authorization", "Basic " + basic)
                .header("Accept", "application/json").header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
        String accessToken = required(token, "access_token");
        if (!"Bearer".equalsIgnoreCase(required(token, "token_type"))) throw failure("用户令牌类型无效");
        // V2.0 explicitly specifies access_token as a query parameter, server-to-server only.
        JsonNode user = send(HttpRequest.newBuilder(URI.create(properties.getUserinfoUrl() + "?access_token=" + encode(accessToken)))
                .timeout(properties.getTimeout()).header("Accept", "application/json").GET().build());
        JsonNode audience = user.path("aud");
        boolean matchesAudience = audience.isTextual() && properties.getClientId().equals(audience.asText());
        if (audience.isArray()) {
            for (JsonNode value : audience) if (value.isTextual() && properties.getClientId().equals(value.asText())) matchesAudience = true;
        }
        if (!matchesAudience) throw failure("统一认证返回的应用身份不匹配");
        if (user.hasNonNull("eCode") && !properties.getExternalTenantCode().equals(user.path("eCode").asText())) {
            throw failure("统一认证返回的租户不匹配");
        }
        String uid = required(user, "uid");
        String subject = required(user, "sub");
        if (!uid.equals(subject)) throw failure("统一认证 uid 与 sub 不一致");
        String directoryUserId = required(user, properties.getDirectoryUserIdClaim());
        if (!directoryUserId.equals(uid)) throw failure("统一认证目录用户标识不一致");
        return new Identity(directoryUserId, subject, required(user, "org_id"), optional(user, "orgNum"));
    }

    public URI logoutUri() {
        properties.requireConfigured();
        return URI.create(properties.getLogoutUrl() + "?post_logout_redirect_uri=" + encode(properties.getLoggedOutUrl()));
    }

    private JsonNode send(HttpRequest request) {
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw failure("统一认证请求失败，HTTP " + response.statusCode());
            JsonNode payload = mapper.readTree(response.body());
            if (payload == null || !payload.isObject()) throw failure("统一认证响应格式无效");
            return payload;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw failure("统一认证请求被中断");
        } catch (java.io.IOException exception) {
            // Exception causes can contain the userinfo URL/token or sensitive JSON. Do not expose them.
            throw failure("统一认证网络或响应解析失败");
        }
    }

    private static String required(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) throw failure("统一认证缺少身份字段：" + field);
        return value.asText();
    }
    private static String optional(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return "";
        if (!value.isTextual() && !value.isNumber()) throw failure("统一认证身份字段类型无效：" + field);
        return value.asText().trim();
    }

    private static void requireOpaqueValue(String value) {
        if (value == null || value.isBlank() || value.length() > 4096) throw failure("统一认证凭据为空或过长");
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static IllegalStateException failure(String message) { return new IllegalStateException(message); }
    public record Identity(String directoryUserId, String subject, String externalOrganizationId,
                           String externalOrganizationCode) {
        /** Compatibility constructor for call sites that only test user identity. */
        public Identity(String directoryUserId, String subject) {
            this(directoryUserId, subject, "", "");
        }
    }
}
