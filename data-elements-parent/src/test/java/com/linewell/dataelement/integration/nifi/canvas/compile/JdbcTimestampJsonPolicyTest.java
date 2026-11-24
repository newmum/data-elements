package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcTimestampJsonPolicyTest {
    @ParameterizedTest @ValueSource(ints = {20,100})
    void fullFractionBranchesReuseOneExistingRootServiceWithNoWrites(int count) {
        var client = mock(NifiClient.class);
        var services = services(JdbcTimestampJsonPolicy.TIMESTAMP_FORMAT);
        when(client.get("/flow/process-groups/root", JsonNode.class)).thenReturn(HiveServiceFixtures.rootFlow());
        when(client.listControllerServices("root")).thenReturn(services);
        var nodes = new ArrayList<Pipeline.Node>(); var edges = new ArrayList<Pipeline.Edge>();
        for (int index = 0; index < count; index++) {
            nodes.add(source("sql"+index,"source.mysql",Map.of("dbType","MYSQL","sourceColumns",List.of(Map.of("columnName","created_at","dataType","datetime(6)")))));
            nodes.add(new Pipeline.Node("gov"+index,"transform.field-mapping","mapping","transform",0,0,Map.of()));
            edges.add(new Pipeline.Edge("e"+index,"sql"+index,"gov"+index,"default"));
        }
        nodes.add(source("file","source.minio",Map.of("dbType","MINIO","sourceColumns",List.of(Map.of("columnName","text","dataType","timestamp")))));
        var selected = GlobalJsonRecordServices.resolve(client,flow(nodes,edges),true);
        for (int index = 0; index < count; index++) {
            assertThat(selected.writerForNode("sql"+index)).isEqualTo("global-timestamp9");
            assertThat(selected.writerForNode("gov"+index)).isEqualTo("global-timestamp9");
            assertThat(selected.formatsTimestampsForNode("gov"+index)).isTrue();
        }
        assertThat(selected.writerForNode("file")).isEqualTo("global-json-writer");
        verify(client).get("/flow/process-groups/root",JsonNode.class);
        verify(client).listControllerServices("root"); verifyNoMoreInteractions(client);
        assertThat(services).isEqualTo(services(JdbcTimestampJsonPolicy.TIMESTAMP_FORMAT));
    }

    @Test void absentOrTruncatingRootCodecRejectsBeforeCreatingAnyTaskOrService() {
        for (var rows : List.of(HiveServiceFixtures.codecs().stream().filter(row -> !JdbcTimestampJsonPolicy.WRITER_NAME.equals(row.path("component").path("name").asText())).toList(),services("yyyy-MM-dd HH:mm:ss.SSS"))) {
            var client = mock(NifiClient.class);
            when(client.get("/flow/process-groups/root",JsonNode.class)).thenReturn(HiveServiceFixtures.rootFlow());
            when(client.listControllerServices("root")).thenReturn(rows);
            var pipeline = flow(List.of(source("sql","source.mysql",Map.of("dbType","MYSQL","sourceColumns",List.of(Map.of("dataType","DATETIME"))))),List.of());
            assertThatThrownBy(() -> GlobalJsonRecordServices.resolve(client,pipeline,true)).hasMessageContaining(JdbcTimestampJsonPolicy.WRITER_NAME);
            verify(client).get("/flow/process-groups/root",JsonNode.class);
            verify(client).listControllerServices("root"); verifyNoMoreInteractions(client);
        }
    }

    @Test void dateTimeAndTimestampLookingTextDoNotRequestTimestampCodec() {
        for (String type : List.of("DATE","TIME","VARCHAR(255)","STRING","JSON"))
            assertThat(JdbcTimestampJsonPolicy.hasTimestamps(Map.of("dbType","MYSQL","sourceColumns",List.of(Map.of("dataType",type))))).isFalse();
        for (String type : List.of("DATETIME","DATETIME(6)","TIMESTAMP(9)","TIMESTAMP WITH TIME ZONE"))
            assertThat(JdbcTimestampJsonPolicy.hasTimestamps(Map.of("dbType","MYSQL","sourceColumns",List.of(Map.of("dataType",type))))).isTrue();
    }

    private static List<JsonNode> services(String format) {
        var rows = new ArrayList<>(HiveServiceFixtures.codecs().stream().filter(row -> !JdbcTimestampJsonPolicy.WRITER_NAME.equals(row.path("component").path("name").asText())).toList());
        JsonNode codec = HiveServiceFixtures.codec("global-timestamp9",JdbcTimestampJsonPolicy.WRITER_NAME,"org.apache.nifi.json.JsonRecordSetWriter","root","ENABLED");
        ((ObjectNode)codec.path("component")).putObject("properties").put("Schema Access Strategy","inherit-record-schema").put("Timestamp Format",format);
        rows.add(codec); return rows;
    }
    private static Pipeline.Node source(String id,String manifest,Map<String,Object> config) {return new Pipeline.Node(id,manifest,id,"source",0,0,config);}
    private static Pipeline flow(List<Pipeline.Node> nodes,List<Pipeline.Edge> edges) {return new Pipeline("validation","validation",null,null,null,new Pipeline.Dsl(1,nodes,edges),null,null,null,null,null,null,null);}
}
