package com.linewell.dataelement.integration.nifi.canvas.nifi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class NifiNativeViewTest {
    @Test void nativeViewReusesServerAuthenticationWithinTheCurrentGraphAndDeniesWritesAndForeignObjects() throws Exception {
        var nifi=mock(NifiClient.class);var service=new NifiNativeView(nifi,new ObjectMapper());
        try(var identity=mockStatic(StpUtil.class);var tenant=TenantContext.use("tenant-a")) {
            identity.when(StpUtil::getTokenValue).thenReturn("private-platform-session");
            identity.when(StpUtil::getLoginId).thenReturn("user");
            identity.when(()->StpUtil.getLoginIdByToken("private-platform-session")).thenReturn("user");
            var session=new SaSession("validation");session.set("tenantId","tenant-a");
            identity.when(()->StpUtil.getTokenSessionByToken("private-platform-session")).thenReturn(session);
            String cookie=service.open("assigned-node","group");
            assertThat(cookie).contains("HttpOnly","SameSite=Strict").doesNotContain("private-platform-session");
            var request=new MockHttpServletRequest("GET","/nifi-api/flow/process-groups/group");
            request.setCookies(new Cookie(NifiNativeView.COOKIE,cookie.substring(cookie.indexOf('=')+1,cookie.indexOf(';'))));
            String json="{\"processGroupFlow\":{\"permissions\":{\"canRead\":true,\"canWrite\":true},\"flow\":{\"processors\":[{\"component\":{\"id\":\"own-processor\"}}]}}}";
            when(nifi.proxy(eq(HttpMethod.GET),anyString(),any(),isNull())).thenReturn(ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.getBytes(StandardCharsets.UTF_8)));
            var response = service.proxy(request,"/nifi-api/flow/process-groups/root",false);
            assertThat(new ObjectMapper().readTree(response.getBody()).at("/processGroupFlow/permissions/canWrite").asBoolean()).isFalse();
            service.proxy(request,"/nifi-api/processors/own-processor",false);
            verify(nifi,times(2)).bindAssignedNode("assigned-node");
            assertThatThrownBy(()->service.proxy(request,"/nifi-api/processors/foreign",false)).isInstanceOf(ResponseStatusException.class);
            request.setMethod("PUT");
            assertThatThrownBy(()->service.proxy(request,"/nifi-api/processors/own-processor",false)).isInstanceOf(ResponseStatusException.class);
            request.setMethod("GET");session.set("tenantId","tenant-b");
            assertThatThrownBy(()->service.proxy(request,"/nifi-api/flow/process-groups/group",false)).isInstanceOf(ResponseStatusException.class);
        }
    }
    @Test void missingCookieNeverUsesTheDefaultTenantOrBackendCredentials() {
        var nifi=mock(NifiClient.class);var service=new NifiNativeView(nifi,new ObjectMapper());
        assertThatThrownBy(()->service.proxy(new MockHttpServletRequest("GET","/nifi-ui/"),"/nifi/",true))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(nifi);
    }
    @Test void protocolRejectionsKeepTheirHttpStatusInsteadOfBecomingAPlatformSuccessEnvelope() throws Exception {
        var view=mock(NifiNativeView.class);
        when(view.proxy(any(),anyString(),anyBoolean()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,"outside current flow"));
        var controller=new com.linewell.dataelement.integration.nifi.canvas.controller.NifiNativeUiProxyController(
                view,"/nifi-ui","/nifi-api","/nifi","/nifi-api");
        var result=controller.proxy(new MockHttpServletRequest("GET","/nifi-api/process-groups/foreign"),null);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
