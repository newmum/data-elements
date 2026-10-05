package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;

import com.linewell.dataelement.model.dataasset.DataAssetPropBatchSaveRequest;
import com.linewell.dataelement.dataassets.runtime.DataAssetRuntimeAdapter;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessAggTaskTService;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineTaskLifecycleSynchronizerTest {

    @Mock
    private IDataAccessAggTaskTService taskService;

    @Mock
    private DataAssetRuntimeAdapter dataAssetRuntimeAdapter;

    @Test
    void mapsPipelineStatusToTaskStatus() {
        assertThat(PipelineTaskLifecycleSynchronizer.taskStatus(PipelineStatus.RUNNING)).isEqualTo(1);
        assertThat(PipelineTaskLifecycleSynchronizer.taskStatus(PipelineStatus.DEPLOY_FAILED)).isEqualTo(2);
        assertThat(PipelineTaskLifecycleSynchronizer.taskStatus(PipelineStatus.STOPPED)).isZero();
        assertThat(PipelineTaskLifecycleSynchronizer.taskStatus(null)).isZero();
    }

    @Test
    void normalizesMissingCatalogIdentifiers() {
        assertThat(PipelineTaskLifecycleSynchronizer.normalizeCatalogTid(null)).isNull();
        assertThat(PipelineTaskLifecycleSynchronizer.normalizeCatalogTid(" undefined ")).isNull();
        assertThat(PipelineTaskLifecycleSynchronizer.normalizeCatalogTid(" catalog-1 "))
                .isEqualTo("catalog-1");
    }

    @Test
    void synchronizesGeneratedTaskIdBackToCatalog() {
        Pipeline pipeline = pipeline();
        when(taskService.ensureTaskForCatalog("catalog-1", pipeline.id(), pipeline.name(), pipeline.dsl()))
                .thenReturn("task-1");
        when(dataAssetRuntimeAdapter.queryById(any()))
                .thenReturn(Map.of("accessTaskId", "old-task"));

        PipelineTaskLifecycleSynchronizer synchronizer =
                new PipelineTaskLifecycleSynchronizer(taskService, dataAssetRuntimeAdapter);
        synchronizer.ensureCatalogAccessTask(" catalog-1 ", pipeline);

        ArgumentCaptor<DataAssetPropBatchSaveRequest> request =
                ArgumentCaptor.forClass(DataAssetPropBatchSaveRequest.class);
        verify(dataAssetRuntimeAdapter).propBatchSaveOrUpdate(request.capture());
        assertThat(request.getValue().getTids()).containsExactly("catalog-1");
        assertThat(request.getValue().getProps()).containsEntry("accessTaskId", "task-1");
    }

    @Test
    void leavesCatalogUntouchedWhenTaskIdAlreadyMatches() {
        Pipeline pipeline = pipeline();
        when(taskService.ensureTaskForCatalog("catalog-1", pipeline.id(), pipeline.name(), pipeline.dsl()))
                .thenReturn("task-1");
        when(dataAssetRuntimeAdapter.queryById(any()))
                .thenReturn(Map.of("accessTaskId", "task-1"));

        PipelineTaskLifecycleSynchronizer synchronizer =
                new PipelineTaskLifecycleSynchronizer(taskService, dataAssetRuntimeAdapter);
        synchronizer.ensureCatalogAccessTask("catalog-1", pipeline);

        verify(dataAssetRuntimeAdapter, never()).propBatchSaveOrUpdate(any());
    }

    private Pipeline pipeline() {
        Pipeline.Dsl dsl = new Pipeline.Dsl(1, List.of(), List.of());
        return new Pipeline(
                "pipeline-1", "测试流程", null, 1L, 1L, dsl,
                null, PipelineStatus.SAVED, null, null, null, null, null);
    }

    @Test
    void deletionClearsOnlyTheCatalogLinkOwnedByTheDeletedTask() {
        var task = new com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessAggTaskT()
                .setTid("task-1").setDataCatalogId("catalog-1");
        when(taskService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(task));
        when(dataAssetRuntimeAdapter.queryById(any())).thenReturn(Map.of("accessTaskId", "task-1"));
        var synchronizer = new PipelineTaskLifecycleSynchronizer(taskService, dataAssetRuntimeAdapter);
        synchronizer.unlinkCatalogTasks("pipeline-1");
        ArgumentCaptor<DataAssetPropBatchSaveRequest> request = ArgumentCaptor.forClass(DataAssetPropBatchSaveRequest.class);
        verify(dataAssetRuntimeAdapter).propBatchSaveOrUpdate(request.capture());
        assertThat(request.getValue().getProps()).containsEntry("accessTaskId", "");
        clearInvocations(dataAssetRuntimeAdapter);
        when(dataAssetRuntimeAdapter.queryById(any())).thenReturn(Map.of("accessTaskId", "new-task"));
        synchronizer.unlinkCatalogTasks("pipeline-1");
        verify(dataAssetRuntimeAdapter, never()).propBatchSaveOrUpdate(any());
    }

    @Test
    void keepsCurrentTaskAssociationWhenRetiredAssetCatalogIsUnavailable() {
        Pipeline pipeline = pipeline();
        when(taskService.ensureTaskForCatalog("catalog-1", pipeline.id(), pipeline.name(), pipeline.dsl()))
                .thenReturn("task-1");
        when(dataAssetRuntimeAdapter.queryById(any()))
                .thenThrow(new RuntimeException("Table 'da_asset_t' doesn't exist"));

        PipelineTaskLifecycleSynchronizer synchronizer =
                new PipelineTaskLifecycleSynchronizer(taskService, dataAssetRuntimeAdapter);

        assertThatCode(() -> synchronizer.ensureCatalogAccessTask("catalog-1", pipeline))
                .doesNotThrowAnyException();
        verify(dataAssetRuntimeAdapter, never()).propBatchSaveOrUpdate(any());
    }
}
