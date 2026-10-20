package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MappingFieldLabelsTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    @Test void twentyAndHundredLegacyAndUnifiedRulesReuseColumnSnapshotsWithoutChangingPolicies() throws Exception {
        for(int count:List.of(20,100)) {
            var nodes=new ArrayList<Pipeline.Node>();var edges=new ArrayList<Pipeline.Edge>();
            for(int i=0;i<count;i++) {
                nodes.add(node("s"+i,"source",Map.of("sourceColumns",List.of(Map.of("columnName","RAW","columnComment","来源经度")))));
                nodes.add(node("m"+i,"transform",Map.of()));
                nodes.add(node("t"+i,"sink",Map.of("targetColumns","[{\"columnName\":\"longitude\",\"columnComment\":\"目标经度\"}]")));
                edges.add(new Pipeline.Edge("a"+i,"s"+i,"m"+i,"default"));edges.add(new Pipeline.Edge("b"+i,"m"+i,"t"+i,"default"));
            }
            var labels=new MappingFieldLabels(flow(nodes,edges));
            for(int i=0;i<count;i++) {
                var legacy=JSON.readTree("{\"version\":\"1.0\",\"mappings\":[{\"from\":\"/raw\",\"to\":\"/longitude\",\"targetType\":\"DECIMAL\"}]}");
                var value=labels.enrich("m"+i,legacy).path("mappings").get(0);
                assertEquals("来源经度",value.path("fromLabel").asText());assertEquals("目标经度",value.path("toLabel").asText());
                assertEquals("DECIMAL",value.path("targetType").asText());assertFalse(legacy.path("mappings").get(0).has("fromLabel"));
                var unified=labels.enrich("m"+i,JSON.readTree("{\"schemaVersion\":\"2.0\",\"rules\":[{\"input\":{\"kind\":\"FIELD\",\"name\":\"raw\"},\"target\":{\"name\":\"longitude\"}}]}"));
                assertEquals("来源经度",unified.path("rules").get(0).path("input").path("comment").asText());
                assertEquals("目标经度",unified.path("rules").get(0).path("target").path("comment").asText());assertFalse(unified.has("schemaContract"));
            }
        }
    }
    @Test void chainedMappingsCanReachSnapshotsAndConflictingSourcesAreNotGuessed() throws Exception {
        var source=node("s","source",Map.of("sourceColumns",List.of(Map.of("columnName","x","columnComment","来源说明"))));
        var other=node("other","source",Map.of("sourceColumns",List.of(Map.of("columnName","x","columnComment","另一个含义"))));
        var first=node("a","transform",Map.of());var second=node("b","transform",Map.of());
        var edges=new ArrayList<>(List.of(new Pipeline.Edge("a","s","a","default"),new Pipeline.Edge("b","a","b","default")));
        var spec=JSON.readTree("{\"version\":\"1.0\",\"mappings\":[{\"from\":\"x\",\"to\":\"y\"}]}");
        assertEquals("来源说明",new MappingFieldLabels(flow(List.of(source,first,second),edges)).enrich("b",spec).path("mappings").get(0).path("fromLabel").asText());
        edges.add(new Pipeline.Edge("c","other","b","default"));
        assertFalse(new MappingFieldLabels(flow(List.of(source,other,first,second),edges)).enrich("b",spec).path("mappings").get(0).has("fromLabel"));
    }
    @Test void oracleDateInputUnitsAreAttachedOnlyToDirectTemporalRulesAndNeverGuessedAcrossTransforms() throws Exception {
        for(String dbType:List.of("ORACLE","OCEANBASE_ORACLE","MySQL")) {
            var source=node("s","source",Map.of("dbType",dbType,"sourceColumns",List.of(Map.of("columnName","csrq","dataType","DATE","columnComment","出生日期"))));
            var mapping=node("m","transform",Map.of());
            var spec=JSON.readTree("{\"version\":\"1.0\",\"mappings\":[{\"from\":\"csrq\",\"to\":\"day\",\"targetType\":\"DATETIME\"},{\"from\":\"csrq\",\"to\":\"raw\",\"targetType\":\"STRING\"}]}");
            var labels=new MappingFieldLabels(flow(List.of(source,mapping),List.of(new Pipeline.Edge("e","s","m","default"))));
            var result=labels.enrich("m",spec);
            assertEquals(!dbType.equals("MySQL"),result.path("mappings").get(0).has("sourceTemporalUnit"));
            assertFalse(result.path("mappings").get(1).has("sourceTemporalUnit"));assertFalse(spec.path("mappings").get(0).has("sourceTemporalUnit"));
            var second=node("second","transform",Map.of());
            var chained=new MappingFieldLabels(flow(List.of(source,mapping,second),List.of(new Pipeline.Edge("e","s","m","default"),new Pipeline.Edge("next","m","second","default"))));
            assertFalse(chained.enrich("second",spec).path("mappings").get(0).has("sourceTemporalUnit"));
        }
    }
    private static Pipeline.Node node(String id,String category,Map<String,Object> config){return new Pipeline.Node(id,category+".test",id,category,0,0,config);}
    private static Pipeline flow(List<Pipeline.Node> nodes,List<Pipeline.Edge> edges){return new Pipeline("labels","labels",null,null,null,new Pipeline.Dsl(1,nodes,edges),null,null,null,null,null,null,null);}
}
