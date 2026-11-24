package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 编译器部署测试共用的 NiFi 根作用域与已启用 JSON 服务夹具。 */
public final class HiveServiceFixtures {
    public static void stubGlobals(com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient nifi) {
        org.mockito.Mockito.when(nifi.get("/flow/process-groups/root", JsonNode.class)).thenReturn(rootFlow());
        org.mockito.Mockito.when(nifi.listControllerServices("root")).thenReturn(codecs());
        org.mockito.Mockito.when(nifi.get("/flow/controller-service-types", JsonNode.class)).thenReturn(new ObjectMapper().valueToTree(Map.of(
                "controllerServiceTypes",List.of(Map.of("type","com.linewell.dataelement.nifi.HaitongJdbcRecordReader","bundle",Map.of(
                        "group","com.linewell.dataelement","artifact","haitong-mapping-nar","version",com.linewell.dataelement.mapping.core.MappingEngine.engineVersion()))))));
    }
    static JsonNode rootFlow(){return new ObjectMapper().valueToTree(Map.of("processGroupFlow",Map.of("id","root")));}
    static JsonNode codec(String id,String name,String type,String scope,String state) {
        return new ObjectMapper().valueToTree(Map.of("component",Map.of("id",id,"name",name,"type",type,"parentGroupId",scope,"state",state)));
    }
    static List<JsonNode> codecs() {
        JsonNode precise=codec("global-timestamp9",JdbcTimestampJsonPolicy.WRITER_NAME,"org.apache.nifi.json.JsonRecordSetWriter","root","ENABLED");
        ((com.fasterxml.jackson.databind.node.ObjectNode)precise.path("component")).putObject("properties")
                .put("Schema Access Strategy","inherit-record-schema").put("Timestamp Format",JdbcTimestampJsonPolicy.TIMESTAMP_FORMAT);
        return List.of(codec("global-json-writer",HiveTemporalJsonPolicy.DEFAULT_WRITER_NAME,"org.apache.nifi.json.JsonRecordSetWriter","root","ENABLED"),
                codec("global-hive-json-writer",HiveTemporalJsonPolicy.WRITER_NAME,"org.apache.nifi.json.JsonRecordSetWriter","root","ENABLED"),
                precise,
                codec("global-json-reader",HiveTemporalJsonPolicy.READER_NAME,"org.apache.nifi.json.JsonTreeReader","root","ENABLED"));
    }
    static List<JsonNode> withCsv(JsonNode csv) {var result=new ArrayList<>(codecs());result.add(csv);return result;}
}
