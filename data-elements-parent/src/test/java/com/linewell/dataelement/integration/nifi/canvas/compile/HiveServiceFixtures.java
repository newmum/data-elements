package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class HiveServiceFixtures {
    static JsonNode rootFlow(){return new ObjectMapper().valueToTree(Map.of("processGroupFlow",Map.of("id","root")));}
    static JsonNode codec(String id,String name,String type,String scope,String state) {
        return new ObjectMapper().valueToTree(Map.of("component",Map.of("id",id,"name",name,"type",type,"parentGroupId",scope,"state",state)));
    }
    static List<JsonNode> codecs() {
        return List.of(codec("global-json-writer",HiveTemporalJsonPolicy.WRITER_NAME,"org.apache.nifi.json.JsonRecordSetWriter","root","ENABLED"),
                codec("global-json-reader",HiveTemporalJsonPolicy.READER_NAME,"org.apache.nifi.json.JsonTreeReader","root","ENABLED"));
    }
    static List<JsonNode> withCsv(JsonNode csv) {var result=new ArrayList<>(codecs());result.add(csv);return result;}
}
