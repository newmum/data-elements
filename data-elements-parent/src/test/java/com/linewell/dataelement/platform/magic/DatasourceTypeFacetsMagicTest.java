package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

/** Run the full aggregation against actual SQL, including sources outside both old hits limits. */
class DatasourceTypeFacetsMagicTest {
    @ParameterizedTest @CsvSource({"ALL,20", "ALL,100", "ORG,20", "ORG,100", "OWNER,20", "OWNER,100", "ALL,3501"})
    void fullCountsIgnoreOverviewAndEsCandidateLimits(String policy, int size) throws Exception {
        var f = fixture(policy, size);
        var result = run(f, Map.of("mode", "facets", "tenantId", "foreign", "scope", "ALL"), f.index);
        int expected = f.allowedIds().size();
        assertThat(result.get("complete")).isEqualTo(true);
        assertThat(number(result.get("total"))).isEqualTo(expected);
        assertThat(number(((Map<?,?>)result.get("typeCounts")).get("mysql"))).isEqualTo(expected);
        assertThat(f.counter.reads).isEqualTo(1);
        assertThat(f.index.reads).isZero();
        assertThat(f.scope.decisions).isEqualTo(1);
        assertThat(result.containsKey("list") || result.containsKey("datasources") || result.containsKey("poolCfg")).isFalse();
        System.out.println(policy+" full facets "+expected+" sources: SQL=1, ES=0; no overview or ES hits limit");
    }

    @Test void filtersAndAliasesAreScopedBeforeAggregation() throws Exception {
        var f = fixture("OWNER", 20);
        for(String hidden:List.of("validationXsource","validation_source"))
            f.jdbc.update("insert into db_datasource_t(tid,tenant_id,is_del,db_name,db_type,org_id,created_by,asset_status,pool_cfg) values(?,'ga',0,?,'oracle','dept-a','person-a',2,'{}')",hidden,hidden);
        f.jdbc.update("update db_datasource_t set db_type='KingbaseES',asset_status=1 where tid='own-0'");
        f.jdbc.update("update db_datasource_t set db_type='api',pool_cfg=? where tid='own-1'", "{\"accessMode\":\"receive\"}");
        f.jdbc.update("update db_datasource_t set db_type='api',pool_cfg=? where tid='own-2'", "{\"accessMode\":\"upload\"}");
        f.jdbc.update("update db_datasource_t set db_type='api',pool_cfg=?,show_connect=0 where tid='own-3'", "{\"accessMode\":\"proxy\"}");
        f.jdbc.update("update db_datasource_t set db_type='api',show_connect=0 where tid='own-4'");
        f.jdbc.update("update db_datasource_t set pool_cfg=? where tid='own-5'", "{invalid JSON");
        f.jdbc.update("update db_datasource_t set pool_cfg=? where tid='own-6'", "{\"database\":\"schema_%validation\"}");
        var all = run(f, Map.of("mode","facets"), f.index);
        var counts = (Map<?,?>)all.get("typeCounts");
        assertThat(number(all.get("total"))).isEqualTo(20);
        assertThat(number(counts.get("kingbase8"))).isEqualTo(1);
        assertThat(number(counts.get("mode:receive"))).isEqualTo(1);
        assertThat(number(counts.get("mode:upload"))).isEqualTo(2);
        assertThat(number(counts.get("mode:proxy"))).isEqualTo(1);
        assertThat(number(run(f,Map.of("mode","facets","filter","incomplete"),f.index).get("total"))).isEqualTo(1);
        assertThat(number(run(f,Map.of("mode","facets","keyword","own-0"),f.index).get("total"))).isEqualTo(1);
        assertThat(number(run(f,Map.of("mode","facets","keyword","%"),f.index).get("total"))).isEqualTo(1);
        assertThat(number(run(f,Map.of("mode","facets","keyword","schema_%"),f.index).get("total"))).isEqualTo(1);
        assertThat(number(run(f,Map.of("mode","facets","keyword","schemaX"),f.index).get("total"))).isZero();
        assertThat(number(run(f,Map.of("mode","facets","orgIds",List.of("dept-b")),f.index).get("total"))).isZero();
        assertThat(number(run(f,Map.of("mode","facets","appId","missing"),f.index).get("total"))).isZero();
        f.jdbc.update("update db_datasource_t set table_num=10 where tid='own-0'");
        f.jdbc.update("""
                insert into db_table_t(tid,tenant_id,datasource_id,is_del,annotated) values
                  ('validation-annotated','ga','own-7',0,1),
                  ('validation-unannotated','ga','own-7',0,0),
                  ('validation-foreign','foreign','own-8',0,0),
                  ('validation-deleted','ga','own-8',1,0)
                """);
        assertThat(number(run(f,Map.of("mode","facets","filter","unannotated"),f.index).get("total"))).isEqualTo(2);
        assertThat(f.counter.reads).isEqualTo(1);
        var denied = raw(f, Map.of("mode","facets","orgIds",Collections.nCopies(1001,"dept-a")), f.index);
        assertThat(denied).isInstanceOf(ExitValue.class);
        assertThat(f.counter.writes).isZero();
    }

