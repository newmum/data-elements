package com.linewell.dataelement.platform.integration.pingao.sso;

import java.net.URI;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "data-element.pingao-sso")
public class PingaoSsoProperties {
    private boolean enabled;
    private boolean identityContractConfirmed;
    private String authorizeUrl = "";
    private String tokenUrl = "";
    private String userinfoUrl = "";
    private String logoutUrl = "";
    private String callbackUrl = "";
    private String loggedOutUrl = "";
    private String frontendUrl = "";
    private String clientId = "";
    private String clientSecret = "";
    private String localTenantId = "";
    private String externalTenantCode = "";
    private String localAppId = "";
    private Integer localActiveAccountStatus;
    // Live joint-test evidence confirms userinfo.uid == userinfo.sub == directory userId.
    // Keep the contract confirmation switch so this default cannot turn SSO on by itself.
    private String directoryUserIdClaim = "uid";
    private boolean allowPrivateHttp;
    private Duration timeout = Duration.ofSeconds(15);

    public void requireConfigured() {
        if (!enabled) throw new IllegalStateException("警综 SSO 未启用");
        if (!identityContractConfirmed) throw new IllegalStateException("警综身份标识契约尚未确认");
        if (!("uid".equals(directoryUserIdClaim))
                || clientId.isBlank() || clientSecret.isBlank() || localTenantId.isBlank()
                || externalTenantCode.isBlank() || localAppId.isBlank() || localActiveAccountStatus == null
                || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException("警综 SSO 配置不完整");
        }
        for (String value : new String[]{authorizeUrl, tokenUrl, userinfoUrl, logoutUrl, callbackUrl, loggedOutUrl, frontendUrl}) {
            validateUrl(value);
        }
    }

    private void validateUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!("https".equals(uri.getScheme()) || (allowPrivateHttp && "http".equals(uri.getScheme())))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("警综 SSO 地址必须为完整且不带查询参数或片段的受信地址");
        }
    }
}
