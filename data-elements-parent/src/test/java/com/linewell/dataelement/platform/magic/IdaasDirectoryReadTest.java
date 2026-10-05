package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class IdaasDirectoryReadTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("db/migrations/resources/idaas-foundation-20260928/functions/"+name+".ms"))
                .replaceAll("(?m)^import (db|identityTransport);\\r?\\n", "")
                .replaceAll("(?m)^import '@/[^\\r\\n]+;\\r?\\n", "");
    }

    private static Object fields(DbStub db, String operation, Object values) throws Exception {
        Map<String,Object> args = new LinkedHashMap<>();
        args.put("db", db); args.put("operation", operation); args.put("identityDomain", "workforce");
        args.put("entityType", "ORG"); args.put("objectId", "org-1"); args.put("values", values); args.put("prior", Map.of());
        return MagicScript.create(source("field-values"), null).execute(new MagicScriptContext(args));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bulkReadQueriesDefinitionsOnceAndMasksSensitiveValues() throws Exception {
        DbStub db = new DbStub();
        List<Map<String,Object>> rows = new ArrayList<>();
        for(int i=0;i<113;i++) rows.add(Map.of("id","org-"+i,"values",Map.of("label","plain","secret",Map.of("encrypted","cipher"),"unknown","hidden")));
        Map<String,Object> result = (Map<String,Object>) fields(db,"view-many",rows);
        assertEquals(1,db.queries); assertEquals(113,result.size());
        assertEquals(Map.of("label","plain","secret","[受保护]"), result.get("org-112"));
    }

    @Test
    void nextReadAndWriteReloadDefinitionsAndRequiredValidationRemains() throws Exception {
        DbStub db = new DbStub();
        fields(db,"view-many",List.of(Map.of("id","org-1","values",Map.of("label","before"))));
        db.required=true;
        assertInstanceOf(ExitValue.class,fields(db,"encode",Map.of()));
        assertEquals(2,db.queries);
    }

    @Test
    @SuppressWarnings("unchecked")
    void directoryFiltersScopeAndPaginatesBeforeBulkFieldRead() throws Exception {
        DbStub db = new DbStub(); Collector collector = new Collector();
        String prelude="var directoryScope=(permission,domain)=>({ctx:{userId:'operator'},all:false,orgIds:['org-2','org-3']});\n"
                +"var fieldValues=(operation,entityType,domain,objectId,values,prior)=>collector.read(operation,values);\n";
        Map<String,Object> args = Map.of("db",db,"collector",collector,"entity","orgs","operation","list","payload",Map.of("domain","workforce","page",2,"size",1));
        Map<String,Object> result = (Map<String,Object>) MagicScript.create(prelude+source("directory"),null).execute(new MagicScriptContext(args));
        assertEquals(2,((Number)result.get("total")).intValue());
        assertEquals(1,collector.calls); assertEquals(List.of("org-3"),collector.ids);
        List<Map<String,Object>> list=(List<Map<String,Object>>)result.get("list");
        assertEquals("org-3",list.get(0).get("id"));
        assertEquals(Map.of("label","org-3"),list.get(0).get("ext"));
    }

    public static class Collector {
        int calls; List<String> ids;
        public Map<String,Object> read(String operation,List<Map<String,Object>> values) {
            assertEquals("view-many",operation); calls++; ids=values.stream().map(row->String.valueOf(row.get("id"))).toList();
            Map<String,Object> result=new LinkedHashMap<>();
            values.forEach(row->result.put(String.valueOf(row.get("id")),row.get("values")));
            return result;
        }
    }

    public static class DbStub {
        int queries; boolean required;
        public DbStub normal(){return this;}
        public List<Map<String,Object>> select(String sql) {
            queries++;
            if(sql.contains("iam_field_definition_t")) return List.of(
                    Map.of("field_key","label","sensitive_flag",0,"required_flag",required?1:0,"value_type","STRING"),
                    Map.of("field_key","secret","sensitive_flag",1,"required_flag",0,"value_type","STRING"));
            List<Map<String,Object>> result=new ArrayList<>();
            for(String id:List.of("org-1","org-2","org-3")) result.add(Map.of("id",id,"identity_domain","workforce","name",id,"status","ACTIVE","version",1,"ext_json","{\"label\":\""+id+"\"}"));
            return result;
        }
    }
}
