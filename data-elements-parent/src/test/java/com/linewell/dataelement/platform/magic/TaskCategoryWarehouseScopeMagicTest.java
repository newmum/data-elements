package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.dialect.*;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.script.*;
import org.ssssssss.script.runtime.ExitValue;

/** Executes the editable resources against real SQL, including mixed tenant IDs and legacy menu scope. */
class TaskCategoryWarehouseScopeMagicTest {
    private static final String INGEST_PAGE = "76fd211790f54164947f276824d19c1d";
    private static final String DISTRIBUTE_PAGE = "1f475a245fda4ba4a885b3ac4f8e2d59";
    private static final String INGEST_TREE = "ods_data_agg_tree_statistics_01";
    private static final String DISTRIBUTE_TREE = "2b8dd89d130a4738a8aa1d0c8584e401";

    @ParameterizedTest @ValueSource(ints = {20, 100})
    void ingestionSortsTheWholeAuthorizedResultBeforePagingAndPreservesExplicitSort(int pageSize) throws Exception {
        int count = 2 * pageSize + 5;
        var f = fixture(count);
        var base = java.time.Instant.parse("2026-01-01T00:00:00Z");
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var values = new LinkedHashMap<String,Object>();
            values.put("updated_time", i >= count - 2 ? null : java.util.Date.from(base.plusSeconds(i / 2)));
            values.put("access_request_status", 1);
            // 仅准备测试数据；业务排序仍执行规范目录中的完整Magic接口。
            MagicScript.create("db.table('db_table_t').withBlank().where().eq('tenant_id','ga').eq('tid',id).update(values)",null)
                    .execute(new MagicScriptContext(Map.of("db",f.db,"id","table-"+i,"values",values)));
            expected.add("table-"+i);
        }
        expected.sort(Comparator.<String>comparingInt(id -> {
            int i = Integer.parseInt(id.substring(6)); return i >= count - 2 ? Integer.MAX_VALUE : -(i / 2);
        }).thenComparing(Comparator.naturalOrder()));
        var counter = new Counter(); f.db.setSqlInterceptors(List.of(counter));
        for (var filter : List.of(Map.<String,Object>of(), Map.<String,Object>of("keyword","table-"), Map.<String,Object>of("accessStatus","2"))) {
            List<String> actual = new ArrayList<>();
            for (int page = 1; page <= 3; page++) {
                var body = new LinkedHashMap<String,Object>(filter);
                body.putAll(Map.of("viewLevel","table","datasourceIds",List.of("db","denied","foreign"),"pageNum",page,"pageSize",pageSize));
                counter.reads = 0;
                var result = run(f, INGEST_PAGE, body);
                assertThat(((Number)result.get("total")).intValue()).isEqualTo(count);
                for (Object value : (List<?>)result.get("list")) {
                    var row = (Map<?,?>)value;
                    assertThat(row.get("datasourceId")).isEqualTo("db");
                    actual.add(row.get("tid").toString());
                }
                assertThat(counter.reads).isEqualTo(filter.containsKey("accessStatus") ? 8 : 7);
            }
            assertThat(actual).containsExactlyElementsOf(expected);
        }
        var ascending = run(f, INGEST_PAGE, Map.of("viewLevel","table","datasourceIds",List.of("db"),
                "pageSize",pageSize,"sortField","tableName","sortDir","asc"));
        assertThat(((List<?>)ascending.get("list")).stream().map(value -> ((Map<?,?>)value).get("tableName").toString()).toList())
                .containsExactlyElementsOf(expected.stream().sorted().limit(pageSize).toList());
        assertThatThrownBy(() -> run(f, INGEST_PAGE, Map.of("viewLevel","table","sortField","untrusted-column")))
                .hasMessageContaining("不支持的排序字段");
        // 同租户目标不再受数据源菜单范围过滤；任务监控时间和异租户画布不能提升根表排序。
        insertTask(f,"recent-root","table-0","warehouse-0",List.of("warehouse-0"));
        insertTask(f,"recent-hop","warehouse-0","raw-0",List.of("raw-0"));
        insertTask(f,"monitor-only","table-2","warehouse-2",List.of("warehouse-2"));
        insertTask(f,"null-table-time","table-"+(count-1),"warehouse-"+(count-1),List.of("warehouse-"+(count-1)));
        insertTask(f,"denied-sort","table-1","denied-0",List.of("denied-0"));
        var seedScript = """
                db.table('db_table_t').where().eq('tid','warehouse-0').update({source_table_id:'table-0'})
                db.table('nifi_pipeline_t').where().eq('id','pipe-recent-root').update({updated_at:base+100000})
                db.table('nifi_pipeline_t').where().eq('id','pipe-recent-hop').update({updated_at:base+2000000})
                db.table('nifi_pipeline_t').where().eq('id','pipe-monitor-only').update({updated_at:base+500000})
                db.table('nifi_pipeline_t').where().eq('id','pipe-null-table-time').update({updated_at:base+1500000})
                db.table('nifi_pipeline_t').where().eq('id','pipe-denied-sort').update({updated_at:base+5000000})
                db.table('data_access_agg_task_t').where().eq('tid','monitor-only').update({updated_time:future})
                db.table('nifi_pipeline_t').insert({id:'pipe-monitor-only',tenant_id:'other',is_del:0,updated_at:base+9000000})
                """;
        MagicScript.create(seedScript,null).execute(new MagicScriptContext(Map.of("db",f.db,"base",base.toEpochMilli(),
                "future",java.util.Date.from(base.plusSeconds(90000)))));
        var recent = run(f, INGEST_PAGE, Map.of("viewLevel","table","datasourceIds",List.of("db"),"pageSize",pageSize));
        assertThat(((List<?>)recent.get("list")).stream().limit(4).map(value -> ((Map<?,?>)value).get("tid").toString()).toList())
                .containsExactly("table-1","table-0","table-"+(count-1),"table-2");
        System.out.println("ingestion sorted pages: size="+pageSize+", SQL=7/8; ties/nulls/filters and tenant bounds preserved without menu scope");
    }

    @Test
    void ingestionStatesUseDirectLiveTasksBeforePagingIncludingApprovalWithoutExtraRowReads() throws Exception {
        Map<String, Integer> smallReads = new HashMap<>();
        for (int count : List.of(20, 100)) {
            var f = fixture(count);
            var response = (com.fasterxml.jackson.databind.node.ObjectNode) TaskRuntimeMagicFixture.emptyRootStatus();
            var groups = ((com.fasterxml.jackson.databind.node.ObjectNode) response.at("/processGroupStatus/aggregateSnapshot"))
                    .putArray("processGroupStatusSnapshots");
            for (int i = 0; i < count; i++) {
                if (i % 4 == 0) continue;
                insertTask(f, "access-" + i, "table-" + i, "warehouse-" + i, List.of("warehouse-" + i));
                if (i % 4 != 3) {
                    f.jdbc.update("update nifi_pipeline_t set status=?,nifi_process_group_id=? where id=?",
                            i % 4 == 2 ? "STOPPED" : "RUNNING", "pg-" + i, "pipe-access-" + i);
                    groups.addObject().putObject("processGroupStatusSnapshot").put("id", "pg-" + i)
                            .putArray("processorStatusSnapshots").addObject().putObject("processorStatusSnapshot")
                            .put("runStatus", i % 4 == 2 ? "Running" : "Stopped");
                }
            }
            f.jdbc.update("update db_table_t set access_request_status=1 where datasource_id='db' and mod(cast(substring(tid,7) as int),8)=0");
            // 下游运行不代表本表第一跳运行；旧菜单范围内隐藏的同租户任务仍参与本表状态计算。
            insertTask(f, "downstream", "warehouse-1", "raw-1", List.of("raw-1"));
            f.jdbc.update("update nifi_pipeline_t set nifi_process_group_id='downstream-pg' where id='pipe-downstream'");
            groups.addObject().putObject("processGroupStatusSnapshot").put("id", "downstream-pg")
                    .putArray("processorStatusSnapshots").addObject().putObject("processorStatusSnapshot").put("runStatus", "Running");
            insertTask(f, "denied", "table-0", "denied-0", List.of("denied-0"));
            var counter = new Counter(); f.db.setSqlInterceptors(List.of(counter));
            for (String state : List.of("all", "0", "1", "2", "3")) {
                counter.reads = 0;
                var result = run(f, INGEST_PAGE, Map.of("viewLevel", "table", "pageSize", count,
                        "datasourceIds", List.of("db"), "accessStatus", state), response);
                int expected = switch (state) { case "all" -> count; case "0" -> count / 8; case "2" -> (count + 4) / 8 - 1; case "3" -> count / 2 + 1; default -> count / 4; };
                assertThat(((Number) result.get("total")).intValue()).isEqualTo(expected);
                assertThat((List<?>) result.get("list")).hasSize(expected);
                for (Object value : (List<?>) result.get("list")) {
                    var row = (Map<?, ?>) value;
                    int index = Integer.parseInt(row.get("tid").toString().substring("table-".length()));
                    String expectedState = index == 0 ? "3" : index % 8 == 0 ? "2" : index % 4 == 0 ? "0" : index % 4 == 2 ? "1" : "3";
                    assertThat(row.get("accessStatus")).isEqualTo(expectedState);
                    assertThat(row.get("accessStatusText")).isEqualTo(Map.of("0", "待接入", "1", "已接入", "2", "待审批", "3", "待启动").get(expectedState));
                    if (!state.equals("all")) assertThat(row.get("accessStatus")).isEqualTo(state);
                }
                if (count == 20) smallReads.put(state, counter.reads);
                else assertThat(counter.reads).isEqualTo(smallReads.get(state));
                System.out.println("ingestion state=" + state + ", rows=" + count + ", SQL=" + counter.reads + ", NiFi root reads<=1");
            }
            var second = run(f, INGEST_PAGE, Map.of("viewLevel", "table", "pageSize", 2, "pageNum", 2,
                    "datasourceIds", List.of("db"), "accessStatus", "1"), response);
            assertThat(((Number) second.get("total")).intValue()).isEqualTo(count / 4);
            assertThat((List<?>) second.get("list")).hasSize(2).allSatisfy(value ->
                    assertThat(((Map<?, ?>) value).get("accessStatus")).isEqualTo("1"));
            var offline = run(f, INGEST_PAGE, Map.of("viewLevel", "table", "pageSize", count, "datasourceIds", List.of("db")), null);
            assertThat((List<?>) offline.get("list")).anySatisfy(value -> {
                var row = (Map<?, ?>) value;
                assertThat(row.get("tid")).isEqualTo("table-1");
                assertThat(row.get("accessStatus")).isEqualTo("");
                assertThat(row.get("accessStatusText")).isEqualTo("状态待确认");
            });
            var offlineReady = run(f, INGEST_PAGE, Map.of("viewLevel", "table", "pageSize", count,
                    "datasourceIds", List.of("db"), "accessStatus", "3"), null);
            assertThat(((Number) offlineReady.get("total")).intValue()).isEqualTo(count / 4 + 1);
            assertThat((List<?>) offlineReady.get("list")).allSatisfy(value ->
                    assertThat(((Map<?, ?>) value).get("accessStatus")).isEqualTo("3"));
            var offlineRunning = run(f, INGEST_PAGE, Map.of("viewLevel", "table", "pageSize", count,
                    "datasourceIds", List.of("db"), "accessStatus", "1"), null);
            assertThat(((Number) offlineRunning.get("total")).intValue()).isZero();
        }
    }

    @ParameterizedTest @ValueSource(ints = {20, 100})
    void liveRuntimeStateOverridesLifecycleSuccessfulBatchAndStaleStartFlagWithConstantQueries(int count) throws Exception {
        var f = fixture(count);
        f.jdbc.execute("alter table data_access_agg_task_t add task_status int");
        f.jdbc.execute("alter table data_access_agg_task_t add monitor_status varchar(32)");
        f.jdbc.update("update db_table_t set source_table_id=concat('table-',substring(tid,locate('-',tid)+1)) where datasource_id in ('warehouse','raw')");
        var lifecycle = List.of("STOPPED", "RUNNING", "DEPLOY_FAILED", "RUN_ERROR", "RUNNING", "RUNNING", "RUNNING", "RUNNING");
        var expected = List.of("RUNNING", "STOPPED", "INVALID", "RUN_ERROR", "RUNNING", "UNKNOWN", "UNDEPLOYED", "UNDEPLOYED");
        var labels = List.of("运行中", "已停止", "运行异常", "运行异常", "运行中", "状态待确认", "未部署", "未部署");
        var response = (com.fasterxml.jackson.databind.node.ObjectNode) TaskRuntimeMagicFixture.emptyRootStatus();
        var groups = ((com.fasterxml.jackson.databind.node.ObjectNode) response.at("/processGroupStatus/aggregateSnapshot"))
                .putArray("processGroupStatusSnapshots");
        for (int i = 0; i < count; i++) {
            for (String prefix : List.of("first-", "second-")) {
                String id = prefix + i;
                insertTask(f, id, (prefix.equals("first-") ? "table-" : "warehouse-") + i,
                        (prefix.equals("first-") ? "warehouse-" : "raw-") + i,
                        List.of((prefix.equals("first-") ? "warehouse-" : "raw-") + i));
                f.jdbc.update("update data_access_agg_task_t set task_status=?,monitor_status='SUCCESS' where tid=?", i % 8 == 4 ? 0 : 1, id);
                f.jdbc.update("update nifi_pipeline_t set nifi_process_group_id=? where id=?", "pipe-" + id + "-pg", "pipe-" + id);
                if (i % 8 != 5) f.jdbc.update("update nifi_pipeline_t set status=? where id=?", lifecycle.get(i % 8), "pipe-" + id);
                if (i % 8 == 6) f.jdbc.update("update nifi_pipeline_t set tenant_id='other',status='DEPLOY_FAILED' where id=?", "pipe-" + id);
                if (i % 8 == 7) f.jdbc.update("update nifi_pipeline_t set is_del=1,status='DEPLOY_FAILED' where id=?", "pipe-" + id);
                // 数据库生命周期故意与 NiFi 快照不同；其他租户及已删除流程即使出现在快照中也不能关联。
                var wrapper = groups.addObject();
                if (i % 8 == 5) wrapper.put("canRead", false);
                var group = wrapper.putObject("processGroupStatusSnapshot").put("id", "pipe-" + id + "-pg");
                var processors = group.putArray("processorStatusSnapshots");
                String state = switch (i % 8) { case 0, 3, 4 -> "Running"; case 1 -> "Stopped"; default -> "Invalid"; };
                processors.addObject().putObject("processorStatusSnapshot").put("runStatus", state);
                if (i % 8 == 3) processors.addObject().putObject("processorStatusSnapshot").put("runStatus", "Invalid");
            }
        }
        var counter = new Counter(); f.db.setSqlInterceptors(List.of(counter));
        for (String resource : List.of(INGEST_PAGE, DISTRIBUTE_PAGE)) {
            counter.reads = 0;
            var result = run(f, resource, Map.of("viewLevel", "table", "pageSize", count,
                    "datasourceIds", List.of(resource.equals(INGEST_PAGE) ? "db" : "warehouse")), response);
            assertThat((List<?>) result.get("list")).hasSize(count);
            assertThat(counter.reads).isEqualTo(12);
            for (Object raw : (List<?>) result.get("list")) {
                var row = (Map<?, ?>) raw;
                for (Object taskValue : (List<?>) row.get("tasks")) {
                    var task = (Map<?, ?>) taskValue;
                    int index = Integer.parseInt(task.get("tid").toString().split("-")[1]);
                    String state = expected.get(index % 8);
                    assertThat(task.get("currentStatus")).isEqualTo(state);
                    assertThat(task.get("monitorStatus")).isEqualTo("SUCCESS");
                    assertThat(task.get("storedTaskStatus")).isEqualTo(index % 8 == 4 ? 0 : 1);
                    assertThat(task.get("runtimeFresh")).isEqualTo(true);
                    assertThat(task.get("taskStatusText")).isEqualTo(labels.get(index % 8));
                }
            }
        }
        System.out.println("runtime status evidence: " + count + " rows, ingestion SQL=12, distribution SQL=12; foreign/deleted pipelines excluded, historical SUCCESS retained");
    }

    @Test
    void singleChainTargetDetailKeepsTenantAndRelationBoundariesWithoutMenuScope() throws Exception {
        var f = fixture(1);
        f.jdbc.update("update db_table_t set asset_status=0 where tid='warehouse-0'");
        f.jdbc.update("update db_datasource_t set asset_status=0 where tid='warehouse'");
        insertTask(f, "existing", "table-0", "warehouse-0", List.of("warehouse-0"));
        insertTask(f, "denied", "table-0", "denied-0", List.of("denied-0"));
        for (String resource : List.of(INGEST_PAGE, DISTRIBUTE_PAGE)) {
            Map<?, ?> detail = run(f, resource, Map.of("viewLevel", "tableDetail", "tableId", "warehouse-0"));
            assertThat(((Map<?, ?>) detail.get("detail")).get("tid")).isEqualTo("warehouse-0");
            Map<?, ?> menuHiddenTarget = (Map<?, ?>) run(f, resource, Map.of("viewLevel", "tableDetail", "tableId", "denied-0")).get("detail");
            assertThat(menuHiddenTarget.get("tid")).isEqualTo("denied-0");
            assertThat(run(f, resource, Map.of("viewLevel", "tableDetail", "tableId", "foreign-0")).get("detail")).isNull();
            assertThat(run(f, resource, Map.of("viewLevel", "tableDetail", "tableId", "warehouse-draft-0")).get("detail")).isNull();
        }
        Map<?, ?> listing = run(f, DISTRIBUTE_PAGE, Map.of("pageSize", 20, "datasourceIds", List.of("warehouse")));
        assertThat((List<?>) listing.get("list")).isEmpty();
        f.jdbc.update("update data_access_agg_task_t set is_del=1 where tid='existing'");
        assertThat(run(f, INGEST_PAGE, Map.of("viewLevel", "tableDetail", "tableId", "warehouse-0")).get("detail")).isNull();
    }

    @ParameterizedTest @ValueSource(ints = {20, 100})
    void warehouseConfigurationPartitionsPagesAndTreesWithConstantQueries(int count) throws Exception {
        var f = fixture(count);
        var counter = new Counter(); f.db.setSqlInterceptors(List.of(counter));
        Map<?, ?> ingest = run(f, INGEST_PAGE, Map.of("viewLevel", "table", "pageSize", count));
        assertThat(((Number) ingest.get("total")).intValue()).isEqualTo(count + 6);
        assertThat((List<?>) ingest.get("list")).hasSize(count);
        assertThat(((List<?>) ingest.get("datasourceIds")).stream().map(String::valueOf).toList()).containsExactlyInAnyOrder(
                "db", "business-hive", "stale-binding", "foreign-binding", "deleted-node", "dwd-binding", "fake-root");
        assertThat(counter.reads).isEqualTo(7);
        counter.reads = 0;
        Map<?, ?> distribute = run(f, DISTRIBUTE_PAGE, Map.of("pageSize", count));
        assertThat(((Number) distribute.get("total")).intValue()).isEqualTo(3 * count + 1);
        assertThat((List<?>) distribute.get("list")).hasSize(count);
        assertThat(((List<?>) distribute.get("datasourceIds")).stream().map(String::valueOf).toList()).containsExactlyInAnyOrder("warehouse", "raw", "files", "denied");
        assertThat(counter.reads).isEqualTo(8);
        // Warehouse tables need not have historical source_table_id: ordinary registered rows are valid sources too.
        counter.reads = 0;
        Map<?, ?> warehouse = run(f, DISTRIBUTE_PAGE, Map.of("pageSize", count, "datasourceIds", List.of("warehouse")));
        assertThat(((Number) warehouse.get("total")).intValue()).isEqualTo(count);
        assertThat((List<?>) warehouse.get("list")).hasSize(count);
        assertThat(counter.reads).isEqualTo(8);
        counter.reads = 0;
        assertThat(((Number) run(f, INGEST_TREE, Map.of()).get("total")).intValue()).isEqualTo(count + 6);
        assertThat(counter.reads).isEqualTo(3);
        counter.reads = 0;
        assertThat(((Number) run(f, DISTRIBUTE_TREE, Map.of()).get("total")).intValue()).isEqualTo(3 * count + 1);
        assertThat(counter.reads).isEqualTo(3);

        for (String resource : List.of(INGEST_PAGE, DISTRIBUTE_PAGE)) {
            counter.reads = 0;
            Map<?, ?> mixedScope = run(f, resource, Map.of("viewLevel", "table", "pageSize", count,
                    "tenantId", "other", "datasourceIds", List.of("denied", "foreign", "warehouse-draft")));
            if (resource.equals(DISTRIBUTE_PAGE)) {
                assertThat((List<?>) mixedScope.get("list")).singleElement().satisfies(raw ->
                        assertThat(((Map<?, ?>) raw).get("tid")).isEqualTo("denied-0"));
                assertThat(counter.reads).isEqualTo(8);
            } else {
                assertThat((List<?>) mixedScope.get("list")).isEmpty();
                assertThat(counter.reads).isEqualTo(2);
            }
            counter.reads = 0;
            assertThat((List<?>) run(f, resource, Map.of("viewLevel", "table", "tenantId", "other", "datasourceIds",
                    List.of("foreign", "warehouse-draft", "missing"))).get("list")).isEmpty();
            assertThat(counter.reads).isEqualTo(2);
        }
        // ODS 资格和可信租户保持生效；旧数据源菜单范围不再隐藏有效同租户对象。
        Map<?, ?> mixed = run(f, DISTRIBUTE_PAGE, Map.of("pageSize", count,
                "datasourceIds", List.of("warehouse", "db", "denied", "foreign")));
        assertThat(((List<?>) mixed.get("datasourceIds")).stream().map(String::valueOf).toList()).containsExactlyInAnyOrder("warehouse", "denied");
        assertThat((List<?>) mixed.get("list")).allSatisfy(raw ->
                assertThat(((Map<?, ?>) raw).get("datasourceId")).isIn("warehouse", "denied"));
        System.out.println("task category evidence: page size=" + count
                + ", ingestion SQL=7, distribution SQL=8, each tree SQL=3; foreign/missing/draft IDs excluded without menu scope");
    }

    @ParameterizedTest @ValueSource(ints = {20, 100})
    void classificationDoesNotHideAuthorizedDownstreamHopsOrBranchTargets(int count) throws Exception {
        var f = fixture(count);
        f.jdbc.update("update db_table_t set source_table_id=concat('table-',substring(tid,locate('-',tid)+1)) where datasource_id in ('warehouse','raw','files')");
        for (int i = 0; i < count; i++) {
            insertTask(f, "root-" + i, "table-" + i, "warehouse-" + i,
                    List.of("warehouse-" + i, "raw-" + i, "denied-0", "foreign-0", "deleted"));
            insertTask(f, "second-" + i, "warehouse-" + i, "raw-" + i, List.of("raw-" + i));
            insertTask(f, "third-" + i, "raw-" + i, "files-" + i, List.of("files-" + i));
        }
        // 同租户中间表不受旧菜单范围过滤；跨租户中间表不能因关联至有效根表而被读取。
        f.jdbc.update("update db_table_t set source_table_id='table-0' where tid='denied-0'");
        insertTask(f, "denied-hop", "denied-0", "raw-0", List.of("raw-0"));
        f.jdbc.update("update db_table_t set source_table_id='table-0' where tid='foreign-0'");
        insertTask(f, "foreign-hop", "foreign-0", "raw-0", List.of("raw-0"));
        var counter = new Counter(); f.db.setSqlInterceptors(List.of(counter));
        Map<?, ?> ingest = run(f, INGEST_PAGE, Map.of("viewLevel", "table", "pageSize", count, "datasourceIds", List.of("db")));
        assertThat((List<?>) ingest.get("list")).hasSize(count);
        assertThat(counter.reads).isEqualTo(12);
        for (Object raw : (List<?>) ingest.get("list")) {
            Map<?, ?> source = (Map<?, ?>) raw;
            List<?> tasks = (List<?>) source.get("tasks");
            String suffix = String.valueOf(source.get("tid")).substring("table-".length());
            assertThat(tasks).hasSize(suffix.equals("0") ? 4 : 3);
            assertThat(tasks).noneSatisfy(task -> assertThat(((Map<?, ?>) task).get("tid")).isEqualTo("foreign-hop"));
            if (suffix.equals("0")) assertThat(tasks).anySatisfy(task ->
                    assertThat(((Map<?, ?>) task).get("tid")).isEqualTo("denied-hop"));
            Map<?, ?> first = (Map<?, ?>) tasks.stream().filter(task -> ("root-" + suffix).equals(((Map<?, ?>) task).get("tid"))).findFirst().orElseThrow();
            assertThat((List<?>) first.get("targetTables")).hasSize(3).allSatisfy(target ->
                    assertThat(((Map<?, ?>) target).get("targetDbId")).isIn("warehouse", "raw", "denied"));
            Map<?, ?> second = (Map<?, ?>) tasks.stream().filter(task -> ("second-" + suffix).equals(((Map<?, ?>) task).get("tid"))).findFirst().orElseThrow();
            assertThat(second.get("sourceDbName")).isEqualTo("名称相同");
            assertThat(second.get("sourceTableId")).isEqualTo("warehouse-" + suffix);
            assertThat(second.get("targetTableId")).isEqualTo("raw-" + suffix);
        }
        counter.reads = 0;
        Map<?, ?> distribution = run(f, DISTRIBUTE_PAGE, Map.of("pageSize", count, "datasourceIds", List.of("warehouse"), "accessStatus", "1"));
        assertThat(((Number) distribution.get("total")).intValue()).isEqualTo(count);
        assertThat((List<?>) distribution.get("list")).hasSize(count);
        assertThat(counter.reads).isEqualTo(13);
        for (Object raw : (List<?>) distribution.get("list")) {
            Map<?, ?> source = (Map<?, ?>) raw;
            assertThat((List<?>) source.get("tasks")).allSatisfy(task ->
                    assertThat(((Map<?, ?>) task).get("sourceTableId")).isEqualTo(source.get("tid")));
        }
        System.out.println("task family evidence: page size=" + count
                + ", root+three hops SQL=12, filtered distribution SQL=13; valid menu-hidden target retained, foreign/deleted targets excluded");
        f.jdbc.update("update db_datasource_t set asset_status=0 where tid='warehouse'");
        Map<?, ?> afterUnregister = run(f, INGEST_PAGE, Map.of("viewLevel", "table", "pageSize", count, "datasourceIds", List.of("db")));
        for (Object raw : (List<?>) afterUnregister.get("list"))
            assertThat((List<?>) ((Map<?, ?>) raw).get("tasks")).anySatisfy(task ->
                    assertThat(((Map<?, ?>) task).get("sourceDbId")).isEqualTo("warehouse"));
        assertThat((List<?>) run(f, DISTRIBUTE_PAGE, Map.of("datasourceIds", List.of("warehouse"))).get("list")).isEmpty();
    }

    @ParameterizedTest @ValueSource(ints = {20, 100})
    void sixteenActualHopsWithImmediateParentsAndRootSelfReferenceUseBoundedBatchReads(int count) throws Exception {
        var f = fixture(count);
        f.jdbc.update("update db_table_t set source_table_id=tid where datasource_id='db'");
        f.jdbc.update("update db_datasource_t set asset_status=0 where tid='warehouse'");
        for (int root = 0; root < count; root++) {
            String previous = "table-" + root;
            for (int hop = 1; hop <= 17; hop++) {
                String id = "deep-" + root + "-" + hop;
                f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,business_type,table_name,source_table_id,updated_time) values(?,'ga','warehouse',0,0,'业务表',?,?,current_timestamp)", id, id, previous);
                insertTask(f, "step-" + root + "-" + hop, previous, id, List.of(id));
                previous = id;
            }
        }
        var counter = new Counter(); f.db.setSqlInterceptors(List.of(counter));
        Map<?, ?> result = run(f, INGEST_PAGE, Map.of("viewLevel", "table", "pageSize", count, "datasourceIds", List.of("db")));
        assertThat(((Number) result.get("total")).intValue()).isEqualTo(count);
        assertThat((List<?>) result.get("list")).hasSize(count);
        assertThat(counter.reads).isEqualTo(12);
        for (Object raw : (List<?>) result.get("list")) {
            Map<?, ?> row = (Map<?, ?>) raw;
            List<?> tasks = (List<?>) row.get("tasks");
            assertThat(tasks).hasSize(16);
            String root = String.valueOf(row.get("tid")).substring("table-".length());
            for (int hop = 1; hop <= 16; hop++) {
                Map<?, ?> task = (Map<?, ?>) tasks.get(hop - 1);
                assertThat(task.get("tid")).isEqualTo("step-" + root + "-" + hop);
                assertThat(task.get("sourceTableId")).isEqualTo(hop == 1 ? row.get("tid") : "deep-" + root + "-" + (hop - 1));
            }
        }
        System.out.println("deep chain evidence: page size=" + count + ", 16 actual tasks/row, SQL=" + counter.reads + "; root self and unregistered existing warehouse preserved; hop 17 excluded");
    }

    @Test
    void excessivePageRelationsAndTasksAreRejectedBeforeFurtherMetadataReads() throws Exception {
        var relations = fixture(1);
        var relationCounter = new Counter(); relations.db.setSqlInterceptors(List.of(relationCounter));
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 2100; i++) rows.add(new Object[]{"many-" + i});
        relations.jdbc.batchUpdate("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,business_type,source_table_id) values(?,'ga','warehouse',0,0,'业务表','table-0')", rows);
        assertThat(runValue(relations, INGEST_PAGE, Map.of("viewLevel", "table", "datasourceIds", List.of("db")))).isInstanceOf(ExitValue.class);
        // 固定排序聚合只读取任务标识/时间；超限后仍不得继续装载当前页任务和画布详情。
        assertThat(relationCounter.sql.stream().filter(sql -> !sql.contains("task_ancestry")).toList()).noneSatisfy(sql -> assertThat(sql).contains("data_access_agg_task_t"));
        var tasks = fixture(1);
        var taskCounter = new Counter(); tasks.db.setSqlInterceptors(List.of(taskCounter));
        tasks.jdbc.batchUpdate("insert into data_access_agg_task_t(tid,tenant_id,source_table_id,target_table_id,is_del) values(?,'ga','table-0','warehouse-0',0)", rows);
        assertThat(runValue(tasks, INGEST_PAGE, Map.of("viewLevel", "table", "datasourceIds", List.of("db")))).isInstanceOf(ExitValue.class);
        assertThat(taskCounter.sql.stream().filter(sql -> !sql.contains("task_ancestry")).toList()).noneSatisfy(sql -> assertThat(sql).contains("nifi_pipeline_t"));
    }

    @Test
    void samePageAncestorAndChildEachOwnTheirAuthorizedRemainingChain() throws Exception {
        var f = fixture(2);
        f.jdbc.update("update db_table_t set source_table_id=tid where tid='table-0'");
        f.jdbc.update("update db_table_t set source_table_id='table-0' where tid='table-1'");
        f.jdbc.update("update db_table_t set source_table_id='table-1' where tid='warehouse-0'");
        insertTask(f, "first", "table-0", "table-1", List.of("table-1"));
        insertTask(f, "second", "table-1", "warehouse-0", List.of("warehouse-0"));
        var counter = new Counter(); f.db.setSqlInterceptors(List.of(counter));
        Map<?, ?> result = run(f, INGEST_PAGE, Map.of("viewLevel", "table", "datasourceIds", List.of("db")));
        for (Object raw : (List<?>) result.get("list")) {
            Map<?, ?> row = (Map<?, ?>) raw;
            List<?> tasks = (List<?>) row.get("tasks");
            assertThat(tasks.stream().map(task -> String.valueOf(((Map<?, ?>) task).get("tid"))).toList()).containsExactly(
                    row.get("tid").equals("table-0") ? new String[]{"first", "second"} : new String[]{"second"});
        }
        assertThat(counter.reads).isEqualTo(12);
    }

    @Test
    void sharedAncestorsCannotMultiplyTheBoundedPageIntoAnUnlimitedAssociationSet() throws Exception {
        var f = fixture(16);
        f.jdbc.update("update db_table_t set source_table_id=tid where tid='table-0'");
        for (int i = 1; i < 16; i++)
            f.jdbc.update("update db_table_t set source_table_id=? where tid=?", "table-" + (i - 1), "table-" + i);
        List<Object[]> leaves = new ArrayList<>();
        for (int i = 0; i < 140; i++) leaves.add(new Object[]{"shared-leaf-" + i});
        f.jdbc.batchUpdate("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,business_type,source_table_id) values(?,'ga','warehouse',0,0,'业务表','table-15')", leaves);
        var counter = new Counter(); f.db.setSqlInterceptors(List.of(counter));
        assertThat(runValue(f, INGEST_PAGE, Map.of("viewLevel", "table", "datasourceIds", List.of("db")))).isInstanceOf(ExitValue.class);
        assertThat(counter.sql.stream().filter(sql -> !sql.contains("task_ancestry")).toList()).noneSatisfy(sql -> assertThat(sql).contains("data_access_agg_task_t"));
    }

    private static DatasourceCompletionMagicTest.Fixture fixture(int count) {
        var f = new DatasourceCompletionMagicTest.Fixture(count);
        DataIngestionRegistrationScopeTest.createWarehouseConfiguration(f);
        f.jdbc.execute("alter table db_datasource_t add db_name varchar(100)");
        f.jdbc.execute("alter table db_datasource_t add db_type varchar(32)");
        f.jdbc.execute("alter table db_datasource_t add org_id varchar(64)");
        f.jdbc.execute("alter table db_datasource_t add app_id varchar(64)");
        for (String column : List.of("business_type", "table_name", "table_name_cn", "table_comment", "source_table_id", "schema_name"))
            f.jdbc.execute("alter table db_table_t add " + column + " varchar(100)");
        f.jdbc.execute("alter table db_table_t add updated_time timestamp");
        f.jdbc.execute("create index idx_test_table_lookup on db_table_t(tenant_id,tid,is_del)");
        f.jdbc.execute("create table data_access_agg_task_t(tid varchar(64),source_table_id varchar(64),target_table_id varchar(64),pipeline_id varchar(64),tenant_id varchar(32),is_del int,updated_time timestamp,process_group_id varchar(64))");
        f.jdbc.execute("create table nifi_pipeline_t(id varchar(64),tenant_id varchar(32),is_del int,dsl_json clob,status varchar(32),nifi_process_group_id varchar(64),updated_at bigint)");
        f.jdbc.execute("create table rm_org_t(tenant_id varchar(32),id varchar(64),name varchar(100),deleted int)");
        f.jdbc.update("insert into rm_org_t values('ga','org','当前租户部门',0),('other','org','其他租户部门',0)");
        f.jdbc.update("update db_datasource_t set asset_status=1,db_name='名称相同',db_type='mysql',org_id='org',app_id='app'");
        f.jdbc.update("update db_table_t set asset_status=1,business_type='业务表',table_name=tid,schema_name='public',updated_time=current_timestamp");
        f.jdbc.update("insert into sym_dict_t values('root','ga','0','dataSourceType',0),('ods','ga','root','ods',0),('raw-domain','ga','ods','raw',0),('file-domain','ga','ods','files',0),('dead-domain','ga','ods','dead',1),('dwd','ga','root','dwd',0),('fake-parent','ga','0','wrongRoot',0),('fake-ods','ga','fake-parent','ods',0),('foreign-domain','other','ods','raw',0)");
        for (String id : List.of("warehouse", "raw", "files", "business-hive", "stale-binding", "foreign-binding", "deleted-node", "dwd-binding", "fake-root", "denied", "warehouse-draft", "foreign")) {
            String tenant = id.equals("foreign") ? "other" : "ga";
            int status = id.equals("warehouse-draft") ? 0 : 2;
            f.jdbc.update("insert into db_datasource_t(tid,tenant_id,asset_status,is_del,db_name,db_type,org_id,app_id) values(?,?,?,0,'名称相同',?,'org','app')", id, tenant, status, id.equals("business-hive") ? "hive" : "mysql");
            int rows = List.of("warehouse", "raw", "files").contains(id) ? count : 1;
            for (int i = 0; i < rows; i++)
                f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,business_type,table_name,table_name_cn,updated_time) values(?,?,?,2,0,'业务表',?,'同名业务表',current_timestamp)", id + "-" + i, tenant, id, "physical_" + i);
        }
        f.jdbc.update("insert into dwm_center_layer_source_t values('ga','ods','layer','warehouse',0),('ga','raw-domain','domain','raw',0),('ga','file-domain','domain','files',0),('ga','ods','layer','denied',0),('ga','ods','layer','warehouse-draft',0),('ga','ods','layer','foreign',0),('ga','ods','layer','stale-binding',1),('other','ods','layer','foreign-binding',0),('ga','dead-domain','domain','deleted-node',0),('ga','dwd','layer','dwd-binding',0),('ga','fake-ods','layer','fake-root',0)");
        for (Object status : Arrays.asList(0, -1, null))
            f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,business_type,table_name,updated_time) values(?,'ga','warehouse',?,0,'字典表','excluded_status',current_timestamp)", "status-" + status, status);
        f.jdbc.update("insert into db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,business_type,table_name,updated_time) values('deleted','ga','warehouse',2,1,'业务表','deleted',current_timestamp),('foreign-shadow','other','warehouse',2,0,'业务表','foreign-shadow',current_timestamp)");
        var dialects = new DialectAdapter(); dialects.add(new MySQLDialect() { @Override public boolean match(String url) { return url.contains(":h2:"); } });
        f.db.setDialectAdapter(dialects); f.db.setResultProvider(new DefaultResultProvider("task-category-test"));
        return f;
    }
    private static void insertTask(DatasourceCompletionMagicTest.Fixture f, String id, String source, String target, List<String> targets) {
        List<Map<String, Object>> nodes = targets.stream().map(value -> Map.<String, Object>of("category", "sink", "config", Map.of("targetTableId", value))).toList();
        String dsl = cn.hutool.json.JSONUtil.toJsonStr(Map.of("nodes", nodes));
        f.jdbc.update("insert into nifi_pipeline_t(id,tenant_id,is_del,dsl_json) values(?,'ga',0,?)", "pipe-" + id, dsl);
        f.jdbc.update("insert into data_access_agg_task_t(tid,source_table_id,target_table_id,pipeline_id,tenant_id,is_del,updated_time) values(?,?,?,?,'ga',0,current_timestamp)", id, source, target, "pipe-" + id);
    }
    private static Map<?, ?> run(DatasourceCompletionMagicTest.Fixture f, String id, Map<String, Object> body) throws Exception {
        return (Map<?, ?>) runValue(f, id, body);
    }
    private static Map<?, ?> run(DatasourceCompletionMagicTest.Fixture f, String id, Map<String, Object> body,
            com.fasterxml.jackson.databind.JsonNode response) throws Exception {
        return (Map<?, ?>) runValue(f, id, body, response);
    }
    private static Object runValue(DatasourceCompletionMagicTest.Fixture f, String id, Map<String, Object> body) throws Exception {
        return runValue(f, id, body, TaskRuntimeMagicFixture.emptyRootStatus());
    }
    private static Object runValue(DatasourceCompletionMagicTest.Fixture f, String id, Map<String, Object> body,
            com.fasterxml.jackson.databind.JsonNode response) throws Exception {
        String script = CanonicalMagicSources.byId(id).replaceAll("(?m)^import (?!cn\\.hutool\\.)[^\\r\\n]*\\r?\\n", "");
        var scope = new DataIngestionRegistrationScopeTest.VisibleScope();
        var inputs = new LinkedHashMap<String, Object>(); inputs.put("db", f.db); inputs.put("body", body);
        inputs.put("tenantRuntime", scope); inputs.put("dataScope", scope); inputs.put("identityRuntime",scope);
        inputs.put("tableSort", new TableSortMagicModule());
        try (var runtime = new TaskRuntimeMagicFixture(scope.id(), response)) {
            inputs.put("taskRuntime", runtime.module);
            Object result = MagicScript.create(script, null).execute(new MagicScriptContext(inputs));
            if (result instanceof Map<?, ?> page && page.containsKey("runtimeRevision")) runtime.verifySingleRootRead();
            return result;
        }
    }
    static class Counter implements SQLInterceptor {
        int reads;
        final List<String> sql = new ArrayList<>();
        @Override public void preHandle(BoundSql sql, RequestEntity request) {
            String statement=sql.getSql().stripLeading().toLowerCase();
            if (statement.startsWith("select") || statement.startsWith("with")) {
                reads++;
                this.sql.add(sql.getSql().toLowerCase());
            }
            H2RecursiveCteParameters.adapt(sql);
        }
    }
}
