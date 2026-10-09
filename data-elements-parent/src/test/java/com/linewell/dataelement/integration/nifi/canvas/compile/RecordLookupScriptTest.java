package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import java.io.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.io.StreamCallback;
import org.apache.nifi.serialization.*;
import org.apache.nifi.serialization.record.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecordLookupScriptTest {
    @ParameterizedTest
    @ValueSource(ints = {20, 100, 201})
    void actualNifiScriptUsesOneMetadataQueryAndOneQueryPerBoundedBatch(int size) throws Exception {
        try (Connection raw = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL")) {
            raw.createStatement().execute("create table dictionary(code int, label varchar(100), enabled int)");
            raw.createStatement().execute("insert into dictionary values(1,'一',1),(2,'二',1),(3,'禁用',0)");
            AtomicInteger reads = new AtomicInteger();
            Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")) {
                            reads.incrementAndGet();
                            assertThat((String) args[0]).doesNotContain("JSON_TABLE", "GROUP_CONCAT", "DUAL", "bad");
                        }
                        return method.invoke(raw, args);
                    });
            var rule = Map.of("from", "/codes", "to", "/labels", "lookup", Map.of("multiValue", true,
                    "query", "SELECT code, label FROM dictionary WHERE enabled = 1 AND code IN (:codes)",
                    "dataSource", Map.of("dbType", "POSTGRESQL")));
            var plan = new FieldMappingService().compilePlan(Map.of("version", "1.0", "mappings", List.of(rule)));
            var properties = RecordLookupScript.properties(plan.lookups(), "reader", "writer", ignored -> "dbcp");
            List<String> inputs = Collections.nCopies(size, "1,bad,2,1,3");
            var output = execute(properties.get("Script Body"), inputs, Map.of("dictionary0", Map.of("connection", connection)), false);
            assertThat(output).hasSize(size).allSatisfy(record -> assertThat(record.getValue("labels")).isEqualTo("一,bad,二,一,3"));
            assertThat(reads.get()).isEqualTo(1 + (size + 99) / 100);
        }
    }

    @Test
    void inlineStandardUsesNoConnectionAndPreservesNullUnknownAndDuplicates() throws Exception {
        var spec = Map.of("version", "1.0", "mappings", List.of(Map.of("from", "/codes", "to", "/labels",
                "lookup", Map.of("multiValue", true, "multiValueSeparator", "|", "values", Map.of("U", "未知", "F", "女'性")))));
        var properties = RecordLookupScript.properties(new FieldMappingService().compilePlan(spec).lookups(), "reader", "writer",
                ignored -> { throw new AssertionError("An inline standard must not create a database service"); });
        assertThat(properties.keySet()).noneMatch(key -> key.startsWith("SQL."));
        var output = execute(properties.get("Script Body"), Arrays.asList(" U |X|F|U||", "X|Y", "", null), Map.of(), false);
        assertThat(output.getFirst().getValue("labels")).isEqualTo("未知|X|女'性|未知");
        assertThat(output.subList(1, 4)).allSatisfy(record -> assertThat(record.getValue("labels")).isNull());
    }

    @Test
    void dictionaryFailureRetainsFlowFileInFailureRelationship() throws Exception {
        var spec = Map.of("version", "1.0", "mappings", List.of(Map.of("from", "/codes", "to", "/labels",
                "lookup", Map.of("multiValue", true, "query", "SELECT code,label FROM dictionary WHERE code IN (:codes)"))));
        var properties = RecordLookupScript.properties(new FieldMappingService().compilePlan(spec).lookups(), "reader", "writer", ignored -> "dbcp");
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("connection unavailable"));
        assertThat(execute(properties.get("Script Body"), List.of("U,F"), Map.of("dictionary0", Map.of("connection", connection)), true)).isEmpty();
    }

    private List<org.apache.nifi.serialization.record.Record> execute(String script, List<String> codes,
                                                                     Map<String, Object> sql, boolean failure) throws Exception {
        FlowFile flowFile = mock(FlowFile.class);
        when(flowFile.getAttributes()).thenReturn(Map.of());
        ProcessSession session = mock(ProcessSession.class);
        when(session.get()).thenReturn(flowFile);
        when(session.putAllAttributes(eq(flowFile), anyMap())).thenReturn(flowFile);
        when(session.putAttribute(eq(flowFile), anyString(), anyString())).thenReturn(flowFile);
        when(session.write(eq(flowFile), any(StreamCallback.class))).thenAnswer(invocation -> {
            ((StreamCallback) invocation.getArgument(1)).process(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
            return flowFile;
        });
        RecordSchema schema = new SimpleRecordSchema(List.of(new RecordField("__lookup_0_0", RecordFieldType.STRING.getDataType())));
        RecordReader reader = mock(RecordReader.class);
        when(reader.getSchema()).thenReturn(schema);
        AtomicInteger cursor = new AtomicInteger();
        when(reader.nextRecord()).thenAnswer(ignored -> {
            int index = cursor.getAndIncrement();
            if (index >= codes.size()) return null;
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("__lookup_0_0", codes.get(index));
            return new MapRecord(schema, data);
        });
        RecordReaderFactory readers = mock(RecordReaderFactory.class);
        when(readers.createRecordReader(anyMap(), any(InputStream.class), anyLong(), any(ComponentLog.class))).thenReturn(reader);
        RecordSetWriter writer = mock(RecordSetWriter.class);
        RecordSetWriterFactory writers = mock(RecordSetWriterFactory.class);
        when(writers.createWriter(any(ComponentLog.class), any(RecordSchema.class), any(OutputStream.class), anyMap())).thenReturn(writer);
        List<org.apache.nifi.serialization.record.Record> output = new ArrayList<>();
        when(writer.write(any(org.apache.nifi.serialization.record.Record.class))).thenAnswer(invocation -> {
            output.add(invocation.getArgument(0)); return null;
        });
        Relationship success = new Relationship.Builder().name("success").build();
        Relationship failed = new Relationship.Builder().name("failure").build();
        ComponentLog log = mock(ComponentLog.class);
        var bindings = new Binding(Map.of("session", session, "log", log,
                "RecordReader", Map.of("input", readers), "RecordWriter", Map.of("output", writers),
                "SQL", sql, "REL_SUCCESS", success, "REL_FAILURE", failed));
        new GroovyShell(bindings).evaluate(script);
        verify(session).transfer(flowFile, failure ? failed : success);
        verify(reader).close();
        verify(writer).close();
        if (failure) verify(session).putAttribute(eq(flowFile), eq("multi.value.error"), anyString());
        return output;
    }
}
