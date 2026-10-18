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
    void treeAndPageUsePositiveSourceAndTableStatusesWithConstantReads(int count) throws Exception {
        var f = new DatasourceCompletionMagicTest.Fixture(count);
        createWarehouseConfiguration(f);
        f.jdbc.execute("alter table db_datasource_t add db_name varchar(100)");
        f.jdbc.execute("alter table db_datasource_t add org_id varchar(64)");
        f.jdbc.execute("alter table db_datasource_t add app_id varchar(64)");
        f.jdbc.execute("alter table db_table_t add business_type varchar(64)");
        f.jdbc.execute("alter table db_table_t add table_name varchar(64)");
        f.jdbc.execute("alter table db_table_t add table_name_cn varchar(64)");
        f.jdbc.execute("alter table db_table_t add table_comment varchar(64)");
        f.jdbc.execute("alter table db_table_t add source_table_id varchar(64)");
        f.jdbc.execute("alter table db_table_t add updated_time timestamp");
        f.jdbc.execute("create table data_access_agg_task_t(source_table_id varchar(64),target_table_id varchar(64),tenant_id varchar(32),is_del int,updated_time timestamp)");
        f.jdbc.execute("create table rm_org_t(tenant_id varchar(32),id varchar(64),name varchar(100),deleted int)");
        f.jdbc.update("insert into rm_org_t values('ga','org','来源部门',0),('other','org','其他租户同名ID部门',0),('ga','completed-org','完成库部门',0),('ga','denied-org','无权部门',0)");
        f.jdbc.update("update db_datasource_t set asset_status=1,db_name='partial',org_id='org',app_id='app'");
        f.jdbc.update("update db_table_t set business_type='业务表',table_name=tid,updated_time=current_timestamp");
        f.jdbc.update("update db_table_t set asset_status=1 where tid='table-1'");
        f.jdbc.update("insert into db_datasource_t(tid,tenant_id,asset_status,is_del,db_name,org_id,app_id) values('completed','ga',2,0,'done','org','app'),('draft','ga',0,0,'draft','org','app'),('foreign','other',1,0,'foreign','org','app'),('denied','ga',1,0,'denied','org','app')");
        f.jdbc.update("update db_datasource_t set org_id='completed-org' where tid='completed'");
        f.jdbc.update("update db_datasource_t set org_id='denied-org' where tid='denied'");
        f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,business_type,table_name,updated_time) values('done','ga','completed',2,0,'字典表','done',current_timestamp),('draft','ga','draft',2,0,'业务表','draft',current_timestamp),('foreign','other','foreign',2,0,'业务表','foreign',current_timestamp),('denied','ga','denied',2,0,'业务表','denied',current_timestamp)");
        f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,business_type,table_name,updated_time) values('null-status','ga','db',null,0,'业务表','null-status',current_timestamp),('negative','ga','db',-1,0,'业务表','negative',current_timestamp),('deleted','ga','db',1,1,'业务表','deleted',current_timestamp),('process','ga','db',1,0,'过程表','process',current_timestamp),('foreign-in-db','other','db',1,0,'业务表','foreign-in-db',current_timestamp)");
        var counter=new Counter();f.db.setSqlInterceptors(List.of(counter));
        var dialects=new DialectAdapter();dialects.add(new MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});
        f.db.setDialectAdapter(dialects);f.db.setResultProvider(new DefaultResultProvider("status-test"));
        var scope=new VisibleScope();
        Map<?,?> tree=run(f,"ods_data_agg_tree_statistics_01",Map.of(),scope);
        assertThat(((Number)tree.get("total")).intValue()).isEqualTo(count);
        assertThat(counter.reads).isEqualTo(3);
        counter.reads=0;
        Map<?,?> page=run(f,"76fd211790f54164947f276824d19c1d",Map.of("viewLevel","table","pageNum",1,"pageSize",count),scope);
        assertThat(((Number)page.get("total")).intValue()).isEqualTo(count);
        assertThat((List<?>)page.get("list")).hasSize(count);
        assertThat((List<?>)page.get("list")).anySatisfy(raw -> assertThat(((Map<?,?>)raw).get("tid")).isEqualTo("table-1"));
        assertThat(counter.reads).isEqualTo(8);
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
        assertThat(counter.reads).isEqualTo(2);
        System.out.println("department page verification: items="+count+", SQL=8; foreign/denied datasource items=0, SQL=2");
        counter.reads=0;
        Map<?,?> pending=run(f,"76fd211790f54164947f276824d19c1d",Map.of("viewLevel","table","pageSize",count,"accessStatus","0"),scope);
        assertThat(((Number)pending.get("total")).intValue()).isEqualTo(count);
        assertThat((List<?>)pending.get("list")).hasSize(count);
        assertThat(counter.reads).isEqualTo(9);
        counter.reads=0;
        Map<?,?> search=run(f,"76fd211790f54164947f276824d19c1d",Map.of("viewLevel","table","pageSize",count,"keyword","table-"),scope);
        assertThat(((Number)search.get("total")).intValue()).isEqualTo(count-1);
        assertThat(counter.reads).isEqualTo(8);
        System.out.println("filtered page verification: items="+count+", pending SQL=9; keyword SQL=8");
    }
    @ParameterizedTest @ValueSource(ints={20,100})
    void taskLinksResolveAllTargetsAndRealChainedSourcesInConstantReads(int count) throws Exception {
        var f = new DatasourceCompletionMagicTest.Fixture(count);
        createWarehouseConfiguration(f);
        f.jdbc.execute("alter table db_datasource_t add db_name varchar(100)");
        f.jdbc.execute("alter table db_datasource_t add org_id varchar(64)");
        f.jdbc.execute("alter table db_table_t add business_type varchar(64)");
        f.jdbc.execute("alter table db_table_t add table_name varchar(64)");
        f.jdbc.execute("alter table db_table_t add table_name_cn varchar(64)");
        f.jdbc.execute("alter table db_table_t add table_comment varchar(64)");
        f.jdbc.execute("alter table db_table_t add schema_name varchar(64)");
        f.jdbc.execute("alter table db_table_t add source_table_id varchar(64)");
        f.jdbc.execute("alter table db_table_t add updated_time timestamp");
        f.jdbc.execute("create table data_access_agg_task_t(tid varchar(64),source_table_id varchar(64),target_table_id varchar(64),pipeline_id varchar(64),tenant_id varchar(32),is_del int,updated_time timestamp)");
        f.jdbc.execute("create table nifi_pipeline_t(id varchar(64),tenant_id varchar(32),is_del int,dsl_json clob,status varchar(32),nifi_process_group_id varchar(64))");
        f.jdbc.update("update db_datasource_t set asset_status=1,db_name='来源库'");
        f.jdbc.update("update db_table_t set asset_status=1,business_type='业务表',table_name=tid,schema_name='public',updated_time=current_timestamp");
        f.jdbc.update("insert into db_datasource_t(tid,tenant_id,asset_status,is_del,db_name) values('target-db','ga',2,0,'汇聚库'),('hive-db','ga',2,0,'Hive库'),('denied','ga',2,0,'无权库')");
        for (int i=0;i<count;i++) {
            String id="table-"+i, target="ods-"+i, hive="hive-"+i;
            f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,business_type,table_name,schema_name,source_table_id,updated_time) values(?,'ga','target-db',2,0,'业务表',?,'ods',?,current_timestamp)",target,target,id);
            // A second DSL target is not linked by source_table_id; it must still be batched and shown.
            f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,business_type,table_name,schema_name,updated_time) values(?,'ga','hive-db',2,0,'业务表',?,'dw',current_timestamp)",hive,hive);
            String pipeline="pipe-"+i;
            f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,table_name) values(?,'other','target-db',2,0,'foreign-shadow'),(?,'ga','denied',2,0,'denied-table')",target,"denied-"+i);
            String dsl="{\"nodes\":[{\"category\":\"sink\",\"config\":{\"targetTableId\":\""+target+"\"}},{\"category\":\"sink\",\"config\":{\"targetTableId\":\""+hive+"\"}},{\"category\":\"sink\",\"config\":{\"targetTableId\":\"denied-"+i+"\"}}]}";
            f.jdbc.update("insert into nifi_pipeline_t(id,tenant_id,is_del,dsl_json) values(?,'ga',0,?)",pipeline,dsl);
            f.jdbc.update("insert into data_access_agg_task_t values(?,?,?,?, 'ga',0,current_timestamp)","task-"+i,id,target,pipeline);
        }
        // The next hop starts from the ODS table rather than the root table.
        f.jdbc.update("insert into data_access_agg_task_t values('chain','ods-0','hive-0','chain-pipe','ga',0,current_timestamp)");
        f.jdbc.update("insert into nifi_pipeline_t(id,tenant_id,is_del,dsl_json) values('chain-pipe','ga',0,'{\"nodes\":[{\"category\":\"sink\",\"config\":{\"targetTableId\":\"hive-0\"}}]}')");
        var counter=new Counter();f.db.setSqlInterceptors(List.of(counter));
        var dialects=new DialectAdapter();dialects.add(new MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});
        f.db.setDialectAdapter(dialects);f.db.setResultProvider(new DefaultResultProvider("link-test"));
        Map<?,?> page=run(f,"76fd211790f54164947f276824d19c1d",Map.of("viewLevel","table","pageSize",count,"datasourceIds",List.of("db")),new VisibleScope());
        assertThat((List<?>)page.get("list")).hasSize(count);
        assertThat(counter.reads).isEqualTo(12);
        for (Object raw:(List<?>)page.get("list")) {
            Map<?,?> row=(Map<?,?>)raw;
            List<?> tasks=(List<?>)row.get("tasks");
            Map<?,?> root=(Map<?,?>)tasks.stream().filter(task -> row.get("tid").equals(((Map<?,?>)task).get("sourceTableId"))).findFirst().orElseThrow();
            assertThat(root.get("sourceTableName")).isEqualTo(row.get("tid"));
            assertThat(root.get("sourceSchemaName")).isEqualTo("public");
            assertThat(root.get("sourceDbName")).isEqualTo("来源库");
            assertThat((List<?>)root.get("targetTables")).hasSize(2);
            if ("table-0".equals(row.get("tid"))) {
                Map<?,?> chained=(Map<?,?>)tasks.stream().filter(task -> "chain".equals(((Map<?,?>)task).get("tid"))).findFirst().orElseThrow();
                assertThat(chained.get("sourceTableName")).isEqualTo("ods-0");
                assertThat(chained.get("sourceDbName")).isEqualTo("汇聚库");
            }
        }
        System.out.println("task link verification: items="+count+", SQL="+counter.reads+"; DSL targets and chained source preserved");
        counter.reads=0;
        Map<?,?> accessed=run(f,"76fd211790f54164947f276824d19c1d",Map.of("viewLevel","table","pageSize",count,"datasourceIds",List.of("db"),"accessStatus","1"),new VisibleScope());
        assertThat(((Number)accessed.get("total")).intValue()).isEqualTo(count);
        assertThat((List<?>)accessed.get("list")).hasSize(count);
        assertThat(counter.reads).isEqualTo(13);
        System.out.println("accessed link verification: items="+count+", SQL="+counter.reads);
        if (count==20) {
            Map<?,?> together=run(f,"76fd211790f54164947f276824d19c1d",Map.of("viewLevel","table","pageSize",100,"accessStatus","1"),new VisibleScope());
            assertThat(((Number)together.get("total")).intValue()).isEqualTo(21);
            List<?> all=(List<?>)together.get("list");
            assertThat(all).hasSize(21);
            for (String sourceId:List.of("table-0","ods-0")) {
                Map<?,?> source=(Map<?,?>)all.stream().filter(raw -> sourceId.equals(((Map<?,?>)raw).get("tid"))).findFirst().orElseThrow();
                assertThat((List<?>)source.get("tasks")).anySatisfy(raw -> assertThat(((Map<?,?>)raw).get("tid")).isEqualTo("chain"));
            }
        }
    }
    static void createWarehouseConfiguration(DatasourceCompletionMagicTest.Fixture f) {
        f.jdbc.execute("create table sym_dict_t(tid varchar(64),tenant_id varchar(32),parent_id varchar(64),dict_code varchar(64),is_del int)");
        f.jdbc.execute("create table dwm_center_layer_source_t(tenant_id varchar(32),target_id varchar(64),target_type varchar(32),datasource_id varchar(64),is_del int)");
    }
    private Map<?,?> run(DatasourceCompletionMagicTest.Fixture f,String id,Map<String,Object> body,VisibleScope scope) throws Exception {
        String script=CanonicalMagicSources.byId(id).replaceAll("(?m)^import (?!cn\\.hutool\\.)[^\\r\\n]*\\r?\\n","");
        Map<String,Object> bindings=new LinkedHashMap<>();bindings.put("body",body);bindings.put("db",f.db);
        bindings.put("tenantRuntime",scope);bindings.put("dataScope",scope);
        bindings.put("tableSort",new TableSortMagicModule());
        try (var runtime = new TaskRuntimeMagicFixture(scope.id())) {
            bindings.put("taskRuntime", runtime.module);
            return (Map<?,?>)MagicScript.create(script,null).execute(new MagicScriptContext(bindings));
        }
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
