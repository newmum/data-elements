package com.linewell.dataelement.platform.tenant.infrastructure.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.TenantTableRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.model.SqlMode;
import org.ssssssss.magicapi.modules.db.table.NamedTable;

class TenantNamedTableInterceptorTest {

    @Test
    void overwritesSubmittedTenantWithCurrentContextTenant() {
        TenantProperties properties = new TenantProperties();
        TenantTableRegistry registry = mock(TenantTableRegistry.class);
        when(registry.hasTenantColumn("db_table_t")).thenReturn(true);
        TenantNamedTableInterceptor interceptor = new TenantNamedTableInterceptor(properties, registry);
        NamedTable table = new NamedTable("db_table_t", mock(SQLModule.class), value -> value, List.of());
        table.column("tenant_id", "stale-tenant");

        try (TenantContext.Scope ignored = TenantContext.use("current-tenant")) {
            interceptor.preHandle(SqlMode.INSERT, table);
        }

        assertThat(table.getColumns().get("tenant_id")).isEqualTo("current-tenant");
    }
}
