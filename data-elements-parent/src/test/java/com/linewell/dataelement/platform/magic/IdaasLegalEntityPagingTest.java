package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.*;

/** Execute canonical Magic mapping and verify that member reads stay fixed for 20/100 parents. */
class IdaasLegalEntityPagingTest {
    public static class Database {
        int reads; int size; String statement;
        Database(int size) { this.size=size; }
        public Database normal(){return this;}
        public Map<String,Object> selectOne(String sql){reads++;return Map.of("total",120);}
        public List<Map<String,Object>> select(String sql){
            reads++;statement=sql;
            if(sql.contains("where m.entity_id in")) return List.of(Map.of("id","member","entity_id","entity-0","subject_id","person","relation_type","AGENT","name","关联人员","phone",""));
            var rows=new ArrayList<Map<String,Object>>();
            for(int i=0;i<size;i++) {
                var row=new HashMap<String,Object>();row.putAll(Map.of("id","entity-"+i,"version",1,"name","法人"+i,"code","code"+i,"status","ACTIVE","verification_status","UNVERIFIED","verified_now",0,"sub_count",i));
                row.put("created_utc","2026-10-01T00:00:00Z");row.put("updated_utc","2026-10-01T00:00:00Z");rows.add(row);
            }
            assertTrue(sql.contains("order by coalesce(n.sub_count,0) desc,e.tid asc limit #{take} offset #{skip}"));
            assertTrue(sql.contains("s.is_del=0") || sql.contains("e.is_del=0"));
            return rows;
        }
    }
    @Test void paginationUsesThreeReadsForEitherPageSizeAndNoReadInsideMapping() throws Exception {
        for(int size:List.of(20,100)) {
            var db=new Database(size);
            var result=(Map<?,?>)run(db,Map.of("paged",true,"size",size,"sortField","subAccountCount","sortDir","desc"),true);
            assertEquals(3,db.reads);assertEquals(size,((List<?>)result.get("list")).size());assertEquals(120,result.get("total"));
            assertTrue(db.statement.contains("s.identity_domain='public' and s.is_del=0"));
        }
    }
    @Test void missingPublicPermissionRejectsBeforeAnyBusinessRead() throws Exception {
        var db=new Database(100);assertInstanceOf(org.ssssssss.script.runtime.ExitValue.class,run(db,Map.of("paged",true),false));assertEquals(0,db.reads);
    }
    private Object run(Database db,Map<String,Object> payload,boolean allowed) throws Exception {
        String source=CanonicalMagicSources.byId("cf6a9cc605f155d0a54adfbb783abec6").replaceAll("(?m)^import [^\\r\\n]+;\\r?\\n","");
        String guard=allowed?"var guard=(permission,app,domain)=>({});\n":"var guard=(permission,app,domain)=>{exit 403,'没有公众管理权限';};\n";
        return MagicScript.create(guard+source,null).execute(new MagicScriptContext(Map.of("db",db,"operation","list","payload",payload,"tableSort",new TableSortMagicModule())));
    }
}
