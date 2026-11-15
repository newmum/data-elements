package com.linewell.dataelement.platform.magic;

import com.linewell.dataelement.utils.StructuredValues;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;
import com.linewell.dataelement.platform.magic.module.JsonModule;
import com.linewell.dataelement.platform.magic.TableSortMagicModule;
import com.linewell.dataelement.feature.dataquality.infrastructure.jdbc.QualitySqlTemplate;

class QualityTemplateMagicTest {
    private static final String TEMPLATE_PAGE = "quality_sql_template_page_v1";
    private static final String TEMPLATE_SAVE = "quality_sql_template_save_v1";
    private static final String TEMPLATE_DELETE = "quality_sql_template_delete_v1";
    private static final String TASK_SAVE = "a1000000000000000000000000000004";
    private static final String QUALITY_RULES = "7220cd17e6c24858a617d3e6f2d4ea42";
    private static final String FIELD_OPTIONS = "a1000000000000000000000000000014";
    private static final List<String> LEGACY_RULE_WRITES = List.of(
            "dd4214943e5341c482a59821a64245eb", "f15e72e40a2c41f4af8620ceb68a393b");
    public static class Scope {
        boolean denied;
        Map<String, Object> sourceScope = Map.of("scope", "ALL");
        public String id(){return "ga";}
        public boolean canReadDataSource(Map<?,?> row){return !denied;}
        public Map<String,Object> dataSource(){return denied ? Map.of("scope", "NONE") : sourceScope;}
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
        final StructuredTableReadMagicModule tableRead=TaskRuntimeMagicFixture.structuredReads(f.jdbc, sql -> counter.reads++);
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
        Object run(String metadataId,Map<String,Object> body) throws Exception {
            String script=CanonicalMagicSources.byId(metadataId).replaceAll("(?m)^import [^\\r\\n]*\\r?\\n","");
            Map<String,Object> bindings=new LinkedHashMap<>();bindings.put("body",body);bindings.put("db",f.db);bindings.put("tenantRuntime",scope);bindings.put("dataScope",scope);
            bindings.put("qualitySql",new QualitySqlTemplate());bindings.put("jsons",new JsonModule());bindings.put("IdUtil",new Ids());bindings.put("StpUtil",new Actor());bindings.put("tableSort",new TableSortMagicModule());bindings.put("dataQuality",task);
            bindings.put("log",new Log());
            bindings.put("tableRead",tableRead);
            if (FIELD_OPTIONS.equals(metadataId)) {
                bindFieldScope(bindings);
            }
            counter.reads=0;
            return TenantContext.call(scope.id(), () -> MagicScript.create(script,null).execute(new MagicScriptContext(bindings)));
        }
        private void bindFieldScope(Map<String, Object> bindings) throws Exception {
            var streamingRead = TaskRuntimeMagicFixture.structuredReads(f.jdbc, sql -> counter.reads++, null);
            bindings.put("tableRead", streamingRead);
            var catalogScope = script("cc92b11b7eb845768205468cb139d247");
            var fieldScope = script("e30966d29ca7423fa7cde295f8918b28");
            MagicScriptLambdaFunction filter = (context, args) -> catalogScope.execute(new MagicScriptContext(
                    Map.of("tableRead", streamingRead, "alias", args[0], "scope", args[1])));
            bindings.put("filterScope", filter);
            bindings.put("columns", (MagicScriptLambdaFunction) (context, args) -> fieldScope.execute(new MagicScriptContext(
                    Map.of("tableRead", streamingRead, "catalogScope", filter, "tenantId", args[0], "scope", args[1]))));
        }

