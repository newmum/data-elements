package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.*;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import com.linewell.dataelement.mapping.core.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MappingUpgradeServiceTest {
    final ObjectMapper json=new ObjectMapper();
    final MappingVersionStore versions=mock(MappingVersionStore.class);
    final PipelineRepository pipelines=mock(PipelineRepository.class);
    final UnifiedMappingService engine=mock(UnifiedMappingService.class);
    final DslCompiler compiler=mock(DslCompiler.class);
    final NifiClient nifi=mock(NifiClient.class);
    final PlatformTransactionManager transactions=mock(PlatformTransactionManager.class);
    Pipeline pipeline;
    Pipeline persisted;
    MappingEngine.Plan plan;
    void fixture(String sourceState)throws Exception{
        plan=MappingEngine.compile(json.readTree("{\"schemaVersion\":\"2.0\",\"rules\":[]}"));
        pipeline=new Pipeline("p","flow","",1L,2L,new Pipeline.Dsl(1,List.of(new Pipeline.Node("m","transform.field-mapping","映射","transform",0,0,Map.of("mappings",plan.document()))),List.of(new Pipeline.Edge("in","s","m",null),new Pipeline.Edge("out","m","t",null))),"group",PipelineStatus.RUNNING,"old-hash",1L,null,new NifiNodeMapping(Map.of("s","source","m","old-map","t","target"),Map.of("source","s","old-map","m","target","t"),Map.of("in",new NifiNodeMapping.EdgeEndpoint("source","old-map","c-in"),"out",new NifiNodeMapping.EdgeEndpoint("old-map","target","c-out"))),0L);
        when(versions.requirePipeline("p")).thenReturn(pipeline);when(engine.compile(any())).thenReturn(new UnifiedMappingService.Compiled(plan,Map.of()));when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(pipelines.update(eq("p"),any())).thenAnswer(call->{persisted=((java.util.function.UnaryOperator<Pipeline>)call.getArgument(1)).apply(pipeline);return persisted;});when(pipelines.updateStatus(eq("p"),any())).thenReturn(pipeline);
        when(nifi.getProcessGroup("group")).thenReturn(json.readTree("{\"component\":{\"comments\":\"data-elements:v1:tenant:pipeline:p\"}}"));
        when(nifi.get("/processors/old-map",JsonNode.class)).thenReturn(json.readTree("{\"component\":{\"parentGroupId\":\"group\",\"config\":{\"properties\":{\"Record Reader\":\"reader\"}}}}"));
        when(nifi.get(contains("/controller-services?"),eq(JsonNode.class))).thenReturn(json.readTree("{\"controllerServices\":[{\"id\":\"reader\",\"component\":{\"type\":\"org.apache.nifi.json.JsonTreeReader\"}},{\"id\":\"writer\",\"component\":{\"type\":\"org.apache.nifi.json.JsonRecordSetWriter\"}}]}"));
        var flow=json.readTree("""
            {"processGroupFlow":{"flow":{"processGroups":[],"processors":[{"id":"source","revision":{"version":1},"component":{"state":"SOURCE_STATE"}},{"id":"old-map","revision":{"version":1},"component":{"state":"RUNNING","config":{"properties":{"Record Writer":"writer"}}}},{"id":"target","revision":{"version":1},"component":{"state":"RUNNING"}},{"id":"new-map","revision":{"version":1},"component":{"state":"STOPPED"}}],"connections":[{"id":"c-in","revision":{"version":1},"component":{"source":{"id":"source","type":"PROCESSOR","groupId":"group"},"destination":{"id":"old-map","type":"PROCESSOR","groupId":"group"},"selectedRelationships":["success"]}},{"id":"c-out","revision":{"version":1},"component":{"source":{"id":"old-map","type":"PROCESSOR","groupId":"group"},"destination":{"id":"target","type":"PROCESSOR","groupId":"group"},"selectedRelationships":["success"]}}]}}}
            """.replace("SOURCE_STATE",sourceState));when(nifi.get("/flow/process-groups/group",JsonNode.class)).thenReturn(flow);
        when(nifi.getProcessGroupStatus("group")).thenReturn(json.readTree("{\"processGroupStatus\":{\"aggregateSnapshot\":{\"flowFilesQueued\":0}}}"));
        when(nifi.get("/processors/new-map",JsonNode.class)).thenReturn(json.readTree("{\"component\":{\"validationErrors\":[]}}"));
        when(nifi.post(eq("/process-groups/group/connections"),any(),eq(JsonNode.class))).thenReturn(json.readTree("{\"id\":\"new-connection\"}"));
        var replacement=new DslCompiler.NodeCompilation();replacement.inletProcessorId="new-map";when(compiler.createMappingReplacement(any(),any(),anyString(),anyString(),anyString(),anyString(),any())).thenReturn(new DslCompiler.MappingReplacement(replacement,"plan-id"));
    }
    MappingUpgradeService service(){return new MappingUpgradeService(versions,pipelines,engine,compiler,nifi,transactions);}
    @Test void upgradeNeverRestartsACompletedSourceAndPreservesEverySourceIdentity()throws Exception{
        fixture("STOPPED");try(var tenant=TenantContext.use("tenant")){var result=service().upgrade("p","m",2L,plan.hash(),true);assertEquals(true,result.get("sourceProcessorIdsPreserved"));}
        verify(nifi).put(eq("/flow/process-groups/group"),argThat((JsonNode body)->body.path("state").asText().equals("RUNNING")&&!body.path("components").has("source")&&body.path("components").has("new-map")),eq(JsonNode.class));
        verify(nifi,never()).setProcessorRunStatus(eq("source"),anyString());verify(nifi,never()).delete(contains("processors/source"));verify(nifi,never()).put(contains("source/state"),any(),any());verify(versions).activateNode(any(),eq("m"),eq("plan-id"),eq("group"));
    }
    @Test void nonemptyQueueAndUnsavedPlanRejectBeforeStoppingOrReplacement()throws Exception{
        fixture("RUNNING");when(nifi.getProcessGroupStatus("group")).thenReturn(json.readTree("{\"processGroupStatus\":{\"aggregateSnapshot\":{\"flowFilesQueued\":1}}}"));
        try(var tenant=TenantContext.use("tenant")){assertEquals("MIGRATION_QUEUE_NOT_EMPTY",assertThrows(MappingException.class,()->service().upgrade("p","m",2L,plan.hash(),true)).code());assertEquals("DRAFT_NOT_SAVED",assertThrows(MappingException.class,()->service().upgrade("p","m",2L,"other",true)).code());}
        verify(nifi,never()).setProcessGroupState(anyString(),anyString());verifyNoInteractions(compiler);
    }
    @Test void invalidCandidateRetainsOldConnectionsAndStopsWithoutClearingState()throws Exception{
        fixture("RUNNING");when(nifi.get("/processors/new-map",JsonNode.class)).thenReturn(json.readTree("{\"component\":{\"validationErrors\":[\"invalid\"]}}"));
        try(var tenant=TenantContext.use("tenant")){assertEquals("MAPPING_UPGRADE_FAILED",assertThrows(MappingException.class,()->service().upgrade("p","m",2L,plan.hash(),true)).code());}
        verify(nifi,never()).delete(contains("c-in"));verify(nifi,never()).delete(contains("c-out"));verifyNoMoreInteractions(transactions);verify(versions,never()).activateNode(any(),anyString(),anyString(),anyString());verify(nifi,never()).cleanupProcessGroup(anyString());
        assertEquals(PipelineStatus.STOPPED,persisted.status());
    }
    @Test void queueGrowingDuringStopLeavesOriginalTopologyAndPersistsStoppedState()throws Exception{
        fixture("RUNNING");
        when(nifi.getProcessGroupStatus("group")).thenReturn(
            json.readTree("{\"processGroupStatus\":{\"aggregateSnapshot\":{\"flowFilesQueued\":0}}}"),
            json.readTree("{\"processGroupStatus\":{\"aggregateSnapshot\":{\"flowFilesQueued\":1}}}"));
        try(var tenant=TenantContext.use("tenant")){assertEquals("MAPPING_UPGRADE_FAILED",assertThrows(MappingException.class,()->service().upgrade("p","m",2L,plan.hash(),true)).code());}
        assertEquals(PipelineStatus.STOPPED,persisted.status());assertEquals(pipeline.nodeMapping(),persisted.nodeMapping());
        verifyNoInteractions(compiler);verify(nifi,never()).delete(anyString());verify(nifi,never()).setProcessorRunStatus(anyString(),anyString());
        verify(nifi,times(2)).setProcessGroupState("group","STOPPED");
    }
    @Test void firstStopWaitFailureIsHandledAndConfirmedStoppedBeforePersistence()throws Exception{
        fixture("RUNNING");doThrow(new IllegalStateException("stop wait unavailable")).doNothing().when(nifi).waitForAllProcessorsStopped("group",30000);
        try(var tenant=TenantContext.use("tenant")){assertEquals("MAPPING_UPGRADE_FAILED",assertThrows(MappingException.class,()->service().upgrade("p","m",2L,plan.hash(),true)).code());}
        assertEquals(PipelineStatus.STOPPED,persisted.status());assertEquals(pipeline.nodeMapping(),persisted.nodeMapping());
        verifyNoInteractions(compiler);verify(nifi,never()).delete(anyString());
    }
    @Test void postStopTreeReadFailureDoesNotNeedAnotherTreeReadToPreserveOriginalGraph()throws Exception{
        fixture("RUNNING");JsonNode flow=nifi.get("/flow/process-groups/group",JsonNode.class);clearInvocations(nifi);
        when(nifi.get("/flow/process-groups/group",JsonNode.class)).thenReturn(flow).thenThrow(new IllegalStateException("tree read unavailable"));
        try(var tenant=TenantContext.use("tenant")){assertEquals("MAPPING_UPGRADE_FAILED",assertThrows(MappingException.class,()->service().upgrade("p","m",2L,plan.hash(),true)).code());}
        assertEquals(PipelineStatus.STOPPED,persisted.status());assertEquals(pipeline.nodeMapping(),persisted.nodeMapping());
        verify(nifi,times(2)).get("/flow/process-groups/group",JsonNode.class);verifyNoInteractions(compiler);verify(nifi,never()).delete(anyString());
    }
    @Test void unconfirmedStopRequiresRecoveryAndNeverClaimsStoppedPersistence()throws Exception{
        fixture("RUNNING");doThrow(new IllegalStateException("stop cannot be confirmed")).when(nifi).waitForAllProcessorsStopped("group",30000);
        try(var tenant=TenantContext.use("tenant")){assertEquals("MAPPING_UPGRADE_RECOVERY_REQUIRED",assertThrows(MappingException.class,()->service().upgrade("p","m",2L,plan.hash(),true)).code());}
        assertNull(persisted);verifyNoInteractions(compiler);verifyNoInteractions(pipelines);verify(nifi,never()).delete(anyString());
    }
    @Test void recoveryNeverOverwritesConcurrentRuntimeIdentity()throws Exception{
        fixture("RUNNING");
        when(nifi.getProcessGroupStatus("group")).thenReturn(
            json.readTree("{\"processGroupStatus\":{\"aggregateSnapshot\":{\"flowFilesQueued\":0}}}"),
            json.readTree("{\"processGroupStatus\":{\"aggregateSnapshot\":{\"flowFilesQueued\":1}}}"));
        Pipeline newer=new Pipeline(pipeline.id(),pipeline.name(),pipeline.description(),pipeline.createdAt(),3L,pipeline.dsl(),pipeline.nifiProcessGroupId(),pipeline.status(),pipeline.lastDeployedHash(),pipeline.lastDeployedAt(),pipeline.lastStoppedAt(),pipeline.nodeMapping(),pipeline.lastBulletinId());
        when(pipelines.update(eq("p"),any())).thenAnswer(call->((java.util.function.UnaryOperator<Pipeline>)call.getArgument(1)).apply(newer));
        try(var tenant=TenantContext.use("tenant")){assertEquals("MAPPING_UPGRADE_RECOVERY_REQUIRED",assertThrows(MappingException.class,()->service().upgrade("p","m",2L,plan.hash(),true)).code());}
        assertNull(persisted);verifyNoInteractions(compiler);verify(nifi,never()).delete(anyString());
    }
    @Test void partiallyFailedDisableStillAttemptsToRestoreOldMappingState()throws Exception{
        fixture("RUNNING");
        when(nifi.put(eq("/flow/process-groups/group"),argThat((JsonNode body)->body.path("state").asText().equals("DISABLED")),eq(JsonNode.class))).thenThrow(new IllegalStateException("disable response failed"));
        try(var tenant=TenantContext.use("tenant")){assertEquals("MAPPING_UPGRADE_FAILED",assertThrows(MappingException.class,()->service().upgrade("p","m",2L,plan.hash(),true)).code());}
        verify(nifi).put(eq("/flow/process-groups/group"),argThat((JsonNode body)->body.path("state").asText().equals("STOPPED")&&body.path("components").has("old-map")),eq(JsonNode.class));
        assertEquals(PipelineStatus.STOPPED,persisted.status());assertEquals("source",persisted.nodeMapping().primaryProcessorIds().get("s"));
    }
    @Test void failedCandidateConnectionCleanupRequiresManualRecovery()throws Exception{
        fixture("RUNNING");JsonNode flow=nifi.get("/flow/process-groups/group",JsonNode.class);clearInvocations(nifi);
        ((com.fasterxml.jackson.databind.node.ArrayNode)flow.path("processGroupFlow").path("flow").path("connections")).addObject().put("id","new-connection").putObject("revision").put("version",1);
        when(nifi.get("/processors/new-map",JsonNode.class)).thenReturn(json.readTree("{\"component\":{\"validationErrors\":[\"invalid\"]}}"));
        doThrow(new IllegalStateException("candidate connection retained")).when(nifi).delete(startsWith("/connections/new-connection?"));
        try(var tenant=TenantContext.use("tenant")){assertEquals("MAPPING_UPGRADE_RECOVERY_REQUIRED",assertThrows(MappingException.class,()->service().upgrade("p","m",2L,plan.hash(),true)).code());}
        assertEquals(PipelineStatus.STOPPED,persisted.status());assertEquals(pipeline.nodeMapping(),persisted.nodeMapping());verify(nifi).setProcessorRunStatus("new-map","DISABLED");
    }
}
