package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.linewell.dataelement.mapping.core.*;
import java.math.*;
import java.nio.ByteBuffer;
import java.sql.*;
import java.util.*;
import org.apache.nifi.avro.*;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.dbcp.DBCPService;
import org.apache.nifi.json.JsonTreeReader;
import org.apache.nifi.json.JsonRecordSetWriter;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processors.standard.PutDatabaseRecord;
import org.apache.nifi.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual NiFi JSON -> mapping -> embedded Avro -> native JDBC writer, with isolated storage. */
class BinaryLobCompatibilityTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final byte[] JPEG={(byte)255,(byte)216,(byte)255,0,1,127,(byte)128,(byte)217};

    static MappingEngine.Plan plan(String target,boolean binarySource) {
        var legacy=JSON.createObjectNode().put("version","1.0").put("recordErrorMode","FAIL_FLOWFILE");
        var mappings=legacy.putArray("mappings");
        mappings.addObject().put("from","event_no").put("to","event_no").put("targetType","STRING").put("targetNullable",false);
        mappings.addObject().put("from","pic_byte").put("to","pic_byte").put("targetType",binarySource?"BLOB":"CLOB").put("targetNullable",false);
        var contract=legacy.putObject("schemaContract");
        contract.putArray("source").addObject().put("name","event_no").put("logicalType","STRING");
        ((ArrayNode)contract.get("source")).addObject().put("name","pic_byte").put("logicalType",binarySource?"BINARY":"STRING").put("physicalType",binarySource?"BLOB":"CLOB");
        var fields=contract.putArray("target");
        fields.addObject().put("name","event_no").put("logicalType","STRING").put("nullable",false);
        fields.addObject().put("name","pic_byte").put("logicalType",target.equals("BLOB")?"BINARY":"STRING").put("physicalType",target).put("nullable",false);
        return MappingEngine.compile(legacy);
    }
    static TestRunner runner(MappingEngine.Plan plan,boolean avro)throws Exception {
        return runner(plan,avro?"avro":"mock");
    }
    static TestRunner runner(MappingEngine.Plan plan,String codec)throws Exception {
        var runner=TestRunners.newTestRunner(HaitongTransformRecord.class);
        var reader=new JsonTreeReader();runner.addControllerService("reader",reader);
        runner.setProperty(reader,"Schema Access Strategy","infer-schema");runner.enableControllerService(reader);
        org.apache.nifi.serialization.RecordSetWriterFactory writer=switch(codec){case "avro"->new AvroRecordSetWriter();case "json"->new JsonRecordSetWriter();default->new HaitongTransformRecordTest.Writer();};
        runner.addControllerService("writer",writer);
        if(!codec.equals("mock")){runner.setProperty(writer,"Schema Access Strategy","inherit-record-schema");runner.setProperty(writer,"Schema Write Strategy",codec.equals("avro")?"avro-embedded":"full-schema-attribute");}
        runner.enableControllerService(writer);
        runner.setProperty(HaitongTransformRecord.READER,"reader");runner.setProperty(HaitongTransformRecord.WRITER,"writer");
        runner.setProperty(HaitongTransformRecord.PLAN,plan.document().toString());return runner;
    }
    private static List<Integer> signed(byte[] bytes){var result=new ArrayList<Integer>();for(byte b:bytes)result.add((int)b);return result;}
    private static ObjectNode row(int index,Object value){var row=JSON.createObjectNode().put("event_no","validation-"+index);row.set("pic_byte",JSON.valueToTree(value));return row;}

    @Test void signedUnsignedBuffersAndJdbcLobsAreLosslessAndInvalidOctetsAreRejected()throws Exception {
        assertArrayEquals(JPEG,BinaryValues.bytes(signed(JPEG)));
        assertArrayEquals(JPEG,BinaryValues.bytes(List.of(255,216,255,0,1,127,128,217)));
        var buffer=ByteBuffer.wrap(JPEG);assertArrayEquals(JPEG,BinaryValues.bytes(buffer));assertEquals(0,buffer.position());
        assertArrayEquals(JPEG,BinaryValues.bytes(Base64.getEncoder().encodeToString(JPEG)));
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:validation_lob_cast")){
            var blob=connection.createBlob();blob.setBytes(1,JPEG);assertArrayEquals(JPEG,BinaryValues.bytes(blob));
            var clob=connection.createClob();clob.setString(1,"完整大文本");
            assertEquals("完整大文本",ValueTypes.cast(clob,"CLOB",0,0,RoundingMode.UNNECESSARY));
        }
        for(Object invalid:List.of(List.of(256),List.of(-129),List.of(1.5),List.of(new BigDecimal("2.5")),Arrays.asList(1,null),List.of("1"),"not base64!"))
            assertEquals("TYPE_MISMATCH",assertThrows(MappingException.class,()->BinaryValues.bytes(invalid)).code());
        for(String alias:List.of("BLOB","BINARY(8)","VARBINARY(8)","RAW(8)","BYTEA"))assertEquals("BINARY",ValueTypes.requireType(alias));
    }
    @Test void nativeNifiWriterPersistsTwentyAndHundredImagesToClobAndBlob()throws Exception {
        for(String codec:List.of("avro","json"))for(String type:List.of("CLOB","BLOB"))for(int count:List.of(20,100)){
            var plan=plan(type,true);var runner=runner(plan,codec);var input=JSON.createArrayNode();
            for(int i=0;i<count;i++)input.add(row(i,signed(JPEG)));
            runner.enqueue(input.toString());runner.run();runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);
            runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
            var output=runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst();
            assertEquals("0",output.getAttribute("ht.dictionary.batches"));
            String url="jdbc:h2:mem:validation_binary_"+UUID.randomUUID().toString().replace("-","")+";MODE=Oracle;DB_CLOSE_DELAY=-1";
            try(var database=DriverManager.getConnection(url)){
                try(var sql=database.createStatement()){sql.execute("CREATE TABLE validation_binary (event_no VARCHAR(100) PRIMARY KEY, pic_byte "+type+" NOT NULL)");}
                var sink=TestRunners.newTestRunner(PutDatabaseRecord.class);var pool=new Pool(url);sink.addControllerService("pool",pool);sink.enableControllerService(pool);
                org.apache.nifi.serialization.RecordReaderFactory avroReader=codec.equals("avro")?new AvroReader():new JsonTreeReader();sink.addControllerService("avro",avroReader);
                if(codec.equals("json")){sink.setProperty(avroReader,"Schema Access Strategy","schema-text-property");sink.setProperty(avroReader,"Schema Text","${avro.schema}");}
                sink.enableControllerService(avroReader);
                sink.setProperty("Database Connection Pooling Service","pool");sink.setProperty("Record Reader","avro");
                sink.setProperty("Statement Type","INSERT");sink.setProperty("Table Name","VALIDATION_BINARY");sink.setProperty("Database Type","Generic");
                sink.enqueue(output.toByteArray(),output.getAttributes());sink.run();sink.assertTransferCount("failure",0);sink.assertTransferCount("retry",0);sink.assertTransferCount("success",1);
                try(var sql=database.createStatement();var records=sql.executeQuery("SELECT pic_byte FROM validation_binary")){
                    int stored=0;while(records.next()){byte[] bytes=type.equals("BLOB")?records.getBytes(1):Base64.getDecoder().decode(records.getString(1));assertArrayEquals(JPEG,bytes);stored++;}assertEquals(count,stored);
                }
            }
        }
    }
    @Test void base64CarrierAndOrdinaryClobTextArePreserved()throws Exception {
        String base64=Base64.getEncoder().encodeToString(JPEG);
        for(String target:List.of("BLOB","CLOB")){
            var runner=runner(plan(target,true),false);runner.enqueue("["+row(0,base64)+"]");runner.run();runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);
            assertEquals(base64,JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray()).get(0).path("pic_byte").asText());
        }
        var runner=runner(plan("CLOB",false),false);String text="普通大文本，不是Base64";runner.enqueue("["+row(0,text)+"]");runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
        assertEquals(text,JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray()).get(0).path("pic_byte").asText());
    }
    @Test void largeBinaryToClobBypassesTextTransformBudgetButKeepsRecordBudget()throws Exception {
        byte[] bytes=new byte[300_000];new Random(7).nextBytes(bytes);
        var runner=runner(plan("CLOB",true),false);runner.enqueue("["+row(0,signed(bytes))+"]");runner.run();runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);
        assertArrayEquals(bytes,Base64.getDecoder().decode(JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray()).get(0).path("pic_byte").asText()));
        assertEquals("BUDGET_EXCEEDED",assertThrows(MappingException.class,()->BinaryValues.base64(new byte[6_291_457])).code());
    }
    @Test void mixedBinaryCarriersAreAllowedOnlyForDeclaredBinaryFields()throws Exception {
        var input=JSON.createArrayNode().add(row(0,signed(JPEG))).add(row(1,Base64.getEncoder().encodeToString(JPEG)));
        var runner=runner(plan("CLOB",true),false);runner.enqueue(input.toString());runner.run();runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);
        var rows=JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray());
        for(var row:rows)assertArrayEquals(JPEG,Base64.getDecoder().decode(row.path("pic_byte").asText()));
        runner=runner(plan("CLOB",false),false);runner.enqueue(input.toString());runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,0);runner.assertTransferCount(HaitongTransformRecord.FAILURE,1);
    }
    @Test void binaryTargetLengthIsInBytesAndFallbackCannotStringifyBadArray(){
        var spec=plan("BLOB",true).document();((ObjectNode)spec.path("rules").get(1).path("target")).put("length",4);
        var result=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("event_no","validation","pic_byte",signed(JPEG))),0,new ExecutionContext(java.time.Instant.EPOCH,java.time.ZoneId.of("Asia/Shanghai"),Locale.ROOT,"validation"),null);
        assertFalse(result.rows().getFirst().success());
        spec=plan("CLOB",true).document();((ObjectNode)spec.path("rules").get(1)).put("onError","KEEP_SOURCE");
        result=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("event_no","validation","pic_byte",List.of(256))),0,new ExecutionContext(java.time.Instant.EPOCH,java.time.ZoneId.of("Asia/Shanghai"),Locale.ROOT,"validation"),null);
        assertFalse(result.rows().getFirst().success());
    }
    @Test void badLateOctetOrRequiredNullPreservesWholeOriginalAndEmitsNoSuccess()throws Exception {
        for(Object invalid:List.of(List.of(256),Collections.singletonList(null))){
            var runner=runner(plan("CLOB",true),false);var input=JSON.createArrayNode();input.add(row(0,signed(JPEG)));input.add(row(1,invalid));byte[] original=JSON.writeValueAsBytes(input);
            runner.enqueue(original);runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,0);runner.assertTransferCount(HaitongTransformRecord.FAILURE,1);
            assertArrayEquals(original,runner.getFlowFilesForRelationship(HaitongTransformRecord.FAILURE).getFirst().toByteArray());
        }
        var runner=runner(plan("BLOB",true),false);runner.enqueue("[{\"event_no\":\"validation-null\",\"pic_byte\":null}]");runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,0);runner.assertTransferCount(HaitongTransformRecord.FAILURE,1);
    }
    public static class Pool extends AbstractControllerService implements DBCPService {
        private final String url;Pool(String url){this.url=url;}
        public Connection getConnection(){try{return DriverManager.getConnection(url);}catch(SQLException error){throw new ProcessException("Validation database connection failed");}}
    }
}
