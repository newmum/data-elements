package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.mapping.core.MappingEngine;
import org.apache.nifi.json.JsonRecordSetWriter;
import org.apache.nifi.json.JsonTreeReader;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** JSON inference describes token width; registered BIGINT values must retain their SQL domain. */
class JdbcIntegerCarrierTest {
    private static final ObjectMapper JSON=new ObjectMapper();

    @ParameterizedTest @ValueSource(ints={20,100})
    void exactJsonSmallIntegersKeepRegisteredLongDomainWithoutDatabaseLookups(int count)throws Exception {
        var runner=runner("LONG",true);
        try {
            var rows=new ArrayList<Map<String,Object>>();
            for(int index=0;index<count;index++)rows.add(Collections.singletonMap("n",index%4==0?null:index%4==1?0:index%4==2?-1:Integer.MAX_VALUE));
            runner.enqueue(JSON.writeValueAsBytes(rows));runner.run();
            runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);
            runner.assertTransferCount(HaitongTransformRecord.INVALID,0);
            runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
            var file=runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst();
            assertEquals("0",file.getAttribute("ht.dictionary.batches"));
            var output=JSON.readTree(file.toByteArray());assertEquals(count,output.size());
            for(int index=0;index<count;index++)assertEquals(JSON.valueToTree(rows.get(index).get("n")),output.get(index).get("value"));
        }finally{runner.shutdown();}
    }

    @Test void inferredLongBoundariesRemainExact()throws Exception {
        var runner=runner("LONG",true);
        try {
            var input=List.of(Map.of("n",Long.MIN_VALUE),Map.of("n",Long.MAX_VALUE),Map.of("n",0L));
            runner.enqueue(JSON.writeValueAsBytes(input));runner.run();
            runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
            runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);
            runner.assertTransferCount(HaitongTransformRecord.INVALID,0);
            var output=JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray());
            for(int index=0;index<input.size();index++)assertEquals(input.get(index).get("n"),output.get(index).path("value").longValue());
        }finally{runner.shutdown();}
    }

    @Test void stringBooleanFractionAndNarrowingStillFailSchemaChecks()throws Exception {
        for(String value:List.of("\"1\"","true","1.5")) {
            var runner=runner("LONG",true);
            try {
                String input="[{\"n\":"+value+"}]";runner.enqueue(input);runner.run();
                runner.assertTransferCount(HaitongTransformRecord.SUCCESS,0);
                runner.assertTransferCount(HaitongTransformRecord.FAILURE,1);
                runner.getFlowFilesForRelationship(HaitongTransformRecord.FAILURE).getFirst().assertContentEquals(input);
            }finally{runner.shutdown();}
        }
        var runner=runner("INT",true);
        try {
            runner.enqueue("[{\"n\":2147483648}]");runner.run();
            runner.assertTransferCount(HaitongTransformRecord.SUCCESS,0);
            runner.assertTransferCount(HaitongTransformRecord.FAILURE,1);
        }finally{runner.shutdown();}
    }

    @Test void disablingExactJsonCodecRetainsStrictReaderSchemaContract()throws Exception {
        var runner=runner("LONG",false);
        try {
            runner.enqueue("[{\"n\":1}]");runner.run();
            runner.assertTransferCount(HaitongTransformRecord.SUCCESS,0);
            runner.assertTransferCount(HaitongTransformRecord.FAILURE,1);
        }finally{runner.shutdown();}
    }

    private static TestRunner runner(String type,boolean exact)throws Exception {
        var spec=JSON.createObjectNode().put("version","1.0");
        spec.putArray("mappings").addObject().put("from","/n").put("to","/value").put("targetType",type);
        spec.putObject("schemaContract").putArray("source").addObject()
                .put("name","n").put("logicalType",type).put("physicalType",type.equals("LONG")?"bigint":"int").put("nullable",true);
        var runner=TestRunners.newTestRunner(HaitongTransformRecord.class);
        var reader=new JsonTreeReader();runner.addControllerService("reader",reader);
        runner.setProperty(reader,"Schema Access Strategy","infer-schema");runner.enableControllerService(reader);
        var writer=new JsonRecordSetWriter();runner.addControllerService("writer",writer);
        runner.setProperty(writer,"Schema Access Strategy","inherit-record-schema");runner.enableControllerService(writer);
        runner.setProperty(HaitongTransformRecord.READER,"reader");runner.setProperty(HaitongTransformRecord.WRITER,"writer");
        runner.setProperty(HaitongTransformRecord.EXACT_JSON_NUMBERS,Boolean.toString(exact));
        runner.setProperty(HaitongTransformRecord.PLAN,MappingEngine.compile(spec).document().toString());return runner;
    }
}
