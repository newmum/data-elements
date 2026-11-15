package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.dataquality.api.DataQualityMagicModule;
import com.linewell.dataelement.feature.dataquality.application.DataQualityApplicationService;
import com.linewell.dataelement.feature.dataquality.config.DataQualityProperties;
import com.linewell.dataelement.feature.dataquality.infrastructure.persistence.DataQualityRepository;
import com.linewell.dataelement.feature.dataquality.infrastructure.persistence.mapper.DataQualityMapper;
import com.linewell.dataelement.feature.reconciliation.api.ReconciliationMagicModule;
import com.linewell.dataelement.feature.reconciliation.application.ReconciliationApplicationService;
import com.linewell.dataelement.feature.reconciliation.config.ReconciliationProperties;
import com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.ReconciliationRepository;
import com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.mapper.ReconciliationControlMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MagicApplicationBoundaryTest {

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void pageSizeDoesNotAddPerRowQueriesAndUsesCurrentTenant(int size) {
        var qualityMapper = mock(DataQualityMapper.class);
        var reconcileMapper = mock(ReconciliationControlMapper.class);
        List<Map<String, Object>> rows = IntStream.range(0, size)
                .mapToObj(id -> Map.<String, Object>of("tid", "record-" + id)).toList();
        var page = new Page<Map<String, Object>>(1, size, size);
        page.setRecords(rows);
        when(qualityMapper.selectTaskPage(any(), eq("tenant-a"), isNull(), isNull(), isNull(), anyString()))
                .thenReturn(page);
        when(reconcileMapper.selectPolicyPage(any(), eq("tenant-a"), eq(""), anyString())).thenReturn(page);
        var quality = qualityModule(qualityMapper);
        var reconcile = new ReconciliationMagicModule(new ReconciliationApplicationService(
                new ReconciliationRepository(reconcileMapper, new ObjectMapper(), null,
                        new ReconciliationProperties()), null));

        TenantContext.run("tenant-a", () -> {
            assertThat(quality.taskPage(null, null, 1, size, null).get("list")).isEqualTo(rows);
            assertThat(reconcile.policyPage(null, 1, size).get("list")).isEqualTo(rows);
        });

        verify(qualityMapper).selectTaskPage(any(), eq("tenant-a"), isNull(), isNull(), isNull(), anyString());
        verify(reconcileMapper).selectPolicyPage(any(), eq("tenant-a"), eq(""), anyString());
        verifyNoMoreInteractions(qualityMapper, reconcileMapper);
    }

    @Test
    void missingTenantRejectsBothModulesBeforeDatabaseAccess() {
        var qualityRepository = mock(DataQualityRepository.class);
        var reconcileRepository = mock(ReconciliationRepository.class);
        var quality = new DataQualityMagicModule(new DataQualityApplicationService(qualityRepository, null));
        var reconcile = new ReconciliationMagicModule(new ReconciliationApplicationService(reconcileRepository, null));
        TenantContext.clear();

        assertThatThrownBy(() -> quality.taskPage(null, null, 1, 20, null))
                .isInstanceOf(TenantAccessException.class);
        assertThatThrownBy(() -> reconcile.policyDetail("policy-a"))
                .isInstanceOf(TenantAccessException.class);
        verifyNoInteractions(qualityRepository, reconcileRepository);
    }

    private DataQualityMagicModule qualityModule(DataQualityMapper mapper) {
        return new DataQualityMagicModule(new DataQualityApplicationService(
                new DataQualityRepository(mapper, null, new DataQualityProperties(), new ObjectMapper()), null));
    }
}
