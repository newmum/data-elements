package com.linewell.dataelement.feature.approval.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataassets.base.service.IDataApplyFormTService;
import com.linewell.dataelement.elasticsearch.EsCommonService;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.EngineInstance;
import com.linewell.dataelement.feature.approval.domain.ApprovalEngineModels.StartCommand;
import com.linewell.dataelement.feature.approval.infrastructure.persistence.service.IFlowSuggestionService;
import com.linewell.dataelement.feature.assetcenter.application.AssetCenterApprovalBridge;
import com.linewell.dataelement.feature.delivery.application.ResourceDeliveryService;
import com.linewell.dataelement.feature.identity.application.IdentityUserLookupService;
import com.linewell.dataelement.model.approval.ApprovalCallbackRequest;
import com.linewell.dataelement.model.approval.ApprovalStartRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApprovalFlowAssociationTest {
    private final ApprovalEnginePort engine = mock(ApprovalEnginePort.class);
    private final ApprovalBusinessAssociationService associations = mock(ApprovalBusinessAssociationService.class);
    private final IDataApplyFormTService forms = mock(IDataApplyFormTService.class);
    private final EsCommonService search = mock(EsCommonService.class);
    private final IdentityUserLookupService identities = mock(IdentityUserLookupService.class);
    private final ResourceDeliveryService delivery = mock(ResourceDeliveryService.class);
    private final AssetCenterApprovalBridge bridge = mock(AssetCenterApprovalBridge.class);
    private final ApprovalFlowService service = new ApprovalFlowService(engine, forms,
            mock(IFlowSuggestionService.class), search, identities, delivery, bridge, associations);
    private final ApprovalBusinessAssociationService.Business business =
            new ApprovalBusinessAssociationService.Business("form", "tenant-a", "applyForm", "订阅申请", "org-a",
                    "owner", "owner", "CATALOG_SUBSCRIPTION");

    @Test void startUsesOneAuthoritativeAssociationAndNoPerAssetSearchReads() throws Exception {
        when(associations.load("form")).thenReturn(business);
        when(engine.publishedDefinitionName("subscription-flow")).thenReturn(Optional.of("订阅审批"));
        when(engine.startAndEnterFirstTask(any(StartCommand.class))).thenReturn(new EngineInstance(42L));
        when(identities.listUsersByOrgIds(List.of("org-a"))).thenReturn(List.of());
        ApprovalStartRequest request = new ApprovalStartRequest();
        request.setFlowOrderId("form"); request.setFlowType("subscription-flow");
        request.setApproveType("haoyueSubscription"); request.setAppId("app");
        try (var auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginId).thenReturn("owner");
            assertThat(service.start(request)).containsEntry("instanceId", 42L);
        }
        verify(associations).load("form");
        verify(associations).requireInitiator(business, "owner");
        verify(associations).requireCompatible(business, "haoyueSubscription");
        ArgumentCaptor<StartCommand> command = ArgumentCaptor.forClass(StartCommand.class);
        verify(engine).startAndEnterFirstTask(command.capture());
        assertThat(command.getValue().businessId()).isEqualTo("form");
        var metadata = new ObjectMapper().readTree(command.getValue().extension());
        assertThat(metadata.get("businessName").asText()).isEqualTo("订阅申请");
        assertThat(metadata.get("relaId").asText()).isEqualTo("form");
        verifyNoInteractions(search);
    }

    @Test void subscriptionCallbackKeepsCurrentDeliveryBridgeAndAvoidsLegacyAssetQueries() {
        when(associations.load("form")).thenReturn(business);
        ApprovalCallbackRequest request = new ApprovalCallbackRequest();
        request.setBusinessId("form"); request.setApproveType("haoyueSubscription");
        request.setHandleType("PASS"); request.setNextNodeCode("end");
        assertThat(service.finshHandle(request)).isEqualTo(true);
        verify(associations).requireCompatible(business, "haoyueSubscription");
        verify(bridge).complete("haoyueSubscription", "form", 2);
        verifyNoInteractions(forms, search, delivery);
    }
}
