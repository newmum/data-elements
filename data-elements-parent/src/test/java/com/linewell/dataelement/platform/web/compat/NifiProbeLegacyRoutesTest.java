package com.linewell.dataelement.platform.web.compat;

import static org.assertj.core.api.Assertions.assertThat;

import com.linewell.dataelement.platform.configuration.MagicBootConfiguration;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;

class NifiProbeLegacyRoutesTest {

    @ParameterizedTest
    @CsvSource({"connection-test,connect", "columns-probe,columns", "tables-probe,tables"})
    void retiredPostsPassThroughAndTheFormalPathKeepsItsJsonCache(String oldSegment, String newSegment) throws Exception {
        String prefix = "/nifi/api/";
        var compatibility = new LegacyRouteConfiguration().legacyResourceRoutes().getFilter();
        var cache = new MagicBootConfiguration().magicJsonRequestCache();
        byte[] malformed = "{".getBytes(StandardCharsets.UTF_8);

        var retired = new MockHttpServletRequest("POST", "/app" + prefix + oldSegment);
        retired.setContextPath("/app");
        retired.setContentType("application/json");
        retired.setContent(malformed);
        compatibility.doFilter(retired, new MockHttpServletResponse(), (incoming, response) ->
                cache.doFilter(incoming, response, (accepted, ignored) ->
                        assertThat(accepted).isSameAs(retired)));

        var formal = new MockHttpServletRequest("POST", "/app" + prefix + newSegment);
        formal.setContextPath("/app");
        formal.setContentType("application/json");
        formal.setContent(malformed);
        compatibility.doFilter(formal, new MockHttpServletResponse(), (incoming, response) ->
                cache.doFilter(incoming, response, (accepted, ignored) -> {
                    assertThat(accepted).isInstanceOf(ContentCachingRequestWrapper.class);
                    var cached = (ContentCachingRequestWrapper) accepted;
                    assertThat(cached.getRequestURI()).isEqualTo(formal.getRequestURI());
                    assertThat(cached.getInputStream().readAllBytes()).isEqualTo(malformed);
                    assertThat(cached.getContentAsByteArray()).isEqualTo(malformed);
                }));

        var get = new MockHttpServletRequest("GET", prefix + oldSegment);
        compatibility.doFilter(get, new MockHttpServletResponse(), (incoming, ignored) ->
                assertThat(incoming).isSameAs(get));
    }
}
