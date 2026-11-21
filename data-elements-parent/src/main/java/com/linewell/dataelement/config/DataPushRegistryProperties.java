package com.linewell.dataelement.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "data-element.push-registry")
public class DataPushRegistryProperties {
    /** Empty means the internal endpoint is deliberately unavailable. */
    private String sharedKey = "";
    /** Configured per environment; never bake the relay address into platform code. */
    private String relayBaseUrl = "";
    private String registrySyncPath = "";
    private String credentialIssuePath = "";
    /** Separate credential for the platform-to-relay standalone contract mirror. */
    private String standaloneSyncKey = "";
    private int relayTimeoutSeconds = 8;
    public String getSharedKey() { return sharedKey; }
    public void setSharedKey(String sharedKey) { this.sharedKey = sharedKey == null ? "" : sharedKey.trim(); }
    public String getRelayBaseUrl() { return relayBaseUrl; }
    public void setRelayBaseUrl(String relayBaseUrl) { this.relayBaseUrl = relayBaseUrl == null ? "" : relayBaseUrl.trim(); }
    public String getRegistrySyncPath() { return registrySyncPath; }
    public void setRegistrySyncPath(String path) { this.registrySyncPath = path == null ? "" : path.trim(); }
    public String getCredentialIssuePath() { return credentialIssuePath; }
    public void setCredentialIssuePath(String path) { this.credentialIssuePath = path == null ? "" : path.trim(); }
    public String getStandaloneSyncKey() { return standaloneSyncKey; }
    public void setStandaloneSyncKey(String standaloneSyncKey) { this.standaloneSyncKey = standaloneSyncKey == null ? "" : standaloneSyncKey.trim(); }
    public int getRelayTimeoutSeconds() { return relayTimeoutSeconds; }
    public void setRelayTimeoutSeconds(int relayTimeoutSeconds) { this.relayTimeoutSeconds = relayTimeoutSeconds; }
}
