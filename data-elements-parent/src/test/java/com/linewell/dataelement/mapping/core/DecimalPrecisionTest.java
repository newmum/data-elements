package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DecimalPrecisionTest {
    private static final ObjectMapper JSON=new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final ExecutionContext CTX=new ExecutionContext(Instant.EPOCH,ZoneOffset.UTC,Locale.ROOT,"decimal-fixture");
    private static final List<String> VALUES=List.of("119.379023855114","25.7223849760167","-119.379023855114","0.000000000000000000123456789","1.23456789012345678901234567890123456789E+30");

    @Test void unqualifiedLegacyDecimalsKeepEveryDigitForTwentyAndOneHundredMixedRows()throws Exception {
        var plan=MappingEngine.compile(JSON.readTree("""
          {"version":"1.0","mappings":[{"from":"x","to":"y","targetType":"DECIMAL"}]}
          """));
        assertEquals(0,plan.outputContracts().get("y").precision());
        for(int count:List.of(20,100)) {
            List<Map<String,Object>>rows=new ArrayList<>();
            for(int i=0;i<count;i++){String value=VALUES.get(i%VALUES.size());rows.add(Map.of("x",i%2==0?new BigDecimal(value):value));}
            var result=MappingEngine.execute(plan,rows,0,CTX,null);
            assertEquals(0,result.dictionaryBatches());assertEquals(count,result.rows().size());
            for(int i=0;i<count;i++){var row=result.rows().get(i);assertTrue(row.success(),row.errors().toString());assertEquals(new BigDecimal(VALUES.get(i%VALUES.size())),row.output().get("y"));}
        }
    }

    @Test void fixedPhysicalPrecisionIsInheritedAndStillProtectsTheTarget()throws Exception {
        var spec=spec();spec.putObject("schemaContract").putArray("target").addObject().put("name","y").put("logicalType","DECIMAL").put("precision",30).put("scale",20);
        var plan=MappingEngine.compile(spec);assertEquals(30,plan.outputContracts().get("y").precision());assertEquals(20,plan.outputContracts().get("y").scale());
        ((ObjectNode)spec.path("rules").get(0).path("target")).put("precision",0).put("scale",0);
        assertEquals(plan.outputContracts(),MappingEngine.compile(spec).outputContracts());
        var row=MappingEngine.execute(plan,List.of(Map.of("x",new BigDecimal(VALUES.getFirst()))),0,CTX,null).rows().getFirst();
        assertTrue(row.success(),row.errors().toString());assertEquals(0,new BigDecimal(VALUES.getFirst()).compareTo((BigDecimal)row.output().get("y")));
        var target=(ObjectNode)spec.path("schemaContract").path("target").get(0);target.put("precision",8).put("scale",2);
        var invalid=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("x",VALUES.getFirst())),0,CTX,null).rows().getFirst();
        assertFalse(invalid.success());assertEquals("TYPE_MISMATCH",invalid.errors().getFirst().code());assertTrue(invalid.errors().getFirst().message().contains("DECIMAL(8,2)"));
    }

    @Test void oracleUnconstrainedNumberMetadataDoesNotBecomeIntegerOrScaleTen()throws Exception {
        for(int precision:List.of(0,38)) {
            var spec=spec();spec.putObject("schemaContract").putArray("target").addObject().put("name","y").put("logicalType","DECIMAL").put("precision",precision).put("scale",-127);
            ((ObjectNode)spec.path("rules").get(0).path("target")).put("precision",precision).put("scale",-127);
            var plan=MappingEngine.compile(spec);assertEquals(0,plan.outputContracts().get("y").precision());assertEquals(0,plan.outputContracts().get("y").scale());
            var row=MappingEngine.execute(plan,List.of(Map.of("x",VALUES.get(1))),0,CTX,null).rows().getFirst();assertTrue(row.success());assertEquals(new BigDecimal(VALUES.get(1)),row.output().get("y"));
        }
    }

    @Test void oracleNegativeScaleAndScaleGreaterThanPrecisionRetainTheirSqlMeaning() {
        assertEquals(new BigDecimal("1.23E+4"),ValueTypes.cast("12300","DECIMAL",3,-2,RoundingMode.UNNECESSARY));
        assertEquals(new BigDecimal("0.00123"),ValueTypes.cast("0.00123","DECIMAL",3,5,RoundingMode.UNNECESSARY));
        assertThrows(MappingException.class,()->ValueTypes.cast("12345","DECIMAL",3,-2,RoundingMode.UNNECESSARY));
        assertThrows(MappingException.class,()->ValueTypes.cast("0.01234","DECIMAL",3,5,RoundingMode.UNNECESSARY));
    }

    @Test void explicitCastDefaultsAndRoundingStayExplicit()throws Exception {
        var spec=spec();((ObjectNode)spec.path("rules").get(0)).putArray("steps").addObject().put("operator","CAST").putObject("args").put("logicalType","DECIMAL").put("precision",18).put("scale",6).put("roundingMode","HALF_UP");
        var plan=MappingEngine.compile(spec);var row=MappingEngine.execute(plan,List.of(Map.of("x",VALUES.getFirst())),0,CTX,null).rows().getFirst();
        assertTrue(row.success());assertEquals(new BigDecimal("119.379024"),row.output().get("y"));
    }

    @Test void inferenceFromArithmeticDoesNotReintroduceScaleTen()throws Exception {
        var spec=spec();((ObjectNode)spec.path("rules").get(0)).putArray("steps").addObject().put("operator","FUNCTION").putObject("args").put("function","abs");
        var plan=MappingEngine.compile(spec);assertEquals(0,plan.outputContracts().get("y").precision());
        assertEquals(new BigDecimal(VALUES.getFirst()),MappingEngine.execute(plan,List.of(Map.of("x",VALUES.get(2))),0,CTX,null).rows().getFirst().output().get("y"));
    }

    @Test void malformedAndOverBudgetNumbersAreStillRejected() {
        assertThrows(MappingException.class,()->ValueTypes.cast("NaN","DECIMAL",0,0,RoundingMode.UNNECESSARY));
        assertEquals("BUDGET_EXCEEDED",assertThrows(MappingException.class,()->ValueTypes.cast("1E-1001","DECIMAL",0,0,RoundingMode.UNNECESSARY)).code());
    }
    @Test void partiallyDeclaredLegacyDimensionsKeepTheirExplicitConversionDefaults()throws Exception {
        var spec=spec();var target=(ObjectNode)spec.path("rules").get(0).path("target");
        target.put("scale",18);var scaleOnly=MappingEngine.compile(spec).outputContracts().get("y");assertEquals(38,scaleOnly.precision());assertEquals(18,scaleOnly.scale());
        target.remove("scale");target.put("precision",38);assertEquals(10,MappingEngine.compile(spec).outputContracts().get("y").scale());
    }
    @Test void unqualifiedExpressionCastPreservesPrecisionAndOneArgumentCastMeansScaleZero()throws Exception {
        var spec=spec();((ObjectNode)spec.path("rules").get(0)).putObject("input").put("kind","EXPRESSION").put("expression","CAST(${field:x} AS DECIMAL)");
        var row=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("x",VALUES.get(1))),0,CTX,null).rows().getFirst();
        assertTrue(row.success(),row.errors().toString());assertEquals(new BigDecimal(VALUES.get(1)),row.output().get("y"));
        ((ObjectNode)spec.path("rules").get(0).path("input")).put("expression","CAST(${field:x} AS DECIMAL(18))");
        assertTrue(MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("x","123456789012345678")),0,CTX,null).rows().getFirst().success());
    }

    private ObjectNode spec()throws Exception {return (ObjectNode)JSON.readTree("""
      {"schemaVersion":"2.0","rules":[{"ruleId":"decimal","input":{"kind":"FIELD","name":"x"},"target":{"name":"y","logicalType":"DECIMAL"},"steps":[]}]}
      """);}
}
