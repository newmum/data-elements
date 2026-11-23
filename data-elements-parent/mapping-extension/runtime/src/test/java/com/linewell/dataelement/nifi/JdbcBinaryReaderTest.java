package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.nifi.json.JsonTreeReader;
import org.apache.nifi.processors.standard.PutDatabaseRecord;
import org.apache.nifi.serialization.MalformedRecordException;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JdbcBinaryReaderTest {
    @Test void actualJsonAndJdbcCodecsPreserveTwentyAndHundredBinaryRowsWithAllOtherFields() throws Exception {
        byte[] bytes=new byte[12_000];new Random(11).nextBytes(bytes);
        for(int count:List.of(20,100)) {
            String url="jdbc:h2:mem:validation_binary_reader_"+count+";DB_CLOSE_DELAY=-1";
            try(var db=DriverManager.getConnection(url)) {
                db.createStatement().execute("CREATE TABLE VALIDATION_BINARY (ID INT PRIMARY KEY, DATA BLOB, NOTE VARCHAR(100), ACTIVE BOOLEAN, ODS_UUID VARCHAR(100))");
                var runner=TestRunners.newTestRunner(PutDatabaseRecord.class);
                var delegate=new JsonTreeReader();runner.addControllerService("json",delegate);runner.setProperty(delegate,"Schema Access Strategy","infer-schema");runner.enableControllerService(delegate);
                var reader=new HaitongBinaryRecordReader();runner.addControllerService("binary",reader);runner.setProperty(reader,HaitongBinaryRecordReader.READER,"json");runner.setProperty(reader,HaitongBinaryRecordReader.FIELDS,"[\"DATA\"]");runner.enableControllerService(reader);
                var pool=new BinaryLobCompatibilityTest.Pool(url);runner.addControllerService("pool",pool);runner.enableControllerService(pool);
                runner.setProperty("Record Reader","binary");runner.setProperty("Database Connection Pooling Service","pool");runner.setProperty("Statement Type","INSERT");runner.setProperty("Table Name","VALIDATION_BINARY");runner.setProperty("Database Type","Generic");
                var input=new ObjectMapper().createArrayNode();
                for(int i=0;i<count;i++) {
                    var row=input.addObject().put("ID",i).put("NOTE","validation-中文-"+i).put("ACTIVE",i%2==0).put("ODS_UUID","validation-"+i);
                    if(i%3==0)row.putNull("DATA");else {var values=row.putArray("DATA");if(i%3==1)for(byte b:bytes)values.add(i%2==0?(b&255):(int)b);}
                }
                runner.enqueue(input.toString());runner.run();runner.assertTransferCount("failure",0);runner.assertTransferCount("success",1);
                try(var rows=db.createStatement().executeQuery("SELECT ID, DATA, NOTE, ACTIVE, ODS_UUID FROM VALIDATION_BINARY ORDER BY ID")) {
                    int i=0;while(rows.next()) {
                        assertEquals(i,rows.getInt(1));assertArrayEquals(i%3==0?null:i%3==1?bytes:new byte[0],rows.getBytes(2));
                        assertEquals("validation-中文-"+i,rows.getString(3));assertEquals(i%2==0,rows.getBoolean(4));assertEquals("validation-"+i,rows.getString(5));i++;
                    }assertEquals(count,i);
                }
                runner.shutdown();
            }
        }
    }

    @Test void onlyRegisteredBinaryFieldsAreRetypedAndInvalidBytesCannotBecomeTextOrSqlArrays() throws Exception {
        var runner=TestRunners.newTestRunner(PutDatabaseRecord.class);var delegate=new JsonTreeReader();runner.addControllerService("json",delegate);
        runner.setProperty(delegate,"Schema Access Strategy","infer-schema");runner.enableControllerService(delegate);
        var reader=new HaitongBinaryRecordReader();runner.addControllerService("binary",reader);runner.setProperty(reader,HaitongBinaryRecordReader.READER,"json");runner.setProperty(reader,HaitongBinaryRecordReader.FIELDS,"[\"data\"]");runner.enableControllerService(reader);
        String json="[{\"DATA\":[255,0,-128],\"ID\":7,\"FLAG\":true,\"BIRTHDAY\":\"1900-01-02T03:04:05.123456\",\"TAGS\":[1,2],\"ODS_UUID\":\"validation\"}]";
        try(var original=delegate.createRecordReader(Map.of(),input(json),json.length(),runner.getLogger());var restored=reader.createRecordReader(Map.of(),input(json),json.length(),runner.getLogger())) {
            var before=original.nextRecord();var after=restored.nextRecord();assertArrayEquals(new byte[]{-1,0,-128},(byte[])after.getValue("DATA"));
            assertEquals(RecordFieldType.BYTE,((org.apache.nifi.serialization.record.type.ArrayDataType)after.getSchema().getField("DATA").orElseThrow().getDataType()).getElementType().getFieldType());
            for(String name:List.of("ID","FLAG","BIRTHDAY","ODS_UUID")) {assertEquals(before.getValue(name),after.getValue(name));assertEquals(before.getSchema().getField(name),after.getSchema().getField(name));}
            assertArrayEquals(before.getAsArray("TAGS"),after.getAsArray("TAGS"));assertEquals(before.getSchema().getField("TAGS"),after.getSchema().getField("TAGS"));assertNull(restored.nextRecord());
        }
        for(String bad:List.of("[256]","[-129]","[1.5]","[null]","[\"validation-secret\"]","\"validation-invalid-base64\"")) {
            String document="[{\"DATA\":"+bad+"}]";
            try(var record=reader.createRecordReader(Map.of(),input(document),document.length(),runner.getLogger())) {
                var error=assertThrows(MalformedRecordException.class,record::nextRecord);assertNull(error.getCause());assertFalse(error.getMessage().contains("validation-secret"));
            }
        }
        runner.shutdown();
    }
    private static ByteArrayInputStream input(String text){return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));}
}
