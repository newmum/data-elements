package com.linewell.dataelement.platform.integration.pingao.sso;

import com.linewell.dataelement.platform.web.security.AdditionalPublicEndpoints;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Public SSO handshake routes exist only while the Pingao integration is installed. */
@Component
public class PingaoPublicEndpoints implements AdditionalPublicEndpoints {
    @Override
    public Set<String> paths() {
        return Set.of("/identity/pingao/sso/status", "/identity/pingao/sso/login",
                "/identity/pingao/sso/callback", "/identity/pingao/sso/exchange");
    }
}
