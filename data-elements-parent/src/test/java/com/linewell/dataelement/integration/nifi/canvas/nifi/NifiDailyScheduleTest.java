package com.linewell.dataelement.integration.nifi.canvas.nifi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver.RuntimeNode;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.*;
import org.junit.jupiter.api.Test;

class NifiDailyScheduleTest {
    @Test void immediateDailyUsesSavedTimerAndNeedsNoTimezoneRead() {
        var client=mock(NifiClient.class);
        var compiler=new DslCompiler(client,mock(ManifestRegistry.class),null,null);
        for(int count:List.of(20,100)) {
            var nodes=java.util.stream.IntStream.range(0,count).mapToObj(i->new Pipeline.Node("s"+i,"source.sftp","来源","source",0,0,
                    BatchSchedulePolicy.markDefault(Map.of()))).toList();
            compiler.preflightSchedules(new Pipeline("p","validation",null,null,null,new Pipeline.Dsl(1,nodes,List.of()),null,null,null,null,null,null,null));
        }
        verifyNoInteractions(client);
    }
    @Test void twentyAndHundredSourcesReadTimezoneOnceAndNoNodeFromAnotherTenantIsReused() {
        for(int count:List.of(20,100)) {
            var resolver=mock(NifiNodeRuntimeResolver.class);
            when(resolver.resolve()).thenAnswer(a->new RuntimeNode(TenantContext.requireTenantId(),"node","validation","http://validation.invalid","validation","test-only",false,"root"));
            var json=new ObjectMapper();var client=spy(new NifiClient(resolver,json));
            var response=json.createObjectNode();response.putObject("about").put("timezone","Asia/Shanghai");
            doReturn(response).when(client).get("/flow/about",JsonNode.class);
            var compiler=new DslCompiler(client,mock(ManifestRegistry.class),null,null);
            var nodes=java.util.stream.IntStream.range(0,count).mapToObj(i->new Pipeline.Node("s"+i,"source.sftp","来源","source",0,0,
                    BatchSchedulePolicy.defaultConfig("tenant-a","p","s"+i,"source.sftp",BatchSchedulePolicy.markStaggered(Map.of())))).toList();
            var pipeline=new Pipeline("p","validation",null,null,null,new Pipeline.Dsl(1,nodes,List.of()),null,null,null,null,null,null,null);
            try(var tenant=TenantContext.use("tenant-a");var scope=client.bindRuntimeNode()) {
                compiler.preflightSchedules(pipeline);compiler.preflightSchedules(pipeline);
                verify(client,times(1)).get("/flow/about",JsonNode.class);
            }
            try(var tenant=TenantContext.use("tenant-b");var scope=client.bindRuntimeNode()) {client.requireDailyScheduleZone();}
            verify(client,times(2)).get("/flow/about",JsonNode.class);
            verify(resolver,times(2)).resolve();
        }
    }
    @Test void timezoneMismatchAndPendingAssignmentFailBeforeNiFiMutation() {
        var json=new ObjectMapper();var client=spy(new NifiClient(mock(NifiNodeRuntimeResolver.class),json));
        var response=json.createObjectNode();response.putObject("about").put("timezone","UTC");
        doReturn(response).when(client).get("/flow/about",JsonNode.class);
        assertThatThrownBy(client::requireDailyScheduleZone).hasMessageContaining("UTC").hasMessageContaining("Asia/Shanghai");
        verify(client,never()).post(anyString(),any(),any());
    }
    @Test void cstDisplayNameMustBeDisambiguatedByTheNiFiOffset() {
        var json=new ObjectMapper();var client=spy(new NifiClient(mock(NifiNodeRuntimeResolver.class),json));
        var about=json.createObjectNode();about.putObject("about").put("timezone","CST");
        var config=json.createObjectNode();config.putObject("flowConfiguration").put("timeOffset",28800000);
        doReturn(about).when(client).get("/flow/about",JsonNode.class);
        doReturn(config).when(client).get("/flow/config",JsonNode.class);
        client.requireDailyScheduleZone();
        ((com.fasterxml.jackson.databind.node.ObjectNode)config.path("flowConfiguration")).put("timeOffset",-21600000);
        assertThatThrownBy(client::requireDailyScheduleZone).hasMessageContaining("Asia/Shanghai");
    }
}
