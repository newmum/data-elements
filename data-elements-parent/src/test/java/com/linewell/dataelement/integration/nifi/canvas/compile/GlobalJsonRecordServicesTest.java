package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalJsonRecordServicesTest {
    private final NifiClient nifi=mock(NifiClient.class);
    GlobalJsonRecordServicesTest(){when(nifi.get("/flow/process-groups/root",com.fasterxml.jackson.databind.JsonNode.class)).thenReturn(HiveServiceFixtures.rootFlow());}
    @Test void resolvesRealRootIdsAndIgnoresSameNamedTaskServices() {
        var services=new ArrayList<>(HiveServiceFixtures.codecs());
        services.add(HiveServiceFixtures.codec("task-writer",HiveTemporalJsonPolicy.DEFAULT_WRITER_NAME,"org.apache.nifi.json.JsonRecordSetWriter","task","ENABLED"));
        services.add(HiveServiceFixtures.codec("task-reader",HiveTemporalJsonPolicy.READER_NAME,"org.apache.nifi.json.JsonTreeReader","task","ENABLED"));
        when(nifi.listControllerServices("root")).thenReturn(services);
        var actual=GlobalJsonRecordServices.resolve(nifi);
        assertThat(actual.writerId()).isEqualTo("global-json-writer");assertThat(actual.readerId()).isEqualTo("global-json-reader");
        verify(nifi).listControllerServices("root");verify(nifi).get("/flow/process-groups/root",com.fasterxml.jackson.databind.JsonNode.class);verifyNoMoreInteractions(nifi);
    }
    @Test void missingGlobalDoesNotFallBackToTaskScope() {
        when(nifi.listControllerServices("root")).thenReturn(List.of(HiveServiceFixtures.codec("task",HiveTemporalJsonPolicy.DEFAULT_WRITER_NAME,"org.apache.nifi.json.JsonRecordSetWriter","task","ENABLED")));
        assertThatThrownBy(()->GlobalJsonRecordServices.resolve(nifi)).hasMessageContaining("缺少全局服务").hasMessageContaining(HiveTemporalJsonPolicy.DEFAULT_WRITER_NAME);
    }
    @Test void duplicateRootNamesAreRejected() {
        var services=new ArrayList<>(HiveServiceFixtures.codecs());services.add(HiveServiceFixtures.codec("other",HiveTemporalJsonPolicy.DEFAULT_WRITER_NAME,"org.apache.nifi.json.JsonRecordSetWriter","root","ENABLED"));
        when(nifi.listControllerServices("root")).thenReturn(services);
        assertThatThrownBy(()->GlobalJsonRecordServices.resolve(nifi)).hasMessageContaining("多个同名");
    }
    @Test void disabledGlobalIsNotEnabledByTheCompiler() {
        when(nifi.listControllerServices("root")).thenReturn(List.of(HiveServiceFixtures.codec("disabled",HiveTemporalJsonPolicy.DEFAULT_WRITER_NAME,"org.apache.nifi.json.JsonRecordSetWriter","root","DISABLED")));
        assertThatThrownBy(()->GlobalJsonRecordServices.resolve(nifi)).hasMessageContaining("尚未启用");
        verify(nifi).get("/flow/process-groups/root",com.fasterxml.jackson.databind.JsonNode.class);verify(nifi).listControllerServices("root");verifyNoMoreInteractions(nifi);
    }
    @Test void incompatibleServiceTypeIsRejected() {
        when(nifi.listControllerServices("root")).thenReturn(List.of(HiveServiceFixtures.codec("wrong",HiveTemporalJsonPolicy.DEFAULT_WRITER_NAME,"org.apache.nifi.csv.CSVRecordSetWriter","root","ENABLED")));
        assertThatThrownBy(()->GlobalJsonRecordServices.resolve(nifi)).hasMessageContaining("类型不正确");
    }
    @Test void configuredApplicationRootCannotShadowTheActualNiFiRoot() {
        when(nifi.getRootProcessGroupId()).thenReturn("application");
        when(nifi.listControllerServices("root")).thenReturn(HiveServiceFixtures.codecs());
        assertThat(GlobalJsonRecordServices.resolve(nifi).rootId()).isEqualTo("root");
        verify(nifi,never()).listControllerServices("application");
        verify(nifi,never()).getRootProcessGroupId();
    }

    @Test void ordinaryFlowDoesNotRequireTheHiveTimeFormatService() {
        when(nifi.listControllerServices("root")).thenReturn(HiveServiceFixtures.codecs().stream()
                .filter(service->!service.path("component").path("name").asText().equals(HiveTemporalJsonPolicy.WRITER_NAME)).toList());
        assertThat(GlobalJsonRecordServices.resolve(nifi,flow(List.of(source("mysql","source.mysql",Map.of())),List.of())).writerId()).isEqualTo("global-json-writer");
        verify(nifi,times(1)).listControllerServices("root");
    }

    @Test void aHiveFlowCannotFallBackToTheOrdinaryWriter() {
        when(nifi.listControllerServices("root")).thenReturn(HiveServiceFixtures.codecs().stream()
                .filter(service->!service.path("component").path("name").asText().equals(HiveTemporalJsonPolicy.WRITER_NAME)).toList());
        assertThatThrownBy(()->GlobalJsonRecordServices.resolve(nifi,flow(List.of(source("hive","source.hive",Map.of())),List.of())))
                .hasMessageContaining(HiveTemporalJsonPolicy.WRITER_NAME);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={20,100})
    void mixedBranchesResolveBothWritersWithOneNiFiRead(int count) {
        when(nifi.listControllerServices("root")).thenReturn(HiveServiceFixtures.codecs());
        var nodes=new ArrayList<Pipeline.Node>();var edges=new ArrayList<Pipeline.Edge>();
        for(int index=0;index<count;index++) {
            nodes.add(source("m"+index,"source.mysql",Map.of()));
            nodes.add(source("h"+index,"source.hive",Map.of()));
            nodes.add(new Pipeline.Node("map"+index,"transform.field-mapping","mapping","transform",0,0,Map.of()));
            edges.add(new Pipeline.Edge("e"+index,"h"+index,"map"+index,"default"));
        }
        var result=GlobalJsonRecordServices.resolve(nifi,flow(nodes,edges));
        for(int index=0;index<count;index++) {
            assertThat(result.writerForNode("m"+index)).isEqualTo("global-json-writer");
            assertThat(result.writerForNode("h"+index)).isEqualTo("global-hive-json-writer");
            assertThat(result.writerForNode("map"+index)).isEqualTo("global-hive-json-writer");
        }
        verify(nifi,times(1)).listControllerServices("root");
        verify(nifi,times(1)).get("/flow/process-groups/root",com.fasterxml.jackson.databind.JsonNode.class);
    }

    @Test void rawHiveBranchDoesNotChangeTheSharedMappingWhichStillFeedsOracle() {
        when(nifi.listControllerServices("root")).thenReturn(HiveServiceFixtures.codecs());
        var nodes=List.of(source("source","source.mysql",Map.of()),
                new Pipeline.Node("mapping","transform.field-mapping","mapping","transform",0,0,Map.of()),
                new Pipeline.Node("hive","sink.hive","Hive","sink",0,0,Map.of()),
                new Pipeline.Node("oracle","sink.jdbc","Oracle","sink",0,0,Map.of("dbType","ORACLE")));
        var edges=List.of(new Pipeline.Edge("a","source","mapping","default"),new Pipeline.Edge("b","mapping","hive","default"),new Pipeline.Edge("c","mapping","oracle","default"));
        var result=GlobalJsonRecordServices.resolve(nifi,flow(nodes,edges));
        assertThat(result.writerForNode("source")).isEqualTo("global-hive-json-writer");
        assertThat(result.writerForNode("mapping")).isEqualTo("global-json-writer");
        assertThat(result.writerForNode("oracle")).isEqualTo("global-json-writer");
        assertThat(result.writerForNode("hive")).isEqualTo("global-hive-json-writer");
    }
    @Test void readingHiveDoesNotChangeTheWriterOfMappingToAnOracleTarget() {
        when(nifi.listControllerServices("root")).thenReturn(HiveServiceFixtures.codecs());
        var nodes=List.of(source("source","source.hive",Map.of()),
                new Pipeline.Node("mapping","transform.field-mapping","mapping","transform",0,0,Map.of()),
                new Pipeline.Node("oracle","sink.jdbc","Oracle","sink",0,0,Map.of("dbType","ORACLE")));
        var result=GlobalJsonRecordServices.resolve(nifi,flow(nodes,List.of(
                new Pipeline.Edge("a","source","mapping","default"),new Pipeline.Edge("b","mapping","oracle","default"))));
        assertThat(result.writerForNode("source")).isEqualTo("global-hive-json-writer");
        assertThat(result.writerForNode("mapping")).isEqualTo("global-json-writer");
        assertThat(result.writerForNode("oracle")).isEqualTo("global-json-writer");
    }
    private static Pipeline.Node source(String id,String key,Map<String,Object> config){return new Pipeline.Node(id,key,id,"source",0,0,config);}
    private static Pipeline flow(List<Pipeline.Node> nodes,List<Pipeline.Edge> edges){return new Pipeline("routing","routing",null,null,null,new Pipeline.Dsl(1,nodes,edges),null,null,null,null,null,null,null);}
}
