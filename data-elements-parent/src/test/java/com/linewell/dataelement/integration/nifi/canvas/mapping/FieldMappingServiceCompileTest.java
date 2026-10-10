package com.linewell.dataelement.integration.nifi.canvas.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;

class FieldMappingServiceCompileTest {

    @Test
    void keepsNifiExpressionsOutOfSourceDatabasePushdown() {
        assertFalse(service.supportsSourceDbPushdown("""
                {"version":"1.0","mappings":[
                  {"expression":"LOCALTIMESTAMP","to":"/ODS_RKSJ"},
                  {"from":"/id","to":"/id"}
                ]}
                """));
    }

    private final FieldMappingService service = new FieldMappingService();

    @ParameterizedTest
    @ValueSource(strings = {"int", "NUMBER(10,0)", "numeric(20,4)", "BIGINT UNSIGNED", "DOUBLE PRECISION", "decimal", "float8"})
    void numericEnumCodesAreTypedInRecordAndSourceQueries(String type) {
        var spec = enumSpec(type, Map.of("0", "审核中", "1", "审核通过"));
        String query = service.compileQuery(spec);
        assertTrue(query.contains("WHEN 0 THEN _UTF-8'审核中'"));
        assertTrue(query.contains("WHEN 1 THEN _UTF-8'审核通过'"));
        assertFalse(query.contains("WHEN '0'"));
        String sourceSql = service.compileSourceDbQuery(spec, "source_table", "src", name -> name);
        assertTrue(sourceSql.contains("CASE src.APPLY_FLAG"));
        assertTrue(sourceSql.contains("WHEN 0 THEN"));
        var preview = service.preview(spec, List.of(Map.of("APPLY_FLAG", 0), Map.of("APPLY_FLAG", 1), Map.of("APPLY_FLAG", 9)));
        assertEquals("审核中", preview.resultRows().get(0).get("APPLY_FLAG_cn"));
        assertEquals("审核通过", preview.resultRows().get(1).get("APPLY_FLAG_cn"));
        assertEquals(null, preview.resultRows().get(2).get("APPLY_FLAG_cn"));
    }

    @Test
    void numericComparisonKeepsDecimalPrecisionAndMatchesPreviewByValue() {
        var spec = enumSpec("NUMBER(30,4)", Map.of("9007199254740993", "large", "1.00", "decimal", "-2.5e1", "negative"));
        String sql = service.compileQuery(spec);
        assertTrue(sql.contains("WHEN 9007199254740993 THEN 'large'"));
        assertTrue(sql.contains("WHEN 1.00 THEN 'decimal'"));
        assertTrue(sql.contains("WHEN -25 THEN 'negative'"));
        var preview = service.preview(spec, List.of(Map.of("APPLY_FLAG", new BigDecimal("1.0")), Map.of("APPLY_FLAG", 9007199254740993L), Map.of("APPLY_FLAG", -25)));
        assertEquals("decimal", preview.resultRows().get(0).get("APPLY_FLAG_cn"));
        assertEquals("large", preview.resultRows().get(1).get("APPLY_FLAG_cn"));
        assertEquals("negative", preview.resultRows().get(2).get("APPLY_FLAG_cn"));
    }

