package com.linewell.dataelement.nifi;

import org.apache.nifi.processors.standard.QueryRecord;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.MalformedRecordException;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class JdbcBooleanReaderTest {
    @Test void twentyAndHundredExactBooleanCarriersPreserveNullAndUnselectedFields()throws Exception {
        for(int count:new int[]{20,100}){
            var runner=TestRunners.newTestRunner(QueryRecord.class);var service=new HaitongJdbcRecordReader();
            runner.addControllerService("reader",service);runner.setProperty(service,"Schema Access Strategy","infer-schema");
            runner.setProperty(service,HaitongJdbcRecordReader.NUMBERS,"[]");runner.setProperty(service,HaitongJdbcRecordReader.BOOLEANS,"[\"FLAG\"]");runner.enableControllerService(service);
            String[] values={"true","false","1","0","1.000","null"};var json=new StringBuilder("[");
            for(int i=0;i<count;i++){if(i>0)json.append(',');json.append("{\"flag\":").append(values[i%values.length]).append(",\"ordinary\":2,\"nested\":{\"flag\":2}}");}json.append(']');
            byte[] bytes=json.toString().getBytes(StandardCharsets.UTF_8);
            try(var reader=service.createRecordReader(Map.of(),new ByteArrayInputStream(bytes),bytes.length,runner.getLogger())){
                assertEquals(RecordFieldType.BOOLEAN,reader.getSchema().getField("flag").orElseThrow().getDataType().getFieldType());
                for(int i=0;i<count;i++){var row=reader.nextRecord();assertNotNull(row);Object expected=switch(i%values.length){case 0,2,4->true;case 1,3->false;default->null;};assertEquals(expected,row.getValue("flag"));assertEquals(2,((Number)row.getValue("ordinary")).intValue());}
                assertNull(reader.nextRecord());
            }
        }
    }
    @Test void otherNumbersTextObjectsAndArraysCannotMasqueradeAsBoolean()throws Exception {
        for(String value:new String[]{"2","-1","0.1","\"1\"","\"true\"","{}","[]"}){
            var runner=TestRunners.newTestRunner(QueryRecord.class);var service=new HaitongJdbcRecordReader();runner.addControllerService("reader",service);
            runner.setProperty(service,"Schema Access Strategy","infer-schema");runner.setProperty(service,HaitongJdbcRecordReader.NUMBERS,"[]");runner.setProperty(service,HaitongJdbcRecordReader.BOOLEANS,"[\"flag\"]");runner.enableControllerService(service);
            byte[] bytes=("[{\"flag\":"+value+"}]").getBytes(StandardCharsets.UTF_8);
            assertThrows(MalformedRecordException.class,()->{try(var reader=service.createRecordReader(Map.of(),new ByteArrayInputStream(bytes),bytes.length,runner.getLogger())){reader.nextRecord();}});
        }
    }
    @Test void hiveCsvReceivesBooleanLiteralsRatherThanNumericText()throws Exception {
        var runner=TestRunners.newTestRunner(org.apache.nifi.processors.standard.ConvertRecord.class);
        var reader=new HaitongJdbcRecordReader();runner.addControllerService("reader",reader);
        runner.setProperty(reader,"Schema Access Strategy","infer-schema");runner.setProperty(reader,HaitongJdbcRecordReader.NUMBERS,"[]");runner.setProperty(reader,HaitongJdbcRecordReader.BOOLEANS,"[\"flag\"]");runner.enableControllerService(reader);
        var writer=new org.apache.nifi.csv.CSVRecordSetWriter();runner.addControllerService("writer",writer);
        for(var setting:Map.of("Schema Access Strategy","inherit-record-schema","Schema Write Strategy","no-schema","CSV Format","custom","Value Separator",",","Record Separator","\\n","Include Header Line","false","Quote Mode","NONE","Escape Character","\\","Null String","\\\\N").entrySet())runner.setProperty(writer,setting.getKey(),setting.getValue());
        runner.enableControllerService(writer);runner.setProperty("Record Reader","reader");runner.setProperty("Record Writer","writer");
        runner.enqueue("[{\"flag\":1},{\"flag\":0},{\"flag\":null}]".getBytes(StandardCharsets.UTF_8));runner.run();runner.assertAllFlowFilesTransferred("success",1);
        var actual=new String(runner.getFlowFilesForRelationship("success").getFirst().toByteArray(),StandardCharsets.UTF_8).replace("\r\n","\n");
        assertEquals("true\nfalse\n\\N\n",actual);
    }
    @Test void overlappingPhysicalDeclarationsAreRejectedBeforeUse()throws Exception {
        var runner=TestRunners.newTestRunner(QueryRecord.class);var service=new HaitongJdbcRecordReader();runner.addControllerService("reader",service);
        runner.setProperty(service,"Schema Access Strategy","infer-schema");runner.setProperty(service,HaitongJdbcRecordReader.NUMBERS,"[\"flag\"]");runner.setProperty(service,HaitongJdbcRecordReader.BOOLEANS,"[\"flag\"]");var error=assertThrows(Throwable.class,()->runner.enableControllerService(service));
        var message=new StringBuilder();for(Throwable cause=error;cause!=null;cause=cause.getCause())message.append(cause.getMessage());assertTrue(message.toString().contains("不同JDBC精确类型"));
    }
}
