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
        when(qualityMapper.selectTaskPage(any(), eq("tenant-a"), isNull(), isNull(), isNull()))
                .thenReturn(page);
        when(reconcileMapper.selectPolicyPage(any(), eq("tenant-a"), eq(""))).thenReturn(page);
        var quality = qualityModule(qualityMapper);
        var reconcile = new ReconciliationMagicModule(new ReconciliationApplicationService(
                new ReconciliationRepository(reconcileMapper, new ObjectMapper(), null,
                        new ReconciliationProperties()), null));

        TenantContext.run("tenant-a", () -> {
            assertThat(quality.taskPage(null, null, 1, size, null).get("list")).isEqualTo(rows);
            assertThat(reconcile.policyPage(null, 1, size).get("list")).isEqualTo(rows);
        });

        verify(qualityMapper).selectTaskPage(any(), eq("tenant-a"), isNull(), isNull(), isNull());
        verify(reconcileMapper).selectPolicyPage(any(), eq("tenant-a"), eq(""));
        verifyNoMoreInteractions(qualityMapper, reconcileMapper);
    }

    @Test
    void missingTenantRejectsBothModulesBeforeDatabaseAccess() {
        var qualityRepository = mock(DataQualityRepository.class);
        var reconcileRepository = mock(ReconciliationRepository.class);
        var quality = new DataQualityMagicModule(new DataQualityApplicationService(qualityRepository, null));
        var reconcile = new ReconciliationMagicModule(new ReconciliationApplicationService(reconcileRepository, null));
        TenantContext.clear();

        assertThatThrownBy(() -> quality.columnsBatch(List.of("table-a")))
                .isInstanceOf(TenantAccessException.class);
        assertThatThrownBy(() -> reconcile.policyDetail("policy-a"))
                .isInstanceOf(TenantAccessException.class);
        verifyNoInteractions(qualityRepository, reconcileRepository);
    }

    @Test
    void batchColumnsRetainTenantScopeDeduplicationAndSizeLimit() {
        var mapper = mock(DataQualityMapper.class);
        when(mapper.selectColumnOptionsBatch("tenant-b", List.of("table-a", "table-b")))
                .thenReturn(List.of(Map.of("table_id", "table-a", "column_name", "id")));
        var quality = qualityModule(mapper);

        TenantContext.run("tenant-b", () -> {
            var result = quality.columnsBatch(List.of("table-a", "table-b", "table-a"));
            assertThat(result).containsOnlyKeys("table-a", "table-b");
            assertThat(result.get("table-a")).hasSize(1);
            assertThat(result.get("table-b")).isEmpty();
            assertThatThrownBy(() -> quality.columnsBatch(java.util.Collections.nCopies(100, "table-a")))
                    .isInstanceOf(IllegalArgumentException.class);
        });

        verify(mapper).selectColumnOptionsBatch("tenant-b", List.of("table-a", "table-b"));
        verifyNoMoreInteractions(mapper);
    }

    private DataQualityMagicModule qualityModule(DataQualityMapper mapper) {
        return new DataQualityMagicModule(new DataQualityApplicationService(
                new DataQualityRepository(mapper, null, new DataQualityProperties(), new ObjectMapper()), null));
    }
}
