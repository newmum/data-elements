package com.linewell.dataelement.integration.nifi.canvas.compile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ComponentManifest;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import com.linewell.dataelement.integration.nifi.canvas.mapping.UnifiedMappingService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingVersionStore;
import com.linewell.dataelement.mapping.core.MappingEngine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DslCompilerHiveTemporalWriterTest {
    private record Created(String id, String type, String name, Map<String, String> properties) {}
    private final List<Created> services = new ArrayList<>();
    private final List<Created> processors = new ArrayList<>();
    private final NifiClient nifi = mock(NifiClient.class);
    private final DslCompiler compiler;

    DslCompilerHiveTemporalWriterTest() throws Exception {
        ManifestRegistry registry = mock(ManifestRegistry.class);
        ObjectMapper json = new ObjectMapper();
        for (String name : List.of("hive", "mysql")) {
            try (var stream = getClass().getResourceAsStream("/manifests/sources/" + name + ".json")) {
                when(registry.get("source." + name)).thenReturn(json.readValue(stream, ComponentManifest.class));
            }
        }
        try (var stream = getClass().getResourceAsStream("/manifests/transforms/sql.json")) {
            when(registry.get("transform.sql")).thenReturn(json.readValue(stream, ComponentManifest.class));
        }
        try (var stream = getClass().getResourceAsStream("/manifests/transforms/field-mapping.json")) {
            when(registry.get("transform.field-mapping")).thenReturn(json.readValue(stream, ComponentManifest.class));
        }
        HiveModule hive = mock(HiveModule.class);
        when(hive.resolveRuntimeProfile("default")).thenReturn(Map.of("dbType", "HIVE",
                "jdbcUrl", "jdbc:hive2://hive.example:10000/default", "hiveConnectionMode", "JDBC"));
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        AtomicInteger sequence = new AtomicInteger();
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap())).thenAnswer(call -> {
            String id = "cs-" + sequence.incrementAndGet();
            services.add(new Created(id, call.getArgument(1), call.getArgument(2), new LinkedHashMap<>(call.getArgument(3))));
            return entity(id);
        });
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(),
                anyMap(), nullable(String.class), nullable(String.class))).thenAnswer(call -> {
            String id = "processor-" + sequence.incrementAndGet();
            processors.add(new Created(id, call.getArgument(1), call.getArgument(2), new LinkedHashMap<>(call.getArgument(5))));
            return entity(id);
        });
        when(nifi.createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenAnswer(call -> entity("connection-" + sequence.incrementAndGet()));
        when(nifi.createRetentionFunnel(anyString(),anyDouble(),anyDouble())).thenAnswer(call->entity("funnel-"+sequence.incrementAndGet()));
        compiler = new DslCompiler(nifi, registry, mock(FieldMappingService.class), hive);
        var unified=mock(UnifiedMappingService.class);
        when(unified.compileBatch(anyMap())).thenReturn(Map.of("mapping",prepared()));
        compiler.setUnifiedMappingService(unified);
        var versions=mock(MappingVersionStore.class);
        when(versions.archive(any(),anyMap())).thenReturn(Map.of("mapping","plan-version"));
        compiler.setMappingVersions(versions);
    }

    @ParameterizedTest @ValueSource(strings={"hive","mysql"})
    void customMappingWriterUsesHiveProfileOnFullPublicationAndOrdinaryWriterOtherwise(String database) {
        Pipeline.Node mapping=mapping();
        compiler.compile(flow(List.of(source("source",database,Map.of()),mapping)));
        Created processor=namedProcessor("字段映射");
        if(database.equals("hive"))assertThat(processor.properties()).containsEntry("record-writer",namedService(HiveTemporalJsonPolicy.WRITER_NAME).id());
        else {assertThat(processor.properties()).containsEntry("record-writer",namedService("字段映射/类型化输出").id());assertThat(services).noneMatch(s->s.name().equals(HiveTemporalJsonPolicy.WRITER_NAME));}
        assertThat(processor.type()).isEqualTo("com.linewell.dataelement.nifi.HaitongTransformRecord");
        assertThat(namedProcessor("source/execute").properties()).containsEntry("Use Avro Logical Types","true");
        if(database.equals("mysql"))assertThat(namedService("JsonWriter").properties()).containsAllEntriesOf(HiveTemporalJsonPolicy.writerProperties());
    }

    @ParameterizedTest @ValueSource(strings={"hive","mysql"})
    void fieldOnlyUpgradeBindsCorrectWriterAndNeverRecreatesOrModifiesSources(String database) {
        compiler.createMappingReplacement(flow(List.of(source("source",database,Map.of()),mapping())),mapping(),"existing-group","reader","typed-reader","existing-writer",prepared());
        String writer=namedService(database.equals("hive")?HiveTemporalJsonPolicy.WRITER_NAME:"字段映射/类型化输出").id();
        assertThat(namedService(database.equals("hive")?HiveTemporalJsonPolicy.WRITER_NAME:"字段映射/类型化输出").properties()).containsAllEntriesOf(HiveTemporalJsonPolicy.writerProperties());
        assertThat(namedProcessor("字段映射").properties()).containsEntry("record-writer",writer).containsEntry("typed-record-reader","typed-reader");
        assertThat(processors).hasSize(1);
        verify(nifi,never()).putWithRevision(contains("/processors/"),anyString(),any());
        if(database.equals("hive"))verify(nifi).waitForControllerServiceEnabled(writer,30_000L);
    }

    private static Pipeline.Node mapping(){return new Pipeline.Node("mapping","transform.field-mapping","字段映射","transform",300,0,Map.of("executionEngine","JAVA_RECORD","mappings","{\"schemaVersion\":\"2.0\",\"rules\":[]}"));}
    private static UnifiedMappingService.Compiled prepared(){try{return new UnifiedMappingService.Compiled(MappingEngine.compile(new ObjectMapper().readTree("{\"schemaVersion\":\"2.0\",\"policies\":{},\"rules\":[]}")),Map.of());}catch(Exception error){throw new IllegalStateException(error);}}

    @Test void hiveSourceUsesNamedTemporalWriterAndMatchingReader() {
        compiler.compile(flow(List.of(source("h", "hive", Map.of()))));
        Created writer = namedService(HiveTemporalJsonPolicy.WRITER_NAME);
        assertThat(writer.type()).isEqualTo("org.apache.nifi.json.JsonRecordSetWriter");
        assertThat(writer.properties()).containsAllEntriesOf(HiveTemporalJsonPolicy.formats())
                .containsEntry("Schema Write Strategy", "full-schema-attribute");
        assertThat(namedProcessor("h/execute").properties()).containsEntry("Record Writer", writer.id());
        assertThat(namedService("JsonReader").properties()).containsAllEntriesOf(HiveTemporalJsonPolicy.formats());
        verify(nifi).waitForControllerServiceEnabled(writer.id(), 30_000L);
    }

    @Test void mysqlKeepsOrdinaryWriterAndReader() {
        compiler.compile(flow(List.of(source("m", "mysql", Map.of("dbType", "MYSQL")))));
        assertThat(services).noneMatch(service -> HiveTemporalJsonPolicy.WRITER_NAME.equals(service.name()));
        assertThat(namedProcessor("m/execute").properties()).containsEntry("Record Writer", namedService("JsonWriter").id());
        assertThat(namedService("JsonReader").properties()).isEmpty();
    }

    @Test void mixedSourcesOnlyReplaceHiveExecuteSqlWriter() {
        compiler.compile(flow(List.of(source("h", "hive", Map.of()), source("m", "mysql", Map.of("dbType", "MYSQL")))));
        assertThat(namedProcessor("h/execute").properties()).containsEntry("Record Writer", namedService(HiveTemporalJsonPolicy.WRITER_NAME).id());
        assertThat(namedProcessor("m/execute").properties()).containsEntry("Record Writer", namedService("JsonWriter").id());
    }

    @Test void queryExecutedDirectlyInHiveUsesTheSameTemporalWriter() {
        Pipeline.Node source = source("h", "hive", Map.of());
        Pipeline.Node query = new Pipeline.Node("q", "transform.sql", "query", "transform", 300, 0,
                Map.of("executionMode", "SOURCE_DB", "sql", "SELECT event_time FROM EVENT_LOG"));
        Pipeline base = flow(List.of(source, query));
        Pipeline pipeline = new Pipeline(base.id(), base.name(), null, null, null,
                new Pipeline.Dsl(1, base.dsl().nodes(), List.of(new Pipeline.Edge("edge", "h", "q", "default"))),
                null, null, null, null, null, null, null);
        compiler.compile(pipeline);
        assertThat(namedProcessor("query/query").properties()).containsEntry("Record Writer", namedService(HiveTemporalJsonPolicy.WRITER_NAME).id());
    }

    @ParameterizedTest @ValueSource(ints = {20, 100})
    void sourcesShareOneTemporalWriterWithoutServiceLookupPerNode(int count) {
        List<Pipeline.Node> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) nodes.add(source("h" + i, "hive", Map.of()));
        compiler.compile(flow(nodes));
        assertThat(services.stream().filter(service -> HiveTemporalJsonPolicy.WRITER_NAME.equals(service.name()))).hasSize(1);
        String id = namedService(HiveTemporalJsonPolicy.WRITER_NAME).id();
        assertThat(processors.stream().filter(processor -> processor.type().endsWith(".ExecuteSQLRecord")))
                .hasSize(count).allMatch(processor -> id.equals(processor.properties().get("Record Writer")));
        verify(nifi, never()).listControllerServices(anyString());
    }

    @Test void typedReaderParsesExactlyTheWriterFormatsAndKeepsSchemaAttribute() {
        assertThat(HiveTemporalJsonPolicy.typedReaderProperties(true)).containsAllEntriesOf(HiveTemporalJsonPolicy.formats())
                .containsEntry("Schema Text", "${avro.schema}");
        assertThat(HiveTemporalJsonPolicy.typedReaderProperties(false)).containsAllEntriesOf(HiveTemporalJsonPolicy.formats());
    }

    @ParameterizedTest @ValueSource(strings = {"jdbc:hive2://host/default", " JDBC:HIVE://host/default "})
    void hiveJdbcUrlRecognizedWithoutExplicitType(String url) {
        assertThat(HiveTemporalJsonPolicy.isHive("source.jdbc", Map.of("jdbcUrl", url))).isTrue();
        assertThat(HiveTemporalJsonPolicy.isHive("source.mysql", Map.of("jdbcUrl", "jdbc:mysql://host/db"))).isFalse();
    }

    private Created namedService(String name) { return services.stream().filter(value -> name.equals(value.name())).findFirst().orElseThrow(); }
    private Created namedProcessor(String name) { return processors.stream().filter(value -> name.equals(value.name())).findFirst().orElseThrow(); }
    private static Pipeline.Node source(String id, String type, Map<String, Object> extras) {
        Map<String, Object> config = new LinkedHashMap<>(extras); config.put("table", "EVENT_LOG");
        return new Pipeline.Node(id, "source." + type, id, "source", 0, 0, config);
    }
    private static Pipeline flow(List<Pipeline.Node> nodes) {
        return new Pipeline("temporal-test", "Temporal codec test", null, null, null,
                new Pipeline.Dsl(1, nodes, nodes.stream().anyMatch(n->n.id().equals("mapping"))?List.of(new Pipeline.Edge("edge","source","mapping","default")):List.of()), null, null, null, null, null, null, null);
    }
    private static NifiEntity entity(String id) { return new NifiEntity(null, Map.of("id", id), null, null); }
}
