package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.mapping.core.ExecutionContext;
import com.linewell.dataelement.mapping.core.MappingEngine;
import com.linewell.dataelement.mapping.core.MappingException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OdsTimestampTargetCompatibilityTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Instant CLOCK=Instant.parse("2026-11-03T03:21:43.123456789Z");
    private final RegisteredDictionaryCatalog dictionaries=mock(RegisteredDictionaryCatalog.class);
    private final RegisteredMappingSchemaService schemas=mock(RegisteredMappingSchemaService.class);

    private UnifiedMappingService service() {
        when(dictionaries.bind(any())).thenAnswer(call->new RegisteredDictionaryCatalog.Bound(call.getArgument(0),Map.of()));
        var service=new UnifiedMappingService(dictionaries,new FieldMappingService());service.setSchemas(schemas);return service;
    }
    private ObjectNode legacy(String field,String expression,String physicalType,String logicalType) {
        var spec=JSON.createObjectNode().put("version","1.0");
        spec.putArray("mappings").addObject().put("to","/"+field).put("targetType","date").put("expression",expression);
        var contract=spec.putObject("schemaContract");
        contract.putArray("target").addObject().put("name",field).put("physicalType",physicalType)
                .put("logicalType",logicalType).put("nullable",false);
        contract.putObject("targetRef").put("datasourceId","validation-target").put("tableId","validation-table");
        return spec;
    }
    private Object value(MappingEngine.Plan plan,String field) {
        var rows=MappingEngine.execute(plan,List.of(Map.of()),0,
                new ExecutionContext(CLOCK,ZoneId.of("Asia/Shanghai"),Locale.ROOT,"validation-audit-time"),null).rows();
        assertTrue(rows.getFirst().success());return rows.getFirst().output().get(field);
    }

    @ParameterizedTest
    @ValueSource(strings={"TIMESTAMP","TIMESTAMP(6)","DATETIME","DATETIME2(7)","TIMESTAMP WITHOUT TIME ZONE"})
    void legacyOdsClockUsesTimestampPrecisionAndTheExistingRuntimeEngine(String physicalType) {
        var service=service();
        for(String field:List.of("ODS_RKSJ","ODS_GXSJ","ods_rksj"))for(String clock:List.of("LOCALTIMESTAMP"," current_timestamp ")) {
            var spec=legacy(field,clock,physicalType,"DATETIME");JsonNode original=spec.deepCopy();
            var plan=service.compile(spec).plan();
            assertEquals("DATETIME",plan.outputTypes().get(field));
            assertEquals(LocalDateTime.of(2026,11,3,11,21,43,123456789),value(plan,field));
            // The runtime needs only an ordinary DATETIME contract, not a new engine feature.
            assertEquals(plan.hash(),MappingEngine.compile(plan.document()).hash());
            assertEquals(plan.hash(),service.compile(plan.document()).plan().hash());
            assertEquals(original,spec);
        }
    }
    @Test void savedV2ClockGeneratorAlsoRepairsTheStaleDateType() {
        var service=service();var spec=MappingEngine.legacy(legacy("ODS_RKSJ","LOCALTIMESTAMP","TIMESTAMP","DATETIME"));
        ((ObjectNode)spec.path("rules").get(0)).putObject("input").put("kind","SYSTEM").put("generator","NOW");
        var plan=service.compile(spec).plan();assertEquals("DATETIME",plan.outputTypes().get("ODS_RKSJ"));
        assertEquals(LocalDateTime.of(2026,11,3,11,21,43,123456789),value(plan,"ODS_RKSJ"));
    }
    @Test void calendarDateAndExplicitRegisteredDateKeepTheirMeaning() {
        var service=service();var calendar=legacy("ODS_RKSJ","LOCALTIMESTAMP","DATE","DATE");
        assertEquals(LocalDate.of(2026,11,3),value(service.compile(calendar).plan(),"ODS_RKSJ"));
        var registered=legacy("ODS_RKSJ","LOCALTIMESTAMP","TIMESTAMP","DATETIME");
        ((ObjectNode)registered.path("schemaContract").path("target").get(0)).put("registeredFormat","DATE");
        var plan=service.compile(registered).plan();assertEquals("DATE",plan.outputTypes().get("ODS_RKSJ"));
        assertEquals(LocalDate.of(2026,11,3),value(plan,"ODS_RKSJ"));
    }
    @Test void manualExpressionsAndBusinessFieldsStillFailWithActionableTypes() {
        var service=service();
        for(var spec:List.of(legacy("business_time","LOCALTIMESTAMP","TIMESTAMP","DATETIME"),
                legacy("ODS_RKSJ","'2026-11-03'","TIMESTAMP","DATETIME"))) {
            var error=assertThrows(MappingException.class,()->service.compile(spec));
            assertEquals("TARGET_TYPE_MISMATCH",error.code());
            assertTrue(error.getMessage().contains("规则输出：DATE"));
            assertTrue(error.getMessage().contains("目标物理列：TIMESTAMP，逻辑类型：DATETIME"));
            assertTrue(error.getMessage().contains("显式配置转换步骤"));
        }
        var stepped=MappingEngine.legacy(legacy("ODS_RKSJ","LOCALTIMESTAMP","TIMESTAMP","DATETIME"));
        ((com.fasterxml.jackson.databind.node.ArrayNode)stepped.path("rules").get(0).path("steps"))
                .addObject().put("operator","CAST").putObject("args").put("logicalType","DATE");
        assertEquals("TARGET_TYPE_MISMATCH",assertThrows(MappingException.class,()->service.compile(stepped)).code());
    }
    @Test void missingPhysicalContractDoesNotGuessTimestampFromTheAuditName() {
        var service=service();var spec=legacy("ODS_RKSJ","LOCALTIMESTAMP","TIMESTAMP","DATETIME");spec.remove("schemaContract");
        assertEquals("DATE",service.compile(spec).plan().outputTypes().get("ODS_RKSJ"));
    }
    @Test void schemaAuthorizationAndDriftFailurePrecedeCompatibility() {
        var service=service();
        for(String code:List.of("RESOURCE_FORBIDDEN","SCHEMA_DRIFT")) {
            var denied=new MappingException(code,"validation rejected");doThrow(denied).when(schemas).checkContracts(any());
            assertSame(denied,assertThrows(MappingException.class,()->service.compile(legacy("ODS_RKSJ","LOCALTIMESTAMP","TIMESTAMP","DATETIME"))));
        }
    }
    @ParameterizedTest
    @ValueSource(ints={20,100})
    void compatibilityAddsNoPerNodeReadOrPerRecordQuery(int count) {
        var service=service();Map<String,JsonNode> batch=new LinkedHashMap<>();
        for(int index=0;index<count;index++)batch.put("validation-node-"+index,legacy("ODS_RKSJ","LOCALTIMESTAMP","TIMESTAMP","DATETIME"));
        try(var tenant=TenantContext.use("validation-tenant")) {
            var plans=service.compileBatch(batch);assertEquals(count,plans.size());
            assertTrue(plans.values().stream().allMatch(compiled->compiled.plan().outputTypes().get("ODS_RKSJ").equals("DATETIME")));
            var preview=service.preview(batch.values().iterator().next(),Collections.nCopies(count,Map.of()));
            assertEquals(count,preview.resultRows().size());assertEquals(0L,((Number)preview.stats().get("dictionarySqlCount")).longValue());
        }
        // Publication checks physical metadata; the explicit editor preview only checks authorized registration.
        verify(dictionaries,times(2)).bind(any());verify(schemas,times(1)).checkContracts(any());verify(schemas,times(1)).checkRegisteredContracts(any());
        verifyNoMoreInteractions(dictionaries,schemas);
    }
}
