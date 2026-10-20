package com.linewell.dataelement.platform.integration.pingao;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Private-network configuration for the Pingao application catalogue. */
@Component
@ConfigurationProperties(prefix = "data-element.pingao")
public class PingaoApplicationProperties {

    private boolean enabled;
    private String tokenUrl = "";
    private String applicationUrl = "";
    private String clientId = "";
    private String clientSecret = "";
    private int pageSize = 100;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(30);
    private Duration tokenRefreshSkew = Duration.ofSeconds(60);
    private Duration syncLockTtl = Duration.ofMinutes(30);
    private String syncLockKey = "data-elements:pingao:application-sync";
    private boolean enforceManualSyncPermission = false;
    private String manualSyncPermission = "datasource:manage";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getTokenUrl() { return tokenUrl; }
    public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
    public String getApplicationUrl() { return applicationUrl; }
    public void setApplicationUrl(String applicationUrl) { this.applicationUrl = applicationUrl; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public int getPageSize() { return Math.min(Math.max(pageSize, 1), 100); }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public Duration getTokenRefreshSkew() { return tokenRefreshSkew; }
    public void setTokenRefreshSkew(Duration tokenRefreshSkew) { this.tokenRefreshSkew = tokenRefreshSkew; }
    public Duration getSyncLockTtl() { return syncLockTtl; }
    public void setSyncLockTtl(Duration syncLockTtl) { this.syncLockTtl = syncLockTtl; }
    public String getSyncLockKey() { return syncLockKey; }
    public void setSyncLockKey(String syncLockKey) { this.syncLockKey = syncLockKey; }
    public boolean isEnforceManualSyncPermission() { return enforceManualSyncPermission; }
    public void setEnforceManualSyncPermission(boolean enforceManualSyncPermission) {
        this.enforceManualSyncPermission = enforceManualSyncPermission;
    }
    public String getManualSyncPermission() { return manualSyncPermission; }
    public void setManualSyncPermission(String manualSyncPermission) { this.manualSyncPermission = manualSyncPermission; }

    public boolean isConfigured() {
        return !tokenUrl.isBlank() && !applicationUrl.isBlank()
                && !clientId.isBlank() && !clientSecret.isBlank();
    }
}
