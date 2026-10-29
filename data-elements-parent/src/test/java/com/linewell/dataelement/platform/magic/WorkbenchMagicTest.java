package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
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


class WorkbenchMagicTest {
 public static class Scope {
  String value="ALL";
  public String id(){return "police";}
  public Map<String,Object> resource(String code){return Map.of("userId","user","roleIds",List.of("role"),"organizationIds",List.of("org-a"),"scope",value);}
  public Map<String,Object> session(){return Map.of("appId","app");}
 }
 public static class Sort {
  public String field(Map<?,?> body,Map<?,?> allowed,String fallback){return String.valueOf(allowed.get(allowed.containsKey(body.get("sortField"))?body.get("sortField"):fallback));}
  public String direction(Map<?,?> body){return "asc".equals(body.get("sortDir"))?"asc":"desc";}
 }
 static class Fixture {
  final JdbcTemplate jdbc;final SQLModule db;final Scope scope=new Scope();int reads;final List<String> statements=new ArrayList<>();
  Fixture(int count) throws Exception {
   var source=new DriverManagerDataSource("jdbc:h2:mem:workbench_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");jdbc=new JdbcTemplate(source);
   String ddl=java.nio.file.Files.readString(java.nio.file.Path.of("../db/baseline_ga/init.sql"));
   for(String table:List.of("db_datasource_t","db_table_t","metadata_table_collection_job_t","data_access_agg_task_t","nifi_pipeline_t","dwm_center_layer_source_t","sym_dict_t","rm_role_t","sym_application_t","rm_org_t","rm_user_t")) {
    String section=ddl.split("CREATE TABLE `"+table+"` \\(",2)[1].split("\\) ENGINE=",2)[0];var columns=new ArrayList<String>();
    for(String line:section.split("\\r?\\n")) {
     var matcher=java.util.regex.Pattern.compile("^\\s*`([^`]+)`\\s+(\\w+)(?:\\(([^)]+)\\))?.*").matcher(line);
     if(matcher.matches()) {
      String type=matcher.group(2).toLowerCase();String mapped=List.of("tinyint","int","bigint","smallint").contains(type)?"bigint":type.equals("datetime")?"timestamp":type.equals("decimal")?"decimal(20,4)":type.equals("longtext")?"clob":"varchar(4000)";
      columns.add("`"+matcher.group(1)+"` "+mapped);
     }
    }
    jdbc.execute("create table `"+table+"`("+String.join(",",columns)+")");
   }
   jdbc.execute("insert into rm_role_t(id,tenant_id,app_id,code_num,deleted,status) values('role','police','app','admin',0,0)");
   jdbc.execute("insert into db_datasource_t(tid,tenant_id,db_name,created_by,org_id,asset_status,is_del) values('owned','police','本部门库','user','org-a',2,0),('other','police','其他部门库','another','org-b',1,0),('foreign','foreign','其他租户库','user','org-a',2,0)");
   for(int i=0;i<count;i++)jdbc.update("insert into db_table_t(tid,datasource_id,tenant_id,table_name,table_name_cn,business_type,annotated,asset_status,field_count,field_governance_config,is_del) values(?,?,'police',?,'业务表','业务表',?,?,1,?,0)","table-"+i,i%2==0?"owned":"other","t_"+i,i%2,i%3==0?2:1,"{\"registrationSavedPhase\":\"business\",\"timestampField\":\"id\",\"fields\":[{\"columnName\":\"id\",\"standardField\":\"DATE\",\"dictionaryRelation\":{\"enabled\":true,\"sql\":\"select code\"}}]}");
   jdbc.execute("insert into db_table_t(tid,datasource_id,tenant_id,table_name,business_type,annotated,asset_status,is_del) values('secret','owned','foreign','secret','业务表',1,2,0)");
   var sources=new MagicDynamicDataSource();sources.setDefault(source);db=new SQLModule(sources);db.setDataSourceNode(sources.getDataSource());db.setResultProvider(new DefaultResultProvider("workbench-test"));
   var dialects=new DialectAdapter();dialects.add(new MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});db.setDialectAdapter(dialects);
   var mapper=new ColumnMapperAdapter();mapper.add(new DefaultColumnMapperProvider());mapper.add(new CamelColumnMapperProvider());mapper.setDefault("camel");db.setColumnMapperProvider(mapper);db.setColumnMapRowMapper(mapper.getDefaultColumnMapRowMapper());db.setRowMapColumnMapper(v->v);db.setNamedTableInterceptors(List.of());
   db.setSqlInterceptors(List.of(new SQLInterceptor(){@Override public void preHandle(BoundSql sql,RequestEntity req){// H2 does not retain bound values when it inlines a multiply referenced CTE.
    try {net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql.getSql());} catch(Exception error) {throw new AssertionError("SQL must also pass the runtime tenant parser",error);}
    // Render only fixture parameters here; production Magic always uses JDBC bindings.
    if(sql.getSql().contains("WITH accessible_sources")) {
     String statement=sql.getSql();for(Object value:sql.getParameters()) {assertNotNull(value,"missing named SQL binding");int index=statement.indexOf('?');String literal=value instanceof Number?value.toString():"'"+value.toString().replace("'","''")+"'";statement=statement.substring(0,index)+literal+statement.substring(index+1);}
     sql.setSql(statement);sql.setParameters(List.of());
    }
if(sql.getSql().stripLeading().toLowerCase().matches("(?s)(select|with).*")){reads++;statements.add(sql.getSql());}}}));
  }
  Object script(String key,Map<String,Object> input,Map<String,Object> extra) throws Exception {
   var paths=Map.of("context","function/01.通用函数/20.工作台数据范围.ms","changes","function/01.通用函数/21.工作台变更查询.ms","overview","api/02.数据盘点/04.盘点统计/01.工作台统计/01.工作台概览.ms","items","api/02.数据盘点/04.盘点统计/01.工作台统计/02.工作台事项分页.ms","export","api/02.数据盘点/04.盘点统计/01.工作台统计/03.工作台导出.ms");
   String source=java.nio.file.Files.readString(java.nio.file.Path.of("../magic/"+paths.get(key))).split("================================",2)[1].replaceAll("(?m)^import[^\\n]*\\n","");
   var vars=new HashMap<String,Object>();vars.put("payload",input);vars.put("body",input);vars.put("tenantRuntime",scope);vars.put("tenantAccounts",scope);vars.put("dataScope",scope);vars.put("db",db);vars.put("tableSort",new Sort());
   vars.put("workbenchData",(java.util.function.Function<Object[],Object>) args->{try{return script("context",(Map<String,Object>)args[0],Map.of());}catch(Exception e){throw new RuntimeException(e);}});
   vars.put("workbenchChanges",(java.util.function.Function<Object[],Object>)args->Map.of("available",true,"list",List.of(),"counts",Map.of("added",0,"modified",0,"deleted",0)));
   vars.putAll(extra);return MagicScript.create("import java.util.Map\nimport java.util.List\nimport cn.hutool.json.JSONUtil\n"+source,null).execute(new MagicScriptContext(vars));
  }
 }
 @Test void collectionChangesUseFixedBatchReadsForTwentyAndHundred() throws Exception {
  for(int count:List.of(20,100)) {
   var f=new Fixture(count);
   for(int parity=0;parity<2;parity++) {
    var details=new ArrayList<Map<String,String>>();for(int i=parity;i<count;i+=2)details.add(Map.of("tid","table-"+i));
    f.jdbc.update("insert into metadata_table_collection_job_t(tid,tenant_id,datasource_id,status,added_count,deleted_detail,is_del) values(?,'police',?,'SUCCEEDED',0,?,0)","job-"+parity,parity==0?"owned":"other",cn.hutool.json.JSONUtil.toJsonStr(details));
   }
   var context=f.script("context",Map.of("scope","all"),Map.of());f.reads=0;var extra=Map.<String,Object>of("context",context);
   var result=(Map<?,?>)f.script("changes",Map.of("pageSize",count,"sortField","name","sortDir","asc"),extra);
   assertEquals(true,result.get("available"));assertEquals(false,result.get("modifiedAvailable"));assertEquals(count,((List<?>)result.get("list")).size());assertEquals(3,f.reads);
   var descending=(Map<?,?>)f.script("changes",Map.of("pageSize",count,"sortField","name","sortDir","desc"),extra);
   var sorted=(List<?>)descending.get("list");for(int i=1;i<sorted.size();i++)assertTrue(((Map<?,?>)sorted.get(i-1)).get("tableName").toString().compareToIgnoreCase(((Map<?,?>)sorted.get(i)).get("tableName").toString())>=0);
   f.jdbc.execute("update metadata_table_collection_job_t set deleted_detail='invalid'");var failure=(Map<?,?>)f.script("changes",Map.of(),extra);assertEquals(false,failure.get("available"));assertTrue(failure.get("reason").toString().contains("差异记录"));
   System.out.println("WORKBENCH changes "+count+": SQL=3, no ES or per-row requests; malformed diff is explicit");
  }
 }
 @Test void collectionDiffsValidateEveryIdAndItsAuthorizedSource() throws Exception {
  var f=new Fixture(20);
  f.jdbc.update("insert into metadata_table_collection_job_t(tid,tenant_id,datasource_id,status,added_count,deleted_detail,is_del) values('job','police','owned','SUCCEEDED',3,?,0)","[{\"tid\":\"table-0\"},{\"tid\":\"secret\"},{\"tid\":\"table-1\"}]");
  var context=f.script("context",Map.of("scope","all"),Map.of());var extra=Map.<String,Object>of("context",context);
  var result=(Map<?,?>)f.script("changes",Map.of(),extra);var counts=(Map<?,?>)result.get("counts");
  assertEquals(true,result.get("available"));assertEquals(3,((Number)counts.get("added")).intValue());assertEquals(1,((Number)counts.get("deleted")).intValue());assertNull(counts.get("modified"));assertEquals(2,((List<?>)result.get("list")).size());
  var empty=(Map<?,?>)f.script("changes",Map.of("keyword","没有匹配项"),extra);assertEquals(0,((Number)empty.get("total")).intValue());
 }
 @Test void twentyAndHundredUseFixedReadsAndSameAuthorizedScope() throws Exception {
  Integer overviewReads=null,itemReads=null;
  for(int count:List.of(20,100)) {
   var f=new Fixture(count);f.reads=0;var all=(Map<?,?>)f.script("overview",Map.of("scope","all"),Map.of());var totals=(Map<?,?>)all.get("counts");assertEquals(count,((Number)totals.get("tables")).intValue());assertEquals(true,all.get("canViewAll"));
   int reads=f.reads;if(overviewReads==null)overviewReads=reads;else assertEquals(overviewReads.intValue(),reads);
   var gov=(Map<?,?>)all.get("governance");assertEquals(true,gov.get("available"));assertEquals(count,((Number)gov.get("standardFields")).intValue());
   f.reads=0;var page=(Map<?,?>)f.script("items",Map.of("scope","all","kind","tables","pageSize",count),Map.of());assertEquals(count,((Number)page.get("total")).intValue());assertEquals(count,((List<?>)page.get("list")).size());
   if(itemReads==null)itemReads=f.reads;else assertEquals(itemReads.intValue(),f.reads);
   var ids=java.util.stream.IntStream.range(0,count).mapToObj(i->"table-"+i).toList();f.reads=0;
   var selected=(Map<?,?>)f.script("items",Map.of("scope","all","ids",ids,"pageSize",count),Map.of());assertEquals(count,((List<?>)selected.get("list")).size());assertEquals(4,f.reads);
   f.reads=0;var report=(Map<?,?>)f.script("export",Map.of("scope","all","pageSize",count),Map.of());assertEquals(4,f.reads);assertEquals(count,((Number)((Map<?,?>)report.get("summary")).get("directoryCount")).intValue());
   var mine=(Map<?,?>)f.script("overview",Map.of("scope","mine"),Map.of());assertEquals(count/2,((Number)((Map<?,?>)mine.get("counts")).get("tables")).intValue());
   f.jdbc.execute("update rm_role_t set code_num='ywjz'");f.scope.value="ORG";
   var police=(Map<?,?>)f.script("overview",Map.of("scope","auto"),Map.of());assertEquals("mine",police.get("mode"));assertEquals(false,police.get("canViewAll"));assertEquals(count/2,((Number)((Map<?,?>)police.get("counts")).get("tables")).intValue());
   assertInstanceOf(ExitValue.class,f.script("context",Map.of("scope","all"),Map.of()));
   assertInstanceOf(ExitValue.class,f.script("items",Map.of("scope","mine","ids",List.of("table-0","table-1")),Map.of()));
   assertInstanceOf(ExitValue.class,f.script("items",Map.of("scope","mine","ids",List.of("table-0","secret")),Map.of()));
   f.scope.value="OWNER";var owner=(Map<?,?>)f.script("items",Map.of("scope","mine","kind","tables"),Map.of());assertEquals(count/2,((Number)owner.get("total")).intValue());
   f.jdbc.execute("update rm_role_t set app_id='other-app',code_num='admin'");
   assertInstanceOf(ExitValue.class,f.script("context",Map.of("scope","all"),Map.of()));
   assertInstanceOf(ExitValue.class,f.script("context",Map.of("scope","mine"),Map.of()));
   System.out.println("WORKBENCH "+count+": overview SQL="+overviewReads+", page SQL="+itemReads+", admin/ORG/OWNER/foreign IDs checked");
  }
 }
 @Test void pipelineStateWinsOverHistoricalTaskFailure() throws Exception {
  var f=new Fixture(20);
  f.jdbc.execute("insert into data_access_agg_task_t(tid,tenant_id,source_table_id,target_table_id,pipeline_id,task_status,is_del) values('task','police','table-0','table-2','flow',2,0)");
  f.jdbc.execute("insert into nifi_pipeline_t(id,tenant_id,status,is_del) values('flow','police','RUNNING',0)");
  var running=(Map<?,?>)((Map<?,?>)f.script("overview",Map.of("scope","all"),Map.of())).get("counts");
  assertEquals(1,((Number)running.get("accessRunningTasks")).intValue());assertEquals(0,((Number)running.get("accessExceptionTasks")).intValue());
  f.jdbc.execute("update nifi_pipeline_t set status='STOPPED'");
  var stopped=(Map<?,?>)((Map<?,?>)f.script("overview",Map.of("scope","all"),Map.of())).get("counts");
  assertEquals(0,((Number)stopped.get("accessRunningTasks")).intValue());assertEquals(0,((Number)stopped.get("accessExceptionTasks")).intValue());
 }
 @Test void independentSectionsKeepFixedQueriesAndSameAuthorizedResults() throws Exception {
  var queryCounts=Map.of("registration",3,"governance",3,"health",2,"tasks",2,"changes",1);
  for(int count:List.of(20,100)) {
   var f=new Fixture(count);
   var full=(Map<?,?>)f.script("overview",Map.of("scope","all"),Map.of());
   for(String section:List.of("registration","governance","health","tasks","changes")) {
    f.reads=0;f.statements.clear();
    var result=(Map<?,?>)f.script("overview",Map.of("scope","all","section",section),Map.of());
    assertEquals(queryCounts.get(section).intValue(),f.reads,section);
    assertEquals(section,result.get("section"));assertEquals("all",result.get("mode"));
    assertEquals(true,result.get("canViewAll"));
    if(section.equals("registration")) {
     assertEquals(((Map<?,?>)full.get("counts")).get("tables"),((Map<?,?>)result.get("counts")).get("tables"));
     assertEquals(full.get("types"),result.get("types"));
    } else {
     assertTrue(f.statements.stream().noneMatch(sql->sql.contains("t.field_governance_config")),section+" must not parse registration JSON");
     assertFalse(((Map<?,?>)result.get("counts")).containsKey("tables"));
    }
   }
   f.jdbc.execute("update rm_role_t set code_num='ywjz'");f.scope.value="ORG";
   var mine=(Map<?,?>)f.script("overview",Map.of("scope","auto","section","registration","tenantId","foreign"),Map.of());
   assertEquals(count/2,((Number)((Map<?,?>)mine.get("counts")).get("tables")).intValue());
   for(String section:queryCounts.keySet()) {
    var scoped=(Map<?,?>)f.script("overview",Map.of("scope","auto","section",section),Map.of());
    assertEquals("mine",scoped.get("mode"));assertEquals(false,scoped.get("canViewAll"));
    if(section.equals("health"))assertTrue(((List<?>)scoped.get("health")).stream().allMatch(row->"owned".equals(((Map<?,?>)row).get("id"))));
   }
   assertInstanceOf(ExitValue.class,f.script("context",Map.of("scope","all"),Map.of()));
   f.reads=0;assertInstanceOf(ExitValue.class,f.script("overview",Map.of("section","unknown"),Map.of()));assertEquals(0,f.reads);
   System.out.println("WORKBENCH sections "+count+": fixed reads="+queryCounts+", same scope, lightweight non-registration SQL");
  }
 }
}
