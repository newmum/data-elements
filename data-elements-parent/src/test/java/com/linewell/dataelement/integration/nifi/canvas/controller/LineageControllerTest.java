package com.linewell.dataelement.integration.nifi.canvas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LineageControllerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PipelineRepository repo = mock(PipelineRepository.class);
    private final NifiClient nifi = mock(NifiClient.class);
    private final LineageController controller = new LineageController(repo, nifi);

    private void deployed() {
        var mapping = new NifiNodeMapping(Map.of("m", "head"),
                Map.of("head", "m", "tail", "m", "foreign", "another-node"), Map.of());
        when(repo.findById("owned")).thenReturn(Optional.of(new Pipeline(
                "owned", "test", "", 1L, 1L, new Pipeline.Dsl(1, List.of(), List.of()),
                "group", PipelineStatus.DRAFT, "hash", 1L, null, mapping, null)));
    }

    private JsonNode json(String value) throws Exception { return mapper.readTree(value); }

    private void search(String processor, String id, String results) throws Exception {
        when(nifi.submitProcessorProvenance(processor, 10)).thenReturn(json("{\"provenance\":{\"id\":\"" + id + "\"}}"));
        when(nifi.getProvenance(id)).thenReturn(json("{\"provenance\":{\"finished\":true,\"results\":" + results + "}}"));
    }

    @Test
    void fallsBackWithinSameNodeAndPreservesClusterWithoutFlowfileSecrets() throws Exception {
        deployed();
        search("head", "q1", "{\"provenanceEvents\":[{\"eventId\":999,\"componentId\":\"foreign\"}]}");
        search("tail", "q2", "{\"provenanceEvents\":[{\"eventId\":2,\"componentId\":\"tail\",\"clusterNodeId\":\"cluster\",\"attributes\":[{\"password\":\"must-not-return\"}],\"transitUri\":\"secret\"}]}");
        when(nifi.submitLineage("2", "cluster")).thenReturn(json("{\"lineage\":{\"id\":\"l1\"}}"));
        when(nifi.getLineage("l1", "cluster")).thenReturn(json("{\"lineage\":{\"finished\":true,\"results\":{\"nodes\":[{\"id\":\"event\"}],\"links\":[]}}}"));
        var response = controller.compute("owned", "m", 10);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = mapper.valueToTree(response.getBody());
        assertThat(body.path("anchorEventId").asText()).isEqualTo("2");
        assertThat(body.path("nodes").size()).isEqualTo(1);
        assertThat(body.toString()).doesNotContain("must-not-return", "transitUri", "attributes");
        verify(nifi, never()).submitProcessorProvenance("foreign", 10);
        verify(nifi).deleteProvenance("q1");
        verify(nifi).deleteProvenance("q2");
        verify(nifi).getLineage("l1", "cluster");
        verify(nifi).deleteLineage("l1", "cluster");
    }

    @Test
    void genuineEmptyEventStoreIsNotInventedLineage() throws Exception {
        deployed();
        search("head", "q1", "{\"provenanceEvents\":[]}");
        search("tail", "q2", "{\"provenanceEvents\":[]}");
        var response = controller.compute("owned", "m", 10);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(mapper.valueToTree(response.getBody()).path("nodes").size()).isZero();
        verify(nifi, never()).submitLineage(anyString(), any());
    }

    @Test
    void queryErrorsAreNotPresentedAsSuccessfulEmptyState() throws Exception {
        deployed();
        search("head", "q1", "{\"errors\":[\"repository error\"],\"provenanceEvents\":[]}");
        assertThat(controller.compute("owned", "m", 10).getStatusCode().value()).isEqualTo(502);
        verify(nifi).deleteProvenance("q1");
    }

    @Test
    void parameterBoundsAndUnknownNodesCannotLaunchNativeSearch() {
        deployed();
        assertThat(controller.compute("owned", "m", 0).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.compute("owned", "m", 101).getStatusCode().value()).isEqualTo(400);
        assertThat(controller.compute("owned", "missing", 10).getStatusCode().value()).isEqualTo(400);
        when(repo.findById("unowned")).thenReturn(Optional.empty());
        assertThat(controller.compute("unowned", "m", 10).getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(nifi);
    }
}
