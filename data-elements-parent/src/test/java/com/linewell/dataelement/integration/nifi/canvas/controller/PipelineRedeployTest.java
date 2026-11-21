package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PipelineRedeployTest {
    @Test
    void stoppingRetainsRegisteredIncrementalFieldAndEntireSourceDesign() {
        var before = stored.get();
        var source = new Pipeline.Node("source", "source.oracle", "来源", "source", 0, 0,
                Map.of("sourceTableId", "registered", "incrementalColumn", "UPDATE_TIME", "syncMode", "FULL_THEN_INCR"));
        var dsl = new Pipeline.Dsl(1, List.of(source), List.of());
        stored.set(new Pipeline(before.id(), before.name(), before.description(), before.createdAt(), before.updatedAt(),
                dsl, "running-group", PipelineStatus.RUNNING, before.lastDeployedHash(), before.lastDeployedAt(), null, before.nodeMapping(), null));
        assertThat(deployment.stop(before.id()).getStatusCode().value()).isEqualTo(200);
        assertThat(stored.get().dsl()).isEqualTo(dsl);
        assertThat(stored.get().dsl().nodes().getFirst().config()).containsEntry("incrementalColumn", "UPDATE_TIME");
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.STOPPED);
    }

    @Test
    void stopTimeoutKeepsStoppingUntilRetryConfirmsNativeCompletion() {
        doThrow(new NifiException("still running", 409, null)).doNothing().when(nifi)
                .waitForAllProcessorsStopped("old-group", 30_000L);
        assertThat(deployment.stop("p1").getStatusCode().value()).isEqualTo(202);
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.STOPPING);
        verify(tasks, never()).syncState("p1", "stopped", null);

        assertThat(deployment.stop("p1").getStatusCode().value()).isEqualTo(200);
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.STOPPED);
        verify(tasks).syncState("p1", "stopped", null);
    }
    private final PipelineRepository repo = mock(PipelineRepository.class);
    private final DslCompiler compiler = mock(DslCompiler.class);
    private final NifiClient nifi = mock(NifiClient.class);
    private final PipelineGroupOrganizer groups = mock(PipelineGroupOrganizer.class);
    private final NifiDeploymentMagicFixture.TaskOperations tasks = mock(NifiDeploymentMagicFixture.TaskOperations.class);
    private final AtomicReference<Pipeline> stored = new AtomicReference<>();
    private final TargetTableCleanupService cleanup = mock(TargetTableCleanupService.class);
    private final ErrorService errors = mock(ErrorService.class);
    private final NifiDeploymentMagicFixture deployment = new NifiDeploymentMagicFixture(repo, compiler, nifi, errors, tasks, groups, cleanup);
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
    void repeatedSaveReusesRunningGroupWithoutStoppingIt() {
        Pipeline p = stored.get();
        stored.set(pipeline("old-group", deployedHash(p.dsl()), PipelineStatus.RUNNING));
        when(groups.isCurrent(any(), eq("old-group"))).thenReturn(true);
        var response = deployment.deploy("p1", null, false);
        assertThat(response.getStatusCode().value()).as("%s", response.getBody()).isEqualTo(200);
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.RUNNING);
        verifyNoInteractions(compiler, nifi);
    }

    @Test
    void assignedNodeIsBoundBeforeGroupDiscoveryAndIsNeverReplacedByTheDefault() {
        Pipeline prior = stored.get();
        stored.set(new Pipeline(prior.id(), prior.name(), prior.description(), prior.createdAt(), prior.updatedAt(),
                prior.dsl(), prior.nifiProcessGroupId(), prior.status(), deployedHash(prior.dsl()), prior.lastDeployedAt(),
                prior.lastStoppedAt(), new NifiNodeMapping(null, null, null).withRuntimeNode("secondary"), null));
        when(groups.isCurrent(any(), eq("old-group"))).thenReturn(true);
        var scope = mock(NifiClient.RuntimeScope.class);
        when(nifi.bindAssignedNode("secondary")).thenReturn(scope);

        assertThat(deployment.deploy("p1", null, false).getStatusCode().value()).isEqualTo(200);

        var order = inOrder(nifi, groups, scope);
        order.verify(nifi).bindAssignedNode("secondary");
        order.verify(groups).prepare(any());
        order.verify(scope).close();
        verify(nifi, never()).bindRuntimeNode();
    }

    @Test
    void cleanupFailurePreventsCreation() {
        doThrow(new IllegalStateException("still running")).when(nifi).cleanupProcessGroup("old-group");
        assertThat(deployment.deploy("p1", null, false).getStatusCode().value()).isEqualTo(500);
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("old-group");
        verify(compiler).preflightSources(any());
        verify(compiler, never()).compile(any(), any(), any());
    }
    @Test
    void missingDateFetchExtensionPreservesOldGroupAndQueuedData() {
        doThrow(new IllegalStateException("GenerateDateTableFetch unavailable")).when(compiler).preflightSources(any());
        var before = stored.get();
        var response = deployment.deploy("p1", null, false);
        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("old-group");
        assertThat(stored.get().status()).isEqualTo(before.status());
        verify(nifi, never()).cleanupProcessGroup(anyString());
        verify(compiler, never()).compile(any(), any(), any());
    }
    @Test
    void unavailableLinewellBundlePreservesOldGroupBeforeStoppingOrCleanup() {
        doThrow(new IllegalStateException("当前 NiFi 未注册可用的 LinewellPutDatabaseRecord Bundle；自动选择范围：com.linewell.microservice:datadevs-database-nar 的最高已注册版本"))
                .when(compiler).preflightWriters(any());
        var before = stored.get();
        var response = deployment.deploy("p1", null, false);
        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().toString()).contains("JDBC_WRITER_PREFLIGHT_FAILED").contains("LinewellPutDatabaseRecord").contains("最高已注册版本");
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo(before.nifiProcessGroupId());
        assertThat(stored.get().status()).isEqualTo(before.status());
        verify(nifi, never()).requireEmptyQueuesForRedeploy(anyString());
        verify(nifi, never()).cleanupProcessGroup(anyString());
        verify(compiler, never()).compile(any(), any(), any());
    }
    @Test
    void missingHiveGlobalServicesPreserveExistingGroupBeforeAnyCleanup() {
        var before=stored.get();
        var dsl=new Pipeline.Dsl(1,List.of(new Pipeline.Node("hive","sink.hive","Hive","sink",0,0,Map.of())),List.of());
        stored.set(new Pipeline(before.id(),before.name(),null,1L,1L,dsl,"old-group",PipelineStatus.STOPPED,"old-hash",1L,null,null,null));
        doThrow(new IllegalStateException("NiFi 根作用域缺少全局服务")).when(compiler).preflightGlobalRecordServices(any());
        var response=deployment.deploy("p1", null, false);
        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("old-group");
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.STOPPED);
        verify(nifi,never()).cleanupProcessGroup(anyString());
        verify(compiler,never()).compile(any(),any(),any());
        verify(groups,never()).prepare(any());
    }

    @Test
    void queuedDataPreventsCleanupAndReplacement() {
        doThrow(new IllegalStateException("旧流程还有 3 条排队数据"))
                .when(nifi).requireEmptyQueuesForRedeploy("old-group");
        assertThat(deployment.deploy("p1", null, false).getStatusCode().value()).isEqualTo(400);
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("old-group");
        verify(nifi, never()).cleanupProcessGroup(anyString());
        verify(compiler).preflightSources(any());
        verify(compiler, never()).compile(any(), any(), any());
    }

    @Test
    void confirmedQueueDiscardValidatesReplacementBeforeTouchingOldGroup() {
        var original = stored.get();
        stored.set(new Pipeline(original.id(), original.name(), null, 1L, 1L, original.dsl(), "old-group",
                PipelineStatus.STOPPED, "old-hash", 1L, null,
                new NifiNodeMapping(Map.of(), Map.of(), Map.of()), null));
        var status = JsonNodeFactory.instance.objectNode();
        status.putObject("processGroupStatus").putObject("aggregateSnapshot").put("queuedCount", 3);
        when(nifi.getProcessGroupStatus("old-group")).thenReturn(status);
        compileNewGroup();
        var options = Map.of("forceRebuild", true, "discardQueueFor", "old-group");

        assertThat(deployment.deploy("p1", Map.of("forceRebuild", true, "discardQueueFor", "other-group"), false)
                .getStatusCode().value()).isEqualTo(409);
        doThrow(new IllegalStateException("invalid replacement")).doNothing()
                .when(nifi).requireValidGeneratedProcessGroup("new-group");
        assertThat(deployment.deploy("p1", options, false).getStatusCode().value()).isEqualTo(409);
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("old-group");
        verify(nifi, never()).cleanupProcessGroup("old-group");

        clearInvocations(nifi);
        assertThat(deployment.deploy("p1", options, false).getStatusCode().value()).isEqualTo(200);
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("new-group");
        var sequence = inOrder(nifi);
        sequence.verify(nifi).requireValidGeneratedProcessGroup("new-group");
        sequence.verify(nifi).waitForAllProcessorsStopped("old-group", 30_000L);
        sequence.verify(nifi).cleanupProcessGroup("old-group");
        verify(nifi, never()).requireEmptyQueuesForRedeploy("old-group");
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
        assertThat(deployment.deploy("p1", null, false).getStatusCode().value()).isEqualTo(400);
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("partial-group");
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.DEPLOY_FAILED);

        doAnswer(i -> {
            Consumer<String> callback = i.getArgument(1);
            callback.accept("final-group");
            return new DslCompiler.CompileResult("final-group", Map.of(), Map.of(), Map.of());
        }).when(compiler).compile(any(), any(), any());
        assertThat(deployment.deploy("p1", null, false).getStatusCode().value()).isEqualTo(200);
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
        when(groups.existingIds(any(), anyList())).thenReturn(List.of("task-only-group"));
        assertThat(deployment.undeploy("p1").getStatusCode().value()).isEqualTo(200);
        verify(nifi).cleanupProcessGroup("task-only-group");
        verify(tasks).syncState("p1", "group", null);
        assertThat(stored.get().lastDeployedHash()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FULL", "FULL_THEN_INCR"})
    void explicitTargetCleanupRebuildsEvenUnchangedFlowBeforeClearingAndStarting(String mode) {
        setFullLoad(mode, true);
        when(groups.isCurrent(any(), eq("old-group"))).thenReturn(true);
        compileNewGroup();

        assertThat(deployment.start("p1", true, true).getStatusCode().value()).isEqualTo(200);

        var order = inOrder(nifi, compiler, cleanup, tasks);
        order.verify(nifi).cleanupProcessGroup("old-group");
        order.verify(compiler).compile(any(), any(), any());
        order.verify(cleanup).clear(any());
        order.verify(nifi).setProcessGroupState("new-group", "RUNNING");
        order.verify(tasks).syncState("p1", "started", null);
        verify(cleanup, times(1)).clear(any());
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("new-group");
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.RUNNING);
    }

    @Test
    void targetCleanupFailureKeepsNewFlowStoppedAndDoesNotReportStarted() {
        setFullLoad("FULL", true);
        compileNewGroup();
        doThrow(new IllegalStateException("Hive 清理失败")).when(cleanup).clear(any());

        assertThat(deployment.start("p1", true, true).getStatusCode().value()).isEqualTo(400);

        verify(nifi, never()).setProcessGroupState(anyString(), eq("RUNNING"));
        verify(tasks, never()).syncState(anyString(), eq("started"), any());
        verify(tasks).syncState("p1", "status", 2);
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.DEPLOY_FAILED);
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("new-group");
        assertThat(stored.get().lastDeployedHash()).isNull();
    }

    @Test
    void failureToStopOldFlowPreventsTargetCleanup() {
        setFullLoad("FULL", true);
        doThrow(new IllegalStateException("still running")).when(nifi).cleanupProcessGroup("old-group");
        assertThat(deployment.start("p1", true, true).getStatusCode().value()).isEqualTo(500);
        verifyNoInteractions(cleanup);
        verify(compiler).preflightSources(any());
        verify(compiler, never()).compile(any(), any(), any());
        verify(nifi, never()).setProcessGroupState(anyString(), eq("RUNNING"));
    }

    @Test
    void deploymentWithoutStartingNeverClearsTarget() {
        setFullLoad("FULL", true);
        compileNewGroup();
        assertThat(deployment.deploy("p1", null, false).getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(cleanup);
        verify(nifi, never()).setProcessGroupState(anyString(), eq("RUNNING"));
    }

    @Test
    void neitherForceNorDeploymentAllowsCleanupWithoutExplicitAcknowledgement() {
        setFullLoad("FULL", true);
        assertThat(deployment.start("p1", true).getStatusCode().value()).isEqualTo(409);
        assertThat(deployment.start("p1").getStatusCode().value()).isEqualTo(409);
        verifyNoInteractions(nifi, compiler, cleanup, tasks);
    }

    @Test
    void periodicTruncateNeedsAcknowledgementButDoesNotUseOneTimeCleanup() {
        var source = new Pipeline.Node("source", "source.mysql", "来源", "source", 0, 0,
                Map.of("syncMode", "PERIODIC_FULL", "fullSyncStrategy", "TRUNCATE_RELOAD"));
        var dsl = new Pipeline.Dsl(1, List.of(source), List.of());
        stored.set(new Pipeline("p1", "流程", null, 1L, 1L, dsl, "old-group",
                PipelineStatus.STOPPED, deployedHash(dsl), 1L, null, null, null));
        assertThat(deployment.start("p1").getStatusCode().value()).isEqualTo(409);
        verifyNoInteractions(nifi, compiler, cleanup);
        assertThat(deployment.start("p1", false, true).getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(cleanup, compiler);
        verify(nifi).setProcessGroupState("old-group", "RUNNING");
    }

    @Test
    void uncheckedUnchangedFlowStillUsesFastStart() {
        setFullLoad("FULL", false);
        assertThat(deployment.start("p1").getStatusCode().value()).isEqualTo(200);
        verifyNoInteractions(compiler);
        verify(nifi, never()).cleanupProcessGroup(anyString());
        verify(nifi).setProcessGroupState("old-group", "RUNNING");
    }

    @ParameterizedTest
    @CsvSource({
        "RUNNING, absent, absent, true",
        "STOPPED, absent, absent, false",
        "STOPPED, '3 / 10', absent, true",
        "STOPPED, '4 / 9', STOPPED, false",
        "STOPPING, '4 / 9', DISABLED, false",
        "RUNNING, '4 / 9', INVALID, true",
        "STOPPED, 0, running, true",
        "STOPPED, 0, STARTING, true",
        "RUNNING, unknown, absent, false",
        "STOPPED, 12, disconnected, true"
    })
    void startOnlyRetriesWhenNativeStateRequiresIt(PipelineStatus local, String activeThreads, String processorState,
                                                  boolean alreadyRunning) throws Exception {
        stored.set(pipeline("old-group", "old-hash", local));
        if (!activeThreads.equals("absent")) {
            var mapper = new ObjectMapper();
            var raw = mapper.createObjectNode();
            var snapshot = raw.putObject("processGroupStatus").putObject("aggregateSnapshot");
            if (activeThreads.matches("[0-9]+")) snapshot.put("activeThreadCount", Integer.parseInt(activeThreads));
            else snapshot.put("activeThreadCount", activeThreads);
            when(nifi.getProcessGroupStatus("old-group")).thenReturn(raw);
            if (processorState.equals("disconnected")) {
                when(nifi.get("/flow/process-groups/old-group", JsonNode.class))
                        .thenThrow(new NifiException("unavailable", 503, null));
            } else {
                var flow = mapper.createObjectNode();
                var processors = flow.putObject("processGroupFlow").putObject("flow").putArray("processors");
                if (!processorState.equals("absent")) processors.addObject().putObject("component").put("state", processorState);
                when(nifi.get("/flow/process-groups/old-group", JsonNode.class)).thenReturn(flow);
            }
        }
        var response = deployment.start("p1");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map<?, ?>) response.getBody()).get("mode")).isEqualTo(alreadyRunning ? "already-running" : "fast-restart");
        verify(nifi, times(alreadyRunning ? 0 : 1)).setProcessGroupState("old-group", "RUNNING");
        verify(nifi, times(activeThreads.equals("absent") ? 0 : 1)).get("/flow/process-groups/old-group", JsonNode.class);
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.RUNNING);
    }

    @Test
    void fullDatabaseSourceUsesOneShotExecutionEvenWhenDownstreamIsRunning() {
        setFullLoad("FULL", false);
        when(compiler.supportsSyncSettings("source.jdbc")).thenReturn(true);
        Pipeline p = stored.get();
        stored.set(new Pipeline(p.id(), p.name(), p.description(), p.createdAt(), p.updatedAt(), p.dsl(),
                p.nifiProcessGroupId(), p.status(), p.lastDeployedHash(), p.lastDeployedAt(), p.lastStoppedAt(),
                new NifiNodeMapping(Map.of("source", "full-fetch"), Map.of("full-fetch", "source"), Map.of()), null));
        when(nifi.startWithOneShotSourcesIfPresent("old-group", java.util.Set.of("full-fetch"))).thenReturn(true);
        assertThat(deployment.start("p1").getStatusCode().value()).isEqualTo(200);
        verify(nifi).startWithOneShotSourcesIfPresent("old-group", java.util.Set.of("full-fetch"));
        verify(nifi, never()).setProcessGroupState(anyString(), eq("RUNNING"));
        clearInvocations(nifi);
        doThrow(new IllegalStateException("上一轮仍有排队数据")).when(nifi).startWithOneShotSourcesIfPresent(anyString(), anySet());
        assertThat(deployment.start("p1").getStatusCode().value()).isEqualTo(409);
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.RUNNING);
        verify(nifi, never()).cleanupProcessGroup(anyString());
    }

    @Test
    void normalStartRejectsAnUndeployedPipelineInsteadOfCompilingIt() {
        stored.set(pipeline(null, null, PipelineStatus.SAVED));

        assertThat(deployment.start("p1").getStatusCode().value()).isEqualTo(409);

        verifyNoInteractions(compiler, nifi);
    }

    @Test
    void normalStartUsesTheBoundGroupWithADifferentSavedDesign() {
        stored.set(pipeline("old-group", "stale-hash", PipelineStatus.STOPPED));

        assertThat(deployment.start("p1").getStatusCode().value()).isEqualTo(200);

        verifyNoInteractions(compiler);
        verify(nifi).setProcessGroupState("old-group", "RUNNING");
        verify(nifi, never()).resumeStoppedProcessors(anyString());
        assertThat(stored.get().lastDeployedHash()).isEqualTo("stale-hash");
    }

    @Test
    void normalStartRejectsTheBrokenFullFetchButPreservesDeployedIncrementalFetch() {
        setFullLoad("FULL", false);
        var p = stored.get();
        var source = p.dsl().nodes().getFirst();
        stored.set(new Pipeline(p.id(), p.name(), p.description(), p.createdAt(), p.updatedAt(), p.dsl(),
                p.nifiProcessGroupId(), p.status(), p.lastDeployedHash(), p.lastDeployedAt(), p.lastStoppedAt(),
                new NifiNodeMapping(Map.of(source.id(), "fetch"), Map.of("fetch", source.id()), Map.of()), null));
        when(compiler.usesStandardJdbcFullFetch(source)).thenReturn(true);
        var graph = JsonNodeFactory.instance.objectNode();
        var properties = graph.putArray("processors").addObject().putObject("component")
                .put("id", "fetch").put("type", "com.linewell.microservice.processors.database.GenerateDateTableFetch")
                .putObject("config").putObject("properties").put("gen-table-column-for-val-partitioning", "updated_at");
        when(nifi.get("/process-groups/old-group/processors?includeDescendantGroups=true", JsonNode.class)).thenReturn(graph);

        assertThat(deployment.start("p1").getStatusCode().value()).isEqualTo(409);
        verify(nifi, never()).setProcessGroupState(anyString(), anyString());
        properties.put("Maximum-value Columns", "updated_at");
        assertThat(deployment.start("p1").getStatusCode().value()).isEqualTo(200);
        verify(nifi).setProcessGroupState("old-group", "RUNNING");
    }

    @Test
    void candidateCanDeployWithoutReadingOldProcessorStateOrChangingTheActiveGroup() {
        compileNewGroup();
        when(groups.prepareCandidate(any())).thenReturn(new PipelineGroupOrganizer.Layout("top", "task", "application",
                "流程 · 候选", "owned:candidate", new PipelineGroupOrganizer.Position(520, 0), List.of()));
        var p = stored.get();
        stored.set(new Pipeline(p.id(), p.name(), p.description(), p.createdAt(), p.updatedAt(), p.dsl(),
                p.nifiProcessGroupId(), PipelineStatus.RUNNING, p.lastDeployedHash(), p.lastDeployedAt(), null,
                new NifiNodeMapping(Map.of("old-source", "deleted-processor"), Map.of("deleted-processor", "old-source"), Map.of()), 0L));
        var processors = JsonNodeFactory.instance.objectNode();
        processors.putArray("processors");
        when(nifi.get("/process-groups/new-group/processors?includeDescendantGroups=true", JsonNode.class)).thenReturn(processors);

        var result = deployment.deploy("p1", Map.of("preserveExisting", true), false);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("old-group");
        assertThat(stored.get().status()).isEqualTo(PipelineStatus.RUNNING);
        assertThat(stored.get().nodeMapping().candidate().processGroupId()).isEqualTo("new-group");
        verify(groups).prepareCandidate(any());
        verify(groups, never()).prepare(any());
        verify(nifi, never()).get(contains("/state"), eq(JsonNode.class));
        verify(nifi, never()).getProcessGroupStatus("old-group");
        verify(nifi, never()).cleanupProcessGroup(anyString());
        verify(nifi, never()).setProcessGroupState(anyString(), anyString());
        verify(tasks, never()).syncState(anyString(), eq("group"), any());

        when(nifi.getProcessGroupStatus("old-group")).thenThrow(new NifiException("missing", 404, null));
        var status = JsonNodeFactory.instance.objectNode();
        status.putObject("processGroupStatus").putObject("aggregateSnapshot").put("activeThreadCount", 0);
        when(nifi.getProcessGroupStatus("new-group")).thenReturn(status);
        when(nifi.getProcessGroup("new-group")).thenReturn(JsonNodeFactory.instance.objectNode().put("runningCount", 0));

        var activated = deployment.activate("p1", "new-group");
        assertThat(activated.getStatusCode().value()).isEqualTo(200);
        assertThat(activated.getBody()).isEqualTo(Map.of("mode", "candidate-activated", "pipeline", stored.get(),
                "started", false, "processGroupId", "new-group"));
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("new-group");
        assertThat(stored.get().nodeMapping().candidate()).isNull();
        verify(tasks).syncState("p1", "group", "new-group");
    }

    @Test
    void forceStartRebuildsAnUnchangedDeployment() {
        Pipeline p = stored.get();
        stored.set(pipeline("old-group", deployedHash(p.dsl()), PipelineStatus.STOPPED));
        when(groups.isCurrent(any(), eq("old-group"))).thenReturn(true);
        compileNewGroup();

        assertThat(deployment.start("p1", true).getStatusCode().value()).isEqualTo(200);

        verify(nifi).cleanupProcessGroup("old-group");
        verify(compiler).compile(any(), any(), any());
        verify(nifi).setProcessGroupState("new-group", "RUNNING");
        verify(tasks).syncState("p1", "started", null);
        assertThat(stored.get().nifiProcessGroupId()).isEqualTo("new-group");
    }

    @Test
    void explicitRedeployRebuildsEvenWhenDesignIsUnchanged() {
        Pipeline p = stored.get();
        stored.set(pipeline("old-group", deployedHash(p.dsl()), PipelineStatus.STOPPED));
        when(groups.isCurrent(any(), eq("old-group"))).thenReturn(true);
        compileNewGroup();

        assertThat(deployment.deploy("p1", Map.of("forceRebuild", true), false)
                .getStatusCode().value()).isEqualTo(200);

        verify(nifi).requireEmptyQueuesForRedeploy("old-group");
        verify(nifi).cleanupProcessGroup("old-group");
        verify(compiler).compile(any(), any(), any());
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
        return DslHasher.deploymentHash(dsl, DslHasher.CURRENT_COMPILER_REVISION);
    }
}
