package com.linewell.dataelement.integration.nifi.canvas.mapping;

import org.apache.nifi.logging.ComponentLog;
import org.apache.nifi.sql.CalciteDatabase;
import org.apache.nifi.sql.ColumnSchema;
import org.apache.nifi.sql.IterableRowStream;
import org.apache.nifi.sql.NiFiTable;
import org.apache.nifi.sql.NiFiTableSchema;
import org.apache.nifi.sql.ResettableDataSource;
import org.apache.nifi.sql.RowStream;
import org.apache.nifi.sql.ColumnType;
import org.apache.calcite.sql.SqlCollation;
import org.apache.calcite.sql.type.SqlTypeName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.integration.nifi.canvas.compile.DslCompiler;
import com.linewell.dataelement.integration.nifi.canvas.compile.HiveServiceFixtures;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ComponentManifest;
import com.linewell.dataelement.integration.nifi.canvas.manifest.ManifestRegistry;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.model.nifi.NifiEntity;
import com.linewell.dataelement.platform.magic.module.HiveModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Runs generated SQL with the same table implementation and engine as QueryRecord 2.9.0. */
class FieldMappingCalciteCharsetTest {
    private final FieldMappingService service = new FieldMappingService();

    @Test
    void asciiStringCodesTranslateToChineseOnNifiRecordTypes() throws Exception {
        var labels = new LinkedHashMap<String, Object>();
        labels.put("0", "否");
        labels.put("1", "是");
        var spec = Map.of("version", "1.0", "mappings", List.of(Map.of(
                "from", "/SDPBS", "to", "/SDPBS_cn", "sourceDataType", "varchar(2)",
                "transform", Map.of("fn", "enumMap", "args", Arrays.asList(labels, null)))));
        assertEquals(Arrays.asList("否", "是", null, null), query(service.compileQuery(spec),
                List.of(new ColumnSchema("SDPBS", String.class, true)),
                Arrays.asList(new Object[]{"0"}, new Object[]{"1"}, new Object[]{"9"}, new Object[]{null})));
    }

    @Test
    void utf8RecordCodesCanBeComparedWithAsciiDictionaryKeys() throws Exception {
        ColumnType utf8 = factory -> factory.createTypeWithCharsetAndCollation(
                factory.createSqlType(SqlTypeName.VARCHAR), StandardCharsets.UTF_8, SqlCollation.IMPLICIT);
        var spec = Map.of("version", "1.0", "mappings", List.of(Map.of(
                "from", "/SDPBS", "to", "/SDPBS_cn", "sourceDataType", "varchar(2)",
                "transform", Map.of("fn", "enumMap", "args", Arrays.asList(Map.of("0", "否", "1", "是"), null)))));
        assertEquals(List.of("否", "是"), query(service.compileQuery(spec),
                List.of(new ColumnSchema("SDPBS", utf8, true)),
                List.of(new Object[]{"0"}, new Object[]{"1"})));
    }

    @Test
    void unicodeDictionaryKeysCanBeComparedWithDefaultNifiStringFields() throws Exception {
        var spec = Map.of("version", "1.0", "mappings", List.of(Map.of(
                "from", "/SDPBS", "to", "/SDPBS_cn", "sourceDataType", "varchar(2)",
                "transform", Map.of("fn", "enumMap", "args", Arrays.asList(Map.of("是", "允许", "否", "禁止"), "unknown")))));
        assertEquals(List.of("允许", "禁止", "unknown"), query(service.compileQuery(spec),
                List.of(new ColumnSchema("SDPBS", String.class, true)),
                List.of(new Object[]{"是"}, new Object[]{"否"}, new Object[]{"?"})));
    }

    @Test
    void legacyMixedCharsetSqlReproducesProductionError() {
        String legacy = "SELECT CASE \"SDPBS\" WHEN _UTF8'否' THEN _UTF8'禁止' ELSE NULL END FROM FLOWFILE";
        var error = assertThrows(SQLException.class, () -> query(legacy,
                List.of(new ColumnSchema("SDPBS", String.class, true)), List.<Object[]>of(new Object[]{"否"})));
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        assertTrue(root.getMessage().contains("Cannot apply operation '=' to strings with different charsets"));
    }

    @Test
    void leadingZerosQuotesAccentsAndEmojiArePreserved() throws Exception {
        var codes = new LinkedHashMap<String, Object>();
        codes.put("01", "第一项");
        codes.put("1", "第二项");
        codes.put("O'Brien", "含'引号");
        codes.put("é😀", "中文😀");
        var values = List.of("01", "1", "O'Brien", "é😀");
        assertEquals(List.of("第一项", "第二项", "含'引号", "中文😀"), query(
                service.compileQuery(spec("varchar(200)", codes, "unknown")),
                List.of(new ColumnSchema("SDPBS", String.class, true)), values.stream().map(v -> new Object[]{v}).toList()));
    }

