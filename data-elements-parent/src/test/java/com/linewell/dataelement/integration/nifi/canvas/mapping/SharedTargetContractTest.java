package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.mapping.core.MappingEngine;
import com.linewell.dataelement.mapping.core.MappingException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SharedTargetContractTest {
    @Test void forcedBinaryTransportProjectionCopiesValuesAndAuditWithoutRecomputingThem()throws Exception {
        var draft=new ObjectMapper().readTree("""
          {"schemaVersion":"2.0","rules":[
           {"input":{"kind":"FIELD","name":"blob"},"target":{"name":"IMAGE","logicalType":"BINARY"}},
           {"input":{"kind":"FIELD","name":"text"},"target":{"name":"CLOB_TEXT","logicalType":"STRING"}},
           {"input":{"kind":"FIELD","name":"uuid"},"target":{"name":"ODS_UUID","logicalType":"STRING"}}],
           "schemaContract":{"targets":[{"nodeId":"hive","reference":{"datasourceId":"validation-hive","tableId":"validation-images"},"fields":[
            {"name":"image","logicalType":"BINARY"},{"name":"clob_text","logicalType":"STRING"},{"name":"ods_uuid","logicalType":"STRING"}]}]}}
          """);
        var shared=MappingEngine.compile(draft);assertNull(UnifiedMappingService.sharedTargetProjection(shared,"hive"));
        var projection=UnifiedMappingService.sharedTargetProjection(shared,"hive",true);assertNotNull(projection);assertEquals(List.of("image","clob_text","ods_uuid"),projection.outputFields());
        byte[] image={0,1,-1,127,-128};String clob="data:image/png;base64,AAH/f4A=";
        for(int count:List.of(20,100)){
            var rows=new java.util.ArrayList<java.util.Map<String,Object>>();for(int i=0;i<count;i++){var row=new java.util.LinkedHashMap<String,Object>();row.put("IMAGE",i%3==0?null:i%3==1?new byte[0]:image);row.put("CLOB_TEXT",clob);row.put("ODS_UUID","validation-"+i);rows.add(row);}
            var result=MappingEngine.execute(projection,rows,0,integerContext(),null);assertEquals(count,result.rows().size());assertEquals(0,result.dictionaryBatches());
            for(int i=0;i<count;i++){assertTrue(result.rows().get(i).success());assertEquals(clob,result.rows().get(i).output().get("clob_text"));assertEquals("validation-"+i,result.rows().get(i).output().get("ods_uuid"));assertArrayEquals((byte[])rows.get(i).get("IMAGE"),(byte[])result.rows().get(i).output().get("image"));}
        }
        ((ObjectNode)draft.path("schemaContract").path("targets").get(0)).remove("reference");
        assertEquals("INVALID_SCHEMA",assertThrows(MappingException.class,()->UnifiedMappingService.sharedTargetProjection(MappingEngine.compile(draft),"hive",true)).code());
    }
    @Test void intrinsicSqlDateRetainsCalendarPrecisionWithoutChangingEitherPhysicalSchema()throws Exception {
        var draft=sharedDateDraft();var before=draft.path("schemaContract").deepCopy();UnifiedMappingService.applySharedDatePrecision(draft);
        var declaredSource=draft.path("schemaContract").path("source").deepCopy();for(var field:declaredSource)((ObjectNode)field).remove(java.util.List.of("registeredFormat","formatOrigin"));
        assertEquals(before.path("source"),declaredSource);assertEquals(MappingEngine.hash(draft.path("schemaContract").path("source")),draft.path("schemaContract").path("sourceHash").asText());var storage=draft.path("schemaContract").path("target").get(0);
        assertEquals("DATETIME",storage.path("logicalType").asText());assertEquals("DATE",storage.path("physicalType").asText());assertTrue(storage.path("dateContainsTime").asBoolean());
        assertEquals("DATE",storage.path("registeredFormat").asText());assertEquals("NATIVE_SOURCE_DATE",storage.path("formatOrigin").asText());
        assertEquals(MappingEngine.hash(draft.path("schemaContract").path("target")),draft.path("schemaContract").path("targetHash").asText());
        var precision=draft.deepCopy();UnifiedMappingService.applySharedDatePrecision(draft);assertEquals(precision,draft);assertTrue(draft.path("rules").get(0).path("steps").isEmpty());
        var plan=MappingEngine.compile(draft);assertEquals("DATE",plan.outputContracts().get("TIME").logicalType());assertDoesNotThrow(()->UnifiedMappingService.validateSharedTargetFields(draft,plan));
        for(String zone:List.of("UTC","Asia/Shanghai","Pacific/Kiritimati","America/Los_Angeles"))for(int count:List.of(20,100)){
            var rows=new java.util.ArrayList<java.util.Map<String,Object>>();for(int i=0;i<count;i++)rows.add(java.util.Collections.singletonMap("day",i%4==0?null:i%4==1?"1900-01-01":i%4==2?java.time.LocalDate.of(1970,1,1):java.sql.Date.valueOf("2024-02-29")));
            var context=new com.linewell.dataelement.mapping.core.ExecutionContext(java.time.Instant.EPOCH,java.time.ZoneId.of(zone),java.util.Locale.ROOT,"validation-date-domain");
            var result=MappingEngine.execute(plan,rows,0,context,null);assertEquals(0,result.dictionaryBatches());
            for(int i=0;i<count;i++){var actual=result.rows().get(i);assertTrue(actual.success());if(i%4==0)assertNull(actual.output().get("TIME"));else{
                var expected=java.time.LocalDate.parse(i%4==1?"1900-01-01":i%4==2?"1970-01-01":"2024-02-29");assertEquals(expected,actual.output().get("TIME"));
            }}
        }
    }
    @Test void dateDomainCannotBeInferredFromSamplesOracleDatesCustomTransformsOrUnverifiedSources()throws Exception {
        for(String mode:List.of("missingSourceRef","unverified","oracleDate","timestamp","expression","customStep","default","condition","nullError","missingBranchRef","sourceFormat","targetFormat","branchFormat")){
            var draft=sharedDateDraft();var contract=(ObjectNode)draft.path("schemaContract");var source=(ObjectNode)contract.path("source").get(0);var rule=(ObjectNode)draft.path("rules").get(0);
            switch(mode){
                case "missingSourceRef" -> contract.remove("sourceRef");case "unverified" -> contract.put("sourceRangeUnverified",true);
                case "oracleDate" -> source.put("logicalType","DATETIME").put("dateContainsTime",true);
                case "timestamp" -> source.put("logicalType","DATETIME").put("physicalType","TIMESTAMP");
                case "expression" -> rule.putObject("input").put("kind","EXPRESSION").put("expression","${field:day}");
                case "customStep" -> rule.withArray("steps").addObject().put("operator","CAST").putObject("args").put("logicalType","DATETIME");
                case "default" -> rule.put("defaultValue","2024-01-01 12:00:00");case "condition" -> rule.put("when",true);
                case "sourceFormat" -> source.put("registeredFormat","DATETIME");case "targetFormat" -> ((ObjectNode)contract.path("target").get(0)).put("registeredFormat","DATETIME");case "branchFormat" -> secondaryField(draft).put("registeredFormat","DATETIME");
                case "nullError" -> rule.put("onError","NULL");case "missingBranchRef" -> ((ObjectNode)contract.path("targets").get(0)).remove("reference");
            }
            var original=draft.deepCopy();UnifiedMappingService.applySharedDatePrecision(draft);assertEquals(original,draft,mode);
        }
    }
    private static ObjectNode sharedDateDraft()throws Exception {
        return (ObjectNode)new ObjectMapper().readTree("""
          {"schemaVersion":"2.0","schemaContract":{
             "sourceRef":{"datasourceId":"validation-source","tableId":"validation-day"},
             "source":[{"name":"day","logicalType":"DATE","physicalType":"DATE","nullable":true}],
             "targetRef":{"datasourceId":"validation-oracle","tableId":"validation-day"},
             "target":[{"name":"TIME","logicalType":"DATETIME","physicalType":"DATE","dateContainsTime":true}],
             "targets":[{"nodeId":"hive","reference":{"datasourceId":"validation-hive","tableId":"validation-day"},
                "fields":[{"name":"time","logicalType":"DATE","physicalType":"DATE"}]}]},
           "rules":[{"input":{"kind":"FIELD","name":"day"},"target":{"name":"TIME","logicalType":"DATE","nullable":true},"steps":[]}]}
          """);
    }
    @Test void lineageAliasKeepsStrictIntegerBoundsAndProjectsToPhysicalNames() {
        var draft=integerSharedDraft("LONG");var contract=(ObjectNode)draft.path("schemaContract");
        ((ObjectNode)draft.path("rules").get(0).path("target")).put("name","C_SHORT");((ObjectNode)contract.path("target").get(0)).put("name","C_SHORT");
        secondaryField(draft).put("name","long_original_column");var branch=(ObjectNode)contract.path("targets").get(0);branch.putObject("outputAliases").put("C_SHORT","long_original_column");
        UnifiedMappingService.applySharedIntegerBounds(draft);assertEquals("LONG",draft.path("rules").get(0).path("steps").get(0).path("args").path("logicalType").asText());
        var shared=MappingEngine.compile(draft);assertDoesNotThrow(()->UnifiedMappingService.validateSharedTargetFields(draft,shared));
        var projection=UnifiedMappingService.sharedTargetProjection(shared,branch.path("nodeId").asText());assertNotNull(projection);assertEquals(List.of("long_original_column"),projection.outputFields());
        for(int count:List.of(20,100)){
            var rows=new java.util.ArrayList<java.util.Map<String,Object>>();for(int i=0;i<count;i++)rows.add(java.util.Collections.singletonMap("C_SHORT",i%2==0?null:new java.math.BigDecimal(Long.MAX_VALUE)));
            var result=MappingEngine.execute(projection,rows,0,integerContext(),null);assertEquals(0,result.dictionaryBatches());
            for(int i=0;i<count;i++){assertTrue(result.rows().get(i).success());assertEquals(rows.get(i).get("C_SHORT"),result.rows().get(i).output().get("long_original_column"));}
        }
        branch.remove("outputAliases");assertEquals("TARGET_NOT_FOUND",assertThrows(MappingException.class,()->UnifiedMappingService.validateSharedTargetFields(draft,shared)).code());
    }
    @Test void physicalProjectionRetainsAuditValuesAndOrdersColumnsByDestination()throws Exception {
        var draft=(ObjectNode)new ObjectMapper().readTree("""
          {"schemaVersion":"2.0","rules":[
            {"input":{"kind":"FIELD","name":"raw"},"target":{"name":"C_SHORT","logicalType":"DECIMAL","precision":38,"scale":6}},
            {"input":{"kind":"FIELD","name":"uuid"},"target":{"name":"ODS_UUID","logicalType":"STRING"}},
            {"input":{"kind":"FIELD","name":"clock"},"target":{"name":"ODS_RKSJ","logicalType":"DATETIME"}}],
           "schemaContract":{"targets":[{"nodeId":"hive","outputAliases":{"C_SHORT":"long_name"},"fields":[
             {"name":"ods_uuid","logicalType":"STRING"},{"name":"long_name","logicalType":"DECIMAL","precision":38,"scale":6},{"name":"ods_rksj","logicalType":"DATETIME"}]}]}}
          """);
        var projection=UnifiedMappingService.sharedTargetProjection(MappingEngine.compile(draft),"hive");
        assertEquals(List.of("ods_uuid","long_name","ods_rksj"),projection.outputFields());assertNull(UnifiedMappingService.sharedTargetProjection(MappingEngine.compile(draft),"other"));
        var precise=new java.math.BigDecimal("123456789012345678901234567890.123456");var clock=java.time.LocalDateTime.parse("1960-01-02T03:04:05.123456789");
        var result=MappingEngine.execute(projection,List.of(java.util.Map.of("C_SHORT",precise,"ODS_UUID","original-id","ODS_RKSJ",clock)),0,integerContext(),null).rows().getFirst();
        assertTrue(result.success());assertEquals(precise,result.output().get("long_name"));assertEquals("original-id",result.output().get("ods_uuid"));assertEquals(clock,result.output().get("ods_rksj"));
        assertFalse(projection.document().toString().contains("UUID_RANDOM"));
        assertFalse(MappingEngine.execute(projection,List.of(java.util.Map.of("C_SHORT",precise,"ODS_UUID","original-id")),0,integerContext(),null).rows().getFirst().success());
        draft.withObject("/policies").put("allowUnmappedPassthrough",true);
        var sources=draft.withObject("/schemaContract").putArray("source");sources.addObject().put("name","EXTRA").put("logicalType","STRING");
        sources.addObject().put("name","raw").put("logicalType","DECIMAL").put("precision",38).put("scale",6);sources.addObject().put("name","uuid").put("logicalType","STRING");sources.addObject().put("name","clock").put("logicalType","DATETIME");
        ((ObjectNode)draft.path("schemaContract").path("targets").get(0)).withArray("fields").addObject().put("name","extra").put("logicalType","STRING");
        var passthrough=UnifiedMappingService.sharedTargetProjection(MappingEngine.compile(draft),"hive");
        var values=new java.util.HashMap<String,Object>();values.put("C_SHORT",precise);values.put("ODS_UUID","original-id");values.put("ODS_RKSJ",clock);values.put("EXTRA","keep");
        assertEquals("keep",MappingEngine.execute(passthrough,List.of(values),0,integerContext(),null).rows().getFirst().output().get("extra"));
    }
    @Test void sharedIntegerBoundsAreEnforcedForUnverifiedJsonWithoutChangingDecimalTargets() {
        for(String type:List.of("INT","LONG")) {
            var draft=integerSharedDraft(type);var physical=draft.path("schemaContract").path("target").deepCopy();
            UnifiedMappingService.applySharedIntegerBounds(draft);
            var steps=draft.path("rules").get(0).path("steps");assertEquals(1,steps.size());
            assertEquals("CAST",steps.get(0).path("operator").asText());assertEquals(type,steps.get(0).path("args").path("logicalType").asText());
            assertTrue(draft.path("schemaContract").path("sourceRangeUnverified").asBoolean());
            assertFalse(draft.path("schemaContract").has("sourceRef"));assertEquals(physical,draft.path("schemaContract").path("target"));
            UnifiedMappingService.applySharedIntegerBounds(draft);assertEquals(1,draft.path("rules").get(0).path("steps").size());
            var plan=MappingEngine.compile(draft);assertEquals("DECIMAL",plan.outputContracts().get("VALUE").logicalType());
            assertDoesNotThrow(()->UnifiedMappingService.validateSharedTargetFields(draft,plan));
            long minimum=type.equals("INT")?Integer.MIN_VALUE:Long.MIN_VALUE,maximum=type.equals("INT")?Integer.MAX_VALUE:Long.MAX_VALUE;
            for(int count:List.of(20,100)) {
                var input=new java.util.ArrayList<java.util.Map<String,Object>>();
                for(int i=0;i<count;i++)input.add(java.util.Collections.singletonMap("raw",i%3==0?null:i%3==1?minimum:maximum));
                var result=MappingEngine.execute(plan,input,0,integerContext(),null);assertEquals(count,result.rows().size());assertEquals(0,result.dictionaryBatches());
                for(int i=0;i<count;i++){
                    var row=result.rows().get(i);assertTrue(row.success());
                    if(i%3==0)assertNull(row.output().get("VALUE"));else assertEquals(new java.math.BigDecimal(i%3==1?minimum:maximum),row.output().get("VALUE"));
                }
            }
        }
    }
    @Test void sharedIntegerBoundsRejectFractionsAndBothOverflowDirectionsBeforeAnyOutput() {
        for(String type:List.of("INT","LONG")) {
            var draft=integerSharedDraft(type);UnifiedMappingService.applySharedIntegerBounds(draft);var plan=MappingEngine.compile(draft);
            var minimum=java.math.BigInteger.valueOf(type.equals("INT")?Integer.MIN_VALUE:Long.MIN_VALUE);
            var maximum=java.math.BigInteger.valueOf(type.equals("INT")?Integer.MAX_VALUE:Long.MAX_VALUE);
            for(Object value:List.of(minimum.subtract(java.math.BigInteger.ONE),maximum.add(java.math.BigInteger.ONE),new java.math.BigDecimal("1.5"))) {
                var result=MappingEngine.execute(plan,List.of(java.util.Map.of("raw",value)),0,integerContext(),null);
                assertFalse(result.rows().getFirst().success());assertFalse(result.rows().getFirst().output().containsKey("VALUE"));
                assertEquals("TYPE_MISMATCH",result.rows().getFirst().errors().getFirst().code());
            }
        }
    }
    @Test void sharedIntegerBoundsNeverRewriteExpressionsFallbacksOrNarrowPhysicalIntegers() {
        for(String mode:List.of("expression","default","recover","steps","smallint","unsigned")) {
            var draft=integerSharedDraft("LONG");var rule=(ObjectNode)draft.path("rules").get(0);
            switch(mode) {
                case "expression" -> ((ObjectNode)rule.path("input")).put("kind","EXPRESSION").put("expression","${field:raw}+1");
                case "default" -> rule.put("defaultValue",new java.math.BigDecimal("9999999999999999999"));
                case "recover" -> rule.put("onError","NULL");
                case "steps" -> rule.withArray("steps").addObject().put("operator","FUNCTION").putObject("args").put("function","trim");
                case "smallint" -> secondaryField(draft).put("logicalType","INT").put("physicalType","SMALLINT");
                case "unsigned" -> secondaryField(draft).put("physicalType","BIGINT UNSIGNED");
            }
            int oldSteps=rule.path("steps").size();UnifiedMappingService.applySharedIntegerBounds(draft);assertEquals(oldSteps,rule.path("steps").size());
            assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());
        }
    }
    @Test void onlyOneStrictIntegerCastProvesTheEmittedRange() {
        var draft=integerSharedDraft("LONG");var rule=(ObjectNode)draft.path("rules").get(0);
        var cast=rule.withArray("steps").addObject().put("operator","CAST");cast.putObject("args").put("logicalType","BIGINT").put("roundingMode","DOWN");
        assertSharedAccepted(draft);
        assertFalse(MappingEngine.execute(MappingEngine.compile(draft),List.of(java.util.Map.of("raw",new java.math.BigDecimal("1.5"))),0,integerContext(),null).rows().getFirst().success());
        rule.withArray("steps").addObject().put("operator","FUNCTION").putObject("args").put("function","abs");
        assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());
        rule.withArray("steps").remove(1);rule.put("onError","DEFAULT_VALUE").put("defaultValue",new java.math.BigDecimal("9999999999999999999"));
        assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());
    }
    @Test void automaticIntegerBoundsRequireRegisteredTargetReferencesAndZeroScale() {
        for(String mode:List.of("primaryRef","branchRef","fractional","unbounded")) {
            var draft=integerSharedDraft("LONG");var contract=(ObjectNode)draft.path("schemaContract");var target=(ObjectNode)draft.path("rules").get(0).path("target");
            switch(mode) {
                case "primaryRef" -> contract.remove("targetRef");
                case "branchRef" -> ((ObjectNode)contract.path("targets").get(0)).remove("reference");
                case "fractional" -> {target.put("scale",1);((ObjectNode)contract.path("target").get(0)).put("scale",1);}
                case "unbounded" -> {target.put("precision",0);((ObjectNode)contract.path("target").get(0)).put("precision",0);}
            }
            UnifiedMappingService.applySharedIntegerBounds(draft);assertEquals(0,draft.path("rules").get(0).path("steps").size());
            assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());
        }
    }
    private static ObjectNode integerSharedDraft(String type) {
        int digits=type.equals("INT")?10:19;var draft=sharedDraft("DECIMAL",digits,0,true);var contract=(ObjectNode)draft.path("schemaContract");
        contract.putObject("targetRef").put("datasourceId","validation-oracle").put("tableId","validation-target");
        contract.put("sourceRangeUnverified",true);contract.putArray("target").addObject().put("name","VALUE").put("logicalType","DECIMAL").put("physicalType","NUMBER").put("precision",digits).put("scale",0).put("nullable",true);
        ((ObjectNode)contract.path("targets").get(0)).putObject("reference").put("datasourceId","validation-hive").put("tableId","validation-target");
        secondaryField(draft).put("logicalType",type).put("physicalType",type.equals("INT")?"INTEGER":"BIGINT");return draft;
    }
    private static com.linewell.dataelement.mapping.core.ExecutionContext integerContext(){return new com.linewell.dataelement.mapping.core.ExecutionContext(java.time.Instant.EPOCH,java.time.ZoneOffset.UTC,java.util.Locale.ROOT,"validation-integer-bound");}
    @Test void nativeBooleanNumberCarrierOnlyFitsHiveBooleanWithVerifiedDirectSource()throws Exception {
        var draft=sharedDraft("DECIMAL",1,0,true);
        secondaryField(draft).put("logicalType","BOOLEAN").put("physicalType","BOOLEAN");
        var contract=(ObjectNode)draft.path("schemaContract");
        contract.putObject("sourceRef").put("datasourceId","validation-db").put("tableId","validation-table");
        var source=contract.putArray("source").addObject().put("name","raw").put("logicalType","BOOLEAN").put("physicalType","bool");
        assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());
        ((ObjectNode)contract.path("targets").get(0)).put("targetBooleanNumberCarrier",true);
        assertSharedAccepted(draft);
        var plan=MappingEngine.compile(draft);
        var context=new com.linewell.dataelement.mapping.core.ExecutionContext(java.time.Instant.EPOCH,java.time.ZoneOffset.UTC,java.util.Locale.ROOT,"validation");
        var rows=new java.util.ArrayList<java.util.Map<String,Object>>();
        rows.add(java.util.Map.of("raw",true));rows.add(java.util.Map.of("raw",false));
        var nullable=new java.util.HashMap<String,Object>();nullable.put("raw",null);rows.add(nullable);
        var result=MappingEngine.execute(plan,rows,0,context,null);
        assertTrue(result.rows().stream().allMatch(MappingEngine.Row::success));
        assertEquals(java.math.BigDecimal.ONE,result.rows().get(0).output().get("VALUE"));
        assertEquals(java.math.BigDecimal.ZERO,result.rows().get(1).output().get("VALUE"));
        assertNull(result.rows().get(2).output().get("VALUE"));
        source.put("logicalType","INT");assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());source.put("logicalType","BOOLEAN");
        contract.put("sourceRangeUnverified",true);assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());contract.remove("sourceRangeUnverified");
        var rule=(ObjectNode)draft.path("rules").get(0);rule.put("defaultValue",2);
        assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());rule.remove("defaultValue");
        ((ObjectNode)rule.path("input")).put("kind","EXPRESSION").put("expression","2");
        assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());
    }
    @Test void timestampStorageWidthIsNotACharacterLimitButExplicitTextLimitsRemain()throws Exception {
        var spec=(ObjectNode)JSON.readTree("""
            {"schemaVersion":"2.0","rules":[{"ruleId":"time","input":{"kind":"SYSTEM","generator":"NOW"},
              "target":{"name":"EVENT_TIME","logicalType":"DATETIME","length":11},"steps":[]}]}
            """);
        UnifiedMappingService.normalizeTargetLengths(spec);
        assertFalse(spec.path("rules").get(0).path("target").has("length"));
        var plan=MappingEngine.compile(spec);
        var result=MappingEngine.execute(plan,List.of(java.util.Map.of()),0,
            new com.linewell.dataelement.mapping.core.ExecutionContext(java.time.Instant.parse("2026-11-23T02:20:30Z"),java.time.ZoneId.of("Asia/Shanghai"),java.util.Locale.ROOT,"validation"),null);
        assertTrue(result.rows().getFirst().success());
        ((ObjectNode)spec.path("rules").get(0).path("target")).put("logicalType","STRING").put("length",3);
        UnifiedMappingService.normalizeTargetLengths(spec);
        assertEquals(3,spec.path("rules").get(0).path("target").path("length").asInt());
    }
    private static final ObjectMapper JSON=new ObjectMapper();
    private ObjectNode draft()throws Exception {
        return (ObjectNode)MappingEngine.legacy((ObjectNode)JSON.readTree("""
            {"version":"1.0","mappings":[{"to":"/ODS_UUID","expression":"UUID()","targetType":"STRING"}],
             "schemaContract":{"targetRef":{"datasourceId":"validation-db","tableId":"validation-table"},
               "target":[{"name":"ods_uuid","logicalType":"STRING","nullable":true}]}}
            """));
    }
    @Test void authorizedJdbcNamesAlignAllOutputsWithoutWeakeningExactJsonValidation()throws Exception {
        var authorized=draft();UnifiedMappingService.alignRegisteredTargetNames(authorized);
        assertEquals(List.of("ods_uuid"),MappingEngine.compile(authorized).outputFields().stream().toList());
        var jsonOnly=draft();((ObjectNode)jsonOnly.path("schemaContract")).remove("targetRef");
        UnifiedMappingService.alignRegisteredTargetNames(jsonOnly);
        assertEquals("TARGET_NOT_FOUND",assertThrows(MappingException.class,()->MappingEngine.compile(jsonOnly)).code());
    }
    @Test void bothDestinationsMustContainOneUnambiguousOutput()throws Exception {
        var draft=draft();UnifiedMappingService.alignRegisteredTargetNames(draft);
        var branch=((ObjectNode)draft.path("schemaContract")).putArray("targets").addObject().put("nodeId","hive").put("label","Hive目标");
        var fields=branch.putArray("fields");fields.addObject().put("name","ODS_UUID");
        var plan=MappingEngine.compile(draft);
        assertDoesNotThrow(()->UnifiedMappingService.validateSharedTargetFields(draft,plan));
        fields.addObject().put("name","ods_uuid");
        assertEquals("AMBIGUOUS_FIELD",assertThrows(MappingException.class,()->UnifiedMappingService.validateSharedTargetFields(draft,plan)).code());
        fields.removeAll();
        var error=assertThrows(MappingException.class,()->UnifiedMappingService.validateSharedTargetFields(draft,plan));
        assertEquals("TARGET_NOT_FOUND",error.code());assertTrue(error.getMessage().contains("Hive目标"));
    }

    @Test void secondaryDecimalTargetRejectsBothFractionalAndIntegerCapacityNarrowing()throws Exception {
        for(int[] dimensions:List.of(new int[]{6,1},new int[]{7,4})){
            var draft=sharedDraft("DECIMAL",6,2,true);
            secondaryField(draft).put("logicalType","DECIMAL").put("precision",dimensions[0]).put("scale",dimensions[1]);
            var error=sharedError(draft);
            assertEquals("TARGET_TYPE_MISMATCH",error.code());assertTrue(error.getMessage().contains("Hive目标"));
            assertTrue(error.getMessage().contains("VALUE"));assertTrue(error.getMessage().contains("DECIMAL(6,2)"));
        }
        var widening=sharedDraft("DECIMAL",6,2,true);
        secondaryField(widening).put("logicalType","DECIMAL").put("precision",8).put("scale",4);
        assertSharedAccepted(widening);
    }

    @Test void secondaryIntegerTargetDoesNotImplicitlyParseStringOutput()throws Exception {
        var draft=sharedDraft("STRING",0,0,true);secondaryField(draft).put("logicalType","INT");
        var error=sharedError(draft);assertEquals("TARGET_TYPE_MISMATCH",error.code());
        assertTrue(error.getMessage().contains("STRING"));assertTrue(error.getMessage().contains("INT"));
    }

    @Test void hiveStringAcceptsUnboundedOracleNumberWithoutChangingNumericOutput()throws Exception {
        var draft=sharedDraft("DECIMAL",0,0,true);
        secondaryField(draft).put("logicalType","STRING").put("physicalType","STRING").put("length",0);
        var plan=MappingEngine.compile(draft);
        assertDoesNotThrow(()->UnifiedMappingService.validateSharedTargetFields(draft,plan));
        var large=new java.math.BigDecimal("-123456789012345678901234567890.123456789");
        var nullable=new java.util.HashMap<String,Object>();nullable.put("raw",null);
        var result=MappingEngine.execute(plan,List.of(java.util.Map.of("raw",large),nullable),0,
                new com.linewell.dataelement.mapping.core.ExecutionContext(java.time.Instant.EPOCH,
                        java.time.ZoneOffset.UTC,java.util.Locale.ROOT,"validation"),null);
        assertTrue(result.rows().stream().allMatch(MappingEngine.Row::success));
        assertEquals(large,result.rows().getFirst().output().get("VALUE"));
        assertNull(result.rows().getLast().output().get("VALUE"));
        assertEquals("DECIMAL",plan.outputContracts().get("VALUE").logicalType());
    }

    @Test void boundedTextRequiresTheFullDeclaredNumericCapacity(){
        for(var type:List.of("INT","LONG","DECIMAL")){
            int capacity=type.equals("INT")?11:type.equals("LONG")?20:8;
            var draft=sharedDraft(type,type.equals("DECIMAL")?6:0,type.equals("DECIMAL")?2:0,true);
            secondaryField(draft).put("logicalType","STRING").put("length",capacity);
            assertSharedAccepted(draft);
            secondaryField(draft).put("length",capacity-1);
            assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());
        }
        var unknown=sharedDraft("DECIMAL",0,0,true);
        secondaryField(unknown).put("logicalType","STRING").put("length",255);
        assertEquals("TARGET_TYPE_MISMATCH",sharedError(unknown).code());
    }

    @Test void hiveIntAcceptsBoundedIntegralDecimalAndPreservesTheSharedDecimalOutput()throws Exception {
        var draft=sharedDraft("DECIMAL",5,0,false);
        secondaryField(draft).put("logicalType","INT").put("physicalType","int");
        var plan=MappingEngine.compile(draft);
        assertDoesNotThrow(()->UnifiedMappingService.validateSharedTargetFields(draft,plan));
        var result=MappingEngine.execute(plan,List.of(java.util.Map.of("raw",-99999),java.util.Map.of("raw",99999)),0,
                new com.linewell.dataelement.mapping.core.ExecutionContext(java.time.Instant.EPOCH,
                        java.time.ZoneOffset.UTC,java.util.Locale.ROOT,"validation"),null);
        assertTrue(result.rows().stream().allMatch(MappingEngine.Row::success));
        assertEquals(List.of(new java.math.BigDecimal("-99999"),new java.math.BigDecimal("99999")),
                result.rows().stream().map(row->row.output().get("VALUE")).toList());
        assertEquals("DECIMAL",plan.outputContracts().get("VALUE").logicalType());
    }

    @Test void integerTargetsRejectFractionalUnboundedAndOutOfRangeDecimalContracts(){
        for(int[] dimensions:List.of(new int[]{5,1},new int[]{0,0},new int[]{10,0})){
            var draft=sharedDraft("DECIMAL",dimensions[0],dimensions[1],true);
            secondaryField(draft).put("logicalType","INT").put("physicalType","int");
            assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());
        }
        for(String physical:List.of("smallint","tinyint","int(11) unsigned")){
            var draft=sharedDraft("DECIMAL",5,0,true);
            secondaryField(draft).put("logicalType","INT").put("physicalType",physical);
            assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());
        }
        var bigint=sharedDraft("DECIMAL",18,0,true);
        secondaryField(bigint).put("logicalType","LONG").put("physicalType","bigint");
        assertSharedAccepted(bigint);
        var oversized=sharedDraft("DECIMAL",19,0,true);
        secondaryField(oversized).put("logicalType","LONG").put("physicalType","bigint");
        assertEquals("TARGET_TYPE_MISMATCH",sharedError(oversized).code());
    }

    @Test void registeredTinyintRemainsInRangeThroughOracleDecimalAndHiveSmallint()throws Exception {
        var draft=MappingEngine.legacy((ObjectNode)JSON.readTree("""
            {"version":"1.0","mappings":[{"from":"/status","to":"/STATUS",
              "expression":"CAST(${field:status} AS INTEGER)","targetType":"NUMBER(5)"}],
             "schemaContract":{
               "sourceRef":{"datasourceId":"validation-source","tableId":"validation-source-table"},
               "source":[{"name":"status","logicalType":"INT","physicalType":"tinyint"}],
               "target":[{"name":"STATUS","logicalType":"DECIMAL","physicalType":"NUMBER","precision":5,"scale":0}],
               "targets":[{"nodeId":"hive","label":"Hive","fields":[{"name":"status","logicalType":"INT","physicalType":"smallint"}]}]
             }}
            """));
        assertSharedAccepted(draft);
        var source=(ObjectNode)draft.path("schemaContract").path("source").get(0);
        source.put("physicalType","int");
        assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());
        source.put("physicalType","tinyint");
        var rule=(ObjectNode)draft.path("rules").get(0);
        ((ObjectNode)rule.path("input")).put("expression","${field:status} * 1000");
        assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());
        ((ObjectNode)rule.path("input")).put("expression","CAST(${field:status} AS INTEGER)");
        ((ObjectNode)draft.path("schemaContract")).remove("sourceRef");
        assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());
    }

    @Test void registeredSmallintCanKeepItsFullRangeThroughOracleNumber5AndHiveSmallint()throws Exception {
        for(String physical:List.of("smallint","int2")) {
            var draft=MappingEngine.legacy((ObjectNode)JSON.readTree("""
                {"version":"1.0","mappings":[{"from":"/enabled","to":"/ENABLED","targetType":"NUMBER(5)"}],
                 "schemaContract":{"sourceRef":{"datasourceId":"validation-source","tableId":"validation-table"},
                  "source":[{"name":"enabled","logicalType":"INT","physicalType":"smallint"}],
                  "target":[{"name":"ENABLED","logicalType":"DECIMAL","physicalType":"NUMBER","precision":5,"scale":0}],
                  "targets":[{"nodeId":"hive","label":"Hive","fields":[{"name":"enabled","logicalType":"INT","physicalType":"smallint"}]}]}}
                """));
            ((ObjectNode)draft.path("schemaContract").path("source").get(0)).put("physicalType",physical);
            assertSharedAccepted(draft);
            ((ObjectNode)draft.path("schemaContract")).put("sourceRangeUnverified",true);
            assertEquals("TARGET_TYPE_MISMATCH",sharedError(draft).code());
        }
    }
    @Test void defaultValueCannotMakeAnIncludedNullableOutputFitANonNullableTarget()throws Exception {
        var draft=sharedDraft("STRING",0,0,true);
        secondaryField(draft).put("logicalType","STRING").put("nullable",false).put("hasDefault",true);
        assertEquals("TARGET_NULLABILITY_MISMATCH",sharedError(draft).code());
        ((ObjectNode)draft.path("rules").get(0).path("target")).put("nullable",false);
        assertSharedAccepted(draft);
    }

    @Test void everyRequiredSecondaryColumnNeedsOutputPassthroughOrADatabaseDefault()throws Exception {
        var draft=sharedDraft("STRING",0,0,false);
        var required=((com.fasterxml.jackson.databind.node.ArrayNode)draft.path("schemaContract").path("targets").get(0).path("fields"))
                .addObject().put("name","REQUIRED_CODE").put("logicalType","STRING").put("nullable",false);
        var error=sharedError(draft);assertEquals("TARGET_REQUIRED",error.code());assertTrue(error.getMessage().contains("REQUIRED_CODE"));
        required.put("hasDefault",true);assertSharedAccepted(draft);
    }

    @Test void datesAndFullRangeSignedLongsCanWidenToCompatibleSecondaryTargets()throws Exception {
        var date=sharedDraft("DATE",0,0,true);secondaryField(date).put("logicalType","DATETIME");assertSharedAccepted(date);
        var integer=sharedDraft("LONG",0,0,false);secondaryField(integer).put("logicalType","DECIMAL").put("precision",19).put("scale",0);assertSharedAccepted(integer);
        secondaryField(integer).put("precision",18);assertEquals("TARGET_TYPE_MISMATCH",sharedError(integer).code());
    }

    private static ObjectNode sharedDraft(String type,int precision,int scale,boolean nullable){
        var old=JSON.createObjectNode().put("version","1.0");
        old.putArray("mappings").addObject().put("from","/raw").put("to","/VALUE").put("targetType",type)
                .put("targetPrecision",precision).put("targetScale",scale).put("targetNullable",nullable);
        var draft=MappingEngine.legacy(old);
        draft.putObject("schemaContract").putArray("targets").addObject().put("nodeId","hive").put("label","Hive目标")
                .putArray("fields").addObject().put("name","value").put("logicalType",type).put("nullable",true);
        return draft;
    }
    private static ObjectNode secondaryField(ObjectNode draft){return (ObjectNode)draft.path("schemaContract").path("targets").get(0).path("fields").get(0);}
    private static MappingException sharedError(ObjectNode draft){var plan=MappingEngine.compile(draft);return assertThrows(MappingException.class,()->UnifiedMappingService.validateSharedTargetFields(draft,plan));}
    private static void assertSharedAccepted(ObjectNode draft){var plan=MappingEngine.compile(draft);assertDoesNotThrow(()->UnifiedMappingService.validateSharedTargetFields(draft,plan));}
}
