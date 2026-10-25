package com.linewell.dataelement.platform.audit;

import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditStoreTest {
    private final DataSource control=mock(DataSource.class);
    private final TenantDataSourceRegistry registry=mock(TenantDataSourceRegistry.class);
    private final TenantDatabaseProperties properties=new TenantDatabaseProperties();
    private final AuditStore store=new AuditStore(control,registry,properties);
    @Test void unknownTenantNeverFallsBackToDefaultIndustryOrControl(){assertThrows(SecurityException.class,()->store.dataSource("unknown-tenant"));verifyNoInteractions(registry);}
    @Test void explicitPlatformScopeUsesOnlyControl(){assertSame(control,store.dataSource(null));verifyNoInteractions(registry);}
    @Test void disabledTenantRoutingDoesNotExposeControlToIndustry(){properties.setEnabled(false);properties.getTenantBindings().put("validation-tenant","validation-binding");assertThrows(SecurityException.class,()->store.dataSource("validation-tenant"));verifyNoInteractions(registry);}
}
