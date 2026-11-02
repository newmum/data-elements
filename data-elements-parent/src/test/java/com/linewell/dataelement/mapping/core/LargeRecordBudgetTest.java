package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LargeRecordBudgetTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final ExecutionContext CONTEXT=new ExecutionContext(Instant.EPOCH,ZoneOffset.UTC,Locale.ROOT,"validation-large-record");
    private static MappingEngine.Plan plan(String operator,boolean passthrough)throws Exception {
        var spec=JSON.createObjectNode().put("schemaVersion","2.0");spec.putObject("policies").put("allowUnmappedPassthrough",passthrough);
        var rule=spec.putArray("rules").addObject().put("ruleId","payload");rule.putObject("input").put("kind","FIELD").put("name","x");rule.putObject("target").put("name","x").put("logicalType","STRING").put("nullable",false);
        var steps=rule.putArray("steps");if(!operator.isEmpty()){
            var step=steps.addObject().put("operator",operator);var args=step.putObject("args");
            if(operator.equals("CAST"))args.put("logicalType","STRING");
            if(operator.equals("FUNCTION"))args.put("function","upper").putArray("parameters");
        }return MappingEngine.compile(spec);
    }
    @Test void utf8CounterMatchesCompactJsonIncludingEscapesAndUnicode()throws Exception {
        var row=new LinkedHashMap<String,Object>();row.put("中文😀","引号\"反斜杠\\\n\t\b\f\r\u0001é😀");row.put("items",Arrays.asList(null,true,false,10,3.5,""));
        row.put("bytes",new byte[]{0,1,2,3});row.put("array",new int[]{1,2,3});
        assertEquals(JSON.writeValueAsBytes(row).length,RecordBudget.record(row,"验证").utf8());
        assertTrue(RecordBudget.record(Map.of("x","A".repeat(2_000_000)),"验证").memory()>RecordBudget.record(Map.of("x","A".repeat(2_000_000)),"验证").utf8());
    }
    @Test void exactEightMiBRecordAndIdentityStepsKeepTheSameString()throws Exception {
        String value="A".repeat((int)RecordBudget.RECORD_BYTES-JSON.writeValueAsBytes(Map.of("x","")).length);
        var row=Map.<String,Object>of("x",value);assertEquals(RecordBudget.RECORD_BYTES,RecordBudget.record(row,"验证").utf8());
        for(String operator:List.of("","DIRECT","CAST"))for(boolean passthrough:new boolean[]{false,true}){
            var result=MappingEngine.execute(plan(operator,passthrough),List.of(row),0,CONTEXT,null);
            assertTrue(result.rows().getFirst().success());assertSame(value,result.rows().getFirst().output().get("x"));assertEquals(0,result.dictionaryBatches());
        }
        var tooLarge=Map.<String,Object>of("x",value+"A");var error=assertThrows(MappingException.class,()->MappingEngine.execute(plan("DIRECT",false),List.of(tooLarge),0,CONTEXT,null));
        assertEquals("BUDGET_EXCEEDED",error.code());assertTrue(error.getMessage().contains("8388608"));
    }
    @Test void memoryBudgetAndWindowSumRemainIndependent() {
        var memory=assertThrows(MappingException.class,()->RecordBudget.record(Map.of("x",Collections.nCopies(900_000,"")),"验证"));
        assertTrue(memory.getMessage().contains("内存"));assertTrue(memory.getMessage().contains("33554432"));
        String value="A".repeat(4_300_000);
        assertThrows(MappingException.class,()->RecordBudget.window(List.of(Map.of("x",value),Map.of("x",value))));
    }
    @Test void outputExpansionAndUnmappedPassthroughCannotBypassTheRecordBudget()throws Exception {
        var spec=plan("DIRECT",false).document();var second=spec.withArray("rules").addObject().put("ruleId","second");
        second.putObject("input").put("kind","FIELD").put("name","x");second.putObject("target").put("name","y").put("logicalType","STRING");second.putArray("steps");
        var error=assertThrows(MappingException.class,()->MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("x","A".repeat(4_300_000))),0,CONTEXT,null));
        assertEquals("BUDGET_EXCEEDED",error.code());assertTrue(error.getMessage().contains("输出记录"));
        var passthrough=plan("DIRECT",true).document();passthrough.withArray("rules").get(0).withObject("/target").put("name","y");
        assertThrows(MappingException.class,()->MappingEngine.execute(MappingEngine.compile(passthrough),List.of(Map.of("x","A".repeat(4_300_000))),0,CONTEXT,null));
    }
    @Test void largeImagesRequireDirectMappingWhileConstraintsAndTransformGuardsStayActive()throws Exception {
        var row=Map.<String,Object>of("x","A".repeat(2_000_000));
        assertThrows(MappingException.class,()->MappingEngine.execute(plan("FUNCTION",false),List.of(row),0,CONTEXT,null));
        var constrained=plan("DIRECT",false).document();constrained.withArray("rules").get(0).withObject("/target").put("length",100);
        var invalid=MappingEngine.execute(MappingEngine.compile(constrained),List.of(row),0,CONTEXT,null).rows().getFirst();
        assertFalse(invalid.success());assertEquals("LENGTH_EXCEEDED",invalid.errors().getFirst().code());
        var values=new HashMap<String,Object>();values.put("x",null);
        assertFalse(MappingEngine.execute(plan("DIRECT",false),List.of(values),0,CONTEXT,null).rows().getFirst().success());
        var expression=plan("",false).document();expression.withArray("rules").get(0).withObject("/input").put("kind","EXPRESSION").put("expression","CONCAT(${field:x}, ${field:x})");
        assertThrows(MappingException.class,()->MappingEngine.execute(MappingEngine.compile(expression),List.of(row),0,CONTEXT,null));
    }
    @Test void previewOmitsLargeTextAndAlwaysMasksSensitiveFields() {
        String picture="A".repeat(2_000_000);var rows=List.of(Map.<String,Object>of("pic_base64",picture,"phone","validation-private"));
        var preview=PreviewMasking.mask(rows,Set.of(),false);
        assertTrue(preview.getFirst().get("pic_base64").toString().length()<100);assertEquals("***",preview.getFirst().get("phone"));assertSame(picture,rows.getFirst().get("pic_base64"));
    }
    @Test void aLargeAlternateDictionaryKeyIsRejectedBeforeAnyQuery()throws Exception {
        var spec=JSON.readTree("""
            {"version":"1.0","dictionaryBindings":{"d":{"datasourceId":"validation-db","tableId":"validation-table","table":"dictionary","keyColumns":["code"],"keyTypes":["STRING"],"valueColumn":"label"}},
             "mappings":[{"from":"code","to":"label","lookup":{"dictionaryId":"d","keyBindings":[{"value":{"kind":"FIELD","name":"picture"}}]}}]}
            """);
        var error=assertThrows(MappingException.class,()->MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("code","1","picture","A".repeat(2_000_000))),0,CONTEXT,(dictionary,keys)->{fail("大文本不得作为字典查询参数发往数据库");return Map.of();}));
        assertEquals("BUDGET_EXCEEDED",error.code());assertTrue(error.getMessage().contains("字典键"));
    }
    @Test void diagnosticsDoNotCopyLargePicturePrefixesIntoLogs()throws Exception {
        String picture="validation-private-prefix"+"A".repeat(2_000_000);
        var spec=plan("DIRECT",false).document();spec.withArray("rules").get(0).withObject("/target").put("length",10);
        var issue=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("x",picture)),0,CONTEXT,null).rows().getFirst().errors().getFirst();
        assertTrue(issue.valueTruncated());assertFalse(JSON.writeValueAsString(issue).contains("validation-private-prefix"));
    }
}
