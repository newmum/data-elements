package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.mapping.core.MappingEngine;
import org.apache.nifi.csv.CSVRecordSetWriter;
import org.apache.nifi.json.JsonTreeReader;
import org.apache.nifi.processors.standard.ConvertRecord;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HiveBinaryTransportTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    @Test void binaryTargetsKeepTheirTypeButUseLosslessBase64InHiveTextThroughActualCodecs() throws Exception {
        var spec=JSON.createObjectNode().put("version","1.0");var mappings=spec.putArray("mappings");
        mappings.addObject().put("from","id").put("to","id").put("targetType","INT");
        mappings.addObject().put("from","data").put("to","data").put("targetType","BLOB");
        var source=spec.putObject("schemaContract").putArray("source");
        source.addObject().put("name","id").put("logicalType","INT");
        source.addObject().put("name","data").put("logicalType","BINARY");
        var plan=MappingEngine.compile(spec);assertEquals("BINARY",plan.outputContracts().get("data").logicalType());
        byte[] bytes=new byte[12_000];new Random(7).nextBytes(bytes);var signed=new ArrayList<Integer>();for(byte b:bytes)signed.add((int)b);
        for(int count:List.of(20,100)) {
            var input=JSON.createArrayNode();
            for(int i=0;i<count;i++){var row=input.addObject().put("id",i);if(i%3==0)row.putNull("data");else row.set("data",JSON.valueToTree(i%3==1?signed:List.of()));}
            var mapping=BinaryLobCompatibilityTest.runner(plan,"json");mapping.setProperty(HaitongTransformRecord.BINARY_ENCODING,"BASE64_TEXT");
            mapping.enqueue(input.toString());mapping.run();mapping.assertTransferCount(HaitongTransformRecord.FAILURE,0);mapping.assertTransferCount(HaitongTransformRecord.INVALID,0);mapping.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
            var output=mapping.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst();
            var values=JSON.readTree(output.toByteArray());assertEquals(count,values.size());
            assertEquals("0",output.getAttribute("ht.dictionary.batches"));
            for(int i=0;i<count;i++){if(i%3==0)assertTrue(values.get(i).path("data").isNull());else assertArrayEquals(i%3==1?bytes:new byte[0],Base64.getDecoder().decode(values.get(i).path("data").asText()));}
            for(boolean typed:List.of(false,true)) {
                var csv=TestRunners.newTestRunner(ConvertRecord.class);var reader=new JsonTreeReader();csv.addControllerService("reader",reader);
                csv.setProperty(reader,"Schema Access Strategy",typed?"schema-text-property":"infer-schema");if(typed)csv.setProperty(reader,"Schema Text","${avro.schema}");csv.enableControllerService(reader);
                var writer=new CSVRecordSetWriter();csv.addControllerService("writer",writer);csv.setProperty(writer,"Schema Access Strategy","inherit-record-schema");csv.setProperty(writer,"Schema Write Strategy","no-schema");
                for(var setting:Map.of("CSV Format","custom","Value Separator","\\u0001","Record Separator","\\n","Include Header Line","false","Quote Mode","NONE","Escape Character","\\","Null String","\\\\N","Trim Fields","false").entrySet())csv.setProperty(writer,setting.getKey(),setting.getValue());csv.enableControllerService(writer);
                csv.setProperty("Record Reader","reader");csv.setProperty("Record Writer","writer");csv.enqueue(output.toByteArray(),output.getAttributes());csv.run();csv.assertTransferCount("failure",0);csv.assertTransferCount("success",1);
                String[] lines=new String(csv.getFlowFilesForRelationship("success").getFirst().toByteArray(),StandardCharsets.UTF_8).split("\n",-1);assertEquals(count+1,lines.length);
                for(int i=0;i<count;i++) {
                    String[] fields=lines[i].split("\u0001",-1);assertEquals(2,fields.length);assertEquals(Integer.toString(i),fields[0]);
                    if(i%3==0)assertEquals("\\N",fields[1]);else assertArrayEquals(i%3==1?bytes:new byte[0],Base64.getDecoder().decode(fields[1]));
                }
                csv.shutdown();
            }
            mapping.shutdown();
        }
    }
}
