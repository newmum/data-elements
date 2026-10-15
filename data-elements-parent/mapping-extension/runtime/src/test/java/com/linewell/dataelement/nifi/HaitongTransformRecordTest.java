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
        runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);var output=runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst();assertEquals("600",output.getAttribute("record.count"));assertEquals("test-schema",output.getAttribute("avro.schema"));assertEquals(600,JSON.readTree(output.toByteArray()).size());
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
