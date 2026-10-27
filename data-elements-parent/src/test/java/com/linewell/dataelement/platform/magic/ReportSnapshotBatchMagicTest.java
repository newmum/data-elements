package com.linewell.dataelement.platform.magic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.ssssssss.script.*;

class ReportSnapshotBatchMagicTest {
 public static class Es {
  int preparations;
  public String ensureTenantIndexReady(String name){preparations++;return "police_dataassets";}
  public Object saveOrUpdate(String index,String id,String json,boolean update){throw new AssertionError("per-table ES read/write helper is forbidden in this path");}
 }
 @Test void twentyAndHundredSnapshotsUseFourReadsAndOnePartialBulkUpsert() throws Exception {
  for(int count:List.of(20,100)){
   var f=new ReportRegistrationMagicTest.Fixture(1);
   var columns=new HashSet<String>();var metadata=f.base.jdbc.queryForRowSet("select * from db_table_t where 1=0").getMetaData();for(int i=1;i<=metadata.getColumnCount();i++)columns.add(metadata.getColumnName(i));
   for(String column:List.of("table_name_en","table_type","business_type_reason","timestamp_field","storage_size","total_size_formatted","total_size_bytes","field_count","org_id","org_path","data_source_type","source_table_id","source_table_name","manage_unit","asset_desc","updated_time","created_time","reg_time"))if(!columns.contains(column))f.base.jdbc.execute("alter table db_table_t add "+column+" varchar(1000)");
   columns.clear();metadata=f.base.jdbc.queryForRowSet("select * from db_table_column_t where 1=0").getMetaData();for(int i=1;i<=metadata.getColumnCount();i++)columns.add(metadata.getColumnName(i));
   for(String column:List.of("data_type","column_type","length","precision_length","scale","default_value","auto_increment","is_unique","indexed","data_standard_id","is_dict","dict_name","updated_time","created_time"))if(!columns.contains(column))f.base.jdbc.execute("alter table db_table_column_t add "+column+" varchar(1000)");
   f.base.jdbc.execute("alter table db_datasource_t add table_num int");f.base.jdbc.execute("alter table db_datasource_t add updated_time timestamp");
   f.base.jdbc.execute("update db_table_t set table_name='validation_0' where tid='table'");
   var tables=new ArrayList<Map<String,Object>>();var fieldMap=new HashMap<String,Object>();
   for(int i=0;i<count;i++){String name="validation_"+i;tables.add(Map.of("tableName",name,"tableComment","上报验证表","tableType","数据表","businessType","业务表","annotated",true));fieldMap.put(name,List.of(Map.of("columnName","record_id","columnComment","记录编号","columnType","varchar","columnLength",64,"primaryKey",false,"nullable",true,"isTimestamp",false)));}
   Es es=new Es();ElasticsearchOperations operations=mock(ElasticsearchOperations.class);final int[] bulk={0};
   doAnswer(invocation->{List<UpdateQuery> queries=invocation.getArgument(0);assertEquals(count,queries.size());for(UpdateQuery query:queries)assertEquals(Boolean.TRUE,query.getDocAsUpsert());bulk[0]++;return null;}).when(operations).bulkUpdate(anyList(),any(IndexCoordinates.class));
   Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();while(!Files.isDirectory(root.resolve("magic")))root=root.getParent();String content=Files.readString(root.resolve("magic/api/02.数据盘点/02.数据库表/01.元数据管理/20.手工导入元数据快照.ms"));
   String script=content.substring(content.indexOf("================================")+32).replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.|org\\.springframework\\.data\\.elasticsearch\\.core\\.(query|document|mapping))[^\\r\\n]*\\r?\\n","");
   var bindings=new HashMap<String,Object>(Map.of("body",Map.of("dbId","source","dataSourceType","mysql","batchIndex",true,"tables",tables,"fieldMap",fieldMap),"db",f.base.db,"tenantRuntime",f.base.scope,"jsons",new TableDetailFieldSaveMagicTest.Jsons(),"metadataAsset",new ReportRegistrationMagicTest.Metadata(),"esCommonService",es,"esOperations",operations));
   f.base.reads=0;assertInstanceOf(Map.class,MagicScript.create(script,null).execute(new MagicScriptContext(bindings)));assertEquals(4,f.base.reads);assertEquals(1,es.preparations);assertEquals(1,bulk[0]);
   System.out.println("Report snapshots="+count+": SQL=4, ES preparation=1, partial bulk upsert=1, per-table exists=0");
  }
 }
}
