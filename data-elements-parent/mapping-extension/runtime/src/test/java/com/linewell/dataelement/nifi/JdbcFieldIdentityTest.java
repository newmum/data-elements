package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.*;
import com.linewell.dataelement.mapping.core.*;
import org.apache.nifi.json.JsonRecordSetWriter;
import org.apache.nifi.json.JsonTreeReader;
import org.apache.nifi.processors.standard.ExecuteSQLRecord;
import org.apache.nifi.processors.standard.PutDatabaseRecord;
import org.apache.nifi.util.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual NiFi JDBC metadata, JSON codecs, mapping and quoted JDBC output. */
class JdbcFieldIdentityTest {
    private static final ObjectMapper JSON=new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final String WIRE="_ht_0000000000000001";

    @Test void sparseJsonNeverCopiesADeclaredSiblingIntoAnAbsentFieldThroughEitherWriter() throws Exception {
        var spec=JSON.createObjectNode().put("schemaVersion","2.0");
        spec.putObject("policies").put("onMissingSource","NULL");
        var fields=spec.putObject("schemaContract").putArray("source");
        var rules=spec.putArray("rules");
        for(String name:List.of("openKflx","open_kflx")) {
            fields.addObject().put("name",name).put("logicalType","ANY").put("nullable",true);
            var rule=rules.addObject().put("ruleId",name).put("sourceOptional",true);
            rule.putObject("input").put("kind","FIELD").put("name",name).put("logicalType","ANY");
            rule.putObject("target").put("name",name).put("logicalType","STRING").put("nullable",true);
            rule.putArray("steps");
        }
        for(boolean hasSchema:List.of(true,false)) {
        var candidate=spec.deepCopy();if(!hasSchema) candidate.remove("schemaContract");
        var plan=MappingEngine.compile(candidate);
        for(int count:List.of(20,100)) for(String format:List.of("json","csv")) {
            var rows=new ArrayList<Map<String,Object>>();
            for(int i=0;i<count;i++) rows.add(Map.of("open_kflx",List.of("validation-"+i)));
            var mapping=runner(plan,format);mapping.enqueue(JSON.writeValueAsBytes(rows));mapping.run();
            mapping.assertTransferCount(HaitongTransformRecord.FAILURE,0);mapping.assertTransferCount(HaitongTransformRecord.INVALID,0);
            mapping.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
            var output=mapping.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst();
            assertEquals("0",output.getAttribute("ht.dictionary.batches"));
            if(format.equals("json")) {
                var values=JSON.readTree(output.toByteArray());assertEquals(count,values.size());
                for(int i=0;i<count;i++) {assertTrue(values.get(i).path("openKflx").isNull());assertEquals(JSON.writeValueAsString(rows.get(i).get("open_kflx")),values.get(i).path("open_kflx").asText());}
            } else {
                var lines=new String(output.toByteArray(),java.nio.charset.StandardCharsets.UTF_8).split("\n");assertEquals(count,lines.length);
                for(int i=0;i<count;i++) assertTrue(lines[i].startsWith("\\N\u0001"));
            }
            mapping.shutdown();
        }
        }
    }

