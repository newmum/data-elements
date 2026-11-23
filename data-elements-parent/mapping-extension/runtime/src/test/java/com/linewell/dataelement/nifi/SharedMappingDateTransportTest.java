package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.mapping.core.MappingEngine;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.apache.nifi.json.JsonRecordSetWriter;
import org.apache.nifi.json.JsonTreeReader;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Shared JSON is a transport contract: Hive's explicit field formatter must consume milliseconds. */
class SharedMappingDateTransportTest {
    private static final ObjectMapper JSON=new ObjectMapper();

    @Test void ordinaryJsonWriterCarriesDatesAsMillisecondsIncludingHistoricalDatesAndKeepsNumericIds() throws Exception {
        TimeZone original=TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            for(int count:List.of(20,100)) {
                var specification=JSON.readTree("""
                    {"version":"1.0","mappings":[
                      {"from":"/date","to":"/birthday","targetType":"DATE"},
                      {"from":"/id","to":"/id","targetType":"LONG"}]}
                    """);
                var runner=TestRunners.newTestRunner(HaitongTransformRecord.class);
                var reader=new JsonTreeReader();runner.addControllerService("reader",reader);
                runner.setProperty(reader,"Schema Access Strategy","infer-schema");runner.enableControllerService(reader);
                var writer=new JsonRecordSetWriter();runner.addControllerService("writer",writer);
                runner.setProperty(writer,"Schema Access Strategy","inherit-record-schema");
                runner.setProperty(writer,"Schema Write Strategy","no-schema");
                runner.setProperty(writer,"Suppress Null Values","never-suppress");runner.enableControllerService(writer);
                runner.setProperty(HaitongTransformRecord.READER,"reader");runner.setProperty(HaitongTransformRecord.WRITER,"writer");
                runner.setProperty(HaitongTransformRecord.PLAN,MappingEngine.compile(specification).document().toString());
                var rows=new ArrayList<Map<String,Object>>();
                for(int index=0;index<count;index++)rows.add(Map.of("date",index%2==0?"1949-10-01":"2026-11-23","id",1_700_000_000_000L+index));
                runner.enqueue(JSON.writeValueAsBytes(rows));runner.run();
                runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
                runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);runner.assertTransferCount(HaitongTransformRecord.INVALID,0);
                var output=JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray());
                assertEquals(count,output.size());
                for(int index=0;index<count;index++) {
                    var date=LocalDate.parse((String)rows.get(index).get("date"));
                    assertTrue(output.get(index).path("birthday").isIntegralNumber());
                    assertEquals(Date.valueOf(date).getTime(),output.get(index).path("birthday").longValue());
                    assertNotEquals(date.toEpochDay(),output.get(index).path("birthday").longValue());
                    assertEquals(1_700_000_000_000L+index,output.get(index).path("id").longValue());
                }
                runner.shutdown();
            }
        } finally {TimeZone.setDefault(original);}
    }
}
