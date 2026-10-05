package com.linewell.dataelement.platform.tenant.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.junit.jupiter.api.AfterEach;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import cn.dev33.satoken.session.SaSession;
import com.linewell.dataelement.feature.identity.application.TenantIdentityQueryService;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import java.util.Map;

class TenantWebInterceptorTest {

    @Test
    void oldControlTokenCannotEnterLocalTenantDespiteSameUserId() {
        var access=mock(TenantAccessService.class);var accounts=mock(TenantIdentityQueryService.class);
        var interceptor=new TenantWebInterceptor(access,new TenantProperties(),accounts);
        var token=new SaSession("unit-token");token.set("tenantId","ga");
        when(accounts.isLocal("ga")).thenReturn(true);
        try(var session=mockStatic(StpUtil.class)) {
            session.when(StpUtil::isLogin).thenReturn(true);session.when(StpUtil::getTokenSession).thenReturn(token);
            assertThrows(TenantAccessException.class,()->interceptor.preHandle(new MockHttpServletRequest("POST","/sym/user/page"),new MockHttpServletResponse(),new Object()));
            verify(accounts,never()).find(anyString(),anyString());verifyNoInteractions(access);
        }
    }

    @Test
    void localSessionWithoutTenantNeverAutoBindsControlMembership() {
        var access=mock(TenantAccessService.class);var accounts=mock(TenantIdentityQueryService.class);
        var interceptor=new TenantWebInterceptor(access,new TenantProperties(),accounts);
        var token=new SaSession("unit-token");token.set("authRealm","tenant-local-v1");
        try(var session=mockStatic(StpUtil.class)) {
            session.when(StpUtil::isLogin).thenReturn(true);session.when(StpUtil::getTokenSession).thenReturn(token);
            assertThrows(TenantAccessException.class,()->interceptor.preHandle(new MockHttpServletRequest("POST","/sym/user/page"),new MockHttpServletResponse(),new Object()));
            verifyNoInteractions(access,accounts);
        }
    }

    @Test
    void disabledLocalAccountCannotUseExistingToken() {
        var access=mock(TenantAccessService.class);var accounts=mock(TenantIdentityQueryService.class);
        var interceptor=new TenantWebInterceptor(access,new TenantProperties(),accounts);
        var token=new SaSession("unit-token");token.set("authRealm","tenant-local-v1").set("tenantId","ga");
        when(accounts.isLocal("ga")).thenReturn(true);when(accounts.find("ga","user")).thenReturn(Map.of("status",1));
        try(var session=mockStatic(StpUtil.class)) {
            session.when(StpUtil::isLogin).thenReturn(true);session.when(StpUtil::getTokenSession).thenReturn(token);
            session.when(StpUtil::getLoginId).thenReturn("user");
            assertThrows(TenantAccessException.class,()->interceptor.preHandle(new MockHttpServletRequest("POST","/sym/user/page"),new MockHttpServletResponse(),new Object()));
            verifyNoInteractions(access);
        }
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void extractsTenantFromPublishedRoute() {
        assertEquals(
                "10000000000000000000000000000002",
                TenantWebInterceptor.publishedTenantId(
                        "/dws/flowserve/published/__tenant/"
                                + "10000000000000000000000000000002/weather/latest"));
    }

    @Test
    void leavesLegacyPublishedRouteOnDefaultTenant() {
        assertNull(TenantWebInterceptor.publishedTenantId(
                "/dws/flowserve/published/weather/latest"));
    }

    @Test
    void publicLoginEndpointsIgnoreStaleSessionAndNeverResolveMembership() {
        TenantAccessService access = mock(TenantAccessService.class);
        TenantWebInterceptor interceptor = new TenantWebInterceptor(access, new TenantProperties(), mock(com.linewell.dataelement.feature.identity.application.TenantIdentityQueryService.class));
        try (var session = mockStatic(StpUtil.class)) {
            session.when(StpUtil::isLogin).thenThrow(new IllegalStateException("stale session"));
            for (String path : new String[] {"/portal/login", "/sym/tenant/login-options", "/sym/tenant/login-settings"}) {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
                request.addHeader("token", "obsolete-test-token");
                TenantContext.bind("previous-request");
                assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
                assertTrue(TenantContext.usesControlDatabase());
                assertNull(TenantContext.getTenantId());
                interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);
                assertNull(TenantContext.getTenantId());
            }
            session.verifyNoInteractions();
            verifyNoInteractions(access);
        }
    }

    @Test
    void identityLoginIgnoresOldTokenAndNeverAutoBindsTenant() {
        TenantAccessService access = mock(TenantAccessService.class);
        TenantWebInterceptor interceptor = new TenantWebInterceptor(access, new TenantProperties(), mock(com.linewell.dataelement.feature.identity.application.TenantIdentityQueryService.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/idaas/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("token", "obsolete-test-token");
        TenantContext.bind("previous-request");
        try (var session = mockStatic(StpUtil.class)) {
            session.when(StpUtil::isLogin).thenReturn(true);
            assertTrue(interceptor.preHandle(request, response, new Object()));
            assertTrue(TenantContext.usesControlDatabase());
            assertNull(TenantContext.getTenantId());
            session.verifyNoInteractions();
            verifyNoInteractions(access);
            interceptor.afterCompletion(request, response, new Object(), null);
            assertNull(TenantContext.getTenantId());
            assertTrue(!TenantContext.usesControlDatabase());
        }
    }
}
