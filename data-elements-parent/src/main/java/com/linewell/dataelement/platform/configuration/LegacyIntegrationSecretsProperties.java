package com.linewell.dataelement.platform.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/** Explicit secrets for retained legacy SSO and MinIO Magic integrations. */
@Component
@RefreshScope
@ConfigurationProperties(prefix = "data-element.legacy-integration")
public class LegacyIntegrationSecretsProperties {

    private String ssoClientSecret = "";
    private String minioPassword = "";

    public String getSsoClientSecret() {
        return ssoClientSecret;
    }

    public void setSsoClientSecret(String ssoClientSecret) {
        this.ssoClientSecret = ssoClientSecret;
    }

    public String getMinioPassword() {
        return minioPassword;
    }

    public void setMinioPassword(String minioPassword) {
        this.minioPassword = minioPassword;
    }
}
