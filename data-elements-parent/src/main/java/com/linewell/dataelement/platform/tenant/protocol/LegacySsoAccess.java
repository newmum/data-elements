package com.linewell.dataelement.platform.tenant.protocol;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.magic.runtime.security.MagicAccessPolicy;
import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.platform.configuration.LegacyIntegrationSecretsProperties;
import com.linewell.dataelement.platform.configuration.SystemConfigService;
import com.linewell.dataelement.platform.tenant.application.TenantSessions;
import com.linewell.dataelement.platform.web.security.GlobalInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.exception.MagicScriptException;

/** Exchanges credentials with the deployment's legacy SSO server. Local membership stays in Magic. */
@Component
@MagicModule("legacySso")
public class LegacySsoAccess {
    private static final String TOKEN_FAILURE = "获取token令牌报错";
    private final SystemConfigService config;
    private final LegacyIntegrationSecretsProperties secrets;
    private final ObjectMapper json;
    private final String tokenPath;
    private final String userPath;
    private final String ticketPath;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public LegacySsoAccess(SystemConfigService config, LegacyIntegrationSecretsProperties secrets, ObjectMapper json,
                           @Value("${data-element.legacy-integration.sso-token-path:}") String tokenPath,
                           @Value("${data-element.legacy-integration.sso-user-path:}") String userPath,
                           @Value("${data-element.legacy-integration.sso-ticket-path:}") String ticketPath) {
        this.config = config;
        this.secrets = secrets;
        this.json = json;
        this.tokenPath = tokenPath;
        this.userPath = userPath;
        this.ticketPath = ticketPath;
    }

    public Map<String, Object> authorizeTicket() {
        if (!StpUtil.isLogin()) {
            throw new MagicScriptException("旧版 SSO 签发需要登录会话");
        }
        var session = TenantSessions.session();
        String name = text(session.get("name"));
        String password = text(session.get("pwd"));
        if (name.isEmpty() || password.isEmpty()) {
            throw new BizException(500, TOKEN_FAILURE);
        }
        var settings = config.runtimeSettings();
        String token = accessToken(settings, "password", Map.of("username", name, "password", password));
        Object ticket = call(settings, ticketPath, false, Map.of("access_token", token), "获取ticket报错").get("data");
        if (ticket == null) {
            throw new BizException(500, "获取ticket报错");
        }
        return Map.of("ticket", ticket);
    }

    public Map<String, Object> authorizeRedemption(Map<String, Object> parameters) {
        if (parameters == null) {
            parameters = Map.of();
        }
        String code = text(parameters.get("code"));
        String ticket = text(parameters.get("ticket"));
        if (code.isEmpty() && ticket.isEmpty()) {
            throw new BizException(500, "无效权限");
        }
        var settings = config.runtimeSettings();
        // The historical consumer gives an authorization code priority when both are present.
        String token = code.isEmpty() ? accessToken(settings, "ticket", Map.of("ticket", ticket))
                : accessToken(settings, "authorization_code", Map.of("code", code));
        var user = call(settings, userPath, false, Map.of("access_token", token), "获取用户信息报错");
        if (text(user.get("id")).isBlank()) {
            throw new BizException(500, "获取用户信息报错");
        }
        return Map.of("user", user, "appId", text(settings.get("appId")));
    }

    public Object verifiedTicket() {
        return GlobalInterceptor.verifiedIdentityProof(currentRequest(), MagicAccessPolicy.LEGACY_SSO_TICKET).get("ticket");
    }

    public Map<?, ?> verifiedRedemption() {
        return GlobalInterceptor.verifiedIdentityProof(currentRequest(), MagicAccessPolicy.LEGACY_SSO_REDEEM);
    }

    private String accessToken(Map<String, Object> settings, String grantType, Map<String, String> credential) {
        String clientId = text(settings.get("client_id"));
        String secret = text(secrets.getSsoClientSecret());
        if (clientId.isBlank() || secret.isBlank()) {
            throw new BizException(500, TOKEN_FAILURE);
        }
        var parameters = new LinkedHashMap<>(credential);
        parameters.put("client_id", clientId);
        parameters.put("client_secret", secret);
        parameters.put("grant_type", grantType);
        parameters.put("scope", "all");
        Object token = call(settings, tokenPath, true, parameters, TOKEN_FAILURE).get("access_token");
        if (!(token instanceof String value) || value.isBlank()) {
            throw new BizException(500, TOKEN_FAILURE);
        }
        return value;
    }

    private Map<String, Object> call(Map<String, Object> settings, String path, boolean post,
                                    Map<String, String> parameters, String failure) {
        try {
            URI base = URI.create(text(settings.get("ssoServerUrl")));
            if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) || base.getHost() == null
                    || base.getRawQuery() != null || base.getRawFragment() != null || base.getRawUserInfo() != null) {
                throw new BizException(500, failure);
            }
            var uri = UriComponentsBuilder.fromUriString(base.toString().replaceFirst("/+$", "") + checkedPath(path));
            parameters.keySet().forEach(key -> uri.queryParam(key, "{" + key + "}"));
            var request = HttpRequest.newBuilder(uri.encode().buildAndExpand(parameters).toUri())
                    .timeout(Duration.ofSeconds(12)).header("Accept", "application/json");
            if (post) {
                request.POST(HttpRequest.BodyPublishers.noBody());
            }
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(500, failure);
            }
            Map<String, Object> result = json.readValue(response.body(), new TypeReference<>() { });
            if (result == null) {
                throw new BizException(500, failure);
            }
            return result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BizException(500, failure);
        } catch (IOException | IllegalArgumentException rejected) {
            // Upstream exception messages can contain the URI and its credentials.
            throw new BizException(500, failure);
        }
    }

    private static String checkedPath(String path) {
        if (path == null || !path.matches("(?:/[A-Za-z0-9_.-]+)+") || !URI.create(path).normalize().getPath().equals(path)) {
            throw new IllegalArgumentException("旧版 SSO 协议路径配置无效");
        }
        return path;
    }

    private static String text(Object value) {
        return Objects.toString(value, "");
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servlet ? servlet.getRequest() : null;
    }
}
