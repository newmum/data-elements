package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

class ServiceOrchestrationOpenMagicTest {
    public static class ScopeRuntime {
        String scope="ALL",tenant="tenant-a";
        public String id(){if(tenant==null)throw new IllegalStateException("租户缺失");return tenant;}
        public Map<String,Object> dataSource(){return Map.of("scope",scope,"userId","user-a","organizationIds",List.of("org-a"));}
    }
    static class Fixture {
        final DatasourceCompletionMagicTest.Fixture data=new DatasourceCompletionMagicTest.Fixture(0);
        final ScopeRuntime scope=new ScopeRuntime();
        final TaskCategoryWarehouseScopeMagicTest.Counter counter=new TaskCategoryWarehouseScopeMagicTest.Counter();
        Fixture(int count) {
            data.jdbc.execute("create table api_info_t(tid varchar(64) primary key,tenant_id varchar(64),is_del int,org_id varchar(64),created_by varchar(64),flow_id varchar(64),service_name varchar(255),method varchar(10))");
            data.jdbc.execute("create table fs_flow(id varchar(64) primary key,tenant_id varchar(64),name varchar(255),flow_group varchar(255),status varchar(32),current_version int,dsl clob,updated_by varchar(64),updated_at timestamp,lock_version int,deleted int)");
            for(int i=0;i<count;i++)data.jdbc.update("insert into api_info_t values(?,'tenant-a',0,'org-a','user-a','','服务编排','GET')","api-"+i);
            data.jdbc.update("insert into api_info_t values('foreign','tenant-b',0,'org-a','user-a','','跨租户服务','GET'),('department','tenant-a',0,'org-b','user-b','','其他部门','GET'),('deleted','tenant-a',1,'org-a','user-a','','删除服务','GET')");
            data.db.setSqlInterceptors(List.of(counter));
        }
        Object open(String id,boolean dryRun)throws Exception {
            String source=Files.readString(Path.of("../magic/api/06.数据服务/01.服务编排/08.服务管理/06.打开服务编排.ms"));
            source=source.substring(source.indexOf("================================")+32).replaceAll("(?m)^import (?!cn\\.hutool\\.)[^\\r\\n]*\\r?\\n","");
            return MagicScript.create(source,null).execute(new MagicScriptContext(new LinkedHashMap<>(Map.of("db",data.db,"tenantRuntime",scope,"dataScope",scope,"body",Map.of("apiId",id,"dryRun",dryRun,"tenantId","tenant-b","orgId","org-b","flowId","forged-flow")))));
        }
        int flows(){return data.jdbc.queryForObject("select count(*) from fs_flow",Integer.class);}
    }
    @ParameterizedTest @ValueSource(ints={20,100})
    void listingDoesNotCreateAndAnExplicitOpenUsesTwoReadsAndCreatesOnlyOneDraft(int count)throws Exception {
        var f=new Fixture(count);assertThat(f.flows()).isZero();assertThat(f.counter.reads).isZero();
        var preview=(Map<?,?>)f.open("api-0",true);assertThat(preview.get("requiresInitialization")).isEqualTo(true);assertThat(f.flows()).isZero();
        int before=f.counter.reads;var opened=(Map<?,?>)f.open("api-0",false);
        assertThat(f.counter.reads-before).isEqualTo(2);assertThat(opened.get("flowId")).isEqualTo("api-0");assertThat(opened.get("created")).isEqualTo(true);assertThat(f.flows()).isEqualTo(1);
        String dsl=f.data.jdbc.queryForObject("select dsl from fs_flow where id='api-0'",String.class);
        assertThat(new ObjectMapper().readTree(dsl).get("nodes").size()).isEqualTo(2);
        assertThat(new ObjectMapper().readTree(dsl).get("name").asText()).isEqualTo("服务编排");
        assertThat(f.data.jdbc.queryForObject("select flow_id from api_info_t where tid='api-0'",String.class)).isEqualTo("api-0");
        assertThat(((Map<?,?>)f.open("api-0",false)).get("created")).isEqualTo(false);assertThat(f.flows()).isEqualTo(1);
        assertThat(f.data.jdbc.queryForObject("select dsl from fs_flow where id='api-0'",String.class)).isEqualTo(dsl);
        assertThat(f.counter.sql).anyMatch(sql->sql.contains("for update"));
        System.out.println("service rows="+count+", list extra reads=0; explicit open reads=2; one draft, idempotent reopen");
    }
    @Test void crossTenantDeletedAndRoleScopeCannotBeBypassedByBodyFields()throws Exception {
        var f=new Fixture(1);
        for(String role:List.of("ALL","ORG","OWNER")){
            f.scope.scope=role;
            assertThat(f.open("foreign",false)).isInstanceOf(ExitValue.class);
            assertThat(f.open("deleted",false)).isInstanceOf(ExitValue.class);
            if(!role.equals("ALL"))assertThat(f.open("department",false)).isInstanceOf(ExitValue.class);
        }
        assertThat(f.flows()).isZero();f.scope.tenant=null;
        assertThatThrownBy(()->f.open("api-0",false)).hasMessageContaining("租户");
    }
    @Test void aBrokenExistingAssociationIsReportedInsteadOfSilentlyReplacingIt()throws Exception {
        var f=new Fixture(1);f.data.jdbc.update("update api_info_t set flow_id='missing-flow' where tid='api-0'");
        assertThat(f.open("api-0",false)).isInstanceOf(ExitValue.class);assertThat(f.flows()).isZero();
        assertThat(f.data.jdbc.queryForObject("select flow_id from api_info_t where tid='api-0'",String.class)).isEqualTo("missing-flow");
    }
}
