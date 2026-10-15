package com.linewell.dataelement.integration.nifi.canvas.mapping;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MappingTemporalPreviewTest {
    @Test void legacyGeneratedDateExpressionUsesTheSavedFormatAndPerValueParser()throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();var zone=ZoneId.of("Asia/Shanghai");
        for(String format:List.of("DATE","DATETIME")) {
            var draft=json.createObjectNode().put("schemaVersion","2.0");draft.putObject("schemaContract").putArray("source").addObject().put("name","opaque").put("logicalType","STRING").put("registeredFormat",format);
            var rule=draft.putArray("rules").addObject().put("ruleId","r");rule.putObject("target").put("name","out").put("logicalType",format.equals("DATE")?"DATETIME":"DATE");
            rule.putObject("input").put("kind","EXPRESSION").put("expression","CAST(NULLIF(TRIM(CAST(${field:opaque} AS VARCHAR)), '') AS DATE)");rule.putArray("steps");
            UnifiedMappingService.applyRegisteredTemporalInputs(draft);
            var plan=com.linewell.dataelement.mapping.core.MappingEngine.compile(draft);
            var result=com.linewell.dataelement.mapping.core.MappingEngine.execute(plan,List.of(Map.of("opaque","20260701112021"),Map.of("opaque","2026/07/01 11:20:21"),Map.of("opaque","   ")),0,new com.linewell.dataelement.mapping.core.ExecutionContext(Instant.EPOCH,zone,Locale.ROOT,"test"),null);
            assertEquals(format,plan.outputTypes().get("out"));assertTrue(result.rows().stream().allMatch(com.linewell.dataelement.mapping.core.MappingEngine.Row::success));
            Object expected=format.equals("DATE")?LocalDate.of(2026,7,1):LocalDateTime.of(2026,7,1,11,20,21);assertEquals(expected,result.rows().get(0).output().get("out"));assertEquals(expected,result.rows().get(1).output().get("out"));assertNull(result.rows().get(2).output().get("out"));
        }
    }
    @Test void previewKeepsSecondsAndNanosecondsWithoutChangingNonTemporalValues() {
        var date=LocalDateTime.of(2024,5,10,14,27,49,123456789);
        var row=new LinkedHashMap<String,Object>();row.put("dt",date);row.put("time",date.toLocalTime());row.put("day",date.toLocalDate());row.put("null",null);row.put("masked","***");
        for(int count:List.of(20,100)){
            var output=UnifiedMappingService.temporalPreviewRows(Collections.nCopies(count,row));assertEquals(count,output.size());
            for(var item:output){assertEquals("2024-05-10 14:27:49.123456789",item.get("dt"));assertEquals("14:27:49.123456789",item.get("time"));assertEquals("2024-05-10",item.get("day"));assertNull(item.get("null"));assertEquals("***",item.get("masked"));}
        }
        assertSame(date,row.get("dt"));
    }
}
