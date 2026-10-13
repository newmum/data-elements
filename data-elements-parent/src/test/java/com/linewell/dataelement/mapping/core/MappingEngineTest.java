package com.linewell.dataelement.mapping.core;

import com.fasterxml.jackson.databind.*;
import com.linewell.dataelement.mapping.jdbc.BatchDictionaryReader;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MappingEngineTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final ExecutionContext CONTEXT=new ExecutionContext(Instant.parse("2026-10-13T00:00:00Z"),ZoneId.of("Asia/Shanghai"),Locale.CHINA,"test-input");
    @Test void usesJavaFunctionsAndKeepsZeroAndFalse()throws Exception{
        var spec=JSON.readTree("""
            {"version":"1.0","mappings":[
              {"from":"/s","to":"/x","transform":{"fn":"upper"}},
              {"from":"/n","to":"/d","transform":{"fn":"toDecimal","args":[6,2,"HALF_UP"]}},
              {"constant":false,"to":"/flag"},{"constant":0,"to":"/zero"}]}
            """);
        var result=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("s","中文abc","n","12.345")),0,CONTEXT,null);
        assertTrue(result.rows().getFirst().success());assertEquals("中文ABC",result.rows().getFirst().output().get("x"));assertEquals(new BigDecimal("12.35"),result.rows().getFirst().output().get("d"));assertEquals(false,result.rows().getFirst().output().get("flag"));
    }
    @Test void validatesAndConvertsIdentityExplicitly()throws Exception{
        var args=JSON.readTree("[{\"centuryPolicy\":\"PREFIX_19\",\"already18Policy\":\"VALIDATE_AND_KEEP\"}]");
        assertEquals("11010519491231002X",FunctionRegistry.bind("idCard15To18",args).apply("110105491231002",CONTEXT));
        assertThrows(MappingException.class,()->FunctionRegistry.bind("idCard15To18",JSON.createArrayNode()));
        assertFalse((Boolean)FunctionRegistry.bind("idCardValidate",JSON.createArrayNode()).apply("110105194912310021",CONTEXT));
    }
    @Test void batchesAcross100FieldsAnd100RowsWithoutCache()throws Exception{
        var spec=JSON.createObjectNode().put("version","1.0");var bindings=spec.putObject("dictionaryBindings");var d=bindings.putObject("dict");d.put("datasourceId","registered-db").put("tableId","registered-table").put("table","dictionary").put("valueColumn","label").put("duplicatePolicy","ERROR");d.putArray("keyColumns").add("code");d.putArray("keyTypes").add("STRING");var mappings=spec.putArray("mappings");
        for(int f=0;f<100;f++){var m=mappings.addObject().put("from","/code").put("to","/label"+f);m.putObject("lookup").put("dictionaryId","dict");}
        var plan=MappingEngine.compile(spec);
        for(int count:List.of(20,100)){
            List<Map<String,Object>>rows=new ArrayList<>();for(int i=0;i<count;i++)rows.add(Map.of("code","C"+i));
            try(var setup=DriverManager.getConnection("jdbc:h2:mem:mapping"+count+";DB_CLOSE_DELAY=-1")){setup.createStatement().execute("CREATE TABLE \"dictionary\"(\"code\" VARCHAR PRIMARY KEY,\"label\" VARCHAR)");try(var s=setup.prepareStatement("INSERT INTO \"dictionary\" VALUES(?,?)")){for(int i=0;i<count;i++){s.setString(1,"C"+i);s.setString(2,"标签"+i);s.addBatch();}s.executeBatch();}}
            try(var provider=new BatchDictionaryReader(id->DriverManager.getConnection("jdbc:h2:mem:mapping"+count))){var result=MappingEngine.execute(plan,rows,0,CONTEXT,provider);assertEquals(1,provider.queries());assertEquals(count,result.rows().size());assertEquals("标签0",result.rows().getFirst().output().get("label99"));}
        }
    }
    @Test void multiValueUsesOneBatchAndPreservesOrderAndDuplicates()throws Exception{
        var spec=JSON.readTree("""
            {"version":"1.0","dictionaryBindings":{"dict":{"datasourceId":"db","tableId":"t","table":"dictionary","keyColumns":["code"],"keyTypes":["STRING"],"valueColumn":"label"}},
             "mappings":[{"from":"/codes","to":"/labels","lookup":{"dictionaryId":"dict","multiValue":true,"multiValueSeparator":",","onMissing":"KEEP_SOURCE"}}]}
            """);
        int[] calls={0};DictionarySpec.Provider provider=(d,keys)->{calls[0]++;assertEquals(3,keys.size());Map<DictionarySpec.Key,DictionarySpec.LookupValue>out=new HashMap<>();out.put(new DictionarySpec.Key(List.of("A")),new DictionarySpec.LookupValue(true,"甲"));out.put(new DictionarySpec.Key(List.of("B")),new DictionarySpec.LookupValue(true,"乙"));return out;};
        var result=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("codes","A,,X,A,B")),0,CONTEXT,provider);assertEquals(1,calls[0]);assertEquals("甲,X,甲,乙",result.rows().getFirst().output().get("labels"));
    }
    @Test void targetDependenciesAndCyclesAreExplicit()throws Exception{
        var spec=JSON.readTree("""
            {"version":"1.0","mappings":[{"expression":"${target:a} || '-suffix'","to":"/b"},{"from":"/x","to":"/a"}]}
            """);
        var result=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("x","value")),0,CONTEXT,null);assertEquals("value-suffix",result.rows().getFirst().output().get("b"));
        var cycle=JSON.readTree("""
            {"version":"1.0","mappings":[{"expression":"${target:a}","to":"/a"}]}
            """);assertEquals("CYCLIC_DEPENDENCY",assertThrows(MappingException.class,()->MappingEngine.compile(cycle)).code());
    }
    @Test void invalidDataAndUnknownFunctionsNeverPretendSuccess()throws Exception{
        var spec=JSON.readTree("""
            {"version":"1.0","mappings":[{"from":"/x","to":"/date","transform":{"fn":"parseDate","args":["uuuu-MM-dd","DATE"]}}]}
            """);
        var result=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("x","2023-02-29")),0,CONTEXT,null);assertFalse(result.rows().getFirst().success());
        assertThrows(MappingException.class,()->FunctionRegistry.bind("missingFunction",JSON.createArrayNode()));
        assertEquals(56,FunctionRegistry.NAMES.size());
    }
}
