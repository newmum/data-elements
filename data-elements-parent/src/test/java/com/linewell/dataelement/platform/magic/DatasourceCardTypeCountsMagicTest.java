package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.script.*;

/** Execute the current card resource, including tenant selection and role filtering. */
class DatasourceCardTypeCountsMagicTest {
    @ParameterizedTest @ValueSource(ints = {20, 100})
    void registeredTypeCountsStayBatchedAndScoped(int count) throws Exception {
        var f = new DatasourceManagementPermissionsMagicTest.Fixture("ALL", 0);
        f.jdbc.update("delete from db_datasource_t");
        f.db.setResultProvider(new DefaultResultProvider("datasource-cards-validation"));
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
        f.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source-0'", "{\"accessMode\":\"receive\",\"pushDataType\":\"UNSTRUCTURED\"}");
        f.jdbc.update("update db_datasource_t set pool_cfg=? where tid='source-1'",
                "{\"accessMode\":\"upload\",\"reportRegistration\":{\"files\":[{},{}]}}");
        f.jdbc.update("update db_datasource_t set is_del=1 where tid='denied'");
        var scope = new Scope(); var es = new Index(count);
        String script = CanonicalMagicSources.byId("datapd_dept_datasources_01")
                .replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.util\\.)[^\\r\\n]*\\r?\\n", "");
        script = "var me=()=>{orgId:'org',orgName:'部门'}; var configData=()=>{es_dataassets:'assets'};\n" + script;
        Map<String,Object> bindings = new LinkedHashMap<>();
        bindings.put("body", Map.of("mode", "summary")); bindings.put("db", f.db);
        bindings.put("tableRead", f.tableRead);
        bindings.put("tenantRuntime", scope); bindings.put("dataScope", scope);
        bindings.put("tenantAccounts", scope); bindings.put("tenantEs", es);
        var compiled = MagicScript.create(script, null);
        f.counter.reads = 0;
        Map<?,?> result = (Map<?,?>) TenantContext.call("ga",
                () -> compiled.execute(new MagicScriptContext(bindings)));
        assertCounts(result, count);
        assertThat(f.counter.reads).isEqualTo(2); assertThat(es.reads).isEqualTo(2);
        assertThat(scope.calls).isEqualTo(1);
        System.out.println("datasource cards=" + count + ": SQL=2, ES=2, scope decisions=1; foreign/deleted sources excluded");
        // Historical default-mode callers must not inherit a positive orphan table from ES.
        f.counter.reads=0; es.reads=0; bindings.put("body", Map.of());
        result=(Map<?,?>) TenantContext.call("ga", () -> compiled.execute(new MagicScriptContext(bindings)));
        assertCounts(result, count);
        assertThat(f.counter.reads).isEqualTo(5); assertThat(es.reads).isEqualTo(2);
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
        public Map<String,Object> resource(String resource){calls++;return Map.of("scope","ALL","userId","person-a","organizationIds",List.of());}
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
}
