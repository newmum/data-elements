package com.linewell.dataelement.platform.tenant.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.UseControlDataSource;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.entity.SymTenantT;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.mapper.SymTenantTMapper;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

/** Explicit per-tenant account source. Missing local storage never falls back to control accounts. */
@Service
@UseControlDataSource
public class TenantAccountPolicy {
    private final SymTenantTMapper tenantMapper;
    private final ObjectMapper json;
    private final TenantDatabaseProperties properties;
    private final TenantDataSourceRegistry registry;
    @Value("${idaas.legacy-control-accounts.enabled:false}")
    private boolean legacyControlAccountsEnabled;

    public TenantAccountPolicy(SymTenantTMapper tenantMapper,
            ObjectMapper json, TenantDatabaseProperties properties, TenantDataSourceRegistry registry) {
        this.tenantMapper = tenantMapper;
        this.json = json;
        this.properties = properties;
        this.registry = registry;
    }

    public boolean isLocal(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            if (legacyControlAccountsEnabled) return false;
            throw new TenantAccessException("TENANT-ID-MISSING", "请选择所属应用后使用本地账号登录");
        }
        SymTenantT tenant = tenantMapper.selectActiveAccountConfig(tenantId);
        if (tenant == null) throw new TenantAccessException("TENANT-NOT-AVAILABLE", "租户不可用");
        String config = tenant.getJsonConfig();
        try {
            JsonNode root = config == null || config.isBlank() ? json.createObjectNode() : json.readTree(config);
            String source = root.path("accountSource").asText("CONTROL");
            if ("CONTROL".equals(source)) {
                if (legacyControlAccountsEnabled) return false;
                throw new TenantAccessException("TENANT-ACCOUNT-SOURCE-RETIRED", "共享控制库账号已退役，请先为此应用配置本地账号及数据源");
            }
            if (!"LOCAL".equals(source)) throw new IllegalArgumentException("unknown account source");
            String key = properties.getTenantBindings().get(tenantId);
            if (!properties.isEnabled() || key == null || TenantDatabaseProperties.CONTROL_KEY.equals(key)
                    || !(registry.targets().get(key) instanceof DataSource)) {
                throw new IllegalArgumentException("local account datasource must be explicitly bound");
            }
            return true;
        } catch (TenantAccessException error) {
            throw error;
        } catch (Exception error) {
            throw new TenantAccessException("TENANT-ACCOUNT-CONFIG-INVALID", "租户账号来源配置无效，请检查本地数据源绑定");
        }
    }
}
