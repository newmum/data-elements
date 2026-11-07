package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class TaskMonitorSnapshotPageMagicTest {
    private static final String ID = "f541570df3bc4bf3a38e9466d2e16cd7";

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void pagesScopedSnapshotsInTwoReadsAndKeepsTheLegacyKeys(int pageSize) throws IOException {
        var fixture = new Fixture();
        fixture.jdbc.execute("""
                INSERT INTO data_access_task_monitor_snap_t
                    (tid, tenant_id, task_id, pipeline_id, process_group_id, monitor_time, delay_level, monitor_status,
                     monitor_msg, queued_count, flow_files_in, flow_files_out, bytes_in, bytes_out, active_thread_count, is_del)
                SELECT 'snap-' || LPAD(n, 3, '0'), 'tenant-a', 'task-a', 'pipeline-a', 'process-a',
                       TIMESTAMP '2026-11-01 12:00:00.123456', 'WARN', 'SUCCESS', '已采集', 5, 7, 6, 100, 99, 2, 0
                  FROM SYSTEM_RANGE(1,105) AS numbers(n)
                """);
        fixture.snapshot("snap-orphan", "tenant-a", "missing-task", "2026-11-01 12:00:00.123456", 0);
        fixture.snapshot("snap-other", "tenant-b", "task-a", "2026-11-01 12:00:00.123456", 0);
        fixture.snapshot("snap-deleted", "tenant-a", "task-a", "2026-11-01 12:00:00.123456", 1);
        fixture.snapshot("snap-null-time", "tenant-a", "task-a", null, 0);

        var filters = Map.<String, Object>ofEntries(Map.entry("pageSize", pageSize),
                Map.entry("taskId", "task-a"), Map.entry("pipelineId", "pipeline-a"), Map.entry("processGroupId", "process-a"),
                Map.entry("delayLevel", "WARN"), Map.entry("monitorStatus", "SUCCESS"),
                Map.entry("beginTime", "2026-11-01T12:00:00.123456"), Map.entry("endTime", "2026-11-01 12:00:00.123456"),
                Map.entry("sortField", "queuedCount"), Map.entry("sortDir", "asc"), Map.entry("tenantId", "tenant-b"));
        var result = fixture.page("OWNER", List.of(), filters);
        assertThat(fixture.source.reads).isEqualTo(2);
        assertThat(result).containsOnlyKeys("total", "list");
        assertThat(result.get("total")).isEqualTo(105L);
        var rows = rows(result);
        assertThat(rows).hasSize(pageSize);
        assertThat(rows.getFirst()).containsOnlyKeys("tid", "taskId", "pipelineId", "processGroupId", "monitorTime",
                "delayLevel", "delayMsEstimate", "thresholdMs", "isTimeout", "queuedCount", "queuedBytes",
                "activeThreadCount", "flowFilesIn", "flowFilesOut", "bytesIn", "bytesOut", "monitorStatus", "monitorMsg",
                "createdTime", "isDel", "tenantId", "monitortime", "monitorstatus", "monitormsg", "delaylevel", "queuedcount",
                "flowfilesin", "flowfilesout", "bytesin", "bytesout", "activethreadcount");
        assertThat(rows.getFirst()).containsEntry("tid", "snap-001").containsEntry("tenantId", "tenant-a")
                .containsEntry("monitorMsg", "已采集").containsEntry("monitormsg", "已采集")
                .containsEntry("monitortime", rows.getFirst().get("monitorTime"));
        assertThat(rows.getLast()).containsEntry("tid", "snap-%03d".formatted(pageSize));
    }

    @Test
    void preservesAllOrphansButRestrictedScopesRequireActiveSameTenantAssociations() throws IOException {
        var fixture = new Fixture();
        fixture.jdbc.execute("""
                INSERT INTO data_access_task_monitor_snap_t (tid, tenant_id, task_id, queued_count, is_del) VALUES
                ('visible-owner','tenant-a','task-a',2,0), ('visible-org','tenant-a','task-org',4,0),
                ('null-metric','tenant-a','task-a',NULL,0), ('deleted-snapshot','tenant-a','task-a',9,1),
                ('orphan','tenant-a','missing-task',3,0), ('foreign-task','tenant-a','task-foreign',3,0),
                ('foreign-source','tenant-a','task-foreign-source',3,0),
                ('deleted-source','tenant-a','task-deleted-source',3,0), ('deleted-task','tenant-a','task-deleted',3,0),
                ('other-org','tenant-a','task-other-org',3,0), ('other-tenant','tenant-b','task-a',3,0)
                """);
        var sort = Map.<String, Object>of("sortField", "queuedCount", "sortDir", "desc");
        var all = fixture.page("ALL", List.of(), sort);
        assertThat(ids(all)).containsExactly("visible-org", "deleted-source", "deleted-task", "foreign-source", "foreign-task",
                "orphan", "other-org", "visible-owner", "null-metric");
        assertThat(fixture.source.reads).isEqualTo(2);

        assertThat(ids(fixture.page("ORG", List.of("org-a"), sort))).containsExactly("visible-org", "visible-owner", "null-metric");
        assertThat(fixture.source.reads).isEqualTo(2);
        assertThat(ids(fixture.page("OWNER", List.of(), sort))).containsExactly("visible-owner", "null-metric");
        assertThat(fixture.source.reads).isEqualTo(2);
        assertThat(fixture.page("ORG", List.of(), sort)).containsEntry("total", 0L).containsEntry("list", List.of());
        assertThat(fixture.source.reads).isEqualTo(1);

        assertThat(fixture.page("ALL", List.of(), Map.of("pageNum", 2147483648L)))
                .containsEntry("total", 9L).containsEntry("list", List.of());
        assertThat(fixture.source.reads).isEqualTo(1);
        assertThat(assertInstanceOf(ExitValue.class, fixture.run("ALL", List.of(), Map.of("endTime", "invalid"))).getValues()[0])
                .isEqualTo(400);
        assertThat(fixture.source.reads).isZero();
    }

    private static List<Map<String, Object>> rows(Map<String, Object> page) {
        return ((List<?>) page.get("list")).stream().map(StructuredValues::objectMap).toList();
    }

    private static List<Object> ids(Map<String, Object> page) {
        return rows(page).stream().map(row -> row.get("tid")).toList();
    }

    public record Session(String id) { }

    public record Scope(String mode, List<String> orgIds) {
        public Map<String, Object> dataSource() {
            return Map.of("scope", mode, "userId", "owner-a", "organizationIds", orgIds);
        }
    }

    private static final class Fixture {
        private final CountedSource source = new CountedSource();
        private final JdbcTemplate jdbc = new JdbcTemplate(source);
        private final StructuredTableReadMagicModule tableRead = new StructuredTableReadMagicModule(jdbc);
        private final TableSortMagicModule tableSort = new TableSortMagicModule();
        private final MagicScript script;

        Fixture() throws IOException {
            jdbc.execute("""
                    CREATE TABLE data_access_task_monitor_snap_t (
                        tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, task_id VARCHAR, pipeline_id VARCHAR,
                        process_group_id VARCHAR, monitor_time TIMESTAMP(6), delay_level VARCHAR, delay_ms_estimate BIGINT,
                        threshold_ms BIGINT, is_timeout INT, queued_count BIGINT, queued_bytes BIGINT, active_thread_count INT,
                        flow_files_in BIGINT, flow_files_out BIGINT, bytes_in BIGINT, bytes_out BIGINT, monitor_status VARCHAR,
                        monitor_msg VARCHAR, created_time TIMESTAMP(6), is_del INT)
                    """);
            jdbc.execute("CREATE TABLE data_access_agg_task_t (tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, source_db_id VARCHAR, is_del INT)");
            jdbc.execute("CREATE TABLE db_datasource_t (tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, created_by VARCHAR, org_id VARCHAR, is_del INT)");
            jdbc.execute("""
                    INSERT INTO data_access_agg_task_t VALUES
                    ('task-a','tenant-a','source-a',0), ('task-org','tenant-a','source-org',0),
                    ('task-deleted','tenant-a','source-a',1), ('task-deleted-source','tenant-a','source-deleted',0),
                    ('task-foreign','tenant-b','source-a',0), ('task-foreign-source','tenant-a','source-foreign',0),
                    ('task-other-org','tenant-a','source-other-org',0)
                    """);
            jdbc.execute("""
                    INSERT INTO db_datasource_t VALUES
                    ('source-a','tenant-a','owner-a','org-a',0), ('source-org','tenant-a','owner-b','org-a',0),
                    ('source-deleted','tenant-a','owner-a','org-a',1), ('source-foreign','tenant-b','owner-a','org-a',0),
                    ('source-other-org','tenant-a','owner-b','org-b',0)
                    """);
            String body = CanonicalMagicSources.byId(ID)
                    .replaceAll("(?m)^import (tableSort|tableRead|tenantRuntime|dataScope)\\R", "");
            script = MagicScript.create(body, null);
        }

        void snapshot(String tid, String tenant, String task, String time, int deleted) {
            jdbc.update("INSERT INTO data_access_task_monitor_snap_t (tid, tenant_id, task_id, monitor_time, is_del) VALUES (?,?,?,?,?)",
                    tid, tenant, task, time, deleted);
        }

        Object run(String mode, List<String> orgIds, Map<String, Object> filters) {
            source.reads = 0;
            var vars = Map.of("body", filters, "tenantRuntime", new Session("tenant-a"),
                    "dataScope", new Scope(mode, orgIds), "tableRead", tableRead, "tableSort", tableSort);
            return TenantContext.call("tenant-a", () -> script.execute(new MagicScriptContext(vars)));
        }

        Map<String, Object> page(String mode, List<String> orgIds, Map<String, Object> filters) {
            return StructuredValues.objectMap(assertInstanceOf(Map.class, run(mode, orgIds, filters)));
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        private int reads;

        CountedSource() {
            super("jdbc:h2:mem:monitor_snap_page_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection actual = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("getMetaData".equals(method.getName())) {
                            var metadata = actual.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (ignored, metaMethod, metaArgs) -> "getURL".equals(metaMethod.getName())
                                            ? "jdbc:mysql://monitor-snapshot-validation" : invoke(metadata, metaMethod, metaArgs));
                        }
                        if ("prepareStatement".equals(method.getName())
                                && ((String) args[0]).stripLeading().regionMatches(true, 0, "SELECT", 0, 6)) reads++;
                        return invoke(actual, method, args);
                    });
        }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }
    }
}
