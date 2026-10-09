package com.linewell.dataelement.integration.nifi.canvas.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessAggTaskT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessFieldMapping;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessAggTaskTService;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessFieldMappingService;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccessTaskFieldMappingBinderTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void restoresDictionaryMarkerAndMandatoryStandardRulesBeforeDeploy() throws Exception {
        IDataAccessAggTaskTService tasks = mock(IDataAccessAggTaskTService.class);
        IDataAccessFieldMappingService mappings = mock(IDataAccessFieldMappingService.class);
        DataAccessAggTaskT task = new DataAccessAggTaskT().setTid("task-1");
        when(tasks.findByPipelineId("pipeline-1")).thenReturn(task);
        when(mappings.list(any(Wrapper.class))).thenReturn(List.of(
                dictionaryMarkerRule(), mandatoryCertificateRule(), systemUpdateRule()));

        Pipeline pipeline = pipelineWithLegacyDirectMappings();
        Pipeline bound = new AccessTaskFieldMappingBinder(tasks, mappings).bind(pipeline);

        assertNotNull(bound);
        JsonNode spec = mappingsSpec(bound);
        JsonNode gender = mapping(spec, "/gender_code_cn");
        assertEquals("/gender_code", gender.path("from").asText());
        assertEquals("SELECT gender_name AS gender_code_cn FROM dict_gender WHERE gender_code = ?",
                gender.path("lookup").path("sql").asText());
        assertEquals("mysql", gender.path("lookup").path("dataSource").path("dbType").asText());
        assertEquals("jdbc:mysql://source-host:3306/personaldb",
                gender.path("lookup").path("dataSource").path("jdbcUrl").asText());

        JsonNode certificate = mapping(spec, "/source_certificate_type_cn");
        assertEquals("enumMap", certificate.path("transform").path("fn").asText());
        assertEquals("身份证", certificate.path("transform").path("args").get(0).path("IDCARD").asText());

        assertEquals("LOCALTIMESTAMP", mapping(spec, "/ODS_GXSJ").path("expression").asText());
    }

    @Test
    void restoresFullRegisteredDictionaryAndMandatoryStandardContractsForEnrichmentNode() throws Exception {
        IDataAccessAggTaskTService tasks = mock(IDataAccessAggTaskTService.class);
        IDataAccessFieldMappingService mappings = mock(IDataAccessFieldMappingService.class);
        DataAccessAggTaskT task = new DataAccessAggTaskT().setTid("task-2");
        when(tasks.findByPipelineId("pipeline-2")).thenReturn(task);
        when(mappings.list(any(Wrapper.class))).thenReturn(List.of(
                registeredDictionaryRule(), registeredMandatoryStandardRule()));

        Pipeline.Node mapping = new Pipeline.Node("mapping", "transform.field-enrichment", "字段增强", "transform",
                0, 0, Map.of("mappings", "{\"version\":\"1.0\",\"mappings\":["
                        + "{\"from\":\"/case_status_code\",\"to\":\"/case_status_code_cn\"},"
                        + "{\"from\":\"/source_occupation_type\",\"to\":\"/source_occupation_type_cn\"}]}"));
        Pipeline pipeline = new Pipeline("pipeline-2", "test", null, 1L, 1L,
                new Pipeline.Dsl(1, List.of(mapping), List.of()), null, null,
                null, null, null, null, null);

        Pipeline bound = new AccessTaskFieldMappingBinder(tasks, mappings).bind(pipeline);
        JsonNode spec = mappingsSpec(bound);

        JsonNode caseStatus = mapping(spec, "/case_status_code_cn");
        assertEquals("SELECT status_name AS case_status_code_cn FROM demo_dict_case_status "
                        + "WHERE status_code = ? AND is_enabled = 1",
                caseStatus.path("lookup").path("sql").asText());
        assertEquals("jdbc:mysql://dictionary-host:3306/personaldb",
                caseStatus.path("lookup").path("dataSource").path("jdbcUrl").asText());

        JsonNode occupation = mapping(spec, "/source_occupation_type_cn");
        assertEquals("enumMap", occupation.path("transform").path("fn").asText());
        assertEquals("个体经营者", occupation.path("transform").path("args").get(0).path("SELF_EMPLOYED").asText());
        assertEquals("未映射", occupation.path("transform").path("args").get(1).asText());
    }

    @Test
    void restoresMultiValueStandardAsLookupInsteadOfSingleValueEnum() throws Exception {
        IDataAccessAggTaskTService tasks = mock(IDataAccessAggTaskTService.class);
        IDataAccessFieldMappingService mappings = mock(IDataAccessFieldMappingService.class);
        when(tasks.findByPipelineId("pipeline-manual")).thenReturn(new DataAccessAggTaskT().setTid("task-multi"));
        when(mappings.list(any(Wrapper.class))).thenReturn(List.of(new DataAccessFieldMapping()
                .setSourceField("gender_code").setTargetField("gender_code_cn")
                .setFuncCode("ENUM_MAP").setFuncEnable(1).setIsDel(0)
                .setFuncValue("""
                        {"ruleKind":"MANDATORY_STANDARD","values":{"U":"未知","F":"女"},
                         "multiValue":true,"multiValueSeparator":","}
                        """)));
        Pipeline.Node source = new Pipeline.Node("source", "source.mysql", "来源表", "source",
                0, 0, Map.of("dbType", "MYSQL", "datasourceId", "source-1"));
        Pipeline.Node mapping = new Pipeline.Node("mapping", "transform.field-enrichment", "字段增强", "transform",
                100, 0, Map.of("mappings", "{\"version\":\"1.0\",\"mappings\":[]} "));
        Pipeline pipeline = new Pipeline("pipeline-manual", "test", null, 1L, 1L,
                new Pipeline.Dsl(1, List.of(source, mapping), List.of()), null, null,
                null, null, null, null, null);

        JsonNode spec = mappingsSpec(new AccessTaskFieldMappingBinder(tasks, mappings).bind(pipeline));
        JsonNode lookup = mapping(spec, "/gender_code_cn").path("lookup");
        assertTrue(lookup.path("multiValue").asBoolean());
        assertEquals("未知", lookup.path("values").path("U").asText());
        assertFalse(lookup.has("sql"));
        assertFalse(lookup.has("dataSource"));
    }

    @Test
    void replacesLegacyLookupAccidentallyAttachedToRawDictionaryCodeField() throws Exception {
        IDataAccessAggTaskTService tasks = mock(IDataAccessAggTaskTService.class);
        IDataAccessFieldMappingService mappings = mock(IDataAccessFieldMappingService.class);
        when(tasks.findByPipelineId("pipeline-3")).thenReturn(new DataAccessAggTaskT().setTid("task-3"));
        when(mappings.list(any(Wrapper.class))).thenReturn(List.of(
                rawDictionaryCodePassThroughRule(), registeredDictionaryRule()));

        Pipeline.Node mapping = new Pipeline.Node("mapping", "transform.field-enrichment", "字段增强", "transform",
                0, 0, Map.of("mappings", """
                        {"version":"1.0","mappings":[
                          {"from":"/case_status_code","to":"/case_status_code","lookup":{"sql":"SELECT status_name FROM demo_dict_case_status"}},
                          {"from":"/case_status_code","to":"/case_status_code_cn","lookup":{"sql":"SELECT status_name AS case_status_code_cn FROM demo_dict_case_status WHERE status_code = ?"}}
                        ]}
                        """));
        Pipeline pipeline = new Pipeline("pipeline-3", "test", null, 1L, 1L,
                new Pipeline.Dsl(1, List.of(mapping), List.of()), null, null,
                null, null, null, null, null);

        Pipeline bound = new AccessTaskFieldMappingBinder(tasks, mappings).bind(pipeline);
        JsonNode spec = mappingsSpec(bound);
        JsonNode rawCode = mapping(spec, "/case_status_code");
        assertEquals("/case_status_code", rawCode.path("from").asText());
        assertTrue(rawCode.path("lookup").isMissingNode());

        JsonNode translation = mapping(spec, "/case_status_code_cn");
        assertTrue(translation.has("lookup"));
        assertEquals("jdbc:mysql://dictionary-host:3306/personaldb",
                translation.path("lookup").path("dataSource").path("jdbcUrl").asText());
    }

    @Test
    void addsReadableLegacyAliasesToNumericMandatoryStandardRules() throws Exception {
        IDataAccessAggTaskTService tasks = mock(IDataAccessAggTaskTService.class);
        IDataAccessFieldMappingService mappings = mock(IDataAccessFieldMappingService.class);
        when(tasks.findByPipelineId("pipeline-4")).thenReturn(new DataAccessAggTaskT().setTid("task-4"));
        when(mappings.list(any(Wrapper.class))).thenReturn(List.of(
                numericCertificateRule(), numericMaritalRule()));

        Pipeline.Node mapping = new Pipeline.Node("mapping", "transform.field-enrichment", "字段增强", "transform",
                0, 0, Map.of("mappings", """
                        {"version":"1.0","mappings":[
                          {"from":"/source_certificate_type","to":"/source_certificate_type_cn","transform":{"fn":"enumMap","args":[{"111":"居民身份证","414":"普通护照","516":"港澳居民来往内地通行证","990":"其他"},"未映射"]}},
                          {"from":"/source_marital_status","to":"/source_marital_status_cn","transform":{"fn":"enumMap","args":[{"10":"未婚","20":"已婚","30":"丧偶","40":"离婚","90":"其他"},"未映射"]}}
                        ]}
                        """));
        Pipeline pipeline = new Pipeline("pipeline-4", "test", null, 1L, 1L,
                new Pipeline.Dsl(1, List.of(mapping), List.of()), null, null,
                null, null, null, null, null);

        JsonNode spec = mappingsSpec(new AccessTaskFieldMappingBinder(tasks, mappings).bind(pipeline));
        JsonNode certificateValues = mapping(spec, "/source_certificate_type_cn")
                .path("transform").path("args").get(0);
        JsonNode maritalValues = mapping(spec, "/source_marital_status_cn")
                .path("transform").path("args").get(0);
        assertEquals("居民身份证", certificateValues.path("IDCARD").asText());
        assertEquals("普通护照", certificateValues.path("PASSPORT").asText());
        assertEquals("港澳居民来往内地通行证", certificateValues.path("HKM_PERMIT").asText());
        assertEquals("已婚", maritalValues.path("MARRIED").asText());
        assertEquals("未婚", maritalValues.path("SINGLE").asText());
        assertEquals("其他", maritalValues.path("UNKNOWN").asText());
    }

    @Test
    void mergesMissingSystemRuleWithoutErasingManualExpressionsFunctionsOrOptions() throws Exception {
        IDataAccessAggTaskTService tasks = mock(IDataAccessAggTaskTService.class);
        IDataAccessFieldMappingService mappings = mock(IDataAccessFieldMappingService.class);
        when(tasks.findByPipelineId("pipeline-manual")).thenReturn(new DataAccessAggTaskT().setTid("task-manual"));
        when(mappings.list(any(Wrapper.class))).thenReturn(List.of(
                new DataAccessFieldMapping().setSourceField("label").setTargetField("label").setIsDel(0),
                systemUpdateRule()));
        Pipeline pipeline = manualPipeline("""
                {"version":"1.0","passthroughUnmapped":true,"onMissingSource":"ERROR",
                 "functions":{"decorate":{"expression":"CONCAT(${value},${arg:0})"}},
                 "mappings":[
                   {"to":"/label","expression":"UPPER(${field:label})"},
                   {"to":"/extra","from":"/label","userFunction":{"name":"decorate","args":["!"]}}
                 ]}
                """);
        AccessTaskFieldMappingBinder binder = new AccessTaskFieldMappingBinder(tasks, mappings);
        Pipeline bound = binder.bind(pipeline);
        JsonNode spec = mappingsSpec(bound);
        assertEquals("UPPER(${field:label})", mapping(spec, "/label").path("expression").asText());
        assertEquals("decorate", mapping(spec, "/extra").path("userFunction").path("name").asText());
        assertEquals("CONCAT(${value},${arg:0})", spec.path("functions").path("decorate").path("expression").asText());
        assertTrue(spec.path("passthroughUnmapped").asBoolean());
        assertEquals("ERROR", spec.path("onMissingSource").asText());
        assertEquals("LOCALTIMESTAMP", mapping(spec, "/ODS_GXSJ").path("expression").asText());
        assertEquals(470, bound.dsl().nodes().get(0).x());
        assertEquals("kept", bound.dsl().nodes().get(0).config().get("customOption"));
        assertSame(bound, binder.bind(bound), "A second read must not persist another repair");
    }

    @Test
    void preservesManualLookupOnOrdinaryRegisteredFieldAndDatasourceReference() throws Exception {
        IDataAccessAggTaskTService tasks = mock(IDataAccessAggTaskTService.class);
        IDataAccessFieldMappingService mappings = mock(IDataAccessFieldMappingService.class);
        when(tasks.findByPipelineId("pipeline-manual")).thenReturn(new DataAccessAggTaskT().setTid("task-manual"));
        when(mappings.list(any(Wrapper.class))).thenReturn(List.of(
                new DataAccessFieldMapping().setSourceField("label").setTargetField("label").setIsDel(0),
                registeredDictionaryRule(), systemUpdateRule()));
        Pipeline pipeline = manualPipeline("""
                {"version":"1.0","mappings":[
                  {"to":"/label","from":"/label","lookup":{"sql":"SELECT label FROM labels WHERE code = ?","dataSource":{"datasourceId":"owned-source"}}},
                  {"to":"/case_status_code_cn","from":"/case_status_code","lookup":{"sql":"SELECT status_name FROM statuses WHERE code = ?","dataSource":{"datasourceId":"owned-dictionary"}}}
                ]}
                """);
        JsonNode spec = mappingsSpec(new AccessTaskFieldMappingBinder(tasks, mappings).bind(pipeline));
        assertEquals("owned-source", mapping(spec, "/label").path("lookup").path("dataSource").path("datasourceId").asText());
        assertEquals("owned-dictionary", mapping(spec, "/case_status_code_cn").path("lookup").path("dataSource").path("datasourceId").asText());
        assertEquals("SELECT label FROM labels WHERE code = ?", mapping(spec, "/label").path("lookup").path("sql").asText());
    }

    @Test
    void invalidPersistedSpecFailsWithoutReplacingOriginal() {
        IDataAccessAggTaskTService tasks = mock(IDataAccessAggTaskTService.class);
        IDataAccessFieldMappingService mappings = mock(IDataAccessFieldMappingService.class);
        when(tasks.findByPipelineId("pipeline-manual")).thenReturn(new DataAccessAggTaskT().setTid("task-manual"));
        when(mappings.list(any(Wrapper.class))).thenReturn(List.of(systemUpdateRule()));
        Pipeline original = manualPipeline("{bad-json");
        assertThrows(IllegalStateException.class, () -> new AccessTaskFieldMappingBinder(tasks, mappings).bind(original));
        assertEquals("{bad-json", original.dsl().nodes().get(0).config().get("mappings"));
    }

    @Test
    void emptyLegacySpecStillBuildsAllRegisteredFields() throws Exception {
        IDataAccessAggTaskTService tasks = mock(IDataAccessAggTaskTService.class);
        IDataAccessFieldMappingService mappings = mock(IDataAccessFieldMappingService.class);
        when(tasks.findByPipelineId("pipeline-manual")).thenReturn(new DataAccessAggTaskT().setTid("task-manual"));
        when(mappings.list(any(Wrapper.class))).thenReturn(List.of(
                new DataAccessFieldMapping().setSourceField("label").setTargetField("label").setIsDel(0),
                systemUpdateRule()));
        JsonNode spec = mappingsSpec(new AccessTaskFieldMappingBinder(tasks, mappings).bind(manualPipeline("")));
        assertEquals("/label", mapping(spec, "/label").path("from").asText());
        assertEquals("LOCALTIMESTAMP", mapping(spec, "/ODS_GXSJ").path("expression").asText());
    }

    private Pipeline manualPipeline(String spec) {
        Pipeline.Node mapping = new Pipeline.Node("mapping", "transform.field-mapping", "字段映射", "transform",
                470, 230, Map.of("mappings", spec, "customOption", "kept"));
        return new Pipeline("pipeline-manual", "test", null, 1L, 1L,
                new Pipeline.Dsl(1, List.of(mapping), List.of()), null, null,
                null, null, null, null, null);
    }

    private DataAccessFieldMapping registeredDictionaryRule() {
        return new DataAccessFieldMapping()
                .setSourceField("case_status_code")
                .setTargetField("case_status_code_cn")
                .setDictEnable(1)
                .setFuncEnable(1)
                .setFuncCode("LOOKUP")
                .setFuncValue("""
                        {"ruleKind":"DICTIONARY","table":"demo_dict_case_status",
                         "keyField":"status_code","labelField":"status_name",
                         "sql":"SELECT status_name AS case_status_code_cn FROM demo_dict_case_status WHERE status_code = ? AND is_enabled = 1",
                         "dataSource":{"jdbcUrl":"jdbc:mysql://dictionary-host:3306/personaldb"}}
                        """)
                .setIsDel(0);
    }

    private DataAccessFieldMapping rawDictionaryCodePassThroughRule() {
        return new DataAccessFieldMapping()
                .setSourceField("case_status_code")
                .setSourceFieldCn("【字典关联】demo_dict_case_status.status_code")
                .setTargetField("case_status_code")
                .setIsDel(0);
    }

    private DataAccessFieldMapping registeredMandatoryStandardRule() {
        return new DataAccessFieldMapping()
                .setSourceField("source_occupation_type")
                .setTargetField("source_occupation_type_cn")
                .setFuncEnable(1)
                .setFuncCode("ENUM_MAP")
                .setFuncValue("""
                        {"ruleKind":"MANDATORY_STANDARD","values":{
                          "SELF_EMPLOYED":"个体经营者","ENTERPRISE_STAFF":"企业职员"
                        }}
                        """)
                .setIsDel(0);
    }

    private DataAccessFieldMapping dictionaryMarkerRule() {
        return new DataAccessFieldMapping()
                .setSourceField("gender_code")
                .setSourceFieldCn("【字典关联】dict_gender.gender_code")
                .setTargetField("gender_code_cn")
                // This is the malformed, already-created registration shape.
                .setDictEnable(0)
                .setFuncEnable(1)
                .setFuncCode("ENUM_MAP")
                .setFuncValue("MANDATORY_STANDARD")
                .setIsDel(0);
    }

    private DataAccessFieldMapping mandatoryCertificateRule() {
        return new DataAccessFieldMapping()
                .setSourceField("source_certificate_type")
                .setTargetField("source_certificate_type_cn")
                .setFuncEnable(1)
                .setFuncCode("ENUM_MAP")
                .setFuncValue("MANDATORY_STANDARD")
                .setIsDel(0);
    }

    private DataAccessFieldMapping numericCertificateRule() {
        return new DataAccessFieldMapping()
                .setSourceField("source_certificate_type")
                .setTargetField("source_certificate_type_cn")
                .setFuncEnable(1)
                .setFuncCode("ENUM_MAP")
                .setFuncValue("""
                        {"ruleKind":"MANDATORY_STANDARD","values":{
                          "111":"居民身份证","414":"普通护照",
                          "516":"港澳居民来往内地通行证","990":"其他"}}
                        """)
                .setIsDel(0);
    }

    private DataAccessFieldMapping numericMaritalRule() {
        return new DataAccessFieldMapping()
                .setSourceField("source_marital_status")
                .setTargetField("source_marital_status_cn")
                .setFuncEnable(1)
                .setFuncCode("ENUM_MAP")
                .setFuncValue("""
                        {"ruleKind":"MANDATORY_STANDARD","values":{
                          "10":"未婚","20":"已婚","30":"丧偶","40":"离婚","90":"其他"}}
                        """)
                .setIsDel(0);
    }

    private DataAccessFieldMapping systemUpdateRule() {
        return new DataAccessFieldMapping()
                .setSourceField("SYSTEM:ODS_GXSJ")
                .setTargetField("ODS_GXSJ")
                .setFuncEnable(1)
                .setFuncCode("EXPRESSION")
                .setFuncValue("LOCALTIMESTAMP")
                .setIsDel(0);
    }

    private Pipeline pipelineWithLegacyDirectMappings() {
        String spec = """
                {"version":"1.0","mappings":[
                  {"from":"/gender_code","to":"/gender_code_cn"},
                  {"from":"/source_certificate_type","to":"/source_certificate_type_cn"}
                ]}
                """;
        Pipeline.Node source = new Pipeline.Node("source", "source.mysql", "来源表", "source",
                -100, 0, Map.of(
                        "sourceDbId", "source-db",
                        "host", "source-host",
                        "port", "3306",
                        "database", "personaldb",
                        "jdbcUrl", "jdbc:mysql://source-host:3306/personaldb",
                        "username", "source-user",
                        "password", "source-password"));
        Pipeline.Node mapping = new Pipeline.Node("mapping", "transform.field-mapping", "字段映射", "transform",
                0, 0, Map.of("mappings", spec));
        return new Pipeline("pipeline-1", "test", null, 1L, 1L,
                new Pipeline.Dsl(1, List.of(source, mapping), List.of()), null, null,
                null, null, null, null, null);
    }

    private JsonNode mapping(JsonNode spec, String target) {
        for (JsonNode item : spec.path("mappings")) {
            if (target.equals(item.path("to").asText())) {
                return item;
            }
        }
        throw new AssertionError("Missing mapping: " + target);
    }

    private JsonNode mappingsSpec(Pipeline pipeline) throws Exception {
        Pipeline.Node mappingNode = pipeline.dsl().nodes().stream()
                .filter(node -> "transform.field-mapping".equals(node.manifestKey())
                        || "transform.field-enrichment".equals(node.manifestKey()))
                .findFirst()
                .orElseThrow();
        return JSON.readTree(String.valueOf(mappingNode.config().get("mappings")));
    }
}
