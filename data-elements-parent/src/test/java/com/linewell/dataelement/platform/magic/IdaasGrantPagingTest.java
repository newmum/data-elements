package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.*;

class IdaasGrantPagingTest {
    public static class Database {
        int reads;int size;String pageSql;String relationSql;
        Database(int size){this.size=size;}
        public Database normal(){return this;}
        public Map<String,Object> selectOne(String statement){reads++;return Map.of("total",120);}
        public List<Map<String,Object>> select(String statement){
            reads++;
            if(statement.startsWith("select rr.")){relationSql=statement;return List.of(Map.of("grant_id","grant-0","role_id","role-a","name","角色"));}
            pageSql=statement;var rows=new ArrayList<Map<String,Object>>();
            for(int i=0;i<size;i++){
                var row=new HashMap<String,Object>();row.putAll(Map.of("id","grant-"+i,"identity_domain","workforce","app_id","app-a","app_name","应用","subject_name","人员"+i,"principal_type","SUBJECT","subject_id","subject-"+i,"include_children",0,"status","ACTIVE","view_status","enabled"));
                row.put("version",1);row.put("start_utc","2026-10-01");row.put("created_utc","2026-10-01");row.put("updated_utc","2026-10-01");rows.add(row);
            }return rows;
        }
    }
    @Test void fixedThreeReadsAndPairedApplicationScopeForTwentyAndHundred() throws Exception {
        for(int size:List.of(20,100)){
            var db=new Database(size);var result=(Map<?,?>)read(db,size);
            assertEquals(3,db.reads);assertEquals(size,((List<?>)result.get("list")).size());
            assertTrue(db.pageSql.contains("and g.app_id in (#{allowedApps})"));
            assertTrue(db.pageSql.contains("g.identity_domain=#{domain}"));
            assertTrue(db.pageSql.contains("g.valid_until desc,g.tid asc limit #{take} offset #{offset}"));
            assertTrue(db.relationSql.contains("rr.grant_id in (#{ids})"));
            assertTrue(db.relationSql.contains("g.app_id=rr.app_id and g.identity_domain=#{domain}"));
        }
    }
    private Object read(Database db,int size) throws Exception{
        String source=CanonicalMagicSources.byId("9e888c8030275ef3aac1db63d8417f3f").replaceAll("(?m)^import [^\\r\\n]+;\\r?\\n","");
        String helpers="var listContext=(permission,app,domain)=>({pairs:[{codes:['grants:read'],scopes:[{identity_domain:'workforce',scope_kind:'APPLICATION',app_id:'app-a'},{identity_domain:'public',scope_kind:'ALL'}]},{codes:['resources:read'],scopes:[{identity_domain:'workforce',scope_kind:'ALL'}]}]});\n";
        return MagicScript.create(helpers+source,null).execute(new MagicScriptContext(Map.of("db",db,"operation","list","payload",Map.of("paged",true,"size",size,"sortField","expiresAt","sortDir","desc"),"tableSort",new TableSortMagicModule())));
    }
}
