package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.script.*;

/** Execute the current card resource, including tenant selection and role filtering. */
class DatasourceCardTypeCountsMagicTest {
    @ParameterizedTest @ValueSource(ints = {20, 100})
    void registeredTypeCountsStayBatchedAndScoped(int count) throws Exception {
        var f = new DatasourceCompletionMagicTest.Fixture(0);
        f.jdbc.update("delete from db_datasource_t");
        for (String column : List.of("db_name", "remark", "app_id", "db_type", "database_type", "pool_cfg", "org_id", "created_by", "connection_status", "storage_domain", "node_id"))
            f.jdbc.execute("alter table db_datasource_t add " + column + " varchar(100)");
        f.jdbc.execute("alter table db_datasource_t add table_num int");
        f.jdbc.execute("alter table db_datasource_t add show_connect int");
        f.jdbc.execute("alter table db_datasource_t add created_time timestamp");
        f.jdbc.execute("create table sym_application_t(tid varchar(64),app_name varchar(100),is_del int)");
        f.jdbc.execute("create table rm_org_t(id varchar(64),name varchar(100),org_path varchar(100),deleted int)");
        for (String column : List.of("business_type", "table_name", "table_name_en", "table_name_cn", "table_comment", "table_type"))
            f.jdbc.execute("alter table db_table_t add " + column + " varchar(100)");
        f.jdbc.execute("alter table db_table_t add annotated int");
        f.jdbc.execute("alter table db_table_t add field_count int");
        f.jdbc.execute("alter table db_table_t add updated_time timestamp");
        f.jdbc.execute("alter table db_table_t add created_time timestamp");
        for (int i = 0; i < count; i++) {
            String id = "source-" + i;
            // Management cards include draft datasources too; do not add a source-status guard.
            f.jdbc.update("insert into db_datasource_t(tid,tenant_id,is_del,asset_status,db_name,table_num) values(?,'ga',0,?, ?,0)", id, i % 3, id);
            if (i == count - 1) continue; // Empty source has a stale ES table below.
            for (String type : List.of("业务表", "字典表", "日志表")) {
                for (Integer status : Arrays.asList(0, 1, 2, -1, null))
                    f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,business_type,annotated) values(?,'ga',?,?,0,?,1)", id + type + status, id, status, type);
                f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,business_type) values(?,'ga',?,1,1,?), (?,'other',?,2,0,?)", id + type + "deleted", id, type, id + type + "foreign", id, type);
            }
        }
        for (String id : List.of("denied", "foreign", "deleted")) {
            f.jdbc.update("insert into db_datasource_t(tid,tenant_id,is_del,asset_status,db_name) values(?,?,?,2,?)", id, id.equals("foreign") ? "other" : "ga", id.equals("deleted") ? 1 : 0, id);
            f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,business_type) values(?,?,?,2,0,'业务表')", id, id.equals("foreign") ? "other" : "ga", id);
        }
        var counter = new Counter(); f.db.setSqlInterceptors(List.of(counter));
        f.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source-0'", "{\"accessMode\":\"receive\",\"pushDataType\":\"UNSTRUCTURED\"}");
        f.jdbc.update("update db_datasource_t set is_del=1 where tid='denied'");
        var scope = new Scope(); var es = new Index(count);
        // H2 has no MySQL JSON projection functions. Simulate these two scalar projections;
        // the real tenant-scoped reads, DTO merge, counts and ES fallback are executed below.
        String script = CanonicalMagicSources.byId("datapd_dept_datasources_01")
                .replaceAll("(?m)^import [^\\r\\n]*\\r?\\n", "")
                .replaceAll("(?s)coalesce\\(\\s*nullif\\(json_unquote.*?\\) as accessMode", "'' as accessMode")
                .replaceAll("(?s)upper\\(coalesce\\(nullif\\(json_unquote.*?\\) as pushDataType", "case when d.tid='source-0' then 'UNSTRUCTURED' else 'STRUCTURED' end as pushDataType")
                .replaceAll("(?s)coalesce\\(json_length\\(json_extract.*?\\) as reportFileCount", "case when d.tid='source-1' then 2 else 0 end as reportFileCount");
        script = "var me=()=>{orgId:'org',orgName:'部门'}; var configData=()=>{es_dataassets:'assets'};\n" + script;
        Map<String,Object> bindings = new LinkedHashMap<>();
        bindings.put("body", Map.of("mode", "summary")); bindings.put("db", f.db);
        bindings.put("tenantRuntime", scope); bindings.put("dataScope", scope);
        bindings.put("tenantAccounts", scope); bindings.put("tenantEs", es);
        Map<?,?> result = (Map<?,?>)MagicScript.create(script, null).execute(new MagicScriptContext(bindings));
        assertCounts(result, count);
        assertThat(counter.reads).isEqualTo(3); assertThat(es.reads).isEqualTo(2);
        assertThat(scope.calls).isZero();
        System.out.println("datasource cards=" + count + ": SQL=3, ES=2; foreign/deleted sources excluded; menu role not queried");
        // Historical default-mode callers must not inherit a positive orphan table from ES.
        counter.reads=0; es.reads=0; bindings.put("body", Map.of());
        result=(Map<?,?>)MagicScript.create(script, null).execute(new MagicScriptContext(bindings));
        assertCounts(result, count);
        assertThat(counter.reads).isEqualTo(5); assertThat(es.reads).isEqualTo(2);
        System.out.println("legacy datasource cards=" + count + ": SQL=5, ES=2; orphan ES type counts cleared");
    }
    private void assertCounts(Map<?,?> response, int count) {
        List<?> rows=(List<?>)response.get("list"); assertThat(rows).hasSize(count);
        for (Object raw:rows) {
            Map<?,?> row=(Map<?,?>)raw; String id=String.valueOf(row.get("tid"));
            assertThat(id).startsWith("source-");
            assertThat(row.get("pushDataType")).isEqualTo(id.equals("source-0") ? "UNSTRUCTURED" : "STRUCTURED");
            assertThat(((Number)row.get("reportFileCount")).intValue()).isEqualTo(id.equals("source-1")?2:0);
            assertThat(row.containsKey("poolCfg")).isFalse();
            int expected=id.equals("source-"+(count-1)) ? 0 : 2;
            for (String key:List.of("businessTableNum","dictionaryTableNum","logTableNum"))
                assertThat(((Number)row.get(key)).intValue()).as(id+" "+key).isEqualTo(expected);
            if (expected > 0) {
                assertThat(((Number)row.get("tableNum")).intValue()).isEqualTo(15);
                assertThat(((Number)row.get("annotatedTableNum")).intValue()).isEqualTo(15);
                assertThat(((Number)row.get("registeredTableNum")).intValue()).isEqualTo(3);
            }
        }
    }
    public static class Scope {
        int calls;
        public String id(){return "ga";}
        public Map<String,String> names(List<?> ids){return Map.of();}
        public List<Map<String,Object>> visibleDataSourcesFor(String resource,List<Map<String,Object>> rows){
            assertThat(resource).isEqualTo("MENU:2081000000000000002"); calls++;
            return rows.stream().filter(row -> !"denied".equals(row.get("tid"))).toList();
        }
    }
    public static class Index {
        int reads; final int count;
        Index(int count){this.count=count;}
        public Index index(String name){return this;}
        public Map<String,Object> search(Map<?,?> query){
            reads++;
            Object hits=List.of();
            if (query.containsKey("sort") && !query.containsKey("track_total_hits"))
                hits=List.of(Map.of("_id","orphan","_source",new LinkedHashMap<>(Map.of("datasourceId","source-"+(count-1),"businessType","业务表","assetStatus",1))));
            return Map.of("hits",Map.of("hits",hits));
        }
    }
    static class Counter implements SQLInterceptor {
        int reads;
        @Override public void preHandle(BoundSql sql,RequestEntity request){if(sql.getSql().stripLeading().toLowerCase().startsWith("select")) reads++;}
    }
}