    @Test
    void textAndUnknownTypesKeepNumericLookingCodesQuotedIncludingLeadingZeros() {
        for (String type : List.of("varchar(2)", "CHAR", "", "not-a-number-type")) {
            var spec = enumSpec(type, Map.of("01", "first", "1", "second"));
            String sql = service.compileQuery(spec);
            assertTrue(sql.contains("WHEN '01' THEN 'first'"));
            assertTrue(sql.contains("WHEN '1' THEN 'second'"));
            var result = service.preview(spec, List.of(Map.of("APPLY_FLAG", "01"), Map.of("APPLY_FLAG", "1")));
            assertEquals("first", result.resultRows().get(0).get("APPLY_FLAG_cn"));
            assertEquals("second", result.resultRows().get(1).get("APPLY_FLAG_cn"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "0' OR 1=1--", "1; DROP TABLE x", "NaN", "Infinity", "1e999999999", ""})
    void malformedNumericEnumCodesFailBeforeSqlIsProduced(String code) {
        assertThrows(IllegalStateException.class, () -> service.compileQuery(enumSpec("int", Map.of(code, "label"))));
    }

    @Test
    void generatedNumericCaseExecutesAgainstATypedRecordTable() throws Exception {
        var spec = enumSpec("number(10,0)", Map.of("0", "pending", "1", "passed", "2", "returned", "3", "invalid"));
        try (var connection = java.sql.DriverManager.getConnection("jdbc:h2:mem:enum_" + java.util.UUID.randomUUID());
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE FLOWFILE (\"APPLY_FLAG\" NUMBER(10,0))");
            statement.execute("INSERT INTO FLOWFILE VALUES (0), (1), (2), (3), (9), (NULL)");
            try (var results = statement.executeQuery(service.compileQuery(spec))) {
                for (String expected : Arrays.asList("pending", "passed", "returned", "invalid", null, null)) {
                    assertTrue(results.next());
                    assertEquals(expected, results.getString("APPLY_FLAG_cn"));
                }
                assertFalse(results.next());
            }
        }
    }

    private Map<String, Object> enumSpec(String sourceType, Map<String, ?> values) {
        return Map.of("version", "1.0", "mappings", List.of(Map.of("from", "/APPLY_FLAG", "to", "/APPLY_FLAG_cn",
                "sourceDataType", sourceType, "transform", Map.of("fn", "enumMap", "args", Arrays.asList(values, null)))));
    }

    @Test void nestedJsonScalarUsesTheRuntimeSqlOperator() {
        var spec=Map.of("version","1.0","mappings",List.of(Map.of("from","/payload","to","/person_id","transform",Map.of("fn","jsonValue","args",List.of("$.person.id")))));
        assertEquals("SELECT JSON_VALUE(\"payload\", 'lax $.person.id' NULL ON EMPTY NULL ON ERROR) AS \"person_id\" FROM FLOWFILE",service.compilePlan(spec).baseQuery());
        assertFalse(service.supportsSourceDbPushdown(spec));
    }
    @Test void namedExpressionFunctionsAreExpandedAndPreservedWithoutArbitraryCode() {
        var spec=Map.of("version","1.0","functions",Map.of("normalizeCode",Map.of("expression","UPPER(TRIM(${value}))")),"mappings",List.of(Map.of("from","/name","to","/normalized","transform",Map.of("fn","user:normalizeCode","args",List.of()))));
        var normalized=service.normalizeSpec(spec);assertTrue(normalized.path("mappings").get(0).has("userFunction"));
        assertEquals("SELECT UPPER(TRIM(\"name\")) AS \"normalized\" FROM FLOWFILE",service.compilePlan(normalized).baseQuery());
        assertEquals(service.compilePlan(spec).baseQuery(),service.compilePlan(normalized).baseQuery());
        var preview=service.preview(spec,List.of(Map.of("name","  a'|b  ")));
        assertTrue(preview.rowResults().getFirst().success());
        assertEquals("A'|B",preview.resultRows().getFirst().get("normalized"));
    }

    @Test void unsupportedPreviewDoesNotReturnExpressionTextAsSuccessfulData() {
        var spec=Map.of("version","1.0","mappings",List.of(Map.of("expression","SUBSTRING(${field:name},1,2)","to","/name")));
        assertFalse(service.preview(spec,List.of(Map.of("name","Alpha"))).rowResults().getFirst().success());
        assertTrue(service.compilePlan(spec).baseQuery().contains("SUBSTRING"));
    }

    @Test void previewJsonScalarAndNullPropagationAreRealValues() {
        var spec=Map.of("version","1.0","mappings",List.of(Map.of("from","/payload","to","/id","transform",Map.of("fn","jsonValue","args",List.of("$.person.id"))),Map.of("expression","COALESCE(${field:name},'default')","to","/name")));
        var preview=service.preview(spec,List.of(Map.of("payload","{\"person\":{\"id\":\"A1\"}}")));
        assertTrue(preview.rowResults().getFirst().success());assertEquals("A1",preview.resultRows().getFirst().get("id"));assertEquals("default",preview.resultRows().getFirst().get("name"));
    }

    @Test
    void compilesMandatoryStandardValuesIntoUtf8ChineseTranslationCaseExpression() {
        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("01", "刑事案件");
        labels.put("02", "治安案件");
        Map<String, Object> spec = Map.of(
                "version", "1.0",
                "mappings", List.of(Map.of(
                        "from", "/case_category_code",
                        "to", "/case_category_code_cn",
                        "transform", Map.of("fn", "enumMap", "args", Arrays.asList(labels, null)))));

        FieldMappingService.CompiledMapping plan = service.compilePlan(spec);

        assertEquals("SELECT CASE \"case_category_code\" WHEN '01' THEN _UTF-8'刑事案件' WHEN '02' THEN _UTF-8'治安案件' ELSE NULL END AS \"case_category_code_cn\" FROM FLOWFILE",
                plan.baseQuery());
        assertEquals("SELECT \"case_category_code_cn\" AS \"case_category_code_cn\" FROM FLOWFILE", plan.finalQuery());
    }

    @Test
    void previewUsesTheSameMandatoryStandardTranslation() {
        Map<String, Object> labels = new LinkedHashMap<>();
        labels.put("01", "刑事案件");
        Map<String, Object> spec = Map.of(
                "version", "1.0",
                "mappings", List.of(Map.of(
                        "from", "/case_category_code",
                        "to", "/case_category_code_cn",
                        "transform", Map.of("fn", "enumMap", "args", Arrays.asList(labels, null)))));

        FieldMappingService.PreviewResponse preview = service.preview(spec, List.of(Map.of("case_category_code", "01")));

        assertTrue(preview.rowResults().getFirst().success());
        assertEquals("刑事案件", preview.resultRows().getFirst().get("case_category_code_cn"));
    }

    @Test
    void compilesEnumAndSameDatabaseDictionaryRulesIntoOneSourceQuery() {
        String spec = """
                {
                  "version":"1.0",
                  "mappings":[
                    {"from":"/id","to":"/id"},
                    {"from":"/gender_code","to":"/gender_code_cn",
                     "transform":{"fn":"enumMap","args":[{"M":"男","F":"女"},null]}},
                    {"from":"/case_type_code","to":"/case_type_code_cn",
                     "lookup":{"sql":"SELECT case_type_name FROM dict_case_type WHERE case_type_code = ?",
                     "resultColumn":"case_type_name","onMissing":"NULL"}}
                  ]
                }
                """;

        String sql = service.compileSourceDbQuery(spec, "`demo_case`", "src", field -> "`" + field + "`");

        assertTrue(sql.startsWith("SELECT src.`id` AS `id`, CASE src.`gender_code`"));
        assertTrue(sql.contains("WHEN 'M' THEN _UTF-8'男'"));
        assertTrue(sql.contains("(SELECT case_type_name FROM dict_case_type WHERE case_type_code = src.`case_type_code`) AS `case_type_code_cn`"));
        assertTrue(sql.endsWith("FROM `demo_case` src"));
    }
}
