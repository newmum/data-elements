package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SqlTimestampCarrierTest {
    @Test void nativeTimeTextRequiresExactClockLineageAndDoesNotRelaxOtherReaderTypes(){
        for(String physical:List.of("Time","TIME(6)","TIME WITHOUT TIME ZONE","TIME(9) WITHOUT TIME ZONE")){
            var spec=new ObjectMapper().createObjectNode().put("schemaVersion","2.0");
            spec.putObject("schemaContract").put("allowAdditionalSourceFields",false).putArray("source").addObject()
                    .put("name","clock_value").put("logicalType","TIME").put("physicalType",physical);
            var rule=spec.putArray("rules").addObject();rule.putObject("input").put("kind","FIELD").put("name","clock_value");
            rule.putObject("target").put("name","text").put("logicalType","STRING");var plan=MappingEngine.compile(spec);
            assertDoesNotThrow(()->SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("clock_value","STRING",true,0,0,0))));
            assertEquals("TIME",plan.document().path("schemaContract").path("source").get(0).path("logicalType").asText());
            for(String wire:List.of("ARRAY","ANY","BINARY","INT","LONG","DOUBLE","BOOLEAN","DATETIME","DATE"))
                assertThrows(MappingException.class,()->SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("clock_value",wire,true,0,0,0)),true));
            assertThrows(MappingException.class,()->SchemaContract.requireInput(plan,List.of()));
            for(String untrusted:List.of("VARCHAR(80)","DATETIME","TIMESTAMP","TIMETZ","TIME WITH TIME ZONE","TIME(6) WITH TIME ZONE","")){
                ((com.fasterxml.jackson.databind.node.ObjectNode)spec.path("schemaContract").path("source").get(0)).put("physicalType",untrusted);
                var bad=MappingEngine.compile(spec);assertThrows(MappingException.class,()->SchemaContract.requireInput(bad,List.of(new SchemaContract.Field("clock_value","STRING",true,0,0,0))));
            }
        }
    }
    @Test void nativeTimeValuesAreStrictAndNullRemainsNullEvenWithStringOutput(){
        var spec=new ObjectMapper().createObjectNode().put("schemaVersion","2.0");
        spec.putObject("policies").put("recordErrorMode","ROUTE_INVALID").put("onMissingSource","FAIL").put("caseSensitive",false);
        spec.putObject("schemaContract").putArray("source").addObject().put("name","clock_value").put("logicalType","TIME").put("physicalType","Time");
        var rule=spec.putArray("rules").addObject();rule.putObject("input").put("kind","FIELD").put("name","clock_value");
        rule.putObject("target").put("name","text").put("logicalType","STRING");var plan=MappingEngine.compile(spec);
        var context=new ExecutionContext(java.time.Instant.parse("2026-11-26T00:00:00Z"),java.time.ZoneId.of("Asia/Shanghai"),java.util.Locale.CHINA,"validation-time");
        for(Object value:java.util.Arrays.asList(null,"00:00:00","23:59:59.123456789",java.time.LocalTime.NOON,java.sql.Time.valueOf("12:30:00"))){
            var row=MappingEngine.execute(plan,List.of(java.util.Collections.singletonMap("CLOCK_VALUE",value)),0,context,null).rows().getFirst();
            assertTrue(row.success(),row.errors().toString());if(value==null)assertNull(row.output().get("text"));else assertEquals(value.toString(),row.output().get("text"));
        }
        for(Object value:List.of("24:00:00","12:60:00","12:00:60","2026-11-26T12:00:00","12:00:00Z","12:00:00+08:00","1234","",42,true,List.of("12:00:00"))){
            var row=MappingEngine.execute(plan,List.of(java.util.Map.of("clock_value",value)),0,context,null).rows().getFirst();
            assertFalse(row.success());assertEquals("DATE_INVALID",row.errors().getFirst().code());
        }
        assertEquals("MISSING_SOURCE",MappingEngine.execute(plan,List.of(java.util.Map.of()),0,context,null).rows().getFirst().errors().getFirst().code());
    }
    @Test void nativeJsonTextCarrierRequiresExplicitJsonPhysicalLineage(){
        for(String physical:List.of("json","JSONB"))for(String logical:List.of("JSON","JSONB")){
            var spec=new ObjectMapper().createObjectNode().put("schemaVersion","2.0");
            spec.putObject("schemaContract").putArray("source").addObject().put("name","doc").put("logicalType",logical).put("physicalType",physical);
            var rule=spec.putArray("rules").addObject();rule.putObject("input").put("kind","FIELD").put("name","doc");rule.putObject("target").put("name","text").put("logicalType","STRING");var plan=MappingEngine.compile(spec);
            assertDoesNotThrow(()->SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("doc","STRING",true,0,0,0))));
            assertEquals(logical,plan.document().path("schemaContract").path("source").get(0).path("logicalType").asText());
            for(String wire:List.of("ARRAY","ANY","BINARY","INT","DATETIME"))assertThrows(MappingException.class,()->SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("doc",wire,true,0,0,0))));
            ((com.fasterxml.jackson.databind.node.ObjectNode)spec.path("schemaContract").path("source").get(0)).put("physicalType","TEXT");var untrusted=MappingEngine.compile(spec);
            assertThrows(MappingException.class,()->SchemaContract.requireInput(untrusted,List.of(new SchemaContract.Field("doc","STRING",true,0,0,0))));
        }
    }
    @Test void onlyExplicitTimestampPhysicalFieldsAllowTextInTheConfiguredCodecMode() {
        for(String physical:List.of("TIMESTAMP(9)","DATETIME(6)")) {
            var plan=plan(physical);
            var text=List.of(new SchemaContract.Field("event_time","STRING",true,0,0,0));
            assertThrows(MappingException.class,()->SchemaContract.requireInput(plan,text));
            assertDoesNotThrow(()->SchemaContract.requireInput(plan,text,true));
            for(String type:List.of("LONG","DOUBLE","BOOLEAN","ARRAY"))
                assertThrows(MappingException.class,()->SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("event_time",type,true,0,0,0)),true));
        }
        for(String physical:List.of("VARCHAR(80)","STRING","DATE","TIME",""))
            assertThrows(MappingException.class,()->SchemaContract.requireInput(plan(physical),List.of(new SchemaContract.Field("event_time","STRING",true,0,0,0)),true));
    }

    @Test void explicitlyRegisteredDateOnlyTextKeepsStrictCarrierAndPhysicalTypeChecks() {
        for(String physical:List.of("DATE","VARCHAR(20)","TIMESTAMP"))for(String registered:List.of("DATE","DATETIME","")) {
            var spec=new ObjectMapper().createObjectNode().put("schemaVersion","2.0");
            spec.putObject("schemaContract").putArray("source").addObject().put("name","day").put("logicalType","DATE")
                    .put("physicalType",physical).put("registeredFormat",registered);
            var rule=spec.putArray("rules").addObject();rule.putObject("input").put("kind","FIELD").put("name","day");
            rule.putObject("target").put("name","day").put("logicalType","DATE");
            var plan=MappingEngine.compile(spec);var text=List.of(new SchemaContract.Field("day","STRING",true,0,0,0));
            assertThrows(MappingException.class,()->SchemaContract.requireInput(plan,text));
            if(physical.equals("DATE")&&registered.equals("DATE"))assertDoesNotThrow(()->SchemaContract.requireInput(plan,text,true));
            else assertThrows(MappingException.class,()->SchemaContract.requireInput(plan,text,true));
            for(String type:List.of("LONG","DOUBLE","BOOLEAN","ARRAY","DATETIME"))
                assertThrows(MappingException.class,()->SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("day",type,true,0,0,0)),true));
        }
    }
    private static MappingEngine.Plan plan(String physical) {
        var spec=new ObjectMapper().createObjectNode().put("schemaVersion","2.0");
        spec.putObject("schemaContract").putArray("source").addObject().put("name","event_time").put("logicalType","DATETIME").put("physicalType",physical).put("nullable",true);
        var rule=spec.putArray("rules").addObject();rule.putObject("input").put("kind","FIELD").put("name","event_time");rule.putObject("target").put("name","event_time").put("logicalType","DATETIME").put("nullable",true);
        return MappingEngine.compile(spec);
    }
}
