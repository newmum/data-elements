package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.nifi.processors.standard.QueryRecord;
import org.apache.nifi.serialization.DateTimeUtils;
import org.apache.nifi.serialization.record.RecordFieldType;
import org.apache.nifi.serialization.record.util.DataTypeUtils;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HaitongMongoRecordReaderTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void keepsEmbeddedKeyPresenceAndJsonScalarTypesAtMongoWriterBoundary() throws Exception {
        for (int size : List.of(20, 100)) {
            var records = JSON.createArrayNode();
            for (int index = 0; index < size; index++) {
                var root = records.addObject().put("id", index);
                root.putNull("nullable");
                var rules = root.putArray("rules");
                var encoded = rules.addObject().put("code", "007");
                encoded.putObject("details").put("active", true);
                encoded.putArray("values").addNull().add(2).add("2");
                var numeric = rules.addObject().put("number", 2_147_483_648L).put("rate", 1.5);
                numeric.putNull("details");
                if (index % 2 == 0) numeric.put("active", false);
                rules.addObject();
            }
            verify(records);
        }
    }

    @Test
    void usesTheSelectedTimestampFormatForRootFields() throws Exception {
        var runner = TestRunners.newTestRunner(QueryRecord.class);
        var reader = new HaitongMongoRecordReader();
        runner.addControllerService("reader", reader);
        runner.setProperty(reader, DateTimeUtils.TIMESTAMP_FORMAT, "yyyy-MM-dd HH:mm:ss");
        runner.setProperty(reader, JsonWireOptions.ALLOW_COMMENTS, "true");
        runner.enableControllerService(reader);
        byte[] payload = "[/* record */{\"recorded\":\"2026-11-24 09:22:13\"}]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (var records = reader.createRecordReader(Map.of(), new ByteArrayInputStream(payload), payload.length, runner.getLogger())) {
            assertInstanceOf(java.sql.Timestamp.class, records.nextRecord().getValue("recorded"));
            assertNull(records.nextRecord());
        } finally {
            runner.shutdown();
        }
    }

    private static void verify(JsonNode original) throws Exception {
        var runner = TestRunners.newTestRunner(QueryRecord.class);
        var reader = new HaitongMongoRecordReader();
        runner.addControllerService("reader", reader);
        runner.enableControllerService(reader);
        byte[] payload = JSON.writeValueAsBytes(original);
        try (var records = reader.createRecordReader(Map.of(), new ByteArrayInputStream(payload), payload.length, runner.getLogger())) {
            for (JsonNode expected : original) {
                var record = records.nextRecord();
                assertNotNull(record);
                // NiFi PutMongoRecord uses this conversion immediately before building its BSON document.
                var document = DataTypeUtils.convertRecordFieldtoObject(record, RecordFieldType.RECORD.getRecordDataType(record.getSchema()));
                assertEquals(expected, JSON.valueToTree(document));
            }
            assertNull(records.nextRecord());
        } finally {
            runner.shutdown();
        }
    }
}
