package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.script.*;

/** Real database pages include other-tenant and other-application decoys. */
class IdaasLocalInventoryPagingTest {
    @Test void twentyAndHundredRowsUseFiveReadsAndCannotExpandTheVerifiedTenantBinding() throws Exception {
        var fixture=new Fixture();
        var small=fixture.read("users",20,"name");assertEquals(5,fixture.counter.count);
        fixture.counter.count=0;var large=fixture.read("users",100,"name");assertEquals(5,fixture.counter.count);
        var rows=(List<Map<String,Object>>)large.get("list");
        assertEquals(100,rows.size());assertEquals(120,((Number)large.get("total")).intValue());
        assertEquals(((List<?>)small.get("list")),rows.subList(0,20));
        assertTrue(rows.stream().allMatch(r->String.valueOf(r.get("id")).startsWith("person-")));
        assertTrue(rows.stream().allMatch(r->((List<?>)r.get("orgNames")).equals(List.of("Visible organization"))));
        assertTrue(rows.stream().allMatch(r->((List<?>)r.get("roleNames")).equals(List.of("Visible role"))));
        fixture.counter.count=0;fixture.read("roles",100,"userCount");assertEquals(3,fixture.counter.count);
    }
    @Test void applicationPermissionIsCheckedBeforeAnyDatabaseRead() throws Exception {
        var fixture=new Fixture();fixture.allowed=false;
        assertThrows(Exception.class,()->fixture.read("users",100,"name"));assertEquals(0,fixture.counter.count);
    }
    public static class Runtime {
        public void inspectTenantBinding(String id){assertEquals("tenant-a",id);}
        public Object inTenant(String id,org.ssssssss.script.runtime.function.MagicScriptLambdaFunction callback){assertEquals("tenant-a",id);return callback.apply(null,new Object[0]);}
    }
    private static class Counter implements SQLInterceptor {
        int count;public void preHandle(BoundSql sql,RequestEntity request){count++;}
    }
    private static class Fixture {
        final Counter counter=new Counter();final SQLModule sql;boolean allowed=true;
        Fixture() {
            var source=new DriverManagerDataSource("jdbc:h2:mem:local_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            var sources=new MagicDynamicDataSource();sources.setDefault(source);sql=new SQLModule(sources);sql.setDataSourceNode(sources.getDataSource());
            var columns=new ColumnMapperAdapter();columns.add(new DefaultColumnMapperProvider());columns.add(new CamelColumnMapperProvider());columns.setDefault("default");
            sql.setColumnMapperProvider(columns);sql.setSqlInterceptors(List.of(counter));sql.setNamedTableInterceptors(List.of());sql.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());sql.setRowMapColumnMapper(v->v);
            var db=new JdbcTemplate(source);
            db.execute("CREATE TABLE iam_application_t(tid VARCHAR(64),kind VARCHAR(30),is_del INT)");
            db.execute("CREATE TABLE iam_application_runtime_t(app_id VARCHAR(64),runtime_tenant_id VARCHAR(64),local_permission_app_id VARCHAR(64),binding_status VARCHAR(30))");
            db.update("INSERT INTO iam_application_t VALUES('app','BUSINESS',0)");db.update("INSERT INTO iam_application_runtime_t VALUES('app','tenant-a','local-a','VERIFIED')");
            db.execute("CREATE TABLE rm_user_t(id VARCHAR(64),tenant_id VARCHAR(64),real_name VARCHAR(100),user_name VARCHAR(100),status INT,deleted INT)");
            db.execute("CREATE TABLE rm_org_t(id VARCHAR(64),tenant_id VARCHAR(64),name VARCHAR(100),dept_code VARCHAR(100),parent_id VARCHAR(64),deleted INT,status INT)");
            db.execute("CREATE TABLE rm_role_t(id VARCHAR(64),tenant_id VARCHAR(64),app_id VARCHAR(64),name VARCHAR(100),code_num VARCHAR(100),status INT,deleted INT)");
            db.execute("CREATE TABLE rm_menu_t(id VARCHAR(64),tenant_id VARCHAR(64),app_id VARCHAR(64),name VARCHAR(100),code VARCHAR(100),url VARCHAR(100),status INT,deleted INT)");
            db.execute("CREATE TABLE rm_user_role_rela_t(user_id VARCHAR(64),role_id VARCHAR(64),tenant_id VARCHAR(64),auth_from INT,end_time TIMESTAMP)");
            db.execute("CREATE TABLE rm_user_org_rela_t(id VARCHAR(64),user_id VARCHAR(64),org_id VARCHAR(64),tenant_id VARCHAR(64),job_type INT)");
            db.execute("CREATE TABLE rm_role_menu_rela_t(role_id VARCHAR(64),app_resource_id VARCHAR(64),tenant_id VARCHAR(64))");
            db.update("INSERT INTO rm_org_t VALUES('org','tenant-a','Visible organization','ORG','ROOT',0,0)");
            db.update("INSERT INTO rm_role_t VALUES('role','tenant-a','local-a','Visible role','ROLE',0,0)");
            db.update("INSERT INTO rm_role_t VALUES('wrong-app','tenant-a','local-b','Wrong application','OTHER',0,0)");
            db.update("INSERT INTO rm_role_t VALUES('wrong-tenant','tenant-b','local-a','Wrong tenant','OTHER',0,0)");
            for(int i=0;i<120;i++) {
                String id="person-"+String.format("%03d",i);db.update("INSERT INTO rm_user_t VALUES(?,'tenant-a',?,?,0,0)",id,"Name "+String.format("%03d",119-i),id);
                db.update("INSERT INTO rm_user_t VALUES(?,'tenant-b','Outside',?,0,0)","outsider-"+i,id);
                db.update("INSERT INTO rm_user_role_rela_t VALUES(?,'role','tenant-a',0,NULL)",id);db.update("INSERT INTO rm_user_role_rela_t VALUES(?,'wrong-app','tenant-a',0,NULL)",id);
                db.update("INSERT INTO rm_user_org_rela_t VALUES(?,?,'org','tenant-a',0)",id,id);
            }
        }
        Map<String,Object> read(String kind,int size,String field) throws Exception {
            String source=CanonicalMagicSources.byId("ddfd5d8289e95bbc85e62d8708f45b6d").replaceAll("(?m)^import [^\\r\\n]+;\\r?\\n","");
            String guard="var guard=(permission,appId,domain)=>{if(!allowed){throw new java.lang.IllegalArgumentException('Denied');}};\n";
            Object value=MagicScript.create(guard+source,null).execute(new MagicScriptContext(Map.of("db",sql,"identityRuntime",new Runtime(),"tableSort",new TableSortMagicModule(),"operation","page","allowed",allowed,"payload",Map.of("appId","app","domain","workforce","tenantId","tenant-b","kind",kind,"page",1,"size",size,"sortField",field,"sortDir","asc"))));
            return (Map<String,Object>)assertInstanceOf(Map.class,value);
        }
    }
}
