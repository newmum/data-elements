package com.linewell.dataelement.platform.integration.pingao.sso;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.model.common.CommonResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

/** Disabled-by-default pilot entrypoint. Never accepts a username/local ID from the browser. */
@RestController
@RequestMapping("/identity/pingao/sso")
public class PingaoSsoController {
    private static final String BINDING_COOKIE = "pingao_sso_binding";
    private final PingaoSsoProperties properties;
    private final PingaoSsoClient client;
    private final PingaoSsoTickets tickets;
    private final PingaoSsoIdentityService identities;

    public PingaoSsoController(PingaoSsoProperties properties, PingaoSsoClient client,
                               PingaoSsoTickets tickets, PingaoSsoIdentityService identities) {
        this.properties = properties; this.client = client; this.tickets = tickets; this.identities = identities;
    }

    @GetMapping("/status")
    public CommonResponse<?> status(HttpServletResponse response) {
        noCache(response);
        return CommonResponse.success(Map.of("enabled", properties.isEnabled() && properties.isIdentityContractConfirmed()));
    }

    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        properties.requireConfigured();
        noCache(response);
        String binding = PingaoSsoTickets.randomValue();
        String state = tickets.issueState(binding);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(BINDING_COOKIE, binding)
                .httpOnly(true).secure(!properties.isAllowPrivateHttp()).sameSite("Lax").path("/").maxAge(300).build().toString());
        response.sendRedirect(client.authorizationUri(state).toString());
    }

    @GetMapping("/callback")
    public void callback(@RequestParam String state, @RequestParam String code,
                         HttpServletRequest request, HttpServletResponse response) throws IOException {
        noCache(response);
        String binding = binding(request);
        if (!tickets.consumeState(state, binding)) throw new IllegalStateException("认证请求已失效或浏览器不匹配，请重新登录");
        var identity = client.exchangeCode(code);
        identities.resolve(identity);
        String ticket = tickets.issueExchange(binding, identity);
        response.sendRedirect(properties.getFrontendUrl() + "#/pingao-sso/callback?exchange=" + ticket);
    }

    @PostMapping("/exchange")
    public CommonResponse<?> exchange(@RequestBody Map<String, String> body,
                                     HttpServletRequest request, HttpServletResponse response) {
        noCache(response);
        PingaoSsoClient.Identity identity = tickets.consumeExchange(body.get("ticket"), binding(request));
        if (identity == null) throw new IllegalStateException("登录交换票据已失效或已使用");
        String userId = identities.resolve(identity);
        // Reuse platform authentication; business authorization continues to read local roles.
        StpUtil.login(userId);
        StpUtil.getTokenSession().set("tenantId", properties.getLocalTenantId());
        StpUtil.getTokenSession().set("pingaoExternalUserId", identity.directoryUserId());
        StpUtil.getSession().set("appId", properties.getLocalAppId());
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(BINDING_COOKIE, "")
                .httpOnly(true).secure(!properties.isAllowPrivateHttp()).sameSite("Lax").path("/").maxAge(0).build().toString());
        return CommonResponse.success(Map.of("token", StpUtil.getTokenValue(), "tenantId", properties.getLocalTenantId()));
    }

    @PostMapping("/logout")
    public CommonResponse<?> logout(HttpServletResponse response) {
        noCache(response);
        StpUtil.checkLogin();
        // Do not log out unrelated legacy logins through this endpoint.
        if (StpUtil.getTokenSession().get("pingaoExternalUserId") == null) throw new IllegalStateException("当前会话不是警综登录");
        String url = client.logoutUri().toString();
        StpUtil.logout();
        return CommonResponse.success(Map.of("logoutUrl", url));
    }

    private static String binding(HttpServletRequest request) {
        if (request.getCookies() != null) for (Cookie cookie : request.getCookies()) {
            if (BINDING_COOKIE.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
    private static void noCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
}
