package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.linewell.dataelement.mapping.jdbc.BatchDictionaryReader;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class MappingAdvancedSemanticsTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static ObjectNode definition(String mode){ObjectNode d=JSON.createObjectNode().put("datasourceId","db").put("tableId","t").put("schema","").put("table","dict").put("valueColumn","label").put("duplicatePolicy","ERROR").put("freshnessMode",mode).put("ttlSeconds",10);d.putArray("keyColumns").add("code");d.putArray("keyTypes").add("STRING");return d;}
    private static DictionarySpec spec(){return new DictionarySpec("d","db","t","","dict",List.of("code"),List.of("STRING"),"label",null,"ERROR");}
    @Test void ttlFetchesOnlyOneBatchIncludingNegativeKeysAndNeverSharesTenantEntries(){
        AtomicInteger calls=new AtomicInteger();AtomicLong clock=new AtomicLong();var cache=new DictionaryPolicyProvider.Cache(clock::get);ObjectNode bindings=JSON.createObjectNode();bindings.set("d",definition("TTL_BATCH_CACHE"));DictionarySpec.Provider live=(d,keys)->{calls.incrementAndGet();assertEquals(100,keys.size());Map<DictionarySpec.Key,DictionarySpec.LookupValue> result=new HashMap<>();keys.forEach(key->result.put(key,key.values().getFirst().equals("99")?DictionarySpec.LookupValue.missing():new DictionarySpec.LookupValue(true,"label")));return result;};Set<DictionarySpec.Key> keys=new LinkedHashSet<>();for(int i=0;i<100;i++)keys.add(new DictionarySpec.Key(List.of(""+i)));
        var provider=new DictionaryPolicyProvider(live,bindings,JSON.createObjectNode(),cache,"tenant-a");assertEquals(100,provider.fetch(spec(),keys).size());assertFalse(provider.fetch(spec(),keys).get(new DictionarySpec.Key(List.of("99"))).found());assertEquals(1,calls.get());
        new DictionaryPolicyProvider(live,bindings,JSON.createObjectNode(),cache,"tenant-b").fetch(spec(),keys);assertEquals(2,calls.get());clock.set(11_000_000_000L);provider.fetch(spec(),keys);assertEquals(3,calls.get());
    }
    @Test void pinnedSnapshotPreservesNullAndRejectsMissingCorruptOrForeignDefinition(){
        ObjectNode payload=JSON.createObjectNode().put("definitionHash","authorized");payload.putArray("rows").addObject().putArray("keys").add("a");((ObjectNode)payload.path("rows").get(0)).putNull("value");ObjectNode bindings=JSON.createObjectNode();ObjectNode d=definition("VERSIONED_SNAPSHOT").put("snapshotId","v1").put("snapshotHash",MappingEngine.hash(payload)).put("definitionHash","authorized");bindings.set("d",d);ObjectNode snapshots=JSON.createObjectNode();snapshots.set("v1",payload);var provider=new DictionaryPolicyProvider((dictionary,keys)->{fail("snapshot must not access JDBC");return Map.of();},bindings,snapshots,new DictionaryPolicyProvider.Cache(),"tenant");var key=new DictionarySpec.Key(List.of("a"));assertTrue(provider.fetch(spec(),Set.of(key)).get(key).found());assertNull(provider.fetch(spec(),Set.of(key)).get(key).value());
        assertEquals("SNAPSHOT_UNAVAILABLE",assertThrows(MappingException.class,()->new DictionaryPolicyProvider(null,bindings,JSON.createObjectNode(),new DictionaryPolicyProvider.Cache(),"tenant").fetch(spec(),Set.of(key))).code());
        snapshots.path("v1").deepCopy();((ObjectNode)snapshots.path("v1")).put("definitionHash","other");assertEquals("RESOURCE_FORBIDDEN",assertThrows(MappingException.class,()->new DictionaryPolicyProvider(null,bindings,snapshots,new DictionaryPolicyProvider.Cache(),"tenant").fetch(spec(),Set.of(key))).code());
    }
    @Test void completeSnapshotRejectsRowOverflowAndPreservesDecimalKeyBeyondTenPlaces(){
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","","");var jdbc=new JdbcTemplate(source);jdbc.execute("CREATE TABLE \"dict\" (\"code\" DECIMAL(38,18),\"label\" VARCHAR(32))");BigDecimal key=new BigDecimal("123456789012.123456789012345678");jdbc.update("INSERT INTO \"dict\" VALUES (?,?)",key,"first");var dictionary=new DictionarySpec("d","db","t","","dict",List.of("code"),List.of("DECIMAL"),"label",null,"ERROR");
        try(var reader=new BatchDictionaryReader(id->source.getConnection())){assertTrue(reader.fetch(dictionary,Set.of(new DictionarySpec.Key(List.of(key)))).values().iterator().next().found());assertEquals(1,reader.snapshot(dictionary,"hash").path("rows").size());}
        jdbc.update("DELETE FROM \"dict\"");jdbc.execute("INSERT INTO \"dict\" SELECT X,'value' FROM SYSTEM_RANGE(1,5001)");try(var reader=new BatchDictionaryReader(id->source.getConnection())){assertEquals("BUDGET_EXCEEDED",assertThrows(MappingException.class,()->reader.snapshot(dictionary,"hash")).code());}
    }
    @Test void schemaDiffCoversMissingTypeNullabilityAndPrecisionAndOptionalSource(){
        var expected=List.of(new SchemaContract.Field("amount","DECIMAL",false,0,18,2),new SchemaContract.Field("optional","STRING",true,10,0,0));var actual=List.of(new SchemaContract.Field("amount","DECIMAL",true,0,20,3),new SchemaContract.Field("new","INT",false,0,0,0));var differences=SchemaContract.compare(expected,actual,false,Set.of("optional"));assertEquals(Set.of("SCHEMA_NULLABILITY_CHANGED","SCHEMA_PRECISION_CHANGED","SCHEMA_FIELD_ADDED"),new HashSet<>(differences.stream().map(SchemaContract.Difference::code).toList()));
    }
    @Test void functionSchemasCoverAllNamesAndPreviewMasksAliasesWhenRequested(){
        for(String name:FunctionRegistry.NAMES)assertNotNull(FunctionArgumentSchema.parameters(name));assertEquals(3,FunctionArgumentSchema.parameters("toDecimal").size());assertEquals("***",PreviewMasking.mask(List.of(Map.of("copied_value","private","age",20)),Set.of(),true).getFirst().get("copied_value"));assertEquals(20,PreviewMasking.mask(List.of(Map.of("phone","123456","age",20)),Set.of(),false).getFirst().get("age"));
    }
    @Test void exponentBudgetAndFallbackLengthCannotBypassTargetContract()throws Exception{
        assertEquals("BUDGET_EXCEEDED",assertThrows(MappingException.class,()->ValueTypes.decimal("1e1000000000")).code());
        var plan=MappingEngine.compile(JSON.readTree("""
            {"schemaVersion":"2.0","rules":[{"ruleId":"r","input":{"kind":"FIELD","name":"phone"},"target":{"name":"alias","logicalType":"STRING","length":3},"onError":"DEFAULT_VALUE","defaultValue":"toolong","steps":[]}]}
            """));
        assertTrue(plan.sensitiveOutputs(Set.of()).contains("alias"));
        var result=MappingEngine.execute(plan,List.of(Map.of("phone","123456")),0,new ExecutionContext(java.time.Instant.EPOCH,java.time.ZoneId.of("Asia/Shanghai"),Locale.CHINA,"test"),null);
        assertFalse(result.rows().getFirst().success());assertEquals("LENGTH_EXCEEDED",result.rows().getFirst().errors().getFirst().code());
    }
}
