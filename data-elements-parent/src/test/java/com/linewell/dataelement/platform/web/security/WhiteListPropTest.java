package com.linewell.dataelement.platform.web.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WhiteListPropTest {

    @Test
    void nodeProtocolDoesNotUseConfiguredWildcard() {
        WhiteListProp properties = new WhiteListProp();
        properties.setFlowServeFallbackPattern("/api/v1/runtime/**");
        properties.setWhiteList(List.of("/api/v1/runtime/**", "/public/**"));
        assertFalse(properties.isWhiteListed("/api/v1/runtime/health"));
        assertFalse(properties.isWhiteListed("/api/v1/runtime/unknown"));
        assertTrue(properties.isWhiteListed("/public/example"));
    }

    @Test
    void keepsAutomaticProtocolPrefixExemptionsOutOfTheBusinessWhitelist() {
        WhiteListProp properties = new WhiteListProp();
        properties.setWhiteList(List.of("/public/**"));

        assertFalse(properties.isWhiteListed("/portal/sso/ticket"));
        assertFalse(properties.isWhiteListed("/portal/sso/options"));
        assertFalse(properties.isWhiteListed("/idaas/oauth2/consent"));
        assertFalse(properties.isWhiteListed("/idaas/oauth2/unregistered"));
        assertFalse(properties.isWhiteListed("/idaas/connect/userinfo"));
        assertFalse(properties.isWhiteListed("/.well-known/openid-configuration"));
        assertFalse(properties.isWhiteListed("/.well-known/oauth-authorization-server/extra"));
        assertTrue(properties.isWhiteListed("/public/demo"));
        assertFalse(properties.isWhiteListed("/private/demo"));
    }

    @Test
    void handlesMissingExternalWhitelist() {
        WhiteListProp properties = new WhiteListProp();

        assertFalse(properties.isWhiteListed("/portal/sso/redeem"));
        assertFalse(properties.isWhiteListed("/private/demo"));
    }

    @Test
    void policyManagedRoutesDoNotNeedExactLegacyEntries() {
        WhiteListProp properties = new WhiteListProp();
        properties.setWhiteList(List.of("/portal/**"));
        assertFalse(properties.isWhiteListed("/portal/main/index"));
        assertFalse(properties.isWhiteListed("/idaas/auth/settings"));
        assertFalse(properties.isWhiteListed("/portal/sso/options"));
        assertFalse(properties.isWhiteListed("/portal/sso/settings"));
        assertFalse(properties.isWhiteListed("/portal/sso/login"));
        assertFalse(properties.isWhiteListed("/portal/sso/logout"));
        assertFalse(properties.isWhiteListed("/portal/profile/password"));
        assertFalse(properties.isWhiteListed("/idaas/receiver/admin"));
        assertFalse(properties.isWhiteListed("/idaas/api/whoami"));
        assertFalse(properties.isWhiteListed("/idaas/receiver/v2"));
        assertFalse(properties.isWhiteListed("/idaas/auth/login"));
    }

    @Test
    void identityAccountAndRecoveryUseResourceAccessInsteadOfLegacyPaths() {
        WhiteListProp properties = new WhiteListProp();
        assertFalse(properties.isWhiteListed("/idaas/recovery/request"));
        assertFalse(properties.isWhiteListed("/idaas/recovery/requests"));
        assertFalse(properties.isWhiteListed("/idaas/auth/session"));
        assertFalse(properties.isWhiteListed("/idaas/users/save"));
        assertFalse(properties.isWhiteListed("/idaas/auth/login/anything"));
        assertFalse(properties.isWhiteListed("/idaas/identity/login"));
        assertFalse(properties.isWhiteListed("/idaas/identity/me"));
        assertFalse(properties.isWhiteListed("/idaas/verifications/submit"));
        assertFalse(properties.isWhiteListed("/idaas/mfa/status"));
        assertFalse(properties.isWhiteListed("/idaas/mfa/verify"));
    }

    @Test
    void canvasBusinessApisFollowConfiguredWhitelist() {
        WhiteListProp properties = new WhiteListProp();
        properties.setWhiteList(List.of("/portal/**", "/public/**"));
        assertFalse(properties.isWhiteListed("/nifi/flow/list"));
        assertFalse(properties.isWhiteListed("/nifi/flow/detail"));
        assertFalse(properties.isWhiteListed("/nifi/life/deploy"));
        assertFalse(properties.isWhiteListed("/portal/sso/session"));
        assertFalse(properties.isWhiteListed("/portal/sso/tenants"));
        assertFalse(properties.isWhiteListed("/portal/sso/switch"));

        properties.setWhiteList(List.of("/nifi/**"));
        assertTrue(properties.isWhiteListed("/nifi/flow/list"));
    }
}
