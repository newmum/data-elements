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

        assertTrue(properties.isWhiteListed("/portal/index"));
        assertTrue(properties.isWhiteListed("/sym/tenant/login-options"));
        assertTrue(properties.isWhiteListed("/public/demo"));
        assertFalse(properties.isWhiteListed("/private/demo"));
    }

    @Test
    void handlesMissingExternalWhitelist() {
        WhiteListProp properties = new WhiteListProp();

        assertTrue(properties.isWhiteListed("/portal/index"));
        assertFalse(properties.isWhiteListed("/private/demo"));
    }

    @Test
    void identityLoginIsPublicWithoutOpeningBusinessApis() {
        WhiteListProp properties = new WhiteListProp();
        assertTrue(properties.isWhiteListed("/idaas/auth/login"));
        assertTrue(properties.isWhiteListed("/idaas/password-recovery/request"));
        assertFalse(properties.isWhiteListed("/idaas/password-recovery/requests"));
        assertFalse(properties.isWhiteListed("/idaas/auth/session"));
        assertFalse(properties.isWhiteListed("/idaas/users/save"));
        assertFalse(properties.isWhiteListed("/idaas/auth/login/anything"));
    }

    @Test
    void canvasBusinessApisFollowConfiguredWhitelist() {
        WhiteListProp properties = new WhiteListProp();
        properties.setWhiteList(List.of("/portal/**", "/public/**"));
        assertFalse(properties.isWhiteListed("/nifi/api/pipelines"));
        assertFalse(properties.isWhiteListed("/nifi/api/pipelines/123"));
        assertFalse(properties.isWhiteListed("/nifi/api/pipelines/123/deploy"));
        assertTrue(properties.isWhiteListed("/portal/login"));

        properties.setWhiteList(List.of("/nifi/**"));
        assertTrue(properties.isWhiteListed("/nifi/api/pipelines"));
    }
}
