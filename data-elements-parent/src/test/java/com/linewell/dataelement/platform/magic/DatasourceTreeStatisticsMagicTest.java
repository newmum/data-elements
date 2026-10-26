package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

/** Execute the real tree resource: ES candidates must pass one tenant/role-scoped batch read. */
class DatasourceTreeStatisticsMagicTest {
    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void treeStatisticsRemainBatchedAndExcludeForeignDeniedDeletedAndStaleCandidates(int count) throws Exception {
        var fixture = new DatasourceCompletionMagicTest.Fixture(0);
        fixture.jdbc.update("delete from db_datasource_t");
        for (String column : List.of("db_name", "db_type", "org_id", "app_id", "app_name")) {
            fixture.jdbc.execute("alter table db_datasource_t add " + column + " varchar(100)");
        }
        for (int i = 0; i < count; i++) {
            fixture.jdbc.update("insert into db_datasource_t(tid,tenant_id,is_del,asset_status,db_name,db_type,org_id,app_id,app_name) values(?,'ga',0,2,?,'mysql','org','app','真实系统')", "source-" + i, "数据库" + i);
        }
        fixture.jdbc.update("insert into db_datasource_t(tid,tenant_id,is_del,asset_status) values('foreign','other',0,2),('denied','ga',0,2),('deleted','ga',1,2)");
        fixture.jdbc.update("update db_datasource_t set is_del=1 where tid='denied'");
        var counter = new Counter();
        fixture.db.setSqlInterceptors(List.of(counter));
        var scope = new Scope();
        var index = new Index(count);
        String source = CanonicalMagicSources.byId("datapd_database_tree_statistics_01")
                .replaceAll("(?m)^import [^\\r\\n]*\\r?\\n", "");
        source = "var configData=()=>{es_dataassets:'assets'};\n" + source;
        var bindings = new LinkedHashMap<String,Object>();
        bindings.put("body", Map.of("tenantId", "other"));
        bindings.put("tenantRuntime", scope);
        bindings.put("dataScope", scope);
        bindings.put("tenantEs", index);
        bindings.put("db", fixture.db);
        Map<?,?> result = (Map<?,?>) MagicScript.create(source, null).execute(new MagicScriptContext(bindings));

        assertThat(((Number) result.get("total")).intValue()).isEqualTo(count);
        assertThat(result.get("orgCounts")).isEqualTo(Map.of("org", count));
        assertThat(counter.reads).isEqualTo(1);
        assertThat(scope.calls).isZero();
        assertThat(index.reads).isEqualTo(1);
        System.out.println("tree statistics " + count + " candidates: ES=1, SQL=1, role lookups=0; foreign/deleted/stale candidates excluded");
    }

    public static class Scope {
        int calls;
        public String id() { return "ga"; }
        public List<Map<String,Object>> visibleDataSourcesFor(String resource, List<Map<String,Object>> rows) {
            assertThat(resource).isEqualTo("MENU:2081000000000000002");
            assertThat(rows).allMatch(row -> "ga".equals(row.get("tenantId")));
            calls++;
            return rows.stream().filter(row -> !"denied".equals(row.get("tid"))).toList();
        }
    }

    public static class Index {
        int reads;
        final int count;
        Index(int count) { this.count = count; }
        public Index index(String name) { return this; }
        public Map<String,Object> search(Map<?,?> query) {
            reads++;
            assertThat(query.toString()).contains("validation_*");
            List<Map<String,Object>> hits = new ArrayList<>();
            for (int i = 0; i < count; i++) { hits.add(hit("source-" + i)); }
            for (String id : List.of("foreign", "denied", "deleted", "stale")) { hits.add(hit(id)); }
            return Map.of("hits", Map.of("hits", hits));
        }
        private Map<String,Object> hit(String id) {
            return Map.of("_id", id, "_source", Map.of("tid", id, "orgId", "forged-org", "assetType", "db", "dbName", "过期投影名称"));
        }
    }

    static class Counter implements SQLInterceptor {
        int reads;
        @Override public void preHandle(BoundSql sql, RequestEntity request) {
            if (sql.getSql().stripLeading().toLowerCase().startsWith("select")) { reads++; }
        }
    }
}
