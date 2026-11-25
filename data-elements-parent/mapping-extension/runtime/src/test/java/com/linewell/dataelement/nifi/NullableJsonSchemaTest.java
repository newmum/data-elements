package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.mapping.core.MappingEngine;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.json.JsonRecordSetWriter;
import org.apache.nifi.json.JsonTreeReader;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.serialization.*;
import org.apache.nifi.serialization.record.*;
import org.apache.nifi.serialization.record.Record;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NullableJsonSchemaTest {
    private static final ObjectMapper JSON=new ObjectMapper();

    @Test void registeredNullableDomainsAcceptOnlyOriginalNullsForTwentyAndHundredRecords()throws Exception {
        for(int count:List.of(20,100))for(String type:List.of("INT","LONG","DOUBLE","DECIMAL(20,6)","BOOLEAN","ARRAY")) {
            var runner=runner(type,true,true,"n");
            try {
                var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<count;i++)rows.add(Collections.singletonMap("n",null));
                runner.enqueue(JSON.writeValueAsBytes(rows));runner.run();
                runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);runner.assertTransferCount(HaitongTransformRecord.INVALID,0);
                runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
                var file=runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst();
                assertEquals("0",file.getAttribute("ht.dictionary.batches"));var output=JSON.readTree(file.toByteArray());assertEquals(count,output.size());
                for(var row:output)assertTrue(row.has("value")&&row.get("value").isNull());
            }finally{runner.shutdown();}
        }
    }

    @Test void nonNullableAndUnconfirmedReaderModesKeepStrictFailures()throws Exception {
        for(boolean nullable:List.of(false,true)) {
            var runner=runner("INT",nullable,!nullable,"n");
            try{failure(runner,"[{\"n\":null}]");}finally{runner.shutdown();}
        }
    }

    @Test void nonNullStringsAndBooleanCannotBorrowTheNullableProof()throws Exception {
        for(String value:List.of("\"1\"","\"null\"","true","\"bad\"")) {
            var runner=runner("INT",true,true,"n");
            try{failure(runner,"[{\"n\":null},{\"n\":"+value+"}]");}finally{runner.shutdown();}
        }
    }

    @Test void lateMismatchWithdrawsEveryEarlierWindow()throws Exception {
        var runner=runner("INT",true,true,"n");
        try {
            var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<MappingEngine.WINDOW_RECORDS;i++)rows.add(Collections.singletonMap("n",null));
            rows.add(Map.of("n","wrong"));String input=JSON.writeValueAsString(rows);failure(runner,input);
        }finally{runner.shutdown();}
    }

    @Test void registeredCamelAliasUsesTheSamePhysicalNullProof()throws Exception {
        var runner=runner("LONG",true,true,"business_type");
        try {
            runner.enqueue("[{\"businessType\":null},{\"businessType\":null}]");runner.run();
            runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
            assertEquals(2,JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray()).size());
        }finally{runner.shutdown();}
    }

    @Test void absentAndDuplicateTokensAreNotAnExplicitNullProof()throws Exception {
        for(String input:List.of("[{\"n\":null},{}]","[{\"n\":null,\"n\":null}]")) {
            var runner=runner("INT",true,true,"n");
            try{failure(runner,input);}finally{runner.shutdown();}
        }
    }

    @Test void readerLostValuesCannotForgeOriginalNullTokens()throws Exception {
        var runner=runner("INT",true,true,"n");
        try {
            var delegate=runner.getControllerService("reader",RecordReaderFactory.class);var lost=new NullingReader(delegate);
            runner.addControllerService("lost",lost);runner.enableControllerService(lost);runner.setProperty(HaitongTransformRecord.READER,"lost");
            failure(runner,"[{\"n\":\"not-null\"}]");
        }finally{runner.shutdown();}
    }

    @Test void invalidEnvelopeRetainsTheProvenDeclaredNullTypeForReplay()throws Exception {
        var runner=runner("INT",true,true,"n");
        try {
            var spec=spec("INT",true,"n").put("recordErrorMode","ROUTE_INVALID");spec.withArray("mappings").addObject().put("from","/bad").put("to","/other").put("targetType","INT");
            ((com.fasterxml.jackson.databind.node.ObjectNode)spec.get("schemaContract")).withArray("source").addObject().put("name","bad").put("logicalType","STRING").put("nullable",true);
            runner.setProperty(HaitongTransformRecord.PLAN,MappingEngine.compile(spec).document().toString());runner.enqueue("[{\"n\":null,\"bad\":\"wrong\"}]");runner.run();
            runner.assertTransferCount(HaitongTransformRecord.INVALID,1);runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);
            var original=runner.getFlowFilesForRelationship(HaitongTransformRecord.INVALID).getFirst();var attributes=new HashMap<>(original.getAttributes());attributes.remove(MappingErrorRecords.NEXT);attributes.remove(MappingErrorRecords.ATTEMPT);
            ((com.fasterxml.jackson.databind.node.ObjectNode)spec.withArray("mappings").get(1)).put("targetType","STRING");
            runner.setProperty(HaitongTransformRecord.PLAN,MappingEngine.compile(spec).document().toString());runner.clearTransferState();runner.enqueue(original.toByteArray(),attributes);runner.run();
            runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);runner.assertTransferCount(HaitongTransformRecord.INVALID,0);
            assertTrue(JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray()).get(0).get("value").isNull());
        }finally{runner.shutdown();}
    }

    private static void failure(TestRunner runner,String input)throws Exception {
        runner.enqueue(input);runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,0);runner.assertTransferCount(HaitongTransformRecord.INVALID,0);runner.assertTransferCount(HaitongTransformRecord.FAILURE,1);
        runner.getFlowFilesForRelationship(HaitongTransformRecord.FAILURE).getFirst().assertContentEquals(input);
    }
    private static com.fasterxml.jackson.databind.node.ObjectNode spec(String type,boolean nullable,String source) {
        var spec=JSON.createObjectNode().put("version","1.0");spec.putObject("policies").put("caseSensitive",false);
        spec.putArray("mappings").addObject().put("from","/"+source).put("to","/value").put("targetType",type);
        var field=spec.putObject("schemaContract").putArray("source").addObject().put("name",source).put("logicalType",type.startsWith("DECIMAL")?"DECIMAL":type).put("nullable",nullable);
        if(type.startsWith("DECIMAL"))field.put("precision",20).put("scale",6);
        return spec;
    }
    private static TestRunner runner(String type,boolean nullable,boolean exact,String source)throws Exception {
        var runner=TestRunners.newTestRunner(HaitongTransformRecord.class);var reader=new JsonTreeReader();runner.addControllerService("reader",reader);runner.setProperty(reader,"Schema Access Strategy","infer-schema");runner.enableControllerService(reader);
        var writer=new JsonRecordSetWriter();runner.addControllerService("writer",writer);runner.setProperty(writer,"Schema Access Strategy","inherit-record-schema");runner.enableControllerService(writer);
        runner.setProperty(HaitongTransformRecord.READER,"reader");runner.setProperty(HaitongTransformRecord.WRITER,"writer");runner.setProperty(HaitongTransformRecord.EXACT_JSON_NUMBERS,Boolean.toString(exact));runner.setProperty(HaitongTransformRecord.PLAN,MappingEngine.compile(spec(type,nullable,source)).document().toString());return runner;
    }
    public static final class NullingReader extends AbstractControllerService implements RecordReaderFactory {
        private final RecordReaderFactory delegate;NullingReader(RecordReaderFactory delegate){this.delegate=delegate;}
        @Override public RecordReader createRecordReader(Map<String,String> attributes,InputStream input,long size,ComponentLog logger)throws IOException,MalformedRecordException,org.apache.nifi.schema.access.SchemaNotFoundException {
            var reader=delegate.createRecordReader(attributes,input,size,logger);
            return new RecordReader() {
                public RecordSchema getSchema()throws MalformedRecordException{return reader.getSchema();}
                public Record nextRecord(boolean coerce,boolean dropUnknown)throws IOException,MalformedRecordException {
                    var row=reader.nextRecord(coerce,dropUnknown);return row==null?null:new MapRecord(row.getSchema(),Collections.singletonMap("n",null));
                }
                public void close()throws IOException{reader.close();}
            };
        }
    }
}
