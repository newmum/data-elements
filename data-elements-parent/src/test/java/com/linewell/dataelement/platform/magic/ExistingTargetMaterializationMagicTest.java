package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import com.linewell.dataelement.platform.magic.module.JsonModule;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

/** Replays materialization after task deletion: retained physical table, missing registration. */
class ExistingTargetMaterializationMagicTest {
    @Test
    void retainedHiveTableGetsRegisteredThenCreatesAFlowAndRetriesKeepFieldIds() throws Exception {
        List<Integer> readCounts=new ArrayList<>();
        for(int fields:List.of(20,100)) {
            Fixture f=new Fixture(fields);
            Map<?,?> registered=assertInstanceOf(Map.class,f.run(false));
            String id=registered.get("targetTableId").toString();
            assertEquals(true,registered.get("physicalTableReused")); assertEquals(1,f.physicalCalls);
            readCounts.add(f.base.readCount());
            assertEquals(fields+3,f.jdbc.queryForObject("select count(*) from db_table_column_t where table_id=? and is_del=0",Integer.class,id));
            List<String> ids=f.jdbc.queryForList("select tid from db_table_column_t where table_id=? order by column_name",String.class,id);
            f.jdbc.update("update db_table_t set record_count=77,storage_size='10 KB' where tid=?",id);
            Map<?,?> retried=assertInstanceOf(Map.class,f.run(false));
            assertEquals(id,retried.get("targetTableId"));
            assertEquals(ids,f.jdbc.queryForList("select tid from db_table_column_t where table_id=? order by column_name",String.class,id));
            assertEquals(77,f.jdbc.queryForObject("select record_count from db_table_t where tid=?",Integer.class,id));
            assertEquals(1,f.jdbc.queryForObject("select count(*) from db_table_t where datasource_id='retained-db' and is_del=0",Integer.class));
            Map<?,?> flow=assertInstanceOf(Map.class,f.base.run(Map.of("tableId","source-1","targetTableIds",List.of(id))));
            assertEquals(1,flow.get("createdCount"));
            assertEquals(id,f.jdbc.queryForObject("select target_table_id from data_access_agg_task_t where is_del=0",String.class));
            System.out.println("Retained Hive registration fields="+fields+", metadata SQL="+readCounts.getLast()+", physical CREATE=0, flow created, stable field IDs on retry");
        }
        assertEquals(readCounts.getFirst(),readCounts.getLast());
    }

