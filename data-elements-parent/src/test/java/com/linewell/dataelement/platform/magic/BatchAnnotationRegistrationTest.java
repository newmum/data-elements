package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

class BatchAnnotationRegistrationTest {
    @ParameterizedTest @ValueSource(ints={20,100})
    void annotationsWithColumnsHaveConstantReadsAndRefreshSourceOnce(int count) throws Exception {
        var f = fixture(count);
        var counter = new DataIngestionRegistrationScopeTest.Counter();
        f.db.setSqlInterceptors(List.of(counter));
        var scope=new DatasourceCompletionMagicTest.ScopeRuntime();
        var index=new IndexRuntime();
        var rows=rows(count);
        Map<?,?> result=(Map<?,?>) run(f,scope,index,rows,false);
        assertThat(((Number)result.get("affected")).intValue()).isEqualTo(count);
        assertThat(counter.reads).isEqualTo(3); // sources, requested tables, all old columns
        assertThat(scope.calls).isEqualTo(1);
        assertThat(index.calls).isEqualTo(1);
        assertThat(f.status()).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("select count(*) from db_table_column_t",Integer.class)).isEqualTo(count);
        assertThat(f.jdbc.queryForObject("select count(*) from db_table_t where asset_status=0",Integer.class)).isEqualTo(count);
    }
    @Test void deniedAndForeignSourcesAndOversizedBatchesNeverWrite() throws Exception {
        var f=fixture(20);
        var scope=new DatasourceCompletionMagicTest.ScopeRuntime();
        var index=new IndexRuntime();
        scope.denied=true;
        assertThat(run(f,scope,index,rows(20),false)).isInstanceOf(ExitValue.class);
        scope.denied=false;
        var mixed=new ArrayList<>(rows(20));
        mixed.add(Map.of("propList",Map.of("datasourceId","foreign","tableName","foreign","tableComment","外租户表")));
        assertThat(run(f,scope,index,mixed,false)).isInstanceOf(ExitValue.class);
        assertThat(run(f,scope,index,rows(101),false)).isInstanceOf(ExitValue.class);
        assertThat(f.jdbc.queryForObject("select count(*) from db_table_t where asset_status=0",Integer.class)).isEqualTo(1);
        assertThat(index.calls).isZero();
    }
    private DatasourceCompletionMagicTest.Fixture fixture(int count) {
        var f=new DatasourceCompletionMagicTest.Fixture(count);
        f.jdbc.update("update db_datasource_t set asset_status=2");
        for(String name:List.of("table_name","table_name_en","table_name_cn","table_comment","table_type","business_type","business_type_reason","total_size_formatted","org_id","org_path","related_directory","data_source_type","source_table_id","source_table_name","manage_unit","asset_desc","field_governance_config","flow_order_id")) {
            f.jdbc.execute("alter table db_table_t add "+name+" varchar(2000)");
        }
        for(String name:List.of("annotated","record_count","field_count","storage_size","total_size_bytes","flow_status")) {
            f.jdbc.execute("alter table db_table_t add "+name+" bigint");
        }
        for(String name:List.of("updated_time","created_time","reg_time")) f.jdbc.execute("alter table db_table_t add "+name+" timestamp");
        f.jdbc.update("update db_table_t set table_name=tid");
        f.jdbc.execute("create table db_table_column_t(tid varchar(64),table_id varchar(64),tenant_id varchar(32),column_name varchar(64),column_comment varchar(200),data_type varchar(64),column_type varchar(64),length bigint,precision_length bigint,scale bigint,nullable int,default_value varchar(100),primary_key int,auto_increment int,is_unique int,indexed int,ordinal_position int,charset varchar(64),collation varchar(64),extra varchar(64),is_del int,updated_time timestamp,created_time timestamp)");
        for(int i=0;i<count;i++) f.jdbc.update("insert into db_table_column_t(tid,table_id,tenant_id,column_name,is_del) values(?,?,'ga','code',0)","column-"+i,"table-"+i);
        return f;
    }
    private List<Map<String,Object>> rows(int count) {
        List<Map<String,Object>> rows=new ArrayList<>();
        for(int i=0;i<count;i++) rows.add(Map.of("tid","table-"+i,"propList",Map.of("datasourceId","db","tableName","table-"+i,"tableComment","业务数据表"+i,"columns",List.of(Map.of("columnName","code","dataType","varchar","length",64)))));
        return rows;
    }
    private Object run(DatasourceCompletionMagicTest.Fixture f,DatasourceCompletionMagicTest.ScopeRuntime scope,IndexRuntime index,List<Map<String,Object>> rows,boolean dryRun) throws Exception {
        String script=CanonicalMagicSources.byId("db_table_batch_annotate_01").replaceAll("(?m)^import (?!cn\\.hutool\\.)[^\\r\\n]*\\r?\\n", "");
        var bindings=new LinkedHashMap<String,Object>();
        bindings.put("body",Map.of("tables",rows,"dryRun",dryRun,"refreshDatasource",false));
        bindings.put("db",f.db);bindings.put("tenantRuntime",scope);bindings.put("dataScope",scope);
        bindings.put("metadataAsset",f.metadata);bindings.put("esCommonService",index);
        try(var ignored=TenantContext.use("ga")) { return MagicScript.create(script,null).execute(new MagicScriptContext(bindings)); }
    }
    public static class IndexRuntime {
        int calls;
        public void bulkSaveWithIds(String index,List<String> ids,List<Map<String,Object>> docs){calls++;}
    }
}
