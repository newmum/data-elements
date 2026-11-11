package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class DatasourceCardsPagingMagicTest {
    private static final String SCRIPT = "datapd_dept_datasources_01";
    private static final String RESOURCE = "MENU:2081000000000000002";

    @Test
    void readsStablePagesBeyondTheLegacyThousandWithBoundedQueries() throws Exception {
        var fixture = new Fixture();
        var sources = new ArrayList<Object[]>();
        for (int index = 0; index < 1_005; index++) {
            String id = String.format(Locale.ROOT, "source-%04d", index);
            sources.add(new Object[]{id, "ga", 0, id, "mysql", "dept-a", "person-a", "app-a", 2, 1, "{}"});
        }
        fixture.insertSources(sources);
        fixture.source.jdbc.update("UPDATE db_datasource_t SET updated_time=TIMESTAMP '2026-04-02 00:00:00' WHERE tid='source-1004'");

        var twenty = fixture.cards(Map.of("pageSize", 20));
        assertThat(number(twenty, "total")).isEqualTo(1_005);
        assertThat(ids(twenty)).hasSize(20).startsWith("source-1004", "source-0000", "source-0001")
                .endsWith("source-0018").doesNotHaveDuplicates();
        int smallSql = fixture.source.counter.reads;
        int smallEs = fixture.index.reads;
        assertThat(smallSql).isBetween(1, 4);
        assertThat(smallEs).isEqualTo(1);
        assertThat(fixture.index.lastPageIds).containsExactlyInAnyOrderElementsOf(
                ids(twenty).stream().map(String::valueOf).toList());

        var hundred = fixture.cards(Map.of("pageSize", 100));
        assertThat(ids(hundred)).hasSize(100).doesNotHaveDuplicates();
        assertThat(ids(hundred).subList(0, 20)).isEqualTo(ids(twenty));
        assertThat(fixture.source.counter.reads).isEqualTo(smallSql);
        assertThat(fixture.index.reads).isEqualTo(smallEs);
        assertThat(fixture.index.lastPageIds).containsExactlyInAnyOrderElementsOf(
                ids(hundred).stream().map(String::valueOf).toList());
        assertThat(fixture.scope.decisions).isEqualTo(1);
        assertThat(fixture.source.counter.writes).isZero();
        assertThat(rows(hundred)).allSatisfy(row -> assertThat(row.keySet())
                .doesNotContain("poolCfg", "pool_cfg", "password", "jdbcUrl", "tables"));

        var last = fixture.cards(Map.of("pageNum", 11, "pageSize", 100));
        assertThat(number(last, "total")).isEqualTo(1_005);
        assertThat(number(last, "pageNum")).isEqualTo(11);
        assertThat(number(last, "pageSize")).isEqualTo(100);
        assertThat(ids(last)).containsExactly("source-0999", "source-1000", "source-1001", "source-1002", "source-1003")
                .doesNotContainAnyElementsOf(ids(hundred));
        assertThat(ids(fixture.cards(Map.of("pageNum", 11, "pageSize", 100)))).isEqualTo(ids(last));

        var choices = fixture.choices(Map.of("pageNum", 11, "pageSize", 100));
        assertThat(number(choices, "total")).isEqualTo(1_005);
        assertThat(ids(choices)).isEqualTo(ids(last));
        assertThat(fixture.index.reads).isZero();
        assertThat(fixture.source.counter.reads).isEqualTo(2);
        System.out.printf("datasource card page 20/100: SQL=%d/%d, ES=%d/%d; total=1005 and page 11 verified%n",
                smallSql, smallSql, smallEs, smallEs);

        Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
        for (int index = 0; index < 505; index++) {
            changes.put(String.format(Locale.ROOT, "source-%04d", index), changeBuckets(1, 0, 0));
        }
        changes.put("source-1004", changeBuckets(0, 1, 0));
        changes.put("foreign", changeBuckets(200, 0, 0));
        changes.put("deleted", changeBuckets(200, 0, 0));
        fixture.index.changes = changes;

        var changedTwenty = fixture.cards(Map.of("filter", "changed", "pageSize", 20));
        assertThat(number(changedTwenty, "total")).isEqualTo(506);
        // The most recently updated source is in the second ES composite page.
        assertThat(ids(changedTwenty)).hasSize(20).startsWith("source-1004");
        assertThat(fixture.source.counter.reads).isEqualTo(4);
        assertThat(fixture.index.reads).isEqualTo(2);
        var changedHundred = fixture.cards(Map.of("filter", "changed", "pageSize", 100));
        assertThat(ids(changedHundred)).hasSize(100);
        assertThat(ids(changedHundred).subList(0, 20)).isEqualTo(ids(changedTwenty));
        assertThat(fixture.source.counter.reads).isEqualTo(4);
        assertThat(fixture.index.reads).isEqualTo(2);
        System.out.println("changed datasource cards across two ES windows: SQL=4/4, ES=2/2 for page 20/100");
    }

    @Test
    void filtersUseCurrentScopeAndProxyServicesUseIndependentServiceScope() throws Exception {
        var fixture = new Fixture();
        fixture.seedMixedCards();
        var forgedBody = Map.<String, Object>of("tenantId", "foreign", "scope", "ALL", "userId", "person-b");

        assertThat(ids(fixture.cards(forgedBody))).containsExactlyInAnyOrder("incomplete", "complete", "draft", "proxy");
        fixture.scope.policy = "ORG";
        assertThat(ids(fixture.cards(forgedBody))).containsExactlyInAnyOrder("incomplete", "complete", "proxy");
        assertThat(ids(fixture.cards(Map.of("orgIds", List.of("dept-b"))))).isEmpty();
        var proxy = fixture.cards(Map.of("appId", "app-a", "typeKey", "mode:proxy"));
        assertThat(ids(proxy)).containsExactly("proxy");
        assertThat(proxy).containsEntry("orgId", "dept-a").containsEntry("orgName", "本部门");
        assertThat(number(rows(proxy).getFirst(), "serviceCount")).isEqualTo(1);
        assertThat(fixture.scope.serviceDecisions).isEqualTo(1);
        fixture.scope.policy = "OWNER";
        assertThat(ids(fixture.cards(forgedBody))).containsExactlyInAnyOrder("incomplete", "draft");
        assertThat(ids(fixture.cards(Map.of("appId", "app-a", "keyword", "incom")))).containsExactly("incomplete");
        assertThat(ids(fixture.cards(Map.of("keyword", "source_schema")))).containsExactly("incomplete");
        assertThat(ids(fixture.cards(Map.of("appId", "app-b", "typeKey", "mysql")))).containsExactly("draft");
        assertThat(number(fixture.cards(Map.of("typeKey", "mode:proxy")), "total")).isZero();
    }

    @Test
    void statusFiltersAndGlobalMetricsAreCalculatedBeforePagingInsideScope() throws Exception {
        var fixture = new Fixture();
        fixture.seedMixedCards();

        var incomplete = fixture.cards(Map.of("filter", "incomplete"));
        assertThat(ids(incomplete)).containsExactly("incomplete");
        var card = rows(incomplete).getFirst();
        assertThat(number(card, "tableNum")).isEqualTo(3);
        assertThat(number(card, "unannotatedTableNum")).isEqualTo(2);
        assertThat(number(card, "businessTableNum")).isEqualTo(1);
        assertThat(ids(fixture.cards(Map.of("filter", "completed")))).containsExactlyInAnyOrder("complete", "proxy");
        assertThat(ids(fixture.cards(Map.of("filter", "unannotated")))).containsExactlyInAnyOrder("incomplete", "draft");
        var metrics = fixture.metrics(Map.of("pageSize", 1, "filter", "completed"));
        assertThat(number(metrics, "datasourceTotal")).isEqualTo(4);
        assertThat(number(metrics, "completedTotal")).isEqualTo(2);
        assertThat(number(metrics, "incompleteDatasourceTotal")).isEqualTo(1);
        assertThat(number(metrics, "draftDatasourceTotal")).isEqualTo(1);
        assertThat(number(metrics, "pendingDatasourceTotal")).isEqualTo(2);
        assertThat(number(metrics, "tableTotal")).isEqualTo(6);
        assertThat(number(metrics, "annotatedTableTotal")).isEqualTo(3);
        assertThat(number(metrics, "unannotatedTotal")).isEqualTo(3);
        fixture.scope.policy = "OWNER";
        assertThat(ids(fixture.cards(Map.of("filter", "completed")))).isEmpty();
        assertThat(ids(fixture.cards(Map.of("filter", "unannotated")))).containsExactlyInAnyOrder("incomplete", "draft");
        metrics = fixture.metrics(Map.of("tenantId", "foreign", "scope", "ALL"));
        assertThat(number(metrics, "datasourceTotal")).isEqualTo(2);
        assertThat(number(metrics, "completedTotal")).isZero();
        assertThat(number(metrics, "unannotatedTotal")).isEqualTo(3);
    }

    @Test
    void changeIndexCannotAddUnauthorizedCardsOrTurnAnOutageIntoAnEmptyResult() throws Exception {
        var fixture = new Fixture();
        fixture.seedMixedCards();
        fixture.index.changes = Map.of(
                "incomplete", changeBuckets(1, 0, 0), "complete", changeBuckets(0, 2, 0),
                "foreign", changeBuckets(100, 0, 0), "deleted", changeBuckets(0, 0, 50));

        assertThat(ids(fixture.cards(Map.of("filter", "changed"))))
                .containsExactlyInAnyOrder("incomplete", "complete");
        var first = fixture.cards(Map.of("filter", "changed", "pageSize", 1));
        assertThat(ids(first)).containsExactly("complete");
        assertThat(first.get("nextCursor")).isNotNull();
        var second = fixture.cards(Map.of("filter", "changed", "pageNum", 2, "pageSize", 1,
                "cursor", first.get("nextCursor")));
        assertThat(ids(second)).containsExactly("incomplete");
        assertThat(number(second, "total")).isEqualTo(2);
        assertThat(number(fixture.metrics(Map.of()), "changedTotal")).isEqualTo(3);
        fixture.scope.policy = "OWNER";
        assertThat(ids(fixture.cards(Map.of("filter", "changed", "scope", "ALL")))).containsExactly("incomplete");
        assertThat(number(fixture.metrics(Map.of()), "changedTotal")).isEqualTo(1);

        fixture.index.unavailable = true;
        var ordinary = fixture.cards(Map.of());
        assertThat(number(ordinary, "total")).isEqualTo(2);
        assertThat(ordinary.get("changesAvailable")).isEqualTo(false);
        var degradedMetrics = StructuredValues.objectMap(fixture.execute("cardMetrics", Map.of()));
        assertThat(degradedMetrics.get("changesAvailable")).isEqualTo(false);
        var metrics = StructuredValues.objectMap(degradedMetrics.get("metrics"));
        assertThat(number(metrics, "datasourceTotal")).isEqualTo(2);
        assertThat(metrics).containsEntry("changedTotal", null);
        Object changed = fixture.execute("cards", Map.of("filter", "changed"));
        assertThat(changed).isInstanceOfSatisfying(ExitValue.class,
                exit -> assertThat(exit.getValues()[0]).isEqualTo(503));
    }

    private static Map<String, Object> changeBuckets(int added, int modified, int deleted) {
        return Map.of("buckets", Map.of("added", Map.of("doc_count", added),
                "modified", Map.of("doc_count", modified), "deleted", Map.of("doc_count", deleted)));
    }

    private static List<Map<String, Object>> rows(Map<String, Object> page) {
        return StructuredValues.objectRows(page.get("list"));
    }

    private static List<Object> ids(Map<String, Object> page) {
        return rows(page).stream().map(row -> row.get("tid")).toList();
    }

    private static int number(Map<String, Object> row, String key) {
        assertThat(row.get(key)).as(key).isInstanceOf(Number.class);
        return ((Number) row.get(key)).intValue();
    }

    public static final class Scope {
        String policy = "ALL";
        int decisions;
        int serviceDecisions;

        public String id() {
            return TenantContext.requireTenantId();
        }

        public Map<String, Object> resource(String code) {
            assertThat(code).isEqualTo(RESOURCE);
            decisions++;
            return Map.of("scope", policy, "userId", "person-a", "organizationIds", List.of("dept-a"));
        }

        public Map<String, Object> dataSource() {
            serviceDecisions++;
            return Map.of("scope", "OWNER", "userId", "person-a", "organizationIds", List.of("dept-a"));
        }

        public Map<String, String> names(List<?> ids) {
            assertThat(ids).hasSizeLessThanOrEqualTo(100);
            return Map.of("person-a", "登记人员 A", "person-b", "登记人员 B");
        }
    }

    public static final class ChangeIndex {
        int reads;
        boolean unavailable;
        Map<String, Map<String, Object>> changes = Map.of();
        Set<String> lastPageIds = Set.of();

        public ChangeIndex index(String name) {
            assertThat(name).isEqualTo("assets");
            return this;
        }

        public Map<String, Object> search(Map<?, ?> query) {
            reads++;
            if (unavailable) {
                throw new IllegalStateException("Validation index unavailable");
            }
            var aggregations = StructuredValues.objectMap(query.get("aggs"));
            if (aggregations.containsKey("bySource")) {
                var bySource = StructuredValues.objectMap(aggregations.get("bySource"));
                var filters = StructuredValues.objectMap(StructuredValues.objectMap(bySource.get("filters")).get("filters"));
                lastPageIds = Set.copyOf(filters.keySet());
                Map<String, Object> buckets = new LinkedHashMap<>();
                for (String id : lastPageIds) {
                    buckets.put(id, Map.of("changes", changes.getOrDefault(id, changeBuckets(0, 0, 0))));
                }
                return Map.of("aggregations", Map.of("bySource", Map.of("buckets", buckets)));
            }
            var changedSources = StructuredValues.objectMap(aggregations.get("changedSources"));
            var composite = StructuredValues.objectMap(changedSources.get("composite"));
            assertThat(number(composite, "size")).isEqualTo(500);
            var after = StructuredValues.objectMap(composite.get("after"));
            String lastId = after == null ? "" : String.valueOf(after.get("sourceId"));
            var window = changes.entrySet().stream().filter(entry -> entry.getKey().compareTo(lastId) > 0)
                    .sorted(Map.Entry.comparingByKey()).limit(500).toList();
            var buckets = window.stream().map(entry -> Map.of(
                    "key", Map.of("sourceId", entry.getKey()), "changes", entry.getValue())).toList();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("buckets", buckets);
            if (window.size() == 500) {
                response.put("after_key", Map.of("sourceId", window.getLast().getKey()));
            }
            return Map.of("aggregations", Map.of("changedSources", response));
        }
    }

    private static final class Fixture {
        final DatasourceManagementPermissionsMagicTest.Fixture source = new DatasourceManagementPermissionsMagicTest.Fixture("ALL", 0);
        final Scope scope = new Scope();
        final ChangeIndex index = new ChangeIndex();
        final MagicScript script;

        Fixture() throws Exception {
            source.db.setResultProvider(new DefaultResultProvider("datasource-card-paging-validation"));
            source.jdbc.execute("CREATE TABLE api_info_t(tid VARCHAR,tenant_id VARCHAR,datasource_id VARCHAR,org_id VARCHAR,created_by VARCHAR,is_del INT)");
            String canonical = CanonicalMagicSources.byId(SCRIPT)
                    .replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.util\\.)[^\\r\\n]*\\r?\\n", "");
            script = MagicScript.create("""
                    var me = () => {
                        if (body?.appId != null) {
                            throw new IllegalStateException('业务系统筛选不能用于当前会话的角色查询')
                        }
                        return {orgId:'dept-a', orgName:'本部门'}
                    }
                    var configData = () => {es_dataassets:'assets'}
                    """ + canonical, null);
        }

        Map<String, Object> cards(Map<String, Object> options) {
            return run("cards", options);
        }

        Map<String, Object> metrics(Map<String, Object> options) {
            return StructuredValues.objectMap(run("cardMetrics", options).get("metrics"));
        }

        Map<String, Object> choices(Map<String, Object> options) {
            return run("choices", options);
        }

        private Map<String, Object> run(String mode, Map<String, Object> options) {
            return StructuredValues.objectMap(execute(mode, options));
        }

        private Object execute(String mode, Map<String, Object> options) {
            source.counter.reads = 0;
            source.counter.writes = 0;
            scope.decisions = 0;
            scope.serviceDecisions = 0;
            index.reads = 0;
            index.lastPageIds = Set.of();
            var body = new LinkedHashMap<>(options);
            body.put("mode", mode);
            var inputs = Map.<String, Object>of("body", body, "db", source.db, "tenantRuntime", scope,
                    "dataScope", scope, "tenantAccounts", scope, "tenantEs", index,
                    "tableSort", new TableSortMagicModule(), "tableRead", source.tableRead);
            return TenantContext.call("ga", () -> script.execute(new MagicScriptContext(inputs)));
        }

        private void insertSources(List<Object[]> sources) {
            source.jdbc.batchUpdate("""
                    INSERT INTO db_datasource_t(tid,tenant_id,is_del,db_name,db_type,org_id,created_by,app_id,
                        asset_status,table_num,pool_cfg,created_time,show_connect)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,TIMESTAMP '2026-04-01 00:00:00',1)
                    """, sources);
        }

        private void seedMixedCards() {
            insertSources(List.of(
                    new Object[]{"incomplete", "ga", 0, "incomplete", "mysql", "dept-a", "person-a", "app-a", 1, 3, "{\"schema\":\"source_schema\"}"},
                    new Object[]{"complete", "ga", 0, "complete", "mysql", "dept-a", "person-b", "app-a", 2, 1, "{}"},
                    new Object[]{"draft", "ga", 0, "draft", "mysql", "dept-b", "person-a", "app-b", 0, 0, "{}"},
                    new Object[]{"proxy", "ga", 0, "proxy", "api", "dept-a", "person-b", "app-a", 2, 0, "{\"accessMode\":\"proxy\"}"}));
            source.jdbc.update("INSERT INTO sym_application_t VALUES ('app-a','ga','系统 A',0), ('app-b','ga','系统 B',0)");
            source.jdbc.update("""
                    INSERT INTO db_table_t(tid,tenant_id,datasource_id,is_del,asset_status,annotated,business_type) VALUES
                    ('incomplete-1','ga','incomplete',0,2,1,'业务表'),('incomplete-2','ga','incomplete',0,0,0,'业务表'),
                    ('complete-1','ga','complete',0,2,1,'日志表'),('complete-2','ga','complete',0,2,1,'字典表'),
                    ('draft-1','ga','draft',0,0,0,'业务表'),('foreign-table','foreign','proxy',0,2,0,'业务表'),
                    ('deleted-table','ga','proxy',1,2,0,'业务表')
                    """);
            source.jdbc.update("""
                    INSERT INTO api_info_t VALUES ('own','ga','proxy','dept-a','person-a',0),
                    ('other-owner','ga','proxy','dept-a','person-b',0), ('foreign','foreign','proxy','dept-a','person-a',0),
                    ('deleted','ga','proxy','dept-a','person-a',1)
                    """);
        }
    }
}
