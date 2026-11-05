package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import cn.hutool.core.util.StrUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.*;
import org.ssssssss.magicapi.modules.db.dialect.*;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.*;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

/** Runs the canonical maintenance scripts against the installed Magic DB API and real SQL. */
class OrganizationUserMaintenanceMagicTest {
    static final String SAVE="885b78dbd4474a4cb47566030e050c76", DELETE="d2ca261a63de4aa38188a0ed93c93acb";
    static final String SORT="288235e0cc4047b09933797f75ecfe12", DETAIL="1f91899a67444ecb8cbdaae5485ef17f";

    public static class Tenant { public String id(){return "tenant-a";} }
    public static class Identity { public boolean hasTable(String name){return false;} }
    public static class Sorter {
        public String field(Map<?,?> p,Map<?,?> allowed,String fallback){return String.valueOf(allowed.get(allowed.containsKey(p.get("sortField"))?p.get("sortField"):fallback));}
        public String direction(Map<?,?> p){return "asc".equals(p.get("sortDir"))?"asc":"desc";}
    }
    public static class Accounts {
        final Fixture fixture;final List<String> revoked=new ArrayList<>();
        Accounts(Fixture fixture){this.fixture=fixture;}
        public boolean isLocal(String tenant){return "tenant-a".equals(tenant);}
        public Map<String,Object> session(){return Map.of("appId","app-a");}
        public Map<String,Object> find(String tenant,String id){fixture.reads++;var rows=fixture.jdbc.queryForList("select id from rm_user_t where tenant_id=? and id=? and deleted=0",tenant,id);return rows.isEmpty()?null:rows.getFirst();}
        public void revoke(String id){revoked.add(id);}
    }
    public static class Fixture {
        final JdbcTemplate jdbc; final SQLModule db; final Accounts accounts=new Accounts(this);
        int reads; final List<String> queries=new ArrayList<>();
        Fixture() throws Exception {
            var ds=new DriverManagerDataSource("jdbc:h2:mem:maintenance_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc=new JdbcTemplate(ds);
            String ddl=Files.readString(Path.of("../db/baseline_ga/init.sql"));
            for(String table:List.of("rm_org_t","rm_user_t","rm_user_org_rela_t","rm_role_t","rm_user_role_rela_t")) {
                String section=ddl.split("CREATE TABLE `"+table+"` \\(",2)[1].split("\\) ENGINE=",2)[0];
                var columns=new ArrayList<String>();
                for(String line:section.split("\\r?\\n")) {
                    var m=java.util.regex.Pattern.compile("^\\s*`([^`]+)`\\s+(\\w+)(?:\\(([^)]+)\\))?.*").matcher(line);
                    if(m.matches()) {
                        String type=m.group(2).toLowerCase();
                        String mapped=List.of("tinyint","int","bigint","smallint").contains(type)?"bigint":List.of("datetime","timestamp").contains(type)?"timestamp":type.equals("date")?"date":"varchar(4000)";
                        columns.add("`"+m.group(1)+"` "+mapped);
                    }
                }
                jdbc.execute("create table `"+table+"`("+String.join(",",columns)+")");
            }
            var sources=new MagicDynamicDataSource();sources.setDefault(ds);db=new SQLModule(sources);db.setDataSourceNode(sources.getDataSource());
            db.setResultProvider(new DefaultResultProvider("maintenance-test"));
            var dialects=new DialectAdapter();dialects.add(new MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});db.setDialectAdapter(dialects);
            var mapper=new ColumnMapperAdapter();mapper.add(new DefaultColumnMapperProvider());mapper.add(new CamelColumnMapperProvider());mapper.setDefault("camel");
            db.setColumnMapperProvider(mapper);db.setColumnMapRowMapper(mapper.getDefaultColumnMapRowMapper());db.setRowMapColumnMapper(StrUtil::toUnderlineCase);db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(new SQLInterceptor(){@Override public void preHandle(BoundSql sql,RequestEntity request){
                if(sql.getSql().stripLeading().toLowerCase().startsWith("select")){reads++;queries.add(sql.getSql());}
            }}));
            org("root","ROOT","根机构","根机构","0001","tenant-a");
            org("parent","root","上级","根机构/上级","00010001","tenant-a");
            org("other","root","另一个上级","根机构/另一个上级","00010002","tenant-a");
            org("foreign","root","外租户","外租户","0002","tenant-b");
        }
        void org(String id,String parent,String name,String path,String serial,String tenant){jdbc.update("insert into rm_org_t(id,parent_id,name,org_path,serial_number,sort_num,tenant_id,deleted,status,nature) values(?,?,?,?,?,1,?,0,0,'2')",id,parent,name,path,serial,tenant);}
        public Object users(String operation,Map<String,Object> payload) throws Exception {return run("ga_local_users_01",payload,Map.of("operation",operation,"payload",payload));}
        Object run(String id,Map<String,Object> body) throws Exception {return run(id,body,Map.of());}
        Object run(String id,Map<String,Object> body,Map<String,Object> extra) throws Exception {
            String source=CanonicalMagicSources.byId(id).replaceAll("(?m)^import[^\\r\\n]*[\\r\\n]+","").replace("StpUtil.getLoginId()","'operator'");
            var input=new HashMap<String,Object>();input.put("body",body);input.put("db",db);input.put("actor","operator");input.put("tenantRuntime",new Tenant());input.put("tenantAccounts",accounts);input.put("identityRuntime",new Identity());input.put("tableSort",new Sorter());input.put("users",this);input.putAll(extra);
            String prelude="import cn.hutool.core.util.IdUtil;\nimport cn.hutool.crypto.SmUtil;\nimport cn.hutool.json.JSONUtil;\nimport com.linewell.dataelement.utils.PwdRuleUtil;\nvar auditOperation=(a,b,c,d,e)=>true;\nvar localUsers=(operation,payload)=>users.users(operation,payload);\n";
            return MagicScript.create(prelude+source,null).execute(new MagicScriptContext(input));
        }
        void user(String id,String tenant,String org){
            jdbc.update("insert into rm_user_t(id,tenant_id,real_name,user_name,phone,password,status,deleted,gender,app_file_id,office_address,date_birth) values(?,?,?,?,?,'preserved-hash',0,0,'1','avatar','办公地址',date '1990-01-02')",id,tenant,"验证用户",id,"13800000000");
            jdbc.update("insert into rm_user_org_rela_t(id,tenant_id,user_id,org_id,job_type,sort_num) values(?,?,?,?,'1',0)","relation-"+id,tenant,id,org);
        }
    }
    static Map<?,?> result(Object value){return assertInstanceOf(Map.class,value);}
    static void exit(int expected,Object value){assertEquals(expected,((Number)assertInstanceOf(ExitValue.class,value).getValues()[0]).intValue());}

    @Test void orgCreationEditMoveAndStatusPreserveTreeAndUseConstantReads() throws Exception {
        for(int count:List.of(20,100)) {
            var f=new Fixture();
            for(int i=0;i<count;i++)f.org("child-"+i,"parent","子机构"+i,"根机构/上级/子机构"+i,"000100010001"+i,"tenant-a");
            f.reads=0;
            result(f.run(SAVE,Map.of("id","parent","name","新上级","parentId","other")));
            assertEquals(2,f.reads,"one page query and its count, regardless of subtree size");
            assertEquals(count,f.jdbc.queryForObject("select count(*) from rm_org_t where parent_id='parent' and org_path like '根机构/另一个上级/新上级/%'",Integer.class));
            assertEquals("外租户",f.jdbc.queryForObject("select org_path from rm_org_t where id='foreign'",String.class));
            f.reads=0;result(f.run(SAVE,Map.of("id","parent","status",1)));assertEquals(2,f.reads);
            assertEquals(count+1,f.jdbc.queryForObject("select count(*) from rm_org_t where tenant_id='tenant-a' and status=1",Integer.class));
            assertEquals("新上级",f.jdbc.queryForObject("select name from rm_org_t where id='parent'",String.class));
            System.out.println("ORG subtree="+count+": save/status SQL reads=2; foreign tenant unchanged");
        }
        var f=new Fixture();var created=result(f.run(SAVE,Map.of("name","新机构","parentId","parent","tenantId","tenant-b")));
        assertEquals("tenant-a",f.jdbc.queryForObject("select tenant_id from rm_org_t where id=?",String.class,created.get("id")));
        assertEquals("根机构/上级/新机构",f.jdbc.queryForObject("select org_path from rm_org_t where id=?",String.class,created.get("id")));
        exit(400,f.run(SAVE,Map.of("name","新机构","parentId","parent")));
    }
    @Test void orgRejectsInvalidForeignParentsCyclesAndWrongTenantDetails() throws Exception {
        var f=new Fixture();
        exit(404,f.run(SAVE,Map.of("id","foreign","status",1)));
        exit(404,f.run(SAVE,Map.of("id","parent","parentId","foreign")));
        exit(400,f.run(SAVE,Map.of("id","root","parentId","parent")));
        exit(400,f.run(SAVE,Map.of("id","parent","status",9)));
        exit(404,f.run(DETAIL,Map.of("id","foreign")));
        assertEquals("根机构",result(f.run(DETAIL,Map.of("id","root"))).get("name"));
    }
    @Test void sortValidatesEveryIdBeforeWritesWithFixedReads() throws Exception {
        for(int count:List.of(20,100)) {
            var f=new Fixture();var ids=new ArrayList<String>();
            for(int i=0;i<count;i++){String id="s-"+i;ids.addFirst(id);f.org(id,"parent",id,"根机构/上级/"+id,"00010001"+i,"tenant-a");}
            f.reads=0;result(f.run(SORT,Map.of("ids",ids)));assertEquals(1,f.reads);
            assertEquals(1,f.jdbc.queryForObject("select sort_num from rm_org_t where id=?",Integer.class,ids.getFirst()));
            var mixed=new ArrayList<>(ids);mixed.set(count-1,"foreign");exit(404,f.run(SORT,Map.of("ids",mixed)));
            assertEquals(1,f.jdbc.queryForObject("select sort_num from rm_org_t where id='foreign'",Integer.class));
            exit(400,f.run(SORT,Map.of("ids",List.of("parent",ids.getFirst()))));
            exit(400,f.run(SORT,Map.of("ids",List.of(ids.getFirst(),ids.getFirst()))));
        }
    }
    @Test void deleteChecksWholeSubtreeAndForeignIdsBeforeAnyWrite() throws Exception {
        for(int count:List.of(20,100)) {
            var f=new Fixture();for(int i=0;i<count;i++)f.org("d-"+i,"parent","子"+i,"根机构/上级/子"+i,"00010001"+i,"tenant-a");
            f.reads=0;result(f.run(DELETE,Map.of("ids",List.of("parent"))));assertEquals(3,f.reads);
            assertEquals(count+1,f.jdbc.queryForObject("select count(*) from rm_org_t where deleted=1",Integer.class));
            assertEquals(0,f.jdbc.queryForObject("select deleted from rm_org_t where id='foreign'",Integer.class));
        }
        var f=new Fixture();f.user("member","tenant-a","parent");
        exit(409,f.run(DELETE,Map.of("ids",List.of("parent"))));
        exit(404,f.run(DELETE,Map.of("ids",List.of("other","foreign"))));
        assertEquals(0,f.jdbc.queryForObject("select count(*) from rm_org_t where deleted=1",Integer.class));
    }
    @Test void userStatusOnlyPreservesProfilePasswordAvatarAndPrimaryOrganization() throws Exception {
        var f=new Fixture();f.user("member","tenant-a","parent");
        result(f.users("save",Map.of("id","member","status",1)));
        var row=f.jdbc.queryForMap("select * from rm_user_t where id='member'");
        assertEquals("preserved-hash",row.get("password"));assertEquals("avatar",row.get("app_file_id"));
        assertEquals("办公地址",row.get("office_address"));assertEquals("1990-01-02",row.get("date_birth").toString());
        assertEquals("member",row.get("user_name"));assertEquals(List.of("member"),f.accounts.revoked);
        assertEquals("parent",f.jdbc.queryForObject("select org_id from rm_user_org_rela_t where user_id='member'",String.class));
    }
    @Test void userCreationSupportsExistingUiAccountsAndChecksOrganizationAndDuplicates() throws Exception {
        var f=new Fixture();
        for(String account:List.of("0","001234","yh","中文账号","a@b+测试")) {
            var p=new HashMap<String,Object>(Map.of("realName","验证用户","userName",account,"phone","13800000000","orgId","parent","status",0,"password","Validation!Pass9","tenantId","tenant-b"));
            var created=result(f.users("save",p));String id=created.get("id").toString();
            assertEquals("tenant-a",f.jdbc.queryForObject("select tenant_id from rm_user_t where id=?",String.class,id));
            exit(409,f.users("save",p));
            p.put("orgId","foreign");p.put("userName","foreign-org");exit(400,f.users("save",p));
        }
        f.user("foreign-user","tenant-b","foreign");exit(404,f.users("save",Map.of("id","foreign-user","status",1)));
        f.user("operator","tenant-a","parent");exit(409,f.users("save",Map.of("id","operator","status",1)));
        exit(409,f.users("delete",Map.of("id","operator")));
    }
    @Test void userPagingUsesFixedBatchQueriesAndDoesNotReturnPasswordHash() throws Exception {
        for(int count:List.of(20,100)) {
            var f=new Fixture();for(int i=0;i<count;i++)f.user("member-"+i,"tenant-a","parent");f.user("foreign-user","tenant-b","foreign");
            f.reads=0;var page=result(f.run("f75db35437b546f8a8c8e4eda6740e33",Map.of("pageNum",1,"pageSize",count)));
            var list=(List<?>)page.get("list");assertEquals(count,list.size());assertEquals(4,f.reads);
            for(Object item:list){var user=(Map<?,?>)item;assertEquals("******",user.get("password"));assertEquals("parent",user.get("orgId"));}
            var empty=result(f.run("f75db35437b546f8a8c8e4eda6740e33",Map.of("orgId","foreign","pageNum",1,"pageSize",count)));
            assertEquals(0,((List<?>)empty.get("list")).size());
            System.out.println("USER page="+count+": SQL reads=4; tenant-filtered and password hash excluded");
        }
    }
    @Test void userDeletionWithLegacyAvatarDoesNotRequireRetiredPhotoTable() throws Exception {
        var f=new Fixture();f.user("member","tenant-a","parent");
        result(f.users("delete",Map.of("id","member")));
        assertEquals(1,f.jdbc.queryForObject("select deleted from rm_user_t where id='member'",Integer.class));
        assertEquals(0,f.jdbc.queryForObject("select count(*) from rm_user_org_rela_t where user_id='member'",Integer.class));
        assertEquals(List.of("member"),f.accounts.revoked);
    }
    @Test void userRoleMaintenanceValidatesAllRolesAndPreservesOtherApplicationsWithFixedReads() throws Exception {
        for(int count:List.of(20,100)) {
            var f=new Fixture();f.user("member","tenant-a","parent");var ids=new ArrayList<String>();
            for(int i=0;i<count;i++){String id="r-"+i;ids.add(id);f.jdbc.update("insert into rm_role_t(id,tenant_id,app_id,name,deleted,status) values(?,'tenant-a','app-a',?,0,0)",id,id);}
            f.jdbc.update("insert into rm_role_t(id,tenant_id,app_id,name,deleted,status) values('other-app','tenant-a','app-b','其他应用角色',0,0),('foreign-role','tenant-b','app-a','外租户角色',0,0)");
            f.jdbc.update("insert into rm_user_role_rela_t(id,tenant_id,user_id,role_id) values('existing','tenant-a','member','other-app')");
            f.reads=0;result(f.run("ga_local_roles_01",Map.of(),Map.of("payload",Map.of("userId","member","roleIds",ids))));assertEquals(2,f.reads);
            assertEquals(count+1,f.jdbc.queryForObject("select count(*) from rm_user_role_rela_t where user_id='member'",Integer.class));
            var invalid=new ArrayList<>(ids);invalid.set(count-1,"foreign-role");exit(400,f.run("ga_local_roles_01",Map.of(),Map.of("payload",Map.of("userId","member","roleIds",invalid))));
            assertEquals(count+1,f.jdbc.queryForObject("select count(*) from rm_user_role_rela_t where user_id='member'",Integer.class));
        }
    }
}
