package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class MappingIdentityTemporalTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static ExecutionContext context(String id){return new ExecutionContext(Instant.parse("2026-10-14T06:27:49.123456Z"),ZoneId.of("Asia/Shanghai"),Locale.CHINA,id);}
    @Test void savedRegistrationControlsDatePrecisionRegardlessOfFieldNameOrOldParser()throws Exception {
        for(String name:List.of("birth_date","updated_time","opaque"))for(String format:List.of("DATE","DATETIME")) {
            var spec=JSON.createObjectNode().put("schemaVersion","2.0");var contract=spec.putObject("schemaContract");
            contract.putArray("source").addObject().put("name",name).put("logicalType","STRING").put("registeredFormat",format);
            contract.putArray("target").addObject().put("name",name).put("logicalType","DATETIME").put("dateContainsTime",true);
            var rule=spec.putArray("rules").addObject().put("ruleId","r");rule.putObject("input").put("kind","FIELD").put("name",name);rule.putObject("target").put("name",name).put("logicalType",format.equals("DATE")?"DATETIME":"DATE");
            rule.putArray("steps").addObject().put("operator","DATE_PARSE").putObject("args").put("pattern","AUTO").put("logicalType","DATE");
            var plan=MappingEngine.compile(spec);assertEquals(format,plan.outputTypes().get(name));
            var result=MappingEngine.execute(plan,List.of(Map.of(name,"20260701112021"),Map.of(name,"2026/07/01 11:20:21")),0,context("registered"),null);
            for(var row:result.rows()){assertTrue(row.success());assertEquals(format.equals("DATE")?LocalDate.of(2026,7,1):LocalDateTime.of(2026,7,1,11,20,21),row.output().get(name));}
            if(format.equals("DATETIME")) {((com.fasterxml.jackson.databind.node.ObjectNode)contract.path("target").get(0)).put("logicalType","DATE").put("dateContainsTime",false);assertEquals("REGISTERED_FORMAT_TARGET_MISMATCH",assertThrows(MappingException.class,()->MappingEngine.compile(spec)).code());}
        }
    }
    @Test void compactUuidRetainsFullBitsAcrossRowsWindowsInputsAndWorkers() throws Exception {
        Set<String> identifiers=ConcurrentHashMap.newKeySet();
        try(var executor=Executors.newFixedThreadPool(4)) {
            List<Future<?>> futures=new ArrayList<>();
            for(int worker=0;worker<4;worker++) {
                int number=worker;futures.add(executor.submit(()->{
                    var ctx=context("flow-"+number+":node");
                    for(long row=0;row<100_000;row++) {
                        String id=ctx.compactGeneratedId(row,"ods");
                        assertTrue(id.matches("[0-9a-f]{32}"));assertTrue(identifiers.add(id));
                        if(row%512==0)assertEquals(id,ctx.compactGeneratedId(row,"ods"));
                    }
                }));
            }
            for(var future:futures)future.get();
        }
        assertEquals(400_000,identifiers.size());
        assertNotEquals(context("a:1").compactGeneratedId(2,"r"),context("a").compactGeneratedId(1,"2:r"));
    }
    @Test void knownOdsTemplatesCompileToCompactSystemRuleButManualValuesSurvive() throws Exception {
        String old="CAST(TIMESTAMPDIFF(SECOND, TIMESTAMP '1970-01-01 00:00:00', LOCALTIMESTAMP) AS VARCHAR) || CAST(100000000 + RAND_INTEGER(900000000) AS VARCHAR)";
        for(String expression:List.of(old,"UUID()")) {
            var spec=JSON.createObjectNode().put("version","1.0");
            spec.putArray("mappings").addObject().put("to","/ODS_UUID").put("expression",expression).put("targetType","varchar(32)");
            var plan=MappingEngine.compile(spec);assertEquals("UUID32",plan.document().path("rules").path(0).path("input").path("generator").asText());
            var first=MappingEngine.execute(plan,Collections.nCopies(100,Map.of()),0,context("flow"),null);
            assertEquals(100,first.rows().stream().map(r->r.output().get("ODS_UUID")).distinct().count());
            assertTrue(first.rows().stream().allMatch(MappingEngine.Row::success));
            assertEquals(first,MappingEngine.execute(plan,Collections.nCopies(100,Map.of()),0,context("flow"),null));
        }
        var manual=MappingEngine.compile(JSON.readTree("{\"version\":\"1.0\",\"mappings\":[{\"to\":\"/ODS_UUID\",\"constant\":\"manual-key\"}]}"));
        assertEquals("manual-key",MappingEngine.execute(manual,List.of(Map.of()),0,context("flow"),null).rows().getFirst().output().get("ODS_UUID"));
    }
    @Test void datetimeAndDateContractsHaveDifferentPrecisionAndNullSurvives() throws Exception {
        var spec=JSON.readTree("""
            {"schemaVersion":"2.0","rules":[
              {"ruleId":"time","input":{"kind":"FIELD","name":"time"},"target":{"name":"time","logicalType":"DATETIME"},"steps":[]},
              {"ruleId":"day","input":{"kind":"FIELD","name":"time"},"target":{"name":"day","logicalType":"DATE"},"steps":[]}]}
            """);
        var plan=MappingEngine.compile(spec);var timestamp=LocalDateTime.parse("2026-10-14T14:27:49.123456");
        var output=MappingEngine.execute(plan,List.of(Map.of("time",java.sql.Timestamp.from(timestamp.atZone(context("x").zone()).toInstant()))),0,context("x"),null).rows().getFirst().output();
        assertEquals(timestamp,output.get("time"));assertEquals(timestamp.toLocalDate(),output.get("day"));
        var textOutput=MappingEngine.execute(plan,List.of(Map.of("time","2026-10-14 14:27:49.123456")),0,context("x"),null).rows().getFirst();
        assertTrue(textOutput.success());assertEquals(timestamp,textOutput.output().get("time"));assertEquals(timestamp.toLocalDate(),textOutput.output().get("day"));
        assertEquals(LocalDate.of(2026,10,15),ValueTypes.cast("2026-10-14T23:30:00Z","DATE",38,10,java.math.RoundingMode.UNNECESSARY,ZoneId.of("Asia/Shanghai")));
        assertNull(MappingEngine.execute(plan,List.of(Collections.singletonMap("time",null)),0,context("x"),null).rows().getFirst().output().get("time"));
    }
    @Test void dateOnlyParserCannotSilentlyInventMidnightForTimeTarget() throws Exception {
        var spec=JSON.readTree("""
            {"schemaVersion":"2.0","rules":[{"ruleId":"r","input":{"kind":"FIELD","name":"v"},"target":{"name":"v","logicalType":"DATETIME"},"steps":[{"operator":"DATE_PARSE","args":{"pattern":"uuuu-MM-dd","logicalType":"DATE"}}]}]}
            """);
        assertEquals("TEMPORAL_PRECISION_LOSS",assertThrows(MappingException.class,()->MappingEngine.compile(spec)).code());
        var args=(com.fasterxml.jackson.databind.node.ObjectNode)spec.path("rules").path(0).path("steps").path(0).path("args");
        args.put("pattern","uuuu-MM-dd HH:mm:ss").put("logicalType","DATETIME");
        var result=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("v","2026-10-14 14:27:49")),0,context("x"),null);
        assertEquals(LocalDateTime.parse("2026-10-14T14:27:49"),result.rows().getFirst().output().get("v"));
    }
}
