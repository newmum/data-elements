package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MappingDiagnosticsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ExecutionContext CONTEXT = new ExecutionContext(Instant.EPOCH, ZoneId.of("Asia/Shanghai"), Locale.CHINA, "diagnostics-test");
    private MappingEngine.Issue error(String spec, Map<String,Object> row) throws Exception {
        var result = MappingEngine.execute(MappingEngine.compile(JSON.readTree(spec)), List.of(row), 1, CONTEXT, null);
        assertFalse(result.rows().getFirst().success());
        return result.rows().getFirst().errors().getFirst();
    }
    @Test void decimalFailureIdentifiesBothFieldsOriginalValueCapacityAndActualRoute() throws Exception {
        var error = error("""
          {"version":"1.0","recordErrorMode":"ROUTE_INVALID","mappings":[{"from":"MAPX","fromLabel":"来源经度","to":"longitude","toLabel":"经度","targetType":"DECIMAL(8,2)"}]}
          """, Map.of("MAPX", new BigDecimal("119.379023855114")));
        assertEquals("TYPE_MISMATCH", error.code()); assertEquals("target", error.stepId());
        assertEquals("longitude", error.targetField()); assertEquals("经度", error.targetComment());
        assertEquals("MAPX", error.sourceFields().getFirst().name()); assertEquals("来源经度", error.sourceFields().getFirst().comment());
        assertEquals(new BigDecimal("119.379023855114"), error.problemValue());
        for (String text : List.of("第2条记录", "MAPX（来源经度）", "longitude（经度）", "119.379023855114", "DECIMAL(8,2)", "目标类型及约束校验", "invalid")) assertTrue(error.message().contains(text), error.message());
        assertTrue(error.reason().contains("小数位数"));
    }
    @Test void separatesOriginalInputFromTheTransformedValueThatActuallyFailed() throws Exception {
        var error = error("""
          {"schemaVersion":"2.0","rules":[{"ruleId":"number","target":{"name":"amount"},"input":{"kind":"FIELD","name":"raw"},"steps":[
            {"stepId":"trim","operator":"FUNCTION","args":{"function":"trim"}},
            {"stepId":"integer","operator":"CAST","args":{"logicalType":"INT"}}]}]}
          """, Map.of("raw", "  abc  "));
        assertEquals("integer", error.stepId()); assertEquals("CAST", error.operator());
        assertEquals("  abc  ", error.inputValue()); assertEquals("abc", error.problemValue());
        assertEquals("  abc  ", error.sourceFields().getFirst().value());
        assertTrue(error.effect().contains("整批输入转入 failure"));
    }
    @Test void inputExpressionFailureStillIncludesReferencedFieldAndValue() throws Exception {
        var error = error("""
          {"schemaVersion":"2.0","rules":[{"ruleId":"expression","target":{"name":"amount"},"input":{"kind":"EXPRESSION","expression":"CAST(${field:raw} AS DECIMAL)"}}]}
          """, Map.of("raw", "not-a-number"));
        assertEquals("input", error.stepId()); assertEquals("not-a-number", error.problemValue());
        assertTrue(error.message().contains("not-a-number"));
    }
    @Test void invalidCalendarDateShowsParserAndPattern() throws Exception {
        var error = error("""
          {"version":"1.0","mappings":[{"from":"day","to":"DAY","fromLabel":"业务日期","transform":{"fn":"parseDate","args":["AUTO","DATE"]}}]}
          """, Map.of("day", "20250230"));
        assertEquals("DATE_INVALID", error.code()); assertEquals("20250230", error.problemValue());
        assertTrue(error.message().contains("业务日期"));
    }
    @Test void multivalueDictionaryPointsToUnmatchedTokenRatherThanOnlyWholeInput() throws Exception {
        var error = error("""
          {"schemaVersion":"2.0","rules":[{"ruleId":"codes","target":{"name":"labels"},"input":{"kind":"FIELD","name":"codes"},"steps":[{"operator":"LOOKUP_MULTI","args":{"values":{"A":"甲"},"partialMissingPolicy":"ERROR"}}]}]}
          """, Map.of("codes", "A,UNKNOWN"));
        assertEquals("DICT_MISSING", error.code()); assertEquals("A,UNKNOWN", error.inputValue());
        assertEquals("UNKNOWN", error.problemValue()); assertEquals("LOOKUP_MULTI", error.operator());
    }
    @Test void missingNullAndEmptyRemainDifferentAndCaseInsensitiveValuesAreResolved() throws Exception {
        String spec="""
          {"version":"1.0","onMissingSource":"FAIL","passthroughCaseSensitive":false,"mappings":[{"from":"raw","to":"amount","targetType":"INT","targetNullable":false}]}
          """;
        var missing=error(spec,Map.of()); assertTrue(missing.sourceFields().getFirst().missing()); assertEquals("MISSING_SOURCE",missing.code());
        var row=new HashMap<String,Object>();row.put("raw",null);
        var nil=error(spec,row);assertNull(nil.problemValue());assertFalse(nil.sourceFields().getFirst().missing());
        var empty=error(spec,Map.of("RAW",""));assertEquals("",empty.problemValue());assertEquals("",empty.sourceFields().getFirst().value());
        assertTrue(empty.message().contains("\"\""));
    }
    @Test void onlyExecutedBranchContributesValuesAndItsActualOperatorIsReported() throws Exception {
        var error=error("""
          {"schemaVersion":"2.0","rules":[{"ruleId":"branch","target":{"name":"result"},"when":"${field:flag} = 1",
          "then":{"input":{"kind":"FIELD","name":"unused"},"steps":[{"operator":"CAST","args":{"logicalType":"INT"}}]},
          "else":{"input":{"kind":"FIELD","name":"actual"},"steps":[{"operator":"DATE_PARSE","args":{"pattern":"AUTO","logicalType":"DATE"}}]}}]}
          """,Map.of("flag",0,"actual","invalid-day","unused","not-executed"));
        assertEquals("DATE_PARSE",error.operator());assertTrue(error.expected().contains("日期格式=AUTO"));
        assertFalse(error.sourceFields().stream().anyMatch(f->f.name().equals("unused")));
        assertTrue(error.sourceFields().stream().anyMatch(f->f.name().equals("actual")));
    }
    @Test void legacySchemaCommentsAreKeptAndUnknownCommentsAreExplicit() throws Exception {
        var error=error("""
          {"version":"1.0","schemaContract":{"source":[{"name":"x","comment":"源数量"}],"target":[{"name":"y","comment":"目标数量","logicalType":"INT"}]},"mappings":[{"from":"x","to":"y"}]}
          """,Map.of("x","bad"));
        assertEquals("源数量",error.sourceFields().getFirst().comment());assertEquals("目标数量",error.targetComment());
        var unknown=error("{\"version\":\"1.0\",\"mappings\":[{\"constant\":\"bad\",\"to\":\"y\",\"targetType\":\"INT\"}]}",Map.of());
        assertTrue(unknown.sourceFields().isEmpty());assertTrue(unknown.message().contains("未登记中文注释"));
        assertEquals("bad",unknown.problemValue());
    }
    @Test void integerFractionAndOverflowHaveDifferentActionableReasons() throws Exception {
        String spec="{\"version\":\"1.0\",\"mappings\":[{\"from\":\"x\",\"to\":\"y\",\"targetType\":\"INT\"}]}";
        assertTrue(error(spec,Map.of("x","12.25")).reason().contains("非零小数位"));
        assertTrue(error(spec,Map.of("x","2147483648")).reason().contains("[-2147483648,2147483647]"));
    }
    @Test void valuesInControlledErrorContentNeverLeakThroughPreviewOrFailureSummary() throws Exception {
        var error=error("""
          {"version":"1.0","mappings":[{"from":"private_value","fromLabel":"手机号","to":"value","targetType":"INT"}]}
          """,Map.of("private_value","synthetic-private-value"));
        assertTrue(error.message().contains("synthetic-private-value"));
        assertFalse(MappingDiagnostics.summary(error).contains("synthetic-private-value"));
        assertFalse(MappingDiagnostics.previewMessage(error,Set.of(),false).contains("synthetic-private-value"));
        assertTrue(MappingDiagnostics.summary(error).contains("private_value"));
    }
    @Test void longValuesAreBoundedAndJsonEscapingRemainsValid() throws Exception {
        String value="\"\n"+"😀".repeat(1000);
        var error=error("{\"version\":\"1.0\",\"mappings\":[{\"from\":\"x\",\"to\":\"y\",\"targetType\":\"INT\"}]}",Map.of("x",value));
        assertTrue(error.valueTruncated());assertTrue(error.sourceFields().getFirst().truncated());
        assertTrue(error.problemValue().toString().length()<1200);assertFalse(error.message().contains("\n"));
        assertEquals(error.message(),JSON.readTree(JSON.writeValueAsBytes(error)).path("message").asText());
    }
    @Test void twentyAndHundredFailedRowsKeepOneDictionaryBatchAndNoMetadataLookups() throws Exception {
        var plan=MappingEngine.compile(JSON.readTree("""
          {"version":"1.0","recordErrorMode":"ROUTE_INVALID","dictionaryBindings":{"d":{"datasourceId":"db","tableId":"table","table":"dictionary","keyColumns":["code"],"keyTypes":["STRING"],"valueColumn":"label"}},"mappings":[{"from":"code","fromLabel":"业务编码","to":"label","lookup":{"dictionaryId":"d","onMissing":"ERROR"}}]}
          """));
        for(int count:List.of(20,100)) {
            int[] calls={0};DictionarySpec.Provider provider=(definition,keys)->{calls[0]++;assertEquals(count,keys.size());return Map.of();};
            var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<count;i++)rows.add(Map.of("code","missing-"+i));
            var result=MappingEngine.execute(plan,rows,0,CONTEXT,provider);assertEquals(1,calls[0]);assertEquals(1,result.dictionaryBatches());
            for(int i=0;i<count;i++){var issue=result.rows().get(i).errors().getFirst();assertEquals("DICT_MISSING",issue.code());assertEquals("missing-"+i,issue.problemValue());assertEquals("业务编码",issue.sourceFields().getFirst().comment());}
        }
    }
    @Test void unusuallyLongCommentsCannotAmplifyEveryRowsErrorAndTargetCommentsAreMaskedInPreview() throws Exception {
        var spec=JSON.readTree("{\"version\":\"1.0\",\"mappings\":[{\"from\":\"x\",\"to\":\"y\",\"targetType\":\"INT\"}]}");
        ((com.fasterxml.jackson.databind.node.ObjectNode)spec.path("mappings").get(0)).put("fromLabel","说明".repeat(10000)).put("toLabel","手机号");
        var issue=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("x","synthetic-private-value")),0,CONTEXT,null).rows().getFirst().errors().getFirst();
        assertTrue(issue.sourceFields().getFirst().comment().length()<520);assertTrue(issue.message().length()<2000);
        assertFalse(MappingDiagnostics.previewMessage(issue,Set.of(),false).contains("synthetic-private-value"));
    }
}
