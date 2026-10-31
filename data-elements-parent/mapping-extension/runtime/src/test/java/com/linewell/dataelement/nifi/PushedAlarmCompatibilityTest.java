package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.linewell.dataelement.mapping.core.*;
import org.apache.nifi.json.JsonTreeReader;
import org.apache.nifi.util.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The production sample's field names/types, with synthetic values for ordinary regression. */
class PushedAlarmCompatibilityTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String[] FIELDS={"event_no","event_type","event_time","camera_index_code","event_address","camera_name","pic_base64","longitude","latitude","event_name","rect_x","rect_y","rect_h","rect_w"};
    static final String[] INPUT={"eventNo","eventType","eventTime","cameraIndexCode","eventAddress","cameraName","picBase64","longitude","latitude","eventName","rectX","rectY","rectH","rectW"};

    static MappingEngine.Plan plan(boolean translateDate) {
        var spec=JSON.createObjectNode().put("schemaVersion","2.0");
        spec.putObject("policies").put("onMissingSource","FAIL").put("recordErrorMode","FAIL_FLOWFILE").put("zoneId","Asia/Shanghai");
        var contract=spec.putObject("schemaContract");var source=contract.putArray("source");var rules=spec.putArray("rules");
        for(String name:FIELDS){
            source.addObject().put("name",name).put("logicalType","STRING").put("nullable",true);
            var rule=rules.addObject().put("ruleId",name);rule.putObject("input").put("kind","FIELD").put("name",name);
            rule.putObject("target").put("name",name).put("logicalType",name.equals("event_time")&&translateDate?"DATETIME":"STRING").put("nullable",!Set.of("event_no","event_time","camera_index_code","camera_name","pic_base64","event_type","event_name").contains(name));
            var steps=rule.putArray("steps");
            if(name.equals("event_time")&&translateDate)steps.addObject().put("operator","DATE_PARSE").putObject("args").put("pattern","AUTO").put("logicalType","DATETIME");
        }
        return MappingEngine.compile(spec);
    }
    static TestRunner runner(boolean translateDate)throws Exception {
        var runner=TestRunners.newTestRunner(HaitongTransformRecord.class);
        var reader=new JsonTreeReader();runner.addControllerService("reader",reader);
        runner.setProperty(reader,"Schema Access Strategy","infer-schema");runner.enableControllerService(reader);
        var writer=new HaitongTransformRecordTest.Writer();runner.addControllerService("writer",writer);runner.enableControllerService(writer);
        runner.setProperty(HaitongTransformRecord.READER,"reader");runner.setProperty(HaitongTransformRecord.WRITER,"writer");
        runner.setProperty(HaitongTransformRecord.PLAN,plan(translateDate).document().toString());return runner;
    }
    static ArrayNode rows(int count){
        var rows=JSON.createArrayNode();
        for(int i=0;i<count;i++){
            var row=rows.addObject();for(String name:INPUT)row.put(name,"validation");
            row.put("eventNo","validation-"+i).put("eventTime","1700000000123").put("picBase64","A".repeat(20_000)).put("eventAddress","").put("cameraName","验证通道");
        }
        return rows;
    }
    @Test void realNifiReaderConvertsAllFourteenFieldsForTwentyAndHundredRows()throws Exception {
        for(int count:new int[]{20,100})for(boolean translateDate:new boolean[]{false,true})for(boolean jsonl:new boolean[]{false,true}){
            var runner=runner(translateDate);var input=rows(count);
            String text=jsonl?String.join("\n",java.util.stream.StreamSupport.stream(input.spliterator(),false).map(JsonNode::toString).toList()):input.toString();
            runner.enqueue(text);runner.run();runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
            var output=runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst();var result=JSON.readTree(output.toByteArray());
            assertEquals(count,result.size());assertEquals("validation-0",result.path(0).path("event_no").asText());
            assertEquals("验证通道",result.path(count-1).path("camera_name").asText());
            assertEquals(20_000,result.path(0).path("pic_base64").asText().length());assertEquals(14,result.path(0).size());
            if(translateDate)assertEquals(1700000000123L,result.path(0).path("event_time").asLong());
            else assertEquals("1700000000123",result.path(0).path("event_time").asText());
            assertEquals("0",output.getAttribute("ht.dictionary.batches"));
        }
    }
    @Test void suppliedProductionSampleKeepsValuesAndConvertsItsEpochWithoutLoggingPayload()throws Exception {
        String path=System.getProperty("validation.alarm.input");Assumptions.assumeTrue(path!=null);
        byte[] bytes=Files.readAllBytes(Path.of(path));var original=JSON.readTree(bytes);var runner=runner(true);
        runner.enqueue(bytes);runner.run();runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
        var result=JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray());
        assertEquals(original.size(),result.size());
        for(int row=0;row<result.size();row++)for(int field=0;field<FIELDS.length;field++){
            if(FIELDS[field].equals("event_time"))assertTrue(Long.parseLong(original.path(row).path(INPUT[field]).asText())==result.path(row).path(FIELDS[field]).asLong(),"时间戳转换必须保持时间点");
            else assertTrue(original.path(row).path(INPUT[field]).equals(result.path(row).path(FIELDS[field])),"字段命名转换必须保留原值："+FIELDS[field]);
        }
        PushedAlarmJdbcTest.assertTwoStorageHops(result);
    }
    @Test void aLateRequiredNullAfterTheImageWindowKeepsTheWholeOriginal()throws Exception {
        var runner=runner(true);var rows=rows(600);((ObjectNode)rows.get(599)).putNull("eventNo");
        byte[] input=JSON.writeValueAsBytes(rows);assertTrue(input.length>MappingEngine.WINDOW_BYTES);
        runner.enqueue(input);runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,0);runner.assertTransferCount(HaitongTransformRecord.FAILURE,1);
        var failed=runner.getFlowFilesForRelationship(HaitongTransformRecord.FAILURE).getFirst();
        assertTrue(Arrays.equals(input,failed.toByteArray()),"后续窗口失败时须保留整份原始图片数据");
        assertEquals("RECORD_INVALID",failed.getAttribute("ht.error.code"));
    }
    @Test void anAnyTargetKeepsTheActualCamelCaseSourceSchemaType()throws Exception {
        var runner=runner(false);var writer=new SchemaWriter();runner.addControllerService("schema-writer",writer);runner.enableControllerService(writer);
        runner.setProperty(HaitongTransformRecord.WRITER,"schema-writer");
        var spec=JSON.readTree("""
            {"schemaVersion":"2.0","policies":{"onMissingSource":"FAIL"},"rules":[
             {"ruleId":"event","input":{"kind":"FIELD","name":"event_no"},"target":{"name":"event_no","logicalType":"ANY"},"steps":[]}]}
            """);
        runner.setProperty(HaitongTransformRecord.PLAN,MappingEngine.compile(spec).document().toString());
        runner.enqueue("[{\"eventNo\":7}]");runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
        var type=writer.schema.getField("event_no").orElseThrow().getDataType().getFieldType();
        assertTrue(Set.of(org.apache.nifi.serialization.record.RecordFieldType.INT,org.apache.nifi.serialization.record.RecordFieldType.LONG).contains(type),"别名不应使ANY字段退化成字符串");
    }
    public static class SchemaWriter extends HaitongTransformRecordTest.Writer {
        org.apache.nifi.serialization.record.RecordSchema schema;
        @Override public org.apache.nifi.serialization.RecordSetWriter createWriter(org.apache.nifi.logging.ComponentLog log,org.apache.nifi.serialization.record.RecordSchema schema,java.io.OutputStream out,Map<String,String> attributes)throws java.io.IOException {
            this.schema=schema;return super.createWriter(log,schema,out,attributes);
        }
    }
}
