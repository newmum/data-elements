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
    private static MappingEngine.Plan jsonTextPlan(boolean insensitive)throws Exception{
        var spec=JSON.readTree("""
            {"schemaVersion":"2.0","schemaContract":{"source":[{"name":"DOC","logicalType":"JSON","physicalType":"json","nullable":true}]},"rules":[{"input":{"kind":"FIELD","name":"DOC"},"target":{"name":"TEXT","logicalType":"STRING","nullable":true}}]}
            """);
        ((com.fasterxml.jackson.databind.node.ObjectNode)spec).putObject("policies").put("caseSensitive",!insensitive);
        return MappingEngine.compile(spec);
    }
    @Test void nativeJsonTextPreservesNumbersWhitespaceScalarsAndSqlNullAt20And100Rows()throws Exception{
        List<String> values=Arrays.asList(null,"null","\"\"","true","  {\"n\":123456789012345678901234567890.12345678901234567890}  ","[1,false,null,\"中文\"]");
        for(boolean insensitive:List.of(false,true))for(int count:List.of(20,100)){
            var rows=new ArrayList<Map<String,Object>>();for(int i=0;i<count;i++){var row=new LinkedHashMap<String,Object>();row.put(insensitive?"doc":"DOC",values.get(i%values.size()));rows.add(row);}
            var result=MappingEngine.execute(jsonTextPlan(insensitive),rows,0,CONTEXT,null);assertEquals(0,result.dictionaryBatches());assertEquals(count,result.rows().size());
            for(int i=0;i<count;i++){assertTrue(result.rows().get(i).success());assertEquals(values.get(i%values.size()),result.rows().get(i).output().get("TEXT"));}
        }
    }
    @Test void nativeJsonValidationRejectsMalformedOrMultipleValuesWithoutRewritingErrorPolicy()throws Exception{
        var invalid=List.of(""," ","{","{\"x\":1} false","[1,]","{\"x\":NaN}","unquoted","\"unterminated");
        for(String value:invalid){var result=MappingEngine.execute(jsonTextPlan(false),List.of(Map.of("DOC",value)),0,CONTEXT,null);assertFalse(result.rows().getFirst().success());assertEquals("INVALID_JSON",result.rows().getFirst().errors().getFirst().code());}
        String large="{\"value\":\""+"x".repeat(300_000)+"\",\"n\":"+"9".repeat(1500)+"}";
        var result=MappingEngine.execute(jsonTextPlan(false),List.of(Map.of("DOC",large)),0,CONTEXT,null);assertTrue(result.rows().getFirst().success());assertEquals(large,result.rows().getFirst().output().get("TEXT"));
    }
    @Test void jsonValidationFailureIsNotCachedAsSuccessAndOrdinaryTextRemainsOpaque()throws Exception{
        var spec=jsonTextPlan(false).document();((com.fasterxml.jackson.databind.node.ObjectNode)spec.path("rules").get(0)).put("onError","NULL");var second=((com.fasterxml.jackson.databind.node.ArrayNode)spec.path("rules")).addObject();second.putObject("input").put("kind","FIELD").put("name","DOC");second.putObject("target").put("name","SECOND").put("logicalType","STRING");
        var result=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("DOC","{broken")),0,CONTEXT,null);assertFalse(result.rows().getFirst().success());assertEquals("INVALID_JSON",result.rows().getFirst().errors().getFirst().code());
        ((com.fasterxml.jackson.databind.node.ObjectNode)spec.path("schemaContract").path("source").get(0)).put("logicalType","STRING").put("physicalType","CLOB");
        result=MappingEngine.execute(MappingEngine.compile(spec),List.of(Map.of("DOC","{ordinary text")),0,CONTEXT,null);assertTrue(result.rows().getFirst().success());assertEquals("{ordinary text",result.rows().getFirst().output().get("SECOND"));
    }
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
        for(int count:List.of(20,100)){
            calls[0]=0;var result=MappingEngine.execute(MappingEngine.compile(spec),Collections.nCopies(count,Map.of("codes","A，X A；B")),0,CONTEXT,provider);
            assertEquals(1,calls[0]);assertEquals(count,result.rows().size());for(var row:result.rows())assertEquals("甲,X,甲,乙",row.output().get("labels"));
        }
    }
    @Test void commonMultiValueSeparatorsMatchTheRegistrationPreview()throws Exception{
        var spec=JSON.readTree("""
            {"version":"1.0","mappings":[{"from":"/codes","to":"/labels","lookup":{"multiValue":true,"values":{"A":"甲","B":"乙"}}}]}
            """);
        var plan=MappingEngine.compile(spec);
        for(String raw:List.of("A,B","A，B","A B","A\u3000B","A\tB","A\u00a0B","A、B","A;B","A；B")){
            var result=MappingEngine.execute(plan,List.of(Map.of("codes",raw)),0,CONTEXT,null);
            assertTrue(result.rows().getFirst().success());assertEquals("甲,乙",result.rows().getFirst().output().get("labels"),raw);
        }
        var result=MappingEngine.execute(plan,List.of(Map.of("codes"," A，X A；B、 ")),0,CONTEXT,null);
        assertEquals("甲,X,甲,乙",result.rows().getFirst().output().get("labels"));
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
