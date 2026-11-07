package com.linewell.dataelement.platform.magic;

import static com.linewell.dataelement.platform.magic.TaskCategoryWarehouseScopeMagicTest.fixture;
import static com.linewell.dataelement.platform.magic.TaskCategoryWarehouseScopeMagicTest.run;
import static org.assertj.core.api.Assertions.assertThat;

import com.linewell.dataelement.utils.StructuredValues;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class DistributionTaskCountMagicTest {
    private static final String PAGE = "1f475a245fda4ba4a885b3ac4f8e2d59";

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void sortsAllCandidatesByDistinctFamilyTasksBeforePaging(int pageSize) throws Exception {
        int total = 2 * pageSize + 5;
        var fixture = fixture(total);
        var jdbc = fixture.jdbc;
        jdbc.update("UPDATE db_table_t SET source_table_id='warehouse-0' WHERE tid='raw-0'");
        addTask(jdbc, "direct-0", "warehouse-0", "raw-0", "ga", 0);
        addTask(jdbc, "downstream-0", "raw-0", "files-0", "ga", 0);
        addTask(jdbc, "direct-1", "warehouse-1", "raw-1", "ga", 0);
        addTask(jdbc, "draft-target-1", "warehouse-1", "warehouse-draft-0", "ga", 0);

        String parent = "warehouse-2";
        for (int depth = 1; depth <= 17; depth++) {
            String node = "sort-depth-" + depth;
            addHop(jdbc, node, parent, "raw");
            parent = node;
        }
        addTask(jdbc, "depth-16", "sort-depth-16", "raw-2", "ga", 0);
        addTask(jdbc, "depth-17-a", "sort-depth-17", "raw-2", "ga", 0);
        addTask(jdbc, "depth-17-b", "sort-depth-17", "files-2", "ga", 0);

        jdbc.update("INSERT INTO db_datasource_t(tid,tenant_id,asset_status,is_del) VALUES ('deleted-sort-db','ga',2,1)");
        addHop(jdbc, "legacy-source", "warehouse-3", "deleted-sort-db");
        addHop(jdbc, "invalid-target-db", null, "deleted-sort-db");
        addTask(jdbc, "legacy-source-task", "legacy-source", "raw-3", "ga", 0);

        addHop(jdbc, "sort-cycle", "warehouse-4", "raw");
        jdbc.update("UPDATE db_table_t SET source_table_id='sort-cycle' WHERE tid='warehouse-4'");
        addTask(jdbc, "cycle-task", "sort-cycle", "raw-4", "ga", 0);
        addTask(jdbc, "wrong-target-tenant", "warehouse-5", "foreign-0", "ga", 0);
        addTask(jdbc, "wrong-task-tenant", "warehouse-5", "raw-5", "other", 0);
        addTask(jdbc, "deleted-task", "warehouse-5", "raw-5", "ga", 1);
        addTask(jdbc, "deleted-target", "warehouse-5", "deleted", "ga", 0);
        addTask(jdbc, "deleted-target-db", "warehouse-5", "invalid-target-db", "ga", 0);

        Map<String, Integer> score = Map.of(
                "warehouse-0", 2, "warehouse-1", 2, "warehouse-2", 1, "warehouse-3", 1, "warehouse-4", 1);
        var expected = IntStream.range(0, total).mapToObj(index -> "warehouse-" + index)
                .sorted(Comparator.<String>comparingInt(id -> score.getOrDefault(id, 0)).reversed()
                        .thenComparing(Comparator.naturalOrder())).toList();
        var actual = new ArrayList<String>();
        for (int pageNo = 1; pageNo <= 3; pageNo++) {
            var counter = fixture.countQueries();
            var page = run(fixture, PAGE, Map.of("datasourceIds", List.of("warehouse", "foreign"),
                    "sortField", "taskCount", "sortDir", "desc", "pageNum", pageNo, "pageSize", pageSize,
                    "includeDatasourceTypeStatistics", true));
            assertThat(((Number) page.get("total")).intValue()).isEqualTo(total);
            var facet = StructuredValues.objectMap(page.get("datasourceTypeStatistics"));
            assertThat(((Number) facet.get("total")).intValue()).isEqualTo(total);
            actual.addAll(ids(page));
            assertThat(counter.sql.stream().filter(sql -> sql.contains("task_family"))).hasSize(1);
            if (pageNo == 1) {
                // 深链查询按深度执行，首屏 20/100 条共享相同的 JDBC 开销。
                assertThat(counter.reads).isEqualTo(28);
            } else {
                assertThat(counter.reads).isLessThanOrEqualTo(28);
            }
        }
        assertThat(actual).containsExactlyElementsOf(expected);

        var ascending = run(fixture, PAGE, Map.of("datasourceIds", List.of("warehouse"),
                "sortField", "taskCount", "sortDir", "asc", "pageSize", pageSize));
        assertThat(ids(ascending)).containsExactlyElementsOf(
                expected.stream().filter(id -> !score.containsKey(id)).limit(pageSize).toList());

        var counter = fixture.countQueries();
        var pending = run(fixture, PAGE, Map.of("datasourceIds", List.of("warehouse"), "accessStatus", "0",
                "sortField", "taskCount", "sortDir", "desc", "pageSize", pageSize));
        assertThat(((Number) pending.get("total")).intValue()).isEqualTo(total - 2);
        assertThat(ids(pending)).startsWith("warehouse-2", "warehouse-3", "warehouse-4");
        assertThat(rows(pending).subList(0, 3)).allSatisfy(row ->
                assertThat(((Number) row.get("taskCount")).intValue()).isZero());
        assertThat(counter.sql.stream().filter(sql -> sql.contains("task_family"))).hasSize(1);

        var accessed = run(fixture, PAGE, Map.of("datasourceIds", List.of("warehouse"), "accessStatus", "1",
                "sortField", "taskCount", "sortDir", "asc", "pageSize", pageSize));
        assertThat(ids(accessed)).containsExactly("warehouse-0", "warehouse-1");
        assertThat(rows(accessed)).allSatisfy(row ->
                assertThat(((Number) row.get("taskCount")).intValue()).isEqualTo(2));

        counter = fixture.countQueries();
        var empty = run(fixture, PAGE, Map.of("datasourceIds", List.of("warehouse"), "sortField", "taskCount",
                "pageNum", 4, "pageSize", pageSize));
        assertThat(rows(empty)).isEmpty();
        assertThat(((Number) empty.get("total")).intValue()).isEqualTo(total);
        assertThat(counter.sql).noneMatch(sql -> sql.contains("task_family"));
    }

    private static void addHop(JdbcTemplate jdbc, String id, String parent, String datasource) {
        jdbc.update("""
                INSERT INTO db_table_t(tid,tenant_id,datasource_id,asset_status,is_del,business_type,table_name,source_table_id)
                VALUES (?,'ga',?,0,0,'业务表',?,?)
                """, id, datasource, id, parent);
    }

    private static void addTask(JdbcTemplate jdbc, String id, String source, String target, String tenant, int deleted) {
        jdbc.update("""
                INSERT INTO data_access_agg_task_t(tid,source_table_id,target_table_id,tenant_id,is_del,updated_time)
                VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)
                """, id, source, target, tenant, deleted);
    }

    private static List<Map<String, Object>> rows(Map<?, ?> page) {
        return StructuredValues.objectRows(page.get("list"));
    }

    private static List<String> ids(Map<?, ?> page) {
        return rows(page).stream().map(row -> (String) row.get("tid")).toList();
    }
}
