package com.linewell.dataelement.platform.tenant.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.linewell.dataelement.magic.runtime.route.RegisteredMagicFunctionInvoker;
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
    private final RegisteredMagicFunctionInvoker functions = mock(RegisteredMagicFunctionInvoker.class);

    @Test
    void asyncHandlingReleasesTheOriginalRequestThreadTenant() {
        var interceptor = new TenantWebInterceptor(mock(TenantAccessService.class), new TenantProperties(),
                mock(TenantIdentityQueryService.class), functions);
        TenantContext.use("industry-validation");
        interceptor.afterConcurrentHandlingStarted(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void oldControlTokenCannotEnterLocalTenantDespiteSameUserId() {
        var access=mock(TenantAccessService.class);var accounts=mock(TenantIdentityQueryService.class);
        var interceptor=new TenantWebInterceptor(access,new TenantProperties(),accounts,functions);
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
        var interceptor=new TenantWebInterceptor(access,new TenantProperties(),accounts,functions);
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
        var interceptor=new TenantWebInterceptor(access,new TenantProperties(),accounts,functions);
        var token=new SaSession("unit-token");token.set("authRealm","tenant-local-v1").set("tenantId","ga");
        when(accounts.isLocal("ga")).thenReturn(true);when(accounts.find("ga","user")).thenReturn(Map.of("status",1));
        try(var session=mockStatic(StpUtil.class)) {
            session.when(StpUtil::isLogin).thenReturn(true);session.when(StpUtil::getTokenSession).thenReturn(token);
            session.when(StpUtil::getLoginId).thenReturn("user");
            assertThrows(TenantAccessException.class,()->interceptor.preHandle(new MockHttpServletRequest("POST","/sym/user/page"),new MockHttpServletResponse(),new Object()));
            verifyNoInteractions(access);
            verifyNoInteractions(functions);
        }
    }

    @Test
    void activeLocalAccountRequiresRegisteredDecisionUnderItsSessionTenant() {
        var accounts = mock(TenantIdentityQueryService.class);
        var interceptor = new TenantWebInterceptor(mock(TenantAccessService.class), new TenantProperties(), accounts, functions);
        var token = new SaSession("unit-token").set("authRealm", "tenant-local-v1").set("tenantId", "ga");
        when(accounts.isLocal("ga")).thenReturn(true);
        when(accounts.find("ga", "user")).thenReturn(Map.of("status", 0));
        var input = Map.<String, Object>of("userId", "user");
        String checkId = "275494036ca45d1ea4eb856c7a5bef94";
        when(functions.invoke(checkId, input)).thenAnswer(invocation -> {
            assertEquals("ga", TenantContext.getTenantId());
            return Map.of("allowed", true);
        }).thenReturn(Map.of("allowed", false), null);
        var request = new MockHttpServletRequest("POST", "/sym/user/page");
        request.addParameter("tenantId", "foreign");
        var response = new MockHttpServletResponse();
        try (var session = mockStatic(StpUtil.class)) {
            session.when(StpUtil::isLogin).thenReturn(true);
            session.when(StpUtil::getTokenSession).thenReturn(token);
            session.when(StpUtil::getLoginId).thenReturn("user");
            assertTrue(interceptor.preHandle(request, response, new Object()));
            for (int index = 0; index < 2; index++) {
                var denied = assertThrows(TenantAccessException.class, () -> interceptor.preHandle(request, response, new Object()));
                assertEquals("TENANT-CENTRAL-SOURCE-REVOKED", denied.getCode());
            }
        }
        verify(functions, times(3)).invoke(checkId, input);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void identityLikePathWithoutAVerifiedResourceDoesNotSelectTheControlDatabase() {
        TenantAccessService access = mock(TenantAccessService.class);
        var properties = new TenantProperties();
        properties.setEnabled(false);
        TenantWebInterceptor interceptor = new TenantWebInterceptor(access, properties, mock(TenantIdentityQueryService.class), functions);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/idaas/legacy/validation");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("token", "obsolete-test-token");
        TenantContext.bind("previous-request");
        try (var session = mockStatic(StpUtil.class)) {
            session.when(StpUtil::isLogin).thenReturn(true);
            assertTrue(interceptor.preHandle(request, response, new Object()));
            assertTrue(!TenantContext.usesControlDatabase());
            assertNull(TenantContext.getTenantId());
            session.verifyNoInteractions();
            verifyNoInteractions(access);
            interceptor.afterCompletion(request, response, new Object(), null);
            assertNull(TenantContext.getTenantId());
            assertTrue(!TenantContext.usesControlDatabase());
        }
    }
}
