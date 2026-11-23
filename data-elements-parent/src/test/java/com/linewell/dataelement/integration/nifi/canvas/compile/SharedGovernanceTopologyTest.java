package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SharedGovernanceTopologyTest {
    @Test void manualAndNormalJdbcSourcesRetainTheirKnownTemporalWireUnits()throws Exception {
        var config=Map.<String,Object>of("jdbcUrl","jdbc:oracle:thin:@validation", "sourceColumns",List.of(Map.of("columnName","EVENT_TIME","columnType","TIMESTAMP")));
        var main=node("main","source.oracle","source",config);var backfill=node("backfill","source.oracle","source",config);
        var mapping=node("mapping","transform.field-enrichment","transform",Map.of());
        var pipeline=flow(List.of(main,backfill,mapping),List.of(edge("main","mapping"),edge("backfill","mapping")));
        var raw=new ObjectMapper().readTree("{\"version\":\"1.0\",\"mappings\":[{\"from\":\"/EVENT_TIME\",\"to\":\"/EVENT_TIME\",\"targetType\":\"TIMESTAMP\"}]}");
        var enriched=new MappingFieldLabels(pipeline).enrich("mapping",raw);
        assertEquals("EPOCH_MILLIS",enriched.path("mappings").get(0).path("sourceTemporalUnit").asText());
    }
    @Test void sqlProjectionStartsANewContractRatherThanConstrainingItsParentGovernance()throws Exception {
        var mapping=node("mapping","transform.field-enrichment","transform",Map.of());
        var jdbc=node("jdbc","sink.jdbc","sink",Map.of("targetDbId","db","targetTableId","one"));
        var sql=node("sql","transform.sql","transform",Map.of("sql","SELECT ID AS ACCOUNT_ID FROM FLOWFILE"));
        var hive=node("hive","sink.hive","sink",Map.of("targetDbId","hive","targetTableId","two"));
        var pipeline=flow(List.of(mapping,jdbc,sql,hive),List.of(edge("mapping","jdbc"),edge("mapping","sql"),edge("sql","hive")));
        var raw=new ObjectMapper().readTree("{\"version\":\"1.0\",\"mappings\":[{\"from\":\"/ID\",\"to\":\"/ID\"}]}");
        assertFalse(new MappingFieldLabels(pipeline).enrich("mapping",raw).path("schemaContract").has("targets"));
    }
    private static Pipeline.Node node(String id,String manifest,String category,Map<String,Object> cfg){return new Pipeline.Node(id,manifest,id,category,0,0,cfg);}
    private static Pipeline flow(List<Pipeline.Node> nodes,List<Pipeline.Edge> edges){return new Pipeline("validation","validation",null,null,null,new Pipeline.Dsl(1,nodes,edges),null,null,null,null,null,null,null);}
    private static Pipeline.Edge edge(String from,String to){return new Pipeline.Edge(from+to,from,to,"success");}
    @Test void sharedMappingKeepsTwoAuthorizedTargetsThroughTheHiveAdapter()throws Exception {
        var source=node("source","source.oracle","source",Map.of());
        var mapping=node("mapping","transform.field-enrichment","transform",Map.of());
        var oracle=node("oracle","sink.jdbc","sink",Map.of("targetDbId","oracle-db","targetTableId","oracle-table"));
        var adapter=node("adapter","transform.timestamp","transform",Map.of());
        var hive=node("hive","sink.hive","sink",Map.of("targetDbId","hive-db","targetTableId","hive-table","hiveTimestampFormat",true));
        var flow=flow(List.of(source,mapping,oracle,adapter,hive),List.of(edge("source","mapping"),edge("mapping","oracle"),edge("mapping","adapter"),edge("adapter","hive")));
        var spec=new ObjectMapper().readTree("{\"version\":\"1.0\",\"mappings\":[{\"from\":\"id\",\"to\":\"id\"}]}");
        var contract=new MappingFieldLabels(flow).enrich("mapping",spec).path("schemaContract");
        assertEquals("oracle-table",contract.path("targetRef").path("tableId").asText());
        assertEquals(Set.of("oracle","hive"),new HashSet<>(List.of(contract.path("targets").get(0).path("nodeId").asText(),contract.path("targets").get(1).path("nodeId").asText())));
        assertEquals(Set.of("hive","adapter"),HiveTemporalJsonPolicy.hiveWriterNodes(flow));
        assertFalse(spec.has("schemaContract"));
    }
    @Test void disabledBackfillDoesNotChangeTheMainPeriodicOrCleanupPolicy(){
        var primary=node("source","source.oracle","source",Map.of("syncMode","PERIODIC_FULL"));
        var backfill=node("backfill","source.oracle","source",Map.of("backfillSource",true,"syncMode","FULL","deleteTargetData",true));
        var flow=flow(List.of(primary,backfill),List.of());
        assertEquals(primary,SyncPolicy.periodicSource(flow));assertFalse(SyncPolicy.needsCleanupConfirmation(flow));
    }
}
