package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceFieldNamesTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final ExecutionContext CONTEXT=new ExecutionContext(Instant.EPOCH,ZoneId.of("Asia/Shanghai"),Locale.ROOT,"validation");
    private static MappingEngine.Plan plan(boolean caseSensitive)throws Exception {
        return MappingEngine.compile(JSON.readTree("""
            {"schemaVersion":"2.0","policies":{"caseSensitive":%s,"onMissingSource":"FAIL"},
             "schemaContract":{"source":[{"name":"event_no","logicalType":"STRING","nullable":false}]},
             "rules":[{"ruleId":"event","input":{"kind":"FIELD","name":"event_no"},"target":{"name":"event_no","logicalType":"STRING","nullable":false},"steps":[]}]}
            """.formatted(caseSensitive)));
    }
    @Test void absentDeclaredFieldsNeverBorrowAnIndependentlyDeclaredNamingVariant() throws Exception {
        for (boolean hasSchema : List.of(true,false)) for (boolean sensitive : List.of(true,false)) for (int count : List.of(20,100)) {
            var spec=JSON.createObjectNode().put("schemaVersion","2.0");
            spec.putObject("policies").put("caseSensitive",sensitive).put("onMissingSource","NULL");
            var fields=spec.putObject("schemaContract").putArray("source");
            var rules=spec.putArray("rules");
            for(String name:List.of("openKflx","open_kflx")) {
                fields.addObject().put("name",name).put("logicalType","ANY").put("nullable",true);
                var rule=rules.addObject().put("ruleId",name).put("sourceOptional",true);
                rule.putObject("input").put("kind","FIELD").put("name",name).put("logicalType","ANY");
                rule.putObject("target").put("name",name).put("logicalType","ANY").put("nullable",true);
                rule.putArray("steps");
            }
            if(!hasSchema) spec.remove("schemaContract");
            var plan=MappingEngine.compile(spec);
            var rows=new ArrayList<Map<String,Object>>();
            for(int i=0;i<count;i++) rows.add(Map.of("open_kflx",List.of("validation-"+i)));
            SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("open_kflx","ARRAY",true,0,0,0)));
            var result=MappingEngine.execute(plan,rows,0,CONTEXT,(dictionary,keys)->{fail("名称解析不得查询字典");return Map.of();});
            assertEquals(0,result.dictionaryBatches());assertEquals(count,result.rows().size());
            for(int i=0;i<count;i++) {
                var row=result.rows().get(i);assertTrue(row.success());
                assertNull(row.output().get("openKflx"));assertEquals(rows.get(i).get("open_kflx"),row.output().get("open_kflx"));
            }
            assertNull(plan.sourceNames(Set.of("open_kflx")).resolve("openKflx"));
            assertEquals("open_kflx",plan.sourceNames(Set.of("open_kflx")).resolve("open_kflx"));
        }
    }
    @Test void exactNamesWinIncludingNullAndCaseSensitivityRemainsEffective()throws Exception {
        var values=new LinkedHashMap<String,Object>();values.put("event_no",null);values.put("eventNo","validation");
        var result=MappingEngine.execute(plan(true),List.of(values),0,CONTEXT,null);
        assertFalse(result.rows().getFirst().success());assertNull(result.rows().getFirst().output().get("event_no"));
        assertNull(new SourceFieldNames(Set.of("EVENT_NO"),true).resolve("event_no"));
        assertNull(new SourceFieldNames(Set.of("foo"),true).resolve("Foo"));
        assertEquals("EVENT_NO",new SourceFieldNames(Set.of("EVENT_NO"),false).resolve("event_no"));
        assertEquals("EVENT_NO",new SourceFieldNames(Set.of("EVENT_NO"),false).resolve("eventNo"));
    }
    @Test void camelAndSqlNamesResolveInBothDirectionsWithoutGuessingMissingFields() {
        assertEquals("eventNo",new SourceFieldNames(Set.of("eventNo"),true).resolve("event_no"));
        assertEquals("camera_index_code",new SourceFieldNames(Set.of("camera_index_code"),true).resolve("cameraIndexCode"));
        assertEquals("picBase64",new SourceFieldNames(Set.of("picBase64"),true).resolve("pic_base64"));
        assertNull(new SourceFieldNames(Set.of("eventNO"),true).resolve("event_no"));
        assertNull(new SourceFieldNames(Set.of("event.No"),false).resolve("event_no"));
        assertNull(new SourceFieldNames(Set.of("missing"),false).resolve("event_no"));
    }
    @Test void ambiguousCaseOrNamingMatchesAreRejectedEvenWhenOneValueIsNull()throws Exception {
        var values=new LinkedHashMap<String,Object>();values.put("eventNo",null);values.put("eventNO","validation");
        var result=MappingEngine.execute(plan(false),List.of(values),0,CONTEXT,null);
        assertFalse(result.rows().getFirst().success());assertEquals("AMBIGUOUS_FIELD",result.rows().getFirst().errors().getFirst().code());
        var error=assertThrows(MappingException.class,()->new SourceFieldNames(Set.of("eventNo","eventNO"),false).resolve("event_no"));
        assertEquals("AMBIGUOUS_FIELD",error.code());
        // An exact name still takes priority over both alternatives.
        assertEquals("event_no",new SourceFieldNames(Set.of("event_no","eventNo","eventNO"),false).resolve("event_no"));
    }
    @Test void readerSchemaUsesTheSameResolutionAndStillRejectsTypeDrift()throws Exception {
        var plan=plan(true);
        SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("eventNo","STRING",true,0,0,0)));
        var error=assertThrows(MappingException.class,()->SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("eventNo","LONG",true,0,0,0))));
        assertEquals("SCHEMA_DRIFT",error.code());
        assertThrows(MappingException.class,()->SchemaContract.requireInput(plan,List.of(new SchemaContract.Field("other","STRING",true,0,0,0))));
    }
    @Test void twentyAndHundredRowsNeedNoLookupsAndKeepAllRequiredValues()throws Exception {
        for(int count:new int[]{20,100}){
            var rows=new ArrayList<Map<String,Object>>();for(int index=0;index<count;index++)rows.add(Map.of("eventNo","validation-"+index));
            var result=MappingEngine.execute(plan(true),rows,0,CONTEXT,(dictionary,keys)->{fail("字段命名适配不得发起SQL或接口查询");return Map.of();});
            assertEquals(count,result.rows().size());assertEquals(0,result.dictionaryBatches());
            for(int index=0;index<count;index++){assertTrue(result.rows().get(index).success());assertEquals("validation-"+index,result.rows().get(index).output().get("event_no"));}
        }
    }
}
