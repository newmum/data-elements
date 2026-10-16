package com.linewell.dataelement.nifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.mapping.core.*;
import org.apache.nifi.serialization.SimpleRecordSchema;
import org.apache.nifi.serialization.record.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MappingErrorRecordsTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    @Test void envelopeRoundTripPreservesNativeTemporalPrecisionBinaryAndNestedValues() throws Exception {
        var plan=MappingEngine.compile(JSON.readTree("{\"version\":\"1.0\",\"mappings\":[]}"));
        var schema=new SimpleRecordSchema(List.of(
                new RecordField("at",RecordFieldType.TIMESTAMP.getDataType()),
                new RecordField("decimal",RecordFieldType.DECIMAL.getDecimalDataType(20,6))));
        var timestamp=java.sql.Timestamp.valueOf("2026-07-01 11:20:21.123456789");
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("at",timestamp);row.put("decimal",new java.math.BigDecimal("1.230001"));
        row.put("nested/escaped~key",List.of(LocalDate.of(1947,4,15),LocalDateTime.of(2026,7,1,11,20,21),new byte[]{1,2,3}));
        row.put("offset",OffsetDateTime.parse("2026-07-01T11:20:21.123456+08:00"));
        Map<String,Object> envelope=new LinkedHashMap<>(Map.of("planHash",plan.hash(),"inputRecordIndex",56,"errors",List.of()));
        MappingErrorRecords.snapshot(envelope,row);
        var attributes=Map.of(MappingErrorRecords.SCHEMA,MappingErrorRecords.schemaText(schema));
        try(var reader=new MappingErrorRecords(new ByteArrayInputStream(JSON.writeValueAsBytes(envelope)),attributes,plan)) {
            var restored=reader.nextRecord().toMap();
            assertEquals(56,reader.originalIndex());assertEquals(timestamp,restored.get("at"));
            assertEquals(row.get("decimal"),restored.get("decimal"));assertEquals(row.get("offset"),restored.get("offset"));
            var nested=(List<?>)restored.get("nested/escaped~key");
            assertEquals(LocalDate.of(1947,4,15),nested.get(0));assertEquals(((List<?>)row.get("nested/escaped~key")).get(1),nested.get(1));
            assertArrayEquals(new byte[]{1,2,3},(byte[])nested.get(2));
            assertEquals(RecordFieldType.TIMESTAMP,reader.getSchema().getField("at").orElseThrow().getDataType().getFieldType());
            assertEquals(RecordFieldType.DECIMAL,reader.getSchema().getField("decimal").orElseThrow().getDataType().getFieldType());
            assertNull(reader.nextRecord());
        }
    }
    @Test void wrongPlanOrNonEnvelopeContentIsKeptAsFailureRatherThanBusinessData() throws Exception {
        var plan=MappingEngine.compile(JSON.readTree("{\"version\":\"1.0\",\"mappings\":[]}"));
        String content="{\"planHash\":\"wrong\",\"inputRecordIndex\":0,\"errors\":[],\"record\":{}}";
        assertEquals("INVALID_ERROR_ENVELOPE",assertThrows(MappingException.class,()->new MappingErrorRecords(new ByteArrayInputStream(content.getBytes()),Map.of(),plan)).code());
        assertFalse(MappingErrorRecords.isEnvelope(Map.of("mime.type","application/x-ndjson"),"node"));
    }
}
