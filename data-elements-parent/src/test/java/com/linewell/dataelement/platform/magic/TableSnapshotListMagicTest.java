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

class TableSnapshotListMagicTest {
 public static class Scope { boolean denied; String tenant="police"; public String id(){return tenant;} public List<?> visibleDataSourcesFor(String resource,List<?> rows){return denied?List.of():rows;} }
 static class Fixture {
  final JdbcTemplate jdbc;
  final SQLModule db;
  final Scope scope=new Scope();
  int reads;
  Fixture(int count){
   var source=new DriverManagerDataSource("jdbc:h2:mem:snapshot_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
   jdbc=new JdbcTemplate(source);
   jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(64),db_name varchar(64),app_id varchar(64),org_id varchar(64),is_del int)");
   jdbc.execute("insert into db_datasource_t values('source','police','db','app','org',0),('foreign-source','other','secret','app','org',0)");
   String fields="tid,datasource_id,table_name,table_name_en,table_name_cn,table_comment,table_type,business_type,business_type_reason,record_count,storage_size,total_size_bytes,total_size_formatted,org_id,org_path,related_directory,data_source_type,timestamp_field,dictionary_structure_type,dictionary_table_flag,source_catalog_id,field_governance_config,dictionary_profiles,dictionary_profile,dictionary_categories,source_table_id,source_table_name,manage_unit,asset_status,flow_status,reg_time,created_time,updated_time,tenant_id";
   jdbc.execute("create table db_table_t("+String.join(",",Arrays.stream(fields.split(",")).map(f->f+" varchar(500)").toList())+",field_count int,annotated int,is_del int)");
   for(int i=0;i<count;i++)jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,table_comment,business_type,annotated,field_count,updated_time,is_del) values(?,'police','source',?,?,?,?,?,'2026-10-24',0)","row-"+i,"table_"+i,"表"+i,i%2==0?"业务表":"字典表",i%2,i);
   jdbc.execute("insert into db_table_t(tid,tenant_id,datasource_id,table_name,table_comment,business_type,annotated,field_count,updated_time,is_del) values('preferred','police','source','table_0','已标注名称','业务表',1,999,'2026-10-24',0),('foreign','other','source','secret','跨租户','业务表',1,5,'2026-10-24',0)");
            var sources = new MagicDynamicDataSource(); sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setResultProvider(new DefaultResultProvider("detail-fields-test"));
            var dialects = new DialectAdapter(); dialects.add(new MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});
            db.setDialectAdapter(dialects);
            var columns=new ColumnMapperAdapter(); columns.add(new DefaultColumnMapperProvider()); columns.add(new CamelColumnMapperProvider()); columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value->value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(new SQLInterceptor(){@Override public void preHandle(BoundSql sql,RequestEntity request){if(sql.getSql().stripLeading().toLowerCase().startsWith("select")) reads++;}}));
        }

  Object read(boolean all,int size,String business,String sourceId) throws Exception {
   return read(all,size,business,sourceId,"","");
  }
  Object read(boolean all,int size,String business,String sourceId,String sortField,String sortDir) throws Exception {
   String canonical=CanonicalMagicSources.byId("52e398a9f2fc429da79048b9ea719b37");
   String script="import java.util.Map\nvar tenantId=tenantRuntime.id()\n"+canonical.substring(canonical.indexOf("var textOf ="));
   Map<String,Object> vars=new HashMap<>();
   vars.put("db",db);
   vars.put("tenantRuntime",scope);
   vars.put("dataScope",scope);
   vars.put("dbId",sourceId);
   vars.put("paged",true);
   vars.put("pageNo",1);
   vars.put("pageSize",size);
   vars.put("allSnapshots",all);
   vars.put("includeGovernance",false);
   vars.put("businessType",business);
   vars.put("catalogPaged",false);
   vars.put("keyword","");
   vars.put("sortField",sortField);
   vars.put("sortDir",sortDir);
   reads=0;return MagicScript.create(script,null).execute(new MagicScriptContext(vars));
  }
 }
 @Test void fixedReadsForTwentyAndHundredTablesIncludeBothStatesAndKeepStepTwoCompatible() throws Exception {
  for(int count:List.of(20,100)){
   var f=new Fixture(count);
   var all=(Map<?,?>)f.read(true,count,"","source");
   assertEquals(4,f.reads);
   assertEquals(count,((Number)all.get("total")).intValue());
   assertEquals(count,((List<?>)all.get("rows")).size());
   assertEquals(count,((Number)all.get("snapshotTotal")).intValue());
   var rows=(List<Map<String,Object>>)all.get("rows");
   assertTrue(rows.stream().anyMatch(r->((Number)r.get("annotated")).intValue()==0));
   assertTrue(rows.stream().anyMatch(r->((Number)r.get("annotated")).intValue()==1));
   assertFalse(rows.stream().anyMatch(r->"secret".equals(r.get("tableName"))));
   assertEquals("preferred",rows.stream().filter(r->"table_0".equals(r.get("tableName"))).findFirst().orElseThrow().get("tid"));
   assertTrue(rows.stream().noneMatch(r->r.containsKey("fieldGovernanceConfig")));
   var sorted=(Map<?,?>)f.read(true,count,"","source","fieldCount","desc");
   assertEquals("preferred",((List<Map<?,?>>)sorted.get("rows")).get(0).get("tid"));
   assertEquals(4,f.reads);
   var injection=(Map<?,?>)f.read(true,count,"","source","fieldCount; drop table db_table_t","desc");
   assertEquals(count,((Number)injection.get("total")).intValue());
   assertEquals(4,f.reads);
   var marked=(Map<?,?>)f.read(false,count,"","source");
   assertEquals(count/2+1,((Number)marked.get("total")).intValue());
   assertEquals(4,f.reads);
   var filtered=(Map<?,?>)f.read(true,count,"字典表","source");
   assertEquals(count/2,((Number)filtered.get("total")).intValue());
   assertEquals(4,f.reads);
  }
 }
 @Test void foreignSourcesStopBeforePageQueriesWhileMenuRoleDoesNotBlock() throws Exception {
  var f=new Fixture(20);
   assertInstanceOf(ExitValue.class,f.read(true,100,"","foreign-source"));
   assertEquals(1,f.reads);f.scope.denied=true;
   assertInstanceOf(Map.class,f.read(true,100,"","source"));
   assertEquals(4,f.reads);f.scope.denied=false;f.scope.tenant="none";
   assertInstanceOf(ExitValue.class,f.read(true,100,"","source"));
   assertEquals(1,f.reads);
 }
}
