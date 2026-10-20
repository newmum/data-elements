package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistoricalEpochMappingTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final ExecutionContext CONTEXT = new ExecutionContext(Instant.EPOCH, ZONE, Locale.ROOT, "historical-fixture");

    @Test void screenshotDateSupportsTwelveDigitNumbersStringsAndLiteralQuotes() {
        var expected = LocalDateTime.of(1976,4,4,0,0);
        for (Object carrier : List.of(197395200000L, "197395200000", "\"197395200000\"",
                " \" 197395200000 \" ", new BigDecimal("197395200000.000"))) {
            assertEquals(expected, TemporalParser.auto(carrier,"DATETIME",ZONE));
            assertEquals(expected.toLocalDate(), TemporalParser.auto(carrier,"DATE",ZONE));
            assertEquals(Instant.ofEpochMilli(197395200000L), TemporalParser.auto(carrier,"INSTANT",ZONE));
        }
        for (long millis : List.of(10000000000L,99999999999L,100000000000L,999999999999L,-197395200000L,-99999999999L))
            for (Object carrier : List.of(millis,Long.toString(millis),"\""+millis+"\""))
                assertEquals(Instant.ofEpochMilli(millis),TemporalParser.auto(carrier,"INSTANT",ZONE));
        assertEquals(Instant.ofEpochSecond(9999999999L),TemporalParser.auto(9999999999L,"INSTANT",ZONE));
    }

    @Test void dateRulesWithJdbcMillisMetadataCoverTheEpochBoundaryWithoutGuessingSeconds() throws Exception {
        var spec=JSON.readTree("""
            {"version":"1.0","recordErrorMode":"ROUTE_INVALID","mappings":[
              {"from":"csrq","to":"at","targetType":"DATETIME","sourceTemporalUnit":"EPOCH_MILLIS"},
              {"from":"csrq","to":"day","targetType":"DATE","sourceTemporalUnit":"EPOCH_MILLIS"}]}
            """);
        var plan=MappingEngine.compile(spec);
        var values=List.of(0L,1L,-1L,86400000L,-86400000L,197395200000L,946656000000L,-725875200000L);
        for(int count:List.of(20,100)) {
            List<Map<String,Object>> rows=new ArrayList<>();
            for(int i=0;i<count;i++) {long value=values.get(i%values.size());rows.add(Map.of("csrq",switch(i%3){case 0->value;case 1->Long.toString(value);default->"\""+value+"\"";}));}
            var output=MappingEngine.execute(plan,rows,0,CONTEXT,(dict,keys)->{fail("Temporal conversion must not query dictionaries");return Map.of();});
            assertEquals(0,output.dictionaryBatches());
            for(int i=0;i<count;i++) {
                var row=output.rows().get(i);assertTrue(row.success(),row.errors().toString());
                var expected=Instant.ofEpochMilli(values.get(i%values.size())).atZone(ZONE).toLocalDateTime();
                assertEquals(expected,row.output().get("at"));assertEquals(expected.toLocalDate(),row.output().get("day"));
            }
        }
    }

    @Test void registeredOracleDateContractAcceptsJsonCarriersAndStillChecksEveryValue() throws Exception {
        var spec=JSON.readTree("""
            {"schemaVersion":"2.0","policies":{"recordErrorMode":"ROUTE_INVALID"},
             "schemaContract":{"source":[{"name":"csrq","logicalType":"DATETIME","physicalType":"DATE","dateContainsTime":true}]},
             "rules":[{"input":{"kind":"FIELD","name":"csrq"},"target":{"name":"out","logicalType":"DATETIME"},"steps":[]}]}
            """);
        var plan=MappingEngine.compile(spec);
        assertEquals("EPOCH_MILLIS",plan.document().path("rules").get(0).path("input").path("numericTemporalUnit").asText());
        for(String carrierType:List.of("LONG","INT","STRING","DATETIME"))SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("csrq",carrierType,true,0,0,0)));
        assertThrows(MappingException.class,()->SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("csrq","BOOLEAN",true,0,0,0))));
        var result=MappingEngine.execute(plan,List.of(Map.of("csrq",0L),Map.of("csrq","1976-04-04 12:34:56"),Map.of("csrq","bad-date")),0,CONTEXT,null);
        assertTrue(result.rows().get(0).success());assertEquals(LocalDateTime.of(1970,1,1,8,0),result.rows().get(0).output().get("out"));
        assertEquals(LocalDateTime.of(1976,4,4,12,34,56),result.rows().get(1).output().get("out"));
        assertFalse(result.rows().get(2).success());assertEquals("DATE_INVALID",result.rows().get(2).errors().getFirst().code());
    }

    @Test void explicitPatternsRemainAuthoritativeAndOrdinaryNumbersAreNotChanged() throws Exception {
        for(String pattern:List.of("EPOCH_SECONDS","uuuuMMdd")) {
            var spec=JSON.readTree("""
                {"schemaVersion":"2.0","rules":[{"input":{"kind":"FIELD","name":"x","numericTemporalUnit":"EPOCH_MILLIS"},
                 "target":{"name":"out","logicalType":"DATETIME"},"steps":[{"operator":"DATE_PARSE","args":{"pattern":"%s","logicalType":"DATETIME"}}]}]}
                """.formatted(pattern));
            Object value=pattern.equals("EPOCH_SECONDS")?197395200L:"19760404";
            var row=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("x",value)),0,CONTEXT,null).rows().getFirst();
            assertTrue(row.success(),row.errors().toString());assertEquals(LocalDateTime.of(1976,4,4,0,0),row.output().get("out"));
        }
        var ordinary=MappingEngine.compile(JSON.readTree("{\"version\":\"1.0\",\"mappings\":[{\"from\":\"x\",\"to\":\"x\",\"targetType\":\"LONG\"}]}"));
        assertEquals(197395200000L,MappingEngine.execute(ordinary,List.of(Map.of("x",197395200000L)),0,CONTEXT,null).rows().getFirst().output().get("x"));
        for(Object invalid:List.of(20250230,"20250230","20250230000000",new BigDecimal("197395200000.25")))
            assertThrows(MappingException.class,()->TemporalParser.auto(invalid,"DATETIME",ZONE));
    }
}
