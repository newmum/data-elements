package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class DistributionTaskPageMagicTest {
    private static final String ID = "b1813cae042f4fa0a5e08ae30fd00a12";
    private static final String TENANT = "tenant-a";
    private static final String PAGE_PATH = "api/03.数据接入/04.数据分发/01.分发任务分页.ms";

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void pagesCurrentTenantInTwoReadsAndPreservesAllSeventeenFields(int size) throws IOException {
        var fixture = new Fixture();
        fixture.jdbc.execute("""
                INSERT INTO data_distribution_task_t
                SELECT 'task-' || LPAD(n, 3, '0'), 'apply', 'catalog', '目录', 'source', '来源表',
                       'task-code', '分发任务', 'GENERATED', 7, 'NiFi', 'PENDING_INTEGRATION', NULL,
                       NULL, NULL, TIMESTAMP '2026-11-01 12:00:00.123456', TIMESTAMP '2026-11-01 12:01:00.123456',
                       'tenant-a', 0
                  FROM SYSTEM_RANGE(1,105) AS numbers(n)
                """);
        fixture.task("foreign", "tenant-b", 0, "foreign", "foreign", "RUNNING", null);
        fixture.task("deleted", TENANT, 1, "deleted", "deleted", "RUNNING", null);

        var page = fixture.page(Map.of("pageSize", size, "tenantId", "tenant-b", "status", " "));
        assertThat(fixture.counter.reads).isEqualTo(2);
        assertThat(page).containsOnlyKeys("pageNum", "pageSize", "total", "list")
                .containsEntry("pageNum", 1).containsEntry("pageSize", size);
        assertThat(((Number) page.get("total")).intValue()).isEqualTo(105);
        var rows = StructuredValues.objectRows(page.get("list"));
        assertThat(rows).hasSize(size);
        assertThat(rows.getFirst()).containsOnlyKeys("tid", "applyFormId", "catalogId", "catalogName", "sourceTableId",
                "sourceTableName", "taskCode", "taskName", "taskStatus", "progress", "executionEngine",
                "executionMode", "statusMessage", "startedTime", "finishedTime", "createdTime", "updatedTime")
                .containsEntry("tid", "task-001").containsEntry("applyFormId", "apply").containsEntry("catalogName", "目录")
                .containsEntry("sourceTableId", "source").containsEntry("taskStatus", "GENERATED")
                .containsEntry("progress", 7).containsEntry("startedTime", null).containsEntry("statusMessage", null)
                .containsEntry("createdTime", Timestamp.valueOf("2026-11-01 12:00:00.123456"));
        assertThat(rows.getLast()).containsEntry("tid", "task-%03d".formatted(size));
        var second = fixture.page(Map.of("pageNum", 2, "pageSize", size));
        assertThat(ids(second)).hasSize(Math.min(size, 105 - size))
                .startsWith("task-%03d".formatted(size + 1));
        assertThat(fixture.counter.reads).isEqualTo(2);
        System.out.printf("Distribution task page size=%d, SQL=%d%n", size, fixture.counter.reads);
    }

    @Test
    void preservesBoundWildcardSearchAndSortsNullLastWithStableTies() throws IOException {
        var fixture = new Fixture();
        var started = Timestamp.valueOf("2026-11-01 13:00:00");
        fixture.task("null-time", TENANT, 0, "test", "release-A", "RUNNING", null);
        fixture.task("started-b", TENANT, 0, "release-B", "test", "RUNNING", started);
        fixture.task("started-a", TENANT, 0, "release-A", "test", "RUNNING", started);
        fixture.task("started-earlier", TENANT, 0, "release-C", "test", "RUNNING",
                Timestamp.valueOf("2026-11-01 12:59:00"));
        fixture.task("different-status", TENANT, 0, "release-B", "test", "FAILED", started);
        fixture.task("different-keyword", TENANT, 0, "different", "test", "RUNNING", started);
        fixture.task("foreign", "tenant-b", 0, "release-A", "test", "RUNNING", started);
        fixture.task("deleted", TENANT, 1, "release-A", "test", "RUNNING", started);

        assertThat(ids(fixture.page(Map.of("keyword", "release-_", "status", "RUNNING",
                "sortField", "startedTime", "sortDir", "asc"))))
                .containsExactly("started-earlier", "started-a", "started-b", "null-time");
        assertThat(fixture.counter.reads).isEqualTo(2);
        assertThat(ids(fixture.page(Map.of("keyword", "release-%", "status", "RUNNING",
                "sortField", "startedTime", "sortDir", "desc"))))
                .containsExactly("started-a", "started-b", "started-earlier", "null-time");
        assertThat(fixture.counter.reads).isEqualTo(2);
        var empty = fixture.page(Map.of("keyword", "release-_", "status", "validation-nomatch"));
        assertThat(ids(empty)).isEmpty();
        assertThat(((Number) empty.get("total")).intValue()).isZero();
        assertThat(fixture.counter.reads).isEqualTo(1);
    }

    private static List<Object> ids(Map<String, Object> page) {
        return StructuredValues.objectRows(page.get("list")).stream().map(row -> row.get("tid")).toList();
    }

    public record Session(String id) { }

    private static final class Fixture {
        private final JdbcTemplate jdbc;
        private final TaskCategoryWarehouseScopeMagicTest.Counter counter = new TaskCategoryWarehouseScopeMagicTest.Counter();
        private final StructuredTableReadMagicModule tableRead;
        private final MagicScript script;

        Fixture() throws IOException {
            var source = new DriverManagerDataSource("jdbc:h2:mem:distribution_task_" + System.nanoTime()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=LOW", "sa", "");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE data_distribution_task_t (
                        tid VARCHAR PRIMARY KEY, apply_form_id VARCHAR, catalog_id VARCHAR, catalog_name VARCHAR,
                        source_table_id VARCHAR, source_table_name VARCHAR, task_code VARCHAR, task_name VARCHAR,
                        task_status VARCHAR, progress INT, execution_engine VARCHAR, execution_mode VARCHAR,
                        status_message VARCHAR, started_time TIMESTAMP(6), finished_time TIMESTAMP(6),
                        created_time TIMESTAMP(6), updated_time TIMESTAMP(6), tenant_id VARCHAR, is_del INT
                    )
                    """);
            tableRead = TaskRuntimeMagicFixture.structuredReads(jdbc, counter::record);
            var body = CanonicalMagicSources.byPath(PAGE_PATH, ID).replaceAll("(?m)^import [^\\r\\n]+\\R", "");
            script = MagicScript.create(body, null);
        }

        void task(String id, String tenant, int deleted, String name, String code, String status, Timestamp started) {
            jdbc.update("""
                    INSERT INTO data_distribution_task_t
                        (tid, tenant_id, is_del, task_name, task_code, task_status, started_time, created_time)
                    VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                    """, id, tenant, deleted, name, code, status, started);
        }

        Map<String, Object> page(Map<String, Object> body) {
            counter.reads = 0;
            counter.sql.clear();
            var context = new MagicScriptContext(Map.of(
                    "tableRead", tableRead, "body", body, "tenantRuntime", new Session(TENANT),
                    "tableSort", new TableSortMagicModule()));
            try (var ignored = TenantContext.use(TENANT)) {
                return StructuredValues.objectMap(script.execute(context));
            }
        }
    }
}
