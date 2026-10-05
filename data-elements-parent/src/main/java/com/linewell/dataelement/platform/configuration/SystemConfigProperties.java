package com.linewell.dataelement.platform.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "data-element.system-config")
public class SystemConfigProperties {

    private boolean cacheEnabled = true;
    private Duration cacheTtl = Duration.ofMinutes(30);
    private String redisKeyPrefix = "data-elements:system-config:v1";
    private String globalTenantId = "GLOBAL";
    private boolean uiComponentCacheEnabled = true;
    private Duration uiComponentCacheTtl = Duration.ofMinutes(15);
    private String uiComponentRedisKey = "data-elements:ui-components:v1:active";
    private String uiComponentVersionRedisKey = "data-elements:ui-components:v1:version";

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(Duration cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }

    public String getGlobalTenantId() {
        return globalTenantId;
    }

    public void setGlobalTenantId(String globalTenantId) {
        this.globalTenantId = globalTenantId;
    }

    public boolean isUiComponentCacheEnabled() {
        return uiComponentCacheEnabled;
    }

    public void setUiComponentCacheEnabled(boolean uiComponentCacheEnabled) {
        this.uiComponentCacheEnabled = uiComponentCacheEnabled;
    }

    public Duration getUiComponentCacheTtl() {
        return uiComponentCacheTtl;
    }

    public void setUiComponentCacheTtl(Duration uiComponentCacheTtl) {
        this.uiComponentCacheTtl = uiComponentCacheTtl;
    }

    public String getUiComponentRedisKey() {
        return uiComponentRedisKey;
    }

    public void setUiComponentRedisKey(String uiComponentRedisKey) {
        this.uiComponentRedisKey = uiComponentRedisKey;
    }

    public String getUiComponentVersionRedisKey() {
        return uiComponentVersionRedisKey;
    }

    public void setUiComponentVersionRedisKey(String uiComponentVersionRedisKey) {
        this.uiComponentVersionRedisKey = uiComponentVersionRedisKey;
    }
}
