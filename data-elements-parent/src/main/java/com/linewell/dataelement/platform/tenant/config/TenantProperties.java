package com.linewell.dataelement.platform.tenant.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "data-element.tenant")
public class TenantProperties {

    public static final String DEFAULT_TENANT_ID = "00000000000000000000000000000000";

    private boolean enabled = true;
    private boolean compatibilityAutoBind = true;
    private String defaultTenantId = DEFAULT_TENANT_ID;
    private Set<String> ignoredTables = defaultIgnoredTables();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCompatibilityAutoBind() {
        return compatibilityAutoBind;
    }

    public void setCompatibilityAutoBind(boolean compatibilityAutoBind) {
        this.compatibilityAutoBind = compatibilityAutoBind;
    }

    public String getDefaultTenantId() {
        return defaultTenantId;
    }

    public void setDefaultTenantId(String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }

    public Set<String> getIgnoredTables() {
        return ignoredTables;
    }

    public void setIgnoredTables(Set<String> ignoredTables) {
        this.ignoredTables = ignoredTables;
    }

    public boolean ignores(String tableName) {
        if (tableName == null) {
            return true;
        }
        String cleaned = tableName.replace("`", "").replace("\"", "");
        int dot = cleaned.lastIndexOf('.');
        if (dot >= 0) {
            cleaned = cleaned.substring(dot + 1);
        }
        String normalized = cleaned;
        return ignoredTables.stream().anyMatch(value -> value.equalsIgnoreCase(normalized));
    }

    private static Set<String> defaultIgnoredTables() {
        return new LinkedHashSet<>(Set.of(
                "sym_tenant_t",
                "sym_platform_app_t",
                "rm_user_t",
                "rm_user_tenant_rela_t",
                "api_file_t",
                "api_backup_t",
                "ui_component_t",
                "ui_component_history_t",
                "sym_config_t",
                "sys_oper_log",
                "flyway_schema_history"
        ));
    }
}
