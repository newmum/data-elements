package com.linewell.dataelement.feature.approval.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserLookupMapper;
import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ApprovalBusinessAssociationTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final IdentityUserLookupMapper identities = mock(IdentityUserLookupMapper.class);
    private final ApprovalBusinessAssociationService service = new ApprovalBusinessAssociationService(jdbc, identities);

    @AfterEach void clearTenant() { TenantContext.clear(); }

    private Map<String, Object> form(String tenant, String type) {
        return Map.of("business_id", "form", "tenant_id", tenant, "business_type", "applyForm",
                "business_name", "资源申请", "org_id", "org-a", "owner_id", "owner", "created_by", "owner",
                "request_type", type);
    }

    private void rows(List<Map<String, Object>> rows) {
        when(jdbc.queryForList(anyString(), eq("tenant-a"), eq("form"), eq("tenant-a"), eq("form")))
                .thenReturn(rows);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void businessWithManyRequestedResourcesStillUsesOneTenantBoundQuery(int resources) {
        var row = new java.util.HashMap<>(form("tenant-a", "apply"));
        row.put("catalog_ids", java.util.stream.IntStream.range(0, resources)
                .mapToObj(i -> "catalog-" + i).collect(java.util.stream.Collectors.joining(",")));
        rows(List.of(row));
        TenantContext.run("tenant-a", () -> {
            var business = service.load("form");
            service.requireInitiator(business, "owner");
            service.requireCompatible(business, "apply");
        });
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(1)).queryForList(sql.capture(), eq("tenant-a"), eq("form"), eq("tenant-a"), eq("form"));
        assertThat(sql.getValue()).contains("a.tenant_id=?", "p.tenant_id=?", "a.tid=?", "p.tid=?")
                .doesNotContain("da_order_asset_rela");
        verifyNoInteractions(identities);
    }

    @Test void rejectsMissingAndAmbiguousBusinessIds() {
        rows(List.of());
        TenantContext.run("tenant-a", () -> assertThatThrownBy(() -> service.load("form")).isInstanceOf(BizException.class));
        rows(List.of(form("tenant-a", "apply"), form("tenant-a", "apply")));
        TenantContext.run("tenant-a", () -> assertThatThrownBy(() -> service.load("form")).isInstanceOf(BizException.class));
    }

    @Test void rejectsCrossTenantRowsEvenIfTheDatabaseReturnsOne() {
        rows(List.of(form("tenant-b", "apply")));
        TenantContext.run("tenant-a", () -> assertThatThrownBy(() -> service.load("form")).isInstanceOf(BizException.class));
    }

    @Test void requiresContextBeforeAnySql() {
        assertThatThrownBy(() -> service.load("form")).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void checksOrganizationMembershipInCurrentTenantAndRejectsUnrelatedUsers() {
        rows(List.of(form("tenant-a", "apply")));
        when(identities.selectOrganizationIds("member", "tenant-a")).thenReturn(List.of("org-a"));
        when(identities.selectOrganizationIds("stranger", "tenant-a")).thenReturn(List.of("org-b"));
        TenantContext.run("tenant-a", () -> {
            var business = service.load("form");
            service.requireInitiator(business, "member");
            assertThatThrownBy(() -> service.requireInitiator(business, "stranger")).isInstanceOf(BizException.class);
        });
        verify(identities).selectOrganizationIds("member", "tenant-a");
        verify(identities).selectOrganizationIds("stranger", "tenant-a");
    }

    @Test void keepsSubscriptionAndPublicationContractsSeparate() {
        var subscription = new ApprovalBusinessAssociationService.Business("form", "tenant-a", "applyForm",
                "订阅", "org-a", "owner", "owner", "CATALOG_SUBSCRIPTION");
        service.requireCompatible(subscription, "haoyueSubscription");
        assertThatThrownBy(() -> service.requireCompatible(subscription, "apply")).isInstanceOf(BizException.class);
        var publication = new ApprovalBusinessAssociationService.Business("pub", "tenant-a", "publication",
                "发布", "org-a", "owner", "owner", "haoyuePublication");
        service.requireCompatible(publication, "haoyuePublication");
        assertThatThrownBy(() -> service.requireCompatible(publication, "haoyueSubscription")).isInstanceOf(BizException.class);
    }

    @Test void cannotReconnectRegistrationToLegacyAssetApproval() {
        var business = new ApprovalBusinessAssociationService.Business("form", "tenant-a", "applyForm",
                "申请", "org-a", "owner", "owner", "apply");
        for (String type : List.of("checkIn", "assetUpdate")) {
            assertThatThrownBy(() -> service.requireCompatible(business, type)).isInstanceOf(BizException.class);
        }
    }
}
