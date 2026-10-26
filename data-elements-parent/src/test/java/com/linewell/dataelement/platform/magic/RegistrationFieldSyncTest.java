package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import com.linewell.dataelement.dataassets.runtime.MetadataAssetPersistenceModule;
import com.linewell.dataelement.metautil.model.dto.ColumnInfo;
import com.linewell.dataelement.metautil.model.dto.DataSourceConfig;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
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

/** Actual canonical scripts and H2 persistence, with a deterministic physical metadata boundary. */
class RegistrationFieldSyncTest {
    public static class Tenant { public String id(){return "tenant-a";} }
    public static class Scope {
        boolean denied;
        public List<?> visibleDataSourcesFor(String permission,List<Map<String,Object>> rows){
            assertEquals("MENU:2081000000000000002",permission);
            assertTrue(rows.stream().allMatch(r->"tenant-a".equals(r.get("tenantId"))));return denied?List.of():rows;
        }
    }
    public static class Connections {public Map<String,Object> read(String id){return Map.of("host","example","database","physical","username","reader");}}
    public static class Physical {
        int calls;final List<ColumnInfo> columns=new ArrayList<>();
        public List<ColumnInfo> getColumns(DataSourceConfig config,String table){calls++;assertEquals("physical",config.getDatabase());return columns;}
    }
    private static class Counter implements SQLInterceptor {
        int reads;
        public void preHandle(BoundSql sql,RequestEntity request){if(sql.getSql().stripLeading().toLowerCase().startsWith("select"))reads++;}
    }
    private static class Fixture {
        final JdbcTemplate jdbc;final SQLModule db;final MetadataAssetPersistenceModule persistence;
        final Counter counter=new Counter();final Physical physical=new Physical();final Scope scope=new Scope();
        Fixture(int width) {
            var ds=new DriverManagerDataSource("jdbc:h2:mem:sync_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");jdbc=new JdbcTemplate(ds);persistence=new MetadataAssetPersistenceModule(ds);
            var sources=new MagicDynamicDataSource();sources.setDefault(ds);db=new SQLModule(sources);db.setDataSourceNode(sources.getDataSource());
            var dialects=new DialectAdapter();dialects.add(new MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});db.setDialectAdapter(dialects);db.setResultProvider(new DefaultResultProvider("sync-test"));
            var mapper=new ColumnMapperAdapter();mapper.add(new DefaultColumnMapperProvider());mapper.add(new CamelColumnMapperProvider());mapper.setDefault("camel");db.setColumnMapperProvider(mapper);db.setColumnMapRowMapper(mapper.getDefaultColumnMapRowMapper());db.setRowMapColumnMapper(v->v);db.setSqlInterceptors(List.of(counter));db.setNamedTableInterceptors(List.of());
            jdbc.execute("CREATE TABLE db_datasource_t(tid VARCHAR,tenant_id VARCHAR,db_type VARCHAR,db_name VARCHAR,org_id VARCHAR,created_by VARCHAR,is_del INT)");
            jdbc.execute("CREATE TABLE db_table_t(tid VARCHAR,tenant_id VARCHAR,datasource_id VARCHAR,table_name VARCHAR,field_count INT,field_governance_config VARCHAR,updated_time TIMESTAMP,is_del INT)");
            jdbc.execute("CREATE TABLE db_table_column_t(tid VARCHAR PRIMARY KEY,tenant_id VARCHAR,table_id VARCHAR,column_name VARCHAR,column_comment VARCHAR,data_type VARCHAR,column_type VARCHAR,`length` BIGINT,precision_length INT,scale INT,nullable INT,default_value VARCHAR,primary_key INT,auto_increment INT,is_unique INT,indexed INT,ordinal_position INT,charset VARCHAR,collation VARCHAR,extra VARCHAR,source_table_column_id VARCHAR,created_time TIMESTAMP,updated_time TIMESTAMP,is_del INT,data_standard_id VARCHAR)");
            jdbc.update("INSERT INTO db_datasource_t VALUES('source','tenant-a','mysql','显示名','org','owner',0),('foreign-source','tenant-b','mysql','foreign','org','owner',0)");
            jdbc.update("INSERT INTO db_table_t VALUES('table','tenant-a','source','wide',?,NULL,NULL,0),('foreign-table','tenant-b','foreign-source','secret',1,NULL,NULL,0)",width);
            for(int i=0;i<width;i++){
                String name="field_"+i;jdbc.update("INSERT INTO db_table_column_t(tid,tenant_id,table_id,column_name,column_comment,data_type,primary_key,ordinal_position,is_del,data_standard_id) VALUES(?,'tenant-a','table',?,'旧注释','varchar',0,?,0,'governance-kept')","column-"+i,name,i+1);
                var column=new ColumnInfo();column.setColumnName(name);column.setColumnComment(i==1?null:"最新注释"+i);column.setDataType("varchar");column.setColumnType("varchar(100)");column.setLength(100L);column.setOrdinalPosition(i+1);column.setNullable(true);physical.columns.add(column);
            }
            jdbc.update("INSERT INTO db_table_column_t(tid,tenant_id,table_id,column_name,column_comment,is_del) VALUES('decoy','tenant-b','table','foreign','秘密',0)");
        }
        Object execute(String id,Map<String,Object> arguments)throws Exception{
            String script=CanonicalMagicSources.byId(id).replaceAll("(?m)^import ('@/common/datasourceConnectionConfig'|jsons|tenantRuntime|dataScope|hwhive|metadataAsset|com.linewell.dataelement.metautil.service.MetadataExplorerService|com.linewell.dataelement.metautil.structured.StructuredSourceProbeService)[^\\r\\n]*\\r?\\n","");
            var context=new HashMap<String,Object>();context.put("db",db);context.put("tenantRuntime",new Tenant());context.put("dataScope",scope);context.put("metadataAsset",persistence);context.put("metadataExplorerService",physical);context.put("connections",new Connections());context.putAll(arguments);
            try(var ignored=TenantContext.use("tenant-a")){return MagicScript.create("var datasourceConnectionConfig=(a,b,c,d)=>connections.read(b)\n"+script,null).execute(new MagicScriptContext(context));}
        }
        Object sync(boolean dry)throws Exception{return execute("metadata_columns_collect_24",Map.of("body",Map.of("tableId","table","force",true,"dryRun",dry)));}
        Object fields(String ids)throws Exception{return execute("a9dc328184b7410781b389f67fad1233",Map.of("tid",ids,"snapshotOnly",true,"dbId","","tableName",""));}
    }
    @Test void refreshUpdatesCommentsAddsEveryFieldAndPreservesIdsAndGovernanceAboveTwoHundred()throws Exception{
        for(int width:List.of(20,100,260)){
            var f=new Fixture(width);var extra=new ColumnInfo();extra.setColumnName("new_field");extra.setColumnComment("新增字段");extra.setDataType("int");f.physical.columns.add(extra);
            f.counter.reads=0;var rows=(List<Map<String,Object>>)f.sync(false);assertEquals(4,f.counter.reads);assertEquals(1,f.physical.calls);assertEquals(width+1,rows.size());
            var first=rows.stream().filter(r->"field_0".equals(r.get("columnName"))).findFirst().orElseThrow();assertEquals("column-0",first.get("tid"));assertEquals("最新注释0",first.get("columnComment"));assertEquals("governance-kept",first.get("dataStandardId"));
            assertEquals("旧注释",rows.stream().filter(r->"field_1".equals(r.get("columnName"))).findFirst().orElseThrow().get("columnComment"));
            assertEquals(width+1,f.jdbc.queryForObject("SELECT field_count FROM db_table_t WHERE tid='table'",Integer.class));assertEquals("秘密",f.jdbc.queryForObject("SELECT column_comment FROM db_table_column_t WHERE tid='decoy'",String.class));
            assertEquals(width+1,((List<?>)f.fields("table")).size());
        }
    }
    @Test void batchReadsRemainThreeSqlAtTwentyAndOneHundredTablesAndCheckEveryId()throws Exception{
        var f=new Fixture(260);assertEquals(260,((List<?>)f.fields("table")).size());
        for(int count:List.of(20,100)){
            var ids=new ArrayList<String>();
            for(int i=0;i<count;i++){String id="table-"+i;ids.add(id);f.jdbc.update("MERGE INTO db_table_t(tid,tenant_id,datasource_id,table_name,is_del) KEY(tid) VALUES(?,'tenant-a','source','wide',0)",id);}
            f.counter.reads=0;assertInstanceOf(Map.class,f.fields(String.join(",",ids)));assertEquals(3,f.counter.reads);
            ids.set(count-1,"foreign-table");assertInstanceOf(ExitValue.class,f.fields(String.join(",",ids)));
        }
        f.scope.denied=true;assertInstanceOf(List.class,f.fields("table"));assertInstanceOf(List.class,f.sync(false));assertEquals(1,f.physical.calls);
    }
    @Test void dryRunCountsAllFieldsWithoutWritesAndCaseDuplicateDoesNotAddAField()throws Exception{
        var f=new Fixture(260);f.physical.columns.add(f.physical.columns.getFirst());
        var result=(Map<String,Object>)f.sync(true);assertEquals(260,result.get("sourceFieldCount"));assertEquals(0,result.get("added"));assertEquals("旧注释",f.jdbc.queryForObject("SELECT column_comment FROM db_table_column_t WHERE tid='column-0'",String.class));
    }
}
