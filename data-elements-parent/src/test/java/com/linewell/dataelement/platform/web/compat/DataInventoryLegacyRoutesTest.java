package com.linewell.dataelement.platform.web.compat;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DataInventoryLegacyRoutesTest {

    @Test
    void collectionAliasesUseOnlyTheirOriginalMethodsAndPreserveQueryParameters() throws Exception {
        var routes = Map.of(
                "/collection/status", "/status",
                "/collection/status/batch", "/statuses",
                "/collection/start", "/start",
                "/collection/rename-missing", "/rename",
                "/collection/additions", "/additions",
                "/collection/changes", "/changes",
                "/review-order", "/order",
                "/snapshot", "/snapshot",
                "/saved", "/saved");
        var filter = new LegacyRouteConfiguration().legacyResourceRoutes().getFilter();
        for (var route : routes.entrySet()) {
            String old = "/dst/database/metadata/tables" + route.getKey();
            boolean post = java.util.Set.of("/collection/status/batch", "/collection/start",
                    "/collection/rename-missing", "/review-order").contains(route.getKey());
            for (String method : new String[]{"GET", "HEAD", "POST", "DELETE"}) {
                var request = new MockHttpServletRequest(method, old);
                byte[] payload = "{\"dbIds\":[\"validation-source\"]}".getBytes(StandardCharsets.UTF_8);
                request.setContent(payload);
                request.addParameter("dbId", "validation-primary");
                request.addParameter("dbId[]", "validation-secondary");
                filter.doFilter(request, new MockHttpServletResponse(), (incoming, response) -> {
                    var forwarded = (HttpServletRequest) incoming;
                    boolean mapped = post ? method.equals("POST") : method.equals("GET") || method.equals("HEAD");
                    assertThat(forwarded.getMethod()).isEqualTo(method);
                    assertThat(forwarded.getRequestURI()).isEqualTo(mapped ? "/dst/collect" + route.getValue() : old);
                    assertThat(forwarded.getParameter("dbId")).isEqualTo("validation-primary");
                    assertThat(forwarded.getParameter("dbId[]")).isEqualTo("validation-secondary");
                    if (post && mapped) assertThat(forwarded.getInputStream().readAllBytes()).isEqualTo(payload);
                });
            }
        }
    }

    @Test
    void preservesPostBodyAndQueryWhenForwardingInventoryRoutes() throws Exception {
        var routes = Map.of(
                "/dst/application/register-options", "/dst/application/options",
                "/dst/catalog/saveOrUpdate", "/dst/catalog/save",
                "/dst/catalog/treeStatistics", "/dst/catalog/treestats",
                "/dst/maintenance/batchRefresh", "/dst/maintenance/reindex");
        var filter = new LegacyRouteConfiguration().legacyResourceRoutes().getFilter();
        byte[] body = "{\"pageSize\":20}".getBytes(StandardCharsets.UTF_8);
        for (var route : routes.entrySet()) {
            var post = new MockHttpServletRequest("POST", "/app" + route.getKey());
            post.setContextPath("/app");
            post.setContent(body);
            post.addParameter("pageSize", "100");
            filter.doFilter(post, new MockHttpServletResponse(), (incoming, response) -> {
                var forwarded = (HttpServletRequest) incoming;
                assertThat(forwarded.getMethod()).isEqualTo("POST");
                assertThat(forwarded.getRequestURI()).isEqualTo("/app" + route.getValue());
                assertThat(forwarded.getParameter("pageSize")).isEqualTo("100");
                assertThat(forwarded.getInputStream().readAllBytes()).isEqualTo(body);
            });

            var get = new MockHttpServletRequest("GET", route.getKey());
            filter.doFilter(get, new MockHttpServletResponse(), (incoming, response) ->
                    assertThat(incoming).isSameAs(get));
        }
    }

    @Test
    void leavesRetiredWorkbenchExportAddressUnmapped() throws Exception {
        var filter = new LegacyRouteConfiguration().legacyResourceRoutes().getFilter();
        var request = new MockHttpServletRequest("POST", "/dst/statistics/management-workbench");
        filter.doFilter(request, new MockHttpServletResponse(), (incoming, response) ->
                assertThat(incoming).isSameAs(request));
    }
}