    @ParameterizedTest @CsvSource({"20", "100", "1101"})
    void changedBucketsUseBoundedPagesAndValidateEveryId(int count) throws Exception {
        var f=fixture("OWNER",count);
        var ids=new ArrayList<String>();
        for(int i=0;i<count;i++)ids.add("own-"+i);
        ids.addAll(List.of("dept-0","foreign","deleted","missing"));
        var index=new CompositeIndex(ids);
        var result=run(f,Map.of("mode","facets","filter","changed"),index);
        assertThat(number(result.get("total"))).isEqualTo(count);
        assertThat(f.counter.reads).isEqualTo((ids.size()+499)/500);
        assertThat(index.reads).isEqualTo((ids.size()+499)/500);
        assertThat(f.scope.decisions).isEqualTo(1);
        System.out.println("changed "+count+" sources: SQL="+f.counter.reads+", ES="+index.reads+"; 500 IDs/page, foreign/denied/deleted/missing excluded");
    }

    @Test void changedPageFailureDoesNotReturnPartialCounts() throws Exception {
        var f = fixture("ALL", 1);
        var invalid = new CompositeIndex(List.of()) {
            @Override public Map<String,Object> search(Map<?,?> query) {
                return Map.of("timed_out",true);
            }
        };
        var reply = (ExitValue)raw(f, Map.of("mode","facets","filter","changed"),invalid);
        assertThat(reply.getValues()[0]).isEqualTo(503);
        assertThat(f.counter.reads).isZero();
    }

    private static int number(Object value){return value==null?0:((Number)value).intValue();}
    private static DatasourceManagementPermissionsMagicTest.Fixture fixture(String policy,int size) throws Exception {
        return new DatasourceManagementPermissionsMagicTest.Fixture(policy,size);
    }
    static void registerJsonFunctions(JdbcTemplate jdbc) {
        for(var function:List.of("jsonValid","jsonExtract","jsonUnquote")) {
            String sqlName=function.replace("json","JSON_").toUpperCase();
            jdbc.execute("CREATE ALIAS "+sqlName+" FOR '"+JsonFunctions.class.getName()+"."+function+"'");
        }
    }
    private static Map<?,?> run(DatasourceManagementPermissionsMagicTest.Fixture f,Map<String,Object> body,Object index) throws Exception {
        return (Map<?,?>)raw(f,body,index);
    }
    private static Object raw(DatasourceManagementPermissionsMagicTest.Fixture f,Map<String,Object> body,Object index) throws Exception {
        f.counter.reads=0;f.counter.writes=0;f.scope.decisions=0;f.index.reads=0;
        var script=CanonicalMagicSources.byId("datapd_database_tree_statistics_01")
            .replaceAll("(?m)^import (?!java\\.util\\.)[^\\r\\n]*\\r?\\n","");
        var vars=new HashMap<String,Object>();vars.put("db",f.db);vars.put("body",body);
        vars.put("tenantRuntime",f.scope);vars.put("dataScope",f.scope);vars.put("tenantEs",index);
        vars.put("tableRead",f.tableRead);
        var compiled=MagicScript.create("var configData=()=>{es_dataassets:'assets'};\n"+script,null);
        return TenantContext.call("ga", () -> compiled.execute(new MagicScriptContext(vars)));
    }
    public static class JsonFunctions {
        private static final ObjectMapper JSON=new ObjectMapper();
        public static boolean jsonValid(String value){try{return value!=null&&JSON.readTree(value)!=null;}catch(Exception e){return false;}}
        public static String jsonExtract(String value,String path)throws Exception {
            if (value==null) return null;
            var node=JSON.readTree(value).get(path.substring(2));
            return node==null?null:node.toString();
        }
        public static String jsonUnquote(String value)throws Exception {
            if (value==null) return null;
            var node=JSON.readTree(value);
            return node.isTextual()?node.textValue():node.toString();
        }
    }
    public static class CompositeIndex {
        final List<String> ids;int reads;
        CompositeIndex(List<String> ids){this.ids=ids;}
        public CompositeIndex index(String ignored){return this;}
        public Map<String,Object> search(Map<?,?> query){
            reads++;assertThat(query.get("size")).isEqualTo(0);
            var composite=(Map<?,?>)((Map<?,?>)((Map<?,?>)query.get("aggs")).get("changedSources")).get("composite");
            assertThat(composite.get("size")).isEqualTo(500);
            int start=composite.get("after") instanceof Map<?,?> after?Integer.parseInt(after.get("offset").toString()):0;
            int end=Math.min(start+500,ids.size());
            var buckets=ids.subList(start,end).stream().map(id->Map.of("key",Map.of("sourceId",id),"doc_count",1)).toList();
            return Map.of("aggregations",Map.of("changedSources",Map.of("buckets",buckets,"after_key",Map.of("offset",end))));
        }
    }
}
