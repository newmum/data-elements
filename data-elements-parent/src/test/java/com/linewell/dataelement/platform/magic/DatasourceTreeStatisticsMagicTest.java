package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

/** Execute the real tree resource: ES candidates must pass one tenant/role-scoped batch read. */
class DatasourceTreeStatisticsMagicTest {
    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void treeStatisticsRemainBatchedAndExcludeForeignDeniedDeletedAndStaleCandidates(int count) throws Exception {
        var fixture = new DatasourceManagementPermissionsMagicTest.Fixture("OWNER", count);
        var scope = new Scope();
        var index = new Index(count);
        String source = CanonicalMagicSources.byId("datapd_database_tree_statistics_01")
                .replaceAll("(?m)^import (?!java\\.util\\.)[^\\r\\n]*\\r?\\n", "");
        var script = MagicScript.create("var configData=()=>{es_dataassets:'assets'};\n" + source, null);
        var bindings = Map.<String, Object>of("body", Map.of("tenantId", "other", "scope", "ALL"),
                "tenantRuntime", scope, "dataScope", scope, "tenantEs", index,
                "db", fixture.db, "tableRead", fixture.tableRead);
        fixture.counter.reads = 0;
        fixture.counter.writes = 0;
        var result = (Map<?, ?>) TenantContext.call("ga", () -> script.execute(new MagicScriptContext(bindings)));

        assertThat(((Number) result.get("total")).intValue()).isEqualTo(count);
        assertThat(result.get("orgCounts")).isEqualTo(Map.of("dept-a", count));
        var appsByOrg = (Map<?, ?>) result.get("orgApps");
        var apps = (List<?>) appsByOrg.get("dept-a");
        assertThat(apps).hasSize(1);
        var sources = (List<?>) ((Map<?, ?>) apps.getFirst()).get("datasources");
        assertThat(sources).hasSize(count);
        assertThat(sources.stream().map(row -> (Map<?, ?>) row).map(row -> String.valueOf(row.get("value"))).toList())
                .containsExactlyInAnyOrderElementsOf(fixture.allowedIds());
        assertThat(sources).allMatch(row -> !((Map<?, ?>) row).get("label").equals("过期投影名称"));
        assertThat(fixture.counter.reads).isEqualTo(1);
        assertThat(fixture.counter.writes).isZero();
        assertThat(scope.calls).isEqualTo(1);
        assertThat(index.reads).isEqualTo(1);
        System.out.println("tree statistics " + count + " visible candidates: ES=1, SQL=1, scope decisions=1; mixed unauthorized and duplicate IDs excluded");
    }

    public static class Scope {
        int calls;
        public String id() { return "ga"; }
        public Map<String, Object> resource(String resource) {
            assertThat(resource).isEqualTo("MENU:2081000000000000002");
            calls++;
            return Map.of("scope", "OWNER", "userId", "person-a");
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
            for (int i = 0; i < count; i++) { hits.add(hit("own-" + i)); }
            for (String id : List.of("foreign", "dept-0", "other-0", "deleted", "stale", "own-0")) {
                hits.add(hit(id));
            }
            return Map.of("hits", Map.of("hits", hits));
        }
        private Map<String,Object> hit(String id) {
            return Map.of("_id", id, "_source", Map.of("tid", id, "orgId", "forged-org", "createdBy", "person-a",
                    "appId", "app", "appName", "索引系统名", "assetType", "db", "dbName", "过期投影名称"));
        }
    }
}
