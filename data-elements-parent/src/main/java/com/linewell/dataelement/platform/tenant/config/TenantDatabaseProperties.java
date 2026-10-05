package com.linewell.dataelement.platform.tenant.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "data-element.tenant.database")
public class TenantDatabaseProperties {

    public static final String CONTROL_KEY = "control";

    private boolean enabled = true;
    private String defaultDataSourceKey = "public-security";
    private Map<String, String> tenantBindings = new LinkedHashMap<>();
    private Map<String, Definition> dataSources = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultDataSourceKey() {
        return defaultDataSourceKey;
    }

    public void setDefaultDataSourceKey(String defaultDataSourceKey) {
        this.defaultDataSourceKey = defaultDataSourceKey;
    }

    public Map<String, String> getTenantBindings() {
        return tenantBindings;
    }

    public void setTenantBindings(Map<String, String> tenantBindings) {
        this.tenantBindings = tenantBindings;
    }

    public Map<String, Definition> getDataSources() {
        return dataSources;
    }

    public void setDataSources(Map<String, Definition> dataSources) {
        this.dataSources = dataSources;
    }

    public String resolve(String tenantId) {
        if (!enabled) {
            return CONTROL_KEY;
        }
        if (tenantId == null || tenantId.isBlank()) {
            return defaultDataSourceKey;
        }
        return tenantBindings.getOrDefault(tenantId, defaultDataSourceKey);
    }

    public static class Definition {

        private String driverClassName;
        private String url;
        private String username;
        private String password;
        private String schema;

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }
    }
}
