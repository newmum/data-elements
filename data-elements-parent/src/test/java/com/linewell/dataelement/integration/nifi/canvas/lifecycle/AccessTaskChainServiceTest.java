package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.DslHasher;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

class AccessTaskChainServiceTest {
    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void twentyOrHundredTasksUseFiveReadsAndOneScopeDecisionWithoutNifi(int count) throws Exception {
        Fixture fixture = new Fixture();
        for (int i = 0; i < count; i++) fixture.task("t" + i, "root", "out" + i, "source", "target");
        clearInvocations(fixture.jdbc);
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.service.byTask("t0");
            assertThat(items(graph)).hasSize(count);
            assertThat(graph).containsEntry("rootSourceTableId", "root").containsEntry("selectedPipelineId", "p-t0");
        }
        verify(fixture.jdbc, times(5)).query(any(PreparedStatementCreator.class), any(ColumnMapRowMapper.class));
        verify(fixture.scopes, times(1)).visibleDataSources(eq("MENU:2081000000000000002"), any());
        verifyNoMoreInteractions(fixture.scopes);
        verifyNoInteractions(fixture.nifi);
    }

    @Test
    void twoRealHopTasksReturnOneChainAndPreserveIndependentDslIds() throws Exception {
        Fixture fixture = new Fixture();
        fixture.task("first", "root", "ods", "source", "target");
        fixture.task("second", "ods", "dst", "target", "target");
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.service.byPipeline("p-second");
            assertThat(graph).containsEntry("rootSourceTableId", "root").containsEntry("selectedTaskId", "second");
            assertThat(items(graph)).extracting(item -> item.get("pipelineId")).containsExactly("p-first", "p-second");
            assertThat(graph.get("links")).isEqualTo(List.of(Map.of("fromTaskId", "first", "toTaskId", "second", "tableId", "ods")));
            assertThat(((Map<?, ?>) graph.get("nativeCanvas")).get("mode")).isEqualTo("SELECT_TASK");
        }
        verifyNoInteractions(fixture.nifi);
    }

    @Test
    void undeployedTaskHasNoNativeEntryAndOpeningCanvasDoesNotSave() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("first", "root", "ods", "source", "target");
        fixture.jdbc.update("update nifi_pipeline_t set nifi_process_group_id=null, last_deployed_hash=null");
        clearInvocations(fixture.jdbc);
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.service.byTask("first");
            assertThat(items(graph).getFirst()).doesNotContainKeys("nativeUrl", "processGroupId").containsKey("unavailableReason");
            assertThat(((Map<?, ?>) graph.get("nativeCanvas")).get("mode")).isEqualTo("NONE");
        }
        verify(fixture.jdbc, times(5)).query(any(PreparedStatementCreator.class), any(ColumnMapRowMapper.class));
        assertThat(org.mockito.Mockito.mockingDetails(fixture.jdbc).getInvocations())
                .noneMatch(invocation -> Set.of("update", "execute", "batchUpdate").contains(invocation.getMethod().getName()));
    }

    @Test
    void graphOverTaskLimitOpensOnlyTheAuthorizedSelectedDesign() throws Exception {
        Fixture fixture = new Fixture();
        for (int i = 0; i < 101; i++) fixture.task("t" + i, "root", "out" + i, "source", "target");
        clearInvocations(fixture.jdbc);
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.service.byTask("t0");
            assertThat(graph).containsEntry("readOnly", true);
            assertThat(items(graph)).extracting(item -> item.get("taskId")).containsExactly("t0");
            assertThat(graph.get("warnings").toString()).contains("100");
        }
        verify(fixture.jdbc, times(6)).query(any(PreparedStatementCreator.class), any(ColumnMapRowMapper.class));
        verifyNoInteractions(fixture.nifi);
    }

    @ParameterizedTest
    @ValueSource(strings = {"source", "target"})
    void everyRootAndTargetSourceRequiresCurrentRoleVisibility(String hidden) throws Exception {
        Fixture fixture = new Fixture(); fixture.task("t", "root", "ods", "source", "target");
        fixture.deny(hidden);
        try (var tenant = TenantContext.use("ga")) {
            assertThatThrownBy(() -> fixture.service.byTask("t")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"task", "source", "target", "pipeline"})
    void foreignTenantRecordsCannotBeReachedBySupplyingTheirIds(String foreign) throws Exception {
        Fixture fixture = new Fixture(); fixture.task("t", "root", "ods", "source", "target");
        switch (foreign) {
            case "task" -> fixture.jdbc.update("update data_access_agg_task_t set tenant_id='other'");
            case "source" -> fixture.jdbc.update("update db_table_t set tenant_id='other' where tid='root'");
            case "target" -> fixture.jdbc.update("update db_table_t set tenant_id='other' where tid='ods'");
            case "pipeline" -> fixture.jdbc.update("update nifi_pipeline_t set tenant_id='other'");
        }
        try (var tenant = TenantContext.use("ga")) {
            assertThatThrownBy(() -> fixture.service.byTask("t")).isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    void foreignOrHiddenSecondSinkInLegacyMultiTargetDslRejectsTheWholeGraph() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("t", "root", "ods", "source", "target");
        fixture.datasource("hidden"); fixture.table("secret", "hidden", "root"); fixture.deny("hidden");
        Pipeline.Dsl original = fixture.dsl("root", "ods", "source", "target");
        List<Pipeline.Node> nodes = new ArrayList<>(original.nodes());
        nodes.add(new Pipeline.Node("hidden-sink", "sink.jdbc", "隐藏目标", "sink", 700, 0,
                Map.of("registeredDatasourceId", "hidden", "targetTableId", "secret")));
        fixture.jdbc.update("update nifi_pipeline_t set dsl_json=?", fixture.json.writeValueAsString(new Pipeline.Dsl(1, nodes, original.edges())));
        try (var tenant = TenantContext.use("ga")) {
            assertThatThrownBy(() -> fixture.service.byTask("t")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        }
    }

    @Test
    void missingOrCyclicAncestryOpensTheSavedTaskWithoutInventingChainLinks() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("t", "root", "ods", "source", "target");
        fixture.jdbc.update("update db_table_t set source_table_id='missing' where tid='root'");
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> missing = fixture.service.byTask("t");
            assertThat(missing).containsEntry("readOnly", true).containsEntry("links", List.of());
            assertThat(missing.get("warnings").toString()).contains("关联不完整");
            fixture.jdbc.update("update db_table_t set source_table_id='ods' where tid='root'");
            Map<String, Object> cyclic = fixture.service.byTask("t");
            assertThat(cyclic).containsEntry("readOnly", true).containsEntry("links", List.of());
            assertThat(cyclic.get("warnings").toString()).contains("循环");
        }
    }

    @Test
    void legacySelfReferenceIsARootMarkerForDirectAndMultiHopTaskEntries() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("a", "root", "ods", "source", "target");
        fixture.task("b", "ods", "dst", "target", "target"); fixture.task("c", "dst", "final", "target", "target");
        fixture.jdbc.update("update db_table_t set source_table_id='root' where tid='root'");
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> direct = fixture.service.byTask("a");
            Map<String, Object> downstream = fixture.service.byTask("c");
            assertThat(direct).containsEntry("rootSourceTableId", "root");
            assertThat(downstream).containsEntry("rootSourceTableId", "root").containsEntry("selectedTaskId", "c");
            assertThat(items(direct)).hasSize(3);
            assertThat(items(downstream)).extracting(item -> item.get("taskId")).containsExactly("a", "b", "c");
            assertThat((List<?>) downstream.get("links")).hasSize(2);
        }
        verifyNoInteractions(fixture.nifi);
    }

    @Test
    void intermediateSourceMayStillBeUnregisteredForReadingAnExistingTask() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("a", "root", "ods", "source", "target"); fixture.task("b", "ods", "dst", "target", "target");
        fixture.jdbc.execute("alter table db_datasource_t add asset_status int default 0");
        fixture.jdbc.execute("alter table db_table_t add asset_status int default 0");
        try (var tenant = TenantContext.use("ga")) {
            assertThat(items(fixture.service.byTask("a"))).hasSize(2);
        }
    }

    @Test
    void authorizedButIncorrectDslSourceOpensReadOnlyWithoutCreatingAFalseLink() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("a", "root", "ods", "source", "target");
        fixture.table("another", "source", null);
        fixture.jdbc.update("update nifi_pipeline_t set dsl_json=?", fixture.json.writeValueAsString(fixture.dsl("another", "ods", "source", "target")));
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.service.byTask("a");
            assertThat(graph).containsEntry("readOnly", true).containsEntry("links", List.of());
            assertThat(graph.get("warnings").toString()).contains("不一致");
        }
    }

    @Test
    void tooDeepChainStopsTraversalAndOpensOnlyTheSavedSelectedTask() throws Exception {
        Fixture fixture = new Fixture(); String previous = "root";
        for (int i = 0; i < 18; i++) { fixture.table("level" + i, "source", previous); previous = "level" + i; }
        fixture.task("deep", previous, "out", "source", "target");
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.service.byTask("deep");
            assertThat(graph).containsEntry("readOnly", true);
            assertThat(items(graph)).hasSize(1);
            assertThat(graph.get("warnings").toString()).contains("16级");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void changingRegisteredSourceDatabaseDoesNotHideSavedCanvasOrGrowReadsWithTaskCount(int count) throws Exception {
        Fixture fixture = new Fixture();
        for (int i = 0; i < count; i++) fixture.task("t" + i, "root", "out" + i, "source", "target");
        fixture.datasource("replacement");
        fixture.jdbc.update("update db_table_t set datasource_id='replacement' where tid='root'");
        String original = fixture.jdbc.queryForObject("select dsl_json from nifi_pipeline_t where id='p-t0'", String.class);
        clearInvocations(fixture.jdbc);
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.service.byTask("t0");
            assertThat(graph).containsEntry("readOnly", true).containsEntry("selectedPipelineId", "p-t0");
            assertThat(items(graph)).hasSize(1);
            Pipeline saved = (Pipeline) items(graph).getFirst().get("pipeline");
            assertThat(fixture.json.writeValueAsString(saved.dsl())).isEqualTo(original);
            assertThat(saved.dsl().nodes().getFirst().config()).containsEntry("registeredDatasourceId", "source");
            assertThat(graph.get("warnings").toString()).contains("任务来源或目标关联不完整");
        }
        verify(fixture.jdbc, times(6)).query(any(PreparedStatementCreator.class), any(ColumnMapRowMapper.class));
        verify(fixture.scopes).visibleDataSources(eq("MENU:2081000000000000002"), any());
        assertThat(org.mockito.Mockito.mockingDetails(fixture.jdbc).getInvocations())
                .noneMatch(invocation -> Set.of("update", "execute", "batchUpdate").contains(invocation.getMethod().getName()));
        verifyNoInteractions(fixture.nifi);
    }

    @ParameterizedTest
    @ValueSource(strings = {"source", "target", "replacement"})
    void sourceDatabaseChangeStillRequiresEveryRecordedAndActualDatasourcePermission(String hidden) throws Exception {
        Fixture fixture = new Fixture(); fixture.task("t", "root", "ods", "source", "target");
        fixture.datasource("replacement");
        fixture.jdbc.update("update db_table_t set datasource_id='replacement' where tid='root'");
        fixture.deny(hidden);
        try (var tenant = TenantContext.use("ga")) {
            assertThatThrownBy(() -> fixture.service.byTask("t")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        }
    }

    @Test
    void softDeletedSameTenantRegistrationAllowsOnlyAuthorizedHistoricalInspection() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("t", "root", "ods", "source", "target");
        fixture.jdbc.update("update db_table_t set is_del=1 where tid='root'");
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.service.byTask("t");
            assertThat(graph).containsEntry("readOnly", true);
            assertThat(items(graph)).hasSize(1);
            assertThat(items(graph).getFirst()).doesNotContainKeys("nativeUrl", "processGroupId");
            fixture.deny("source");
            assertThatThrownBy(() -> fixture.service.byTask("t")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        }
    }

    @Test
    void unrelatedBrokenTaskDoesNotHideSelectedDesignAndMalformedSelectedDslStillFails() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("a", "root", "ods", "source", "target");
        fixture.task("b", "root", "other", "source", "target");
        fixture.jdbc.update("update data_access_agg_task_t set target_db_id='source' where tid='b'");
        try (var tenant = TenantContext.use("ga")) {
            assertThat(items(fixture.service.byTask("a"))).extracting(item -> item.get("taskId")).containsExactly("a");
            fixture.jdbc.update("update nifi_pipeline_t set dsl_json='broken' where id='p-a'");
            assertThatThrownBy(() -> fixture.service.byTask("a")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("无法读取");
        }
    }

    @Test
    void nativeParentProbeUsesOneRecursiveStatusReadAndReturnsOnlyGroupOverview() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("a", "root", "ods", "source", "target"); fixture.task("b", "ods", "dst", "target", "target");
        when(fixture.nifi.getRootProcessGroupId()).thenReturn("native-root");
        when(fixture.nifi.getProcessGroupStatus("native-root")).thenReturn(fixture.json.readTree("""
                {"processGroupStatus":{"aggregateSnapshot":{"id":"native-root","processGroupStatusSnapshots":[
                  {"processGroupStatusSnapshot":{"id":"parent","processGroupStatusSnapshots":[
                    {"processGroupStatusSnapshot":{"id":"pg-a"}},{"processGroupStatusSnapshot":{"id":"pg-b"}}
                  ]}}
                ]}}}
                """));
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.service.nativeByTask("a");
            assertThat(((Map<?, ?>) graph.get("nativeCanvas")).get("mode")).isEqualTo("COMMON_PARENT");
            assertThat(((Map<?, ?>) graph.get("nativeCanvas")).get("url")).isEqualTo("/nifi-ui/#/process-groups/parent");
            assertThat(items(graph)).allSatisfy(item -> assertThat(item).containsKey("nativeUrl"));
        }
        verify(fixture.nifi).getRootProcessGroupId(); verify(fixture.nifi).getProcessGroupStatus("native-root"); verifyNoMoreInteractions(fixture.nifi);
    }

    @Test
    void nifiUnavailableIsNativeSelectionAndDoesNotBreakSavedCanvas() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("a", "root", "ods", "source", "target"); fixture.task("b", "ods", "dst", "target", "target");
        when(fixture.nifi.getRootProcessGroupId()).thenThrow(new IllegalStateException("offline"));
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.service.nativeByTask("a");
            assertThat(items(graph)).hasSize(2);
            assertThat(((Map<?, ?>) graph.get("nativeCanvas")).get("mode")).isEqualTo("SELECT_TASK");
        }
    }

    @Test
    void sharedParentWithAnUnrelatedSiblingDoesNotOfferThatBroaderNativeView() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("a", "root", "ods", "source", "target"); fixture.task("b", "ods", "dst", "target", "target");
        when(fixture.nifi.getRootProcessGroupId()).thenReturn("root");
        when(fixture.nifi.getProcessGroupStatus("root")).thenReturn(fixture.json.readTree("""
                {"processGroupStatus":{"aggregateSnapshot":{"id":"root","processGroupStatusSnapshots":[
                  {"processGroupStatusSnapshot":{"id":"pg-a"}},{"processGroupStatusSnapshot":{"id":"pg-b"}},
                  {"processGroupStatusSnapshot":{"id":"unrelated"}}
                ]}}}
                """));
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.service.nativeByTask("a");
            assertThat(((Map<?, ?>) graph.get("nativeCanvas")).get("mode")).isEqualTo("SELECT_TASK");
            assertThat(items(graph)).noneMatch(item -> "unrelated".equals(item.get("processGroupId")));
        }
    }

    @Test
    void processGroupMissingFromCurrentNodeDoesNotGetAnIncorrectNativeLink() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("a", "root", "ods", "source", "target"); fixture.task("b", "ods", "dst", "target", "target");
        when(fixture.nifi.getRootProcessGroupId()).thenReturn("root");
        when(fixture.nifi.getProcessGroupStatus("root")).thenReturn(fixture.json.readTree("""
                {"processGroupStatus":{"aggregateSnapshot":{"id":"root","processGroupStatusSnapshots":[
                  {"processGroupStatusSnapshot":{"id":"pg-a"}}
                ]}}}
                """));
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.service.nativeByTask("a");
            assertThat(items(graph).get(1)).doesNotContainKeys("nativeUrl", "processGroupId").containsKey("unavailableReason");
        }
    }

    @Test
    void deepNativeHierarchyDegradesToTaskSelectionRatherThanTraversingWithoutLimit() throws Exception {
        Fixture fixture = new Fixture();
        fixture.task("a", "root", "ods", "source", "target"); fixture.task("b", "ods", "dst", "target", "target");
        var root = fixture.json.createObjectNode(); root.put("id", "root");
        var current = root;
        for (int depth = 0; depth < 34; depth++) {
            var nested = fixture.json.createObjectNode(); nested.put("id", "depth-" + depth);
            current.putArray("processGroupStatusSnapshots").addObject().set("processGroupStatusSnapshot", nested);
            current = nested;
        }
        var response = fixture.json.createObjectNode(); response.putObject("processGroupStatus").set("aggregateSnapshot", root);
        when(fixture.nifi.getRootProcessGroupId()).thenReturn("root"); when(fixture.nifi.getProcessGroupStatus("root")).thenReturn(response);
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.service.nativeByTask("a");
            assertThat(((Map<?, ?>) graph.get("nativeCanvas")).get("mode")).isEqualTo("SELECT_TASK");
            assertThat(items(graph)).hasSize(2);
        }
    }

    @Test
    void contextMissingOrControlDatabaseCannotReadTenantPipelines() {
        Fixture fixture = new Fixture();
        assertThatThrownBy(() -> fixture.service.byTask("a")).hasMessageContaining("未绑定租户");
        try (var tenant = TenantContext.use("ga"); var control = TenantContext.control()) {
            assertThatThrownBy(() -> fixture.service.byTask("a")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        }
    }

    @SuppressWarnings("unchecked") private static List<Map<String, Object>> items(Map<String, Object> graph) { return (List<Map<String, Object>>) graph.get("items"); }

    private static final class Fixture {
        final ObjectMapper json = new ObjectMapper();
        final JdbcTemplate jdbc = spy(new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:task-chain-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "")));
        final DataScopeAuthorizationService scopes = mock(DataScopeAuthorizationService.class);
        final NifiClient nifi = mock(NifiClient.class);
        final AccessTaskChainService service = new AccessTaskChainService(jdbc, scopes, json, nifi);
        Fixture() {
            jdbc.execute("create table db_datasource_t(tid varchar(64) primary key, tenant_id varchar(64), db_name varchar(128), created_by varchar(64), org_id varchar(64), is_del int)");
            jdbc.execute("create table db_table_t(tid varchar(64) primary key, tenant_id varchar(64), datasource_id varchar(64), source_table_id varchar(64), table_name varchar(128), table_name_cn varchar(128), is_del int)");
            jdbc.execute("create table data_access_agg_task_t(tid varchar(64) primary key, tenant_id varchar(64), task_name varchar(128), pipeline_id varchar(64), source_db_id varchar(64), target_db_id varchar(64), source_table_id varchar(64), target_table_id varchar(64), task_status int, created_time bigint, is_del int)");
            jdbc.execute("create table nifi_pipeline_t(id varchar(64) primary key, tenant_id varchar(64), name varchar(128), description varchar(128), status varchar(32), dsl_json clob, nifi_process_group_id varchar(64), last_deployed_hash varchar(64), node_mapping_json clob, created_at bigint, updated_at bigint, last_deployed_at bigint, last_stopped_at bigint, last_bulletin_id bigint, is_del int)");
            when(scopes.visibleDataSources(eq("MENU:2081000000000000002"), any())).thenAnswer(invocation -> invocation.getArgument(1));
            datasource("source"); datasource("target"); table("root", "source", null);
        }
        void deny(String hidden) {
            when(scopes.visibleDataSources(eq("MENU:2081000000000000002"), any())).thenAnswer(invocation -> {
                List<Map<String, Object>> rows = invocation.getArgument(1);
                return rows.stream().filter(row -> !hidden.equals(row.get("tid"))).toList();
            });
        }
        void datasource(String id) { jdbc.update("insert into db_datasource_t values(?, 'ga', ?, 'user', 'org', 0)", id, id); }
        void table(String id, String ds, String parent) { jdbc.update("insert into db_table_t values(?, 'ga', ?, ?, ?, ?, 0)", id, ds, parent, id, id); }
        Pipeline.Dsl dsl(String source, String target, String sourceDb, String targetDb) {
            return new Pipeline.Dsl(1, List.of(
                    new Pipeline.Node("source", "source.jdbc", "来源", "source", 0, 0, Map.of("registeredDatasourceId", sourceDb, "sourceTableId", source)),
                    new Pipeline.Node("sink", "sink.jdbc", "目标", "sink", 400, 0, Map.of("registeredDatasourceId", targetDb, "targetTableId", target))),
                    List.of(new Pipeline.Edge("edge", "source", "sink", "success")));
        }
        void task(String task, String source, String target, String sourceDb, String targetDb) throws Exception {
            table(target, targetDb, source);
            jdbc.update("insert into data_access_agg_task_t values(?, 'ga', ?, ?, ?, ?, ?, ?, 1, ?, 0)", task, task, "p-" + task, sourceDb, targetDb, source, target, System.nanoTime());
            Pipeline.Dsl dsl = dsl(source, target, sourceDb, targetDb);
            jdbc.update("insert into nifi_pipeline_t(id,tenant_id,name,status,dsl_json,nifi_process_group_id,last_deployed_hash,is_del) values(?, 'ga', ?, 'STOPPED', ?, ?, ?, 0)",
                    "p-" + task, task, json.writeValueAsString(dsl), "pg-" + task, DslHasher.deploymentHash(dsl, DslHasher.CURRENT_COMPILER_REVISION));
        }
    }
}
