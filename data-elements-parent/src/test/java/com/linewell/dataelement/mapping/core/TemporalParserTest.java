package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemporalParserTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime FULL = LocalDateTime.of(2024,5,10,14,27,49);
    private static Object auto(Object value,String type) { return TemporalParser.auto(value,type,ZONE); }
    private static Object parse(String pattern,String type,Object value,Map<String,Object> options) {
        return TemporalParser.bind(pattern,type,options).parse(value,ZONE,Locale.ENGLISH);
    }
    @Test void referenceFormatsAndCommonSeparatorsPreserveTime() {
        for(String input:List.of("2024-05-10 14:27:49","2024/05/10 14:27:49","24-05-10 14:27:49",
                "2024.05.10 14:27:49","2024-5-10 14:27:49","2024年5月10日 14:27:49","20240510142749",
                "2024-05-10T14:27:49","Fri May 10 14:27:49 GMT+08:00 2024","Fri May 10 14:27:49 CST 2024",
                "10-MAY-24 02.27.49 PM","10-MAY-2024 02.27.49 PM")) assertEquals(FULL,auto(input,"DATETIME"),input);
        for(String input:List.of("2024/05/10","2024-05-10","2024.5.10","20240510","2024年5月10日")) {
            assertEquals(FULL.toLocalDate(),auto(input,"DATE"),input);
            assertEquals(FULL.toLocalDate().atStartOfDay(),auto(input,"DATETIME"),input);
        }
        assertEquals(LocalDate.of(2024,6,7),auto("2024-06-07","DATE"));
        assertEquals(FULL.withSecond(0),auto("2024-05-10 14:27","DATETIME"));
        assertEquals(LocalTime.of(14,27),auto("14:27","TIME"));
        assertEquals(FULL.toLocalTime(),auto("14:27:49","TIME"));
    }
    @Test void fractionsFromOneToNineDigitsAreDecimalFractions() {
        for(int digits=1;digits<=9;digits++) {
            String fraction="123456789".substring(0,digits);
            int nanos=Integer.parseInt(fraction+"0".repeat(9-digits));
            for(String input:List.of("2024-05-10 14:27:49."+fraction,"20240510142749"+fraction,
                    "10-MAY-24 02.27.49."+fraction+" PM")) assertEquals(FULL.withNano(nanos),auto(input,"DATETIME"),input);
            assertEquals(FULL.toLocalTime().withNano(nanos),auto("14:27:49."+fraction,"TIME"));
        }
    }
    @Test void partialValuesRequireExplicitCompletionInsteadOfInventing1970OrCurrentYear() {
        for(String input:List.of("2024-05","05-10","14:27:49"))
            assertEquals("DATE_INCOMPLETE",assertThrows(MappingException.class,()->auto(input,"DATETIME")).code());
        var options=Map.<String,Object>of("defaultDate","2000-01-01");
        assertEquals(LocalDate.of(2024,5,1),parse("AUTO","DATE","2024-05",options));
        assertEquals(LocalDate.of(2000,5,10),parse("AUTO","DATE","05-10",options));
        assertEquals(LocalDateTime.of(2000,1,1,14,27,49),parse("AUTO","DATETIME","14:27:49",options));
        assertEquals(LocalDate.of(2024,5,1),parse("yyyy-MM","DATE","2024-05",options));
        assertThrows(MappingException.class,()->parse("AUTO","DATE","02-29",Map.of("defaultDate","2001-01-01")));
        assertEquals(LocalDate.of(2099,5,10),auto("99-05-10","DATE"));
    }
    @Test void timezoneOffsetsRfcAndJdbcObjectsDoNotUseJvmDefaultTimezone() {
        TimeZone previous=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        try {
            for(Object input:List.of("2024-05-10T06:27:49Z","2024-05-10 14:27:49 +08:00","2024-05-10 14:27:49+0800",
                    "2024-05-10T14:27:49+08","2024-05-10T14:27:49+08:00[Asia/Shanghai]",
                    "Fri, 10 May 2024 06:27:49 GMT",java.sql.Timestamp.from(Instant.parse("2024-05-10T06:27:49Z")),
                    OffsetDateTime.parse("2024-05-10T14:27:49+08:00"),ZonedDateTime.parse("2024-05-10T14:27:49+08:00[Asia/Shanghai]"))) {
                assertEquals(FULL,auto(input,"DATETIME"),input.toString());
                assertEquals(Instant.parse("2024-05-10T06:27:49Z"),auto(input,"INSTANT"),input.toString());
            }
            assertEquals(LocalDate.of(2024,5,10),auto(java.sql.Date.valueOf("2024-05-10"),"DATE"));
            assertEquals(LocalTime.of(14,27,49),auto(java.sql.Time.valueOf("14:27:49"),"TIME"));
        } finally { TimeZone.setDefault(previous); }
    }
    @Test void invalidCalendarTrailingTextAmbiguityAndDstAreRejected() {
        for(String input:List.of("2023/02/29","2024-02-30","2024-13-01","2024-05-10 24:00:00","2024-05-10 14:27:60",
                "2024-05-10suffix","2024-05-10 14:27:49.1234567890","2024-05-10 14:27:49T"," ","0","2024-05-10 14:27:49+08:00[Europe/Paris]","Fri, 30 Feb 2025 14:27:49 GMT"))
            assertThrows(MappingException.class,()->auto(input,"DATETIME"),input);
        assertEquals("DATE_AMBIGUOUS",assertThrows(MappingException.class,()->auto("10/05/24","DATETIME")).code());
        assertEquals(LocalDate.of(2024,5,10),parse("dd/MM/yyyy","DATE","10/05/2024",Map.of()));
        for(String input:List.of("2024-03-10 02:30:00","2024-11-03 01:30:00")) {
            assertEquals(LocalDateTime.parse(input.replace(' ','T')),TemporalParser.auto(input,"DATETIME",ZoneId.of("America/New_York")));
            assertEquals("DATE_AMBIGUOUS",assertThrows(MappingException.class,()->TemporalParser.auto(input,"INSTANT",ZoneId.of("America/New_York"))).code());
        }
        assertThrows(MappingException.class,()->TemporalParser.bind("bad pattern","DATE",Map.of()));
        assertThrows(MappingException.class,()->TemporalParser.bind("AUTO","DATE",Map.of("defaultDate","2024-02-30")));
    }
    @Test void historicalBirthdaysAndCompactCalendarNumbersAreNotEpochsOrDstErrors() {
        for(Object input:List.of("1947/04/15","1947-04-15",19470415)) {
            assertEquals(LocalDate.of(1947,4,15),auto(input,"DATE"));
            assertEquals(LocalDateTime.of(1947,4,15,0,0),auto(input,"DATETIME"));
        }
        for(Object input:List.of("20260701112021",20260701112021L,new java.math.BigDecimal("20260701112021.0")))
            assertEquals(LocalDateTime.of(2026,7,1,11,20,21),auto(input,"DATETIME"));
        for(Object input:List.of("20250219",20250219,new java.math.BigDecimal("20250219.0")))
            assertEquals(LocalDate.of(2025,2,19),auto(input,"DATE"));
        for(Object input:List.of("20010236",20010236,"20250230",20250230))
            assertEquals("DATE_INVALID",assertThrows(MappingException.class,()->auto(input,"DATE")).code());
    }
    @Test void numericEpochUnitsAreExplicitExactAndHandleNegativeValues() {
        var expected=Instant.parse("2024-05-10T06:27:49.123456789Z");
        for(String mode:List.of("EPOCH_SECONDS","EPOCH_MILLIS","EPOCH_MICROS","EPOCH_NANOS")) {
            long factor=switch(mode){case "EPOCH_SECONDS"->1;case "EPOCH_MILLIS"->1000;case "EPOCH_MICROS"->1_000_000;default->1_000_000_000;};
            long value=expected.getEpochSecond()*factor+expected.getNano()/(1_000_000_000/factor);
            assertEquals(Instant.ofEpochSecond(expected.getEpochSecond(),expected.getNano()/(1_000_000_000/factor)*(1_000_000_000/factor)),parse(mode,"INSTANT",Long.toString(value),Map.of()));
        }
        assertEquals(Instant.ofEpochMilli(-1),parse("EPOCH_MILLIS","INSTANT","-1",Map.of()));
        assertEquals(Instant.ofEpochSecond(1715322469L),auto(1715322469L,"INSTANT"));
        assertEquals(Instant.ofEpochMilli(171532246912L),auto(171532246912L,"INSTANT"));
        assertThrows(MappingException.class,()->parse("EPOCH_MILLIS","INSTANT","1.5",Map.of()));
        assertEquals("BUDGET_EXCEEDED",assertThrows(MappingException.class,()->parse("EPOCH_MILLIS","INSTANT","1e1000000000",Map.of())).code());
    }
    @Test void existingFunctionsCastsAndAutoDateOperatorShareOneImplementation() throws Exception {
        var context=new ExecutionContext(Instant.EPOCH,ZONE,Locale.ENGLISH,"date");var json=new ObjectMapper();
        assertEquals(FULL,FunctionRegistry.bind("parseDate",json.readTree("[\"AUTO\",\"DATETIME\"]")).apply("2024/05/10 14:27:49",context));
        assertEquals(FULL,FunctionRegistry.bind("parseDate",json.readTree("[\"yyyy-MM-dd HH:mm:ss\",\"DATETIME\"]")).apply("2024-05-10 14:27:49",context));
        assertEquals(FULL,ValueTypes.cast("2024/05/10 14:27:49","DATETIME",38,10,RoundingMode.UNNECESSARY,ZONE));
        var spec=json.readTree("""
            {"schemaVersion":"2.0","rules":[{"ruleId":"r","target":{"name":"out","logicalType":"DATETIME"},"input":{"kind":"FIELD","name":"in"},"steps":[{"operator":"DATE_PARSE","args":{"logicalType":"DATETIME"}}]}]}
            """);
        var result=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("in","2024/05/10 14:27:49")),0,context,null);
        assertTrue(result.rows().getFirst().success());assertEquals(FULL,result.rows().getFirst().output().get("out"));
    }
    @Test void compiledParserIsThreadSafeForMixedFormats() throws Exception {
        var parser=TemporalParser.bind("AUTO","DATETIME",Map.of());
        try(var pool=Executors.newFixedThreadPool(4)) {
            var tasks=new ArrayList<Callable<Boolean>>();
            for(int worker=0;worker<8;worker++)tasks.add(()->{
                for(int row=0;row<5000;row++) {
                    String value=switch(row%4){case 0->"2024/05/10 14:27:49";case 1->"20240510142749";case 2->"2024-05-10T06:27:49Z";default->"10-MAY-24 02.27.49 PM";};
                    if(!FULL.equals(parser.parse(value,ZONE,Locale.ENGLISH)))return false;
                }return true;
            });
            for(var future:pool.invokeAll(tasks))assertTrue(future.get());
        }
    }
    @Test void sameSourceColumnMixesFormatsAcrossTwentyAndOneHundredRecords() throws Exception {
        var json=new ObjectMapper();
        var spec=json.readTree("""
            {"schemaVersion":"2.0","policies":{"recordErrorMode":"ROUTE_INVALID","zoneId":"Asia/Shanghai"},"rules":[{"ruleId":"r","target":{"name":"out","logicalType":"DATETIME"},"input":{"kind":"FIELD","name":"mixed"},"steps":[{"operator":"DATE_PARSE","args":{"pattern":"AUTO","logicalType":"DATETIME"}}]}]}
            """);
        var plan=MappingEngine.compile(spec);var context=new ExecutionContext(Instant.EPOCH,ZONE,Locale.ROOT,"mixed");
        var values=List.of("2024/05/10","2024-06-07","2024/05/10 14:27:49","2024-05-10T06:27:49Z","20240510142749123456","invalid");
        for(int count:List.of(20,100)) {
            var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<count;i++)rows.add(Map.of("mixed",values.get(i%values.size())));
            var result=MappingEngine.execute(plan,rows,0,context,(dictionary,keys)->{fail("Pure time conversion must not query a dictionary");return Map.of();});
            assertEquals(count,result.rows().size());
            for(int i=0;i<count;i++) {
                var row=result.rows().get(i);int kind=i%values.size();assertEquals(kind!=5,row.success());
                if(kind==5)continue;
                assertEquals(switch(kind){case 0->LocalDateTime.of(2024,5,10,0,0);case 1->LocalDateTime.of(2024,6,7,0,0);case 4->FULL.withNano(123456000);default->FULL;},row.output().get("out"));
            }
        }
    }
}