    @Test
    void differentLengthLabelsAndFallbackAreNotPadded() throws Exception {
        assertEquals(List.of("否", "允许接入", "unknown", "unknown"), query(
                service.compileQuery(spec("varchar", Map.of("0", "否", "1", "允许接入"), "unknown")),
                List.of(new ColumnSchema("SDPBS", String.class, true)),
                Arrays.asList(new Object[]{"0"}, new Object[]{"1"}, new Object[]{"9"}, new Object[]{null})));
    }

    @Test
    void longCodesAndDescriptionsAreNotTruncated() throws Exception {
        String code = "码".repeat(300);
        String label = "说明".repeat(600);
        assertEquals(List.of(label), query(service.compileQuery(spec("varchar(1000)", Map.of(code, label), null)),
                List.of(new ColumnSchema("SDPBS", String.class, true)), List.<Object[]>of(new Object[]{code})));
    }

    @Test
    void numericCodesKeepNumericComparisonWithChineseLabels() throws Exception {
        assertEquals(Arrays.asList("否", "是", null, null), query(
                service.compileQuery(spec("int", Map.of("0", "否", "1", "是"), null)),
                List.of(new ColumnSchema("SDPBS", Integer.class, true)),
                Arrays.asList(new Object[]{0}, new Object[]{1}, new Object[]{9}, new Object[]{null})));
    }

    @Test
    void decimalCodesKeepPrecisionAndNumericEquality() throws Exception {
        assertEquals(List.of("大数", "小数"), query(
                service.compileQuery(spec("number(30,4)", Map.of("9007199254740993", "大数", "1.00", "小数"), null)),
                List.of(new ColumnSchema("SDPBS", BigDecimal.class, true)),
                List.of(new Object[]{new BigDecimal("9007199254740993")}, new Object[]{new BigDecimal("1.0")})));
    }

