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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"GenerateTableFetch","GenerateDateTableFetch"})
    void retainsSupportedFailureAndRetryWithoutDuplicatingExistingFailureRoutes(String fetchType) throws Exception {
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
                """.replace("x.GenerateTableFetch", "x."+fetchType));
        doReturn(flow).when(client).get("/flow/process-groups/pg", JsonNode.class);
        doReturn(new NifiEntity(null, Map.of("id", "loop"), null, null)).when(client)
                .createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
        stubLayoutWrites(client);

        client.finalizeGeneratedProcessGroup("pg");

        verify(client, times(1)).createConnection("pg", "put", "PROCESSOR", "put", "PROCESSOR", List.of("failure", "retry"));
        var update = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).put(eq("/processors/put"), update.capture(), eq(JsonNode.class));
        assertThat(update.getValue().path("component").path("config").path("autoTerminatedRelationships").toString()).isEqualTo("[\"success\"]");
        verify(client).put(eq("/connections/loop"), update.capture(), eq(JsonNode.class));
        assertThat(update.getValue().path("component").path("flowFileExpiration").asText()).isEqualTo("0 sec");
        assertThat(update.getValue().path("component").path("bends").size()).isEqualTo(2);
        verify(client).put(eq("/processors/fetch"), update.capture(), eq(JsonNode.class));
        assertThat(update.getValue().path("component").path("config").path("autoTerminatedRelationships").toString()).contains("failure");
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
        stubLayoutWrites(client);
        client.finalizeGeneratedProcessGroup("pg");
        var update = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).put(eq("/processors/p"), update.capture(), eq(JsonNode.class));
        assertThat(update.getValue().path("component").path("config").path("autoTerminatedRelationships").isEmpty()).isTrue();
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
        stubLayoutWrites(client);
        client.finalizeGeneratedProcessGroup("pg");
        verify(client, never()).createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
        var update = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client, times(2)).put(eq("/connections/existing"), update.capture(), eq(JsonNode.class));
        assertThat(update.getAllValues().getFirst().path("component").path("selectedRelationships").toString()).isEqualTo("[\"failure\",\"retry\"]");
        assertThat(update.getAllValues().getLast().path("revision").path("version").asLong()).isEqualTo(1);
    }

    @Test
    void retainsFailuresAtEveryProcessorInTheHiveHdfsRecordChain() throws Exception {
        var client = spy(new NifiClient(null, json));
        doReturn(json.readTree("""
                {"processGroupFlow":{"flow":{"processors":[
                  {"id":"merge","component":{"type":"org.apache.nifi.processors.standard.MergeRecord","relationships":[{"name":"merged"},{"name":"failure"}]}},
                  {"id":"convert","component":{"type":"org.apache.nifi.processors.standard.ConvertRecord","relationships":[{"name":"success"},{"name":"failure"}]}},
                  {"id":"filename","component":{"type":"org.apache.nifi.processors.attributes.UpdateAttribute","relationships":[{"name":"success"}]}},
                  {"id":"put","component":{"type":"com.linewell.microservice.processor.hadoop.PutHwHDFS","relationships":[{"name":"success"},{"name":"failure"}]}}
                ],"connections":[
                  {"id":"merge-convert","component":{"source":{"id":"merge"},"destination":{"id":"convert"},"selectedRelationships":["merged"]}},
                  {"id":"convert-filename","component":{"source":{"id":"convert"},"destination":{"id":"filename"},"selectedRelationships":["success"]}},
                  {"id":"filename-put","component":{"source":{"id":"filename"},"destination":{"id":"put"},"selectedRelationships":["success"]}}
                ]}}}
                """)).when(client).get("/flow/process-groups/pg", JsonNode.class);
        var sequence = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> new NifiEntity(null, Map.of("id", "failure-loop-" + sequence.incrementAndGet()), null, null))
                .when(client).createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
        stubLayoutWrites(client);

        client.finalizeGeneratedProcessGroup("pg");

        for (String id : List.of("merge", "convert", "put")) {
            verify(client).createConnection("pg", id, "PROCESSOR", id, "PROCESSOR", List.of("failure"));
        }
        verify(client, never()).createConnection(eq("pg"), eq("filename"), eq("PROCESSOR"),
                eq("filename"), eq("PROCESSOR"), anyList());
        var updates = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client).put(eq("/processors/put"), updates.capture(), eq(JsonNode.class));
        assertThat(updates.getValue().path("component").path("config").path("autoTerminatedRelationships").toString())
                .isEqualTo("[\"success\"]");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {20, 100})
    void laysOutInvalidFunnelsWithOneFlowReadAndPreservesRevisionChecks(int count) {
        var client = spy(new NifiClient(null, json));
        ObjectNode response = json.createObjectNode();
        ObjectNode flow = response.putObject("processGroupFlow").putObject("flow");
        var processors = flow.putArray("processors");
        var funnels = flow.putArray("funnels");
        var connections = flow.putArray("connections");
        for (int i = 0; i < count; i++) {
            ObjectNode processor = processors.addObject().put("id", "p" + i);
            processor.putObject("revision").put("version", 7);
            var component = processor.putObject("component").put("type", "x.HaitongTransformRecord");
            component.putObject("position").put("x", i * 500).put("y", 0);
            var relationships = component.putArray("relationships");
            for (String name : List.of("success", "failure", "retry", "invalid")) relationships.addObject().put("name", name);
            ObjectNode funnel = funnels.addObject().put("id", "f" + i);
            funnel.putObject("revision").put("version", 4);
            funnel.putObject("component").putObject("position").put("x", 0).put("y", 0);
            ObjectNode invalid = connections.addObject().put("id", "invalid" + i);
            invalid.putObject("revision").put("version", 6);
            ObjectNode connection = invalid.putObject("component");
            connection.putObject("source").put("id", "p" + i);
            connection.putObject("destination").put("id", "f" + i);
            connection.putArray("selectedRelationships").add("invalid");
        }
        doReturn(response).when(client).get("/flow/process-groups/pg", JsonNode.class);
        var sequence = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> new NifiEntity(null, Map.of("id", "loop" + sequence.incrementAndGet()), null, null))
                .when(client).createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
        stubLayoutWrites(client);

        client.finalizeGeneratedProcessGroup("pg");

        verify(client, times(1)).get(anyString(), eq(JsonNode.class));
        verify(client, never()).putWithRevision(anyString(), anyString(), any(ObjectNode.class));
        var writes = ArgumentCaptor.forClass(ObjectNode.class);
        verify(client, times(count * 4)).put(anyString(), writes.capture(), eq(JsonNode.class));
        var funnelWrites = writes.getAllValues().stream().filter(w -> w.path("component").path("id").asText().matches("f[0-9]+" )).toList();
        assertThat(funnelWrites).hasSize(count).allSatisfy(w -> {
            assertThat(w.path("revision").path("version").asLong()).isEqualTo(4);
            assertThat(w.path("component").path("position").path("x").asDouble()).isGreaterThan(1430);
        });
        assertThat(writes.getAllValues().stream().filter(w -> w.path("component").path("id").asText().startsWith("invalid")))
                .hasSize(count).allSatisfy(w -> {
                    assertThat(w.path("revision").path("version").asLong()).isEqualTo(6);
                    assertThat(w.path("component").path("bends").size()).isGreaterThanOrEqualTo(2);
                    assertThat(w.path("component").path("labelIndex").asInt()).isGreaterThan(0);
                });
    }

    private void stubLayoutWrites(NifiClient client) {
        doAnswer(invocation -> {
            ObjectNode response = ((ObjectNode) invocation.getArgument(1)).deepCopy();
            ObjectNode revision = (ObjectNode) response.path("revision");
            revision.put("version", revision.path("version").asLong() + 1);
            return response;
        }).when(client).put(anyString(), any(ObjectNode.class), eq(JsonNode.class));
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
