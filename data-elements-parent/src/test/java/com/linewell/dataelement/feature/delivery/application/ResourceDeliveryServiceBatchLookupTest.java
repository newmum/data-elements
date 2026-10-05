package com.linewell.dataelement.feature.delivery.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.linewell.dataelement.dataassets.base.service.IDaAssetApiRelaService;
import com.linewell.dataelement.dataassets.base.service.IDataApplyFormTService;
import com.linewell.dataelement.dataassets.base.service.IDataPropTService;
import com.linewell.dataelement.feature.delivery.infrastructure.persistence.entity.ApiInfoDeliveryView;
import com.linewell.dataelement.feature.delivery.infrastructure.persistence.entity.DataApiAuthorizationEntity;
import com.linewell.dataelement.feature.delivery.infrastructure.persistence.mapper.ApiInfoDeliveryMapper;
import com.linewell.dataelement.feature.delivery.infrastructure.persistence.mapper.DataApiAuthorizationMapper;
import com.linewell.dataelement.feature.delivery.infrastructure.persistence.mapper.DataDistributionTaskMapper;
import com.linewell.dataelement.feature.delivery.infrastructure.persistence.mapper.FlowServiceCallLogMapper;
import com.linewell.dataelement.utils.GatewayUtils;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceDeliveryServiceBatchLookupTest {

    @Test
    void loadsDistinctAuthorizationApisInOneQueryAndPreservesMissingApiFallback() {
        ApiInfoDeliveryMapper apiMapper = mock(ApiInfoDeliveryMapper.class);
        ResourceDeliveryService service = new ResourceDeliveryService(
                mock(IDataApplyFormTService.class), mock(IDaAssetApiRelaService.class),
                mock(IDataPropTService.class), mock(DataDistributionTaskMapper.class),
                mock(DataApiAuthorizationMapper.class), apiMapper,
                mock(FlowServiceCallLogMapper.class), mock(GatewayUtils.class));
        ApiInfoDeliveryView api = new ApiInfoDeliveryView();
        api.setTid("api-1");
        api.setServiceName("服务一");
        when(apiMapper.selectList(any(Wrapper.class))).thenReturn(List.of(api));

        var authorizations = List.of(auth("auth-1", "api-1"), auth("auth-2", "api-1"),
                auth("auth-3", "missing-api"));
        var apisById = service.loadApis(authorizations);
        var rows = service.enrichAuthorizations(authorizations, apisById);

        assertEquals(1, apisById.size());
        assertEquals(3, rows.size());
        assertEquals("服务一", rows.get(0).get("serviceName"));
        assertEquals("服务一", rows.get(1).get("serviceName"));
        assertEquals("missing-api", rows.get(2).get("serviceName"));
        assertNull(rows.get(2).get("serviceCode"));
        verify(apiMapper).selectList(any(Wrapper.class));
        verify(apiMapper, never()).selectById(any());
    }

    private static DataApiAuthorizationEntity auth(String tid, String apiId) {
        DataApiAuthorizationEntity auth = new DataApiAuthorizationEntity();
        auth.setTid(tid);
        auth.setApiId(apiId);
        return auth;
    }
}
