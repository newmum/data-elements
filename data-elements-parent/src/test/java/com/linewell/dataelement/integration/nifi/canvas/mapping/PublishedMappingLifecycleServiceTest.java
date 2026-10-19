package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.*;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PublishedMappingLifecycleServiceTest {
    ObjectMapper json=new ObjectMapper();MappingVersionStore versions=mock(MappingVersionStore.class);PipelineRepository pipelines=mock(PipelineRepository.class);NifiClient nifi=mock(NifiClient.class);UnifiedMappingService engine=mock(UnifiedMappingService.class);
    PublishedMappingLifecycleService service;
    void fixture()throws Exception{
        var mapping=new NifiNodeMapping(Map.of("s","source","m","mapping"),Map.of("source","s","mapping","m"),Map.of("edge",new NifiNodeMapping.EdgeEndpoint("source","mapping","connection")));
        var pipeline=new Pipeline("p","flow","",1L,2L,new Pipeline.Dsl(1,List.of(),List.of()),"group",PipelineStatus.STOPPED,"oldhash",1L,null,mapping,0L);when(versions.requirePipeline("p")).thenReturn(pipeline);when(versions.publishedReferences("p")).thenReturn(Map.of("m","plan"));when(versions.batchPlans(eq("p"),anyCollection())).thenReturn(Map.of("plan",json.createObjectNode()));when(pipelines.updateStatus(eq("p"),any())).thenReturn(pipeline);
        when(nifi.getProcessGroup("group")).thenReturn(json.readTree("{\"component\":{\"comments\":\"data-elements:v1:tenant:pipeline:p\"}}"));
        when(nifi.get("/flow/process-groups/group",JsonNode.class)).thenReturn(json.readTree("""
        {"processGroupFlow":{"flow":{"processGroups":[],"processors":[{"id":"source","revision":{"version":1},"component":{"type":"com.linewell.microservice.processors.database.GenerateDateTableFetch","state":"STOPPED"}},{"id":"mapping","revision":{"version":1},"component":{"type":"com.linewell.dataelement.nifi.HaitongTransformRecord","state":"STOPPED","config":{"properties":{"plan-id":"plan"}}}}],"connections":[{"id":"connection","component":{"source":{"id":"source"},"destination":{"id":"mapping"},"selectedRelationships":["success"]}}]}}}
        """));service=new PublishedMappingLifecycleService(versions,pipelines,nifi);service.setEngine(engine);
    }
    @Test void explicitlyStartsPublishedGraphAndRunsFullFetchOnlyOnceWithoutPublishingDraft()throws Exception{
        fixture();try(var tenant=TenantContext.use("tenant")){var result=service.start("p",2);assertEquals(false,result.get("draftPublished"));assertEquals(1,result.get("oneShotSourceCount"));}
        verify(nifi).put(eq("/flow/process-groups/group"),argThat((JsonNode body)->body.path("state").asText().equals("RUNNING")&&body.path("components").has("mapping")&&!body.path("components").has("source")),eq(JsonNode.class));verify(nifi).put(eq("/processors/source/run-status"),argThat((JsonNode body)->body.path("state").asText().equals("RUN_ONCE")),eq(JsonNode.class));verify(engine).compileBatch(anyMap());verify(nifi,never()).cleanupProcessGroup(anyString());
    }
    @Test void revokedPlanAuthorizationRejectsBeforeNativeReadsOrMutations()throws Exception{
        fixture();when(engine.compileBatch(anyMap())).thenThrow(new MappingException("RESOURCE_FORBIDDEN","revoked"));try(var tenant=TenantContext.use("tenant")){assertEquals("RESOURCE_FORBIDDEN",assertThrows(MappingException.class,()->service.start("p",2)).code());}verifyNoInteractions(nifi);
    }
    @Test void missingSourceOrUnknownSuccessBranchBlocksStart()throws Exception{
        fixture();JsonNode flow=nifi.get("/flow/process-groups/group",JsonNode.class);((com.fasterxml.jackson.databind.node.ArrayNode)flow.path("processGroupFlow").path("flow").path("connections")).add(json.readTree("{\"id\":\"untracked\",\"component\":{\"source\":{\"id\":\"source\"},\"destination\":{\"id\":\"disabled-candidate\"},\"selectedRelationships\":[\"success\"]}}"));try(var tenant=TenantContext.use("tenant")){assertEquals("MAPPING_UPGRADE_RECOVERY_REQUIRED",assertThrows(MappingException.class,()->service.start("p",2)).code());}verify(nifi,never()).put(anyString(),any(),any());
    }
}
