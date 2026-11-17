package com.linewell.dataelement.platform.web.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WhiteListPropTest {

    @Test
    void includesFrameworkPublicEndpointsAndConfiguredPatterns() {
        WhiteListProp properties = new WhiteListProp();
        properties.setWhiteList(List.of("/public/**"));

        assertTrue(properties.isWhiteListed("/portal/sso/ticket"));
        assertFalse(properties.isWhiteListed("/portal/sso/options"));
        assertTrue(properties.isWhiteListed("/public/demo"));
        assertFalse(properties.isWhiteListed("/private/demo"));
    }

    @Test
    void handlesMissingExternalWhitelist() {
        WhiteListProp properties = new WhiteListProp();

        assertTrue(properties.isWhiteListed("/portal/sso/redeem"));
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
        assertFalse(properties.isWhiteListed("/idaas/auth/login"));
    }

    @Test
    void identityRecoveryKeepsItsExistingLegacyAccess() {
        WhiteListProp properties = new WhiteListProp();
        assertTrue(properties.isWhiteListed("/idaas/recovery/request"));
        assertFalse(properties.isWhiteListed("/idaas/recovery/requests"));
        assertFalse(properties.isWhiteListed("/idaas/auth/session"));
        assertFalse(properties.isWhiteListed("/idaas/users/save"));
        assertFalse(properties.isWhiteListed("/idaas/auth/login/anything"));
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
