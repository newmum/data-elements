package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ValueTypesTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final ExecutionContext CONTEXT=new ExecutionContext(Instant.EPOCH,ZoneId.of("Asia/Shanghai"),Locale.CHINA,"synthetic");
    @Test void portableCharacterAliasesIncludingDimensionsWhitespaceAndNationalForms() {
        for(String type:List.of("CHARACTER VARYING", "character varying(200)", " CHARACTER   VARYING (20) ",
                "CHAR VARYING(30)", "NATIONAL CHARACTER VARYING(100)", "NATIONAL CHAR VARYING", "NCHAR VARYING",
                "CHARACTER(2)", "NATIONAL CHARACTER(2)", "NCLOB", "LONGNVARCHAR", "BPCHAR", "MEDIUMTEXT", "LONGTEXT")) {
            assertEquals("STRING",ValueTypes.type(type),type);
            assertEquals("中文123",ValueTypes.cast("中文123",type,38,10,RoundingMode.UNNECESSARY),type);
        }
        assertEquals("INSTANT",ValueTypes.type("TIMESTAMP (6)  WITH TIME ZONE"));
        assertNull(ValueTypes.cast(null,"CHARACTER VARYING",38,10,RoundingMode.UNNECESSARY));
    }
    @Test void screenshotLegacyCastAcross190FieldsAndTwentyOrOneHundredRowsUsesNoProvider() {
        var spec=JSON.createObjectNode().put("version","1.0");var mappings=spec.putArray("mappings");
        for(int i=0;i<190;i++) mappings.addObject().put("from","/x").put("to","/field"+i).put("targetType","CHARACTER VARYING(100)");
        var plan=MappingEngine.compile(spec);
        for(int size:List.of(20,100)) {
            var rows=Collections.<Map<String,Object>>nCopies(size,Map.of("x","中文v"));
            var result=MappingEngine.execute(plan,rows,0,CONTEXT,null);
            assertEquals(size,result.rows().size());assertEquals(0,result.dictionaryBatches());
            assertTrue(result.rows().stream().allMatch(row->row.success()&&row.output().size()==190&&row.output().get("field189").equals("中文v")));
        }
    }
    @Test void explicitCastAcceptsTheScreenshotTypeAlias() throws Exception {
        var plan=MappingEngine.compile(JSON.readTree("""
          {"schemaVersion":"2.0","rules":[{"ruleId":"r","target":{"name":"y"},"input":{"kind":"FIELD","name":"x"},"steps":[{"operator":"CAST","args":{"logicalType":"CHARACTER VARYING"}}]}]}
          """));
        var result=MappingEngine.execute(plan,List.of(Map.of("x",123)),0,CONTEXT,null);
        assertTrue(result.rows().getFirst().success());
        assertEquals("123",result.rows().getFirst().output().get("y"));
        var legacy=MappingEngine.compile(JSON.readTree("""
          {"version":"1.0","mappings":[{"from":"/x","to":"/y","transform":{"fn":"cast","args":["CHARACTER VARYING"]}}]}
          """));
        assertEquals("123",MappingEngine.execute(legacy,List.of(Map.of("x",123)),0,CONTEXT,null).rows().getFirst().output().get("y"));
    }
    @Test void unknownCastTypeFailsAtCompilationRatherThanAfterReceivingRows() throws Exception {
        var spec=JSON.readTree("""
          {"schemaVersion":"2.0","rules":[{"ruleId":"r","target":{"name":"y"},"input":{"kind":"FIELD","name":"x"},"steps":[{"operator":"CAST","args":{"logicalType":"UNKNOWN_DATABASE_TYPE"}}]}]}
          """);
        assertEquals("UNSUPPORTED_TYPE",assertThrows(MappingException.class,()->MappingEngine.compile(spec)).code());
    }
    @Test void sparseReplayIndicesPreserveDeterministicGeneratedValues() throws Exception {
        var plan=MappingEngine.compile(JSON.readTree("{\"version\":\"1.0\",\"mappings\":[{\"to\":\"/id\",\"expression\":\"UUID()\"}]}"));
        var all=MappingEngine.execute(plan,Collections.nCopies(8,Map.of()),0,CONTEXT,null);
        var replay=MappingEngine.executeIndexed(plan,List.of(Map.of(),Map.of()),List.of(2L,7L),CONTEXT,null);
        assertEquals(all.rows().get(2).output().get("id"),replay.rows().get(0).output().get("id"));
        assertEquals(all.rows().get(7).output().get("id"),replay.rows().get(1).output().get("id"));
        assertThrows(MappingException.class,()->MappingEngine.executeIndexed(plan,List.of(Map.of(),Map.of()),List.of(2L,2L),CONTEXT,null));
    }
}
