package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LegacySystemIdExpressionTest {
    private static final String MILLIS="CAST(TIMESTAMPDIFF(MILLISECOND, TIMESTAMP '1970-01-01 00:00:00', LOCALTIMESTAMP) AS VARCHAR) || CAST(100000 + RAND_INTEGER(900000) AS VARCHAR)";
    private static final String SECONDS="CAST(TIMESTAMPDIFF(SECOND, TIMESTAMP '1970-01-01 00:00:00', LOCALTIMESTAMP) AS VARCHAR) || CAST(100000000 + RAND_INTEGER(900000000) AS VARCHAR)";
    private static final ExecutionContext CONTEXT=new ExecutionContext(Instant.parse("2026-10-13T00:00:00.123Z"),ZoneId.of("Asia/Shanghai"),Locale.CHINA,"legacy-input");
    private static final ObjectMapper JSON=new ObjectMapper();

    private static String evaluate(String expression,long row){
        var spec=JSON.createObjectNode().put("version","1.0");
        spec.putArray("mappings").addObject().put("expression",expression).put("to","/historical_id");
        var result=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.<String,Object>of()),row,CONTEXT,null);
        assertTrue(result.rows().getFirst().success());
        return (String)result.rows().getFirst().output().get("historical_id");
    }

    @Test void oldMillisecondsTemplateKeepsTimestampAndSixDigitSuffix(){
        String value=evaluate(MILLIS,0);
        String timestamp=Long.toString(CONTEXT.clock().toEpochMilli());
        assertTrue(value.startsWith(timestamp));
        assertEquals(timestamp.length()+6,value.length());
        assertTrue(Integer.parseInt(value.substring(timestamp.length()))>=100000);
        assertEquals("STRING",Expressions.outputType(MILLIS));
        assertEquals(value,evaluate(MILLIS.toLowerCase(Locale.ROOT).replace(" ","\n"),0));
    }

    @Test void existingSecondsTemplateKeepsTimestampAndNineDigitSuffix(){
        String value=evaluate(SECONDS,0);
        String timestamp=Long.toString(CONTEXT.clock().getEpochSecond());
        assertTrue(value.startsWith(timestamp));
        assertEquals(timestamp.length()+9,value.length());
        assertTrue(Integer.parseInt(value.substring(timestamp.length()))>=100000000);
    }

    @Test void retryOfOneInputKeepsEachRowIdWithoutDatabaseLookup(){
        for(int count:List.of(20,100)){
            List<String> first=java.util.stream.LongStream.range(0,count).mapToObj(i->evaluate(MILLIS,i)).toList();
            List<String> retry=java.util.stream.LongStream.range(0,count).mapToObj(i->evaluate(MILLIS,i)).toList();
            assertEquals(first,retry);
            assertEquals(count,first.stream().distinct().count());
        }
    }

    @Test void alteredGeneratorsAndArbitrarySqlStillFailValidation(){
        for(String expression:List.of(MILLIS.replace("MILLISECOND","MINUTE"),MILLIS.replace("900000)","900001)"),MILLIS+"; SELECT 1",MILLIS.replace("LOCALTIMESTAMP","untrusted_field"))){
            var spec=JSON.createObjectNode().put("version","1.0");
            spec.putArray("mappings").addObject().put("expression",expression).put("to","/historical_id");
            assertThrows(MappingException.class,()->MappingEngine.compile(spec));
        }
    }
}
