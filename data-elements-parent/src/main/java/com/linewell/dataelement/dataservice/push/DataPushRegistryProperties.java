package com.linewell.dataelement.dataservice.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "data-element.push-registry")
public class DataPushRegistryProperties {
    /** Empty means the internal endpoint is deliberately unavailable. */
    private String sharedKey = "";
    /** Configured per environment; never bake the relay address into platform code. */
    private String relayBaseUrl = "";
    /** Separate credential for the platform-to-relay standalone contract mirror. */
    private String standaloneSyncKey = "";
    private int relayTimeoutSeconds = 8;
    public String getSharedKey() { return sharedKey; }
    public void setSharedKey(String sharedKey) { this.sharedKey = sharedKey == null ? "" : sharedKey.trim(); }
    public String getRelayBaseUrl() { return relayBaseUrl; }
    public void setRelayBaseUrl(String relayBaseUrl) { this.relayBaseUrl = relayBaseUrl == null ? "" : relayBaseUrl.trim(); }
    public String getStandaloneSyncKey() { return standaloneSyncKey; }
    public void setStandaloneSyncKey(String standaloneSyncKey) { this.standaloneSyncKey = standaloneSyncKey == null ? "" : standaloneSyncKey.trim(); }
    public int getRelayTimeoutSeconds() { return relayTimeoutSeconds; }
    public void setRelayTimeoutSeconds(int relayTimeoutSeconds) { this.relayTimeoutSeconds = relayTimeoutSeconds; }
}
