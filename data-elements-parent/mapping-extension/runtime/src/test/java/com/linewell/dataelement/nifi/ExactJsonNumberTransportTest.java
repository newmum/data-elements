package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.*;
import com.linewell.dataelement.mapping.core.MappingEngine;
import org.apache.nifi.json.*;
import org.apache.nifi.util.*;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.serialization.*;
import org.apache.nifi.serialization.record.Record;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExactJsonNumberTransportTest {
    private static final ObjectMapper JSON=new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final String[] NUMBERS={"12.1234567890123456","123.1234567890123456",
            "99999999999999999999999999999999999999","9007199254740993","0.00000000000000000000000000000000000001","null"};

    @Test void actualJsonReaderAndGovPreserveExactNumberAndStringTargetsAcrossWindows() throws Exception {
        for(int count:List.of(20,100,600)) {
            var spec=JSON.readTree("""
                {"version":"1.0","mappings":[{"from":"/n","to":"/number","targetType":"NUMBER"},
                {"from":"/n","to":"/text","targetType":"STRING"}]}
                """);
            var runner=runner(spec);runner.setProperty(HaitongTransformRecord.EXACT_JSON_NUMBERS,"true");
            runner.enqueue(input(count));runner.run();
            noFailure(runner);runner.assertTransferCount(HaitongTransformRecord.INVALID,0);
            int index=0;
            for(var output:runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS))for(var row:JSON.readTree(output.toByteArray())){
                String expected=NUMBERS[index++%NUMBERS.length];
                if(expected.equals("null")){assertTrue(row.path("number").isNull());assertTrue(row.path("text").isNull());}
                else {
                    assertTrue(row.path("number").isNumber());assertTrue(row.path("text").isTextual());
                    assertEquals(0,new BigDecimal(expected).compareTo(row.path("number").decimalValue()));
                    assertEquals(0,new BigDecimal(expected).compareTo(new BigDecimal(row.path("text").asText())));
                }
                assertEquals("0",output.getAttribute("ht.dictionary.batches"));
            }
            assertEquals(count,index);runner.shutdown();
        }
    }

    @Test void registeredDecimalContractUsesExactJsonCarrierAndRetainsTargetPrecisionChecks() throws Exception {
        var spec=JSON.readTree("""
            {"schemaVersion":"2.0","policies":{"recordErrorMode":"ROUTE_INVALID"},
            "schemaContract":{"source":[{"name":"n","logicalType":"DECIMAL","nullable":true}],"allowAdditionalSourceFields":true},
            "rules":[{"ruleId":"exact","target":{"name":"number","logicalType":"DECIMAL","precision":19,"scale":16},
            "input":{"kind":"FIELD","name":"n"}}]}
            """);
        var runner=runner(spec);runner.setProperty(HaitongTransformRecord.EXACT_JSON_NUMBERS,"true");
        runner.enqueue("[{\"n\":12.1234567890123456},{\"n\":123.1234567890123456},{\"n\":1234.1234567890123456}]");runner.run();
        noFailure(runner);runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
        runner.assertTransferCount(HaitongTransformRecord.INVALID,1);
        var rows=JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray());assertEquals(2,rows.size());
        assertEquals(0,new BigDecimal(NUMBERS[0]).compareTo(rows.get(0).path("number").decimalValue()));
        assertEquals(0,new BigDecimal(NUMBERS[1]).compareTo(rows.get(1).path("number").decimalValue()));
        runner.shutdown();
    }

    @Test void streamingNumericOverlayKeepsTextBooleansNullDatesAndBinaryCarriersUnchanged() throws Exception {
        var date=java.sql.Timestamp.from(java.time.Instant.parse("2024-05-10T06:27:49Z"));
        byte[] bytes={0,1,-1};Byte[] boxed={0,1,-1};
        var nested=new LinkedHashMap<String,Object>();nested.put("n",12.123456789012346d);nested.put("text","00123");
        var row=new LinkedHashMap<String,Object>();row.put("n",123.12345678901235d);row.put("nested",nested);
        row.put("list",new Object[]{12.123456789012346d,null,true});row.put("date",date);row.put("bytes",bytes);row.put("boxed",boxed);row.put("flag",true);row.put("empty",null);
        String wire="{\"n\":123.1234567890123456,\"nested\":{\"n\":12.1234567890123456,\"text\":\"00123\"},\"list\":[12.1234567890123456,null,true],\"date\":1715322469000,\"bytes\":[0,1,255],\"boxed\":[0,1,255],\"flag\":true,\"empty\":null}";
        for(String format:List.of(wire,"["+wire+"]",wire+"\n"+wire)) {
            try(var overlay=new ExactJsonNumbers(new ByteArrayInputStream(format.getBytes(StandardCharsets.UTF_8)))){
                overlay.restore(row);if(format.contains("\n"))overlay.restore(row);overlay.requireEnd();
            }
            assertEquals(new BigDecimal(NUMBERS[1]),row.get("n"));assertEquals(new BigDecimal(NUMBERS[0]),nested.get("n"));
            assertEquals("00123",nested.get("text"));assertSame(date,row.get("date"));assertSame(bytes,row.get("bytes"));assertSame(boxed,row.get("boxed"));
            assertEquals(true,row.get("flag"));assertNull(row.get("empty"));assertEquals(8,row.size());
            var list=(Object[])row.get("list");assertEquals(new BigDecimal(NUMBERS[0]),list[0]);assertNull(list[1]);assertEquals(true,list[2]);
        }
    }

    @Test void aReaderThatDropsAnOriginalRecordRollsBackAllPreviouslyWrittenWindows() throws Exception {
        var spec=JSON.readTree("{\"version\":\"1.0\",\"mappings\":[{\"from\":\"/n\",\"to\":\"/number\",\"targetType\":\"NUMBER\"}]}");
        var runner=runner(spec);
        var reader=runner.getControllerService("reader",RecordReaderFactory.class);
        var limited=new LimitedReader(reader);runner.addControllerService("limited",limited);runner.enableControllerService(limited);
        runner.setProperty(HaitongTransformRecord.READER,"limited");runner.setProperty(HaitongTransformRecord.EXACT_JSON_NUMBERS,"true");
        byte[] original=input(514).getBytes(StandardCharsets.UTF_8);runner.enqueue(original);runner.run();
        runner.assertTransferCount(HaitongTransformRecord.SUCCESS,0);runner.assertTransferCount(HaitongTransformRecord.INVALID,0);runner.assertTransferCount(HaitongTransformRecord.FAILURE,1);
        var failure=runner.getFlowFilesForRelationship(HaitongTransformRecord.FAILURE).getFirst();
        assertEquals("JSON_NUMBER_CODEC_MISMATCH",failure.getAttribute("ht.error.code"));assertArrayEquals(original,failure.toByteArray());runner.shutdown();
    }

    @Test void numericJdbcWireAliasesRestoreNamesAfterExactValuesAndKeepReplayIndices() throws Exception {
        var spec=JSON.readTree("{\"version\":\"1.0\",\"mappings\":[{\"from\":\"/1231313\",\"to\":\"/number\",\"targetType\":\"NUMBER\"}]}");
        for(int count:List.of(20,100)) {
            var runner=runner(spec);runner.setProperty(HaitongTransformRecord.EXACT_JSON_NUMBERS,"true");
            runner.setProperty(HaitongTransformRecord.JDBC_ALIASES,"{\"1231313\":\"_ht_1234567890abcdef\"}");
            runner.enqueue(input(count).replace("\"n\"","\"_ht_1234567890abcdef\""));runner.run();noFailure(runner);
            var values=JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray());assertEquals(count,values.size());
            for(int i=0;i<count;i++){String expected=NUMBERS[i%NUMBERS.length];if(expected.equals("null"))assertTrue(values.get(i).path("number").isNull());else assertEquals(0,new BigDecimal(expected).compareTo(values.get(i).path("number").decimalValue()));}
            runner.shutdown();
        }
        var replay=JSON.readTree("""
            {"schemaVersion":"2.0","schemaContract":{"source":[{"name":"n","logicalType":"DECIMAL","nullable":true}]},
            "rules":[{"ruleId":"exact","target":{"name":"number","logicalType":"DECIMAL"},"input":{"kind":"FIELD","name":"n"}}]}
            """);
        var runner=runner(replay);runner.setProperty(HaitongTransformRecord.EXACT_JSON_NUMBERS,"true");
        var envelope=JSON.createObjectNode().put("inputRecordIndex",77);envelope.putArray("errors").addObject().put("code","TYPE_MISMATCH");envelope.putObject("record").put("n",new BigDecimal(NUMBERS[0]));
        var schema=new SimpleRecordSchema(List.of(new org.apache.nifi.serialization.record.RecordField("n",org.apache.nifi.serialization.record.RecordFieldType.DOUBLE.getDataType())));
        runner.enqueue(envelope.toString(),Map.of(MappingErrorRecords.ENVELOPE,"1",MappingErrorRecords.SCHEMA,MappingErrorRecords.schemaText(schema)));runner.run();noFailure(runner);
        assertEquals(0,new BigDecimal(NUMBERS[0]).compareTo(JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray()).path(0).path("number").decimalValue()));runner.shutdown();
    }

    public static class LimitedReader extends AbstractControllerService implements RecordReaderFactory {
        private final RecordReaderFactory delegate;
        LimitedReader(RecordReaderFactory delegate){this.delegate=delegate;}
        @Override public RecordReader createRecordReader(Map<String,String> attributes,InputStream input,long size,ComponentLog logger)throws IOException,MalformedRecordException,org.apache.nifi.schema.access.SchemaNotFoundException {
            var reader=delegate.createRecordReader(attributes,input,size,logger);
            return new RecordReader(){int count;
                public org.apache.nifi.serialization.record.RecordSchema getSchema()throws MalformedRecordException{return reader.getSchema();}
                public Record nextRecord(boolean coerce,boolean drop)throws IOException,MalformedRecordException{return count++<513?reader.nextRecord(coerce,drop):null;}
                public void close()throws IOException{reader.close();}
            };
        }
    }

    private static TestRunner runner(JsonNode spec) throws Exception {
        var runner=TestRunners.newTestRunner(HaitongTransformRecord.class);
        var reader=new JsonTreeReader();runner.addControllerService("reader",reader);runner.setProperty(reader,"Schema Access Strategy","infer-schema");runner.enableControllerService(reader);
        var writer=new JsonRecordSetWriter();runner.addControllerService("writer",writer);runner.setProperty(writer,"Schema Access Strategy","inherit-record-schema");runner.setProperty(writer,"Suppress Null Values","never-suppress");runner.enableControllerService(writer);
        runner.setProperty(HaitongTransformRecord.READER,"reader");runner.setProperty(HaitongTransformRecord.WRITER,"writer");
        runner.setProperty(HaitongTransformRecord.PLAN,MappingEngine.compile(spec).document().toString());return runner;
    }
    private static String input(int size){var rows=new ArrayList<String>();for(int i=0;i<size;i++)rows.add("{\"n\":"+NUMBERS[i%NUMBERS.length]+"}");return "["+String.join(",",rows)+"]";}
    private static void noFailure(TestRunner runner){assertTrue(runner.getFlowFilesForRelationship(HaitongTransformRecord.FAILURE).isEmpty(),()->{
        var file=runner.getFlowFilesForRelationship(HaitongTransformRecord.FAILURE).getFirst();
        return file.getAttribute("ht.error.code")+" "+file.getAttribute("ht.error.cause.type")+" "+file.getAttribute("ht.error.message");
    });}
}
