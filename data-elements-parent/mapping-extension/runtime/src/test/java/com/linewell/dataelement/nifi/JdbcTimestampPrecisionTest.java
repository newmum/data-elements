package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.mapping.core.MappingEngine;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.apache.nifi.json.JsonRecordSetWriter;
import org.apache.nifi.json.JsonTreeReader;
import org.apache.nifi.processors.standard.ExecuteSQLRecord;
import org.apache.nifi.processors.standard.PutDatabaseRecord;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcTimestampPrecisionTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String FORMAT="yyyy-MM-dd HH:mm:ss.SSSSSSSSS";

    @Test void nativeJdbcGovernanceAndDatabaseKeepFullFractionsNullsAndTimestampLookingVarchar() throws Exception {
        TimeZone previous=TimeZone.getDefault();
        // Run this fixture in a fresh JVM for each user.timezone: JDBC drivers cache
        // the default zone, so changing it between live H2 connections is not a valid driver matrix.
        try {for(String zone:List.of(previous.getID()))for(int count:List.of(20,100)) {
            String url="jdbc:h2:mem:validation_timestamp_"+UUID.randomUUID().toString().replace("-","")+";DB_CLOSE_DELAY=-1";
            try(var db=DriverManager.getConnection(url);var sql=db.createStatement()) {
                sql.execute("CREATE TABLE VALIDATION_SOURCE(ID INT, TS TIMESTAMP(9), RAW VARCHAR(80))");
                sql.execute("CREATE TABLE VALIDATION_TARGET(ID INT, TS TIMESTAMP(9), RAW VARCHAR(80))");
                try(var add=db.prepareStatement("INSERT INTO VALIDATION_SOURCE VALUES(?,?,?)")) {
                    for(int i=0;i<count;i++){add.setInt(1,i);add.setObject(2,value(i));add.setString(3,"2024-01-02 03:04:05.000000001");add.addBatch();} add.executeBatch();
                }
            }
            var fetch=TestRunners.newTestRunner(ExecuteSQLRecord.class);
            var sourcePool=new BinaryLobCompatibilityTest.Pool(url);fetch.addControllerService("pool",sourcePool);fetch.enableControllerService(sourcePool);
            var sourceWriter=new JsonRecordSetWriter();fetch.addControllerService("writer",sourceWriter);
            fetch.setProperty(sourceWriter,"Schema Access Strategy","inherit-record-schema");fetch.setProperty(sourceWriter,"Schema Write Strategy","no-schema");fetch.setProperty(sourceWriter,"Timestamp Format",FORMAT);fetch.enableControllerService(sourceWriter);
            fetch.setProperty("Database Connection Pooling Service","pool");fetch.setProperty("Record Writer","writer");fetch.setProperty("Normalize Table/Column Names","false");fetch.setProperty("Use Avro Logical Types","true");
            fetch.enqueue("SELECT ID,TS,RAW FROM VALIDATION_SOURCE ORDER BY ID");fetch.run();fetch.assertTransferCount("failure",0);fetch.assertTransferCount("success",1);
            byte[] input=fetch.getFlowFilesForRelationship("success").getFirst().toByteArray();
            var governance=TestRunners.newTestRunner(HaitongTransformRecord.class);
            var reader=new JsonTreeReader();governance.addControllerService("reader",reader);governance.setProperty(reader,"Schema Access Strategy","infer-schema");governance.enableControllerService(reader);
            var writer=new JsonRecordSetWriter();governance.addControllerService("writer",writer);governance.setProperty(writer,"Schema Access Strategy","inherit-record-schema");governance.setProperty(writer,"Schema Write Strategy","no-schema");governance.setProperty(writer,"Timestamp Format",FORMAT);governance.enableControllerService(writer);
            var spec=JSON.createObjectNode().put("schemaVersion","2.0");spec.putObject("policies").put("zoneId",zone);
            var fields=spec.putObject("schemaContract").putArray("source");var rules=spec.putArray("rules");
            for(var field:Map.of("ID","INT","TS","DATETIME","RAW","STRING").entrySet()) {
                fields.addObject().put("name",field.getKey()).put("logicalType",field.getValue()).put("physicalType",field.getKey().equals("TS")?"TIMESTAMP(9)":field.getKey().equals("RAW")?"VARCHAR(80)":"INTEGER").put("nullable",true);
                var rule=rules.addObject();rule.putObject("input").put("kind","FIELD").put("name",field.getKey());rule.putObject("target").put("name",field.getKey()).put("logicalType",field.getValue()).put("nullable",true);
            }
            governance.setProperty(HaitongTransformRecord.READER,"reader");governance.setProperty(HaitongTransformRecord.WRITER,"writer");governance.setProperty(HaitongTransformRecord.TIMESTAMP_TEXT,"true");governance.setProperty(HaitongTransformRecord.PLAN,MappingEngine.compile(spec).document().toString());
            governance.enqueue(input);governance.run();governance.assertTransferCount(HaitongTransformRecord.FAILURE,0);governance.assertTransferCount(HaitongTransformRecord.INVALID,0);governance.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
            byte[] governed=governance.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray();
            var rows=JSON.readTree(governed);assertEquals(count,rows.size());
            for(int i=0;i<count;i++){assertEquals("2024-01-02 03:04:05.000000001",rows.get(i).path("RAW").asText());if(value(i)==null)assertTrue(rows.get(i).path("TS").isNull());else assertEquals(value(i),LocalDateTime.parse(rows.get(i).path("TS").asText().replace(' ','T')));}
            var csv=TestRunners.newTestRunner(org.apache.nifi.processors.standard.ConvertRecord.class);
            var csvReader=new JsonTreeReader();csv.addControllerService("reader",csvReader);csv.setProperty(csvReader,"Schema Access Strategy","infer-schema");csv.enableControllerService(csvReader);
            var csvWriter=new org.apache.nifi.csv.CSVRecordSetWriter();csv.addControllerService("writer",csvWriter);
            Map.of("Schema Access Strategy","inherit-record-schema","Schema Write Strategy","no-schema","CSV Format","custom","Value Separator","\\u0001","Record Separator","\\n","Include Header Line","false","Quote Mode","NONE","Escape Character","\\","Null String","\\\\N","Timestamp Format","yyyy-MM-dd HH:mm:ss.SSS")
                    .forEach((key,text)->csv.setProperty(csvWriter,key,text));
            csv.enableControllerService(csvWriter);csv.setProperty("Record Reader","reader");csv.setProperty("Record Writer","writer");csv.enqueue(governed);csv.run();csv.assertTransferCount("failure",0);csv.assertTransferCount("success",1);
            String[] lines=new String(csv.getFlowFilesForRelationship("success").getFirst().toByteArray(),java.nio.charset.StandardCharsets.UTF_8).split("\n");assertEquals(count,lines.length);
            for(int i=0;i<count;i++) {
                var cells=java.util.Arrays.asList(lines[i].split("\u0001",-1));assertEquals(3,cells.size());assertTrue(cells.contains(Integer.toString(i)));assertTrue(cells.contains("2024-01-02 03:04:05.000000001"));
                assertTrue(cells.contains(value(i)==null?"\\N":value(i).format(java.time.format.DateTimeFormatter.ofPattern(FORMAT))));
            }
            csv.shutdown();
            var sink=TestRunners.newTestRunner(PutDatabaseRecord.class);var targetPool=new BinaryLobCompatibilityTest.Pool(url);sink.addControllerService("pool",targetPool);sink.enableControllerService(targetPool);
            var targetReader=new JsonTreeReader();sink.addControllerService("reader",targetReader);sink.setProperty(targetReader,"Schema Access Strategy","infer-schema");sink.enableControllerService(targetReader);
            sink.setProperty("Database Connection Pooling Service","pool");sink.setProperty("Record Reader","reader");sink.setProperty("Statement Type","INSERT");sink.setProperty("Table Name","VALIDATION_TARGET");sink.setProperty("Database Type","Generic");sink.setProperty("Translate Field Names","false");sink.enqueue(governed);sink.run();sink.assertTransferCount("failure",0);sink.assertTransferCount("retry",0);sink.assertTransferCount("success",1);
            try(var db=DriverManager.getConnection(url);var sql=db.createStatement();var result=sql.executeQuery("SELECT ID,TS,RAW FROM VALIDATION_TARGET ORDER BY ID")) {
                int index=0;while(result.next()){assertEquals(index,result.getInt(1));assertEquals(value(index),result.getObject(2,LocalDateTime.class));assertEquals("2024-01-02 03:04:05.000000001",result.getString(3));index++;}assertEquals(count,index);
            }
            sink.shutdown();governance.shutdown();fetch.shutdown();
        }}finally{TimeZone.setDefault(previous);}
    }

    private static LocalDateTime value(int index) {
        String fraction=switch(index%5){case 0->null;case 1->"000000000";case 2->"123000000";case 3->"986096000";default->"986096123";};
        return fraction==null?null:LocalDateTime.parse("2024-01-02T03:04:05."+fraction);
    }
}
