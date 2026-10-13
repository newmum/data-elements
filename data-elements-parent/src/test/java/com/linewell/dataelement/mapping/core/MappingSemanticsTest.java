package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MappingSemanticsTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final ExecutionContext CTX=new ExecutionContext(Instant.EPOCH,ZoneOffset.UTC,Locale.ROOT,"same-input");
    @Test void unknownConditionDoesNotBecomeTrueUnderNot()throws Exception{
        var spec=JSON.readTree("""
            {"version":"1.0","mappings":[{"to":"x","when":"NOT (${field:a} = 1)","then":{"constant":"yes"},"else":{"constant":"no"}}]}
            """);
        Map<String,Object> row=new HashMap<>();row.put("a",null);
        assertEquals("no",MappingEngine.execute(MappingEngine.compile(spec),List.of(row),0,CTX,null).rows().getFirst().output().get("x"));
        assertNull(Expressions.and(true,null));assertEquals(false,Expressions.and(false,null));assertEquals(true,Expressions.or(true,null));assertNull(Expressions.in(2,Arrays.asList(1,null)));
    }
    @Test void missingAndNullRemainDistinctInPredicate()throws Exception{
        var spec=JSON.readTree("""
            {"schemaVersion":"2.0","rules":[{"ruleId":"x","target":{"name":"x"},"when":{"op":"isMissing","left":{"kind":"FIELD","name":"a"}},"then":{"input":{"kind":"LITERAL","value":true},"steps":[]},"else":{"input":{"kind":"LITERAL","value":false},"steps":[]}}]}
            """);
        var plan=MappingEngine.compile(spec);Map<String,Object> row=new HashMap<>();row.put("a",null);
        var result=MappingEngine.execute(plan,List.of(Map.of(),row),0,CTX,null);assertEquals(true,result.rows().get(0).output().get("x"));assertEquals(false,result.rows().get(1).output().get("x"));
    }
    @Test void stepFallbackKeepsOriginalInputAndFinalValidationStillApplies()throws Exception{
        var spec=JSON.readTree("""
            {"schemaVersion":"2.0","rules":[{"ruleId":"x","input":{"kind":"FIELD","name":"a"},"target":{"name":"x"},"steps":[{"operator":"FUNCTION","args":{"function":"upper"}},{"operator":"CAST","args":{"logicalType":"INT"},"onError":"KEEP_SOURCE"}]}]}
            """);
        assertEquals("abc",MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("a","abc")),0,CTX,null).rows().getFirst().output().get("x"));
    }
    @Test void decimalSchemaContractAndDeterministicSystemIds()throws Exception{
        var spec=JSON.readTree("""
            {"schemaVersion":"2.0","rules":[{"ruleId":"d","input":{"kind":"FIELD","name":"a"},"target":{"name":"d"},"steps":[{"operator":"FUNCTION","args":{"function":"toDecimal","parameters":[8,2,"HALF_UP"]}}]},{"ruleId":"id","input":{"kind":"SYSTEM","generator":"UUID"},"target":{"name":"id"},"steps":[]}]}
            """);
        var plan=MappingEngine.compile(spec);assertEquals(8,plan.outputContracts().get("d").precision());assertEquals(2,plan.outputContracts().get("d").scale());
        var first=MappingEngine.execute(plan,List.of(Map.of("a",1)),7,CTX,null).rows().getFirst();var replay=MappingEngine.execute(plan,List.of(Map.of("a",1)),7,CTX,null).rows().getFirst();assertEquals(first.output().get("id"),replay.output().get("id"));
    }
    @Test void localDateTimeUsesPlanZoneInsteadOfJvmZone(){
        ZoneId zone=ZoneId.of("Asia/Shanghai");LocalDateTime local=LocalDateTime.of(2024,2,29,12,34,56);
        Instant instant=ValueTypes.instant(local,zone);assertEquals(Instant.parse("2024-02-29T04:34:56Z"),instant);
        assertEquals(local,ValueTypes.cast(java.sql.Timestamp.from(instant),"DATETIME",38,10,java.math.RoundingMode.UNNECESSARY,zone));
    }
}
