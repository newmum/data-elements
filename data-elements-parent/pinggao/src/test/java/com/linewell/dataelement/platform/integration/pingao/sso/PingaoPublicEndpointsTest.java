package com.linewell.dataelement.platform.integration.pingao.sso;

import static org.assertj.core.api.Assertions.assertThat;
import com.linewell.dataelement.platform.web.security.WhiteListProp;
import java.util.List;
import org.junit.jupiter.api.Test;

class PingaoPublicEndpointsTest {
    @Test
    void installingIntegrationOnlyExposesItsFourExistingHandshakeRoutes() {
        var whitelist = new WhiteListProp();
        assertThat(whitelist.isWhiteListed("/identity/pingao/sso/login")).isFalse();
        var endpoints = new PingaoPublicEndpoints();
        whitelist.setAdditionalPublicEndpoints(List.of(endpoints));
        assertThat(endpoints.paths()).hasSize(4).allSatisfy(path -> assertThat(whitelist.isWhiteListed(path)).isTrue());
        assertThat(whitelist.isWhiteListed("/identity/pingao/sso/login/anything")).isFalse();
        assertThat(whitelist.isWhiteListed("/identity/pingao/directory/sync")).isFalse();
    }
}
