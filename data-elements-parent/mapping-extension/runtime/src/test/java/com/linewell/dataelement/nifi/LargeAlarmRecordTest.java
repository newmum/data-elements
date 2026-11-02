package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.node.*;
import com.linewell.dataelement.mapping.core.*;
import org.apache.nifi.util.TestRunner;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LargeAlarmRecordTest {
    private static final String IMAGE="QUJD".repeat(524_288); // 2 MiB of valid, synthetic Base64.
    private static TestRunner runner()throws Exception {
        var runner=PushedAlarmCompatibilityTest.runner(true);var spec=PushedAlarmCompatibilityTest.plan(true).document();
        for(var rule:spec.path("rules"))if(rule.path("target").path("name").asText().equals("pic_base64"))((ObjectNode)rule).withArray("steps").addObject().put("operator","DIRECT");
        runner.setProperty(HaitongTransformRecord.PLAN,MappingEngine.compile(spec).document().toString());return runner;
    }
    private static ArrayNode rows(int count,String picture){
        var rows=PushedAlarmCompatibilityTest.rows(count);rows.forEach(row->((ObjectNode)row).put("picBase64",picture));return rows;
    }
    @Test void multiMiBImagesCrossWindowsAndReachBothJdbcStorageHops()throws Exception {
        var runner=runner();var rows=rows(5,IMAGE);runner.enqueue(rows.toString());runner.run();
        runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
        var flow=runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst();assertEquals("2",flow.getAttribute("ht.window.count"));
        var output=PushedAlarmCompatibilityTest.JSON.readTree(flow.toByteArray());assertEquals(5,output.size());
        for(var row:output){assertTrue(IMAGE.equals(row.path("pic_base64").asText()));assertEquals(1700000000123L,row.path("event_time").asLong());}
        assertEquals("0",flow.getAttribute("ht.dictionary.batches"));PushedAlarmJdbcTest.assertTwoStorageHops(output);
    }
    @Test void twentyAndHundredRowsKeepPicturesWithoutLookupRequests()throws Exception {
        String image="QUJD".repeat(25_600);
        for(int count:new int[]{20,100}){
            var runner=runner();runner.enqueue(rows(count,image).toString());runner.run();runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);
            var flow=runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst();var result=PushedAlarmCompatibilityTest.JSON.readTree(flow.toByteArray());
            assertEquals(count,result.size());result.forEach(row->assertTrue(image.equals(row.path("pic_base64").asText())));
            assertEquals("0",flow.getAttribute("ht.dictionary.batches"));
        }
    }
    @Test void aRequiredNullInALaterImageWindowRollsBackAllEarlierOutputs()throws Exception {
        var runner=runner();var rows=rows(5,IMAGE);((ObjectNode)rows.get(4)).putNull("eventNo");byte[] bytes=PushedAlarmCompatibilityTest.JSON.writeValueAsBytes(rows);
        runner.enqueue(bytes);runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,0);runner.assertTransferCount(HaitongTransformRecord.FAILURE,1);
        var failed=runner.getFlowFilesForRelationship(HaitongTransformRecord.FAILURE).getFirst();assertEquals("RECORD_INVALID",failed.getAttribute("ht.error.code"));assertTrue(Arrays.equals(bytes,failed.toByteArray()));
    }
    @Test void anIsolatedLargeImageErrorCanReplayAfterItsRuleIsCorrected()throws Exception {
        var runner=runner();var spec=PushedAlarmCompatibilityTest.plan(true).document();spec.withObject("/policies").put("recordErrorMode","ROUTE_INVALID");
        var rows=rows(2,IMAGE);((ObjectNode)rows.get(1)).putNull("eventNo");runner.setProperty(HaitongTransformRecord.PLAN,MappingEngine.compile(spec).document().toString());
        runner.enqueue(rows.toString());runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);runner.assertTransferCount(HaitongTransformRecord.INVALID,1);
        var invalid=runner.getFlowFilesForRelationship(HaitongTransformRecord.INVALID).getFirst();
        assertTrue(IMAGE.equals(PushedAlarmCompatibilityTest.JSON.readTree(invalid.toByteArray()).path("record").path("picBase64").asText()));
        var attributes=new HashMap<>(invalid.getAttributes());attributes.put(MappingErrorRecords.NEXT,"0");
        for(var rule:spec.path("rules"))if(rule.path("target").path("name").asText().equals("event_no"))((ObjectNode)rule.path("target")).put("nullable",true);
        runner.clearTransferState();runner.setProperty(HaitongTransformRecord.PLAN,MappingEngine.compile(spec).document().toString());runner.enqueue(invalid.toByteArray(),attributes);runner.run();
        runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);runner.assertTransferCount(HaitongTransformRecord.INVALID,0);runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
        var output=PushedAlarmCompatibilityTest.JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray());assertEquals(1,output.size());assertTrue(IMAGE.equals(output.path(0).path("pic_base64").asText()));
    }
    @Test void exactEightMiBIsAcceptedAndOneMoreByteKeepsTheOriginal()throws Exception {
        String text="A".repeat((int)RecordBudget.RECORD_BYTES-8);
        var spec=PushedAlarmCompatibilityTest.JSON.readTree("""
            {"version":"1.0","mappings":[{"from":"x","to":"x","targetType":"STRING"}]}
            """);
        for(boolean extra:new boolean[]{false,true}){
            var runner=runner();runner.setProperty(HaitongTransformRecord.PLAN,MappingEngine.compile(spec).document().toString());
            byte[] input=PushedAlarmCompatibilityTest.JSON.writeValueAsBytes(List.of(Map.of("x",extra?text+"A":text)));
            runner.enqueue(input);runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,extra?0:1);runner.assertTransferCount(HaitongTransformRecord.FAILURE,extra?1:0);
            if(extra){var failed=runner.getFlowFilesForRelationship(HaitongTransformRecord.FAILURE).getFirst();assertEquals("BUDGET_EXCEEDED",failed.getAttribute("ht.error.code"));assertTrue(failed.getAttribute("ht.error.message").contains("8388608"));assertTrue(Arrays.equals(input,failed.toByteArray()));}
            else assertTrue(text.equals(PushedAlarmCompatibilityTest.JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray()).path(0).path("x").asText()));
        }
    }
}