    @Test void nativeJdbcNumericColumnRestoresItsLiteralIdentityBeforeRulesAndBothWriters() throws Exception {
        for(int count:List.of(20,100)) {
            String url="jdbc:h2:mem:validation_numeric_"+UUID.randomUUID().toString().replace("-","")+";MODE=Oracle;DB_CLOSE_DELAY=-1";
            try(var connection=DriverManager.getConnection(url);var sql=connection.createStatement()) {
                sql.execute("CREATE TABLE VALIDATION_SOURCE(\"1231313\" VARCHAR2(40),ID INT,AMOUNT NUMBER(38,10))");
                sql.execute("CREATE TABLE VALIDATION_TARGET(\"1231313\" VARCHAR2(40),ID INT,AMOUNT NUMBER(38,10))");
                BigDecimal exact=new BigDecimal("12345.1250000000");
                try(var add=connection.prepareStatement("INSERT INTO VALIDATION_SOURCE VALUES(?,?,?)")) {
                    for(int i=0;i<count;i++){add.setString(1,i%3==0?null:"validation-"+i);add.setInt(2,i);add.setBigDecimal(3,exact);add.executeUpdate();}
                }
                var fetch=TestRunners.newTestRunner(ExecuteSQLRecord.class);
                var pool=new BinaryLobCompatibilityTest.Pool(url);fetch.addControllerService("pool",pool);fetch.enableControllerService(pool);
                var sourceWriter=new JsonRecordSetWriter();fetch.addControllerService("writer",sourceWriter);
                fetch.setProperty(sourceWriter,"Schema Access Strategy","inherit-record-schema");fetch.setProperty(sourceWriter,"Schema Write Strategy","no-schema");fetch.enableControllerService(sourceWriter);
                fetch.setProperty("Database Connection Pooling Service","pool");fetch.setProperty("Record Writer","writer");
                fetch.setProperty("Normalize Table/Column Names","false");fetch.setProperty("Use Avro Logical Types","true");
                fetch.enqueue("SELECT \"1231313\" AS \""+WIRE+"\",ID,AMOUNT FROM VALIDATION_SOURCE ORDER BY ID");fetch.run();
                fetch.assertTransferCount("success",1);fetch.assertTransferCount("failure",0);
                var source=fetch.getFlowFilesForRelationship("success").getFirst();
                assertTrue(JSON.readTree(source.toByteArray()).get(0).has(WIRE));
                var spec=JSON.createObjectNode().put("version","1.0");var mappings=spec.putArray("mappings");
                mappings.addObject().put("from","/1231313").put("to","/1231313").put("targetType","VARCHAR2(40 CHAR)");
                mappings.addObject().put("from","/ID").put("to","/ID").put("targetType","INT");
                mappings.addObject().put("from","/AMOUNT").put("to","/AMOUNT").put("targetType","NUMBER(38,10)");
                var plan=MappingEngine.compile(spec);
                for(String format:List.of("json","csv")) {
                    var mapping=runner(plan,format);
                    mapping.setProperty(HaitongTransformRecord.JDBC_ALIASES,"{\"1231313\":\""+WIRE+"\"}");
                    mapping.enqueue(source.toByteArray(),source.getAttributes());mapping.run();
                    mapping.assertTransferCount(HaitongTransformRecord.FAILURE,0);mapping.assertTransferCount(HaitongTransformRecord.INVALID,0);
                    mapping.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
                    var output=mapping.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst();
                    assertEquals("0",output.getAttribute("ht.dictionary.batches"));
                    if(format.equals("csv")) {
                        String text=new String(output.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
                        assertFalse(text.contains(WIRE));assertTrue(text.contains(exact.toPlainString()));
                        String[] lines=text.split("\n");assertEquals(count,lines.length);
                        for(int i=0;i<count;i++)assertEquals((i%3==0?"\\N":"validation-"+i)+"\u0001"+i+"\u0001"+exact.toPlainString(),lines[i]);
                        mapping.shutdown();continue;
                    }
                    var values=JSON.readTree(output.toByteArray());assertEquals(count,values.size());
                    for(int i=0;i<count;i++) {
                        assertFalse(values.get(i).has(WIRE));assertTrue(values.get(i).has("1231313"));
                        assertEquals(0,exact.compareTo(values.get(i).path("AMOUNT").decimalValue()));
                    }
                    var sink=TestRunners.newTestRunner(PutDatabaseRecord.class);
                    var targetPool=new BinaryLobCompatibilityTest.Pool(url);sink.addControllerService("pool",targetPool);sink.enableControllerService(targetPool);
                    var reader=new JsonTreeReader();sink.addControllerService("reader",reader);
                    sink.setProperty(reader,"Schema Access Strategy","infer-schema");sink.enableControllerService(reader);
                    sink.setProperty("Database Connection Pooling Service","pool");sink.setProperty("Record Reader","reader");
                    sink.setProperty("Statement Type","INSERT");sink.setProperty("Table Name","VALIDATION_TARGET");sink.setProperty("Database Type","Generic");
                    sink.setProperty("Quote Column Identifiers","true");sink.setProperty("Translate Field Names","false");
                    sink.enqueue(output.toByteArray(),output.getAttributes());sink.run();
                    sink.assertTransferCount("failure",0);sink.assertTransferCount("retry",0);sink.assertTransferCount("success",1);
                    try(var rows=sql.executeQuery("SELECT \"1231313\",ID,AMOUNT FROM VALIDATION_TARGET ORDER BY ID")) {
                        int i=0;while(rows.next()){assertEquals(i%3==0?null:"validation-"+i,rows.getString(1));assertEquals(i,rows.getInt(2));assertEquals(0,exact.compareTo(rows.getBigDecimal(3)));i++;}assertEquals(count,i);
                    }
                    sink.shutdown();mapping.shutdown();
                }
                fetch.shutdown();
            }
        }
    }

    private static TestRunner runner(MappingEngine.Plan plan,String format)throws Exception {
        var runner=TestRunners.newTestRunner(HaitongTransformRecord.class);
        var reader=new JsonTreeReader();runner.addControllerService("reader",reader);
        runner.setProperty(reader,"Schema Access Strategy","infer-schema");runner.enableControllerService(reader);
        org.apache.nifi.serialization.RecordSetWriterFactory writer=format.equals("json")?new JsonRecordSetWriter():new org.apache.nifi.csv.CSVRecordSetWriter();
        runner.addControllerService("writer",writer);runner.setProperty(writer,"Schema Access Strategy","inherit-record-schema");
        runner.setProperty(writer,"Schema Write Strategy","no-schema");
        if(format.equals("csv")) {
            var settings=Map.of("CSV Format","custom","Value Separator","\\u0001","Record Separator","\\n",
                    "Include Header Line","false","Quote Mode","NONE","Escape Character","\\","Null String","\\\\N","Trim Fields","false");
            settings.forEach((key,value)->runner.setProperty(writer,key,value));
        } else runner.setProperty(writer,"Suppress Null Values","never-suppress");
        runner.enableControllerService(writer);runner.setProperty(HaitongTransformRecord.READER,"reader");runner.setProperty(HaitongTransformRecord.WRITER,"writer");
        runner.setProperty(HaitongTransformRecord.PLAN,plan.document().toString());return runner;
    }

    @Test void nativeConflictingWireAndPhysicalFieldsRetainTheWholeInput() throws Exception {
        var spec=JSON.createObjectNode().put("version","1.0");
        spec.putArray("mappings").addObject().put("from","/1231313").put("to","/1231313").put("targetType","STRING");
        var runner=runner(MappingEngine.compile(spec),"json");
        runner.setProperty(HaitongTransformRecord.JDBC_ALIASES,"{\"1231313\":\""+WIRE+"\"}");
        byte[] original=("[{\"1231313\":null,\""+WIRE+"\":\"validation\"}]").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        runner.enqueue(original);runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,0);
        runner.assertTransferCount(HaitongTransformRecord.FAILURE,1);
        assertArrayEquals(original,runner.getFlowFilesForRelationship(HaitongTransformRecord.FAILURE).getFirst().toByteArray());runner.shutdown();
    }

    @Test void restorationIsIdempotentAndNeverOverwritesAnOriginalField() {
        var aliases=JdbcRecordAliases.parse("{\"1231313\":\""+WIRE+"\"}");
        var value=new BigDecimal("12345678901234567890.123456789");
        var restored=aliases.restore(Map.of(WIRE,value,"id",1));assertSame(value,restored.get("1231313"));
        assertEquals(restored,aliases.restore(restored));
        var collision=new LinkedHashMap<String,Object>();collision.put(WIRE,null);collision.put("1231313",value);
        assertEquals("AMBIGUOUS_FIELD",assertThrows(MappingException.class,()->aliases.restore(collision)).code());
        for(String input:List.of("[]","{\"123/child\":\""+WIRE+"\"}","{\"123\":\"unregistered\"}","{\"123\":\""+WIRE+"\",\"456\":\""+WIRE+"\"}"))
            assertEquals("INVALID_FIELD_ALIASES",assertThrows(MappingException.class,()->JdbcRecordAliases.parse(input)).code());
    }
}
