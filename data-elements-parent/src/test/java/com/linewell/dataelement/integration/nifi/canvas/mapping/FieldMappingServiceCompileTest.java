package com.linewell.dataelement.integration.nifi.canvas.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
