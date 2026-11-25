package com.linewell.dataelement.integration.nifi.canvas.compile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ComponentManifest;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class DslCompilerJdbcWriterTest {

    @Test
    void exactReaderAvailabilityIsCheckedOnceForTwentyAndHundredNativeJdbcTargets() {
        for (int count : List.of(20, 100)) {
            var nifi = mockNifi();
            var nodes = java.util.stream.IntStream.range(0, count).mapToObj(i -> new Pipeline.Node(
                    "validation-decimal-" + i, "sink.jdbc", "精确目标", "sink", 0, 0,
                    Map.<String,Object>of("dbType", "MYSQL", "writerType", "NIFI_PUT_DATABASE_RECORD",
                            "targetColumns", List.of(Map.of("columnName", "amount", "columnType", "DECIMAL(20,6)"))))).toList();
            new DslCompiler(nifi, mock(ManifestRegistry.class), mock(FieldMappingService.class), mock(HiveModule.class))
                    .preflightWriters(new Pipeline("validation-exact", "精确字段", null, null, null,
                            new Pipeline.Dsl(1, nodes, List.of()), null, null, null, null, null, null, null));
            verify(nifi, times(1)).get(eq("/flow/controller-service-types"), eq(com.fasterxml.jackson.databind.JsonNode.class));
            verify(nifi, never()).createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble());
        }
    }

    @Test
    void bothJdbcWritersQuoteOracleUidInItsActualRegisteredCase() throws Exception {
        for (String writer : List.of("LINEWELL_PUT_DATABASE_RECORD", "NIFI_PUT_DATABASE_RECORD")) {
            for (String name : List.of("uid", "UID", "1231313")) {
                var nifi = mockNifi();
                when(nifi.findProcessorType("LinewellPutDatabaseRecord")).thenReturn(java.util.Optional.of("com.linewell.microservice.processors.database.LinewellPutDatabaseRecord"));
                var config = new LinkedHashMap<String,Object>(oracleConfig("Oracle"));
                config.put("writerType", writer); config.put("statementType", "INSERT");
                config.put("targetColumns", List.of(Map.of("columnName", name, "dataType", "VARCHAR2"),
                        Map.of("columnName", "ID", "dataType", "NUMBER")));
                compile(nifi, config);
                ArgumentCaptor<Map<String,String>> properties = ArgumentCaptor.captor();
                verify(nifi).createProcessor(eq("pg"), anyString(), eq("目标表/put"), anyDouble(), anyDouble(), properties.capture(), nullable(String.class), nullable(String.class));
                org.assertj.core.api.Assertions.assertThat(properties.getValue())
                        .containsEntry(writer.startsWith("LINEWELL") ? "put-db-record-quoted-identifiers" : "Quote Column Identifiers", "true");
                org.assertj.core.api.Assertions.assertThat(config.get("targetColumns")).isEqualTo(List.of(
                        Map.of("columnName", name, "dataType", "VARCHAR2"), Map.of("columnName", "ID", "dataType", "NUMBER")));
            }
        }
    }

    @Test
    void bothJdbcWritersQuoteActualMongoNamesAndKeepUnderscoreKeysDistinct() throws Exception {
        for (String writer : List.of("LINEWELL_PUT_DATABASE_RECORD", "NIFI_PUT_DATABASE_RECORD")) {
            var nifi = mockNifi();
            when(nifi.findProcessorType("LinewellPutDatabaseRecord")).thenReturn(java.util.Optional.of("com.linewell.microservice.processors.database.LinewellPutDatabaseRecord"));
            var config = new LinkedHashMap<String,Object>(oracleConfig("Oracle"));
            config.put("writerType", writer); config.put("statementType", "INSERT");
            config.put("targetColumns", List.of(Map.of("columnName","_id","dataType","VARCHAR2","primaryKey",true),
                    Map.of("columnName","ID","dataType","VARCHAR2"), Map.of("columnName","@timestamp","dataType","CLOB"),
                    Map.of("columnName","APPLICATION_ID","dataType","VARCHAR2")));
            compile(nifi,config);
            ArgumentCaptor<Map<String,String>> properties = ArgumentCaptor.captor();
            verify(nifi).createProcessor(eq("pg"), anyString(), eq("目标表/put"), anyDouble(), anyDouble(), properties.capture(), nullable(String.class), nullable(String.class));
            boolean linewell = writer.startsWith("LINEWELL");
            org.assertj.core.api.Assertions.assertThat(properties.getValue())
                    .containsEntry(linewell?"put-db-record-quoted-identifiers":"Quote Column Identifiers","true")
                    .containsEntry(linewell?"put-db-record-translate-field-names":"Translate Field Names","false");
        }
    }

    @Test
    void writerPreflightChecksTwentyAndHundredTargetsOnceAndSkipsExplicitNativeWriters() {
        for (int size : List.of(20, 100)) {
            var nifi = mock(NifiClient.class);
            var compiler = new DslCompiler(nifi, mock(ManifestRegistry.class), mock(FieldMappingService.class), mock(HiveModule.class));
            var nodes = java.util.stream.IntStream.range(0, size).mapToObj(i -> new Pipeline.Node(
                    "validation-sink-" + i, "sink.jdbc", "验证目标", "sink", 0, 0, Map.<String,Object>of("dbType", "Oracle"))).toList();
            compiler.preflightWriters(new Pipeline("validation", "验证流程", null, null, null,
                    new Pipeline.Dsl(1, nodes, List.of()), null, null, null, null, null, null, null));
            verify(nifi, times(1)).requireLinewellDatabaseRecordExtension();
            verifyNoMoreInteractions(nifi);
            clearInvocations(nifi);
            var nativeNodes = nodes.stream().map(node -> new Pipeline.Node(node.id(), node.manifestKey(), node.label(),
                    node.category(), node.x(), node.y(), Map.<String,Object>of("dbType", "Oracle", "writerType", "NIFI_PUT_DATABASE_RECORD"))).toList();
            compiler.preflightWriters(new Pipeline("validation", "验证流程", null, null, null,
                    new Pipeline.Dsl(1, nativeNodes, List.of()), null, null, null, null, null, null, null));
            verifyNoInteractions(nifi);
        }
    }

    @Test
    void jdbcSinkAcceptsExistingRecordBatchesWithoutAnotherMergeProcessor() throws Exception {
        NifiClient nifi = mockNifi();

        compile(nifi, Map.of("jdbcUrl", "jdbc:mysql://db:3306/demo", "username", "writer",
                "password", "secret", "table", "target_table"));

        ArgumentCaptor<String> types = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> names = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi, times(1)).createProcessor(eq("pg"), types.capture(), names.capture(),
                anyDouble(), anyDouble(), properties.capture(), nullable(String.class), nullable(String.class));

        org.assertj.core.api.Assertions.assertThat(types.getAllValues()).containsExactly(
                "org.apache.nifi.processors.standard.PutDatabaseRecord");
        org.assertj.core.api.Assertions.assertThat(names.getAllValues()).containsExactly("目标表/put");
        org.assertj.core.api.Assertions.assertThat(properties.getAllValues().getFirst())
                .containsEntry("Record Reader", "global-json-reader")
                .doesNotContainKey("Translate Field Names");
        verify(nifi, never()).createConnection(anyString(), anyString(), anyString(), anyString(),
                anyString(), eq(List.of("merged")));
    }

    @Test
    void temporalNormalizationConnectsDirectlyToTheWriter() throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        AtomicInteger sequence = new AtomicInteger();
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        HiveServiceFixtures.stubGlobals(nifi);
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap()))
                .thenAnswer(invocation -> entity("cs-" + sequence.incrementAndGet()));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class)))
                .thenAnswer(invocation -> entity("processor-" + sequence.incrementAndGet()));
        when(nifi.createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenAnswer(invocation -> entity("connection-" + sequence.incrementAndGet()));

        ManifestRegistry registry = mock(ManifestRegistry.class);
        when(registry.get("source.oracle")).thenReturn(manifest("/manifests/sources/oracle.json"));
        when(registry.get("sink.jdbc")).thenReturn(manifest("/manifests/sinks/jdbc-sink.json"));
        Map<String, Object> sourceConfig = new LinkedHashMap<>(Map.of(
                "host", "db", "port", "1521", "sid", "ORCL", "username", "reader", "password", "secret",
                "table", "SOURCE_TABLE", "incrementalColumn", "EVENT_TIME", "sourceColumns", List.of(
                        Map.of("columnName", "ID", "dataType", "NUMBER"),
                        Map.of("columnName", "_ID", "dataType", "CHAR"),
                        Map.of("columnName", "EVENT_TIME", "dataType", "DATE"))));
        Map<String, Object> targetConfig = new LinkedHashMap<>(Map.of(
                "dbType", "MySQL", "jdbcUrl", "jdbc:mysql://db:3306/ods", "username", "writer",
                "password", "secret", "table", "TARGET_TABLE", "targetColumns", List.of(
                        Map.of("columnName", "ID", "dataType", "BIGINT"),
                        Map.of("columnName", "_ID", "dataType", "CHAR"),
                        Map.of("columnName", "EVENT_TIME", "dataType", "TIMESTAMP"))));
        Pipeline pipeline = new Pipeline("test", "Temporal JDBC", null, null, null,
                new Pipeline.Dsl(1, List.of(
                        new Pipeline.Node("source", "source.oracle", "来源表", "source", 0, 0, sourceConfig),
                        new Pipeline.Node("sink", "sink.jdbc", "目标表", "sink", 0, 0, targetConfig)),
                        List.of(new Pipeline.Edge("edge", "source", "sink", null))),
                null, null, null, null, null, null, null);

        var result = new DslCompiler(nifi, registry, mock(FieldMappingService.class), mock(HiveModule.class)).compile(pipeline);

        verify(nifi).createProcessor(eq("pg"), eq("org.apache.nifi.processors.standard.QueryRecord"),
                eq("目标表/normalize"), anyDouble(), anyDouble(), anyMap(), nullable(String.class), nullable(String.class));
        verify(nifi, never()).createProcessor(eq("pg"), eq("org.apache.nifi.processors.standard.MergeRecord"),
                anyString(), anyDouble(), anyDouble(), anyMap(), nullable(String.class), nullable(String.class));
        verify(nifi).createProcessor(eq("pg"), eq("org.apache.nifi.processors.standard.PutDatabaseRecord"),
                eq("目标表/put"), anyDouble(), anyDouble(), argThat(properties -> "false".equals(properties.get("Translate Field Names"))),
                nullable(String.class), nullable(String.class));
        var sink = result.nodes().get("sink");
        verify(nifi).createConnection("pg", sink.processorIds.get("normalize"), "PROCESSOR",
                sink.processorIds.get("put"), "PROCESSOR", List.of("success"));
    }

    @Test
    void defaultsToNativeWriterForNonOracleTarget() throws Exception {
        NifiClient nifi = mockNifi();

        compile(nifi, Map.of("jdbcUrl", "jdbc:mysql://db:3306/demo", "username", "writer",
                "password", "secret", "table", "target_table"));

        verify(nifi).createProcessor(eq("pg"), eq("org.apache.nifi.processors.standard.PutDatabaseRecord"),
                eq("目标表/put"), anyDouble(), anyDouble(), anyMap(), nullable(String.class), nullable(String.class));
        verify(nifi, never()).findProcessorType("LinewellPutDatabaseRecord");
    }

    @Test
    void defaultsOracleAndOceanBaseOracleToLinewellMerge() throws Exception {
        NifiClient nifi = mockNifi();
        when(nifi.findProcessorType("LinewellPutDatabaseRecord"))
                .thenReturn(java.util.Optional.of("com.linewell.microservice.processors.database.LinewellPutDatabaseRecord"));

        compile(nifi, oracleConfig("Oracle"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createProcessor(eq("pg"),
                eq("com.linewell.microservice.processors.database.LinewellPutDatabaseRecord"),
                eq("目标表/put"), anyDouble(), anyDouble(), properties.capture(), nullable(String.class), nullable(String.class));
        org.assertj.core.api.Assertions.assertThat(properties.getValue())
                .containsKeys("put-db-record-record-reader", "put-db-record-dcbp-service", "db-type",
                        "put-db-record-statement-type", "put-db-record-table-name",
                        "put-db-record-update-keys", "merge update keys")
                .containsEntry("db-type", "Oracle")
                .containsEntry("put-db-record-statement-type", "MERGE")
                .containsEntry("put-db-record-update-keys", "ID")
                .containsEntry("merge update keys", "ID")
                .doesNotContainKeys("Record Reader", "Database Connection Pooling Service", "Database Type",
                        "Statement Type", "Table Name");

        clearInvocations(nifi);
        when(nifi.findProcessorType("LinewellPutDatabaseRecord"))
                .thenReturn(java.util.Optional.of("com.linewell.microservice.processors.database.LinewellPutDatabaseRecord"));
        compile(nifi, oracleConfig("OCEANBASE_ORACLE"));
        verify(nifi).createProcessor(eq("pg"),
                eq("com.linewell.microservice.processors.database.LinewellPutDatabaseRecord"),
                eq("目标表/put"), anyDouble(), anyDouble(), anyMap(), nullable(String.class), nullable(String.class));
    }

    @Test
    void permitsNativeWriterWhenExplicitlySelectedForOracle() throws Exception {
        NifiClient nifi = mockNifi();

        compile(nifi, Map.of("jdbcUrl", "jdbc:oracle:thin:@//db:1521/service", "username", "writer",
                "password", "secret", "table", "target_table", "dbType", "Oracle",
                "writerType", "NIFI_PUT_DATABASE_RECORD"));

        verify(nifi).createProcessor(eq("pg"), eq("org.apache.nifi.processors.standard.PutDatabaseRecord"),
                eq("目标表/put"), anyDouble(), anyDouble(), anyMap(), nullable(String.class), nullable(String.class));
        verify(nifi, never()).findProcessorType("LinewellPutDatabaseRecord");
    }

    @Test
    void permitsMergeWhenLinewellWriterIsExplicitlySelectedForNonOracleTarget() throws Exception {
        NifiClient nifi = mockNifi();
        when(nifi.findProcessorType("LinewellPutDatabaseRecord"))
                .thenReturn(java.util.Optional.of("com.linewell.microservice.processors.database.LinewellPutDatabaseRecord"));

        compile(nifi, Map.of("jdbcUrl", "jdbc:mysql://db:3306/demo", "username", "writer",
                "password", "secret", "table", "target_table", "dbType", "MySQL",
                "writerType", "LINEWELL_PUT_DATABASE_RECORD", "statementType", "MERGE",
                "targetColumns", List.of(Map.of("columnName", "ID", "dataType", "BIGINT", "primaryKey", true))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createProcessor(eq("pg"),
                eq("com.linewell.microservice.processors.database.LinewellPutDatabaseRecord"),
                eq("目标表/put"), anyDouble(), anyDouble(), properties.capture(), nullable(String.class), nullable(String.class));
        org.assertj.core.api.Assertions.assertThat(properties.getValue())
                .containsEntry("db-type", "MySQL")
                .containsEntry("put-db-record-statement-type", "MERGE");
    }

    @Test
    void preservesNonMergeWriteModesWhenLinewellWriterIsExplicitlySelected() throws Exception {
        NifiClient nifi = mockNifi();
        when(nifi.findProcessorType("LinewellPutDatabaseRecord"))
                .thenReturn(java.util.Optional.of("com.linewell.microservice.processors.database.LinewellPutDatabaseRecord"));

        compile(nifi, Map.of("jdbcUrl", "jdbc:mysql://db:3306/demo", "username", "writer",
                "password", "secret", "table", "target_table", "dbType", "MySQL",
                "writerType", "LINEWELL_PUT_DATABASE_RECORD", "statementType", "UPSERT",
                "targetColumns", List.of(Map.of("columnName", "ID", "dataType", "BIGINT", "primaryKey", true))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createProcessor(eq("pg"),
                eq("com.linewell.microservice.processors.database.LinewellPutDatabaseRecord"),
                eq("目标表/put"), anyDouble(), anyDouble(), properties.capture(), nullable(String.class), nullable(String.class));
        org.assertj.core.api.Assertions.assertThat(properties.getValue())
                .containsEntry("put-db-record-statement-type", "UPSERT");
    }

    @Test
    void rejectsMergeWhenNativeWriterIsSelected() {
        NifiClient nifi = mockNifi();

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> compile(nifi,
                Map.of("jdbcUrl", "jdbc:oracle:thin:@//db:1521/service", "username", "writer",
                        "password", "secret", "table", "TARGET_TABLE", "writerType", "NIFI_PUT_DATABASE_RECORD",
                        "statementType", "MERGE")));

        org.assertj.core.api.Assertions.assertThat(error.getMessage()).contains("MERGE").contains("LinewellPutDatabaseRecord");
    }

    private static NifiClient mockNifi() {
        NifiClient nifi = mock(NifiClient.class);
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        HiveServiceFixtures.stubGlobals(nifi);
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap())).thenReturn(entity("cs"));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class))).thenReturn(entity("put"));
        return nifi;
    }

    private static void compile(NifiClient nifi, Map<String, Object> config) throws Exception {
        ComponentManifest manifest = manifest("/manifests/sinks/jdbc-sink.json");
        ManifestRegistry registry = mock(ManifestRegistry.class);
        when(registry.get("sink.jdbc")).thenReturn(manifest);
        Pipeline pipeline = new Pipeline("test", "JDBC", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("sink", "sink.jdbc", "目标表", "sink", 0, 0, config)), List.of()),
                null, null, null, null, null, null, null);
        new DslCompiler(nifi, registry, mock(FieldMappingService.class), mock(HiveModule.class)).compile(pipeline);
    }

    private static ComponentManifest manifest(String resource) throws Exception {
        try (var stream = DslCompilerJdbcWriterTest.class.getResourceAsStream(resource)) {
            return new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
    }

    private static Map<String, Object> oracleConfig(String dbType) {
        return Map.of(
                "jdbcUrl", "jdbc:oracle:thin:@//db:1521/service",
                "username", "writer",
                "password", "secret",
                "table", "TARGET_TABLE",
                "dbType", dbType,
                "targetColumns", List.of(Map.of("columnName", "ID", "dataType", "NUMBER", "primaryKey", true)));
    }

    private static NifiEntity entity(String id) {
        return new NifiEntity(null, Map.of("id", id), null, null);
    }
}
