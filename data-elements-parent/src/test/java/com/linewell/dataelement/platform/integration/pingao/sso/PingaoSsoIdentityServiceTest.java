package com.linewell.dataelement.platform.integration.pingao.sso;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserLookupMapper;
import com.linewell.dataelement.platform.integration.pingao.directory.PingaoDirectoryClient;
import com.linewell.dataelement.platform.integration.pingao.directory.PingaoDirectoryProperties;
import com.linewell.dataelement.platform.integration.pingao.directory.PingaoDirectorySnapshot;
import com.linewell.dataelement.platform.integration.pingao.directory.PingaoIdentityMappingRepository;
import com.linewell.dataelement.platform.tenant.application.TenantAccessService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class PingaoSsoIdentityServiceTest {
    private final PingaoSsoProperties properties = mock(PingaoSsoProperties.class);
    private final PingaoDirectoryProperties directoryProperties = new PingaoDirectoryProperties();
    private final PingaoDirectoryClient directory = mock(PingaoDirectoryClient.class);
    private final PingaoIdentityMappingRepository mappings = mock(PingaoIdentityMappingRepository.class);
    private final TenantAccessService tenants = mock(TenantAccessService.class);
    private final IdentityUserLookupMapper users = mock(IdentityUserLookupMapper.class);

    private PingaoSsoIdentityService service() {
        when(properties.getLocalTenantId()).thenReturn("tenant");
        when(properties.getExternalTenantCode()).thenReturn("external-tenant");
        when(properties.getLocalAppId()).thenReturn("app");
        when(properties.getLocalActiveAccountStatus()).thenReturn(0);
        return new PingaoSsoIdentityService(properties, directoryProperties, directory, mappings, tenants, users);
    }

    @Test
    void tenantScopeMismatchCannotReadDirectoryOrMappings() {
        var service = service();
        directoryProperties.setLocalTenantId("another-tenant");
        assertThrows(IllegalStateException.class,
                () -> service.resolve(new PingaoSsoClient.Identity("external", "subject")));
        verifyNoInteractions(directory, mappings, tenants, users);
    }

    @Test
    void disabledLocalAccountCannotLoadRoles() {
        var service = service();
        when(mappings.activeAccount("tenant", "user", 0)).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> service.requireAuthorized("user"));
        verify(tenants).requireMembership("user", "tenant");
        verifyNoInteractions(users);
    }

    @Test
    void activeAccountWithoutBusinessRolesIsRejected() {
        var service = service();
        when(mappings.activeAccount("tenant", "user", 0)).thenReturn(true);
        when(users.selectRoleCodes("user", "tenant", "app")).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> service.requireAuthorized("user"));
        verify(users).selectRoleCodes("user", "tenant", "app");
    }

    @Test
    void onlyActiveTenantMemberWithBusinessRoleIsAccepted() {
        var service = service();
        when(mappings.activeAccount("tenant", "user", 0)).thenReturn(true);
        when(users.selectRoleCodes("user", "tenant", "app")).thenReturn(List.of("datasource-reader"));
        assertDoesNotThrow(() -> service.requireAuthorized("user"));
        verify(tenants).requireActiveTenant("tenant");
        verify(tenants).requireMembership("user", "tenant");
    }

    @Test
    void liveDirectoryCheckRefreshesConfirmedUserAndOrganizationBeforeResolvingAccess() {
        var service = service();
        directoryProperties.setLocalTenantId("tenant");
        directoryProperties.setExternalTenantCode("external-tenant");
        var organization = new PingaoDirectorySnapshot.Organization("external-org", "350000L00100", "机构", "", "",
                true, false, 1);
        var user = new PingaoDirectorySnapshot.User("external-user", "account", "用户", true, false, 1,
                List.of(new PingaoDirectorySnapshot.Membership("external-org", "350000L00100", 1)));
        when(directory.fetch(0)).thenReturn(new PingaoDirectorySnapshot(0, 1, List.of(organization), List.of(user)));
        when(mappings.resolveUser(eq("tenant"), eq("external-tenant"), eq("external-user"), any()))
                .thenReturn("local-user");
        when(mappings.resolveOrganization(eq("tenant"), eq("external-tenant"), eq("external-org"), any()))
                .thenReturn("local-org");
        when(mappings.activeAccount("tenant", "local-user", 0)).thenReturn(true);
        when(users.selectRoleCodes("local-user", "tenant", "app")).thenReturn(List.of("reader"));
        try (var ignored = TenantContext.use("tenant")) {
            assertEquals("local-user", service.resolve(new PingaoSsoClient.Identity(
                    "external-user", "external-user", "external-org", "350000L00100")));
        }
        verify(mappings).observeUser(eq("tenant"), eq("external-tenant"), eq(user), any());
        verify(mappings).observeOrganization(eq("tenant"), eq("external-tenant"), eq(organization), any());
    }
}
