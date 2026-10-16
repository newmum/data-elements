package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MappingRuleFallbackTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final ExecutionContext CONTEXT=new ExecutionContext(Instant.EPOCH,ZoneId.of("Asia/Shanghai"),Locale.CHINA,"fixture");

    private ObjectNode specification() {
        var spec=JSON.createObjectNode().put("schemaVersion","2.0");
        spec.putObject("policies").put("recordErrorMode","ROUTE_INVALID").put("onMissingSource","FAIL");
        spec.putArray("rules");return spec;
    }
    private ObjectNode rule(ObjectNode spec,String name) {
        var rule=spec.withArray("rules").addObject().put("ruleId",name).put("onError","KEEP_SOURCE");
        rule.putObject("target").put("name",name).put("logicalType","STRING");
        rule.putArray("steps").addObject().put("operator","CAST").putObject("args").put("logicalType","INT");
        return rule;
    }
    @Test void preservesTheEvaluatedLiteralExpressionAndSelectedConditionalInput() throws Exception {
        var spec=specification();
        rule(spec,"literal").putObject("input").put("kind","LITERAL").put("value","bad");
        rule(spec,"expression").putObject("input").put("kind","EXPRESSION").put("expression","${field:a} || ${field:b}");
        var conditional=rule(spec,"conditional");conditional.put("when","${field:a} = 'a'");
        conditional.set("then",JSON.readTree("""
            {"input":{"kind":"LITERAL","value":"branch"},"steps":[{"operator":"CAST","args":{"logicalType":"INT"}}]}
            """));conditional.set("else",JSON.readTree("{\"input\":{\"kind\":\"LITERAL\",\"value\":\"other\"},\"steps\":[]}"));
        var row=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("a","a","b","b")),0,CONTEXT,null).rows().getFirst();
        assertTrue(row.success());assertEquals(Map.of("literal","bad","expression","ab","conditional","branch"),row.output());
    }
    @Test void missingInputDoesNotLeakThePreviousRulesValueOrRetryTheFailedRead() {
        var spec=specification();
        rule(spec,"previous").putObject("input").put("kind","LITERAL").put("value","previous");
        rule(spec,"missing").putObject("input").put("kind","FIELD").put("name","absent");
        var row=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of()),0,CONTEXT,null).rows().getFirst();
        assertTrue(row.success());assertEquals("previous",row.output().get("previous"));assertNull(row.output().get("missing"));
    }
    @Test void fallbackStillHasToSatisfyTheOutputContract() {
        var spec=specification();var rule=rule(spec,"strict");
        rule.putObject("input").put("kind","LITERAL").put("value","invalid");rule.withObject("/target").put("logicalType","INT");
        var row=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of()),0,CONTEXT,null).rows().getFirst();
        assertFalse(row.success());assertEquals("TYPE_MISMATCH",row.errors().getFirst().code());
    }
    @Test void rootStepInputCannotReadAnotherRulesCurrentValue() {
        var spec=specification();rule(spec,"previous").putObject("input").put("kind","LITERAL").put("value","previous");
        rule(spec,"next").putObject("input").put("kind","STEP_INPUT");
        assertEquals("INPUT_UNAVAILABLE",assertThrows(MappingException.class,()->MappingEngine.compile(spec)).code());
    }
}
