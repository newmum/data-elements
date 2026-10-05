package com.linewell.dataelement.integration.nifi.canvas.nifi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NifiClientProvenanceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private NifiClient client(String options) throws Exception {
        var resolver = mock(NifiNodeRuntimeResolver.class);
        when(resolver.resolve()).thenReturn(new NifiNodeRuntimeResolver.RuntimeNode(
                "tenant", "node", "test", "http://nifi.invalid", "unused", "unused", false, "root"));
        var client = spy(new NifiClient(resolver, mapper));
        doReturn(mapper.readTree(options)).when(client).get("/provenance/search-options", JsonNode.class);
        doReturn(mapper.createObjectNode()).when(client).post(eq("/provenance"), any(), eq(JsonNode.class));
        return client;
    }

    @Test
    void usesAdvertisedProcessorFieldAndCachesOnlyNonSecretOptions() throws Exception {
        var client = client("{\"provenanceOptions\":{\"searchableFields\":[{\"id\":\"ProcessorID\",\"field\":\"processorId\"}]}}");
        client.submitProcessorProvenance("owned", 500);
        client.submitProcessorProvenance("other-owned", 10);
        var bodies = ArgumentCaptor.forClass(Object.class);
        verify(client, times(2)).post(eq("/provenance"), bodies.capture(), eq(JsonNode.class));
        JsonNode request = ((JsonNode) bodies.getAllValues().getFirst()).path("provenance").path("request");
        assertThat(request.path("searchTerms").path("ProcessorID").path("value").asText()).isEqualTo("owned");
        assertThat(request.path("maxResults").asInt()).isEqualTo(100);
        assertThat(request.has("componentId")).isFalse();
        assertThat(request.path("incrementalResults").asBoolean()).isFalse();
        verify(client, times(1)).get("/provenance/search-options", JsonNode.class);
    }

    @Test
    void acceptsNativeComponentIdSearchField() throws Exception {
        var client = client("{\"provenanceOptions\":{\"searchableFields\":[{\"id\":\"ComponentID\",\"field\":\"componentId\"}]}}");
        client.submitProcessorProvenance("owned", 1);
        var body = ArgumentCaptor.forClass(Object.class);
        verify(client).post(eq("/provenance"), body.capture(), eq(JsonNode.class));
        assertThat(((JsonNode) body.getValue()).at("/provenance/request/searchTerms/ComponentID/value").asText()).isEqualTo("owned");
    }

    @Test
    void clusterIdentifierIsPreservedForLineagePollingAndDeletion() throws Exception {
        var client = client("{\"provenanceOptions\":{\"searchableFields\":[]}}");
        doReturn(mapper.createObjectNode()).when(client).get("/provenance/lineage/query?clusterNodeId=node%2Fone", JsonNode.class);
        doNothing().when(client).delete("/provenance/lineage/query?clusterNodeId=node%2Fone");
        client.getLineage("query", "node/one");
        client.deleteLineage("query", "node/one");
        verify(client).get("/provenance/lineage/query?clusterNodeId=node%2Fone", JsonNode.class);
        verify(client).delete("/provenance/lineage/query?clusterNodeId=node%2Fone");
    }

    @Test
    void neverFallsBackToUnfilteredProvenance() throws Exception {
        var client = client("{\"provenanceOptions\":{\"searchableFields\":[{\"id\":\"Filename\",\"field\":\"filename\"}]}}");
        assertThatThrownBy(() -> client.submitProcessorProvenance("owned", 10)).isInstanceOf(NifiException.class);
        verify(client, never()).post(anyString(), any(), eq(JsonNode.class));
    }
}
