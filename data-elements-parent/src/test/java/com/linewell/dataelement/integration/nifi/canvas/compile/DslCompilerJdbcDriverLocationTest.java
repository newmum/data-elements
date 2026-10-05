package com.linewell.dataelement.integration.nifi.canvas.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ComponentManifest;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class DslCompilerJdbcDriverLocationTest {

    @ParameterizedTest
    @ValueSource(strings = {"source.oracle", "sink.jdbc"})
    void usesOracle8ForSourceAndTargetWhenTheRuntimeNodeConfiguresADirectory(String component) throws Exception {
        assertOracleDriverLocation(component, "/opt/nifi/lib/jdbc/", Map.of(),
                "/opt/nifi/lib/jdbc/ojdbc8-12.2.0.1.jar");
    }

    @Test
    void preservesAnExplicitRuntimeNodeOracleJar() throws Exception {
        assertOracleDriverLocation("source.oracle", "ORACLE=/opt/nifi/custom/oracle.jar", Map.of(),
                "/opt/nifi/custom/oracle.jar");
    }

    @Test
    void preservesAnExplicitTaskOracleJarOverTheRuntimeNodeDirectory() throws Exception {
        assertOracleDriverLocation("source.oracle", "/opt/nifi/lib/jdbc", Map.of(
                "driverLocations", "/opt/nifi/custom/task-oracle.jar"), "/opt/nifi/custom/task-oracle.jar");
    }

    @Test
    void expandsLegacyKingbase8TargetTypeUsingTheCurrentTargetNodesDriverDirectory() throws Exception {
        NifiClient nifi = configuredNifi();
        ManifestRegistry registry = mock(ManifestRegistry.class);
        NifiNodeRuntimeResolver nodeResolver = mock(NifiNodeRuntimeResolver.class);
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream("/manifests/sinks/jdbc-sink.json")) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get("sink.jdbc")).thenReturn(manifest);
        when(nodeResolver.resolve()).thenReturn(runtimeNode("/opt/nifi/lib/jdbc/"));
        Map<String, Object> config = new java.util.LinkedHashMap<>(Map.of(
                "dbType", "kingbase8", "jdbcUrl", "jdbc:kingbase8://example.test:54321/target",
                "username", "target-user", "password", "target-password", "table", "TARGET_TABLE",
                "writerType", "NIFI_PUT_DATABASE_RECORD"));
        Pipeline pipeline = new Pipeline("task", "Kingbase 目标", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("target", "sink.jdbc", "目标表",
                        "sink", 0, 0, config)), List.of()),
                null, null, null, null, null, null, null);

        new DslCompiler(nifi, nodeResolver, registry, mock(FieldMappingService.class), mock(HiveModule.class), "")
                .compile(pipeline);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"),
                eq("目标表/dbcp"), properties.capture());
        assertThat(properties.getValue())
                .containsEntry("Database Driver Class Name", "com.kingbase8.Driver")
                .containsEntry("Database Driver Locations", "/opt/nifi/lib/jdbc/kingbase8.jar");
    }

    private void assertOracleDriverLocation(String component, String nodeLocation,
                                           Map<String, Object> overrides, String expected) throws Exception {
        NifiClient nifi = configuredNifi();
        ManifestRegistry registry = mock(ManifestRegistry.class);
        NifiNodeRuntimeResolver nodeResolver = mock(NifiNodeRuntimeResolver.class);
        String manifestPath = "source.oracle".equals(component)
                ? "/manifests/sources/oracle.json" : "/manifests/sinks/jdbc-sink.json";
        ComponentManifest manifest;
        try (var stream = getClass().getResourceAsStream(manifestPath)) {
            manifest = new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
        when(registry.get(component)).thenReturn(manifest);
        when(nodeResolver.resolve()).thenReturn(runtimeNode(nodeLocation));
        Map<String, Object> config = new java.util.LinkedHashMap<>(Map.of(
                "dbType", "ORACLE", "jdbcUrl", "jdbc:oracle:thin:@//example.test:1521/prod",
                "username", "test-user", "password", "test-password", "table", "TEST_TABLE",
                "writerType", "NIFI_PUT_DATABASE_RECORD"));
        config.putAll(overrides);
        Pipeline pipeline = new Pipeline("task", "Oracle", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("oracle", component, "Oracle",
                        component.startsWith("source.") ? "source" : "sink", 0, 0, config)), List.of()),
                null, null, null, null, null, null, null);

        new DslCompiler(nifi, nodeResolver, registry, mock(FieldMappingService.class), mock(HiveModule.class), "")
                .compile(pipeline);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"),
                eq("Oracle/dbcp"), properties.capture());
        assertThat(properties.getValue()).containsEntry("Database Driver Locations", expected);
        if ("source.oracle".equals(component) && overrides.isEmpty() && nodeLocation.endsWith("/")) {
            assertThat(manifest.compile().controllerServices().getFirst().properties()
                    .get("Database Driver Locations")).endsWith("/ojdbc8-12.2.0.1.jar");
        }
    }

    @Test
    void usesTheRuntimeNodesExactOceanBaseJarInsteadOfItsJdbcDirectory() throws Exception {
        NifiClient nifi = configuredNifi();
        ManifestRegistry registry = mock(ManifestRegistry.class);
        NifiNodeRuntimeResolver nodeResolver = mock(NifiNodeRuntimeResolver.class);
        when(registry.get("source.oceanbase")).thenReturn(oceanBaseManifest());
        when(nodeResolver.resolve()).thenReturn(runtimeNode(
                "OCEANBASE_ORACLE=/opt/nifi/nifi-current/lib/jdbc/oceanbase-client-2.4.17.jar"));

        new DslCompiler(nifi, nodeResolver, registry, mock(FieldMappingService.class), mock(HiveModule.class), "")
                .compile(oceanBasePipeline());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"),
                eq("OceanBase Oracle/dbcp"), properties.capture());
        assertThat(properties.getValue()).containsEntry("Database Driver Locations",
                "/opt/nifi/nifi-current/lib/jdbc/oceanbase-client-2.4.17.jar");
    }

    @Test
    void expandsRuntimeNodeDriverDirectoryUsingTheManagedVendorJar() throws Exception {
        NifiClient nifi = configuredNifi();
        ManifestRegistry registry = mock(ManifestRegistry.class);
        NifiNodeRuntimeResolver nodeResolver = mock(NifiNodeRuntimeResolver.class);
        when(registry.get("source.oceanbase")).thenReturn(oceanBaseManifest());
        when(nodeResolver.resolve()).thenReturn(runtimeNode("/opt/nifi/nifi-current/lib/jdbc/"));

        new DslCompiler(nifi, nodeResolver, registry, mock(FieldMappingService.class), mock(HiveModule.class), "")
                .compile(oceanBasePipeline());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"),
                eq("OceanBase Oracle/dbcp"), properties.capture());
        assertThat(properties.getValue()).containsEntry("Database Driver Locations",
                "/opt/nifi/nifi-current/lib/jdbc/oceanbase-client-2.4.10.jar");
    }

    private NifiClient configuredNifi() {
        NifiClient nifi = mock(NifiClient.class);
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.listProcessGroups("root")).thenReturn(List.of());
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(entity("controller-service"));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class))).thenReturn(entity("processor"));
        return nifi;
    }

    private ComponentManifest oceanBaseManifest() throws Exception {
        try (var stream = getClass().getResourceAsStream("/manifests/sources/oceanbase.json")) {
            return new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
    }

    private Pipeline oceanBasePipeline() {
        return new Pipeline("task", "OceanBase 接入", null, null, null,
                new Pipeline.Dsl(1, List.of(new Pipeline.Node("source", "source.oceanbase", "OceanBase Oracle",
                        "source", 0, 0, Map.of(
                                "dbType", "OCEANBASE_ORACLE",
                                "compatibleMode", "ORACLE",
                                "jdbcUrl", "jdbc:oceanbase:oracle://example.test:2881/prod",
                                "host", "example.test",
                                "port", "2881",
                                "database", "prod",
                                "username", "test-user",
                                "password", "test-password",
                                "table", "TEST_TABLE"))), List.of()),
                null, null, null, null, null, null, null);
    }

    private NifiNodeRuntimeResolver.RuntimeNode runtimeNode(String jdbcDriverLocations) {
        return new NifiNodeRuntimeResolver.RuntimeNode("tenant", "node", "leefy-prod", "http://leefy.test",
                "user", "password", false, "root", jdbcDriverLocations);
    }

    private NifiEntity entity(String id) {
        return new NifiEntity(null, Map.of("id", id), null, null);
    }
}
