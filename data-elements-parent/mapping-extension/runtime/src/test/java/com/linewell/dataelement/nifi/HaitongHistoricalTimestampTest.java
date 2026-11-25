package com.linewell.dataelement.nifi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.nifi.avro.AvroReader;
import org.apache.nifi.json.JsonRecordSetWriter;
import org.apache.nifi.json.JsonTreeReader;
import org.apache.nifi.serialization.RecordReaderFactory;
import org.apache.nifi.util.TestRunner;
import org.apache.nifi.util.TestRunners;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HaitongHistoricalTimestampTest {
    @Test void registeredIsoSqlDateTextPreservesCalendarAcrossReaderModesJvmsAliasesAndWindows()throws Exception {
        TimeZone previous=TimeZone.getDefault();
        try {
            for(String zone:List.of("UTC","Asia/Shanghai","Pacific/Kiritimati","America/Los_Angeles")){
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                for(boolean explicit:List.of(false,true))for(boolean aliased:List.of(false,true))for(int count:List.of(20,100)){
                    var runner=registeredDateRunner(explicit,aliased);String field=aliased?"_ht_0123456789abcdef":"day";var rows=new ArrayList<Map<String,Object>>();
                    for(int i=0;i<count;i++)rows.add(java.util.Collections.singletonMap(field,i%4==0?null:i%4==1?"1900-01-01":i%4==2?"1970-01-01":"2024-02-29"));
                    try{
                        runner.enqueue(JSON.writeValueAsBytes(rows));runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,1);runner.assertTransferCount(HaitongTransformRecord.FAILURE,0);runner.assertTransferCount(HaitongTransformRecord.INVALID,0);
                        var output=JSON.readTree(runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS).getFirst().toByteArray());assertEquals(count,output.size());
                        for(int i=0;i<count;i++)if(rows.get(i).get(field)==null)assertTrue(output.get(i).path("day").isNull());else assertEquals(rows.get(i).get(field),output.get(i).path("day").asText(),zone+", explicit="+explicit+", alias="+aliased);
                    }finally{runner.shutdown();}
                }
            }
        }finally{TimeZone.setDefault(previous);}
    }
    @Test void invalidOrDuplicateDatesAfterTheWindowBoundaryKeepTheEntireOriginalInput()throws Exception {
        for(String last:List.of("{\"day\":\"2024-02-30\"}","{\"day\":\"1900-01-01\",\"day\":\"2000-01-01\"}")){
            var runner=registeredDateRunner(false,false);String input="["+"{\"day\":\"1900-01-01\"},".repeat(513)+last+"]";
            try{runner.enqueue(input);runner.run();runner.assertTransferCount(HaitongTransformRecord.SUCCESS,0);runner.assertTransferCount(HaitongTransformRecord.FAILURE,1);runner.getFlowFilesForRelationship(HaitongTransformRecord.FAILURE).getFirst().assertContentEquals(input);}finally{runner.shutdown();}
        }
    }
    private TestRunner registeredDateRunner(boolean explicit,boolean aliased)throws Exception {
        var spec=JSON.readTree("""
          {"schemaVersion":"2.0","policies":{"zoneId":"Asia/Shanghai","recordErrorMode":"FAIL_FLOWFILE"},
           "schemaContract":{"source":[{"name":"day","logicalType":"DATE","physicalType":"DATE","registeredFormat":"DATE","nullable":true}]},
           "rules":[{"input":{"kind":"FIELD","name":"day"},"target":{"name":"day","logicalType":"DATE","nullable":true},"steps":[]}]}
          """);
        var runner=TestRunners.newTestRunner(HaitongTransformRecord.class);var reader=new JsonTreeReader();var writer=new JsonRecordSetWriter();
        runner.addControllerService("reader",reader);runner.setProperty(reader,"Schema Access Strategy","infer-schema");if(explicit)runner.setProperty(reader,"Date Format","yyyy-MM-dd");runner.enableControllerService(reader);
        runner.addControllerService("writer",writer);runner.setProperty(writer,"Schema Access Strategy","inherit-record-schema");runner.setProperty(writer,"Date Format","yyyy-MM-dd");runner.enableControllerService(writer);
        runner.setProperty(HaitongTransformRecord.READER,"reader");runner.setProperty(HaitongTransformRecord.WRITER,"writer");runner.setProperty(HaitongTransformRecord.PLAN,com.linewell.dataelement.mapping.core.MappingEngine.compile(spec).document().toString());
        runner.setProperty(HaitongTransformRecord.TIMESTAMP_TEXT,"true");runner.setProperty(HaitongTransformRecord.EXACT_JSON_NUMBERS,"true");if(aliased)runner.setProperty(HaitongTransformRecord.JDBC_ALIASES,"{\"day\":\"_ht_0123456789abcdef\"}");return runner;
    }
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PATTERN = "yyyy-MM-dd HH:mm:ss.SSSSSSSSS";
    private static final DateTimeFormatter WIRE = DateTimeFormatter.ofPattern(PATTERN);
    private static final String TYPED_JSON_SCHEMA = """
            {"type":"record","name":"validation_wall_clock","fields":[
              {"name":"stamp","type":["null",{"type":"long","logicalType":"timestamp-millis"}]},
              {"name":"day","type":"string"},{"name":"time","type":"string"},{"name":"label","type":"string"}]}
            """;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void originalStyleJsonAndExplicitTimestampReaderKeepHistoricalWallTime(boolean dynamic) throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            for (String zoneName : List.of("UTC", "Asia/Shanghai", "America/New_York")) {
                ZoneId zone = ZoneId.of(zoneName);
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                var dates = List.of(LocalDateTime.of(1500, 6, 10, 10, 21, 45, 123456789),
                        LocalDateTime.of(1890, 5, 5, 14, 27, 49, 987654321),
                        LocalDateTime.of(2001, 6, 10, 10, 21, 45, 123456789));
                List<Map<String, Object>> records = new ArrayList<>();
                for (var date : dates) {
                    records.add(row(WIRE.format(date)));
                }
                records.add(row(null));
                for (var readerMode : List.of("infer-text", "infer-timestamp", "explicit-schema")) {
                    var reader = new JsonTreeReader();
                    var runner = runner(reader, PATTERN, zone, dynamic, false);
                    if (!readerMode.equals("infer-text")) {
                        runner.disableControllerService(reader);
                        runner.setProperty(reader, "Timestamp Format", PATTERN);
                        if (readerMode.equals("explicit-schema")) {
                            runner.setProperty(reader, "Schema Access Strategy", "schema-text-property");
                            runner.setProperty(reader, "Schema Text", TYPED_JSON_SCHEMA);
                            runner.setProperty(HaitongTransformRecord.TYPED_READER, "reader");
                        }
                        runner.enableControllerService(reader);
                    }
                    var output = run(runner, JSON.writeValueAsBytes(records));
                    assertEquals(4, output.size());
                    for (int index = 0; index < dates.size(); index++) {
                        assertEquals(WIRE.format(dates.get(index)), output.get(index).path("stamp").asText(),
                                zoneName + ", " + readerMode);
                        assertEquals("1890-05-05", output.get(index).path("day").asText());
                        assertEquals("14:27:49", output.get(index).path("time").asText());
                    }
                    assertTrue(output.get(3).path("stamp").isNull());
                }
            }
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void explicitTextOutputUsesThePlanPolicyAcrossModernDaylightSavingChanges() throws Exception {
        TimeZone previous = TimeZone.getDefault();
        ZoneId zone = ZoneId.of("America/New_York");
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            var dates = List.of(LocalDateTime.of(2024, 3, 10, 2, 31, 22, 123456789),
                    LocalDateTime.of(2024, 11, 3, 1, 20, 15, 987654321));
            var records = dates.stream().map(date -> row(WIRE.format(date))).toList();
            var output = run(runner(new JsonTreeReader(), PATTERN, zone, false, false), JSON.writeValueAsBytes(records));
            for (int index = 0; index < dates.size(); index++) {
                assertEquals(WIRE.format(dates.get(index).atZone(zone).toLocalDateTime()),
                        output.get(index).path("stamp").asText());
            }
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void jdbcNanosecondsAndAvroAbsoluteMillisKeepTheirInstant() throws Exception {
        TimeZone previous = TimeZone.getDefault();
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            Instant jdbc;
            try (var connection = DriverManager.getConnection("jdbc:h2:mem:validation_timestamp");
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT CAST('1890-05-05 14:27:49.123456789+08:00' AS TIMESTAMP(9) WITH TIME ZONE)")) {
                assertTrue(rows.next());
                jdbc = rows.getObject(1, OffsetDateTime.class).toInstant();
            }
            var jdbcCarrier = java.sql.Timestamp.from(jdbc);
            var textCarrier = (java.sql.Timestamp) NifiTemporalValues.writerValue(jdbcCarrier.toInstant(), zone, true);
            assertEquals(jdbc, textCarrier.toInstant());
            assertEquals(123456789, textCarrier.getNanos());
            assertEquals(LocalDateTime.ofInstant(jdbc, zone), textCarrier.toLocalDateTime());

            Instant avro = Instant.ofEpochSecond(jdbc.getEpochSecond(), 123000000);
            byte[] input = avroRecord(avro);
            var formatted = run(runner(new AvroReader(), PATTERN, zone, false, true), input);
            assertEquals(WIRE.format(LocalDateTime.ofInstant(avro, zone)), formatted.getFirst().path("stamp").asText());
            var numeric = run(runner(new AvroReader(), null, zone, false, true), input);
            assertEquals(avro.toEpochMilli(), numeric.getFirst().path("stamp").asLong());
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    private static TestRunner runner(RecordReaderFactory reader, String timestampFormat, ZoneId zone, boolean dynamic,
                                     boolean absolute) throws Exception {
        var runner = TestRunners.newTestRunner(HaitongTransformRecord.class);
        runner.addControllerService("reader", reader);
        if (reader instanceof JsonTreeReader) {
            runner.setProperty(reader, "Schema Access Strategy", "infer-schema");
        }
        runner.enableControllerService(reader);
        var writer = new JsonRecordSetWriter();
        runner.addControllerService("writer", writer);
        runner.setProperty(writer, "Schema Access Strategy", "inherit-record-schema");
        runner.setProperty(writer, "Date Format", "yyyy-MM-dd");
        runner.setProperty(writer, "Time Format", "HH:mm:ss");
        if (timestampFormat != null) {
            runner.setProperty(writer, "Timestamp Format", timestampFormat);
            runner.setProperty(HaitongTransformRecord.TIMESTAMP_TEXT, "true");
        }
        runner.enableControllerService(writer);
        runner.setProperty(HaitongTransformRecord.READER, "reader");
        runner.setProperty(HaitongTransformRecord.WRITER, "writer");
        if (absolute) {
            runner.setProperty(HaitongTransformRecord.TIMESTAMP_INPUT, "ABSOLUTE_INSTANT");
        }
        var spec = JSON.createObjectNode().put("version", "1.0").put("zoneId", zone.getId());
        var mappings = spec.putArray("mappings");
        mappings.addObject().put("from", "/stamp").put("to", "/stamp").put("targetType", absolute ? "INSTANT" : "DATETIME");
        if (!absolute) {
            mappings.addObject().put("from", "/day").put("to", "/day").put("targetType", "DATE");
            mappings.addObject().put("from", "/time").put("to", "/time").put("targetType", "TIME");
        }
        if (dynamic) {
            mappings.addObject().put("from", "/label").put("to", "/label").put("targetType", "ANY");
        }
        runner.setProperty(HaitongTransformRecord.PLAN, spec.toString());
        return runner;
    }

    private static List<JsonNode> run(TestRunner runner, byte[] input) throws Exception {
        try {
            runner.enqueue(input, Map.of("avro.schema", TYPED_JSON_SCHEMA));
            runner.run();
            runner.assertTransferCount(HaitongTransformRecord.INVALID, 0);
            runner.assertTransferCount(HaitongTransformRecord.FAILURE, 0);
            List<JsonNode> rows = new ArrayList<>();
            for (var output : runner.getFlowFilesForRelationship(HaitongTransformRecord.SUCCESS)) {
                JSON.readTree(output.toByteArray()).forEach(rows::add);
            }
            return rows;
        } finally {
            runner.shutdown();
        }
    }

    private static Map<String, Object> row(String timestamp) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("stamp", timestamp);
        row.put("day", "1890-05-05");
        row.put("time", "14:27:49");
        row.put("label", "validation");
        return row;
    }

    private static byte[] avroRecord(Instant instant) throws Exception {
        var schema = new Schema.Parser().parse("""
                {"type":"record","name":"validation_timestamp","fields":[
                  {"name":"stamp","type":{"type":"long","logicalType":"timestamp-millis"}}]}
                """);
        var record = new GenericData.Record(schema);
        record.put("stamp", instant.toEpochMilli());
        var output = new ByteArrayOutputStream();
        try (var writer = new DataFileWriter<>(new GenericDatumWriter<GenericData.Record>(schema))) {
            writer.create(schema, output);
            writer.append(record);
        }
        return output.toByteArray();
    }
}
