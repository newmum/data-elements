package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.*;
import org.apache.nifi.json.JsonRecordSetWriter;
import org.apache.nifi.json.JsonTreeReader;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processors.standard.QueryRecord;
import org.apache.nifi.processors.standard.PutDatabaseRecord;
import org.apache.nifi.serialization.*;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.util.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class JdbcExactNumberReaderTest {
    private static final ObjectMapper JSON=new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final String[] VALUES={"12.1234567890123456","123.1234567890123456","99999999999999999999999999999999999999",
            "9007199254740993","0.00000000000000000000000000000000000001","null"};

    @Test void originalFieldIdentityNullsAndConfiguredTemporalFormatsSurviveTheJdbcBoundary() throws Exception {
        var runner=TestRunners.newTestRunner(QueryRecord.class);var service=reader(runner);
        runner.disableControllerService(service);runner.setProperty(service,HaitongJdbcRecordReader.NUMBERS,"[\"n\",\"MISSING\"]");
        runner.setProperty(service,"Timestamp Format","yyyy-MM-dd HH:mm:ss.SSS");runner.enableControllerService(service);
        var original=new JsonTreeReader();runner.addControllerService("original",original);runner.setProperty(original,"Schema Access Strategy","infer-schema");
        runner.setProperty(original,"Timestamp Format","yyyy-MM-dd HH:mm:ss.SSS");runner.enableControllerService(original);
        String json="[{\"n\":null,\"N\":\"ordinary-string\",\"time\":\"1900-01-02 03:04:05.123\",\"flag\":true,\"nested\":{\"n\":0.1},\"list\":[0.1,0.2]}]";
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
        try(var restored=service.createRecordReader(Map.of(),new ByteArrayInputStream(bytes),bytes.length,runner.getLogger());
            var before=original.createRecordReader(Map.of(),new ByteArrayInputStream(bytes),bytes.length,runner.getLogger())){
            var actual=restored.nextRecord();var expected=before.nextRecord();assertFalse(restored.getSchema().getFieldNames().contains("MISSING"));
            assertNull(actual.getValue("n"));assertEquals("ordinary-string",actual.getValue("N"));
            for(String field:List.of("N","time","flag")){
                assertEquals(expected.getSchema().getField(field),actual.getSchema().getField(field));
                assertEquals(JSON.valueToTree(expected.getValue(field)),JSON.valueToTree(actual.getValue(field)));
            }
            assertEquals(expected.getSchema().getField("nested"),actual.getSchema().getField("nested"));
            assertEquals(((org.apache.nifi.serialization.record.Record)expected.getValue("nested")).getValue("n"),
                    ((org.apache.nifi.serialization.record.Record)actual.getValue("nested")).getValue("n"));
            assertEquals(expected.getSchema().getField("list"),actual.getSchema().getField("list"));
            assertArrayEquals(expected.getAsArray("list"),actual.getAsArray("list"));
        }
        for(String invalid:List.of("true","\"validation-invalid-number\"","{}","[1]")){
            byte[] bad=("[{\"n\":"+invalid+"}]").getBytes(StandardCharsets.UTF_8);
            try(var records=service.createRecordReader(Map.of(),new ByteArrayInputStream(bad),bad.length,runner.getLogger())){
                var error=assertThrows(org.apache.nifi.serialization.MalformedRecordException.class,records::nextRecord);
                assertFalse(error.getMessage().contains("validation-invalid-number"));assertNull(error.getCause());
            }
        }
        runner.shutdown();
    }

    @Test void exactNumberSchemaAndValuesDoNotAlterOtherInferredFieldsOrBinaryBytes() throws Exception {
        for(int size:List.of(20,100)){
            var runner=TestRunners.newTestRunner(HaitongTransformRecord.class);var service=reader(runner);
            String content=records(size,true);byte[] bytes=content.getBytes(StandardCharsets.UTF_8);
            try(var recordReader=service.createRecordReader(Map.of(),new ByteArrayInputStream(bytes),bytes.length,mock(ComponentLog.class))){
                assertEquals(RecordFieldType.DECIMAL,recordReader.getSchema().getField("n").orElseThrow().getDataType().getFieldType());
                for(int index=0;index<size;index++){
                    var row=recordReader.nextRecord();assertNotNull(row);String expected=VALUES[index%VALUES.length];
                    if(expected.equals("null"))assertNull(row.getValue("n"));else{
                        assertInstanceOf(BigDecimal.class,row.getValue("n"));assertEquals(0,new BigDecimal(expected).compareTo((BigDecimal)row.getValue("n")));
                    }
                    assertEquals(0.1d,row.getValue("approximate"));assertEquals("00123",row.getValue("text"));assertEquals(true,row.getValue("flag"));
                    assertArrayEquals(new byte[]{0,127,-1},(byte[])row.getValue("content"));assertEquals("2024-05-10 14:27:49",row.getValue("time"));
                }assertNull(recordReader.nextRecord());
            }runner.shutdown();
        }
    }

    @Test void actualQueryRecordProjectionAndSecondJdbcReaderKeepExactNumbers() throws Exception {
        for(int size:List.of(20,100)){
            var runner=TestRunners.newTestRunner(QueryRecord.class);reader(runner);
            var writer=new JsonRecordSetWriter();runner.addControllerService("writer",writer);runner.setProperty(writer,"Schema Access Strategy","inherit-record-schema");runner.enableControllerService(writer);
            for(var descriptor:runner.getProcessor().getPropertyDescriptors()){
                if(descriptor.getControllerServiceDefinition()==RecordReaderFactory.class)runner.setProperty(descriptor,"reader");
                if(descriptor.getControllerServiceDefinition()==RecordSetWriterFactory.class)runner.setProperty(descriptor,"writer");
            }
            runner.setProperty("project","SELECT \"n\" FROM FLOWFILE");runner.enqueue(records(size,false));runner.run();
            runner.assertTransferCount("failure",0);runner.assertTransferCount("project",1);
            var output=runner.getFlowFilesForRelationship("project").getFirst();var parsed=JSON.readTree(output.toByteArray());assertEquals(size,parsed.size());
            var service=runner.getControllerService("reader",RecordReaderFactory.class);
            try(var second=service.createRecordReader(output.getAttributes(),new ByteArrayInputStream(output.toByteArray()),output.toByteArray().length,mock(ComponentLog.class))){
                for(int index=0;index<size;index++){
                    var row=second.nextRecord();String expected=VALUES[index%VALUES.length];
                    if(expected.equals("null"))assertNull(row.getValue("n"));else assertEquals(0,new BigDecimal(expected).compareTo((BigDecimal)row.getValue("n")));
                }assertNull(second.nextRecord());
            }runner.shutdown();
        }
    }

    @Test void actualJdbcWriterBindsNumbersAndBinaryToTheirDeclaredDatabaseTypes() throws Exception {
        for(int count:List.of(20,100)) {
            String url="jdbc:h2:mem:validation_exact_jdbc_"+count+";DB_CLOSE_DELAY=-1";
            try(var database=java.sql.DriverManager.getConnection(url)){
                database.createStatement().execute("CREATE TABLE VALIDATION_EXACT (ID INT PRIMARY KEY,N DECIMAL(76,38),CONTENT BLOB,NOTE VARCHAR(100))");
                var runner=TestRunners.newTestRunner(PutDatabaseRecord.class);var service=reader(runner);
                runner.disableControllerService(service);runner.setProperty(service,HaitongJdbcRecordReader.NUMBERS,"[\"N\"]");runner.setProperty(service,HaitongJdbcRecordReader.BINARY,"[\"CONTENT\"]");runner.enableControllerService(service);
                var pool=new BinaryLobCompatibilityTest.Pool(url);runner.addControllerService("pool",pool);runner.enableControllerService(pool);
                runner.setProperty("Record Reader","reader");runner.setProperty("Database Connection Pooling Service","pool");runner.setProperty("Statement Type","INSERT");runner.setProperty("Database Type","Generic");runner.setProperty("Table Name","VALIDATION_EXACT");
                var rows=new ArrayList<String>();for(int i=0;i<count;i++)rows.add("{\"ID\":"+i+",\"N\":"+VALUES[i%VALUES.length]+",\"CONTENT\":[0,127,255],\"NOTE\":\"validation-中文\"}");
                runner.enqueue("["+String.join(",",rows)+"]");runner.run();runner.assertTransferCount("failure",0);runner.assertTransferCount("retry",0);runner.assertTransferCount("success",1);
                try(var stored=database.createStatement().executeQuery("SELECT ID,N,CONTENT,NOTE FROM VALIDATION_EXACT ORDER BY ID")){
                    int index=0;while(stored.next()){
                        String expected=VALUES[index%VALUES.length];assertEquals(index,stored.getInt(1));
                        if(expected.equals("null"))assertNull(stored.getBigDecimal(2));else assertEquals(0,new BigDecimal(expected).compareTo(stored.getBigDecimal(2)));
                        assertArrayEquals(new byte[]{0,127,-1},stored.getBytes(3));assertEquals("validation-中文",stored.getString(4));index++;
                    }assertEquals(count,index);
                }runner.shutdown();
            }
        }
    }

    private static HaitongJdbcRecordReader reader(TestRunner runner)throws Exception {
        var service=new HaitongJdbcRecordReader();runner.addControllerService("reader",service);runner.setProperty(service,HaitongJdbcRecordReader.NUMBERS,"[\"n\"]");runner.setProperty(service,HaitongJdbcRecordReader.BINARY,"[\"content\"]");runner.enableControllerService(service);return service;
    }
    @Test void declaredTimestampCarrierKeepsFullFractionsThroughNativeLinewellConversionAndJdbcBinding() throws Exception {
        for(int count:List.of(20,100)) {
            var runner=TestRunners.newTestRunner(PutDatabaseRecord.class);var service=reader(runner);
            runner.disableControllerService(service);runner.setProperty(service,HaitongJdbcRecordReader.NUMBERS,"[]");runner.setProperty(service,HaitongJdbcRecordReader.BINARY,"[]");
            runner.setProperty(service,HaitongJdbcRecordReader.TIMESTAMPS,"[\"AT\"]");runner.enableControllerService(service);
            var rows=JSON.createArrayNode();
            for(int i=0;i<count;i++) {
                var row=rows.addObject().put("ID",i).put("TEXT","2024-01-02 03:04:05.986096123");
                if(i%5==0)row.putNull("AT");else row.put("AT",switch(i%5){case 1->"2024-01-02";case 2->"2024-01-02 03:04:05.123";case 3->"2024-01-02 03:04:05.986096";default->"2024-01-02 03:04:05.986096123";});
            }
            byte[] bytes=JSON.writeValueAsBytes(rows);String url="jdbc:h2:mem:validation_jdbc_timestamp_"+UUID.randomUUID().toString().replace("-","")+";DB_CLOSE_DELAY=-1";
            try(var db=java.sql.DriverManager.getConnection(url);var sql=db.createStatement()) {
                sql.execute("CREATE TABLE VALIDATION_TIMESTAMP(ID INT, AT TIMESTAMP(9), TEXT VARCHAR(80))");
                try(var values=service.createRecordReader(Map.of(),new ByteArrayInputStream(bytes),bytes.length,runner.getLogger());
                    var insert=db.prepareStatement("INSERT INTO VALIDATION_TIMESTAMP VALUES(?,?,?)")) {
                    assertEquals(RecordFieldType.TIMESTAMP,values.getSchema().getField("AT").orElseThrow().getDataType().getFieldType());
                    assertEquals(RecordFieldType.STRING,values.getSchema().getField("TEXT").orElseThrow().getDataType().getFieldType());
                    for(int i=0;i<count;i++) {
                        var row=values.nextRecord();Object value=row.getValue("AT");
                        if(value!=null) {
                            var timestamp=assertInstanceOf(java.sql.Timestamp.class,value);
                            value=org.apache.nifi.serialization.record.util.DataTypeUtils.convertType(timestamp,
                                    org.apache.nifi.serialization.record.util.DataTypeUtils.getDataTypeFromSQLTypeValue(java.sql.Types.TIMESTAMP),"AT");
                            assertEquals(timestamp.toLocalDateTime(),((java.sql.Timestamp)value).toLocalDateTime());
                        }
                        insert.setInt(1,i);insert.setObject(2,value,java.sql.Types.TIMESTAMP);insert.setString(3,row.getAsString("TEXT"));insert.addBatch();
                    }
                    assertNull(values.nextRecord());assertEquals(count,insert.executeBatch().length);
                }
                try(var stored=sql.executeQuery("SELECT ID,AT,TEXT FROM VALIDATION_TIMESTAMP ORDER BY ID")) {
                    for(int i=0;i<count;i++) {
                        assertTrue(stored.next());assertEquals(i,stored.getInt(1));assertEquals(rows.get(i).path("TEXT").asText(),stored.getString(3));
                        if(i%5==0)assertNull(stored.getObject(2));else {
                            String text=rows.get(i).path("AT").asText();var expected=text.length()==10?java.time.LocalDate.parse(text).atStartOfDay():java.time.LocalDateTime.parse(text.replace(' ','T'));
                            assertEquals(expected,stored.getObject(2,java.time.LocalDateTime.class));
                        }
                    }assertFalse(stored.next());
                }
            }runner.shutdown();
        }
    }

    @Test void timestampBoundaryRejectsInvalidOrOverlappingCarriersWithoutExposingTheirValues()throws Exception {
        var runner=TestRunners.newTestRunner(PutDatabaseRecord.class);var service=reader(runner);
        runner.disableControllerService(service);runner.setProperty(service,HaitongJdbcRecordReader.TIMESTAMPS,"[\"AT\"]");runner.enableControllerService(service);
        for(String value:List.of("1234567890123","true","[]","{}","\"validation-invalid-timestamp\"","\"2024-02-30 01:02:03\"")) {
            byte[] bytes=("[{\"AT\":"+value+"}]").getBytes(StandardCharsets.UTF_8);
            try(var values=service.createRecordReader(Map.of(),new ByteArrayInputStream(bytes),bytes.length,runner.getLogger())) {
                var error=assertThrows(MalformedRecordException.class,values::nextRecord);assertFalse(error.getMessage().contains("validation-invalid-timestamp"));assertNull(error.getCause());
            }
        }
        runner.disableControllerService(service);runner.setProperty(service,HaitongJdbcRecordReader.NUMBERS,"[\"at\"]");runner.enableControllerService(service);
        byte[] conflict="[{\"AT\":\"2024-01-02 03:04:05.986096123\"}]".getBytes(StandardCharsets.UTF_8);
        assertThrows(MalformedRecordException.class,()->service.createRecordReader(Map.of(),new ByteArrayInputStream(conflict),conflict.length,runner.getLogger()));
        runner.shutdown();
    }
    private static String records(int size,boolean fields){var rows=new ArrayList<String>();for(int i=0;i<size;i++)rows.add("{\"n\":"+VALUES[i%VALUES.length]+(fields?",\"approximate\":0.1,\"text\":\"00123\",\"flag\":true,\"time\":\"2024-05-10 14:27:49\",\"content\":[0,127,255]":"")+"}");return "["+String.join(",",rows)+"]";}
}