        private MagicScript script(String id) throws Exception {
            return MagicScript.create(CanonicalMagicSources.byId(id).replaceAll("(?m)^import [^\\r\\n]*\\R", ""), null);
        }
        String save() throws Exception {return String.valueOf(((Map<?,?>)run(TEMPLATE_SAVE,new LinkedHashMap<>(Map.of("ruleName","参数模板","ruleCode","DQ_SQL","dimension","ACCURACY","sqlTemplate","${column}<#{min}","values",Map.of("min",0))))).get("tid"));}
    }
    @Test void savesPagedTemplatesWithoutUnscopedUpdateOrDelete() throws Exception {
        var f=new Fixture();String id=f.save();
        for(int i=1;i<100;i++)f.f.jdbc.update("insert into data_gov_quality_rule(tid,tenant_id,is_del,scope_of_application,rule_name,rule_code,check_dimension,check_logic) values(?,'ga',0,'QUALITY_SQL_TEMPLATE_V1',?,'CODE','ACCURACY','{}')","template-"+i,"模板-"+i);
        assertThat(id).hasSize(32);
        for(int count:List.of(20,100)) {
            var page=new com.fasterxml.jackson.databind.ObjectMapper().convertValue(f.run(TEMPLATE_PAGE,Map.of("size",count)),Map.class);
            assertThat(((List<?>)page.get("list"))).hasSize(count);assertThat(f.counter.reads).isEqualTo(2);
            System.out.println("quality templates page size="+count+", SQL="+f.counter.reads);
        }
        f.f.jdbc.update("update data_gov_quality_rule set tenant_id='foreign' where tid=?",id);
        assertThat(f.run(TEMPLATE_DELETE,Map.of("tid",id))).isInstanceOf(ExitValue.class);
        assertThat(f.f.jdbc.queryForObject("select is_del from data_gov_quality_rule where tid=?",Integer.class,id)).isZero();
    }
    @Test void taskCopiesAuthoritativeTemplateWithOneReadAndRejectsForeignIds() throws Exception {
        var f=new Fixture();String id=f.save();
        for(int count:List.of(20,64)) {
            var rules=new ArrayList<Map<String,Object>>();
            for(int i=0;i<count;i++)rules.add(new LinkedHashMap<>(Map.of("ruleType","SQL_TEMPLATE","parameters",new LinkedHashMap<>(Map.of("templateId",id,"sqlTemplate","tampered","values",Map.of("min",0))))));
            var result=f.run(TASK_SAVE,new LinkedHashMap<>(Map.of("taskName","模板任务","datasourceId","db","rules",rules)));
            assertThat(result).isInstanceOf(Map.class);assertThat(f.counter.reads).isEqualTo(2);
            var parameters=(Map<?,?>)(StructuredValues.objectRows(f.task.saved.get("rules"))).getFirst().get("parameters");
            assertThat(parameters.get("sqlTemplate")).isEqualTo("${column}<#{min}");
        }
        f.f.jdbc.update("update data_gov_quality_rule set tenant_id='foreign' where tid=?",id);
        assertThat(f.run(TASK_SAVE,Map.of("taskName","拒绝","datasourceId","db","rules",List.of(Map.of("ruleType","SQL_TEMPLATE","parameters",Map.of("templateId",id)))))).isInstanceOf(ExitValue.class);
    }

