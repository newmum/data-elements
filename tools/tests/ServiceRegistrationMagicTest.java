import java.nio.file.*;
import java.util.*;
import org.ssssssss.script.*;

/** Canonical service registration contract tests; isolated H2 only, no live assets. */
public class ServiceRegistrationMagicTest {
    static final String ROOT="magic/api/06.数据服务/01.服务编排/08.服务管理/";
    public static class Scope {
        String mode="OWNER";String org="org";
        public String id(){return "ga";}
        public Map<String,Object> currentUser(){return Map.of("id","user","orgId",org,"orgName","测试部门");}
        public Map<String,Object> dataSource(){return Map.of("scope",mode,"organizationIds",List.of(org),"userId","user");}
        public boolean canReadDataSource(Map<String,Object> row){return mode.equals("ALL") || (mode.equals("ORG")?org.equals(row.get("orgId")):"user".equals(row.get("createdBy")));}
    }
    public static class Sort {
        public String field(Map<?,?> request,Map<?,?> fields,String fallback){return Objects.toString(fields.get(fields.containsKey(request.get("sortField"))?request.get("sortField"):fallback));}
        public String direction(Map<?,?> request){return "asc".equals(request.get("sortDir"))?"ASC":"DESC";}
    }
    public static class Link {
        int calls;
        public List<?> attachApiFileCounts(List<?> rows){calls++;return rows;}
        public int countByApiId(String id){calls++;return 0;}
        public void link(String file,String id){throw new AssertionError("registration must not create a script");}
    }
    static class Fixture {
        final PushRegistrySyncMagicTest.Fixture base;final Scope scope=new Scope();final Link link=new Link();
        Fixture(int count)throws Exception {
            base=new PushRegistrySyncMagicTest.Fixture(0);
            String ddl=Files.readString(Path.of("db/baseline_ga/init.sql"));
            for(String table:List.of("api_info_t","api_param_t","da_prop_t","sym_dict_t")){
                String definition=ddl.substring(ddl.indexOf("CREATE TABLE `"+table+"` ("));definition=definition.substring(definition.indexOf('\n')+1,definition.indexOf("\n) ENGINE="));
                List<String> columns=new ArrayList<>();for(String line:definition.split("\n"))if(line.stripLeading().startsWith("`")){
                    line=line.strip().replaceAll(",$","").replaceAll(" CHARACTER SET [A-Za-z0-9_]+","").replaceAll(" COLLATE [A-Za-z0-9_]+","").replaceAll(" COMMENT '.*'$","").replaceAll("(?i)longtext|mediumtext|text","varchar(2000000)");columns.add(line);
                }
                base.jdbc.execute("create table "+table+"("+String.join(",",columns)+")");
            }
            for(int i=0;i<count;i++)base.jdbc.update("insert into api_info_t(tid,tenant_id,org_id,created_by,service_name,service_code,is_del,updated_time,status) values(?,'ga','org','user',?,?,0,current_timestamp,'draft')","s"+i,String.format("服务%03d",i),"code"+i);
            base.jdbc.update("insert into api_info_t(tid,tenant_id,org_id,created_by,service_name,is_del) values('other-dept','ga','elsewhere','user','其他部门',0),('denied','ga','org','other','其他人员',0),('foreign','ct','org','user','其他租户',0)");
            base.jdbc.update("insert into sym_dict_t(tid,tenant_id,dict_name,biz_type,is_del) values('api_service_group_default','ga','默认分组','apiServiceGroup',0)");
        }
        Object call(String path,Map<String,Object> body)throws Exception {
            String file=Files.readString(Path.of(ROOT+path));String script=file.substring(file.indexOf("================================")+32)
                .replaceAll("(?m)^import (tenantRuntime|dataScope|tableSort|magicResourceLink)\\r?\\n","")
                .replaceAll("(?m)^import \"@post:/sym/user/me\" as me\\r?\\n","").replace("me()","tenantRuntime.currentUser()");
            base.reads.set(0);link.calls=0;
            return MagicScript.create(script,null).execute(new MagicScriptContext(Map.of("body",body,"parameter",Map.of(),"db",base.db,"tenantRuntime",scope,"dataScope",scope,"tableSort",new Sort(),"magicResourceLink",link,"me",(java.util.function.Supplier<Map<String,Object>>)()->Map.of("id","user","orgId",scope.org,"orgName","测试部门"))));
        }
    }
    static void check(boolean value,String label){PushRegistrySyncMagicTest.check(value,label);}
    static void rejected(Object result,int code){PushRegistrySyncMagicTest.rejected(result,code);}
    static Map<String,Object> form(int count){
        Map<String,Object> result=new HashMap<>(Map.of("name","测试服务","providerConfig",Map.of("address","https://example.test/query","httpMethod","POST","protocol","HTTP"),"publishConfig",Map.of("publishAddress","/query"),"extendInfo",new HashMap<>(Map.of("orgId","forged-org","dataDomain","demo","requestExample","{\"ok\":true}")),"orgId","forged-org","createdBy","forged-user","tenantId","ct"));
        List<Map<String,Object>> params=new ArrayList<>();for(int i=0;i<count;i++)params.add(Map.of("paramName","p"+i,"paramType","string","paramPosition","query","required",i%2==0));result.put("publishParams",params);return result;
    }
    public static void main(String[] args)throws Exception {
        for(int count:List.of(20,100)){
            var f=new Fixture(count);
            var list=(Map<?,?>)f.call("02.服务列表/01.服务列表.ms",Map.of("departmentOnly",true,"orgId","elsewhere","pageSize",count,"sortField","serviceName","sortDir","asc"));
            check(((List<?>)list.get("list")).size()==count,"page rows");check(((Number)list.get("total")).intValue()==count,"authorized total");check(f.base.reads.get()==3,"list fixed SQL "+f.base.reads);check(f.link.calls==1,"one batch script count lookup");
            var groups=(Map<?,?>)f.call("01.服务分组/02.服务分组列表.ms",Map.of("departmentOnly",true));check(((Number)groups.get("total")).intValue()==count,"same group scope");check(f.base.reads.get()==2,"group fixed SQL");
            rejected(f.call("02.服务列表/02.服务详情.ms",Map.of("apiId","foreign")),404);rejected(f.call("02.服务列表/02.服务详情.ms",Map.of("apiId","denied")),404);
            var values=form(count);var saved=(Map<?,?>)f.call("05.保存网关服务.ms",values);String id=saved.get("id").toString();
            check("ga".equals(f.base.jdbc.queryForObject("select tenant_id from api_info_t where tid=?",String.class,id)),"server tenant");check("org".equals(f.base.jdbc.queryForObject("select org_id from api_info_t where tid=?",String.class,id)),"server department");check("user".equals(f.base.jdbc.queryForObject("select created_by from api_info_t where tid=?",String.class,id)),"server creator");
            values.put("id",id);f.call("05.保存网关服务.ms",values);check(f.base.reads.get()==2,"save reads do not scale with parameters");
            var detail=(Map<?,?>)f.call("02.服务列表/02.服务详情.ms",Map.of("apiId",id));check(((List<?>)detail.get("publishParams")).size()==count,"parameter round trip");check(((Map<?,?>)detail.get("extendInfo")).get("requestExample").equals("{\"ok\":true}"),"example round trip");check(f.base.reads.get()==4,"detail fixed SQL");
            values.put("id","foreign");rejected(f.call("05.保存网关服务.ms",values),404);values.put("id","denied");rejected(f.call("05.保存网关服务.ms",values),403);
            f.scope.mode="ALL";var all=(Map<?,?>)f.call("02.服务列表/01.服务列表.ms",Map.of("departmentOnly",true,"pageSize",100));check(((Number)all.get("total")).intValue()==count+2,"department flag narrows even ALL scope");
            f.scope.org="";rejected(f.call("02.服务列表/01.服务列表.ms",Map.of("departmentOnly",true)),403);rejected(f.call("05.保存网关服务.ms",form(1)),403);
            System.out.println(count+" rows/parameters: list=3 business SQL + 1 batch script lookup; groups=2; update=2; detail=4; tenant/department/role checks passed");
        }
        var filtered=new Fixture(2);
        filtered.base.jdbc.update("update api_info_t set service_create_type='apiBuild' where tid='s1'");
        var registered=(Map<?,?>)filtered.call("02.服务列表/01.服务列表.ms",Map.of("departmentOnly",true,"serviceCreateType","api","pageSize",100));
        check(((Number)registered.get("total")).intValue()==1,"registration workspace excludes built services");
        check(filtered.base.reads.get()==3 && filtered.link.calls==1,"registration type filter retains fixed reads");
        rejected(filtered.call("02.服务列表/01.服务列表.ms",Map.of("serviceCreateType","invalid")),400);
        var invalid=new Fixture(0);var input=form(1);input.put("method","INVALID");rejected(invalid.call("05.保存网关服务.ms",input),400);rejected(invalid.call("05.保存网关服务.ms",form(501)),413);
        System.out.println("Canonical service registration Magic checks passed");
    }
}
