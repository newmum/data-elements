package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.platform.magic.module.JsonModule;
import com.linewell.dataelement.utils.StructuredValues;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class AccessTaskPageMagicTest {
    private static final String RESOURCE = "1155bcd0ccad47b49a6c4a94251cf52c";
    private static final String TENANT = "tenant-a";
    private static final Scope ALL = new Scope("ALL", "owner-a", List.of());
    private static final Scope OWNER = new Scope("OWNER", "owner-a", List.of());
    private static final Scope ORG = new Scope("ORG", "owner-a", List.of("org-a"));
    private static final String DSL = """
            {"nodes":[
              {"manifestKey":"source.jdbc","config":{"database":"legacy-source","table":"source_table","schedulingPeriod":"5 sec"}},
              {"category":"target","config":{"jdbcUrl":"jdbc:mysql://validation.example/legacy-target?x=1","targetTable":"target_table"}}
            ]}
            """;

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void pagesPrimaryTasksInFiveReadsWithStableOrderAndEnrichment(int size) throws IOException {
        var fixture = new Fixture();
        fixture.jdbc.execute("""
                INSERT INTO data_access_agg_task_t (tid, tenant_id, is_del, task_name, source_db_id, source_table_id,
                    target_db_id, target_table_id, task_status, source_table_primary_key, source_table_increment_key,
                    schedule_strategy, queued_count, monitor_status, monitor_msg, data_catalog_id, updated_time)
                SELECT 'task-' || LPAD(n, 3, '0'), 'tenant-a', 0, 'validation-job', 'source-a', 'source-table',
                    'target-a', 'target-table', 1, 'pk', 'ts', 0, 12, 'SUCCESS', '已采集', 'catalog-a',
                    TIMESTAMP '2026-11-01 12:00:00.123456' FROM SYSTEM_RANGE(1,105) AS numbers(n)
                """);
        fixture.task("foreign", "tenant-b", 0, "validation-job", "source-a", null, null, 12);
        fixture.task("deleted", TENANT, 1, "validation-job", "source-a", null, null, 12);

        var filters = Map.<String, Object>of("pageSize", size, "taskName", "validation-_ob", "sortField", "queuedCount",
                "sortDir", "asc", "nifiNetworkCode", "network-a", "nifiNodeId", "node-default", "tenantId", "tenant-b");
        var page = fixture.page(OWNER, filters);
        assertEnvelope(page, 1, size, 105);
        assertThat(ids(page)).hasSize(size).startsWith("task-001").endsWith("task-%03d".formatted(size));
        assertThat(fixture.counter.reads).isEqualTo(5);
        var row = rows(page).getFirst();
        assertThat(row).containsEntry("taskName", "validation-job").containsEntry("tenantId", TENANT)
                .containsEntry("dataCatalogId", "catalog-a").containsEntry("storedTaskStatus", 1)
                .containsEntry("taskStatus", 0).containsEntry("runtimeState", "UNDEPLOYED")
                .containsEntry("sourceDbName", "来源库").containsEntry("targetDbName", "目标库")
                .containsEntry("sourceTableSummary", "来源表").containsEntry("targetTableSummary", "目标表")
                .containsEntry("targetPhysicalTableName", "physical_target").containsEntry("scheduleFrequency", "实时")
                .containsEntry("nifiNodeId", "node-default").containsEntry("nifiNetworkCode", "network-a")
                .containsEntry("nifiNodeName", "默认节点");
        var table = StructuredValues.objectRows(row.get("syncTables")).getFirst();
        assertThat(table).containsEntry("taskId", "task-001").containsEntry("primaryKey", "pk")
                .containsEntry("incrementKey", "ts").containsEntry("targetPhysicalTableName", "physical_target")
                .containsEntry("syncStatus", 0).containsEntry("syncStatusText", "未部署");
        assertThat(((Number) table.get("dataCount")).longValue()).isEqualTo(42);
        var second = fixture.page(OWNER, Map.of("pageNum", 2, "pageSize", size));
        assertEnvelope(second, 2, size, 105);
        assertThat(ids(second)).hasSize(Math.min(size, 105 - size)).startsWith("task-%03d".formatted(size + 1));
        assertThat(fixture.counter.reads).isEqualTo(5);
        System.out.printf("Access primary page size=%d, SQL reads=%d%n", size, fixture.counter.reads);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void pagesLegacyPipelinesOnlyForAllScopeWithStableDslResponse(int size) throws IOException {
        var fixture = new Fixture();
        fixture.jdbc.update("""
                INSERT INTO nifi_pipeline_t (id, tenant_id, is_del, name, status, description, dsl_json,
                    last_deployed_at, last_stopped_at, created_at, updated_at)
                SELECT 'pipe-' || LPAD(n, 3, '0'), 'tenant-a', 0, 'LEGACY-JOB', 'STOPPED', 'legacy', ?, 1000, 1100, 900, 1200
                    FROM SYSTEM_RANGE(1,105) AS numbers(n)
                """, DSL);
        fixture.pipeline("foreign", "tenant-b", 0, "legacy-job", "RUNNING", "running", null);
        fixture.pipeline("deleted", TENANT, 1, "legacy-job", "RUNNING", "running", null);
        fixture.task("foreign-task", "tenant-b", 0, "validation", "source-a", null, null, null);
        fixture.task("deleted-task", TENANT, 1, "validation", "source-a", null, null, null);

        var page = fixture.page(ALL, Map.of("pageSize", size, "taskName", "legacy-_ob", "sortField", "flowFilesIn"));
        assertEnvelope(page, 1, size, 105);
        assertThat(ids(page)).hasSize(size).startsWith("pipe-001").endsWith("pipe-%03d".formatted(size));
        assertThat(fixture.counter.reads).isEqualTo(5);
        var row = rows(page).getFirst();
        assertThat(row).containsEntry("recordSource", "pipeline").containsEntry("id", "pipe-001")
                .containsEntry("pipelineId", "pipe-001").containsEntry("lifecycleStatus", "STOPPED")
                .containsEntry("currentStatus", "STOPPED").containsEntry("taskStatus", 0)
                .containsEntry("sourceDbName", "legacy-source").containsEntry("sourceTableName", "source_table")
                .containsEntry("targetDbName", "legacy-target").containsEntry("targetTableName", "target_table")
                .containsEntry("scheduleFrequency", "5 sec").containsEntry("taskDesc", "legacy");
        assertThat(StructuredValues.objectRows(row.get("syncTables"))).singleElement().satisfies(table ->
                assertThat(table).containsEntry("taskId", "pipe-001").containsEntry("syncStatusText", "未部署"));
        assertThat(ids(fixture.page(ALL, Map.of("pageNum", 2, "pageSize", size))))
                .hasSize(Math.min(size, 105 - size)).startsWith("pipe-%03d".formatted(size + 1));
        assertThat(fixture.counter.reads).isEqualTo(5);
        for (Scope scope : List.of(ORG, OWNER)) {
            var restricted = fixture.page(scope, Map.of("pageSize", size));
            assertEnvelope(restricted, 1, size, 0);
            assertThat(ids(restricted)).isEmpty();
            assertThat(fixture.counter.reads).isEqualTo(2);
        }
        System.out.printf("Access fallback page size=%d, populated SQL reads=5%n", size);
    }

    @Test
    void restrictsSourcesAndNeverFallsBackWhenAnyActivePrimaryTaskExists() throws IOException {
        var fixture = new Fixture();
        fixture.pipeline("legacy", TENANT, 0, "find-legacy", "STOPPED", null, null);
        fixture.task("own", TENANT, 0, "normal", "source-a", null, null, 4);
        fixture.task("org", TENANT, 0, "normal", "source-org", null, null, 4);
        fixture.task("other-org", TENANT, 0, "normal", "source-other", null, null, 4);
        fixture.task("orphan", TENANT, 0, "normal", "missing", null, null, null);
        fixture.task("foreign-source", TENANT, 0, "normal", "source-foreign", null, null, 4);
        fixture.task("deleted-source", TENANT, 0, "normal", "source-deleted", null, null, 4);
        fixture.task("foreign-task", "tenant-b", 0, "normal", "source-a", null, null, 4);
        fixture.task("deleted-task", TENANT, 1, "normal", "source-a", null, null, 4);
        fixture.jdbc.execute("""
                UPDATE data_access_agg_task_t SET target_db_id='source-foreign', target_table_id='foreign-table',
                    source_table_id='deleted-table' WHERE tid='own'
                """);

        var sort = Map.<String, Object>of("sortField", "queuedCount", "sortDir", "asc");
        assertThat(ids(fixture.page(ALL, sort))).containsExactly("deleted-source", "foreign-source", "org", "other-org", "own", "orphan");
        assertThat(ids(fixture.page(ORG, sort))).containsExactly("org", "own");
        var owned = fixture.page(OWNER, sort);
        assertThat(ids(owned)).containsExactly("own");
        assertThat(rows(owned).getFirst()).containsEntry("sourceDbName", "来源库")
                .containsEntry("sourceTableName", null).containsEntry("targetDbName", null)
                .containsEntry("targetTableName", null).containsEntry("targetPhysicalTableName", null);

        assertEnvelope(fixture.page(ALL, Map.of("pageNum", 2, "pageSize", 20)), 2, 20, 6);
        assertThat(fixture.counter.reads).isEqualTo(2);
        var distantPage = fixture.page(ALL, Map.of("pageNum", 2_147_483_648L));
        assertThat(((Number) distantPage.get("pageNum")).longValue()).isEqualTo(2_147_483_648L);
        assertThat(((Number) distantPage.get("total")).longValue()).isEqualTo(6);
        assertThat(ids(distantPage)).isEmpty();
        assertThat(fixture.counter.reads).isEqualTo(2);
        assertEnvelope(fixture.page(ALL, Map.of("taskName", "find-legacy")), 1, 20, 0);
        assertThat(fixture.counter.reads).isEqualTo(3);
        assertEnvelope(fixture.page(new Scope("ORG", "owner-a", List.of()), Map.of()), 1, 20, 0);
        assertThat(fixture.counter.reads).isEqualTo(2);

        fixture.jdbc.update("UPDATE data_access_agg_task_t SET is_del=1 WHERE tid='own'");
        assertEnvelope(fixture.page(OWNER, Map.of()), 1, 20, 0);
        assertThat(fixture.counter.reads).isEqualTo(2);
    }

    @Test
    void filtersFromLiveSnapshotAndKeepsActivePipelinePrecedenceEvenWhenGroupIsNull() throws IOException {
        var fixture = new Fixture();
        fixture.pipeline("pipe-running", TENANT, 0, "pipeline", "STOPPED", "running", null);
        fixture.pipeline("pipe-stopped", TENANT, 0, "pipeline", "RUNNING", "stopped", null);
        fixture.pipeline("pipe-failed", TENANT, 0, "pipeline", "DEPLOY_FAILED", null, null);
        fixture.pipeline("pipe-stale-running", TENANT, 0, "pipeline", "RUNNING", null, null);
        fixture.pipeline("pipe-null-group", TENANT, 0, "pipeline", null, null, null);
        fixture.pipeline("pipe-foreign", "tenant-b", 0, "pipeline", "RUNNING", "stopped", null);
        fixture.pipeline("pipe-deleted", TENANT, 1, "pipeline", "RUNNING", "stopped", null);
        fixture.task("a-live", TENANT, 0, "job", "source-a", "pipe-running", "stopped", 4);
        fixture.task("b-stopped", TENANT, 0, "job", "source-a", "pipe-stopped", "running", 4);
        fixture.task("c-failed", TENANT, 0, "job", "source-a", "pipe-failed", "running", 4);
        fixture.task("d-pending", TENANT, 0, "job", "source-a", "pipe-stale-running", "running", 4);
        fixture.task("e-unassigned", TENANT, 0, "job", "source-a", "pipe-null-group", "running", 4);
        fixture.task("f-foreign-pipe", TENANT, 0, "job", "source-a", "pipe-foreign", "running", 4);
        fixture.task("g-deleted-pipe", TENANT, 0, "job", "source-a", "pipe-deleted", "invalid", 4);
        fixture.task("h-missing", TENANT, 0, "job", "source-a", null, "missing", 4);
        fixture.task("i-unreadable", TENANT, 0, "job", "source-a", null, "unreadable", 4);
        fixture.task("j-second-chunk", TENANT, 0, "job", "source-a", null, "running-499", 4);
        fixture.addGroup("running", "RUNNING", true);
        fixture.addGroup("stopped", "STOPPED", true);
        fixture.addGroup("invalid", "INVALID", true);
        fixture.addGroup("unreadable", "RUNNING", false);
        for (int i = 0; i < 500; i++) fixture.addGroup("running-" + i, "RUNNING", true);

        assertThat(ids(fixture.page(ALL, Map.of("taskStatus", "0")))).containsExactly("b-stopped", "e-unassigned");
        var running = fixture.page(ALL, Map.of("taskStatus", "1"));
        assertThat(ids(running)).containsExactly("a-live", "f-foreign-pipe", "j-second-chunk");
        assertThat(rows(running).getFirst()).containsEntry("lifecycleStatus", "STOPPED")
                .containsEntry("runtimeProcessGroupId", "running").containsEntry("taskStatus", 1)
                .containsEntry("storedTaskStatus", 0).containsEntry("runtimeState", "RUNNING");
        assertThat(ids(fixture.page(ALL, Map.of("taskStatus", "2")))).containsExactly("c-failed", "g-deleted-pipe");
        assertThat(ids(fixture.page(ALL, Map.of("taskStatus", "3")))).containsExactly("d-pending", "h-missing", "i-unreadable");
        var all = fixture.page(ALL, Map.of());
        assertThat(rows(all).get(4)).containsEntry("runtimeProcessGroupId", null).containsEntry("taskStatus", 0);
        assertThat(rows(all).get(5)).containsEntry("lifecycleStatus", null).containsEntry("runtimeProcessGroupId", "running");
    }

    @Test
    void filtersLegacyStatusAndRejectsWrongNodeAndInvalidRequests() throws IOException {
        var fixture = new Fixture();
        fixture.pipeline("a-running", TENANT, 0, "PIPE-A", "STOPPED", "running", null);
        fixture.pipeline("b-invalid", TENANT, 0, "PIPE-B", "DEPLOY_FAILED", null, null);
        fixture.pipeline("c-unknown", TENANT, 0, "PIPE-C", "RUNNING", null, null);
        fixture.pipeline("d-unassigned", TENANT, 0, "PIPE-D", null, null, null);
        fixture.jdbc.update("UPDATE nifi_pipeline_t SET last_deployed_at=1000 WHERE id IN ('b-invalid', 'c-unknown')");
        fixture.addGroup("running", "RUNNING", true);
        assertThat(ids(fixture.page(ALL, Map.of("sortField", "lastRunning", "sortDir", "asc"))))
                .containsExactly("a-running", "d-unassigned", "b-invalid", "c-unknown");
        assertThat(ids(fixture.page(ALL, Map.of("sortField", "lastRunning", "sortDir", "desc"))))
                .containsExactly("b-invalid", "c-unknown", "a-running", "d-unassigned");
        assertThat(ids(fixture.page(ALL, Map.of("taskStatus", "0")))).containsExactly("d-unassigned");
        assertThat(ids(fixture.page(ALL, Map.of("taskStatus", "1")))).containsExactly("a-running");
        assertThat(ids(fixture.page(ALL, Map.of("taskStatus", "2")))).containsExactly("b-invalid");
        assertThat(ids(fixture.page(ALL, Map.of("taskStatus", "3")))).containsExactly("c-unknown");
        assertThat(ids(fixture.page(ALL, Map.of("nifiNodeId", "foreign-default", "pageSize", 101)))).isEmpty();
        assertThat(fixture.counter.reads).isOne();
        assertThat(assertInstanceOf(ExitValue.class, fixture.run(ALL, Map.of("taskStatus", "invalid"))).getValues()[0]).isEqualTo(400);
        assertThat(assertInstanceOf(ExitValue.class, fixture.run(new Scope("UNKNOWN", "owner-a", List.of()), Map.of())).getValues()[0]).isEqualTo(403);
        assertThatThrownBy(() -> fixture.run(ALL, Map.of("sortField", "invalid")))
                .hasStackTraceContaining("不支持的排序字段");
    }

    private static List<Map<String, Object>> rows(Map<String, Object> page) {
        return StructuredValues.objectRows(page.get("list"));
    }

    private static List<Object> ids(Map<String, Object> page) {
        return rows(page).stream().map(row -> row.get("tid")).toList();
    }

    private static void assertEnvelope(Map<String, Object> page, int pageNumber, int size, long total) {
        assertThat(page).containsOnlyKeys("pageNum", "pageSize", "total", "list", "runtimeRevision", "runtimeFresh",
                "runtimeObservedAt", "runtimeMessage").containsEntry("runtimeFresh", true);
        assertThat(((Number) page.get("pageNum")).intValue()).isEqualTo(pageNumber);
        assertThat(((Number) page.get("pageSize")).intValue()).isEqualTo(size);
        assertThat(((Number) page.get("total")).longValue()).isEqualTo(total);
    }

    public record Session(String id) { }

    public record Scope(String mode, String userId, List<String> organizations) {
        public Map<String, Object> dataSource() {
            return Map.of("scope", mode, "userId", userId, "organizationIds", organizations);
        }
    }

    private static final class Fixture {
        private final TaskCategoryWarehouseScopeMagicTest.Counter counter = new TaskCategoryWarehouseScopeMagicTest.Counter();
        private final JdbcTemplate jdbc;
        private final SQLModule db;
        private final StructuredTableReadMagicModule tableRead;
        private final MagicScript script;
        private final JsonNode runtimeResponse = TaskRuntimeMagicFixture.emptyRootStatus();
        private final ArrayNode groups = ((ObjectNode) runtimeResponse.at("/processGroupStatus/aggregateSnapshot"))
                .putArray("processGroupStatusSnapshots");

        Fixture() throws IOException {
            var source = new DriverManagerDataSource("jdbc:h2:mem:access_task_page_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE data_access_agg_task_t (
                        tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, is_del INT, task_name VARCHAR, task_desc VARCHAR,
                        source_db_id VARCHAR, target_db_id VARCHAR, source_table_id VARCHAR, target_table_id VARCHAR,
                        source_table_primary_key VARCHAR, source_table_increment_key VARCHAR,
                        schedule_strategy INT, schedule_cycle INT, schedule_running VARCHAR, cron_express VARCHAR,
                        last_running VARCHAR, end_running VARCHAR, created_time TIMESTAMP(6), updated_time TIMESTAMP(6),
                        pipeline_id VARCHAR, process_group_id VARCHAR, task_status INT, monitor_status VARCHAR, monitor_msg VARCHAR,
                        flow_files_in BIGINT, flow_files_out BIGINT, queued_count BIGINT, bytes_in BIGINT, bytes_out BIGINT,
                        active_thread_count INT, monitor_time TIMESTAMP(6), data_catalog_id VARCHAR
                    );
                    CREATE TABLE db_datasource_t (tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, is_del INT,
                        db_name VARCHAR, created_by VARCHAR, org_id VARCHAR);
                    CREATE TABLE db_table_t (tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, is_del INT,
                        datasource_id VARCHAR, table_name VARCHAR, table_name_cn VARCHAR, record_count BIGINT);
                    CREATE TABLE nifi_node_t (tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, is_del INT, enabled INT,
                        is_default INT, updated_time TIMESTAMP, network_code VARCHAR, node_code VARCHAR, node_name VARCHAR);
                    CREATE TABLE nifi_pipeline_t (id VARCHAR PRIMARY KEY, tenant_id VARCHAR, is_del INT, name VARCHAR,
                        description VARCHAR, status VARCHAR, dsl_json CLOB, nifi_process_group_id VARCHAR,
                        last_deployed_at BIGINT, last_stopped_at BIGINT, created_at BIGINT, updated_at BIGINT);
                    INSERT INTO db_datasource_t VALUES
                        ('source-a','tenant-a',0,'来源库','owner-a','org-a'), ('target-a','tenant-a',0,'目标库','owner-b','org-b'),
                        ('source-org','tenant-a',0,'部门库','owner-b','org-a'), ('source-other','tenant-a',0,'其他库','owner-b','org-b'),
                        ('source-foreign','tenant-b',0,'其他租户库','owner-a','org-a'), ('source-deleted','tenant-a',1,'已删除库','owner-a','org-a');
                    INSERT INTO db_table_t VALUES
                        ('source-table','tenant-a',0,'source-a','physical_source','来源表',42),
                        ('target-table','tenant-a',0,'target-a','physical_target','目标表',30),
                        ('deleted-table','tenant-a',1,'source-a','deleted_physical','已删除表',99),
                        ('foreign-table','tenant-b',0,'source-foreign','foreign_physical','其他租户表',99);
                    INSERT INTO nifi_node_t VALUES
                        ('node-default','tenant-a',0,1,1,TIMESTAMP '2026-11-01 12:00:00','network-a','default','默认节点'),
                        ('node-newer','tenant-a',0,1,0,TIMESTAMP '2026-11-01 13:00:00','network-b','newer','较新节点'),
                        ('node-disabled','tenant-a',0,0,1,TIMESTAMP '2026-11-01 13:00:00','network-b','disabled','停用节点'),
                        ('node-deleted','tenant-a',1,1,1,TIMESTAMP '2026-11-01 13:00:00','network-b','deleted','已删除节点'),
                        ('foreign-default','tenant-b',0,1,1,TIMESTAMP '2026-11-01 13:00:00','network-b','foreign','其他租户节点');
                    """);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            var mappings = new ColumnMapperAdapter();
            mappings.add(new DefaultColumnMapperProvider());
            mappings.add(new CamelColumnMapperProvider());
            mappings.setDefault("camel");
            db.setColumnMapperProvider(mappings);
            db.setColumnMapRowMapper(mappings.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setSqlInterceptors(List.of(counter));
            db.setNamedTableInterceptors(List.of());
            tableRead = TaskRuntimeMagicFixture.structuredReads(jdbc, counter::record);
            script = MagicScript.create(CanonicalMagicSources.byId(RESOURCE)
                    .replaceAll("(?m)^import (?!java\\.|org\\.)[^\\r\\n]+\\R", ""), null);
        }

        void task(String id, String tenant, int deleted, String name, String source, String pipeline, String group, Integer queued) {
            jdbc.update("""
                    INSERT INTO data_access_agg_task_t (tid, tenant_id, is_del, task_name, source_db_id, pipeline_id,
                        process_group_id, queued_count, task_status, updated_time) VALUES (?,?,?,?,?,?,?,?,0,TIMESTAMP '2026-11-01 12:00:00')
                    """, id, tenant, deleted, name, source, pipeline, group, queued);
        }

        void pipeline(String id, String tenant, int deleted, String name, String status, String group, String dsl) {
            jdbc.update("""
                    INSERT INTO nifi_pipeline_t (id, tenant_id, is_del, name, status, nifi_process_group_id, dsl_json, updated_at)
                    VALUES (?,?,?,?,?,?,?,1000)
                    """, id, tenant, deleted, name, status, group, dsl);
        }

        void addGroup(String id, String status, boolean readable) {
            var snapshot = groups.addObject().put("canRead", readable).putObject("processGroupStatusSnapshot");
            snapshot.put("id", id).putArray("processorStatusSnapshots").addObject()
                    .putObject("processorStatusSnapshot").put("runStatus", status);
        }

        Object run(Scope scope, Map<String, Object> body) {
            counter.reads = 0;
            counter.sql.clear();
            try (var runtime = new TaskRuntimeMagicFixture(TENANT, runtimeResponse)) {
                var context = new MagicScriptContext(Map.of("db", db, "body", body, "tableRead", tableRead,
                        "tableSort", new TableSortMagicModule(), "taskRuntime", runtime.module, "jsons", new JsonModule(),
                        "tenantRuntime", new Session(TENANT), "dataScope", scope));
                var result = script.execute(context);
                if (result instanceof Map<?, ?> page && page.containsKey("runtimeRevision")) runtime.verifySingleRootRead();
                return result;
            }
        }

        Map<String, Object> page(Scope scope, Map<String, Object> body) {
            return StructuredValues.objectMap(assertInstanceOf(Map.class, run(scope, body)));
        }
    }
}
