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
    @Test void linewellClockIntentsNeverReachNiFiAsDynamicPropertiesAndResolveUsingCreationResponse()throws Exception {
        NifiClient client=spy(new NifiClient(mock(NifiNodeRuntimeResolver.class),mapper));
        JsonNode created=mapper.readTree("""
          {"revision":{"version":0},"component":{"id":"p","config":{"descriptors":{"insert-key":{"displayName":"insert 追加value 值"},"update-key":{"displayName":"update 追加value 值"},"exclude-key":{"displayName":"merge 排除 update"}}}}}
          """);
        doReturn(created).when(client).post(eq("/process-groups/group/processors"),any(),eq(JsonNode.class));
        doReturn(mock(NifiEntity.class)).when(client).put(eq("/processors/p"),any(),eq(NifiEntity.class));
        client.createProcessor("group","com.linewell.nifi.LinewellPutDatabaseRecord","writer",0,0,LinewellAuditProperties.defaults(java.util.List.of("ODS_RKSJ","ODS_GXSJ"),Map.of("dbType","ORACLE")),null,null);
        ArgumentCaptor<Object> initial=ArgumentCaptor.forClass(Object.class),update=ArgumentCaptor.forClass(Object.class);
        verify(client).post(eq("/process-groups/group/processors"),initial.capture(),eq(JsonNode.class));verify(client).put(eq("/processors/p"),update.capture(),eq(NifiEntity.class));
        assertThat(initial.getValue().toString()).doesNotContain(LinewellAuditProperties.PREFIX);
        JsonNode props=((JsonNode)update.getValue()).at("/component/config/properties");assertThat(props.path("insert-key").asText()).isEqualTo("ODS_RKSJ=SYSDATE,ODS_GXSJ=SYSDATE");assertThat(props.path("update-key").asText()).isEqualTo("ODS_GXSJ=SYSDATE");assertThat(props.path("exclude-key").asText()).isEqualTo("ODS_RKSJ");
        org.mockito.Mockito.verify(client,org.mockito.Mockito.never()).get(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any());
    }

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
