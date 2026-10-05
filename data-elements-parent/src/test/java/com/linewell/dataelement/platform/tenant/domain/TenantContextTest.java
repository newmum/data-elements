package com.linewell.dataelement.platform.tenant.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void restoresNestedScopes() {
        try (TenantContext.Scope outer = TenantContext.use("tenant-a")) {
            assertEquals("tenant-a", TenantContext.requireTenantId());
            try (TenantContext.Scope inner = TenantContext.use("tenant-b")) {
                assertEquals("tenant-b", TenantContext.requireTenantId());
            }
            assertEquals("tenant-a", TenantContext.requireTenantId());
        }
        assertNull(TenantContext.getTenantId());
    }

    @Test
    void rejectsUnsafeTenantIds() {
        assertThrows(
                TenantAccessException.class,
                () -> TenantContext.use("' or 1=1 --")
        );
    }

    @Test
    void controlScopeTemporarilyOverridesTenantRouting() {
        assertFalse(TenantContext.usesControlDatabase());
        try (TenantContext.Scope tenant = TenantContext.use("tenant-a")) {
            assertFalse(TenantContext.usesControlDatabase());
            try (TenantContext.Scope control = TenantContext.control()) {
                assertTrue(TenantContext.usesControlDatabase());
                assertEquals("tenant-a", TenantContext.requireTenantId());
            }
            assertFalse(TenantContext.usesControlDatabase());
        }
        assertFalse(TenantContext.usesControlDatabase());
    }

    @Test
    void bindReplacesControlRequestContextWithAuthenticatedTenant() {
        try (TenantContext.Scope ignored = TenantContext.control()) {
            assertTrue(TenantContext.usesControlDatabase());
            TenantContext.bind("tenant-login");
            assertEquals("tenant-login", TenantContext.requireTenantId());
            assertFalse(TenantContext.usesControlDatabase());
        }
    }
}
