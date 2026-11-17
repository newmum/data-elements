package com.linewell.dataelement.platform.web.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.linewell.dataelement.platform.web.security.WhiteListProp;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class IdaasLegacyRoutesTest {
    @Test
    void identityAliasesReachTheSameMethodAndAccessBoundary() throws Exception {
        record Route(String method, String previous, String canonical, boolean inLegacyWhitelist) {}
        var routes = List.of(
                new Route("GET", "/idaas/auth/public-settings", "/idaas/auth/settings", false),
                new Route("HEAD", "/idaas/auth/public-settings", "/idaas/auth/settings", false),
                new Route("GET", "/idaas/applications/local-inventory", "/idaas/applications/inventory", false),
                new Route("GET", "/idaas/platform-roles/list", "/idaas/adminrole/list", false),
                new Route("POST", "/idaas/operators/reset-password", "/idaas/operators/reset", false),
                new Route("POST", "/idaas/auth-account/login", "/idaas/identity/login", true),
                new Route("POST", "/idaas/password-recovery/request", "/idaas/recovery/request", true),
                new Route("GET", "/idaas/password-recovery/requests", "/idaas/recovery/requests", false),
                new Route("POST", "/idaas/receiver/v2", "/idaas/receiver/v2", true));
        var filter = new LegacyRouteConfiguration().legacyResourceRoutes().getFilter();
        var whitelist = new WhiteListProp();
        for (var route : routes) {
            var request = new MockHttpServletRequest(route.method(), route.previous());
            request.addParameter("domain", "public");
            filter.doFilter(request, new MockHttpServletResponse(), (incoming, response) -> {
                var rewritten = (HttpServletRequest) incoming;
                assertEquals(route.method(), rewritten.getMethod());
                assertEquals(route.canonical(), rewritten.getRequestURI());
                assertEquals("public", rewritten.getParameter("domain"));
                assertEquals(route.inLegacyWhitelist(), whitelist.isWhiteListed(rewritten.getRequestURI()));
            });
        }
        var wrongMethod = new MockHttpServletRequest("GET", "/idaas/auth-account/login");
        filter.doFilter(wrongMethod, new MockHttpServletResponse(), (incoming, response) ->
                assertFalse(whitelist.isWhiteListed(((HttpServletRequest) incoming).getRequestURI())));
    }
}
