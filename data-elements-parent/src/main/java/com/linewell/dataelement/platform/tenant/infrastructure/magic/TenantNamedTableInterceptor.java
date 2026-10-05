package com.linewell.dataelement.platform.tenant.infrastructure.magic;

import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.TenantTableRegistry;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.modules.db.inteceptor.NamedTableInterceptor;
import org.ssssssss.magicapi.modules.db.model.SqlMode;
import org.ssssssss.magicapi.modules.db.table.NamedTable;

@Component
public class TenantNamedTableInterceptor implements NamedTableInterceptor {

    private final TenantProperties properties;
    private final TenantTableRegistry tableRegistry;

    public TenantNamedTableInterceptor(
            TenantProperties properties,
            TenantTableRegistry tableRegistry
    ) {
        this.properties = properties;
        this.tableRegistry = tableRegistry;
    }

    @Override
    public void preHandle(SqlMode sqlMode, NamedTable namedTable) {
        if (sqlMode != SqlMode.INSERT
                || !properties.isEnabled()
                || TenantContext.isIgnored()
                || TenantContext.getTenantId() == null
                || properties.ignores(namedTable.getTableName())
                || !tableRegistry.hasTenantColumn(namedTable.getTableName())) {
            return;
        }
        String tenantId = TenantContext.requireTenantId();
        // Request payload tenant values are never authoritative. Overwriting the
        // column keeps legacy/stale clients compatible without permitting a
        // cross-tenant insert.
        namedTable.column("tenant_id", tenantId);
    }
}
