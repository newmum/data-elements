package com.linewell.dataelement.integration.nifi.canvas.nifi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.*;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NifiPublicationTransportTest {
    @Test void groupStartCannotRestartAManualBackfillEvenAfterItHasStopped()throws Exception {
        var client=spy(new NifiClient(mock(NifiNodeRuntimeResolver.class),mapper));
        var graph=mapper.createObjectNode();var rows=graph.putArray("processors");
        for(String id:List.of("main","backfill")){
            var row=rows.addObject().put("id",id);row.putObject("revision").put("version",1);
            var cfg=row.putObject("component").put("state","STOPPED").putObject("config");
            if(id.equals("backfill"))cfg.put("comments",NifiClient.MANUAL_BACKFILL_MARKER);
        }
        doReturn(graph).when(client).get("/process-groups/group/processors?includeDescendantGroups=true",JsonNode.class);
        doReturn(mapper.createObjectNode()).when(client).put(eq("/flow/process-groups/group"),any(),eq(JsonNode.class));
        client.setProcessGroupState("group","RUNNING");
        var body=org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(client).put(eq("/flow/process-groups/group"),body.capture(),eq(JsonNode.class));
        assertThat(((JsonNode)body.getValue()).path("components").has("main")).isTrue();
        assertThat(((JsonNode)body.getValue()).path("components").has("backfill")).isFalse();
    }
    final ObjectMapper mapper = new ObjectMapper();
    @ParameterizedTest @ValueSource(ints={20,100})
    void servicesUseTwoBulkReadsAndOneActivationRegardlessOfSize(int count) {
        var client=spy(new NifiClient(mock(NifiNodeRuntimeResolver.class),mapper));
        var initial=mapper.createObjectNode();var rows=initial.putArray("controllerServices");
        Set<String> ids=new LinkedHashSet<>();
        for(int index=0;index<count;index++) {
            String id="service-"+index;ids.add(id);var row=rows.addObject();row.putObject("revision").put("version",2);
            row.putObject("component").put("id",id).put("parentGroupId","group").put("state","DISABLED");
        }
        var ready=initial.deepCopy();ready.path("controllerServices").forEach(row->((com.fasterxml.jackson.databind.node.ObjectNode)row.path("component")).put("state","ENABLED"));
        String path="/flow/process-groups/group/controller-services?includeAncestorGroups=true&includeDescendantGroups=false";
        doReturn(initial,ready).when(client).get(path,JsonNode.class);
        doReturn(mapper.createObjectNode()).when(client).put(eq("/flow/process-groups/group/controller-services"),any(),eq(JsonNode.class));
        client.enableControllerServices("group",ids,Set.of(),1000);
        verify(client,times(2)).get(path,JsonNode.class);
        var request=org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(client).put(eq("/flow/process-groups/group/controller-services"),request.capture(),eq(JsonNode.class));
        assertThat(((JsonNode)request.getValue()).path("components").size()).isEqualTo(count);
        verify(client,never()).get(startsWith("/controller-services/"),eq(JsonNode.class));
    }
    @Test void readyPublicWriterIsObservedWithoutAnyMutationAndMissingIdsFailClosed() {
        var client=spy(new NifiClient(mock(NifiNodeRuntimeResolver.class),mapper));
        var ready=mapper.createObjectNode();ready.putArray("controllerServices").addObject().putObject("component").put("id","public").put("state","ENABLED");
        doReturn(ready).when(client).get(anyString(),eq(JsonNode.class));
        client.enableControllerServices("group",Set.of(),Set.of("public"),1000);
        verify(client,never()).put(anyString(),any(),eq(JsonNode.class));
        assertThatThrownBy(()->client.enableControllerServices("group",Set.of("foreign"),Set.of(),1000))
                .isInstanceOf(NifiException.class).hasMessageContaining("可见范围");
    }
    @Test void lostStartResponseIsConfirmedWithoutRepeatingTheWriteButPartialStartStillFails() throws Exception {
        for(boolean partial:List.of(false,true)) {
            var client=spy(new NifiClient(mock(NifiNodeRuntimeResolver.class),mapper));
            var failure=new NifiException("replication failed",500,"WebClientServiceException");
            doThrow(failure).when(client).put(eq("/flow/process-groups/group"),any(),eq(JsonNode.class));
            var graph=mapper.readTree("{\"processors\":[{\"component\":{\"state\":\"RUNNING\"}},{\"component\":{\"state\":\""+(partial?"STOPPED":"RUNNING")+"\"}}]}");
            ((com.fasterxml.jackson.databind.node.ArrayNode)graph.path("processors")).addObject().putObject("component")
                    .put("state","STOPPED").putObject("config").put("comments",NifiClient.MANUAL_BACKFILL_MARKER);
            doReturn(graph).when(client).get("/process-groups/group/processors?includeDescendantGroups=true",JsonNode.class);
            if(partial) assertThatThrownBy(()->client.setProcessGroupState("group","RUNNING")).isSameAs(failure);
            else assertThat(client.setProcessGroupState("group","RUNNING").path("confirmedByReadback").asBoolean()).isTrue();
            verify(client,times(1)).put(eq("/flow/process-groups/group"),any(),eq(JsonNode.class));
        }
    }
}
