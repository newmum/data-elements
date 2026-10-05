package com.linewell.dataelement.platform.tenant.application;

import com.linewell.dataelement.platform.tenant.infrastructure.datasource.UseControlDataSource;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.entity.SymTenantT;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.mapper.SymTenantTMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Supplies active tenant scopes to background schedulers and workers.
 */
@Service
@UseControlDataSource
public class TenantExecutionCatalog {

    private final SymTenantTMapper tenantMapper;

    public TenantExecutionCatalog(SymTenantTMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    public List<String> activeTenantIds() {
        return tenantMapper.selectLoginOptions().stream()
                .map(SymTenantT::getTid)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }
}
