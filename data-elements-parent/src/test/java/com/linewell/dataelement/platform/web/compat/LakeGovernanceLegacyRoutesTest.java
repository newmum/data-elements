package com.linewell.dataelement.platform.web.compat;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LakeGovernanceLegacyRoutesTest {
    @Test
    void forwardsEachOldMethodAndPreservesBodyAndOtherParameters() throws Exception {
        record Route(String method, String suffix, String target, boolean pathId) {}
        var routes = List.of(
                new Route("GET", "/zones", "/dws/lake/zones", false),
                new Route("POST", "/zones", "/dws/lake/zones", false),
                new Route("GET", "/zones/overview", "/dws/lake/overview", false),
                new Route("POST", "/zones/init", "/dws/lake/init", false),
                new Route("PUT", "/zones/zone%2Btest", "/dws/lake/state", true),
                new Route("GET", "/zones/zone%2Btest/objects", "/dws/lakeobject/page", true),
                new Route("POST", "/zones/zone%2Btest/objects/batch", "/dws/lakeobject/register", true),
                new Route("POST", "/objects/zone%2Btest/lifecycle", "/dws/lakeobject/lifecycle", true),
                new Route("GET", "/zones/zone%2Btest/rules", "/dws/lakerule/rules", true),
                new Route("PUT", "/zones/zone%2Btest/rules", "/dws/lakerule/rules", true),
                new Route("POST", "/ingest/check", "/dws/lakerule/ingest", false),
                new Route("POST", "/egress/apply", "/dws/lakerule/egress", false));
        var filter = new LegacyRouteConfiguration().legacyResourceRoutes().getFilter();
        byte[] body = "{\"status\":\"ENABLED\"}".getBytes(StandardCharsets.UTF_8);
        for (var route : routes) {
            var request = new MockHttpServletRequest(route.method(), "/app/dws/service/lake" + route.suffix());
            request.setContextPath("/app");
            request.setContent(body);
            request.addParameter("id", "other-zone");
            request.addParameter("pageSize", "100");
            filter.doFilter(request, new MockHttpServletResponse(), (incoming, response) -> {
                var forwarded = (HttpServletRequest) incoming;
                assertThat(forwarded.getMethod()).isEqualTo(route.method());
                assertThat(forwarded.getRequestURI()).isEqualTo("/app" + route.target());
                assertThat(forwarded.getServletPath()).isEqualTo(route.target());
                assertThat(forwarded.getParameterMap().get("id")).containsExactly(route.pathId() ? "zone+test" : "other-zone");
                assertThat(forwarded.getParameter("pageSize")).isEqualTo("100");
                assertThat(forwarded.getInputStream().readAllBytes()).isEqualTo(body);
            });
        }
    }

    @Test
    void leavesUnmappedMethodsAndSiblingGovernancePathsAlone() throws Exception {
        var filter = new LegacyRouteConfiguration().legacyResourceRoutes().getFilter();
        for (String path : List.of("/dws/service/lake/zones/zone/objects/batch", "/dws/service/gateway/routes")) {
            var request = new MockHttpServletRequest("GET",path);
            filter.doFilter(request,new MockHttpServletResponse(),(incoming,response) ->
                    assertThat(incoming).isSameAs(request));
        }
    }
}
