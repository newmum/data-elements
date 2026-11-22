package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.linewell.dataelement.mapping.core.*;
import org.apache.nifi.util.*;
import org.apache.nifi.json.JsonTreeReader;
import org.apache.nifi.json.JsonRecordSetWriter;
import org.apache.nifi.processors.standard.ConvertRecord;
import org.apache.nifi.serialization.record.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HaitongNormalizeJsonRecordsTest {
    static final ObjectMapper JSON=new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    static TestRunner runner(StructuredJsonRecords.Shape shape) {
        var runner=TestRunners.newTestRunner(HaitongNormalizeJsonRecords.class);
        runner.setProperty(HaitongNormalizeJsonRecords.SHAPE,shape.json().toString());return runner;
    }
    static StructuredJsonRecords.Shape shape(String... records)throws Exception {
        var shape=new StructuredJsonRecords.Shape();for(String record:records)shape.accept(StructuredJsonRecords.normalize(JSON.readTree(record)));return shape;
    }
    @Test void nestedPathsArraysAndNullSurviveActualJsonReader()throws Exception {
        String input=""" 
                {"@timestamp":"2026-11-21T12:34:56Z","@metadata":{"beat":"filebeat"},"agent":{"id":"a"},"tags":["one","二"],"empty":null}
                """;
        var runner=runner(shape(input));runner.enqueue(input);runner.run();runner.assertTransferCount(HaitongNormalizeJsonRecords.SUCCESS,1);
        var file=runner.getFlowFilesForRelationship(HaitongNormalizeJsonRecords.SUCCESS).getFirst();var row=JSON.readTree(file.toByteArray()).get(0);
        assertEquals("filebeat",row.path("metadata_beat").asText());assertEquals("a",row.path("agent_id").asText());assertTrue(row.path("empty").isNull());
        assertEquals(JSON.readTree("[\"one\",\"二\"]"),JSON.readTree(row.path("tags").asText()));
        assertEquals(1,readCount(file));
    }
    @Test void observedEmptyAndNonemptyObjectParentsKeepJsonChildrenAndOriginalRecoveryIndexes()throws Exception {
        String empty="{\"@timestamp\":\"2026-11-21T12:34:56Z\",\"params\":{}}";
        String full="{\"@timestamp\":\"2026-11-21T12:34:56Z\",\"params\":{\"content\":\"中文\",\"log_level\":2}}";
        var runner=runner(shape(empty,full));StringBuilder content=new StringBuilder();
        for(int index:List.of(1,3)) {
            ObjectNode envelope=JSON.createObjectNode().put("inputRecordIndex",index).put("planHash","old");
            envelope.set("record",JSON.readTree(full));envelope.putArray("errors");content.append(envelope).append('\n');
        }
        runner.enqueue(content.toString(),Map.of(MappingErrorRecords.ENVELOPE,"1","recovery.ticket","single-replay"));runner.run();
        runner.assertTransferCount(HaitongNormalizeJsonRecords.SUCCESS,1);runner.assertTransferCount(HaitongNormalizeJsonRecords.FAILURE,0);
        var output=runner.getFlowFilesForRelationship(HaitongNormalizeJsonRecords.SUCCESS).getFirst();assertEquals("single-replay",output.getAttribute("recovery.ticket"));
        var rows=JSON.readerFor(JsonNode.class).readValues(output.toByteArray());int count=0;
        while(rows.hasNextValue()) {
            JsonNode row=(JsonNode)rows.nextValue();assertEquals(count++==0?1:3,row.path("inputRecordIndex").intValue());
            assertEquals(JSON.readTree(full).path("params"),JSON.readTree(row.path("record").path("params").asText()));
            assertEquals("中文",row.path("record").path("params_content").asText());assertEquals(2,row.path("record").path("params_log_level").intValue());
        }
        assertEquals(2,count);
    }
    @Test void rootScalarsAndScalarArraysNeverSilentlyProduceZeroRecords()throws Exception {
        for(String input:List.of("\"message\"","42","true","null","[\"one\",\"two\"]","[[1,2],[3]]")) {
            var shape=new StructuredJsonRecords.Shape();JsonNode tree=JSON.readTree(input);
            if(tree.isArray())for(JsonNode child:tree)shape.accept(StructuredJsonRecords.normalize(child));else shape.accept(StructuredJsonRecords.normalize(tree));
            var runner=runner(shape);runner.enqueue(input);runner.run();runner.assertTransferCount(HaitongNormalizeJsonRecords.SUCCESS,1);
            var output=runner.getFlowFilesForRelationship(HaitongNormalizeJsonRecords.SUCCESS).getFirst();int expected=tree.isArray()?tree.size():1;
            assertEquals(expected,readCount(output),input);assertEquals(expected,JSON.readTree(output.toByteArray()).size());
        }
    }
    @Test void flatNumericAndLiteralHeadersRenameSchemaAndRetainAllTwelveRecords()throws Exception {
        var shape=new StructuredJsonRecords.Shape();shape.accept(StructuredJsonRecords.record(Map.of("月份","一月","2021年",3,"2022年",4,"literal.dot",5)));
        var runner=runner(shape);ArrayNode input=JSON.createArrayNode();for(int i=0;i<12;i++)input.addObject().put("月份","月"+i).put("2021年",i).put("2022年",i+1).put("literal.dot",i+2);
        var schema=JSON.createObjectNode().put("type","record").put("name","flat");var fields=schema.putArray("fields");for(String name:List.of("月份","2021年","2022年","literal.dot"))fields.addObject().put("name",name).put("type","string");
        runner.enqueue(input.toString(),Map.of("avro.schema",schema.toString()));runner.run();runner.assertTransferCount(HaitongNormalizeJsonRecords.SUCCESS,1);
        var file=runner.getFlowFilesForRelationship(HaitongNormalizeJsonRecords.SUCCESS).getFirst();var rows=JSON.readTree(file.toByteArray());assertEquals(12,rows.size());
        assertEquals(11,rows.get(11).path("f_2021年").intValue());assertEquals(13,rows.get(11).path("literal_dot").intValue());
        assertEquals("f_2021年",JSON.readTree(file.getAttribute("avro.schema")).path("fields").get(1).path("name").asText());
    }
    @Test void oldInvalidEnvelopesKeepIndexesAndRecoveryGateAndCanBeReadAgain()throws Exception {
        String raw="{\"@timestamp\":\"2026-11-21T12:34:56Z\",\"agent\":{\"id\":\"a\"}}";
        var runner=runner(shape(raw));StringBuilder content=new StringBuilder();for(int i=0;i<4;i++) {
            var envelope=JSON.createObjectNode().put("inputRecordIndex",i).put("planHash","old");envelope.set("record",JSON.readTree(raw));envelope.putArray("errors");content.append(envelope).append('\n');
        }
        runner.enqueue(content.toString(),Map.of(MappingErrorRecords.ENVELOPE,"1",MappingErrorRecords.SCHEMA,"[]","recovery.ticket","one-time"));runner.run();
        runner.assertTransferCount(HaitongNormalizeJsonRecords.SUCCESS,1);var output=runner.getFlowFilesForRelationship(HaitongNormalizeJsonRecords.SUCCESS).getFirst();
        assertEquals("one-time",output.getAttribute("recovery.ticket"));assertNull(output.getAttribute(MappingErrorRecords.SCHEMA));
        var values=JSON.readerFor(JsonNode.class).readValues(output.toByteArray());int count=0;while(values.hasNextValue()) {JsonNode row=(JsonNode)values.nextValue();assertEquals(count++,row.path("inputRecordIndex").intValue());assertEquals("a",row.path("record").path("agent_id").asText());}assertEquals(4,count);
    }
    @Test void lateCollisionRemovesEveryCandidateWindowAndPreservesOriginal()throws Exception {
        var runner=runner(shape("{\"agent\":{\"id\":\"a\"}}"));ArrayNode input=JSON.createArrayNode();for(int i=0;i<300;i++)input.addObject().putObject("agent").put("id","a");input.addObject().put("agent_id","literal").putObject("agent").put("id","nested");
        String original=input.toString();runner.enqueue(original);runner.run();runner.assertTransferCount(HaitongNormalizeJsonRecords.SUCCESS,0);runner.assertTransferCount(HaitongNormalizeJsonRecords.FAILURE,1);
        var failure=runner.getFlowFilesForRelationship(HaitongNormalizeJsonRecords.FAILURE).getFirst();assertEquals(original,new String(failure.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));assertEquals("AMBIGUOUS_SOURCE_PATH",failure.getAttribute("ht.error.code"));
    }
    @Test void nestedNifiRecordsAndArraysSerializeActualValuesWhileBinaryStaysBase64()throws Exception {
        var schema=new org.apache.nifi.serialization.SimpleRecordSchema(List.of(new RecordField("id",RecordFieldType.STRING.getDataType())));
        Object input=NifiTemporalValues.inputValue(new Object[]{new MapRecord(schema,Map.of("id","a")),Map.of("items",new Object[]{"x",2})});
        assertEquals(JSON.readTree("[{\"id\":\"a\"},{\"items\":[\"x\",2]}]"),JSON.readTree(StructuredJsonRecords.jsonText(input)));
        assertEquals("AQI=",ValueTypes.cast(new byte[]{1,2},"STRING",0,0,java.math.RoundingMode.UNNECESSARY));
        assertEquals("AQI=",ValueTypes.cast(NifiTemporalValues.inputValue(new Byte[]{1,2}),"STRING",0,0,java.math.RoundingMode.UNNECESSARY));
    }
    @Test void actualRecordReaderArrayCarrierBecomesJsonTextInTransformRecord()throws Exception {
        var runner=TestRunners.newTestRunner(HaitongTransformRecord.class);var reader=new JsonTreeReader();runner.addControllerService("reader",reader);runner.setProperty(reader,"Schema Access Strategy","infer-schema");runner.enableControllerService(reader);
        var writer=new JsonRecordSetWriter();runner.addControllerService("writer",writer);runner.setProperty(writer,"Schema Access Strategy","inherit-record-schema");runner.enableControllerService(writer);
        runner.setProperty(HaitongTransformRecord.READER,"reader");runner.setProperty(HaitongTransformRecord.WRITER,"writer");
        var plan=MappingEngine.compile(JSON.readTree("{\"version\":\"1.0\",\"mappings\":[{\"from\":\"x\",\"to\":\"y\",\"targetType\":\"STRING\"}]}"));runner.setProperty(HaitongTransformRecord.PLAN,plan.document().toString());
        runner.enqueue("[{\"x\":[\"a\",\"中文\",null]}]");runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
        var row=JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray()).get(0);
        assertEquals(JSON.readTree("[\"a\",\"中文\",null]"),JSON.readTree(row.path("y").asText()));
    }
    @Test void mixedTextTopicAcceptsRegisteredRawTextWithoutMaskingJsonPathFailures()throws Exception {
        var shape=shape("{\"agent\":{\"id\":\"a\"}}");shape.accept(StructuredJsonRecords.text("raw text"));
        var runner=runner(shape);runner.enqueue("raw text");runner.run();runner.assertTransferCount(HaitongNormalizeJsonRecords.SUCCESS,1);
        assertEquals("raw text",JSON.readTree(runner.getFlowFilesForRelationship(HaitongNormalizeJsonRecords.SUCCESS).getFirst().toByteArray()).get(0).path("value").asText());
        runner.clearTransferState();runner.enqueue("{\"agent_id\":\"a\"}");runner.run();runner.assertTransferCount(HaitongNormalizeJsonRecords.FAILURE,1);
        assertEquals("SOURCE_PATH_CHANGED",runner.getFlowFilesForRelationship(HaitongNormalizeJsonRecords.FAILURE).getFirst().getAttribute("ht.error.code"));
    }
    static int readCount(MockFlowFile file)throws Exception {
        var runner=TestRunners.newTestRunner(ConvertRecord.class);var reader=new JsonTreeReader();runner.addControllerService("reader",reader);runner.setProperty(reader,"Schema Access Strategy","infer-schema");runner.enableControllerService(reader);
        var writer=new JsonRecordSetWriter();runner.addControllerService("writer",writer);runner.setProperty(writer,"Schema Write Strategy","no-schema");runner.setProperty(writer,"Schema Access Strategy","inherit-record-schema");runner.enableControllerService(writer);
        runner.setProperty("Record Reader","reader");runner.setProperty("Record Writer","writer");runner.enqueue(file.toByteArray());runner.run();runner.assertTransferCount("success",1);
        return JSON.readTree(runner.getFlowFilesForRelationship("success").getFirst().toByteArray()).size();
    }
}
