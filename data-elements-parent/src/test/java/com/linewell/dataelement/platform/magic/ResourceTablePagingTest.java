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

/** Execute maintained Magic reads against SQL, including other-tenant and owner-scope decoys. */
class ResourceTablePagingTest {
    public static class Tenant { public String id(){return "tenant-a";} }
    public static class Scope {
        public Map<String,Object> dataSource(){return Map.of("scope","OWNER","userId","owner","organizationIds",List.of("org-a"));}
    }
    private static class Counter implements SQLInterceptor {
        int count;
        public void preHandle(BoundSql sql,RequestEntity request){count++;}
    }
    @Test void catalogSortsEntireAuthorizedResultBeforePagingWithThreeReads() throws Exception {
        var f=new Fixture();
        for(String field:List.of("name","updatedAt","itemCount")) {
            f.counter.count=0;var a=f.catalog(20,field);assertEquals(3,f.counter.count);
            f.counter.count=0;var b=f.catalog(100,field);assertEquals(3,f.counter.count);
            var large=(List<Map<String,Object>>)b.get("list");
            assertEquals(120,((Number)b.get("total")).intValue());assertEquals(100,large.size());
            assertEquals(a.get("list"),large.subList(0,20));
            assertTrue(large.stream().allMatch(row->row.get("id").toString().startsWith("cat-")));
            if(field.equals("itemCount"))assertEquals("cat-119",large.getFirst().get("id"));
        }
        f.counter.count=0;assertThrows(Exception.class,()->f.catalog(20,"untrusted SQL"));assertEquals(0,f.counter.count);
    }
    @Test void modelLogsUseFullSqlPaginationAndNeverReturnOtherTenantActorsOrModels() throws Exception {
        var f=new Fixture();
        f.counter.count=0;var small=f.command(Map.of("table","modelLogs","page",1,"size",20,"sortField","modelName","sortDir","asc"));assertEquals(3,f.counter.count);
        f.counter.count=0;var large=f.command(Map.of("table","modelLogs","page",1,"size",100,"sortField","modelName","sortDir","asc"));assertEquals(3,f.counter.count);
        var rows=(List<Map<String,Object>>)large.get("list");assertEquals(120,((Number)large.get("total")).intValue());
        assertEquals(small.get("list"),rows.subList(0,20));assertEquals(100,rows.size());
        assertTrue(rows.stream().allMatch(row->"Authorized actor".equals(row.get("actorName"))));
        assertTrue(rows.stream().allMatch(row->row.get("id").toString().startsWith("log-")));
        assertThrows(Exception.class,()->f.command(Map.of("table","modelLogs","modelId","model-outside")));
    }
    @Test void boundedModelConfigurationValidatesEveryRequestedIdBeforeOrdering() throws Exception {
        var f=new Fixture();f.counter.count=0;var models=f.models();assertEquals(4,f.counter.count);
        var ids=((List<Map<String,Object>>)models.get("models")).stream().map(row->row.get("id")).toList();
        f.counter.count=0;var small=f.commandWithModels(Map.of("table","models","ids",ids,"sortField","entityCount","sortDir","desc","size",20),models);
        var large=f.commandWithModels(Map.of("table","models","ids",ids,"sortField","entityCount","sortDir","desc","size",100),models);
        assertEquals(0,f.counter.count);assertEquals(small.get("keys"),((List<?>)large.get("keys")).subList(0,20));
        assertEquals("model-119",((List<?>)large.get("keys")).getFirst());
        assertThrows(Exception.class,()->f.commandWithModels(Map.of("table","models","ids",List.of("model-001","model-outside")),models));
        assertThrows(Exception.class,()->f.commandWithModels(Map.of("table","models","ids",List.of("model-001","model-001")),models));
    }
    @Test void childItemsSortBeforePagingAndRejectUnauthorizedParents() throws Exception {
        var f=new Fixture();
        String originalSource=CanonicalMagicSources.under("api").stream().filter(s->s.relativePath().endsWith("07.数据目录数据项列表.ms")).findFirst().orElseThrow().source();
        final String source=originalSource.replaceAll("(?m)^import (tenantRuntime|dataScope|tableSort)[^\\r\\n]*\\r?\\n","");
        var database=f.database;
        database.execute("ALTER TABLE da_catalog_t ADD field_governance_config VARCHAR");
        database.execute("ALTER TABLE da_catalog_item_t ADD col_length INT");
        database.execute("ALTER TABLE da_catalog_item_t ADD created_time TIMESTAMP");database.execute("ALTER TABLE da_catalog_item_t ADD updated_time TIMESTAMP");
        for(String name:List.of("col_en","col_type","col_comment","is_pk","is_masking","is_fk","is_nullable","date_format","mark_lvl","col_unit","col_precision","masking_id","share_type","share_condition","is_dict","dict_name","col_max_length","default_value","data_standard_id","quality_rule","enable_code_table","code_table_id","target_table_column_id"))database.execute("ALTER TABLE da_catalog_item_t ADD "+name+" VARCHAR");
        database.update("UPDATE da_catalog_item_t SET col_length=sort_no,col_name=CONCAT('Field ',LPAD(sort_no,3,'0'))");
        for(String field:List.of("colName","colLength")) {
            f.counter.count=0;var small=f.execute(source,Map.of("body",Map.of("catalogId","cat-119","pageNo",1,"pageSize",20,"sortField",field,"sortDir","desc"),"dataScope",new Scope()));assertEquals(3,f.counter.count);
            f.counter.count=0;var large=f.execute(source,Map.of("body",Map.of("catalogId","cat-119","pageNo",1,"pageSize",100,"sortField",field,"sortDir","desc"),"dataScope",new Scope()));assertEquals(3,f.counter.count);
            var rows=(List<Map<String,Object>>)large.get("list");assertEquals(120,((Number)large.get("total")).intValue());assertEquals(small.get("list"),rows.subList(0,20));assertEquals(119,((Number)rows.getFirst().get("colLength")).intValue());
        }
        assertThrows(Exception.class,()->f.execute(source,Map.of("body",Map.of("catalogId","hidden-119","pageNo",1),"dataScope",new Scope())));
    }
    @Test void dictionarySiblingOrderUsesSqlAndTwoReadsAtTwentyAndOneHundredItems() throws Exception {
        var f=new Fixture();
        f.database.execute("CREATE TABLE sym_dict_t(tid VARCHAR,tenant_id VARCHAR,dict_code VARCHAR,dict_name VARCHAR,tree_path VARCHAR,parent_id VARCHAR,sort_no INT,level_no INT,is_del INT,created_time TIMESTAMP,updated_time TIMESTAMP)");
        f.database.update("INSERT INTO sym_dict_t VALUES('root','tenant-a','type','类型','root','0',0,2,0,NULL,NULL),('foreign-root','tenant-b','type','秘密','foreign','0',0,2,0,NULL,NULL)");
        String source=CanonicalMagicSources.under("api").stream().filter(s->s.relativePath().endsWith("02.字典树查询.ms")).findFirst().orElseThrow().source().replaceAll("(?m)^import (tenantRuntime|tableSort)[^\\r\\n]*\\r?\\n","");
        for(int size:List.of(20,100)){
            for(int i=size==20?0:20;i<size;i++)f.database.update("INSERT INTO sym_dict_t VALUES(?,'tenant-a',?,?,'root.child','root',?,3,0,NULL,NULL)","dict-"+i,"code-"+i,String.format("Name %03d",i),100-i);
            f.counter.count=0;
            var context=new HashMap<String,Object>();context.put("db",f.sql);context.put("tenantRuntime",new Tenant());context.put("tableSort",new TableSortMagicModule());context.put("query",Map.of());context.put("body",Map.of("dictCode","type","showRow",true,"sortField","dictName","sortDir","asc"));
            var rows=(List<Map<String,Object>>)MagicScript.create(source,null).execute(new MagicScriptContext(context));
            assertEquals(2,f.counter.count);assertEquals(size,rows.size());assertEquals("Name 000",rows.getFirst().get("dictName"));assertEquals("Name "+String.format("%03d",size-1),rows.getLast().get("dictName"));
            assertTrue(rows.stream().allMatch(r->r.get("tid").toString().startsWith("dict-")));
        }
    }

