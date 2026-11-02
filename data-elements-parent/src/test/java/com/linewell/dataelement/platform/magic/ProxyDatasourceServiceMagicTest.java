package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

class ProxyDatasourceServiceMagicTest {
    public static class ScopeRuntime {
        String role="ALL",tenant="tenant-a";
        public String id(){if(tenant==null)throw new IllegalStateException("租户缺失");return tenant;}
        public Map<String,Object> dataSource(){return Map.of("scope",role,"userId","user-a","organizationIds",List.of("org-a"));}
        public boolean canReadDataSource(Map<String,Object> row){
            return Objects.equals(row.getOrDefault("tenantId",row.get("tenant_id")),tenant)
                && (role.equals("ALL") || role.equals("ORG") && Objects.equals(row.getOrDefault("orgId",row.get("org_id")),"org-a")
                    || role.equals("OWNER") && Objects.equals(row.getOrDefault("createdBy",row.get("created_by")),"user-a"));
        }
        public boolean canReadDataSourceFor(String menu,Map<String,Object> row){return canReadDataSource(row);}
    }
    public static class Links {
        public List<?> attachApiFileCounts(List<?> rows){return rows;}
        public int countByApiId(String apiId){return 0;}
    }
    public static class Sort {
        public String field(Map<?,?> body,Map<String,String> fields,String fallback){return fields.getOrDefault(body.get("sortField"),fields.get(fallback));}
        public String direction(Map<?,?> body){return "asc".equals(body.get("sortDir"))?"ASC":"DESC";}
    }
    static class Fixture {
        final DatasourceCompletionMagicTest.Fixture data=new DatasourceCompletionMagicTest.Fixture(0);
        final ScopeRuntime scope=new ScopeRuntime();
        final TaskCategoryWarehouseScopeMagicTest.Counter counter=new TaskCategoryWarehouseScopeMagicTest.Counter();
        Fixture() throws Exception {
            for(String column:List.of("org_id varchar(64)","created_by varchar(64)","pool_cfg varchar(6000)"))data.jdbc.execute("alter table db_datasource_t add "+column);
            data.jdbc.update("insert into db_datasource_t(tid,tenant_id,is_del,org_id,created_by,pool_cfg) values('source-a','tenant-a',0,'org-a','user-a','{\"accessMode\":\"proxy\"}'),('source-b','tenant-a',0,'org-a','user-a','{\"accessMode\":\"proxy\"}'),('foreign','tenant-b',0,'org-a','user-a','{\"accessMode\":\"proxy\"}'),('department','tenant-a',0,'org-b','user-b','{\"accessMode\":\"proxy\"}'),('deleted','tenant-a',1,'org-a','user-a','{\"accessMode\":\"proxy\"}'),('direct','tenant-a',0,'org-a','user-a','{\"accessMode\":\"explore\"}')");
            String ddl=Files.readString(Path.of("../db/baseline_ga/init.sql"));
            for(String name:List.of("api_info_t","api_param_t","da_prop_t","sym_dict_t")) {
                String table=ddl.substring(ddl.indexOf("CREATE TABLE `"+name+"`"));table=table.substring(0,table.indexOf("\n)"));
                var matcher=Pattern.compile("(?m)^  `([^`]+)` ([^ ,]+)").matcher(table);List<String> columns=new ArrayList<>();
                while(matcher.find()){
                    String field=matcher.group(1),type=matcher.group(2).toLowerCase();
                    columns.add(field+" "+(type.contains("int")?"bigint":type.contains("decimal")?"decimal(38,10)":type.contains("time")?"timestamp":"varchar(6000)"));
                }
                data.jdbc.execute("create table "+name+"("+String.join(",",columns)+")");
            }
            data.db.setSqlInterceptors(List.of(counter));
        }
        Object run(String name,Map<String,Object> body)throws Exception {
            String file="../magic/api/06.数据服务/01.服务编排/08.服务管理/"+(name.equals("save")?"05.保存网关服务.ms":name.equals("detail")?"02.服务列表/02.服务详情.ms":"02.服务列表/01.服务列表.ms");
            String source=Files.readString(Path.of(file));source=source.substring(source.indexOf("================================")+32).replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.util\\.)[^\\r\\n]*\\r?\\n","");
            source="var me=()=>({id:'user-a',orgId:'org-a'})\n"+source;
            return MagicScript.create(source,null).execute(new MagicScriptContext(new LinkedHashMap<>(Map.of("db",data.db,"tenantRuntime",scope,"dataScope",scope,"magicResourceLink",new Links(),"tableSort",new Sort(),"body",body))));
        }
        Map<String,Object> createBody(String source){return new LinkedHashMap<>(Map.of("name","代理验证服务","uri","/validation/proxy-service","datasourceId",source,"tenantId","tenant-b","orgId","org-b"));}
    }
    @Test void mixedRequestAndResponseParametersRoundTripWithConstantReadsAndTenantIsolation() throws Exception {
        int expectedReads = -1;
        for (int count : List.of(20, 100)) {
            var f = new Fixture();
            var body = f.createBody("source-a");
            List<Map<String,Object>> params = new ArrayList<>();
            for (int i = 0; i < count; i++) params.add(Map.of("paramName", "p" + i, "paramType", "string",
                "paramScope", i % 2 == 0 ? "request" : "response", "showcaseValue", "v" + i));
            body.put("publishParams", params);
            body.put("extendInfo", new LinkedHashMap<>(Map.of("requestExample", "{\"q\":1}", "responseExample", "{\"ok\":true}",
                "exampleInitialization", Map.of("request", true, "response", true))));
            var saved = (Map<?,?>) f.run("save", body);
            String id = saved.get("id").toString();
            f.data.jdbc.update("insert into api_param_t(tid,api_id,tenant_id,param_name,param_scope,is_del) values('foreign-param',?,'tenant-b','hidden','response',0)", id);
            f.counter.reads = 0;
            var detail = (Map<?,?>) f.run("detail", Map.of("apiId", id));
            var rows = (List<?>) detail.get("publishParams");
            assertThat(rows).hasSize(count);
            assertThat(rows.stream().filter(row -> "response".equals(((Map<?,?>) row).get("paramScope"))).count()).isEqualTo(count / 2);
            assertThat(detail.get("requestExample")).isEqualTo("{\"q\":1}");
            assertThat(detail.get("responseExample")).isEqualTo("{\"ok\":true}");
            assertThat(((Map<?,?>) detail.get("extendInfo")).get("exampleInitialization").toString()).contains("request", "response");
            if (expectedReads < 0) expectedReads = f.counter.reads;
            assertThat(f.counter.reads).isEqualTo(expectedReads);
            System.out.println("service parameters=" + count + ", detail SQL reads=" + f.counter.reads + "; foreign parameter excluded");
            f.scope.tenant = "tenant-b";
            assertThat(f.run("detail", Map.of("apiId", id))).isInstanceOf(ExitValue.class);
        }
    }
    @Test void requiredDictionaryValuesPersistAndReadBackAsBinaryFlags() throws Exception {
        var f = new Fixture();
        var body = f.createBody("source-a");
        List<Object> values = List.of(true, false, "true", "false", "1", "0", 1, 0, "是", "否", "必填", "可选");
        List<Map<String,Object>> params = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) params.add(Map.of("paramName", "p" + i, "paramType", "string", "required", values.get(i)));
        body.put("publishParams", params);
        var saved = (Map<?,?>) f.run("save", body);
        var detail = (Map<?,?>) f.run("detail", Map.of("apiId", saved.get("id")));
        var rows = (List<?>) detail.get("publishParams");
        assertThat(rows).hasSize(values.size());
        for (int i = 0; i < rows.size(); i++) assertThat(((Map<?,?>) rows.get(i)).get("required")).isEqualTo(i % 2 == 0 ? "1" : "0");
    }
    @Test void aNewSourceHasNoServicesAndThreeRegistrationsPersistOnlyTheirOwnSource()throws Exception {
        var f=new Fixture();
        assertThat(((Map<?,?>)f.run("list",Map.of("datasourceId","source-a"))).get("list")).isEqualTo(List.of());
        for(int i=0;i<3;i++)assertThat(f.run("save",f.createBody("source-a"))).isInstanceOf(Map.class);
        assertThat(f.data.jdbc.queryForObject("select count(*) from api_info_t where datasource_id='source-a' and tenant_id='tenant-a' and org_id='org-a'",Integer.class)).isEqualTo(3);
        assertThat((List<?>)((Map<?,?>)f.run("list",Map.of("datasourceId","source-a"))).get("list")).hasSize(3);
        assertThat((List<?>)((Map<?,?>)f.run("list",Map.of("datasourceId","source-b"))).get("list")).isEmpty();
        String id=f.data.jdbc.queryForObject("select min(tid) from api_info_t",String.class);var update=f.createBody("source-b");update.put("id",id);
        assertThat(f.run("save",update)).isInstanceOf(ExitValue.class);
        update=f.createBody("source-a");update.put("id",id);update.remove("datasourceId");assertThat(f.run("save",update)).isInstanceOf(Map.class);
        assertThat(f.data.jdbc.queryForObject("select datasource_id from api_info_t where tid=?",String.class,id)).isEqualTo("source-a");
    }
    @ParameterizedTest @ValueSource(ints={20,100})
    void sourceScopedPagingUsesFourReadsRegardlessOfRowCount(int count)throws Exception {
        var f=new Fixture();
        for(int i=0;i<count;i++)f.data.jdbc.update("insert into api_info_t(tid,tenant_id,is_del,datasource_id,org_id,created_by,service_name) values(?,'tenant-a',0,'source-a','org-a','user-a',?)","s"+i,"服务"+i);
        f.data.jdbc.update("insert into api_info_t(tid,tenant_id,is_del,datasource_id,org_id,created_by) values('wrong-source','tenant-a',0,'source-b','org-a','user-a'),('unlinked','tenant-a',0,null,'org-a','user-a'),('foreign-service','tenant-b',0,'source-a','org-a','user-a')");
        var result=(Map<?,?>)f.run("list",Map.of("datasourceId","source-a","pageSize",count));
        assertThat((List<?>)result.get("list")).hasSize(count);assertThat(((Number)result.get("total")).intValue()).isEqualTo(count);
        assertThat(f.counter.reads).isEqualTo(4);
        assertThat(f.counter.sql).anyMatch(sql->sql.contains("a.datasource_id=?")&&sql.contains("a.tenant_id=?"));
        System.out.println("proxy service page="+count+", SQL reads=4 (source authorization, page, groups, stats); no row reads");
    }
    @Test void parentAndServiceScopeAreBothCheckedAndForgedTenantOrOrgCannotGrantAccess()throws Exception {
        var f=new Fixture();
        for(String role:List.of("ALL","ORG","OWNER")){
            f.scope.role=role;
            for(String id:List.of("foreign","deleted","missing")) {
                assertThat(f.run("list",Map.of("datasourceId",id,"tenantId","tenant-b","departmentOnly",false))).isInstanceOf(ExitValue.class);
                assertThat(f.run("save",f.createBody(id))).isInstanceOf(ExitValue.class);
            }
            if(!role.equals("ALL")){
                assertThat(f.run("list",Map.of("datasourceId","department"))).isInstanceOf(ExitValue.class);
                assertThat(f.run("save",f.createBody("department"))).isInstanceOf(ExitValue.class);
            }
        }
        assertThat(f.run("save",f.createBody("direct"))).isInstanceOf(ExitValue.class);
        assertThat(f.run("list",Map.of("datasourceId",""))).isInstanceOf(ExitValue.class);
        assertThat(f.run("list",Map.of("datasourceId","x".repeat(33)))).isInstanceOf(ExitValue.class);
        assertThat(f.run("save",f.createBody("x".repeat(33)))).isInstanceOf(ExitValue.class);
        assertThat(f.data.jdbc.queryForObject("select count(*) from api_info_t",Integer.class)).isZero();
        f.scope.tenant=null;assertThatThrownBy(()->f.run("list",Map.of("datasourceId","source-a"))).hasMessageContaining("租户");
    }
    @ParameterizedTest @ValueSource(strings={"ORG","OWNER"})
    void parentPermissionDoesNotBroadenServiceRowScope(String role)throws Exception {
        var f=new Fixture();f.scope.role=role;
        f.data.jdbc.update("insert into api_info_t(tid,tenant_id,is_del,datasource_id,org_id,created_by) values('allowed','tenant-a',0,'source-a','org-a','user-a'),('restricted','tenant-a',0,'source-a','org-b','user-b')");
        var result=(Map<?,?>)f.run("list",Map.of("datasourceId","source-a"));
        assertThat((List<?>)result.get("list")).hasSize(1);
        assertThat(((Number)result.get("total")).intValue()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(ints={20,100})
    void departmentPageUsesThreeReadsAndPreservesTenantAndRoleScope(int count) throws Exception {
        for (String role : List.of("ALL", "ORG", "OWNER")) {
            var f = new Fixture(); f.scope.role = role;
            for (int i=0; i<count; i++) f.data.jdbc.update("insert into api_info_t(tid,tenant_id,is_del,org_id,created_by,service_name) values(?,'tenant-a',0,'org-a','user-a',?)", "service-"+i, "服务"+i);
            f.data.jdbc.update("insert into api_info_t(tid,tenant_id,is_del,org_id,created_by) values('foreign','tenant-b',0,'org-a','user-a'),('outside-department','tenant-a',0,'org-b','user-a'),('same-org-other-owner','tenant-a',0,'org-a','user-b')");
            var result = (Map<?,?>) f.run("list", Map.of("departmentOnly", true, "pageSize", count));
            assertThat(((Number) result.get("total")).intValue()).isEqualTo(count + (role.equals("OWNER") ? 0 : 1));
            assertThat((List<?>) result.get("list")).hasSize(count);
            assertThat(f.counter.reads).isEqualTo(3);
            assertThat(f.counter.sql).anyMatch(sql -> sql.contains("a.tenant_id=?") && sql.contains("a.org_id=?"));
        }
    }
}
