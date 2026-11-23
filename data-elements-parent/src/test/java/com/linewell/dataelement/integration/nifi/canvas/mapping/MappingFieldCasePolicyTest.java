package com.linewell.dataelement.integration.nifi.canvas.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.mapping.core.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MappingFieldCasePolicyTest {
    private final ObjectMapper json=new ObjectMapper();
    private UnifiedMappingService service(){
        var catalog=mock(RegisteredDictionaryCatalog.class);
        when(catalog.bind(any())).thenAnswer(call->new RegisteredDictionaryCatalog.Bound(call.getArgument(0),Map.of()));
        return new UnifiedMappingService(catalog,new FieldMappingService());
    }
    @Test void registeredOldStrictDraftUsesPhysicalNamesInRulesConditionsAndOutputDependencies()throws Exception {
        var spec=(ObjectNode)json.readTree("""
            {"schemaVersion":"2.0","policies":{"caseSensitive":true},"rules":[
              {"ruleId":"copy","input":{"kind":"FIELD","name":"id"},"target":{"name":"id","logicalType":"LONG"},"steps":[]},
              {"ruleId":"expr","input":{"kind":"EXPRESSION","expression":"${target:id}+${field:id}"},"target":{"name":"twice","logicalType":"LONG"},"steps":[]},
              {"ruleId":"conditional","input":{"kind":"FIELD","name":"id"},"target":{"name":"filtered","logicalType":"LONG"},"steps":[],
               "when":{"op":"eq","left":{"kind":"FIELD","name":"Id"},"right":{"kind":"LITERAL","value":7}},
               "then":{"input":{"kind":"TARGET","name":"iD"},"steps":[]},"else":{"input":{"kind":"LITERAL","value":0},"steps":[]}}],
             "schemaContract":{"sourceRef":{"datasourceId":"validation-source","tableId":"source"},
               "targetRef":{"datasourceId":"validation-target","tableId":"target"},
               "source":[{"name":"ID","logicalType":"LONG"}],
               "target":[{"name":"ID","logicalType":"LONG"},{"name":"TWICE","logicalType":"LONG"},{"name":"FILTERED","logicalType":"LONG"}],
               "targets":[{"label":"Hive","fields":[{"name":"id","logicalType":"LONG"},{"name":"twice","logicalType":"LONG"},{"name":"filtered","logicalType":"LONG"}]}]}}
            """);
        var plan=service().compileForEditing(spec).plan();
        assertFalse(plan.document().path("policies").path("caseSensitive").asBoolean());
        SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("id","LONG",true,0,0,0)));
        var result=MappingEngine.execute(plan,List.of(Map.of("iD",7L)),0,
            new ExecutionContext(Instant.EPOCH,ZoneOffset.UTC,Locale.ROOT,"validation-case"),null);
        assertTrue(result.rows().getFirst().success(),result.rows().getFirst().errors().toString());
        assertEquals(Map.of("ID",7L,"TWICE",14L,"FILTERED",7L),result.rows().getFirst().output());
        assertTrue(spec.path("policies").path("caseSensitive").asBoolean(),"compile must not mutate the saved draft");
    }
    @Test void missingPolicyDefaultsToInsensitiveButExplicitUnregisteredStrictPolicySurvives()throws Exception {
        var spec=(ObjectNode)json.readTree("""
            {"schemaVersion":"2.0","rules":[{"ruleId":"id","input":{"kind":"FIELD","name":"ID"},"target":{"name":"id","logicalType":"LONG"},"steps":[]}]}
            """);
        assertFalse(service().compileForEditing(spec).plan().document().path("policies").path("caseSensitive").asBoolean());
        spec.putObject("policies").put("caseSensitive",true);
        assertTrue(service().compileForEditing(spec).plan().document().path("policies").path("caseSensitive").asBoolean());
        assertFalse(new FieldMappingService().normalizeSpec(null).path("passthroughCaseSensitive").asBoolean());
    }
    @Test void ambiguousRegisteredNamesFailBeforeExecution()throws Exception {
        var spec=(ObjectNode)json.readTree("""
            {"schemaVersion":"2.0","rules":[{"ruleId":"id","input":{"kind":"FIELD","name":"Id"},"target":{"name":"id"},"steps":[]}],
             "schemaContract":{"sourceRef":{"datasourceId":"validation","tableId":"source"},"source":[{"name":"ID"},{"name":"id"}]}}
            """);
        assertEquals("AMBIGUOUS_FIELD",assertThrows(MappingException.class,()->service().compileForEditing(spec)).code());
    }
    @Test void literalEnumAndFunctionPayloadsKeepTheirOriginalCaseAndReferenceLikeKeys()throws Exception {
        var spec=(ObjectNode)json.readTree("""
            {"schemaVersion":"2.0","schemaContract":{"sourceRef":{},"source":[{"name":"ID"}],"targetRef":{},"target":[{"name":"VALUE"}]},
             "rules":[{"input":{"kind":"LITERAL","value":{"kind":"FIELD","name":"id","expression":"${target:value}"}},
               "target":{"name":"value"},"defaultValue":{"kind":"FIELD","name":"id"},"steps":[
                {"operator":"STATIC_MAP","args":{"values":{"x":{"kind":"FIELD","name":"id"}}}},
                {"operator":"FUNCTION","args":{"parameters":[{"kind":"TARGET","name":"value"}]}}]}]}
            """);
        var before=spec.deepCopy();
        UnifiedMappingService.applyFieldNamePolicy(spec);UnifiedMappingService.alignSourceNames(spec);UnifiedMappingService.alignRegisteredTargetNames(spec);
        var rule=spec.path("rules").get(0);var original=before.path("rules").get(0);
        assertEquals(original.path("input"),rule.path("input"));
        assertEquals(original.path("defaultValue"),rule.path("defaultValue"));
        assertEquals(original.path("steps"),rule.path("steps"));
        assertEquals("VALUE",rule.path("target").path("name").asText());
    }
}
