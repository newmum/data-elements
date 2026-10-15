package com.linewell.dataelement.integration.nifi.canvas.mapping;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MappingTemporalPreviewTest {
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
