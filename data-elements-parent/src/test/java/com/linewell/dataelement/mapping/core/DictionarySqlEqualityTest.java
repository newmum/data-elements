package com.linewell.dataelement.mapping.core;

import com.linewell.dataelement.mapping.jdbc.BatchDictionaryReader;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DictionarySqlEqualityTest {
    @Test void databaseEqualityMapsPaddingAndCaseToEveryOriginalRequestInOneQuery()throws Exception {
        for(int count:List.of(20,100)) {
            String url="jdbc:h2:mem:dict_equality_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
            try(var c=DriverManager.getConnection(url);var s=c.createStatement()) {
                s.execute("CREATE TABLE \"dict\"(\"code\" VARCHAR_IGNORECASE(20),\"label\" VARCHAR(40))");
                try(var p=c.prepareStatement("INSERT INTO \"dict\" VALUES (?,?)")){for(int i=0;i<count;i++){p.setString(1,"A"+i);p.setString(2,"label"+i);p.addBatch();}p.executeBatch();}
                var keys=new LinkedHashSet<DictionarySpec.Key>();for(int i=0;i<count;i++){keys.add(new DictionarySpec.Key(List.of("A"+i)));keys.add(new DictionarySpec.Key(List.of("a"+i)));}
                var spec=new DictionarySpec("d","db","t","","dict",List.of("code"),List.of("STRING"),"label",null,"ERROR");
                try(var reader=new BatchDictionaryReader(id->DriverManager.getConnection(url))) {
                    var output=reader.fetch(spec,keys);assertEquals(count*2,output.size());assertTrue(output.values().stream().allMatch(DictionarySpec.LookupValue::found));assertEquals(1,reader.queries());
                }
                s.execute("DROP TABLE \"dict\"");s.execute("CREATE TABLE \"dict\"(\"code\" CHAR(8),\"label\" VARCHAR(40))");s.execute("INSERT INTO \"dict\" VALUES ('01','padded')");
                try(var reader=new BatchDictionaryReader(id->DriverManager.getConnection(url))) {var key=new DictionarySpec.Key(List.of("01"));assertEquals("padded",reader.fetch(spec,Set.of(key)).get(key).value());}
            }
        }
    }
    @Test void duplicateAndMissingPoliciesRemainIntactWithRequestCorrelation()throws Exception {
        String url="jdbc:h2:mem:dict_duplicate_"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        try(var c=DriverManager.getConnection(url);var s=c.createStatement()){s.execute("CREATE TABLE \"dict\"(\"code\" VARCHAR_IGNORECASE(20),\"label\" VARCHAR(40))");s.execute("INSERT INTO \"dict\" VALUES ('X','a'),('x','z')");}
        var key=new DictionarySpec.Key(List.of("x"));var missing=new DictionarySpec.Key(List.of("none"));
        for(String policy:List.of("ERROR","LEGACY_MAX_LABEL"))try(var reader=new BatchDictionaryReader(id->DriverManager.getConnection(url))){
            var spec=new DictionarySpec("d","db","t","","dict",List.of("code"),List.of("STRING"),"label",null,policy);
            if(policy.equals("ERROR"))assertEquals("DICT_DUPLICATE_KEY",assertThrows(MappingException.class,()->reader.fetch(spec,Set.of(key))).code());
            else {var result=reader.fetch(spec,Set.of(key,missing));assertEquals("z",result.get(key).value());assertFalse(result.get(missing).found());}
        }
    }
}
