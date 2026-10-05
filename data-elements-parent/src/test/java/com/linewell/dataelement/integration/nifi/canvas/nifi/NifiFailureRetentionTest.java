package com.linewell.dataelement.integration.nifi.canvas.nifi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.model.nifi.NifiEntity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NifiFailureRetentionTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void retainsSupportedFailureAndRetryWithoutDuplicatingExistingFailureRoutes() throws Exception {
        var client = spy(new NifiClient(null, json));
        JsonNode flow = json.readTree("""
                {"processGroupFlow":{"flow":{"processors":[
                  {"id":"fetch","component":{"type":"x.GenerateTableFetch","relationships":[{"name":"failure"},{"name":"success"}]}},
                  {"id":"route","component":{"type":"x.RouteOnAttribute","relationships":[{"name":"unmatched"},{"name":"ok"}]}},
                  {"id":"execute","component":{"type":"x.ExecuteSQLRecord","relationships":[{"name":"failure"},{"name":"success"}]}},
                  {"id":"put","component":{"type":"x.LinewellPutDatabaseRecord","relationships":[{"name":"failure"},{"name":"retry"},{"name":"success"}]}}
                ],"connections":[
                  {"id":"existing","component":{"source":{"id":"execute"},"destination":{"id":"route"},"selectedRelationships":["failure"]}}
                ]}}}
                """);
        doReturn(flow).when(client).get("/flow/process-groups/pg", JsonNode.class);
        doReturn(new NifiEntity(null, Map.of("id", "loop"), null, null)).when(client)
                .createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
        doReturn(null).when(client).putWithRevision(anyString(), anyString(), any(ObjectNode.class));

        client.finalizeGeneratedProcessGroup("pg");

        verify(client, times(1)).createConnection("pg", "put", "PROCESSOR", "put", "PROCESSOR", List.of("failure", "retry"));
        var update = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).putWithRevision(eq("/processors/put"), eq("/processors/put"), update.capture());
        assertThat(update.getValue().path("config").path("autoTerminatedRelationships").toString()).isEqualTo("[\"success\"]");
        verify(client).putWithRevision(eq("/connections/loop"), eq("/connections/loop"), update.capture());
        assertThat(update.getValue().path("flowFileExpiration").asText()).isEqualTo("0 sec");
        assertThat(update.getValue().path("bends").size()).isEqualTo(2);
        verify(client).putWithRevision(eq("/processors/fetch"), eq("/processors/fetch"), update.capture());
        assertThat(update.getValue().path("config").path("autoTerminatedRelationships").toString()).contains("failure");
    }

    @Test
    void sendsEmptyAutoTerminationListWhenEveryRelationshipIsConnected() throws Exception {
        var client = spy(new NifiClient(null, json));
        doReturn(json.readTree("""
                {"processGroupFlow":{"flow":{"processors":[
                  {"id":"p","component":{"type":"x.QueryRecord","relationships":[{"name":"failure"},{"name":"success"}]}}
                ],"connections":[
                  {"id":"success","component":{"source":{"id":"p"},"destination":{"id":"sink"},"selectedRelationships":["success"]}}
                ]}}}
                """)).when(client).get("/flow/process-groups/pg", JsonNode.class);
        doReturn(new NifiEntity(null, Map.of("id", "loop"), null, null)).when(client)
                .createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
        doReturn(null).when(client).putWithRevision(anyString(), anyString(), any(ObjectNode.class));
        client.finalizeGeneratedProcessGroup("pg");
        var update = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).putWithRevision(eq("/processors/p"), eq("/processors/p"), update.capture());
        assertThat(update.getValue().path("config").path("autoTerminatedRelationships").isEmpty()).isTrue();
    }

    @Test
    void addsRetryToExistingFailureLoopInsteadOfOverlappingAnotherLoop() throws Exception {
        var client = spy(new NifiClient(null, json));
        doReturn(json.readTree("""
                {"processGroupFlow":{"flow":{"processors":[
                  {"id":"p","component":{"type":"x.PutDatabaseRecord","relationships":[{"name":"failure"},{"name":"retry"},{"name":"success"}]}}
                ],"connections":[
                  {"id":"existing","component":{"source":{"id":"p"},"destination":{"id":"p"},"selectedRelationships":["failure"]}}
                ]}}}
                """)).when(client).get("/flow/process-groups/pg", JsonNode.class);
        doReturn(null).when(client).putWithRevision(anyString(), anyString(), any(ObjectNode.class));
        client.finalizeGeneratedProcessGroup("pg");
        verify(client, never()).createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
        var update = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client, times(2)).putWithRevision(eq("/connections/existing"), eq("/connections/existing"), update.capture());
        assertThat(update.getAllValues().getFirst().path("selectedRelationships").toString()).isEqualTo("[\"failure\",\"retry\"]");
    }

    @Test
    void refusesRedeployWithQueuedRecordsWithoutDroppingThem() throws Exception {
        var client = spy(new NifiClient(null, json));
        doReturn(json.createObjectNode()).when(client).getProcessGroup("pg");
        doReturn(null).when(client).setProcessGroupState("pg", "STOPPED");
        doNothing().when(client).waitForAllProcessorsStopped(anyString(), anyLong());
        doReturn(json.readTree("{\"processGroupStatus\":{\"aggregateSnapshot\":{\"queuedCount\":3}}}"))
                .when(client).getProcessGroupStatus("pg");
        assertThatThrownBy(() -> client.requireEmptyQueuesForRedeploy("pg"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("3").hasMessageContaining("保留");
        verify(client, never()).dropFlowFiles(anyString(), anyLong());
        verify(client, never()).deleteProcessGroup(anyString());
    }
}
