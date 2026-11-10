package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService;
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.*;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UnifiedMappingDeploymentTest {
    private final PipelineRepository repo=mock(PipelineRepository.class);
    private final DslCompiler compiler=mock(DslCompiler.class);
    private final NifiClient nifi=mock(NifiClient.class);
    private final PipelineGroupOrganizer groups=mock(PipelineGroupOrganizer.class);
    private final PipelineTaskLifecycleSynchronizer tasks=mock(PipelineTaskLifecycleSynchronizer.class);
    private final ErrorService errors=mock(ErrorService.class);
    private final PipelineRunController controller=new PipelineRunController(repo,compiler,nifi,errors,tasks,mock(PipelineRuntimeStatusResolver.class),groups,mock(TargetTableCleanupService.class));
    private Pipeline fixture(){
        var p=new Pipeline("p","flow","",1L,2L,new Pipeline.Dsl(1,List.of(
                new Pipeline.Node("s","source.mysql","来源","source",0,0,Map.of()),
                new Pipeline.Node("m","transform.field-mapping","映射","transform",320,0,
                        Map.of("mappings",Map.of("schemaVersion","2.0","rules",List.of())))),
                List.of(new Pipeline.Edge("edge","s","m",null))),"old",PipelineStatus.STOPPED,"previous",3L,null,new NifiNodeMapping(Map.of("s","source"),Map.of("source","s"),Map.of()),0L);
        when(repo.findById("p")).thenReturn(Optional.of(p));
        when(groups.prepare(any())).thenReturn(new PipelineGroupOrganizer.Layout("root","parent","","flow","owned",new PipelineGroupOrganizer.Position(0,0),List.of("old")));
        when(nifi.getProcessGroupStatus("old")).thenReturn(new ObjectMapper().createObjectNode().set("processGroupStatus",new ObjectMapper().createObjectNode().set("aggregateSnapshot",new ObjectMapper().createObjectNode().put("flowFilesQueued",0))));return p;
    }
    @Test void sourceWatermarkPreventsAnyRebuildOrDeletion()throws Exception{
        fixture();when(nifi.get(eq("/processors/source/state"),eq(com.fasterxml.jackson.databind.JsonNode.class))).thenReturn(new ObjectMapper().readTree("{\"componentState\":{\"clusterState\":{\"state\":[{\"key\":\"maxvalue.id\",\"value\":\"100\"}]}}}"));
        var response=controller.deploy("p",null);assertEquals(409,response.getStatusCode().value());
        verify(errors).add(eq("p"), argThat(error -> error.detail().contains("增量水位") && error.message().contains("来源状态")));
        assertTrue(((Map<?,?>)response.getBody()).containsKey("flowError"));
        verify(compiler,never()).compile(any(),any(),any());verify(nifi,never()).cleanupProcessGroup(anyString());verify(nifi,never()).setProcessGroupState(anyString(),anyString());
    }
    @Test void candidateCompileFailureLeavesOldGraphIntact()throws Exception{
        fixture();when(nifi.get(eq("/processors/source/state"),eq(com.fasterxml.jackson.databind.JsonNode.class))).thenReturn(new ObjectMapper().readTree("{\"componentState\":{}}"));
        when(compiler.compile(any(),any(),any())).thenThrow(new IllegalStateException("candidate invalid"));
        assertEquals(409,controller.deploy("p",null).getStatusCode().value());verify(nifi,never()).cleanupProcessGroup("old");verify(nifi,never()).setProcessGroupState(eq("old"),anyString());
    }
    @Test void unavailableBundleRestoresPreviousStatusBeforeAnyNativeMutation() {
        fixture();
        doThrow(new IllegalStateException("extension unavailable")).when(compiler).preflightMapping(any());
        var response=controller.deploy("p",null);
        assertEquals(422,response.getStatusCode().value());
        verify(errors).add(eq("p"), argThat(error -> error.detail().contains("extension unavailable") && error.phase()==FlowError.ErrorPhase.DEPLOYMENT));
        assertTrue(((Map<?,?>)response.getBody()).containsKey("flowError"));
        verify(repo).updateStatus("p",PipelineStatus.STOPPED);
        // Binding selects the trusted tenant/node for the read-only extension preflight.
        // No native flow mutation is allowed when that preflight fails.
        verify(nifi).bindRuntimeNode();
        verifyNoMoreInteractions(nifi);
        verify(compiler,never()).compile(any(),any(),any());
    }

    @Test void nativePropertyErrorsReachTheResponseAndStoredHistory() throws Exception {
        fixture();
        when(nifi.get(eq("/processors/source/state"),eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(new ObjectMapper().readTree("{\"componentState\":{}}"));
        when(compiler.compile(any(),any(),any())).thenThrow(new NifiException("POST /processors failed: 400",400,
                "Record Writer must reference an enabled Controller Service; password=private-value"));
        var response=controller.deploy("p",null);
        assertEquals(409,response.getStatusCode().value());
        verify(errors).add(eq("p"), argThat(error -> error.detail().contains("Record Writer must reference")
                && error.detail().contains("HTTP 400") && !error.detail().contains("private-value")));
        assertFalse(response.getBody().toString().contains("private-value"));
        verify(nifi,never()).cleanupProcessGroup("old");
    }
    private void partitionFixture() {
        fixture();
        var p = new Pipeline("p", "Hive分区任务", "", 1L, 2L, new Pipeline.Dsl(1, List.of(
                new Pipeline.Node("s", "source.mysql", "来源", "source", 0, 0, Map.of()),
                new Pipeline.Node("h", "sink.hive", "分区目标", "sink", 320, 0,
                        Map.of("partitionPlan", Map.of("enabled", true)))),
                List.of(new Pipeline.Edge("edge", "s", "h", null))), "old", PipelineStatus.STOPPED,
                "previous", 3L, null, new NifiNodeMapping(Map.of(), Map.of(), Map.of()), 0L);
        when(repo.findById("p")).thenReturn(Optional.of(p));
    }

    @Test void hivePartitionWithoutMappingUsesCandidatePublicationAndCleansFailedPartialGraph() {
        partitionFixture();
        when(compiler.compile(any(), any(), any())).thenAnswer(call -> {
            call.<java.util.function.Consumer<String>>getArgument(1).accept("candidate");
            throw new IllegalStateException("分区组件创建失败");
        });
        var response = controller.deploy("p", null);
        assertEquals(409, response.getStatusCode().value());
        verify(nifi).cleanupProcessGroup("candidate");
        verify(nifi, never()).cleanupProcessGroup("old");
        verify(nifi, never()).setProcessGroupState(eq("old"), anyString());
        verify(compiler, never()).preflightMapping(any());
        verify(errors).add(eq("p"), argThat(error -> error.message().contains("Hive基础") && error.detail().contains("分区组件创建失败")));
    }

    @Test void manualHiveTemplateStillPreservesAnExistingSourceWatermark() throws Exception {
        partitionFixture();
        var original = repo.findById("p").orElseThrow();
        var pipeline = new Pipeline(original.id(), original.name(), original.description(), original.createdAt(), original.updatedAt(),
                original.dsl(), original.nifiProcessGroupId(), original.status(), original.lastDeployedHash(), original.lastDeployedAt(),
                original.lastStoppedAt(), new NifiNodeMapping(Map.of("s", "source"), Map.of("source", "s"), Map.of()), 0L);
        when(repo.findById("p")).thenReturn(Optional.of(pipeline));
        when(nifi.get(eq("/processors/source/state"), eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .thenReturn(new ObjectMapper().readTree("{\"componentState\":{\"clusterState\":{\"state\":[{\"key\":\"maxvalue.id\",\"value\":\"100\"}]}}}"));
        assertEquals(409, controller.deploy("p", null).getStatusCode().value());
        verify(compiler, never()).compile(any(), any(), any());
        verify(nifi, never()).cleanupProcessGroup(anyString());
        verify(nifi, never()).setProcessGroupState(anyString(), anyString());
    }

    @Test void basicHiveTemplateCanBePublishedForNativeEditingWithoutPartitionValidation() {
        partitionFixture();
        when(compiler.compile(any(), any(), any())).thenAnswer(call -> {
            call.<java.util.function.Consumer<String>>getArgument(1).accept("candidate");
            return new DslCompiler.CompileResult("candidate", Map.of(), Map.of(), Map.of());
        });
        // Native partition-specific configuration is deliberately completed by the operator later.
        doThrow(new IllegalStateException("partition-specific native configuration is incomplete"))
                .when(nifi).requireValidGeneratedProcessGroup("candidate");
        var response = controller.deploy("p", null);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("staged-hive-template-deploy", ((Map<?, ?>) response.getBody()).get("mode"));
        verify(nifi, never()).requireValidGeneratedProcessGroup(anyString());
        verify(nifi, never()).setProcessGroupState("candidate", "RUNNING");
    }

    @Test void basicHiveCandidateIsCreatedBeforeOldGraphIsStopped() {
        partitionFixture();
        when(compiler.compile(any(), any(), any())).thenAnswer(call -> {
            call.<java.util.function.Consumer<String>>getArgument(1).accept("candidate");
            return new DslCompiler.CompileResult("candidate", Map.of(), Map.of(), Map.of());
        });
        var response = controller.deploy("p", null);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("staged-hive-template-deploy", ((Map<?, ?>) response.getBody()).get("mode"));
        var order = inOrder(compiler, nifi);
        order.verify(compiler).compile(any(), any(), any());
        verify(nifi, never()).requireValidGeneratedProcessGroup(anyString());
        order.verify(nifi).setProcessGroupState("old", "STOPPED");
        order.verify(nifi).cleanupProcessGroup("old");
        verify(nifi, never()).cleanupProcessGroup("candidate");
    }

}