    @Test
    void roleDenialAndOtherSourceOwnershipCannotAdoptExistingTable() throws Exception {
        Fixture denied=new Fixture(1); denied.base.scope.deniedId="retained-db";
        assertInstanceOf(ExitValue.class,denied.run(false)); assertEquals(0,denied.physicalCalls);
        Fixture owned=new Fixture(1);
        owned.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,source_table_id,is_del) values('owned','police','retained-db','ods_retained','another-source',0)");
        assertInstanceOf(ExitValue.class,owned.run(false)); assertEquals(0,owned.physicalCalls);
        assertEquals("another-source",owned.jdbc.queryForObject("select source_table_id from db_table_t where tid='owned'",String.class));
    }

    @Test
    void reuseProbeOnlyDoesNotRegisterAnything() throws Exception {
        Fixture f=new Fixture(1); Map<?,?> result=assertInstanceOf(Map.class,f.run(true));
        assertEquals(false,result.get("sideEffectsApplied")); assertEquals(true,result.get("physicalTableReused"));
        assertEquals(0,f.base.writeCount());
        assertEquals(0,f.jdbc.queryForObject("select count(*) from db_table_t where datasource_id='retained-db'",Integer.class));
    }

    @Test
    void twentyAndHundredDictionaryFieldsPrefetchMetadataAndKeepRoleChecks() throws Exception {
        List<Integer> reads=new ArrayList<>();
        for(int size:List.of(20,100)) {
            Fixture f=new Fixture(size);
            for(int i=0;i<size;i++) {
                String dict="dict-"+i;
                f.jdbc.update("update db_table_column_t set code_table_id=? where tid=?",dict,"source-col-"+i);
                f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,field_governance_config,is_del) values(?,'police','source-db',?,?,0)",dict,"dict_"+i,
                    "{\"dictionaryProfile\":{\"codeField\":\"code\",\"labelField\":\"name\"}}");
                for(String name:List.of("code","name")) f.jdbc.update("insert into db_table_column_t(tid,tenant_id,table_id,column_name,data_type,is_del) values(?,'police',? ,?,'varchar',0)",dict+name,dict,name);
            }
            Map<?,?> result=assertInstanceOf(Map.class,f.run(true));
            assertEquals(size*2+3,((List<?>)result.get("tableItems")).size());
            reads.add(f.base.readCount()); assertEquals(0,f.base.writeCount());
            System.out.println("Reuse dictionary fields="+size+", metadata SQL="+reads.getLast()+", writes=0");
        }
        assertEquals(reads.getFirst(),reads.getLast());
        Fixture hidden=new Fixture(1);
        hidden.jdbc.update("insert into db_datasource_t(tid,tenant_id,db_type,is_del) values('hidden-dict-db','police','mysql',0)");
        hidden.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,table_name,is_del) values('hidden-dict','police','hidden-dict-db','dictionary',0)");
        hidden.jdbc.update("update db_table_column_t set code_table_id='hidden-dict' where table_id='source-1'");
        hidden.base.scope.deniedId="hidden-dict-db";
        assertInstanceOf(ExitValue.class,hidden.run(true)); assertEquals(0,hidden.physicalCalls); assertEquals(0,hidden.base.writeCount());
    }

    public static class Fixture {
        final DataAggTaskEnsureBatchMagicTest.Fixture base=new DataAggTaskEnsureBatchMagicTest.Fixture(0);
        final JdbcTemplate jdbc=base.jdbc;
        final int fields;
        int physicalCalls;
        Fixture(int count) {
            fields=count; base.prepareCreation();
            jdbc.execute("alter table nifi_pipeline_t alter column dsl_json clob");
            add("db_table_t","table_comment varchar(300),table_type varchar(64),business_type varchar(64),record_count bigint,field_count int,storage_size varchar(64),total_size_bytes bigint,total_size_formatted varchar(64),related_directory varchar(128),org_id varchar(64),flow_status int,updated_time timestamp,reg_time timestamp,catalog_name varchar(300),source_table_name varchar(100)");
            add("db_datasource_t","db_name varchar(64),org_id varchar(64),app_id varchar(64),show_connect int default 1");
            add("db_table_column_t","column_comment varchar(300),data_type varchar(64),column_type varchar(100),length bigint,precision_length int,scale int,nullable int,default_value varchar(64),primary_key int,auto_increment int,is_unique int,indexed int,charset varchar(64),collation varchar(64),extra varchar(64),source_table_column_id varchar(64),created_time timestamp,updated_time timestamp");
            add("da_prop_t","tid varchar(64),prop_type varchar(32),data_type varchar(64),created_time timestamp,updated_time timestamp");
            add("da_catalog_t","reg_time timestamp,asset_status int,flow_status int,created_time timestamp,updated_time timestamp");
            jdbc.update("update db_datasource_t set pool_cfg=?,db_name='fixture-source' where tid='source-db'",
                "{\"host\":\"fixture.invalid\",\"database\":\"fixture\",\"username\":\"fixture\"}");
            jdbc.update("insert into db_datasource_t(tid,tenant_id,db_type,pool_cfg,is_del,db_name,asset_status) values('retained-db','police','hive',?,0,'Retained Hive',2)",
                "{\"database\":\"warehouse\",\"metadataAccessMode\":\"server-managed-mrs\"}");
            jdbc.update("delete from db_table_column_t");
            for(int i=0;i<fields;i++) jdbc.update("insert into db_table_column_t(tid,tenant_id,table_id,column_name,data_type,column_type,length,ordinal_position,is_del) values(?,'police','source-1',?,'varchar','varchar(32)',32,?,0)","source-col-"+i,"field_"+i,i);
        }
        void add(String table,String declarations) {
            for(String declaration:declarations.split(",")) jdbc.execute("alter table "+table+" add "+declaration);
        }
        Object run(boolean probe) throws Exception {
            return run(probe,false);
        }
        Object run(boolean probe,boolean dryRun) throws Exception {
            List<Map<String,Object>> items=new ArrayList<>();
            for(int i=0;i<fields;i++) items.add(new LinkedHashMap<>(Map.of("columnName","field_"+i,"dataType","varchar","columnType","varchar(32)","length",32,"nullable",1)));
            Map<String,Object> body=new LinkedHashMap<>(Map.of("tid","source-1","propList",new LinkedHashMap<>(Map.of("dbId","retained-db","tableName","ods_retained","tableNameCn","保留物理表")),"tableItems",items,"ddl","CREATE TABLE ods_retained(id STRING)","reuseExistingTable",true));
            if(probe) body.put("validateReuseOnly",true);
            if(dryRun) body.put("dryRun",true);
            String script=CanonicalMagicSources.byId("e3320ec899a24426adf5f77e2dab36f9").replaceAll("(?m)^import (?!cn\\.hutool\\.)[^\\r\\n]*\\r?\\n","");
            script="var createTable=()=> fixture.physical(body)\nvar date_format=value=>value+''\n"+script;
            Map<String,Object> inputs=new LinkedHashMap<>();
            inputs.put("db",base.db); inputs.put("body",body); inputs.put("fixture",this);
            inputs.put("tenantRuntime",base.scope); inputs.put("dataScope",base.scope); inputs.put("jsons",new JsonModule());
            inputs.put("metadataAsset",this); inputs.put("esCommonService",this);
            base.resetCounter();
            return MagicScript.create(script,null).execute(new MagicScriptContext(inputs));
        }
        public Map<String,Object> physical(Map<String,Object> request) {
            if(Boolean.TRUE.equals(request.get("validateOnly"))) return Map.of("valid",true);
            assertEquals(true,request.get("reuseExistingOnly"),"Existing table path must never request creation"); physicalCalls++;
            var items=(List<Map<String,Object>>)request.get("tableItems");
            return Map.of("created",false,"physicalTableReused",true,"tableItems",items);
        }
        public Object getDocument(String index,String id) { return null; }
        public void saveOrUpdate(String index,String id,String content,boolean overwrite) { }
        public void save(String index,String id,Map<String,Object> content) { }
        public void refreshRegistrationStatus(String id,boolean finish) { }
        public int batchInsertTableColumns(List<Map<String,Object>> columns) {
            for(Map<String,Object> column:columns) {
                List<String> keys=new ArrayList<>(column.keySet());
                String sql="insert into db_table_column_t("+String.join(",",keys.stream().map(key->"`"+key+"`").toList())+") values("+String.join(",",Collections.nCopies(keys.size(),"?"))+")";
                jdbc.update(sql,keys.stream().map(column::get).toArray());
            }
            return columns.size();
        }
    }
}
