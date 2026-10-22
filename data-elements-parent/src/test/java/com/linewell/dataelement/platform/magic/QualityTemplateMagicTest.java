package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;
import com.linewell.dataelement.platform.magic.module.JsonModule;
import com.linewell.dataelement.platform.magic.TableSortMagicModule;
import com.linewell.dataelement.feature.dataquality.infrastructure.jdbc.QualitySqlTemplate;

class QualityTemplateMagicTest {
    public static class Scope {
        boolean denied;
        public String id(){return "ga";}
        public boolean canReadDataSource(Map<?,?> row){return !denied;}
        public List<Map<String,Object>> visibleDataSources(List<Map<String,Object>> rows){return denied?List.of():rows;}
    }
    public static class Ids {public String fastSimpleUUID(){return UUID.randomUUID().toString().replace("-","");}}
    public static class Actor {public String getLoginIdAsString(){return "tester";}}
    public static class Log {public void info(String message,Object value){}}
    public static class Task {
        Map<String,Object> saved;
        public String persistTask(Map<String,Object> body){saved=body;return "task";}
        public Map<String,Object> taskDetail(String id){return Map.of("tid",id);}
    }
    static class Fixture {
        final DatasourceCompletionMagicTest.Fixture f=new DatasourceCompletionMagicTest.Fixture(100);
        final Scope scope=new Scope();final Task task=new Task();
        final TaskCategoryWarehouseScopeMagicTest.Counter counter=new TaskCategoryWarehouseScopeMagicTest.Counter();
        Fixture(){
            f.db.setResultProvider(new org.ssssssss.magicapi.core.interceptor.DefaultResultProvider("quality-template-test"));
            var dialects=new org.ssssssss.magicapi.modules.db.dialect.DialectAdapter();
            dialects.add(new org.ssssssss.magicapi.modules.db.dialect.MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});
            f.db.setDialectAdapter(dialects);
            f.jdbc.execute("alter table db_datasource_t add org_id varchar(64)");f.jdbc.execute("alter table db_datasource_t add created_by varchar(64)");
            f.jdbc.execute("create table data_gov_quality_rule(tid varchar(32),tenant_id varchar(32),is_del int,scope_of_application varchar(255),rule_name varchar(255),rule_code varchar(255),check_dimension varchar(50),check_logic clob,rule_description clob,created_by varchar(32),created_time timestamp,updated_by varchar(32),updated_time timestamp)");
            f.jdbc.execute("create table db_table_column_t(tid varchar(64),tenant_id varchar(32),table_id varchar(64),column_name varchar(100),column_comment varchar(100),data_type varchar(64),column_type varchar(64),nullable int,primary_key int,is_unique int,indexed int,ordinal_position int,is_del int)");
            for(int i=0;i<100;i++)f.jdbc.update("insert into db_table_column_t(tid,tenant_id,table_id,column_name,is_del) values(?,'ga',?,'name',0)","column-"+i,"table-"+i);
            f.db.setSqlInterceptors(List.of(counter));
        }
        Object run(String name,Map<String,Object> body) throws Exception {
            String all=Files.readString(Path.of("../magic/api/05.数据治理/02.质量规则/"+name+".ms"));
            String script=all.substring(all.indexOf("================================")+32).replaceAll("(?m)^import [^\\r\\n]*\\r?\\n","");
            Map<String,Object> bindings=new LinkedHashMap<>();bindings.put("body",body);bindings.put("db",f.db);bindings.put("tenantRuntime",scope);bindings.put("dataScope",scope);
            bindings.put("qualitySql",new QualitySqlTemplate());bindings.put("jsons",new JsonModule());bindings.put("IdUtil",new Ids());bindings.put("StpUtil",new Actor());bindings.put("tableSort",new TableSortMagicModule());bindings.put("dataQuality",task);
            bindings.put("log",new Log());
            counter.reads=0;
            return MagicScript.create(script,null).execute(new MagicScriptContext(bindings));
        }
        String save() throws Exception {return String.valueOf(((Map<?,?>)run("23.保存SQL模板",new LinkedHashMap<>(Map.of("ruleName","参数模板","ruleCode","DQ_SQL","dimension","ACCURACY","sqlTemplate","${column}<#{min}","values",Map.of("min",0))))).get("tid"));}
    }
    @Test void savesPagedTemplatesWithoutUnscopedUpdateOrDelete() throws Exception {
        var f=new Fixture();String id=f.save();
        for(int i=1;i<100;i++)f.f.jdbc.update("insert into data_gov_quality_rule(tid,tenant_id,is_del,scope_of_application,rule_name,rule_code,check_dimension,check_logic) values(?,'ga',0,'QUALITY_SQL_TEMPLATE_V1',?,'CODE','ACCURACY','{}')","template-"+i,"模板-"+i);
        assertThat(id).hasSize(32);
        for(int count:List.of(20,100)) {
            var page=new com.fasterxml.jackson.databind.ObjectMapper().convertValue(f.run("22.SQL模板分页",Map.of("size",count)),Map.class);
            assertThat(((List<?>)page.get("list"))).hasSize(count);assertThat(f.counter.reads).isEqualTo(2);
            System.out.println("quality templates page size="+count+", SQL="+f.counter.reads);
        }
        f.f.jdbc.update("update data_gov_quality_rule set tenant_id='foreign' where tid=?",id);
        assertThat(f.run("24.删除SQL模板",Map.of("tid",id))).isInstanceOf(ExitValue.class);
        assertThat(f.f.jdbc.queryForObject("select is_del from data_gov_quality_rule where tid=?",Integer.class,id)).isZero();
    }
    @Test void taskCopiesAuthoritativeTemplateWithOneReadAndRejectsForeignIds() throws Exception {
        var f=new Fixture();String id=f.save();
        for(int count:List.of(20,64)) {
            var rules=new ArrayList<Map<String,Object>>();
            for(int i=0;i<count;i++)rules.add(new LinkedHashMap<>(Map.of("ruleType","SQL_TEMPLATE","parameters",new LinkedHashMap<>(Map.of("templateId",id,"sqlTemplate","tampered","values",Map.of("min",0))))));
            var result=f.run("04.保存任务",new LinkedHashMap<>(Map.of("taskName","模板任务","datasourceId","db","rules",rules)));
            assertThat(result).isInstanceOf(Map.class);assertThat(f.counter.reads).isEqualTo(2);
            var parameters=(Map<?,?>)((List<Map<String,Object>>)f.task.saved.get("rules")).getFirst().get("parameters");
            assertThat(parameters.get("sqlTemplate")).isEqualTo("${column}<#{min}");
        }
        f.f.jdbc.update("update data_gov_quality_rule set tenant_id='foreign' where tid=?",id);
        assertThat(f.run("04.保存任务",Map.of("taskName","拒绝","datasourceId","db","rules",List.of(Map.of("ruleType","SQL_TEMPLATE","parameters",Map.of("templateId",id)))))).isInstanceOf(ExitValue.class);
    }
    @Test void legacyEditorsCannotBypassTemplateOrTenantBoundaries() throws Exception {
        var f=new Fixture();String id=f.save();
        for(String route:List.of("05.质量规则保存修改","06.质量规则删除"))assertThat(f.run(route,Map.of("tid",id))).isInstanceOf(ExitValue.class);
        assertThat((List<?>)f.run("02.质量规则列表",Map.of())).isEmpty();
        f.f.jdbc.update("update data_gov_quality_rule set tenant_id='foreign' where tid=?",id);
        for(String route:List.of("05.质量规则保存修改","06.质量规则删除"))assertThat(f.run(route,Map.of("tid",id))).isInstanceOf(ExitValue.class);
    }
    @Test void groupedColumnReadsRemainBoundedAndAuthorizeEachId() throws Exception {
        var f=new Fixture();
        for(int count:List.of(20,100)) {
            int reads=0;
            for(int offset=0;offset<count;offset+=50){var ids=new ArrayList<String>();for(int i=offset;i<Math.min(count,offset+50);i++)ids.add("table-"+i);
                var result=(Map<?,?>)f.run("15.字段选项",Map.of("tableIds",ids));assertThat(result).hasSize(ids.size());reads+=f.counter.reads;}
            assertThat(reads).isEqualTo(count==20?2:4);
            System.out.println("quality columns items="+count+", SQL="+reads+", bounded batches="+(count==20?1:2));
        }
        assertThat(f.run("15.字段选项",Map.of("tableIds",List.of("table-0","foreign")))).isInstanceOf(ExitValue.class);
        f.scope.denied=true;assertThat(f.run("15.字段选项",Map.of("tableIds",List.of("table-0")))).isInstanceOf(ExitValue.class);
    }
}
