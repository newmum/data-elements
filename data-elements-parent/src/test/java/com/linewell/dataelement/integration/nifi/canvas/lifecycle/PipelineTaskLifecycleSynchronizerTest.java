package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.dataassets.runtime.DataAssetRuntimeAdapter;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessAggTaskTService;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ssssssss.magicapi.core.service.MagicAPIService;
import org.ssssssss.script.exception.MagicScriptException;

@ExtendWith(MockitoExtension.class)
class PipelineTaskLifecycleSynchronizerTest {

    @Mock
    private IDataAccessAggTaskTService taskService;

    @Mock
    private DataAssetRuntimeAdapter dataAssetRuntimeAdapter;

    @Mock
    private MagicAPIService magic;

    @Test
    void stateBridgeKeepsExplicitNullAndPropagatesTheOriginalFailure() {
        var synchronizer = new PipelineTaskLifecycleSynchronizer(taskService, dataAssetRuntimeAdapter, magic);
        var original = new IllegalStateException("test transaction rejected");
        when(magic.invoke(eq("/common/nifiTaskState"), any())).thenThrow(
                new MagicScriptException("script failure", "script failure", original, null));

        assertThatThrownBy(() -> synchronizer.syncState("pipeline-1", "group", null)).isSameAs(original);
        verify(magic).invoke(eq("/common/nifiTaskState"), org.mockito.ArgumentMatchers.<Map<String, Object>>argThat(
                arguments -> arguments.size() == 3 && "pipeline-1".equals(arguments.get("pipelineId"))
                        && "group".equals(arguments.get("operation"))
                        && arguments.containsKey("value") && arguments.get("value") == null));
    }

    @Test
    void synchronizesGeneratedTaskIdBackToCatalog() {
        Pipeline pipeline = pipeline();
        when(magic.invoke(eq("/common/ensureNifiCatalogAccessTask"), any()))
                .thenReturn(Map.of("taskId", "task-1", "catalogId", "catalog-1", "mirrorEligible", true));
        when(dataAssetRuntimeAdapter.queryById("catalog-1"))
                .thenReturn(Map.of("accessTaskId", "old-task"));

        PipelineTaskLifecycleSynchronizer synchronizer =
                new PipelineTaskLifecycleSynchronizer(taskService, dataAssetRuntimeAdapter, magic);
        synchronizer.ensureCatalogAccessTask(" catalog-1 ", pipeline);

        verify(magic).invoke("/common/ensureNifiCatalogAccessTask", Map.of("rawCatalogTid", " catalog-1 ",
                "pipelineId", pipeline.id(), "pipelineName", pipeline.name(), "dsl", pipeline.dsl()));
        verifyNoInteractions(taskService);

        verify(dataAssetRuntimeAdapter).propBatchSaveOrUpdate(List.of("catalog-1"), Map.of("accessTaskId", "task-1"));
    }

    @Test
    void leavesCatalogUntouchedWhenTaskIdAlreadyMatches() {
        Pipeline pipeline = pipeline();
        when(magic.invoke(eq("/common/ensureNifiCatalogAccessTask"), any()))
                .thenReturn(Map.of("taskId", "task-1", "catalogId", "catalog-1", "mirrorEligible", true));
        when(dataAssetRuntimeAdapter.queryById("catalog-1"))
                .thenReturn(Map.of("accessTaskId", "task-1"));

        PipelineTaskLifecycleSynchronizer synchronizer =
                new PipelineTaskLifecycleSynchronizer(taskService, dataAssetRuntimeAdapter, magic);
        synchronizer.ensureCatalogAccessTask("catalog-1", pipeline);

        verify(dataAssetRuntimeAdapter, never()).propBatchSaveOrUpdate(any(), any());
    }

    @Test
    void leavesTheCatalogProjectionUntouchedForAnIndependentlySavedFlow() {
        when(magic.invoke(eq("/common/ensureNifiCatalogAccessTask"), any()))
                .thenReturn(Map.of("taskId", "task-1", "mirrorEligible", false));
        var synchronizer = new PipelineTaskLifecycleSynchronizer(taskService, dataAssetRuntimeAdapter, magic);

        synchronizer.ensureCatalogAccessTask("another-catalog", pipeline());

        verifyNoInteractions(taskService, dataAssetRuntimeAdapter);
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
        when(taskService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessAggTaskT>>any())).thenReturn(List.of(task));
        when(dataAssetRuntimeAdapter.queryById("catalog-1")).thenReturn(Map.of("accessTaskId", "task-1"));
        var synchronizer = new PipelineTaskLifecycleSynchronizer(taskService, dataAssetRuntimeAdapter, magic);
        synchronizer.unlinkCatalogTasks("pipeline-1");
        verify(dataAssetRuntimeAdapter).propBatchSaveOrUpdate(List.of("catalog-1"), Map.of("accessTaskId", ""));
        clearInvocations(dataAssetRuntimeAdapter);
        when(dataAssetRuntimeAdapter.queryById("catalog-1")).thenReturn(Map.of("accessTaskId", "new-task"));
        synchronizer.unlinkCatalogTasks("pipeline-1");
        verify(dataAssetRuntimeAdapter, never()).propBatchSaveOrUpdate(any(), any());
    }

    @Test
    void keepsCurrentTaskAssociationWhenCatalogReadFails() {
        Pipeline pipeline = pipeline();
        when(magic.invoke(eq("/common/ensureNifiCatalogAccessTask"), any()))
                .thenReturn(Map.of("taskId", "task-1", "catalogId", "catalog-1", "mirrorEligible", true));
        when(dataAssetRuntimeAdapter.queryById("catalog-1"))
                .thenThrow(new IllegalStateException("目录读取失败"));

        PipelineTaskLifecycleSynchronizer synchronizer =
                new PipelineTaskLifecycleSynchronizer(taskService, dataAssetRuntimeAdapter, magic);

        assertThatCode(() -> synchronizer.ensureCatalogAccessTask("catalog-1", pipeline))
                .doesNotThrowAnyException();
        verify(dataAssetRuntimeAdapter, never()).propBatchSaveOrUpdate(any(), any());
    }
}
