package com.linewell.dataelement.feature.approval.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.feature.approval.infrastructure.persistence.mapper.ApprovalDelegationMapper;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserLookupMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ApprovalPrincipalBatchTest {
    @AfterEach void clearTenant() { TenantContext.clear(); }

    @Test
    void resolvesDelegatedRolesAndOrganizationsInTwoBulkQueries() {
        IdentityUserLookupMapper identities = mock(IdentityUserLookupMapper.class);
        ApprovalDelegationMapper delegations = mock(ApprovalDelegationMapper.class);
        when(delegations.selectActiveDelegatorIds(eq("tenant-a"), eq("delegate"), any(LocalDateTime.class)))
                .thenReturn(List.of("owner-1", "owner-2", "owner-1"));
        List<String> principals = List.of("delegate", "owner-1", "owner-2");
        when(identities.selectRoleCodesForUsers(principals, "tenant-a", "app"))
                .thenReturn(List.of("reviewer", "reviewer"));
        when(identities.selectOrganizationIdsForUsers(principals, "tenant-a"))
                .thenReturn(List.of("org-1"));
        ApprovalPrincipalService service = new ApprovalPrincipalService(identities, delegations);

        TenantContext.call("tenant-a", () -> {
            assertThat(service.permissionKeys("delegate", "app"))
                    .containsExactly("delegate", "owner-1", "owner-2", "role:reviewer", "org:org-1");
            return null;
        });

        verify(identities).selectRoleCodesForUsers(principals, "tenant-a", "app");
        verify(identities).selectOrganizationIdsForUsers(principals, "tenant-a");
        verify(identities, never()).selectRoleCodes(any(), any(), any());
        verify(identities, never()).selectOrganizationIds(any(), any());
    }
}
