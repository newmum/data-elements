package com.linewell.dataelement.platform.tenant.infrastructure.datasource;

import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final TenantDatabaseProperties properties;

    public TenantRoutingDataSource(TenantDatabaseProperties properties) {
        this.properties = properties;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        if (TenantContext.usesControlDatabase()) {
            return TenantDatabaseProperties.CONTROL_KEY;
        }
        return properties.resolve(TenantContext.getTenantId());
    }
}
