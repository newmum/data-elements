package com.haitong.api;

import com.haitong.config.RelayProperties;
import com.haitong.config.Secrets;
import com.haitong.core.Json;
import com.haitong.security.PushCredentialStore;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.ssssssss.magicapi.core.config.MagicConfiguration;
import org.ssssssss.magicapi.core.context.MagicUser;
import org.ssssssss.magicapi.core.interceptor.AuthorizationInterceptor;
import org.ssssssss.magicapi.core.model.ApiInfo;
import org.ssssssss.magicapi.core.service.MagicResourceService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EditorValidationAuthenticationTest {
    @Test
    @SuppressWarnings("unchecked")
    void editorSessionIsBoundToEnabledExampleRouteAndNeverReplacesBusinessAuthentication() throws Exception {
        var secrets = mock(Secrets.class);
        var credentials = mock(PushCredentialStore.class);
        var filter = new AuthenticationFilter(secrets, new RelayProperties(), new Json(), credentials);
        var configuration = mock(MagicConfiguration.class);
        var authorization = mock(AuthorizationInterceptor.class);
        var resources = mock(MagicResourceService.class);
        var provider = (ObjectProvider<MagicConfiguration>) mock(ObjectProvider.class);
        var environment = new MockEnvironment().withProperty("haitong.validation.enabled", "true");
        filter.editorConfiguration(provider, environment);
        when(provider.getIfAvailable()).thenReturn(configuration);
        when(configuration.getAuthorizationInterceptor()).thenReturn(authorization);
        when(authorization.requireLogin()).thenReturn(true);
        when(authorization.getUserByToken("valid-editor-session")).thenReturn(new MagicUser("editor", "admin", "valid-editor-session"));
        var api = new ApiInfo();
        api.setId("validation-example");
        api.setGroupId("examples");
        api.setPath("/push/status_dict");
        api.setMethod("POST");
        when(resources.file("validation-example")).thenReturn(api);
        when(resources.getGroupPath("examples")).thenReturn("/validation");

        try (var magic = mockStatic(MagicConfiguration.class)) {
            magic.when(MagicConfiguration::getMagicResourceService).thenReturn(resources);
            var chain = mock(FilterChain.class);
            var valid = request("/validation/push/status_dict", "valid-editor-session", "validation-example");
            var success = new MockHttpServletResponse();
            filter.doFilter(valid, success, chain);
            verify(chain).doFilter(valid, success);
            assertEquals("magic-editor:admin", AuthenticationFilter.identity(valid).id());
            assertFalse(AuthenticationFilter.identity(valid).admin());

            for (var rejected : new MockHttpServletRequest[]{
                    request("/validation/push/status_dict", "forged-editor-session", "validation-example"),
                    request("/validation/push/status_dict", "valid-editor-session", "another-api"),
                    request("/validation/push/vehicle", "valid-editor-session", "validation-example"),
                    request("/validation/push/status_dict", null, "validation-example"),
                    request("/api/v1/data-push", "valid-editor-session", "validation-example")}) {
                var response = new MockHttpServletResponse();
                var denied = mock(FilterChain.class);
                filter.doFilter(rejected, response, denied);
                assertEquals(401, response.getStatus());
                verifyNoInteractions(denied);
            }
            var businessKey = request("/validation/push/status_dict", null, null);
            businessKey.addHeader("X-Api-Key", "any-business-key");
            var response = new MockHttpServletResponse();
            filter.doFilter(businessKey, response, mock(FilterChain.class));
            assertEquals(401, response.getStatus());
            verify(credentials, never()).authenticate("any-business-key");

            environment.setProperty("haitong.validation.enabled", "false");
            response = new MockHttpServletResponse();
            filter.doFilter(request("/validation/push/status_dict", "valid-editor-session", "validation-example"), response, mock(FilterChain.class));
            assertEquals(401, response.getStatus());
        }
    }

    private MockHttpServletRequest request(String path, String token, String script) {
        var request = new MockHttpServletRequest("POST", path);
        if (token != null) request.addHeader("Magic-Token", token);
        if (script != null) request.addHeader("Magic-Request-Script-Id", script);
        return request;
    }
}
