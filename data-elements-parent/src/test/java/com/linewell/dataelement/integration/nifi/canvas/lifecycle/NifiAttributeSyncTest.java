package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NifiAttributeSyncTest {
    private static final String LIST = "/process-groups/bound-group/processors?includeDescendantGroups=true";
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final NifiClient nifi = mock(NifiClient.class);
    private final NifiAttributeSync sync = new NifiAttributeSync(nifi);

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void previewAndApplyEachReadTheGroupOnce(int count) {
        var flow = JSON.objectNode();
        var processors = flow.putArray("processors");
        for (int i = 0; i < count; i++) processors.add(processor("processor-" + i, "30 sec"));
        when(nifi.get(LIST, JsonNode.class)).thenReturn(flow);
        when(nifi.put(startsWith("/processors/"), any(ObjectNode.class), eq(JsonNode.class))).thenAnswer(call -> {
            String id = call.<String>getArgument(0).substring("/processors/".length());
            ObjectNode payload = call.getArgument(1);
            assertThat(payload.path("component").path("config").size()).isEqualTo(1);
            assertThat(payload.path("revision").path("version").asInt()).isEqualTo(7);
            return processor(id, payload.path("component").path("config").path("schedulingPeriod").asText());
        });
        Pipeline pipeline = pipeline(count);
        var changes = sync.preview(pipeline).changes();

        assertThat(changes).hasSize(count).allMatch(change -> "CONFLICT".equals(change.status()));
        verify(nifi).get(LIST, JsonNode.class);
        verifyNoMoreInteractions(nifi);
        clearInvocations(nifi);
        var result = sync.apply(pipeline, new NifiAttributeSync.Request("bound-group",
                changes.stream().map(row -> selection(row, false)).toList()));

        assertThat(result.results()).hasSize(count).allMatch(row -> "SYNCED".equals(row.status()));
        verify(nifi).get(LIST, JsonNode.class);
        verify(nifi, times(count)).put(startsWith("/processors/"), any(ObjectNode.class), eq(JsonNode.class));
        verifyNoMoreInteractions(nifi);
    }

    @Test
    void conflictAndUnrelatedIdsDoNotPreventIndependentUpdates() {
        var flow = JSON.objectNode();
        flow.putArray("processors").add(processor("processor-0", "30 sec"))
                .add(processor("processor-1", "30 sec")).add(processor("manual-node", "30 sec"));
        when(nifi.get(LIST, JsonNode.class)).thenReturn(flow);
        when(nifi.put(eq("/processors/processor-0"), any(), eq(JsonNode.class)))
                .thenThrow(new NifiException("stale revision", 409, null));
        Pipeline pipeline = pipeline(3);
        var preview = sync.preview(pipeline);
        var result = sync.apply(pipeline, new NifiAttributeSync.Request("bound-group", List.of(
                selection(preview.changes().get(0), false),
                selection(preview.changes().get(1), true),
                selection(preview.changes().get(2), false),
                new NifiAttributeSync.Selection("node-9", "schedulingPeriod", "manual-node", "30 sec", "60 sec", false))));

        assertThat(result.results()).extracting(NifiAttributeSync.Change::status)
                .containsExactly("RETRY", "KEPT_NATIVE", "UNAVAILABLE", "UNAVAILABLE");
        assertThat(result.baselines().get("node-1").get("schedulingPeriod").nativeValue()).isEqualTo("30 sec");
        verify(nifi, times(2)).get(LIST, JsonNode.class);
        verify(nifi).put(eq("/processors/processor-0"), any(), eq(JsonNode.class));
        verifyNoMoreInteractions(nifi);
    }

    @Test
    void rejectsRequestsForAnyGroupOtherThanTheSavedTenantBinding() {
        var selection = new NifiAttributeSync.Selection("node-0", "schedulingPeriod", "processor-0", "30 sec", "60 sec", false);
        assertThatThrownBy(() -> sync.apply(pipeline(1), new NifiAttributeSync.Request("unrelated-group", List.of(selection))))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(nifi);
    }

    private static NifiAttributeSync.Selection selection(NifiAttributeSync.Change row, boolean keep) {
        return new NifiAttributeSync.Selection(row.nodeId(), row.field(), row.processorId(),
                row.currentValue(), row.designValue(), keep);
    }

    private static Pipeline pipeline(int count) {
        var nodes = new ArrayList<Pipeline.Node>();
        var ids = new LinkedHashMap<String, String>();
        var reverse = new LinkedHashMap<String, String>();
        var baselines = new LinkedHashMap<String, Map<String, NifiNodeMapping.AttributeBaseline>>();
        for (int i = 0; i < count; i++) {
            String node = "node-" + i;
            String processor = "processor-" + i;
            nodes.add(new Pipeline.Node(node, "source.mysql", node, "source", 0, 0, Map.of("schedulingPeriod", "60 sec")));
            ids.put(node, processor);
            reverse.put(processor, node);
            baselines.put(node, Map.of("schedulingPeriod", new NifiNodeMapping.AttributeBaseline(processor, "15 sec", "15 sec")));
        }
        return new Pipeline("flow", "validation", null, 1L, 1L, new Pipeline.Dsl(1, nodes, List.of()),
                "bound-group", PipelineStatus.STOPPED, "previous", 1L, null,
                new NifiNodeMapping(ids, reverse, Map.of()).withAttributes(baselines), 0L);
    }

    private static ObjectNode processor(String id, String period) {
        ObjectNode entity = JSON.objectNode().put("id", id);
        entity.putObject("revision").put("version", 7);
        var config = entity.putObject("component").put("type", "org.apache.nifi.processors.standard.GenerateTableFetch")
                .put("state", "STOPPED").putObject("config");
        config.put("schedulingStrategy", "TIMER_DRIVEN").put("schedulingPeriod", period);
        config.putObject("properties").put("manual-property", "preserved");
        return entity;
    }
}
