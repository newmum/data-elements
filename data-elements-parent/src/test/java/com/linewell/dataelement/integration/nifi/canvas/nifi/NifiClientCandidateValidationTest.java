package com.linewell.dataelement.integration.nifi.canvas.nifi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NifiClientCandidateValidationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final String path = "/process-groups/candidate/processors?includeDescendantGroups=true";

    @Test void twentyAndOneHundredProcessorsNeedOneBatchRead() {
        for (int count : List.of(20, 100)) {
            var client = spy(new NifiClient(mock(NifiNodeRuntimeResolver.class), json));
            var response = json.createObjectNode();
            var processors = response.putArray("processors");
            for (int i = 0; i < count; i++) processors.addObject().putObject("component")
                    .put("name", "processor-" + i).put("validationStatus", "VALID").putArray("validationErrors");
            doReturn(response).when(client).get(path, JsonNode.class);
            client.requireValidGeneratedProcessGroup("candidate");
            verify(client).get(path, JsonNode.class);
        }
    }

    @Test void missingSuccessConnectionReportsNativeComponentAndReason() {
        var client = spy(new NifiClient(mock(NifiNodeRuntimeResolver.class), json));
        var response = json.createObjectNode();
        response.putArray("processors").addObject().putObject("component")
                .put("name", "Hive/partition").put("validationStatus", "INVALID")
                .putArray("validationErrors").add("Relationship success is not connected");
        doReturn(response).when(client).get(path, JsonNode.class);
        assertThatThrownBy(() -> client.requireValidGeneratedProcessGroup("candidate"))
                .hasMessageContaining("Hive/partition").hasMessageContaining("Relationship success").hasMessageContaining("未替换旧流程");
    }

    @Test void emptyGraphCannotPassValidation() {
        var client = spy(new NifiClient(mock(NifiNodeRuntimeResolver.class), json));
        var response = json.createObjectNode(); response.putArray("processors");
        doReturn(response).when(client).get(path, JsonNode.class);
        assertThatThrownBy(() -> client.requireValidGeneratedProcessGroup("candidate"))
                .hasMessageContaining("未生成完整处理器链路");
    }
}
