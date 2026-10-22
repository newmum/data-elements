package com.linewell.dataelement.integration.nifi.canvas.nifi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    @Test void clusterCoordinatorTypeResponsesAreSufficientWithoutAClusterSizeBan() {
        for(int nodeCount:java.util.List.of(2,3)) {
            var client=spy(new NifiClient(mock(NifiNodeRuntimeResolver.class),mapper));String version=com.linewell.dataelement.mapping.core.MappingEngine.engineVersion();
            var processors=mapper.createObjectNode();processors.putArray("processorTypes").addObject().put("type","com.linewell.dataelement.nifi.HaitongTransformRecord").putObject("bundle").put("group","com.linewell.dataelement").put("artifact","haitong-mapping-nar").put("version",version);
            var services=mapper.createObjectNode();services.putArray("controllerServiceTypes").addObject().put("type","com.linewell.dataelement.nifi.JdbcHaitongDictionaryService").putObject("bundle").put("group","com.linewell.dataelement").put("artifact","haitong-mapping-nar").put("version",version);
            doReturn(processors).when(client).get("/flow/processor-types",JsonNode.class);doReturn(services).when(client).get("/flow/controller-service-types",JsonNode.class);
            var cluster=mapper.createObjectNode();cluster.putObject("clusterSummary").put("clustered",true).put("connectedNodeCount",nodeCount);org.mockito.Mockito.lenient().doReturn(cluster).when(client).get("/flow/cluster/summary",JsonNode.class);
            client.requireMappingExtension();verify(client,org.mockito.Mockito.times(2)).get(anyString(),eq(JsonNode.class));
            verify(client,org.mockito.Mockito.never()).get("/flow/cluster/summary",JsonNode.class);
            ((com.fasterxml.jackson.databind.node.ArrayNode)services.path("controllerServiceTypes")).removeAll();
            org.assertj.core.api.Assertions.assertThatThrownBy(client::requireMappingExtension).hasMessageContaining("JdbcHaitongDictionaryService 当前已注册：无");
        }
    }

    @Test void mappingPreflightListsActualVersionsAndRequiresBothMatchingRegistrations() {
        var client=spy(new NifiClient(mock(NifiNodeRuntimeResolver.class),mapper));
        String version=com.linewell.dataelement.mapping.core.MappingEngine.engineVersion();
        var processors=mapper.createObjectNode();var processorTypes=processors.putArray("processorTypes");
        var services=mapper.createObjectNode();var serviceTypes=services.putArray("controllerServiceTypes");
        processorTypes.addObject().put("type","com.linewell.dataelement.nifi.HaitongTransformRecord")
                .putObject("bundle").put("group","com.linewell.dataelement").put("artifact","haitong-mapping-nar").put("version","1.1.21");
        serviceTypes.addObject().put("type","com.linewell.dataelement.nifi.JdbcHaitongDictionaryService")
                .putObject("bundle").put("group","com.linewell.dataelement").put("artifact","haitong-mapping-nar").put("version","1.1.21");
        doReturn(processors).when(client).get(eq("/flow/processor-types"),eq(JsonNode.class));
        doReturn(services).when(client).get(eq("/flow/controller-service-types"),eq(JsonNode.class));
        var cluster=mapper.createObjectNode();cluster.putObject("clusterSummary").put("clustered",false);
        doReturn(cluster).when(client).get(eq("/flow/cluster/summary"),eq(JsonNode.class));
        org.assertj.core.api.Assertions.assertThatThrownBy(client::requireMappingExtension)
                .hasMessageContaining("后端字段治理内核版本："+version)
                .hasMessageContaining("HaitongTransformRecord 当前已注册：com.linewell.dataelement:haitong-mapping-nar:1.1.21")
                .hasMessageContaining("JdbcHaitongDictionaryService 当前已注册：com.linewell.dataelement:haitong-mapping-nar:1.1.21")
                .hasMessageContaining("haitong-mapping-nar-"+version+".nar");
        verify(client).get(eq("/flow/processor-types"),eq(JsonNode.class));
        verify(client).get(eq("/flow/controller-service-types"),eq(JsonNode.class));
        processorTypes.addObject().put("type","com.linewell.dataelement.nifi.HaitongTransformRecord")
                .putObject("bundle").put("group","com.linewell.dataelement").put("artifact","haitong-mapping-nar").put("version",version);
        org.assertj.core.api.Assertions.assertThatThrownBy(client::requireMappingExtension)
                .hasMessageContaining("JdbcHaitongDictionaryService 当前已注册：com.linewell.dataelement:haitong-mapping-nar:1.1.21");
        serviceTypes.addObject().put("type","com.linewell.dataelement.nifi.JdbcHaitongDictionaryService")
                .putObject("bundle").put("group","com.linewell.dataelement").put("artifact","haitong-mapping-nar").put("version",version)
                .put("extraMetadata","ignored");
        client.requireMappingExtension();
    }
    @Test void dateFetchAcceptsTheExisting29BundleAndPinsItWhenOtherVersionsExist() {
        var client=spy(new NifiClient(mock(NifiNodeRuntimeResolver.class),mapper));
        String type=com.linewell.dataelement.integration.nifi.canvas.compile.DateTableFetchPolicy.TYPE;
        String version=com.linewell.dataelement.integration.nifi.canvas.compile.DateTableFetchPolicy.BUNDLE_VERSION;
        assertThat(version).isEqualTo("2.9.0");
        var response=mapper.createObjectNode();var types=response.putArray("processorTypes");
        types.addObject().put("type",type).putObject("bundle").put("group","com.linewell.microservice")
                .put("artifact","datadevs-database-nar").put("version","2.8.0");
        doReturn(response).when(client).get(eq("/flow/processor-types"),eq(JsonNode.class));
        org.assertj.core.api.Assertions.assertThatThrownBy(client::requireDateFetchExtension)
                .hasMessageContaining("已注册 GenerateDateTableFetch，但扩展版本或坐标不匹配")
                .hasMessageContaining("平台要求：com.linewell.microservice:datadevs-database-nar:2.9.0")
                .hasMessageContaining("当前 NiFi 已注册：com.linewell.microservice:datadevs-database-nar:2.8.0");
        types.addObject().put("type",type).putObject("bundle").put("group","com.linewell.microservice")
                .put("artifact","datadevs-database-nar").put("version","2.9.0");
        client.requireDateFetchExtension();
        doReturn(mock(NifiEntity.class)).when(client).post(eq("/process-groups/group/processors"),any(),eq(NifiEntity.class));
        client.createProcessor("group",type,"fetch",0,0,Map.of(),null,null);
        ArgumentCaptor<Object> request=ArgumentCaptor.forClass(Object.class);
        verify(client).post(eq("/process-groups/group/processors"),request.capture(),eq(NifiEntity.class));
        assertThat(((JsonNode)request.getValue()).at("/component/bundle/version").asText()).isEqualTo("2.9.0");
    }
    @Test void dateFetchDistinguishesMissingTypeFromWrongBundleAndAcceptsAdditionalMetadata() {
        var client=spy(new NifiClient(mock(NifiNodeRuntimeResolver.class),mapper));
        String type=com.linewell.dataelement.integration.nifi.canvas.compile.DateTableFetchPolicy.TYPE;
        String version=com.linewell.dataelement.integration.nifi.canvas.compile.DateTableFetchPolicy.BUNDLE_VERSION;
        var response=mapper.createObjectNode();var types=response.putArray("processorTypes");
        doReturn(response).when(client).get(eq("/flow/processor-types"),eq(JsonNode.class));
        org.assertj.core.api.Assertions.assertThatThrownBy(client::requireDateFetchExtension)
                .hasMessageContaining("未注册 GenerateDateTableFetch 处理器")
                .hasMessageContaining("当前 NiFi 已注册：无");
        types.addObject().put("type",type).putObject("bundle").put("group","other.vendor")
                .put("artifact","datadevs-database-nar").put("version",version);
        org.assertj.core.api.Assertions.assertThatThrownBy(client::requireDateFetchExtension)
                .hasMessageContaining("版本或坐标不匹配")
                .hasMessageContaining("当前 NiFi 已注册：other.vendor:datadevs-database-nar:"+version);
        types.addObject().put("type",type).putObject("bundle").put("group","com.linewell.microservice")
                .put("artifact","datadevs-database-nar").put("version",version).put("extraMetadata","ignored");
        client.requireDateFetchExtension();
    }
    @Test void mappingProcessorAndDictionaryServiceUseTheRunningEngineBundle() {
        NifiClient client=spy(new NifiClient(mock(NifiNodeRuntimeResolver.class),mapper));
        doReturn(mock(NifiEntity.class)).when(client).post(eq("/process-groups/group/processors"),any(),eq(NifiEntity.class));
        doReturn(mock(NifiEntity.class)).when(client).post(eq("/process-groups/group/controller-services"),any(),eq(NifiEntity.class));
        client.createProcessor("group","com.linewell.dataelement.nifi.HaitongTransformRecord","mapping",0,0,Map.of(),null,null);
        client.createControllerService("group","com.linewell.dataelement.nifi.JdbcHaitongDictionaryService","dictionary",Map.of());
        ArgumentCaptor<Object> processor=ArgumentCaptor.forClass(Object.class),service=ArgumentCaptor.forClass(Object.class);
        verify(client).post(eq("/process-groups/group/processors"),processor.capture(),eq(NifiEntity.class));
        verify(client).post(eq("/process-groups/group/controller-services"),service.capture(),eq(NifiEntity.class));
        for(Object request:java.util.List.of(processor.getValue(),service.getValue()))
            assertThat(((JsonNode)request).at("/component/bundle/version").asText()).isEqualTo(com.linewell.dataelement.mapping.core.MappingEngine.engineVersion());
    }
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
