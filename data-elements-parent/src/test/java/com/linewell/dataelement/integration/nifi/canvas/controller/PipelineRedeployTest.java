package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.*;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PipelineRedeployTest {
    private final PipelineRepository repo = mock(PipelineRepository.class);
    private final DslCompiler compiler = mock(DslCompiler.class);
    private final NifiClient nifi = mock(NifiClient.class);
    private final PipelineGroupOrganizer groups = mock(PipelineGroupOrganizer.class);
    private final PipelineTaskLifecycleSynchronizer tasks = mock(PipelineTaskLifecycleSynchronizer.class);
    private final AtomicReference<Pipeline> stored = new AtomicReference<>();
    private final TargetTableCleanupService cleanup = mock(TargetTableCleanupService.class);
    private final PipelineRunController controller = new PipelineRunController(repo, compiler, nifi,
            mock(ErrorService.class), tasks, mock(PipelineRuntimeStatusResolver.class), groups, cleanup);

    @BeforeEach
    void storage() {
        stored.set(pipeline("old-group", "old-hash", PipelineStatus.STOPPED));
        when(repo.findById("p1")).thenAnswer(i -> Optional.of(stored.get()));
        when(repo.update(eq("p1"), any())).thenAnswer(i -> {
            UnaryOperator<Pipeline> change = i.getArgument(1);
            return stored.updateAndGet(change);
        });
        when(repo.updateStatus(eq("p1"), any())).thenAnswer(i -> stored.updateAndGet(p ->
                new Pipeline(p.id(), p.name(), p.description(), p.createdAt(), p.updatedAt(), p.dsl(),
                        p.nifiProcessGroupId(), i.getArgument(1), p.lastDeployedHash(), p.lastDeployedAt(),
                        p.lastStoppedAt(), p.nodeMapping(), p.lastBulletinId())));
        when(groups.prepare(any())).thenAnswer(i -> new PipelineGroupOrganizer.Layout("top", "task", "application", "流程",
                "owned", new PipelineGroupOrganizer.Position(520, 0), List.of(stored.get().nifiProcessGroupId())));
    }

    @Test
    void nativeUiLinkRequiresCurrentVersionToBeDeployed() {
        assertThat(controller.uiLink("p1").getStatusCode().value()).isEqualTo(400);

        Pipeline p = stored.get();
        stored.set(pipeline("old-group", deployedHash(p.dsl()), PipelineStatus.STOPPED));
        assertThat(controller.uiLink("p1").getStatusCode().value()).isEqualTo(200);

        stored.set(pipeline("old-group", deployedHash(p.dsl()), PipelineStatus.DEPLOY_FAILED));
        assertThat(controller.uiLink("p1").getStatusCode().value()).isEqualTo(400);

        stored.set(pipeline(null, deployedHash(p.dsl()), PipelineStatus.SAVED));
        assertThat(controller.uiLink("p1").getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void repeatedSaveReusesRunningGroupWithoutStoppingIt() {
        Pipeline p = stored.get();
        stored.set(pipeline("old-group", deployedHash(p.dsl()), PipelineStatus.RUNNING));
        when(groups.isCurrent(any(), eq("old-group"))).thenReturn(true);
        assertThat(controller.deploy("p1", null).getStatusCode().value()).isEqualTo(200);
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.RUNNING);
        verifyNoInteractions(compiler, nifi);
    }

    @Test
    void cleanupFailurePreventsCreation() {
        doThrow(new IllegalStateException("still running")).when(nifi).cleanupProcessGroup("old-group");
        assertThat(controller.deploy("p1", null).getStatusCode().value()).isEqualTo(500);
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("old-group");
        verifyNoInteractions(compiler);
    }

    @Test
    void queuedDataPreventsCleanupAndReplacement() {
        doThrow(new IllegalStateException("旧流程还有 3 条排队数据"))
                .when(nifi).requireEmptyQueuesForRedeploy("old-group");
        assertThat(controller.deploy("p1", null).getStatusCode().value()).isEqualTo(400);
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("old-group");
        verify(nifi, never()).cleanupProcessGroup(anyString());
        verifyNoInteractions(compiler);
    }

    @Test
    void compileFailurePersistsNewGroupAndRetryRemovesItFirst() {
        when(compiler.compile(any(), any(), any())).thenAnswer(i -> {
            Consumer<String> callback = i.getArgument(1);
            callback.accept("partial-group");
            assertThat(stored.get().nifiProcessGroupId()).isEqualTo("partial-group");
            assertThat(stored.get().lastDeployedHash()).isNull();
            throw new IllegalStateException("invalid processor");
        });
        assertThat(controller.deploy("p1", null).getStatusCode().value()).isEqualTo(400);
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("partial-group");
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.DEPLOY_FAILED);

        doAnswer(i -> {
            Consumer<String> callback = i.getArgument(1);
            callback.accept("final-group");
            return new DslCompiler.CompileResult("final-group", Map.of(), Map.of(), Map.of());
        }).when(compiler).compile(any(), any(), any());
        assertThat(controller.deploy("p1", null).getStatusCode().value()).isEqualTo(200);
        var order = inOrder(nifi, compiler);
        order.verify(nifi).cleanupProcessGroup("old-group");
        order.verify(compiler).compile(any(), any(), any());
        order.verify(nifi).cleanupProcessGroup("partial-group");
        order.verify(compiler).compile(any(), any(), any());
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("final-group");
    }

    @Test
    void undeployAlsoRecoversTaskOnlyOrphanAndClearsAssociation() {
        stored.set(pipeline(null, null, PipelineStatus.DEPLOY_FAILED));
        when(groups.existingIds(any())).thenReturn(List.of("task-only-group"));
        assertThat(controller.undeploy("p1").getStatusCode().value()).isEqualTo(200);
        verify(nifi).cleanupProcessGroup("task-only-group");
        verify(tasks).updateProcessGroup("p1", null);
        assertThat(stored.get().lastDeployedHash()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FULL", "FULL_THEN_INCR"})
    void explicitTargetCleanupRebuildsEvenUnchangedFlowBeforeClearingAndStarting(String mode) {
        setFullLoad(mode, true);
        when(groups.isCurrent(any(), eq("old-group"))).thenReturn(true);
        compileNewGroup();

        assertThat(controller.start("p1", true, true).getStatusCode().value()).isEqualTo(200);

        var order = inOrder(nifi, compiler, cleanup, tasks);
        order.verify(nifi).cleanupProcessGroup("old-group");
        order.verify(compiler).compile(any(), any(), any());
        order.verify(cleanup).clear(any());
        order.verify(nifi).setProcessGroupState("new-group", "RUNNING");
        order.verify(tasks).started("p1");
        verify(cleanup, times(1)).clear(any());
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("new-group");
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.RUNNING);
    }

    @Test
    void targetCleanupFailureKeepsNewFlowStoppedAndDoesNotReportStarted() {
        setFullLoad("FULL", true);
        compileNewGroup();
        doThrow(new IllegalStateException("Hive 清理失败")).when(cleanup).clear(any());

        assertThat(controller.start("p1", true, true).getStatusCode().value()).isEqualTo(400);

        verify(nifi, never()).setProcessGroupState(anyString(), eq("RUNNING"));
        verify(tasks, never()).started(anyString());
        verify(tasks).statusOnly("p1", 2);
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.DEPLOY_FAILED);
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("new-group");
        assertThat(stored.get().lastDeployedHash()).isNull();
    }

    @Test
    void failureToStopOldFlowPreventsTargetCleanup() {
        setFullLoad("FULL", true);
        doThrow(new IllegalStateException("still running")).when(nifi).cleanupProcessGroup("old-group");
        assertThat(controller.start("p1", true, true).getStatusCode().value()).isEqualTo(500);
        verifyNoInteractions(cleanup, compiler);
        verify(nifi, never()).setProcessGroupState(anyString(), eq("RUNNING"));
    }

    @Test
    void deploymentWithoutStartingNeverClearsTarget() {
        setFullLoad("FULL", true);
        compileNewGroup();
        assertThat(controller.deploy("p1", null).getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(cleanup);
        verify(nifi, never()).setProcessGroupState(anyString(), eq("RUNNING"));
    }

    @Test
    void neitherForceNorDeploymentAllowsCleanupWithoutExplicitAcknowledgement() {
        setFullLoad("FULL", true);
        assertThat(controller.start("p1", true).getStatusCode().value()).isEqualTo(409);
        assertThat(controller.start("p1").getStatusCode().value()).isEqualTo(409);
        verifyNoInteractions(nifi, compiler, cleanup, tasks);
    }

    @Test
    void periodicTruncateNeedsAcknowledgementButDoesNotUseOneTimeCleanup() {
        var source = new Pipeline.Node("source", "source.mysql", "来源", "source", 0, 0,
                Map.of("syncMode", "PERIODIC_FULL", "fullSyncStrategy", "TRUNCATE_RELOAD"));
        var dsl = new Pipeline.Dsl(1, List.of(source), List.of());
        stored.set(new Pipeline("p1", "流程", null, 1L, 1L, dsl, "old-group",
                PipelineStatus.STOPPED, deployedHash(dsl), 1L, null, null, null));
        assertThat(controller.start("p1").getStatusCode().value()).isEqualTo(409);
        verifyNoInteractions(nifi, compiler, cleanup);
        assertThat(controller.start("p1", false, true).getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(cleanup, compiler);
        verify(nifi).setProcessGroupState("old-group", "RUNNING");
    }

    @Test
    void uncheckedUnchangedFlowStillUsesFastStart() {
        setFullLoad("FULL", false);
        assertThat(controller.start("p1").getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(compiler);
        verify(nifi, never()).cleanupProcessGroup(anyString());
        verify(nifi).setProcessGroupState("old-group", "RUNNING");
    }

    @Test
    void fullDatabaseSourceUsesOneShotExecutionEvenWhenDownstreamIsRunning() {
        setFullLoad("FULL", false);
        when(compiler.supportsSyncSettings("source.jdbc")).thenReturn(true);
        Pipeline p = stored.get();
        stored.set(new Pipeline(p.id(), p.name(), p.description(), p.createdAt(), p.updatedAt(), p.dsl(),
                p.nifiProcessGroupId(), p.status(), p.lastDeployedHash(), p.lastDeployedAt(), p.lastStoppedAt(),
                new NifiNodeMapping(Map.of("source", "full-fetch"), Map.of("full-fetch", "source"), Map.of()), null));
        assertThat(controller.start("p1").getStatusCode().value()).isEqualTo(200);
        verify(nifi).startWithOneShotSources("old-group", java.util.Set.of("full-fetch"));
        verify(nifi, never()).setProcessGroupState(anyString(), eq("RUNNING"));
        clearInvocations(nifi);
        doThrow(new IllegalStateException("上一轮仍有排队数据")).when(nifi).startWithOneShotSources(anyString(), anySet());
        assertThat(controller.start("p1").getStatusCode().value()).isEqualTo(409);
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.RUNNING);
        verify(nifi, never()).cleanupProcessGroup(anyString());
    }

    @Test
    void normalStartRejectsAnUndeployedPipelineInsteadOfCompilingIt() {
        stored.set(pipeline(null, null, PipelineStatus.SAVED));

        assertThat(controller.start("p1").getStatusCode().value()).isEqualTo(409);

        verifyNoInteractions(compiler, nifi);
    }

    @Test
    void normalStartRejectsAChangedSavedVersionInsteadOfRedeployingIt() {
        stored.set(pipeline("old-group", "stale-hash", PipelineStatus.STOPPED));

        assertThat(controller.start("p1").getStatusCode().value()).isEqualTo(409);

        verifyNoInteractions(compiler, nifi);
    }

    @Test
    void forceStartRebuildsAnUnchangedDeployment() {
        Pipeline p = stored.get();
        stored.set(pipeline("old-group", deployedHash(p.dsl()), PipelineStatus.STOPPED));
        when(groups.isCurrent(any(), eq("old-group"))).thenReturn(true);
        compileNewGroup();

        assertThat(controller.start("p1", true).getStatusCode().value()).isEqualTo(200);

        verify(nifi).cleanupProcessGroup("old-group");
        verify(compiler).compile(any(), any(), any());
        verify(nifi).setProcessGroupState("new-group", "RUNNING");
        verify(tasks).started("p1");
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("new-group");
    }

    private void setFullLoad(String mode, boolean delete) {
        var source = new Pipeline.Node("source", "source.jdbc", "来源", "source", 0, 0,
                Map.of("syncMode", mode, "deleteTargetData", delete));
        var dsl = new Pipeline.Dsl(1, List.of(source), List.of());
        stored.set(new Pipeline("p1", "流程", null, 1L, 1L, dsl,
                "old-group", PipelineStatus.STOPPED, deployedHash(dsl), 1L, null, null, null));
    }

    private void compileNewGroup() {
        when(compiler.compile(any(), any(), any())).thenAnswer(i -> {
            Consumer<String> callback = i.getArgument(1);
            callback.accept("new-group");
            return new DslCompiler.CompileResult("new-group", Map.of(), Map.of(), Map.of());
        });
    }

    private Pipeline pipeline(String pg, String hash, PipelineStatus status) {
        return new Pipeline("p1", "流程", null, 1L, 1L, new Pipeline.Dsl(1, List.of(), List.of()),
                pg, status, hash, 1L, null, null, null);
    }

    private static String deployedHash(Pipeline.Dsl dsl) {
        return DslHasher.deploymentHash(dsl, PipelineRunController.DEPLOYMENT_COMPILER_REVISION);
    }
}
