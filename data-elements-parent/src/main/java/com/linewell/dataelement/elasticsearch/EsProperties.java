package com.linewell.dataelement.elasticsearch;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "es")
public class EsProperties {
    /**
     * 索引前缀（多环境隔离用），例如：dev_ / test_ / prod_
     * 为空则不加前缀。
     */
    private String prefix = "";
    private Init init = new Init();
    private Tenant tenant = new Tenant();

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Init getInit() {
        return init;
    }

    public void setInit(Init init) {
        this.init = init;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public static class Init {
        private boolean enabled = false;
        private List<String> indices = new ArrayList<>();
        private List<String> keywordStringIndices = new ArrayList<>(List.of("dataassets"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getIndices() {
            return indices;
        }

        public void setIndices(List<String> indices) {
            this.indices = indices;
        }

        /**
         * Projection indices whose dynamically discovered string fields must be
         * mapped as keyword so they remain sortable and aggregatable.
         */
        public List<String> getKeywordStringIndices() {
            return keywordStringIndices;
        }

        public void setKeywordStringIndices(List<String> keywordStringIndices) {
            this.keywordStringIndices = keywordStringIndices;
        }
    }

    public static class Tenant {
        private boolean enabled = true;
        private boolean defaultTenantUsesLegacyIndex = true;
        private String separator = "__tenant_";
        private List<String> globalIndices = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isDefaultTenantUsesLegacyIndex() {
            return defaultTenantUsesLegacyIndex;
        }

        public void setDefaultTenantUsesLegacyIndex(boolean defaultTenantUsesLegacyIndex) {
            this.defaultTenantUsesLegacyIndex = defaultTenantUsesLegacyIndex;
        }

        public String getSeparator() {
            return separator;
        }

        public void setSeparator(String separator) {
            this.separator = separator;
        }

        public List<String> getGlobalIndices() {
            return globalIndices;
        }

        public void setGlobalIndices(List<String> globalIndices) {
            this.globalIndices = globalIndices;
        }
    }
}


