package com.linewell.dataelement.platform.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FlowServeLegacyRoutesTest {
    @Test
    void flowPathsKeepTheirMethodAndUseThePathIdAheadOfQueryParameters() throws Exception {
        record Route(String method, String suffix, String target) {}
        var routes = List.of(new Route("GET", "", "detail"), new Route("HEAD", "", "detail"),
                new Route("PUT", "", "draft"), new Route("POST", "/debug", "debug"),
                new Route("POST", "/publish", "publish"), new Route("POST", "/offline", "offline"),
                new Route("GET", "/versions", "versions"));
        var filter = new WebConfiguration().legacyResourceRoutes().getFilter();
        for (Route route : routes) {
            var request = new MockHttpServletRequest(route.method(), "/platform/dws/flowserve/flows/flow%2Btest" + route.suffix());
            request.setContextPath("/platform");
            request.addParameter("id", "other-flow", "third-flow");
            request.addParameter("version", "17");
            filter.doFilter(request, new MockHttpServletResponse(), (incoming, response) -> {
                var forwarded = (HttpServletRequest) incoming;
                assertThat(forwarded.getMethod()).isEqualTo(route.method());
                assertThat(forwarded.getRequestURI()).isEqualTo("/platform/dws/flowserve/" + route.target());
                assertThat(forwarded.getServletPath()).isEqualTo("/dws/flowserve/" + route.target());
                assertThat(forwarded.getParameter("id")).isEqualTo("flow+test");
                assertThat(forwarded.getParameterValues("id")).containsExactly("flow+test");
                assertThat(forwarded.getParameterMap().get("id")).containsExactly("flow+test");
                assertThat(forwarded.getParameter("version")).isEqualTo("17");
            });
        }
    }

    @Test
    void publishedPathsAndUnmappedMethodsContinueUntouched() throws Exception {
        var filter = new WebConfiguration().legacyResourceRoutes().getFilter();
        for (String target : List.of("/dws/flowserve/published/portal", "/dws/flowserve/flows/sample/debug")) {
            var request = new MockHttpServletRequest("GET", target);
            filter.doFilter(request, new MockHttpServletResponse(), (actual, response) -> assertThat(actual).isSameAs(request));
        }
    }
}
