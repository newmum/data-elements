package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** A reproducible core throughput smoke benchmark; does not claim database/end-to-end throughput. */
class MappingEnginePerformanceTest {
    @Test void compiledPlanProcessesBoundedWindowsWithNoConversionSql(){
        ObjectMapper json=new ObjectMapper();var spec=json.createObjectNode().put("version","1.0");var rules=spec.putArray("mappings");
        for(int i=0;i<100;i++)rules.addObject().put("from","text").put("to","field"+i).putObject("transform").put("fn","upper");
        var plan=MappingEngine.compile(spec);var context=new ExecutionContext(Instant.EPOCH,ZoneOffset.UTC,Locale.ROOT,"benchmark");
        List<Map<String,Object>>window=new ArrayList<>();for(int i=0;i<500;i++)window.add(Map.of("text","中文abc"));
        for(int i=0;i<3;i++)MappingEngine.execute(plan,window,0,context,null);
        long began=System.nanoTime();int records=0;for(int i=0;i<20;i++){var result=MappingEngine.execute(plan,window,records,context,null);records+=result.rows().size();assertEquals(0,result.dictionaryBatches());assertEquals("中文ABC",result.rows().getLast().output().get("field99"));}
        double seconds=(System.nanoTime()-began)/1_000_000_000d;
        System.out.printf(Locale.ROOT,"MAPPING_CORE_BENCHMARK records=%d fields=100 seconds=%.3f rowsPerSecond=%.0f conversionSql=0 windowRecords=500%n",records,seconds,records/seconds);
    }
}
