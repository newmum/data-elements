package com.linewell.dataelement.integration.nifi.canvas.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ComponentManifest;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class DslCompilerHiveMetadataTest {

    @Test
    void reusesMatchingHiveKerberosAndDbcpServicesInsideTheApplicationScope() throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        HiveModule hive = mock(HiveModule.class);
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/sinks/hive-sink.json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get("sink.hive")).thenReturn(manifest);
        when(hive.resolveRuntimeProfile("default")).thenReturn(hiveRuntimeProfile());

        AtomicInteger sequence = new AtomicInteger();
        List<com.fasterxml.jackson.databind.JsonNode> applicationServices = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble(), anyString()))
                .thenAnswer(invocation -> entity("task-" + sequence.incrementAndGet()));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap()))
                .thenAnswer(invocation -> {
                    String id = "cs-" + sequence.incrementAndGet();
                    if ("application".equals(invocation.getArgument(0))) {
                        applicationServices.add(mapper.valueToTree(Map.of("component", Map.of(
                                "id", id, "type", invocation.getArgument(1), "name", invocation.getArgument(2)))));
                    }
                    return entity(id);
                });
        when(nifi.listControllerServices("application")).thenAnswer(invocation -> new ArrayList<>(applicationServices));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class))).thenAnswer(invocation -> entity("put-" + sequence.incrementAndGet()));
        when(nifi.findProcessorType("PutHwHDFS")).thenReturn(java.util.Optional.of("com.linewell.microservice.processor.hadoop.PutHwHDFS"));
        when(nifi.listControllerServices("root")).thenReturn(List.of(publicCsvWriterService()));

        DslCompiler compiler = new DslCompiler(nifi, registry, mock(FieldMappingService.class), hive);
        PipelineGroupOrganizer.Layout layout = new PipelineGroupOrganizer.Layout("root", "application", "application",
                "任务", "owned", new PipelineGroupOrganizer.Position(0, 0), List.of());
        compiler.compile(hivePipeline("one"), null, layout);
        compiler.compile(hivePipeline("two"), null, layout);

        verify(nifi, times(2)).createControllerService(eq("application"), anyString(), anyString(), anyMap());
        assertThat(applicationServices).hasSize(2);
        assertThat(applicationServices.stream().map(service -> service.path("component").path("name").asText()))
                .allMatch(name -> name.startsWith("共享/"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"analytics", "service_user", ""})
    void deploymentDefaultsToLinewellHiveWriterAndKeepsDatabaseAndTableSeparate(String database) throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        HiveModule hive = mock(HiveModule.class);
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/sinks/hive-sink.json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get("sink.hive")).thenReturn(manifest);
        when(hive.resolveRuntimeProfile("default")).thenReturn(hiveRuntimeProfile());
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble()))
                .thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(entity("cs"));
        AtomicInteger processorSequence = new AtomicInteger();
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class)))
                .thenAnswer(invocation -> entity("processor-" + processorSequence.incrementAndGet()));
        when(nifi.findProcessorType("PutHwHDFS")).thenReturn(java.util.Optional.of("com.linewell.microservice.processor.hadoop.PutHwHDFS"));
        when(nifi.listControllerServices("root")).thenReturn(List.of(publicCsvWriterService()));
        Map<String, Object> config = Map.of("database", database, "table", "EVENT_LOG", "username", "service_user",
                "hiveProfile", "", "clientConfigDir", "/untrusted/client", "keytabPath", "/untrusted/keytab");
        Pipeline pipeline = new Pipeline("test", "Hive sink", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("sink", "sink.hive", "Hive", "sink", 0, 0, config)), List.of()),
                null, null, null, null, null, null, null);

        new DslCompiler(nifi, registry, mock(FieldMappingService.class), hive).compile(pipeline);
        verify(hive).resolveRuntimeProfile("default");

        ArgumentCaptor<String> processorTypes = ArgumentCaptor.forClass(String.class);
        verify(nifi, times(4)).createProcessor(eq("pg"), processorTypes.capture(), anyString(),
                anyDouble(), anyDouble(), anyMap(), nullable(String.class), nullable(String.class));
        assertThat(processorTypes.getAllValues()).containsExactly(
                "org.apache.nifi.processors.standard.MergeRecord",
                "org.apache.nifi.processors.standard.ConvertRecord",
                "org.apache.nifi.processors.attributes.UpdateAttribute",
                "com.linewell.microservice.processor.hadoop.PutHwHDFS");
        verify(nifi).createConnection("pg", "processor-1", "PROCESSOR", "processor-2", "PROCESSOR", List.of("merged"));
        verify(nifi).createConnection("pg", "processor-2", "PROCESSOR", "processor-3", "PROCESSOR", List.of("success"));
        verify(nifi).createConnection("pg", "processor-3", "PROCESSOR", "processor-4", "PROCESSOR", List.of("success"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> convertProperties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createProcessor(eq("pg"), eq("org.apache.nifi.processors.standard.ConvertRecord"),
                eq("Hive/convert"), anyDouble(), anyDouble(), convertProperties.capture(),
                nullable(String.class), nullable(String.class));
        assertThat(convertProperties.getValue()).containsEntry("Record Reader", "cs")
                .containsEntry("Record Writer", "public-csv-writer")
                .containsEntry("Include Zero Record FlowFiles", "false");
        verify(nifi).createProcessor(eq("pg"), eq("org.apache.nifi.processors.attributes.UpdateAttribute"),
                eq("Hive/filename"), anyDouble(), anyDouble(), eq(Map.of("filename", "${uuid}")),
                nullable(String.class), nullable(String.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createProcessor(eq("pg"), eq("com.linewell.microservice.processor.hadoop.PutHwHDFS"),
                eq("Hive/put"), anyDouble(), anyDouble(), properties.capture(), nullable(String.class), nullable(String.class));
        String expectedDatabase = database.isBlank() ? "default" : database;
        String expectedDirectory = "default".equals(expectedDatabase)
                ? "/user/hive/warehouse/EVENT_LOG"
                : "/user/hive/warehouse/" + expectedDatabase + ".db/EVENT_LOG";
        assertThat(properties.getValue()).containsEntry("Hadoop Configuration Resources",
                        "/opt/mrs/client/core-site.xml,/opt/mrs/client/hdfs-site.xml")
                .containsEntry("Directory", expectedDirectory)
                .containsEntry("Kerberos Principal", "service_user")
                .containsEntry("Kerberos Keytab", "/opt/mrs/client/user.keytab")
                .containsEntry("krb5 conf", "/opt/mrs/client/krb5.conf")
                .containsEntry("Conflict Resolution Strategy", "replace")
                .containsEntry("Kerberos Relogin Period", "4 hours")
                .doesNotContainKeys("Hive Database", "Target Table", "Maximum Rows Per Statement",
                        "Database Type", "Schema Name", "Table Name");
        assertThat(config).containsEntry("database", database).containsEntry("table", "EVENT_LOG");
    }

    @Test
    void deploymentUsesHiveRecordPutWhenStandardWriteIsSelected() throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        HiveModule hive = mock(HiveModule.class);
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/sinks/hive-sink.json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get("sink.hive")).thenReturn(manifest);
        when(hive.resolveRuntimeProfile("default")).thenReturn(hiveRuntimeProfile());
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap())).thenReturn(entity("cs"));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class))).thenReturn(entity("put"));

        Pipeline pipeline = new Pipeline("test", "Hive sink", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("sink", "sink.hive", "Hive", "sink", 0, 0,
                        Map.of("database", "analytics", "table", "EVENT_LOG", "username", "service_user",
                                "hiveWriteMode", "HIVE_RECORD_PUT"))), List.of()),
                null, null, null, null, null, null, null);

        new DslCompiler(nifi, registry, mock(FieldMappingService.class), hive).compile(pipeline);

        verify(nifi).createProcessor(eq("pg"), eq("com.linewell.nifi.hive.HiveRecordPut"),
                eq("Hive/put"), anyDouble(), anyDouble(), anyMap(), nullable(String.class), nullable(String.class));
        verify(nifi, never()).findProcessorType(anyString());
        verify(nifi, never()).listControllerServices(anyString());
    }

    private NifiEntity entity(String id) {
        return new NifiEntity(null, Map.of("id", id), null, null);
    }

    private static Pipeline hivePipeline(String id) {
        return new Pipeline(id, "Hive sink " + id, null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("sink", "sink.hive", "Hive", "sink", 0, 0,
                        Map.of("database", "analytics", "table", "EVENT_LOG", "username", "service_user",
                                "hiveWriteMode", "HIVE_RECORD_PUT"))), List.of()),
                null, null, null, null, null, null, null);
    }

    @Test
    void batchLakeUsesConfiguredHdfsDirectoryForHiveTableWithCustomLocation() throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        HiveModule hive = mock(HiveModule.class);
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/sinks/hive-sink.json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get("sink.hive")).thenReturn(manifest);
        when(hive.resolveRuntimeProfile("default")).thenReturn(hiveRuntimeProfile());
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap())).thenReturn(entity("cs"));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class))).thenReturn(entity("put"));
        when(nifi.findProcessorType("PutHwHDFS")).thenReturn(java.util.Optional.of("com.linewell.microservice.processor.hadoop.PutHwHDFS"));
        when(nifi.listControllerServices("root")).thenReturn(List.of(publicCsvWriterService()));

        Pipeline pipeline = new Pipeline("test", "Hive sink", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("sink", "sink.hive", "Hive", "sink", 0, 0,
                        Map.of("database", "analytics", "table", "EVENT_LOG", "hdfsDirectory", "/warehouse/custom/event_log"))),
                        List.of()), null, null, null, null, null, null, null);

        new DslCompiler(nifi, registry, mock(FieldMappingService.class), hive).compile(pipeline);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createProcessor(eq("pg"), eq("com.linewell.microservice.processor.hadoop.PutHwHDFS"),
                eq("Hive/put"), anyDouble(), anyDouble(), properties.capture(), nullable(String.class), nullable(String.class));
        assertThat(properties.getValue()).containsEntry("Directory", "/warehouse/custom/event_log");
    }

    @Test
    void nativePutHdfsUsesTheSameRecordChainWithoutCustomKrb5Property() throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        HiveModule hive = mock(HiveModule.class);
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/sinks/hive-sink.json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get("sink.hive")).thenReturn(manifest);
        when(hive.resolveRuntimeProfile("default")).thenReturn(hiveRuntimeProfile());
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap())).thenReturn(entity("cs"));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class))).thenReturn(entity("processor"));
        when(nifi.listControllerServices("root")).thenReturn(List.of(publicCsvWriterService()));
        when(nifi.findProcessorType("PutHDFS")).thenReturn(java.util.Optional.of("org.apache.nifi.processors.hadoop.PutHDFS"));
        Pipeline pipeline = new Pipeline("native", "Native HDFS", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("sink", "sink.hive", "Hive", "sink", 0, 0,
                        Map.of("database", "analytics", "table", "EVENT_LOG", "hiveWriteMode", "HDFS_BATCH"))),
                        List.of()), null, null, null, null, null, null, null);

        new DslCompiler(nifi, registry, mock(FieldMappingService.class), hive).compile(pipeline);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createProcessor(eq("pg"), eq("org.apache.nifi.processors.hadoop.PutHDFS"),
                eq("Hive/put"), anyDouble(), anyDouble(), properties.capture(), nullable(String.class), nullable(String.class));
        assertThat(properties.getValue()).containsEntry("Conflict Resolution Strategy", "replace")
                .containsEntry("Kerberos Relogin Period", "4 hours")
                .doesNotContainKey("krb5 conf");
        verify(nifi, times(4)).createProcessor(eq("pg"), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class));
    }

    @Test
    void hdfsDeploymentFailsBeforeCreatingProcessorsWithoutVerifiedCsvWriter() throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        HiveModule hive = mock(HiveModule.class);
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/sinks/hive-sink.json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get("sink.hive")).thenReturn(manifest);
        when(hive.resolveRuntimeProfile("default")).thenReturn(hiveRuntimeProfile());
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap())).thenReturn(entity("cs"));
        when(nifi.listControllerServices("root")).thenReturn(List.of());
        Pipeline pipeline = new Pipeline("missing-writer", "Hive sink", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("sink", "sink.hive", "Hive", "sink", 0, 0,
                        Map.of("database", "analytics", "table", "EVENT_LOG"))), List.of()),
                null, null, null, null, null, null, null);

        assertThatThrownBy(() -> new DslCompiler(nifi, registry, mock(FieldMappingService.class), hive).compile(pipeline))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CSV Record Writer");
        verify(nifi, never()).createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class));
    }

    @Test
    void multipleHiveTargetsResolveTheManagedWriterAndProcessorTypeOnlyOnce() throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        ManifestRegistry registry = mock(ManifestRegistry.class);
        HiveModule hive = mock(HiveModule.class);
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/sinks/hive-sink.json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get("sink.hive")).thenReturn(manifest);
        when(hive.resolveRuntimeProfile("default")).thenReturn(hiveRuntimeProfile());
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap())).thenReturn(entity("cs"));
        var sequence = new AtomicInteger();
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class)))
                .thenAnswer(invocation -> entity("processor-" + sequence.incrementAndGet()));
        when(nifi.listControllerServices("root")).thenReturn(List.of(publicCsvWriterService()));
        when(nifi.findProcessorType("PutHwHDFS"))
                .thenReturn(java.util.Optional.of("com.linewell.microservice.processor.hadoop.PutHwHDFS"));
        Pipeline pipeline = new Pipeline("two-targets", "Hive targets", null, null, null,
                new Pipeline.Dsl(1, List.of(
                        new Pipeline.Node("sink-a", "sink.hive", "Hive A", "sink", 0, 0,
                                Map.of("database", "analytics", "table", "A")),
                        new Pipeline.Node("sink-b", "sink.hive", "Hive B", "sink", 0, 300,
                                Map.of("database", "analytics", "table", "B"))), List.of()),
                null, null, null, null, null, null, null);

        new DslCompiler(nifi, registry, mock(FieldMappingService.class), hive).compile(pipeline);

        verify(nifi, times(1)).listControllerServices("root");
        verify(nifi, times(1)).findProcessorType("PutHwHDFS");
        verify(nifi, times(8)).createProcessor(eq("pg"), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class));
    }

    private static Map<String, Object> hiveRuntimeProfile() {
        return Map.of("dbType", "HIVE", "zookeeperQuorum", "zk.example:2181", "userPrincipal", "service_user",
                "keytabPath", "/opt/mrs/client/user.keytab", "krb5ConfPath", "/opt/mrs/client/krb5.conf",
                "clientConfigDir", "/opt/mrs/client");
    }

    private static JsonNode publicCsvWriterService() {
        return new ObjectMapper().valueToTree(Map.of("component", Map.of(
                "id", "public-csv-writer", "name", "PUBLIC-WRITER-CSV-HDFS",
                "type", "org.apache.nifi.csv.CSVRecordSetWriter",
                "properties", Map.of("Include Header Line", "false", "Value Separator", "\\u0001"))));
    }
}
