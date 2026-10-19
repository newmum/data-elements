package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EpochAutoMappingTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant INSTANT = Instant.parse("2017-06-06T09:54:28Z");
    private static final LocalDateTime LOCAL = LocalDateTime.of(2017,6,6,17,54,28);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ExecutionContext CONTEXT = new ExecutionContext(Instant.EPOCH,ZONE,Locale.ROOT,"epoch-fixture");

    @Test void screenshotMillisAndSecondsAcceptNumericAndTextCarriers() {
        for(Object value:List.of(1496742868000L,1496742868,"1496742868000","1496742868","+1496742868000",
                new BigInteger("1496742868000"),new BigDecimal("1496742868000.000"),1.496742868E12)) {
            assertEquals(INSTANT,TemporalParser.auto(value,"INSTANT",ZONE),value.toString());
            assertEquals(LOCAL,TemporalParser.auto(value,"DATETIME",ZONE),value.toString());
            assertEquals(LOCAL.toLocalDate(),TemporalParser.auto(value,"DATE",ZONE),value.toString());
            assertEquals(LOCAL.toLocalTime(),TemporalParser.auto(value,"TIME",ZONE),value.toString());
        }
    }

    @Test void quotedIntegerTextWorksInAutoAndExplicitEpochModes() {
        for (String value:List.of("1496742868000", "\"1496742868000\"", "  \"1496742868000\"  ",
                "\" 1496742868000 \"", "\"+1496742868000\"")) {
            assertEquals(INSTANT,TemporalParser.auto(value,"INSTANT",ZONE),value);
            assertEquals(LOCAL,ValueTypes.cast(value,"DATETIME",0,0,RoundingMode.UNNECESSARY,ZONE),value);
            assertEquals(INSTANT,TemporalParser.bind("EPOCH_MILLIS","INSTANT",Map.of()).parse(value,ZONE,Locale.ROOT),value);
        }
        assertEquals(INSTANT,TemporalParser.auto("\"1496742868\"","INSTANT",ZONE));
        assertEquals(Instant.ofEpochMilli(-1496742868000L),TemporalParser.auto("\"-1496742868000\"","INSTANT",ZONE));
        assertEquals(LOCAL.withNano(123_000_000),TemporalParser.auto("\"1496742868123\"","DATETIME",ZONE));
        assertEquals(INSTANT,TemporalParser.bind("EPOCH_SECONDS","INSTANT",Map.of()).parse("\"1496742868\"",ZONE,Locale.ROOT));
        assertEquals(INSTANT,TemporalParser.bind("EPOCH_MICROS","INSTANT",Map.of()).parse("\"1496742868000000\"",ZONE,Locale.ROOT));
        assertEquals(INSTANT,TemporalParser.bind("EPOCH_NANOS","INSTANT",Map.of()).parse("\"1496742868000000000\"",ZONE,Locale.ROOT));
        for (String value:List.of("\"1496742868000", "1496742868000\"", "\"\"1496742868000\"\"",
                "\"1496742868.5\"", "\"1.496742868E12\"", "\"not a timestamp\"", "\"20250230\""))
            assertThrows(MappingException.class,()->TemporalParser.auto(value,"DATETIME",ZONE),value);
        assertEquals(assertThrows(MappingException.class,()->TemporalParser.auto("1496742868000000","DATETIME",ZONE)).code(),
                assertThrows(MappingException.class,()->TemporalParser.auto("\"1496742868000000\"","DATETIME",ZONE)).code());
        // JSON string delimiters are removed by the JSON reader; literal embedded
        // delimiters are a separate carrier, and both reach the same date contract.
        try {
            assertEquals(LOCAL,TemporalParser.auto(JSON.readTree("\"1496742868000\"").asText(),"DATETIME",ZONE));
            assertEquals(LOCAL,TemporalParser.auto(JSON.readTree("\"\\\"1496742868000\\\"\"").asText(),"DATETIME",ZONE));
        } catch(Exception error) { throw new AssertionError(error); }
    }

    @Test void millisPreserveFractionAndSignedEpochsUseTheConfiguredZone() {
        for(Object value:List.of(1496742868123L,"1496742868123"))
            assertEquals(LOCAL.withNano(123_000_000),TemporalParser.auto(value,"DATETIME",ZONE));
        for(Object value:List.of(-1496742868000L,"-1496742868000",-1496742868,"-1496742868"))
            assertEquals(Instant.ofEpochMilli(-1496742868000L),TemporalParser.auto(value,"INSTANT",ZONE));
        TimeZone previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        try {
            assertEquals(LOCAL,TemporalParser.auto(1496742868000L,"DATETIME",ZONE));
            assertEquals(LOCAL.minusHours(8),TemporalParser.auto(1496742868000L,"DATETIME",ZoneOffset.UTC));
        } finally { TimeZone.setDefault(previous); }
    }

    @Test void calendarDatesAreNotReinterpretedAndUnclearUnitsStayExplicit() {
        assertEquals(LocalDate.of(1947,4,15),TemporalParser.auto(19470415,"DATE",ZONE));
        assertEquals(LocalDateTime.of(2026,7,1,11,20,21),TemporalParser.auto(20260701112021L,"DATETIME",ZONE));
        assertEquals(LocalDateTime.of(2026,7,1,11,20,21,120_000_000),TemporalParser.auto("2026070111202112","DATETIME",ZONE));
        for(Object value:List.of(20250230,"20250230",20250230000000L,"20250230000000"))
            assertEquals("DATE_INVALID",assertThrows(MappingException.class,()->TemporalParser.auto(value,"DATETIME",ZONE)).code());
        for(Object value:List.of(0,123456789012L,1496742868000000L,1496742868000000000L,new BigDecimal("1496742868.5")))
            assertEquals("DATE_UNIT_REQUIRED",assertThrows(MappingException.class,()->TemporalParser.auto(value,"DATETIME",ZONE)).code());
        // Micro/nanoseconds and nonstandard-width values still have unambiguous explicit modes.
        assertEquals(INSTANT,TemporalParser.bind("EPOCH_MICROS","INSTANT",Map.of()).parse(1496742868000000L,ZONE,Locale.ROOT));
        assertEquals(INSTANT,TemporalParser.bind("EPOCH_NANOS","INSTANT",Map.of()).parse(1496742868000000000L,ZONE,Locale.ROOT));
        assertEquals(1496742868000L,ValueTypes.cast(1496742868000L,"LONG",0,0,RoundingMode.UNNECESSARY,ZONE));
    }

    @Test void functionsDateParseCastsAndLegacyTargetValidationShareEpochAuto() throws Exception {
        assertEquals(LOCAL,FunctionRegistry.bind("parseDate",JSON.readTree("[\"AUTO\",\"DATETIME\"]")).apply(1496742868000L,CONTEXT));
        assertEquals(LOCAL,ValueTypes.cast(1496742868000L,"DATETIME",0,0,RoundingMode.UNNECESSARY,ZONE));
        for(String step:List.of("[]","[{\"operator\":\"DATE_PARSE\",\"args\":{\"pattern\":\"AUTO\",\"logicalType\":\"DATETIME\"}}]",
                "[{\"operator\":\"CAST\",\"args\":{\"logicalType\":\"DATETIME\"}}]")) {
            var spec=JSON.readTree("{\"schemaVersion\":\"2.0\",\"rules\":[{\"ruleId\":\"r\",\"input\":{\"kind\":\"FIELD\",\"name\":\"value\"},\"target\":{\"name\":\"out\",\"logicalType\":\"DATETIME\"},\"steps\":"+step+"}]}");
            var row=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("value",1496742868000L)),0,CONTEXT,null).rows().getFirst();
            assertTrue(row.success(),row.errors().toString());assertEquals(LOCAL,row.output().get("out"));
        }
        var plan=MappingEngine.compile(JSON.readTree("""
          {"version":"1.0","recordErrorMode":"ROUTE_INVALID","mappings":[
            {"from":"LW_RKSJ","to":"LW_RKSJ","targetType":"DATETIME"},
            {"from":"LW_RKSJ","to":"DAY_ONLY","targetType":"DATE"}]}
          """));
        for(int count:List.of(20,100)) {
            var rows=new ArrayList<Map<String,Object>>();
            var carriers=List.of(1496742868000L,"1496742868000","\"1496742868000\"","1496742868","\"1496742868\"");
            for(int i=0;i<count;i++)rows.add(Map.of("LW_RKSJ",carriers.get(i%carriers.size())));
            rows.add(Map.of("LW_RKSJ","20250230"));
            var result=MappingEngine.execute(plan,rows,0,CONTEXT,(dictionary,keys)->{fail("Date conversion must not query dictionaries");return Map.of();});
            assertEquals(0,result.dictionaryBatches());
            for(int i=0;i<count;i++) {
                var row=result.rows().get(i);assertTrue(row.success(),row.errors().toString());
                assertEquals(LOCAL,row.output().get("LW_RKSJ"));assertEquals(LOCAL.toLocalDate(),row.output().get("DAY_ONLY"));
            }
            assertEquals("DATE_INVALID",result.rows().getLast().errors().getFirst().code());
        }
    }
}
