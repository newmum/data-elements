package com.linewell.dataelement.platform.web.compat;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PushRegistryLegacyRoutesTest {
    @Test
    void preservesMethodsParametersAndRelayHeadersForRegisteredAliases() throws Exception {
        var routes = List.of(
                new Route("GET", "/schema/contracts", "/contracts"),
                new Route("HEAD", "/schema/contracts", "/contracts"),
                new Route("POST", "/schema/publish", "/publish"),
                new Route("POST", "/credential/issue", "/issue"),
                new Route("POST", "/credential/rotate", "/rotate"),
                new Route("POST", "/internal/validate", "/validate"),
                new Route("POST", "/internal/ftp-deliver", "/deliver"));
        var registration = new LegacyRouteConfiguration().legacyResourceRoutes();
        for (var route : routes) {
            String legacy = "/dws/push" + route.oldPath();
            assertThat(registration.getUrlPatterns()).contains(legacy);
            var request = new MockHttpServletRequest(route.method(), legacy);
            request.setQueryString("datasourceId=source");
            request.addParameter("datasourceId", "source");
            request.addHeader("X-Relay-Client-Id", "validation-client");
            request.setContent("{\"datasourceId\":\"source\"}".getBytes(StandardCharsets.UTF_8));
            registration.getFilter().doFilter(request, new MockHttpServletResponse(), (wrapped, response) -> {
                var actual = (jakarta.servlet.http.HttpServletRequest) wrapped;
                assertThat(actual.getMethod()).isEqualTo(route.method());
                assertThat(actual.getRequestURI()).isEqualTo("/dws/push" + route.newPath());
                assertThat(actual.getParameterValues("datasourceId")).containsExactly("source");
                assertThat(actual.getHeader("X-Relay-Client-Id")).isEqualTo("validation-client");
                assertThat(actual.getInputStream().readAllBytes()).isEqualTo(request.getContentAsByteArray());
            });

            request.setMethod("DELETE");
            registration.getFilter().doFilter(request, new MockHttpServletResponse(), (wrapped, response) ->
                    assertThat(wrapped).isSameAs(request));
        }
        assertThat(registration.getUrlPatterns()).doesNotContain("/dws/push/internal/*");
    }

    private record Route(String method, String oldPath, String newPath) { }
}
