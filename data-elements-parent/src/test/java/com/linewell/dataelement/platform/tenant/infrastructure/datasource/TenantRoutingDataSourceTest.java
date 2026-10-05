package com.linewell.dataelement.platform.tenant.infrastructure.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantRoutingDataSourceTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void routesControlSharedAndDedicatedTenants() {
        TenantDatabaseProperties properties = new TenantDatabaseProperties();
        properties.setDefaultDataSourceKey("public-security");
        properties.setTenantBindings(Map.of(
                "police", "public-security",
                "broadcast", "broadcasting"
        ));
        TenantRoutingDataSource routing = new TenantRoutingDataSource(properties);

        assertEquals("public-security",
                routing.determineCurrentLookupKey());
        try (TenantContext.Scope ignored = TenantContext.use("police")) {
            assertEquals("public-security", routing.determineCurrentLookupKey());
        }
        try (TenantContext.Scope ignored = TenantContext.use("broadcast")) {
            assertEquals("broadcasting", routing.determineCurrentLookupKey());
            try (TenantContext.Scope control = TenantContext.control()) {
                assertEquals(TenantDatabaseProperties.CONTROL_KEY,
                        routing.determineCurrentLookupKey());
            }
        }
    }
}
