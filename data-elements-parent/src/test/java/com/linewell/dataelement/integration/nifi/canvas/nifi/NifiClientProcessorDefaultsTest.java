package com.linewell.dataelement.integration.nifi.canvas.nifi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.model.nifi.NifiEntity;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NifiClientProcessorDefaultsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void enablesAvroLogicalTypesForEveryGeneratedExecuteSqlRecord() {
        NifiClient client = spy(new NifiClient(mock(NifiNodeRuntimeResolver.class), mapper));
        doReturn(mock(NifiEntity.class)).when(client).post(
                eq("/process-groups/group/processors"), any(), eq(NifiEntity.class));

        client.createProcessor("group", "org.apache.nifi.processors.standard.ExecuteSQLRecord",
                "source/execute", 0, 0, Map.of("Record Writer", "writer"), null, null);
        client.createProcessor("group", "org.apache.nifi.processors.standard.ExecuteSQLRecord",
                "lookup/execute", 0, 0, Map.of("Use Avro Logical Types", "false"), null, null);

        ArgumentCaptor<Object> requests = ArgumentCaptor.forClass(Object.class);
        verify(client, org.mockito.Mockito.times(2)).post(
                eq("/process-groups/group/processors"), requests.capture(), eq(NifiEntity.class));
        for (Object request : requests.getAllValues()) {
            JsonNode properties = ((JsonNode) request).at("/component/config/properties");
            assertThat(properties.path("Use Avro Logical Types").asText()).isEqualTo("true");
        }
        assertThat(((JsonNode) requests.getAllValues().getFirst())
                .at("/component/config/properties/Record Writer").asText()).isEqualTo("writer");
    }

    @Test
    void doesNotAddExecuteSqlPropertyToOtherProcessors() {
        NifiClient client = spy(new NifiClient(mock(NifiNodeRuntimeResolver.class), mapper));
        doReturn(mock(NifiEntity.class)).when(client).post(
                eq("/process-groups/group/processors"), any(), eq(NifiEntity.class));

        client.createProcessor("group", "org.apache.nifi.processors.standard.QueryRecord",
                "transform/map", 0, 0, Map.of("Record Writer", "writer"), null, null);

        ArgumentCaptor<Object> request = ArgumentCaptor.forClass(Object.class);
        verify(client).post(eq("/process-groups/group/processors"), request.capture(), eq(NifiEntity.class));
        assertThat(((JsonNode) request.getValue()).at("/component/config/properties")
                .has("Use Avro Logical Types")).isFalse();
    }
}
