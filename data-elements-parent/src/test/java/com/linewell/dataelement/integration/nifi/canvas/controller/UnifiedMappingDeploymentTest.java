package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService;
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
    private final PipelineRunController controller=new PipelineRunController(repo,compiler,nifi,mock(ErrorService.class),tasks,mock(PipelineRuntimeStatusResolver.class),groups,mock(TargetTableCleanupService.class));
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
        assertEquals(422,controller.deploy("p",null).getStatusCode().value());
        verify(repo).updateStatus("p",PipelineStatus.STOPPED);
        verifyNoInteractions(nifi);
        verify(compiler,never()).compile(any(),any(),any());
    }
}