    @Test
    void sourceDatabaseSqlUsesNativeChineseLiteralsAndEscaping() throws Exception {
        var spec = spec("varchar(20)", Map.of("O'Brien", "含'引号", "01", "短"), "未匹配");
        String sql = service.compileSourceDbQuery(spec, "FLOWFILE", "src", name -> "\"" + name + "\"");
        assertTrue(sql.contains("'O''Brien'"));
        assertTrue(!sql.contains("_UTF") && !sql.contains("CHARACTER SET") && !sql.contains("CONVERT("));
        try (var connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:" + java.util.UUID.randomUUID());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE FLOWFILE (\"SDPBS\" VARCHAR(20))");
            statement.execute("INSERT INTO FLOWFILE VALUES ('O''Brien'), ('01'), ('9')");
            try (var result = statement.executeQuery(sql)) {
                for (String label : List.of("含'引号", "短", "未匹配")) {
                    assertTrue(result.next());
                    assertEquals(label, result.getString(1));
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void enumCompilationDoesNotReadDatabasePerField(int fields) {
        var sources = mock(DataSourceConnectionPropertyResolver.class);
        var compiler = new FieldMappingService(sources);
        var mappings = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < fields; i++) mappings.add(Map.of("from", "/code_" + i, "to", "/label_" + i,
                "sourceDataType", "varchar", "transform", Map.of("fn", "enumMap", "args", List.of(Map.of("0", "否", "1", "是")))));
        var plan = compiler.compilePlan(Map.of("version", "1.0", "mappings", mappings));
        assertEquals(fields, plan.outputFields().size());
        verifyNoInteractions(sources);
    }

    @Test
    void deployedBaseQueryUsesTheFixedSqlAndExecutesWithLookupParameters() throws Exception {
        var nifi = mock(NifiClient.class);
        var sequence = new AtomicInteger();
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        HiveServiceFixtures.stubGlobals(nifi);
        when(nifi.listProcessGroups("root")).thenReturn(List.of());
        when(nifi.createProcessGroup(anyString(), anyString(), anyDouble(), anyDouble())).thenReturn(entity("pg"));
        when(nifi.createControllerService(anyString(), anyString(), anyString(), anyMap())).thenAnswer(call -> entity("cs-" + sequence.incrementAndGet()));
        when(nifi.createProcessor(anyString(), anyString(), anyString(), anyDouble(), anyDouble(), anyMap(),
                nullable(String.class), nullable(String.class))).thenAnswer(call -> entity("processor-" + sequence.incrementAndGet()));
        when(nifi.createConnection(anyString(), anyString(), anyString(), anyString(), anyString(), anyList())).thenAnswer(call -> entity("connection-" + sequence.incrementAndGet()));
        var registry = mock(ManifestRegistry.class);
        when(registry.get("source.test")).thenReturn(manifest("/manifests/sources/postgresql.json"));
        when(registry.get("transform.field-mapping")).thenReturn(manifest("/manifests/transforms/field-mapping.json"));
        var mappings = new ArrayList<Object>((List<?>) spec("varchar", Map.of("0", "否", "1", "是"), null).get("mappings"));
        mappings.add(Map.of("from", "/SBXT", "to", "/SBXT_cn", "lookup", Map.of(
                "sql", "SELECT label FROM dictionary WHERE code = ?", "resultColumn", "label", "onMissing", "NULL")));
        mappings.add(Map.of("expression", "LOCALTIMESTAMP", "to", "/ODS_RKSJ"));
        // 来源配置遵循当前时间分片契约；本用例继续验证后续 QueryRecord 的字符集及 lookup 参数。
        var sourceConfig = Map.<String, Object>of("dbType", "POSTGRESQL", "jdbcUrl", "jdbc:postgresql://example.test/database",
                "table", "source_table", "incrementalColumn", "UPDATED_AT");
        var pipeline = new Pipeline("test", "Charset regression", null, null, null,
                new Pipeline.Dsl(1, List.of(
                        new Pipeline.Node("source", "source.test", "来源", "source", 0, 0, sourceConfig),
                        new Pipeline.Node("mapping", "transform.field-mapping", "字段映射", "transform", 1, 0,
                                Map.of("mappings", Map.of("version", "1.0", "mappings", mappings)))),
                        List.of(new Pipeline.Edge("edge", "source", "mapping", null))),
                null, null, null, null, null, null, null);
        new DslCompiler(nifi, registry, service, mock(HiveModule.class)).compile(pipeline);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> properties = ArgumentCaptor.forClass(Map.class);
        verify(nifi).createProcessor(eq("pg"), eq("org.apache.nifi.processors.standard.QueryRecord"),
                eq("字段映射/base-query"), anyDouble(), anyDouble(), properties.capture(), nullable(String.class), nullable(String.class));
        assertEquals("global-json-reader", properties.getValue().get("Record Reader"));
        assertEquals("global-json-writer", properties.getValue().get("Record Writer"));
        verify(nifi).get("/flow/process-groups/root", com.fasterxml.jackson.databind.JsonNode.class);
        verify(nifi).listControllerServices("root");
        String sql = properties.getValue().get("success");
        assertTrue(sql.contains("CONVERT(\"SDPBS\", UTF8, UTF8)"));
        assertEquals(List.of("否", "是"), query(sql,
                List.of(new ColumnSchema("SDPBS", String.class, true), new ColumnSchema("SBXT", String.class, true)),
                List.of(new Object[]{"0", "A"}, new Object[]{"1", "B"})));
    }

    private static NifiEntity entity(String id) { return new NifiEntity(null, Map.of("id", id), null, null); }

    private static ComponentManifest manifest(String path) throws Exception {
        try (var stream = FieldMappingCalciteCharsetTest.class.getResourceAsStream(path)) {
            return new ObjectMapper().readValue(stream, ComponentManifest.class);
        }
    }

    private static Map<String, Object> spec(String type, Map<String, ?> values, Object fallback) {
        return Map.of("version", "1.0", "mappings", List.of(Map.of(
                "from", "/SDPBS", "to", "/SDPBS_cn", "sourceDataType", type,
                "transform", Map.of("fn", "enumMap", "args", Arrays.asList(values, fallback)))));
    }

    static List<Object> query(String sql, List<ColumnSchema> columns, List<Object[]> rows) throws Exception {
        var schema = new NiFiTableSchema(columns);
        var source = new ResettableDataSource() {
            public NiFiTableSchema getSchema() { return schema; }
            public RowStream reset() { return new IterableRowStream<>(rows, row -> row); }
        };
        try (var database = new CalciteDatabase()) {
            database.addTable(new NiFiTable("FLOWFILE", source, mock(ComponentLog.class)));
            try (var statement = database.getConnection().prepareStatement(sql);
                 var result = statement.executeQuery()) {
                var values = new ArrayList<Object>();
                while (result.next()) values.add(result.getObject(1));
                return values;
            }
        }
    }
}