    private static class Fixture {
        final Counter counter=new Counter();final SQLModule sql;final JdbcTemplate database;
        Fixture() {
            var ds=new DriverManagerDataSource("jdbc:h2:mem:resources_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            var sources=new MagicDynamicDataSource();sources.setDefault(ds);sql=new SQLModule(sources);sql.setDataSourceNode(sources.getDataSource());
            var dialects=new DialectAdapter();dialects.add(new MySQLDialect(){@Override public boolean match(String url){return url.contains(":h2:");}});
            sql.setDialectAdapter(dialects);sql.setResultProvider(new DefaultResultProvider("resource-table-test"));
            var mapper=new ColumnMapperAdapter();mapper.add(new DefaultColumnMapperProvider());mapper.add(new CamelColumnMapperProvider());mapper.setDefault("camel");
            sql.setColumnMapperProvider(mapper);sql.setSqlInterceptors(List.of(counter));sql.setNamedTableInterceptors(List.of());sql.setColumnMapRowMapper(mapper.getDefaultColumnMapRowMapper());sql.setRowMapColumnMapper(v->v);
            var db=new JdbcTemplate(ds);database=db;
            db.execute("CREATE TABLE db_datasource_t(tid VARCHAR,tenant_id VARCHAR,created_by VARCHAR,org_id VARCHAR,is_del INT)");
            db.execute("CREATE TABLE db_table_t(tid VARCHAR,tenant_id VARCHAR,datasource_id VARCHAR,is_del INT)");
            db.execute("CREATE TABLE dwm_center_layer_source_t(tid VARCHAR,tenant_id VARCHAR,datasource_id VARCHAR,target_type VARCHAR,is_del INT)");
            db.execute("CREATE TABLE da_catalog_t(tid VARCHAR,tenant_id VARCHAR,catalog_name VARCHAR,catalog_name_en VARCHAR,asset_desc VARCHAR,org_id VARCHAR,source_table_id VARCHAR,source_table_name VARCHAR,db_id VARCHAR,asset_status INT,flow_status INT,resource_revision_no INT,created_by VARCHAR,updated_by VARCHAR,created_time TIMESTAMP,updated_time TIMESTAMP,is_del INT)");
            db.execute("CREATE TABLE da_catalog_item_t(tid VARCHAR,tenant_id VARCHAR,catalog_id VARCHAR,source_table_column_id VARCHAR,col_name VARCHAR,sort_no INT,is_del INT)");
            db.execute("CREATE TABLE res_resource_operation_log(tid VARCHAR,tenant_id VARCHAR,actor_id VARCHAR,action_name VARCHAR,object_id VARCHAR,object_name VARCHAR,detail_text VARCHAR,created_time TIMESTAMP)");
            db.execute("CREATE TABLE res_logical_model(tid VARCHAR,tenant_id VARCHAR,model_name VARCHAR,model_code VARCHAR,model_status VARCHAR,design_json VARCHAR,revision_no INT,model_type VARCHAR,database_id VARCHAR,domain_name VARCHAR,owner_name VARCHAR,model_description VARCHAR,created_time TIMESTAMP,updated_time TIMESTAMP,is_del INT)");
            db.execute("CREATE TABLE res_logical_model_revision(tid VARCHAR,tenant_id VARCHAR,model_id VARCHAR,revision_number INT,content_json VARCHAR,note VARCHAR,content_hash VARCHAR,created_time TIMESTAMP)");
            db.execute("CREATE TABLE res_materialization_plan(tid VARCHAR,tenant_id VARCHAR,model_id VARCHAR,plan_json VARCHAR,plan_status VARCHAR,revision_no INT,created_time TIMESTAMP,updated_time TIMESTAMP,is_del INT)");
            db.execute("CREATE TABLE res_model_itemset(tid VARCHAR,tenant_id VARCHAR,model_id VARCHAR,itemset_name VARCHAR,items_json VARCHAR,itemset_status VARCHAR,revision_no INT,created_time TIMESTAMP,updated_time TIMESTAMP,is_del INT)");
            db.execute("CREATE TABLE rm_user_t(id VARCHAR,tenant_id VARCHAR,real_name VARCHAR,user_name VARCHAR)");
            db.update("INSERT INTO rm_user_t VALUES('owner','tenant-a','Authorized actor','owner'),('owner','tenant-b','Wrong tenant actor','owner')");
            for(String source:List.of("permitted","forbidden")) {
                db.update("INSERT INTO db_datasource_t VALUES(?,'tenant-a',?,'org-a',0)",source,source.equals("permitted")?"owner":"another-user");
                db.update("INSERT INTO db_table_t VALUES(?,'tenant-a',?,0)",source,source);
                db.update("INSERT INTO dwm_center_layer_source_t VALUES(?,'tenant-a',?,'domain',0)",source,source);
            }
            for(int i=0;i<120;i++) {
                String id=String.format("%03d",i),name="Name "+String.format("%03d",119-i);
                db.update("INSERT INTO da_catalog_t VALUES(?,'tenant-a',?,'CODE','','org-a','permitted','Table','permitted',2,2,1,'owner','owner',CURRENT_TIMESTAMP,DATEADD('SECOND',?,CURRENT_TIMESTAMP),0)","cat-"+id,name,i);
                db.update("INSERT INTO da_catalog_t VALUES(?,'tenant-a','Outside scope','CODE','','org-a','forbidden','Table','forbidden',2,2,1,'owner','owner',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)","hidden-"+id);
                for(int n=0;n<=i;n++)db.update("INSERT INTO da_catalog_item_t VALUES(?,'tenant-a',?,?,'Field',?,0)",id+"-"+n,"cat-"+id,"f"+n,n);
                String design="{\"entities\":["+String.join(",",Collections.nCopies(i,"{}"))+"]}";
                db.update("INSERT INTO res_logical_model VALUES(?,'tenant-a',?,'MODEL','ACTIVE',?,1,'MANUAL','','','','',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)","model-"+id,name,design);
                db.update("INSERT INTO res_resource_operation_log VALUES(?,'tenant-a','owner','创建逻辑模型',?,'Model','Created',DATEADD('SECOND',?,CURRENT_TIMESTAMP))","log-"+id,"model-"+id,i);
                db.update("INSERT INTO res_resource_operation_log VALUES(?,'tenant-b','owner','创建逻辑模型','model-outside','Wrong tenant','Outside',CURRENT_TIMESTAMP)","outside-"+id);
            }
            db.update("INSERT INTO res_logical_model VALUES('model-outside','tenant-b','Outside','MODEL','ACTIVE','{}',1,'MANUAL','','','','',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)");
        }
        Map<String,Object> catalog(int size,String field) throws Exception {
            return runCatalog(size,field);
        }
        Map<String,Object> runCatalog(int size,String field) throws Exception {
            String source=CanonicalMagicSources.under("function").stream().filter(s->s.relativePath().endsWith("02.catalog-read.ms")).findFirst().orElseThrow().source();
            source=source.replaceAll("(?m)^import (tenantRuntime|dataScope|tableSort|jsons|'@post:[^\\r\\n]+)[^\\r\\n]*\\r?\\n","").replace("var actorId = StpUtil.getLoginIdAsString()","var actorId = 'owner'");
            return execute("var currentUser=()=>({orgId:'org-a'});\n"+source,Map.of("input",Map.of("mode","page","page",1,"size",size,"query",Map.of("sortField",field,"sortDir","desc")),"dataScope",new Scope()));
        }
        Map<String,Object> models() throws Exception {
            String source=CanonicalMagicSources.under("function").stream().filter(s->s.relativePath().endsWith("05.model-read.ms")).findFirst().orElseThrow().source();
            source=source.replaceAll("(?m)^import (tenantRuntime|jsons|'@/[^\\r\\n]+)[^\\r\\n]*\\r?\\n","");
            return execute(source,Map.of("input",Map.of("projectionOnly",true)));
        }
        Map<String,Object> command(Map<String,Object> query) throws Exception{return commandWithModels(query,Map.of());}
        Map<String,Object> commandWithModels(Map<String,Object> query,Map<String,Object> models) throws Exception {
            String source=CanonicalMagicSources.under("api").stream().filter(s->s.relativePath().endsWith("20.resource-command.ms")).findFirst().orElseThrow().source();
            source=source.replaceAll("(?m)^import (tenantRuntime|dataScope|tableSort|'[^\\r\\n]+)[^\\r\\n]*\\r?\\n","");
            return execute("var modelRead=(input)=>models;\n"+source,Map.of("body",Map.of("kind","table","method","GET","body",query),"models",models,"dataScope",new Scope()));
        }
        Map<String,Object> execute(String source,Map<String,Object> extra) throws Exception {
            var context=new HashMap<String,Object>(extra);context.put("db",sql);context.put("tenantRuntime",new Tenant());context.put("tableSort",new TableSortMagicModule());
            return (Map<String,Object>)MagicScript.create(source,null).execute(new MagicScriptContext(context));
        }
    }
}
