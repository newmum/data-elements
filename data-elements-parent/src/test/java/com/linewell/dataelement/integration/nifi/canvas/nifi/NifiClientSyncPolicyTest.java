package com.linewell.dataelement.integration.nifi.canvas.nifi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.model.nifi.NifiEntity;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class NifiClientSyncPolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final NifiNodeRuntimeResolver runtimeResolver = mock(NifiNodeRuntimeResolver.class);
    private final NifiClient client = spy(new NifiClient(runtimeResolver, mapper));

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void oneShotStartHasTwoReadsAndTwoWritesForAnyNumberOfDownstreamProcessors(int size) {
        var flow = mapper.createObjectNode();
        var processors = flow.putObject("processGroupFlow").putObject("flow").putArray("processors");
        for (int i = 0; i < size; i++) {
            var processor = processors.addObject().put("id", "p" + i);
            processor.putObject("revision").put("version", 0);
            processor.putObject("component").put("state", "STOPPED").put("name", "p" + i);
        }
        doReturn(flow).when(client).get("/flow/process-groups/group", JsonNode.class);
        doReturn(idleStatus()).when(client).getProcessGroupStatus("group");
        doReturn(mapper.createObjectNode()).when(client).put(eq("/flow/process-groups/group"), any(), eq(JsonNode.class));
        var runtimeNode = mock(NifiNodeRuntimeResolver.RuntimeNode.class);
        doReturn(runtimeNode).when(runtimeResolver).resolve();
        doReturn(mock(NifiEntity.class)).when(client).put(eq(runtimeNode), eq("/processors/p0/run-status"), any(), eq(NifiEntity.class));

        client.startWithOneShotSources("group", Set.of("p0"));

        verify(client, times(1)).get("/flow/process-groups/group", JsonNode.class);
        verify(client, times(1)).getProcessGroupStatus("group");
        verify(client, never()).getProcessor(anyString());
        ArgumentCaptor<Object> bodies = ArgumentCaptor.forClass(Object.class);
        verify(client, times(1)).put(eq("/flow/process-groups/group"), bodies.capture(), eq(JsonNode.class));
        JsonNode downstream = (JsonNode) bodies.getAllValues().getFirst();
        assertThat(downstream.path("state").asText()).isEqualTo("RUNNING");
        assertThat(downstream.path("components").size()).isEqualTo(size - 1);
        assertThat(downstream.path("components").has("p0")).isFalse();
        verify(client).put(eq(runtimeNode), eq("/processors/p0/run-status"), bodies.capture(), eq(NifiEntity.class));
        JsonNode source = (JsonNode) bodies.getAllValues().getLast();
        assertThat(source.path("state").asText()).isEqualTo("RUN_ONCE");
        assertThat(source.path("revision").path("version").asInt(-1)).isEqualTo(0);
        assertThat(source.has("components")).isFalse();
        verify(runtimeResolver, times(1)).resolve();
    }

    @Test
    void queuedRecordsPreventAnotherFullRoundWithoutAnyMutation() {
        JsonNode status = idleStatus();
        ((com.fasterxml.jackson.databind.node.ObjectNode) status.at("/processGroupStatus/aggregateSnapshot")).put("queuedCount", 1);
        doReturn(status).when(client).getProcessGroupStatus("group");
        assertThatThrownBy(() -> client.startWithOneShotSources("group", Set.of("source"))).hasMessageContaining("上一轮");
        verify(client, never()).put(anyString(), any(), any());
    }

    @Test
    void serialGateAndTriggerConnectionUseOneFlowfileWithNoLoadBalancing() {
        doReturn(new NifiEntity(null, Map.of("id", "batch"), null, null)).when(client)
                .createProcessGroup("parent", "batch", 700, 120);
        doReturn(mock(NifiEntity.class)).when(client).putWithRevision(anyString(), anyString(), any());
        client.createSerialBatchGroup("parent", "batch", 700, 120);
        ArgumentCaptor<com.fasterxml.jackson.databind.node.ObjectNode> update = ArgumentCaptor.forClass(com.fasterxml.jackson.databind.node.ObjectNode.class);
        verify(client).putWithRevision(eq("/process-groups/batch"), eq("/process-groups/batch"), update.capture());
        assertThat(update.getValue().path("flowfileConcurrency").asText()).isEqualTo("SINGLE_FLOWFILE_PER_NODE");
        doReturn(mock(NifiEntity.class)).when(client).post(eq("/process-groups/parent/connections"), any(), eq(NifiEntity.class));
        client.connectBatchTrigger("parent", "trigger", "batch", "input");
        ArgumentCaptor<Object> request = ArgumentCaptor.forClass(Object.class);
        verify(client).post(eq("/process-groups/parent/connections"), request.capture(), eq(NifiEntity.class));
        JsonNode component = ((JsonNode)request.getValue()).path("component");
        assertThat(component.path("backPressureObjectThreshold").asInt()).isEqualTo(1);
        assertThat(component.path("loadBalanceStrategy").asText()).isEqualTo("DO_NOT_LOAD_BALANCE");
        assertThat(component.at("/destination/groupId").asText()).isEqualTo("batch");
    }

    private JsonNode idleStatus() {
        var root = mapper.createObjectNode();
        root.putObject("processGroupStatus").putObject("aggregateSnapshot").put("queuedCount", 0).put("activeThreadCount", 0);
        return root;
    }

    @Test
    void primaryExecutionUsesTheNativeNifiWireValue() {
        doReturn(mock(NifiEntity.class)).when(client).putWithRevision(anyString(), anyString(), any());
        client.executeOnPrimaryNode("source");
        ArgumentCaptor<com.fasterxml.jackson.databind.node.ObjectNode> update = ArgumentCaptor.forClass(com.fasterxml.jackson.databind.node.ObjectNode.class);
        verify(client).putWithRevision(eq("/processors/source"), eq("/processors/source"), update.capture());
        assertThat(update.getValue().at("/config/executionNode").asText()).isEqualTo("PRIMARY");
        assertThat(update.getValue().at("/config/concurrentlySchedulableTaskCount").asInt()).isEqualTo(1);
    }

    @Test
    void oversizedSourceBatchFailsBeforeRemoteReadsOrWrites() {
        var sources = java.util.stream.IntStream.range(0, 21).mapToObj(i -> "source" + i).collect(java.util.stream.Collectors.toSet());
        assertThatThrownBy(() -> client.startWithOneShotSources("group", sources)).hasMessageContaining("20");
        verify(client, never()).getProcessGroupStatus(anyString());
        verify(client, never()).get(anyString(), any());
        verify(client, never()).put(anyString(), any(), any());
    }
}
