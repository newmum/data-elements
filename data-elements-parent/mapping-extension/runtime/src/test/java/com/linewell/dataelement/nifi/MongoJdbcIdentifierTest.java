package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.mapping.core.MappingEngine;
import org.apache.nifi.json.JsonTreeReader;
import org.apache.nifi.processors.standard.PutDatabaseRecord;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Real JSON codecs, mapping engine and NiFi JDBC writer against isolated Oracle-style identifiers. */
class MongoJdbcIdentifierTest {
    @Test void mapsAndWritesMongoNamesWithoutLosingCaseOrUnderscoreKeys() throws Exception {
        var json = new ObjectMapper();
        var spec = json.createObjectNode().put("version","1.0");
        var rules = spec.putArray("mappings");
        for (String name : List.of("_id","@timestamp","ID","APPLICATION_ID"))
            rules.addObject().put("from", "/"+name).put("to", "/"+name).put("targetType","STRING");
        var plan = MappingEngine.compile(spec);
        for (int count : List.of(20,100)) {
            var input = json.createArrayNode();
            for (int i=0;i<count;i++) input.addObject().put("_id",String.format("%024x",i)).put("ID","business-"+i)
                    .put("@timestamp","2026-11-22T00:00:00Z").put("APPLICATION_ID","validation");
            var mapping = BinaryLobCompatibilityTest.runner(plan,"json");
            mapping.enqueue(input.toString()); mapping.run();
            mapping.assertTransferCount(HaitongTransformRecord.FAILURE,0);
            mapping.assertTransferCount(HaitongTransformRecord.INVALID,0);
            mapping.assertTransferCount(HaitongTransformRecord.SUCCESS,1);
            var output = mapping.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst();
            String url = "jdbc:h2:mem:validation_mongo_"+UUID.randomUUID().toString().replace("-","")+";MODE=Oracle;DB_CLOSE_DELAY=-1";
            try (var connection = DriverManager.getConnection(url)) {
                try (var sql = connection.createStatement()) {
                    sql.execute("CREATE TABLE ODS_VALIDATION (\"_id\" VARCHAR2(24) PRIMARY KEY, ID VARCHAR2(40), \"@timestamp\" CLOB, APPLICATION_ID VARCHAR2(64))");
                }
                var sink = TestRunners.newTestRunner(PutDatabaseRecord.class);
                var pool = new BinaryLobCompatibilityTest.Pool(url); sink.addControllerService("pool",pool); sink.enableControllerService(pool);
                var reader = new JsonTreeReader(); sink.addControllerService("reader",reader);
                sink.setProperty(reader,"Schema Access Strategy","infer-schema"); sink.enableControllerService(reader);
                sink.setProperty("Database Connection Pooling Service","pool"); sink.setProperty("Record Reader","reader");
                sink.setProperty("Statement Type","INSERT"); sink.setProperty("Table Name","ODS_VALIDATION"); sink.setProperty("Database Type","Generic");
                sink.setProperty("Quote Column Identifiers","true"); sink.setProperty("Translate Field Names","false");
                sink.enqueue(output.toByteArray(),output.getAttributes()); sink.run();
                sink.assertTransferCount("failure",0); sink.assertTransferCount("retry",0); sink.assertTransferCount("success",1);
                try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT \"_id\",ID,\"@timestamp\",APPLICATION_ID FROM ODS_VALIDATION ORDER BY \"_id\"")) {
                    int read=0;
                    while (rows.next()) {
                        assertEquals(String.format("%024x",read),rows.getString(1)); assertEquals("business-"+read,rows.getString(2));
                        assertEquals("2026-11-22T00:00:00Z",rows.getString(3)); assertEquals("validation",rows.getString(4)); read++;
                    }
                    assertEquals(count,read);
                }
            }
        }
    }
}
