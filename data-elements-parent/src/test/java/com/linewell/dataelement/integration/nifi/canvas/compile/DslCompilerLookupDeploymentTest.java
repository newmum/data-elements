package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ComponentManifest;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DslCompilerLookupDeploymentTest {
    @ParameterizedTest
    @CsvSource({"POSTGRESQL,ORACLE,true", "ORACLE,POSTGRESQL,false", "POSTGRESQL,inherit,false"})
    void nativeExecuteSqlAndDbcpUseTheSameDictionaryDatabase(String sourceType, String dictionaryType,
                                                             boolean needsDual) throws Exception {
        var fixture = compile(sourceType, dictionaryType, 2);
        assertThat(fixture.sql().contains("FROM DUAL")).isEqualTo(needsDual);
        String actualType = "inherit".equals(dictionaryType) ? sourceType : dictionaryType;
        assertThat(fixture.lookupConnection().get("Database Driver Class Name"))
                .isEqualTo("ORACLE".equals(actualType) ? "oracle.jdbc.OracleDriver" : "org.postgresql.Driver");
        assertThat(fixture.lookupConnection().get("Database Connection URL"))
                .startsWith("ORACLE".equals(actualType) ? "jdbc:oracle:" : "jdbc:postgresql:");
        if (!needsDual) assertThat(fixture.sql()).contains("AS \"ODS_UUID\"");
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void sameConnectionLookupsStayInOneNativeQueryAndConnection(int mappings) throws Exception {
        var fixture = compile("POSTGRESQL", "POSTGRESQL", mappings);
        assertThat(fixture.sql().split(" LEFT JOIN ")).hasSize(mappings + 1);
        assertThat(fixture.sql()).doesNotContain("FROM DUAL");
        // All lookups in one datasource share one ExecuteSQLRecord and one
        // lookup DBCP service, rather than creating one remote chain per field.
        verify(fixture.nifi(), times(1)).createProcessor(anyString(),
                eq("org.apache.nifi.processors.standard.ExecuteSQLRecord"), startsWith("字段映射/"), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class));
    }

    private static Fixture compile(String sourceType, String dictionaryType, int mappings) throws Exception {
        NifiClient nifi = mock(NifiClient.class);
        AtomicInteger ids = new AtomicInteger();
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.listProcessGroups("root")).thenReturn(List.of());
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap()))
                .thenAnswer(invocation -> entity("cs-" + ids.incrementAndGet()));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyMap(),
                nullable(String.class), nullable(String.class)))
                .thenAnswer(invocation -> entity("processor-" + ids.incrementAndGet()));
        when(nifi.createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenAnswer(invocation -> entity("connection-" + ids.incrementAndGet()));
        ManifestRegistry registry = mock(ManifestRegistry.class);
        when(registry.get("source.test")).thenReturn(manifest("/manifests/sources/postgresql.json"));
        when(registry.get("transform.field-mapping")).thenReturn(manifest("/manifests/transforms/field-mapping.json"));
        FieldMappingService mappingService = mock(FieldMappingService.class);
        List<FieldMappingService.LookupPlan> lookups = new ArrayList<>();
        for (int index = 0; index < mappings; index++) {
            String target = "Label_" + index + "_cn";
            Map<String, Object> dictionary = "inherit".equals(dictionaryType)
                    ? Map.of() : connection(dictionaryType, "dictionary-db");
            lookups.add(new FieldMappingService.LookupPlan(target, target,
                    "SELECT label AS " + target + " FROM dictionary WHERE code = ?",
                    List.of("code"), List.of("__lookup_" + index), "NULL", dictionary));
        }
        when(mappingService.compilePlan(any())).thenReturn(new FieldMappingService.CompiledMapping(
                "SELECT * FROM FLOWFILE", "SELECT * FROM FLOWFILE", lookups,
                List.of("ODS_UUID"), List.of("ODS_UUID"), List.of()));
        Map<String, Object> sourceConfig = new LinkedHashMap<>(connection(sourceType, "source-db"));
        sourceConfig.put("table", "source_table");
        Pipeline pipeline = new Pipeline("task", "Lookup dialect regression", null, null, null,
                new Pipeline.Dsl(1, List.of(
                        new Pipeline.Node("source", "source.test", "来源", "source", 0, 0, sourceConfig),
                        new Pipeline.Node("mapping", "transform.field-mapping", "字段映射", "transform", 1, 0,
                                Map.of("mappings", Map.of()))),
                        List.of(new Pipeline.Edge("edge", "source", "mapping", null))),
                null, null, null, null, null, null, null);
        new DslCompiler(nifi, registry, mappingService, mock(HiveModule.class)).compile(pipeline);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> sqlProperties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createProcessor(eq("pg"), eq("org.apache.nifi.processors.standard.ExecuteSQLRecord"),
                startsWith("字段映射/"), anyDouble(), anyDouble(), sqlProperties.capture(), nullable(String.class), nullable(String.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> lookupProperties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createControllerService(eq("pg"), eq("org.apache.nifi.dbcp.DBCPConnectionPool"),
                startsWith("字段映射/lookup-dbcp-"), lookupProperties.capture());
        return new Fixture(nifi, sqlProperties.getValue().get("SQL Query"), lookupProperties.getValue());
    }

    private static Map<String, Object> connection(String type, String id) {
        return Map.of("dbType", type, "datasourceId", id, "driverLocations", "/test/driver.jar",
                "jdbcUrl", "ORACLE".equals(type) ? "jdbc:oracle:thin:@//example.test/database"
                        : "jdbc:postgresql://example.test/database");
    }

    private static ComponentManifest manifest(String path) throws Exception {
        try (var stream = DslCompilerLookupDeploymentTest.class.getResourceAsStream(path)) {
            return new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
    }

    private static NifiEntity entity(String id) {
        return new NifiEntity(null, Map.of("id", id), null, null);
    }

    private record Fixture(NifiClient nifi, String sql, Map<String, String> lookupConnection) {}
}
