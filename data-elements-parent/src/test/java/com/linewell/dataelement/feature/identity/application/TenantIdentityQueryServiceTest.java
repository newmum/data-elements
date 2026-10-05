package com.linewell.dataelement.feature.identity.application;

import com.linewell.dataelement.platform.tenant.application.TenantAccountPolicy;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantIdentityQueryServiceTest {
    private final ControlIdentityService control=mock(ControlIdentityService.class);
    private final TenantAccountPolicy policy=mock(TenantAccountPolicy.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final TenantIdentityQueryService service=new TenantIdentityQueryService(control,policy,jdbc);
    @AfterEach void clear() {TenantContext.clear();}
    @Test void legacyTenantRetainsControlMembershipSource() {
        TenantContext.bind("legacy");
        when(control.page("legacy",1,10,null,null,null)).thenReturn(Map.of("list",List.of(),"total",0));
        assertEquals(0,service.page("legacy",1,10,null,null,null).get("total"));
        verifyNoInteractions(jdbc);
    }
    @Test void localMissingUserDoesNotFallBackToSameControlId() {
        TenantContext.bind("ga");when(policy.isLocal("ga")).thenReturn(true);
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());
        assertNull(service.find("ga","same-control-id"));
        verifyNoInteractions(control);
    }
    @Test void emptyOrganizationFilterReturnsZeroInsteadOfAllAccounts() {
        TenantContext.bind("ga");when(policy.isLocal("ga")).thenReturn(true);
        assertEquals(0L,service.page("ga",1,10,List.of(),null,null).get("total"));
        verifyNoInteractions(jdbc,control);
    }
    @Test void queryingAnotherTenantFailsBeforeEitherDatabase() {
        TenantContext.bind("ga");
        assertThrows(TenantAccessException.class,()->service.find("other","user"));
        verifyNoInteractions(jdbc,control,policy);
    }
    @Test void controlScopeCannotReadLocalAccounts() {
        TenantContext.bind("ga");
        try(var ignored=TenantContext.control()) {
            assertThrows(TenantAccessException.class,()->service.find("ga","user"));
        }
        verifyNoInteractions(jdbc,control,policy);
    }
}
