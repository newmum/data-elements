package com.linewell.dataelement.integration.nifi.canvas.runtime;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff;
import cn.dev33.satoken.servlet.util.SaTokenContextJakartaServletUtil;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class TaskRuntimeAsyncConfigurationTest {
    @Test void restoresContextDuringAsyncCompletionAndCleansTheWorkerThread() throws Exception {
        var registration = new TaskRuntimeAsyncConfiguration().taskRuntimeAsyncContext();
        var request = new MockHttpServletRequest("GET", "/nifi/api/task-runtime/events");
        var response = new MockHttpServletResponse();
        assertThat(registration.getUrlPatterns()).containsExactly("/nifi/api/task-runtime/events");
        assertThat(registration.isAsyncSupported()).isTrue();
        registration.getFilter().doFilter(request, response, (req, res) -> {
            assertThat(SaTokenContextForThreadLocalStaff.getModelBoxOrNull()).isNotNull();
            assertThat(SaHolder.getRequest().getRequestPath()).isEqualTo(request.getRequestURI());
        });
        assertThat(SaTokenContextForThreadLocalStaff.getModelBoxOrNull()).isNull();
    }

    @Test void preservesAnExistingContextAndCleansUpOnFailure() throws Exception {
        var filter = new TaskRuntimeAsyncConfiguration().taskRuntimeAsyncContext().getFilter();
        var request = new MockHttpServletRequest("GET", "/nifi/api/task-runtime/events");
        var response = new MockHttpServletResponse();
        SaTokenContextJakartaServletUtil.setContext(request, response);
        var prior = SaTokenContextForThreadLocalStaff.getModelBoxOrNull();
        try {
            filter.doFilter(request, response, (req, res) -> assertThat(SaTokenContextForThreadLocalStaff.getModelBoxOrNull()).isSameAs(prior));
            assertThat(SaTokenContextForThreadLocalStaff.getModelBoxOrNull()).isSameAs(prior);
        } finally { SaTokenContextForThreadLocalStaff.clearModelBox(); }
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> { throw new jakarta.servlet.ServletException("test"); })).isInstanceOf(jakarta.servlet.ServletException.class);
        assertThat(SaTokenContextForThreadLocalStaff.getModelBoxOrNull()).isNull();
    }
}