    @Test void templatePageRetainsTheWireContractAndFiltersBeforeStableSorting() throws Exception {
        var f = new Fixture();
        f.f.jdbc.execute("""
                insert into data_gov_quality_rule
                    (tid,tenant_id,is_del,scope_of_application,rule_name,rule_code,check_dimension,
                     rule_description,check_logic,updated_time) values
                ('a','ga',0,'QUALITY_SQL_TEMPLATE_V1','普通名称','find-1','ACCURACY','备注','{}',timestamp '2026-11-01 10:00:00'),
                ('b','ga',0,'QUALITY_SQL_TEMPLATE_V1','find 名称','code-2','ACCURACY','备注','{}',timestamp '2026-11-01 10:00:00'),
                ('c','ga',0,'QUALITY_SQL_TEMPLATE_V1','find 未更新','code-3','ACCURACY',null,'{}',null),
                ('deleted','ga',1,'QUALITY_SQL_TEMPLATE_V1','find','code-4','ACCURACY',null,'{}',null),
                ('other','foreign',0,'QUALITY_SQL_TEMPLATE_V1','find','code-5','ACCURACY',null,'{}',null),
                ('legacy','ga',0,'LEGACY','find','code-6','ACCURACY',null,'{}',null),
                ('dimension','ga',0,'QUALITY_SQL_TEMPLATE_V1','find','code-7','VALIDITY',null,'{}',null),
                ('unmatched','ga',0,'QUALITY_SQL_TEMPLATE_V1','不匹配','code-8','ACCURACY',null,'{}',null)
                """);
        var filters = new LinkedHashMap<String, Object>(Map.of("keyword", "find", "dimension", "ACCURACY",
                "sortField", "updated_time", "sortDir", "asc", "tenantId", "foreign"));
        var page = StructuredValues.objectMap(f.run(TEMPLATE_PAGE, filters));
        assertThat(page).containsOnlyKeys("total", "list").containsEntry("total", 3L);
        var rows = StructuredValues.objectRows(page.get("list"));
        assertThat(rows).extracting(row -> row.get("tid")).containsExactly("a", "b", "c");
        assertThat(rows.getFirst()).containsOnlyKeys("tid", "ruleName", "ruleCode", "checkDimension",
                "ruleDescription", "checkLogic", "updatedTime").containsEntry("checkLogic", "{}");
        assertThat(f.counter.reads).isEqualTo(2);

        filters.put("page", 2);
        assertThat(StructuredValues.objectMap(f.run(TEMPLATE_PAGE, filters)))
                .containsEntry("total", 3L).containsEntry("list", List.of());
        assertThat(f.counter.reads).isOne();
        filters.put("keyword", "absent");
        assertThat(StructuredValues.objectMap(f.run(TEMPLATE_PAGE, filters)))
                .containsEntry("total", 0L).containsEntry("list", null);
        assertThat(f.counter.reads).isOne();
    }
    @Test void legacyEditorsCannotBypassTemplateOrTenantBoundaries() throws Exception {
        var f=new Fixture();String id=f.save();
        for (String metadataId : LEGACY_RULE_WRITES) {
            assertThat(f.run(metadataId, Map.of("tid", id))).isInstanceOf(ExitValue.class);
        }
        assertThat((List<?>)f.run(QUALITY_RULES,Map.of())).isEmpty();
        f.f.jdbc.update("update data_gov_quality_rule set tenant_id='foreign' where tid=?",id);
        for (String metadataId : LEGACY_RULE_WRITES) {
            assertThat(f.run(metadataId, Map.of("tid", id))).isInstanceOf(ExitValue.class);
        }
    }
    @Test void groupedColumnReadsRemainBoundedAndAuthorizeEachId() throws Exception {
        var f=new Fixture();
        for(int count:List.of(20,100)) {
            int reads=0;
            for(int offset=0;offset<count;offset+=50){var ids=new ArrayList<String>();for(int i=offset;i<Math.min(count,offset+50);i++)ids.add("table-"+i);
                var result=(Map<?,?>)f.run(FIELD_OPTIONS,Map.of("tableIds",ids));assertThat(result).hasSize(ids.size());reads+=f.counter.reads;}
            assertThat(reads).isEqualTo(count==20?2:4);
            System.out.println("quality columns items="+count+", SQL="+reads+", bounded batches="+(count==20?1:2));
        }
        f.f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del) values('foreign','foreign','db',0,0)");
        assertThat(f.run(FIELD_OPTIONS,Map.of("tableIds",List.of("table-0","foreign"),"tenantId","foreign"))).isInstanceOf(ExitValue.class);
        assertThat(f.counter.reads).isOne();
        f.f.jdbc.update("insert into db_datasource_t(tid,tenant_id,is_del,org_id,created_by) values('restricted','ga',0,'other','other')");
        f.f.jdbc.update("update db_datasource_t set org_id='team',created_by='owner' where tid='db'");
        f.f.jdbc.update("update db_table_t set datasource_id='restricted' where tid='table-1'");
        for (var profile : List.of(Map.<String, Object>of("scope","ORG","organizationIds",List.of("team")),
                Map.<String, Object>of("scope","OWNER","userId","owner"))) {
            f.scope.sourceScope=profile;
            assertThat(f.run(FIELD_OPTIONS,Map.of("tableIds",List.of("table-0","table-1")))).isInstanceOf(ExitValue.class);
            assertThat(f.counter.reads).isOne();
            assertThat((List<?>)f.run(FIELD_OPTIONS,Map.of("tableId","table-0"))).hasSize(1);
            assertThat(f.counter.reads).isEqualTo(2);
        }
        f.scope.sourceScope=Map.of("scope","ALL");
        f.f.jdbc.update("delete from db_table_column_t where table_id='table-1'");
        var deduplicated=StructuredValues.objectMap(f.run(FIELD_OPTIONS,Map.of("tableIds",List.of("table-0","table-1","table-0"))));
        assertThat(deduplicated).containsOnlyKeys("table-0","table-1").containsEntry("table-1",List.of());
        assertThat(StructuredValues.objectRows(deduplicated.get("table-0"))).hasSize(1).allSatisfy(row ->
                assertThat(row).containsEntry("column_name","name").doesNotContainKeys("table_id","length","primary_key"));
        assertThat(f.run(FIELD_OPTIONS,Map.of("tableIds",Collections.nCopies(51,"table-0")))).isInstanceOf(ExitValue.class);
        assertThat(f.counter.reads).isZero();
        f.scope.denied=true;assertThat(f.run(FIELD_OPTIONS,Map.of("tableIds",List.of("table-0")))).isInstanceOf(ExitValue.class);
    }
}
