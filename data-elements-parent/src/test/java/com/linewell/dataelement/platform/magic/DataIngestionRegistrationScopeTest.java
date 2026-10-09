package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.dialect.*;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.script.*;

class DataIngestionRegistrationScopeTest {
    @ParameterizedTest @ValueSource(ints={20,100})
    void treeAndPageUseSourcesOneOrTwoAndOnlyTablesTwoWithConstantReads(int count) throws Exception {
        var f = new DatasourceCompletionMagicTest.Fixture(count);
        f.jdbc.execute("alter table db_datasource_t add db_name varchar(100)");
        f.jdbc.execute("alter table db_datasource_t add org_id varchar(64)");
        f.jdbc.execute("alter table db_datasource_t add app_id varchar(64)");
        f.jdbc.execute("alter table db_table_t add business_type varchar(64)");
        f.jdbc.execute("alter table db_table_t add table_name varchar(64)");
        f.jdbc.execute("alter table db_table_t add source_table_id varchar(64)");
        f.jdbc.execute("alter table db_table_t add updated_time timestamp");
        f.jdbc.execute("create table data_access_agg_task_t(source_table_id varchar(64),tenant_id varchar(32),is_del int,updated_time timestamp)");
        f.jdbc.execute("create table rm_org_t(tenant_id varchar(32),id varchar(64),name varchar(100),deleted int)");
        f.jdbc.update("insert into rm_org_t values('ga','org','来源部门',0),('other','org','其他租户同名ID部门',0),('ga','completed-org','完成库部门',0),('ga','denied-org','无权部门',0)");
        f.jdbc.update("update db_datasource_t set asset_status=1,db_name='partial',org_id='org',app_id='app'");
        f.jdbc.update("update db_table_t set business_type='业务表',table_name=tid,updated_time=current_timestamp");
        f.jdbc.update("insert into db_datasource_t(tid,tenant_id,asset_status,is_del,db_name,org_id,app_id) values('completed','ga',2,0,'done','org','app'),('draft','ga',0,0,'draft','org','app'),('foreign','other',1,0,'foreign','org','app'),('denied','ga',1,0,'denied','org','app')");
        f.jdbc.update("update db_datasource_t set org_id='completed-org' where tid='completed'");
        f.jdbc.update("update db_datasource_t set org_id='denied-org' where tid='denied'");
        f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,business_type,table_name,updated_time) values('done','ga','completed',2,0,'字典表','done',current_timestamp),('draft','ga','draft',2,0,'业务表','draft',current_timestamp),('foreign','other','foreign',2,0,'业务表','foreign',current_timestamp),('denied','ga','denied',2,0,'业务表','denied',current_timestamp)");
        var counter=new Counter();f.db.setSqlInterceptors(List.of(counter));
        var dialects=new DialectAdapter();dialects.add(new MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});
        f.db.setDialectAdapter(dialects);f.db.setResultProvider(new DefaultResultProvider("status-test"));
        var scope=new VisibleScope();
        Map<?,?> tree=run(f,"ods_data_agg_tree_statistics_01",Map.of(),scope);
        assertThat(((Number)tree.get("total")).intValue()).isEqualTo(count);
        assertThat(counter.reads).isEqualTo(2);
        counter.reads=0;
        Map<?,?> page=run(f,"76fd211790f54164947f276824d19c1d",Map.of("viewLevel","table","pageNum",1,"pageSize",count),scope);
        assertThat(((Number)page.get("total")).intValue()).isEqualTo(count);
        assertThat((List<?>)page.get("list")).hasSize(count);
        assertThat(counter.reads).isEqualTo(7);
        assertThat(scope.calls).isEqualTo(2);
        for(Object raw:(List<?>)page.get("list")) {
            Map<?,?> row=(Map<?,?>)raw;
            assertThat(String.valueOf(row.get("datasourceId"))).isIn("db","completed");
            boolean completed = "completed".equals(row.get("datasourceId"));
            assertThat(row.get("orgId")).isEqualTo(completed ? "completed-org" : "org");
            assertThat(row.get("orgName")).isEqualTo(completed ? "完成库部门" : "来源部门");
        }
        counter.reads=0;
        Map<?,?> forbidden=run(f,"76fd211790f54164947f276824d19c1d",Map.of("viewLevel","table","pageNum",1,"pageSize",count,"datasourceIds",List.of("foreign","denied")),scope);
        assertThat((List<?>)forbidden.get("list")).isEmpty();
        assertThat(counter.reads).isEqualTo(1);
        System.out.println("department page verification: items="+count+", SQL=7; foreign/denied datasource items=0, SQL=1");
    }
    private Map<?,?> run(DatasourceCompletionMagicTest.Fixture f,String id,Map<String,Object> body,VisibleScope scope) throws Exception {
        String script=CanonicalMagicSources.byId(id).replaceAll("(?m)^import [^\\r\\n]*\\r?\\n","");
        Map<String,Object> bindings=new LinkedHashMap<>();bindings.put("body",body);bindings.put("db",f.db);
        bindings.put("tenantRuntime",scope);bindings.put("dataScope",scope);
        return (Map<?,?>)MagicScript.create(script,null).execute(new MagicScriptContext(bindings));
    }
    public static class VisibleScope {
        int calls;
        public String id(){return "ga";}
        public List<Map<String,Object>> visibleDataSourcesFor(String resource,List<Map<String,Object>> rows){
            calls++;return rows.stream().filter(row->!"denied".equals(row.get("tid"))).toList();
        }
    }
    static class Counter implements SQLInterceptor {
        int reads;
        @Override public void preHandle(BoundSql sql,RequestEntity request){if(sql.getSql().stripLeading().toLowerCase().startsWith("select")) reads++;}
    }
}
