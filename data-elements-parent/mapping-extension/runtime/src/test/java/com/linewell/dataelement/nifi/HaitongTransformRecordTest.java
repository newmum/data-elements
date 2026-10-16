package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.mapping.core.MappingEngine;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.serialization.*;
import org.apache.nifi.serialization.record.*;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.util.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HaitongTransformRecordTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    @Test void allWindowsCommitOnceAndCarryWriterSchema()throws Exception{
        var runner=runner("NULL","FAIL_FLOWFILE");List<Map<String,Object>>rows=new ArrayList<>();for(int i=0;i<600;i++)rows.add(Map.of("x","v"+i));runner.enqueue(JSON.writeValueAsBytes(rows));runner.run();
        runner.assertTransferCount(HaitongTransformRecord.SUCCESS,2);int total=0;
        for(var output:runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS)){int count=JSON.readTree(output.toByteArray()).size();assertEquals(count,Integer.parseInt(output.getAttribute("record.count")));assertEquals("test-schema",output.getAttribute("avro.schema"));total+=count;}
        assertEquals(600,total);
    }
    @Test void failureInLaterWindowPublishesNoEarlierSuccessAndKeepsOriginal()throws Exception{
        var runner=runner("FAIL","FAIL_FLOWFILE");List<Map<String,Object>>rows=new ArrayList<>();for(int i=0;i<513;i++)rows.add(Map.of("x","valid"));rows.add(Map.of());byte[]input=JSON.writeValueAsBytes(rows);runner.enqueue(input);runner.run();
        runner.assertTransferCount(HaitongTransformRecord.SUCCESS,0);runner.assertTransferCount(HaitongTransformRecord.FAILURE,1);assertArrayEquals(input,runner.getFlowFilesForRelationship(HaitongTransformRecord.FAILURE).getFirst().toByteArray());
    }
    @Test void isolatedErrorsAndSuccessBelongToSameSession()throws Exception{
        var runner=runner("FAIL","ROUTE_INVALID");runner.enqueue("[{\"x\":\"valid\"},{}]");runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);runner.assertTransferCount(HaitongTransformRecord.INVALID,1);
    }
    @Test void chainedNodeAcceptsUpstreamPlanAndPersistsReplayIdentity()throws Exception{
        var runner=runner("NULL","FAIL_FLOWFILE");runner.enqueue("[{\"x\":\"value\"}]",Map.of("ht.plan.hash","upstream-plan","ht.plan.node.id","upstream-node","ht.input.identity","stable-input","ht.execution.clock","0"));runner.run();
        runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);var output=runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst();assertEquals("stable-input",output.getAttribute("ht.input.identity"));assertEquals("0",output.getAttribute("ht.execution.clock"));assertNotNull(output.getAttribute("ht.plan.hash."+runner.getProcessor().getIdentifier()));
    }
    @Test void sameTextColumnUsesPerValueDateDetectionEvenWithSourceSchema()throws Exception{
        var runner=runner("NULL","FAIL_FLOWFILE");
        runner.setProperty(HaitongTransformRecord.TYPED_READER,"reader");
        var spec=JSON.readTree("""
          {"schemaVersion":"2.0","policies":{"zoneId":"Asia/Shanghai"},"rules":[{"ruleId":"time","target":{"name":"y","logicalType":"DATETIME"},"input":{"kind":"FIELD","name":"x"},"steps":[{"operator":"DATE_PARSE","args":{"pattern":"AUTO","logicalType":"DATETIME"}}]}]}
          """);
        var plan=MappingEngine.compile(spec);runner.setProperty(HaitongTransformRecord.PLAN,plan.document().toString());runner.setProperty(HaitongTransformRecord.HASH,plan.hash());
        var values=List.of("2024/05/10 14:27:49","2024-05-10 14:27:49","20240510142749","2024-05-10T06:27:49Z","10-MAY-24 02.27.49 PM");
        var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<100;i++)rows.add(Map.of("x",values.get(i%values.size())));
        runner.enqueue(JSON.writeValueAsBytes(rows),Map.of("avro.schema","source-string-schema"));runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
        var output=JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray());assertEquals(100,output.size());
        for(var row:output)assertEquals(java.time.Instant.parse("2024-05-10T06:27:49Z").toEpochMilli(),row.path("y").asLong());
    }
    @Test void historicalAndNumericDatesAreDetectedPerValueAndBadCalendarDatesAreIsolated() throws Exception {
        var spec=JSON.readTree("""
          {"schemaVersion":"2.0","policies":{"zoneId":"Asia/Shanghai","recordErrorMode":"ROUTE_INVALID"},"rules":[{"ruleId":"date","target":{"name":"y","logicalType":"DATETIME"},"input":{"kind":"FIELD","name":"x"},"steps":[{"operator":"DATE_PARSE","args":{"pattern":"AUTO","logicalType":"DATETIME"}}]}]}
          """);
        var plan=MappingEngine.compile(spec);
        var values=List.of("1947/04/15","20260701112021",20260701112021L,"20250219",20250219);
        for(int count:List.of(20,100)) {
            var runner=runner("NULL","ROUTE_INVALID");
            runner.setProperty(HaitongTransformRecord.PLAN,plan.document().toString());runner.setProperty(HaitongTransformRecord.HASH,plan.hash());
            var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<count;i++)rows.add(Map.of("x",values.get(i%values.size())));
            rows.add(Map.of("x","20010236"));runner.enqueue(JSON.writeValueAsBytes(rows));runner.run();
            runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);runner.assertTransferCount(HaitongTransformRecord.INVALID,1);runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);
            var output=JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray());assertEquals(count,output.size());
            var invalid=JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.INVALID).getFirst().toByteArray());assertEquals("DATE_INVALID",invalid.path("errors").get(0).path("code").asText());
        }
    }
    @Test void invalidSelfLoopUnwrapsOriginalRowAndKeepsSparseIndexWithoutNesting() throws Exception {
        var runner=runner("FAIL","ROUTE_INVALID");runner.enqueue("[{\"x\":\"valid\"},{}]");runner.run();
        var invalid=runner.getFlowFilesForRelationship(HaitongTransformRecord.INVALID).getFirst();
        assertEquals("1",invalid.getAttribute(MappingErrorRecords.ENVELOPE));
        assertEquals("1",invalid.getAttribute(MappingErrorRecords.ATTEMPT));
        assertTrue(invalid.isPenalized());
        var attributes=new HashMap<>(invalid.getAttributes());attributes.put(MappingErrorRecords.NEXT,"0");
        runner.clearTransferState();runner.enqueue(invalid.toByteArray(),attributes);runner.run();
        runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);runner.assertTransferCount(HaitongTransformRecord.INVALID,1);
        var retried=runner.getFlowFilesForRelationship(HaitongTransformRecord.INVALID).getFirst();
        var envelope=JSON.readTree(retried.toByteArray());assertEquals(1,envelope.path("inputRecordIndex").asInt());
        assertTrue(envelope.path("record").isObject());assertEquals(0,envelope.path("record").size());
        assertFalse(envelope.path("record").has("record"));assertEquals("2",retried.getAttribute(MappingErrorRecords.ATTEMPT));
    }
    @Test void errorLoopDelayKeepsTheExactEnvelopeUntilRetryTime() throws Exception {
        var runner=runner("FAIL","ROUTE_INVALID");runner.enqueue("[{}]");runner.run();
        var invalid=runner.getFlowFilesForRelationship(HaitongTransformRecord.INVALID).getFirst();
        runner.clearTransferState();runner.enqueue(invalid.toByteArray(),invalid.getAttributes());runner.run();
        runner.assertTransferCount(HaitongTransformRecord.INVALID,1);runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);
        assertArrayEquals(invalid.toByteArray(),runner.getFlowFilesForRelationship(HaitongTransformRecord.INVALID).getFirst().toByteArray());
    }
    @Test void legacyEnvelopeUsesOriginalPayloadInsteadOfItsErrorWrapper() throws Exception {
        var runner=runner("FAIL","ROUTE_INVALID");var plan=MappingEngine.compile(JSON.readTree(runner.getProcessContext().getProperty(HaitongTransformRecord.PLAN).getValue()));
        var envelope=JSON.createObjectNode().put("planHash",plan.hash()).put("inputRecordIndex",56);envelope.putArray("errors").addObject().put("code","UNSUPPORTED_TYPE");envelope.putObject("record").put("x","restored");
        runner.enqueue(envelope.toString(),Map.of("ht.plan.hash",plan.hash(),"ht.plan.node.id",runner.getProcessor().getIdentifier(),"mime.type","application/x-ndjson"));runner.run();
        runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);
        var output=runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst();assertEquals("restored",JSON.readTree(output.toByteArray()).path(0).path("y").asText());
        assertNull(output.getAttribute(MappingErrorRecords.ENVELOPE));assertNull(output.getAttribute(MappingErrorRecords.NEXT));
    }
    @Test void splitChildrenWithSameParentIdentityHaveDifferentUuidsAndRetryIsStable() throws Exception {
        TestRunner runner=runner("NULL","FAIL_FLOWFILE");
        var spec=JSON.createObjectNode().put("version","1.0");spec.putArray("mappings").addObject().put("to","/ODS_UUID").put("expression","UUID()");
        var plan=MappingEngine.compile(spec);runner.setProperty(HaitongTransformRecord.PLAN,plan.document().toString());runner.setProperty(HaitongTransformRecord.HASH,plan.hash());runner.setProperty(HaitongTransformRecord.CONTEXT_NODE,"same-node");
        for(int i=0;i<2;i++)runner.enqueue("[{\"x\":\"same\"}]",Map.of("ht.input.identity","one-parent","ht.execution.clock","0"));
        runner.run(2);runner.assertTransferCount(HaitongTransformRecord.SUCCESS,2);
        var outputs=runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS);
        String a=JSON.readTree(outputs.get(0).toByteArray()).path(0).path("ODS_UUID").asText(),b=JSON.readTree(outputs.get(1).toByteArray()).path(0).path("ODS_UUID").asText();assertNotEquals(a,b);
        String identity=outputs.get(0).getAttribute("ht.mapping.input.identity.same-node");assertNotNull(identity);
        runner.clearTransferState();runner.enqueue("[{\"x\":\"same\"}]",Map.of("ht.input.identity","one-parent","ht.execution.clock","0","ht.mapping.input.identity.same-node",identity));runner.run();
        assertEquals(a,JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray()).path(0).path("ODS_UUID").asText());
    }
    private TestRunner runner(String missing,String mode)throws Exception{
        TestRunner runner=TestRunners.newTestRunner(HaitongTransformRecord.class);runner.addControllerService("reader",new Reader());runner.enableControllerService(runner.getControllerService("reader"));runner.addControllerService("writer",new Writer());runner.enableControllerService(runner.getControllerService("writer"));runner.setProperty(HaitongTransformRecord.READER,"reader");runner.setProperty(HaitongTransformRecord.WRITER,"writer");
        var spec=JSON.createObjectNode().put("version","1.0").put("onMissingSource",missing).put("recordErrorMode",mode);spec.putArray("mappings").addObject().put("from","/x").put("to","/y");var plan=MappingEngine.compile(spec);runner.setProperty(HaitongTransformRecord.PLAN,plan.document().toString());runner.setProperty(HaitongTransformRecord.HASH,plan.hash());runner.assertValid();return runner;
    }
    @Test void decimalCoordinatesAndMixedScalesSurviveEveryWindowWithoutImplicitRounding()throws Exception {
        var values=List.of("119.379023855114","25.7223849760167","-119.379023855114","0.000000000000000000123456789");
        var exact=new ObjectMapper().enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        var plan=MappingEngine.compile(JSON.readTree("""
          {"version":"1.0","recordErrorMode":"ROUTE_INVALID","mappings":[{"from":"x","to":"y","targetType":"DECIMAL"}]}
          """));
        for(int count:List.of(20,100,600)) {
            var runner=runner("NULL","ROUTE_INVALID");runner.setProperty(HaitongTransformRecord.PLAN,plan.document().toString());runner.setProperty(HaitongTransformRecord.HASH,plan.hash());
            List<Map<String,Object>>rows=new ArrayList<>();for(int i=0;i<count;i++)rows.add(Map.of("x",values.get(i%values.size())));
            runner.enqueue(JSON.writeValueAsBytes(rows));runner.run();runner.assertTransferCount(HaitongTransformRecord.INVALID,0);runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);
            int index=0;for(var output:runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS))for(var row:exact.readTree(output.toByteArray())) {
                assertEquals(0,new java.math.BigDecimal(values.get(index++%values.size())).compareTo(row.path("y").decimalValue()));
            }assertEquals(count,index);
        }
    }
    public static class Reader extends AbstractControllerService implements RecordReaderFactory {
        public RecordReader createRecordReader(Map<String,String>a,InputStream in,long length,ComponentLog log)throws IOException,MalformedRecordException{
            List<Map<String,Object>>rows=JSON.readValue(in,List.class);var schema=new SimpleRecordSchema(List.of(new RecordField("x",RecordFieldType.STRING.getDataType())));int[]index={0};RecordReader reader=mock(RecordReader.class);when(reader.getSchema()).thenReturn(schema);when(reader.nextRecord()).thenAnswer(i->index[0]<rows.size()?new MapRecord(schema,rows.get(index[0]++)):null);return reader;
        }
    }
    public static class Writer extends AbstractControllerService implements RecordSetWriterFactory {
        public RecordSchema getSchema(Map<String,String>a,RecordSchema schema){return schema;}
        public RecordSetWriter createWriter(ComponentLog log,RecordSchema schema,OutputStream out,Map<String,String>a)throws IOException{
            List<Map<String,Object>>rows=new ArrayList<>();RecordSetWriter writer=mock(RecordSetWriter.class);when(writer.write(any(Record.class))).thenAnswer(i->{rows.add(i.<Record>getArgument(0).toMap());return WriteResult.of(1,Map.of());});when(writer.finishRecordSet()).thenAnswer(i->{JSON.writeValue(out,rows);return WriteResult.of(rows.size(),Map.of("avro.schema","test-schema"));});when(writer.getMimeType()).thenReturn("application/json");return writer;
        }
    }
}
