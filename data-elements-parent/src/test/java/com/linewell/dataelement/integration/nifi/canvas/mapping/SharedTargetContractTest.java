package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.mapping.core.MappingEngine;
import com.linewell.dataelement.mapping.core.MappingException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SharedTargetContractTest {
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
