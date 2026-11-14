package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.DslHasher;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.magic.StructuredTableReadMagicModule;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.modules.servlet.RequestModule;
import org.ssssssss.script.MagicResourceLoader;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class AccessTaskChainMagicTest {
    @Test
    void verifiedTaskLinkRepairsHiveDisplayEvenWhenAllOldChainMetadataWasLostWithoutWriting() throws Exception {
        Fixture fixture=new Fixture();fixture.task("first","root","ods","source","target");fixture.task("second","ods","raw","target","target");
        var old=new Pipeline.Dsl(1,List.of(
                new Pipeline.Node("s","source.mysql","s","source",0,0,Map.of("sourceTableId","ods","sourceDbId","target")),
                new Pipeline.Node("m","transform.field-enrichment","m","transform",400,0,Map.of()),
                new Pipeline.Node("h","sink.hive","h","sink",800,0,Map.of("targetTableId","raw","targetDbId","target"))),
                List.of(new Pipeline.Edge("sm","s","m","default"),new Pipeline.Edge("mh","m","h","default")));
        String saved=fixture.json.writeValueAsString(old);
        fixture.jdbc.update("update nifi_pipeline_t set dsl_json=? where id='p-second'",saved);fixture.resetSqlCounts();
        try(var tenant=TenantContext.use("ga")) {
            var graph=fixture.byTask("first");assertThat(items(graph)).hasSize(2);
            var downstream=(Pipeline)items(graph).get(1).get("pipeline");
            assertThat(downstream.dsl().nodes()).extracting(Pipeline.Node::id).containsExactly("s","h");
            assertThat(downstream.dsl().edges()).singleElement().satisfies(edge->{assertThat(edge.source()).isEqualTo("s");assertThat(edge.target()).isEqualTo("h");});
        }
        assertThat(fixture.reads.get()).isEqualTo(5);
        assertThat(fixture.jdbc.queryForObject("select dsl_json from nifi_pipeline_t where id='p-second'",String.class)).isEqualTo(saved);
        verifyNoInteractions(fixture.nifi);
    }
    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void pushStorageBridgeKeepsEveryDownstreamHiveTaskWithFiveBatchedReads(int count) throws Exception {
        for (String taskSource : List.of("physical", "source")) {
            Fixture fixture = new Fixture(); fixture.datasource("physical"); fixture.datasource("hive");
            fixture.task("first", "root", "ods", taskSource, "target");
            Pipeline.Dsl first = pushStorageDsl("source.sftp", "source", "physical", "fjghcc", "source");
            fixture.jdbc.update("update nifi_pipeline_t set dsl_json=? where id='p-first'", fixture.json.writeValueAsString(first));
            for (int i = 1; i < count; i++) {
                fixture.task("hive" + i, "ods", "raw" + i, "target", "hive");
                var hive = new Pipeline.Dsl(1, List.of(
                        new Pipeline.Node("read", "source.jdbc", "汇聚库", "source", 0, 0,
                                Map.of("registeredDatasourceId", "target", "sourceTableId", "ods")),
                        new Pipeline.Node("write", "sink.hive", "Hive", "sink", 400, 0,
                                Map.of("targetDbId", "hive", "targetTableId", "raw" + i))),
                        List.of(new Pipeline.Edge("edge", "read", "write", "success")));
                fixture.jdbc.update("update nifi_pipeline_t set dsl_json=? where id=?", fixture.json.writeValueAsString(hive), "p-hive" + i);
            }
            fixture.resetSqlCounts(); clearInvocations(fixture.scopes);
            try (var tenant = TenantContext.use("ga")) {
                var graph = fixture.byTask("first");
                assertThat(items(graph)).hasSize(count);
                assertThat(graph).doesNotContainKey("relationshipUnavailable");
                assertThat((List<?>) graph.get("links")).hasSize(count - 1);
                assertThat(((Pipeline) items(graph).getFirst().get("pipeline")).dsl()).isEqualTo(first);
                assertThat(items(graph).subList(1, count)).allSatisfy(item ->
                        assertThat(((Pipeline) item.get("pipeline")).dsl().nodes()).anyMatch(node -> node.manifestKey().equals("sink.hive")));
            }
            assertThat(fixture.reads.get()).isEqualTo(5);
            assertThat(fixture.writes.get()).isZero();
            verifyNoInteractions(fixture.nifi);
        }
    }

    @Test
    void pushBridgeCannotAuthorizeUnknownOrForeignStorageAndLogicalIds() throws Exception {
        for (boolean foreignLogical : List.of(false, true)) {
            Fixture fixture = new Fixture(); fixture.datasource("physical");
            fixture.task("first", "root", "ods", "physical", "target");
            fixture.task("second", "ods", "raw", "target", "target");
            var dsl = pushStorageDsl("source.ftp", foreignLogical ? "foreign" : "source",
                    foreignLogical ? "physical" : "foreign", "fjghcc", "source");
            fixture.jdbc.update("update nifi_pipeline_t set dsl_json=? where id='p-first'", fixture.json.writeValueAsString(dsl));
            fixture.jdbc.update("insert into db_datasource_t values('foreign','other','foreign','user','org',0)");
            try (var tenant = TenantContext.use("ga")) {
                assertThat(fixture.taskResponse("first").getStatusCode().value()).isEqualTo(403);
            }
        }
    }

    @Test
    void arbitraryJdbcOrIncompleteStorageMarkersStillCannotJoinTheChain() throws Exception {
        for (var invalid : List.of(
                pushStorageDsl("source.jdbc", "source", "physical", "fjghcc", "source"),
                pushStorageDsl("source.ftp", "source", "physical", "other-domain", "source"),
                pushStorageDsl("source.sftp", "source", "physical", "fjghcc", "target"))) {
            Fixture fixture = new Fixture(); fixture.datasource("physical");
            fixture.task("first", "root", "ods", "physical", "target");
            fixture.task("second", "ods", "raw", "target", "target");
            fixture.jdbc.update("update nifi_pipeline_t set dsl_json=? where id='p-first'", fixture.json.writeValueAsString(invalid));
            try (var tenant = TenantContext.use("ga")) {
                var graph = fixture.byTask("first");
                assertThat(graph).containsEntry("relationshipUnavailable", true);
                assertThat(items(graph)).hasSize(1);
                assertThat((List<?>) graph.get("warnings")).isNotEmpty();
            }
        }
    }

    private static Pipeline.Dsl pushStorageDsl(String manifest, String logical, String physical, String domain, String sourceDb) {
        return new Pipeline.Dsl(1, List.of(
                new Pipeline.Node("source", manifest, "推送落地文件", "source", 0, 0,
                        Map.of("sourceTableId", "root", "sourceDbId", sourceDb, "registeredDatasourceId", physical,
                                "logicalSourceDatasourceId", logical, "pushStorageDomain", domain)),
                new Pipeline.Node("governance", "transform.field-enrichment", "字段治理", "transform", 400, 0, Map.of()),
                new Pipeline.Node("sink", "sink.jdbc", "汇聚库", "sink", 800, 0,
                        Map.of("targetDbId", "target", "targetTableId", "ods"))),
                List.of(new Pipeline.Edge("first", "source", "governance", "success"),
                        new Pipeline.Edge("second", "governance", "sink", "success")));
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void standaloneTasksOpenEditableSavedCanvasWithoutRegistrationWarningsWithThreeReads(int count) throws Exception {
        Fixture fixture = new Fixture();
        for (int i = 0; i < count; i++) fixture.nonTableTask("kafka" + i);
        fixture.scope(DataScopeAuthorizationService.Scope.ALL);
        String original = fixture.jdbc.queryForObject("select dsl_json from nifi_pipeline_t where id='p-kafka0'", String.class);
        fixture.resetSqlCounts(); clearInvocations(fixture.scopes);
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.byTask("kafka0");
            assertThat(graph).containsEntry("readOnly", false).containsEntry("selectedPipelineId", "p-kafka0")
                    .containsEntry("standalone", true).containsEntry("relationshipUnavailable", false).containsEntry("warnings", List.of());
            assertThat(items(graph)).hasSize(1);
            Pipeline pipeline = (Pipeline) items(graph).getFirst().get("pipeline");
            assertThat(fixture.json.writeValueAsString(pipeline.dsl())).isEqualTo(original);
            assertThat(graph.get("links")).isEqualTo(List.of());
        }
        assertThat(fixture.reads.get()).isEqualTo(3);
        verifyNoInteractions(fixture.scopes);
        assertThat(fixture.writes.get()).isZero();
        verifyNoInteractions(fixture.nifi);
    }

    @Test
    void chainPreservesStoredDesignVersionIncludingSpacesInDescription() throws Exception {
        Fixture fixture = new Fixture(); fixture.nonTableTask("manual");
        String dsl = fixture.jdbc.queryForObject("select dsl_json from nifi_pipeline_t where id='p-manual'", String.class);
        for (String description : java.util.Arrays.asList(null, "  保存说明  ")) {
            String name = "  已保存名称  ";
            fixture.jdbc.update("update nifi_pipeline_t set name=?,description=? where id='p-manual'", name, description);
            var stored = new Pipeline("p-manual", name, description, null, null,
                    fixture.json.readValue(dsl, Pipeline.Dsl.class), null, PipelineStatus.SAVED, null, null, null, null, null);
            try (var tenant = TenantContext.use("ga")) {
                Pipeline opened = (Pipeline) items(fixture.byPipeline("p-manual")).getFirst().get("pipeline");
                assertThat(opened.designHash()).isEqualTo(stored.designHash());
                assertThat(opened.description()).isEqualTo(description);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "ORG"})
    void standaloneTasksUseTheirTenantCanvasWithoutRequiringAllDatasourceScope(String scope) throws Exception {
        Fixture fixture = new Fixture(); fixture.nonTableTask("kafka");
        fixture.scope(DataScopeAuthorizationService.Scope.valueOf(scope));
        try (var tenant = TenantContext.use("ga")) {
            assertThat(fixture.byTask("kafka")).containsEntry("standalone", true)
                    .containsEntry("readOnly", false).containsEntry("relationshipUnavailable", false);
            verifyNoInteractions(fixture.scopes);
        }
    }

    @Test
    void manuallyConfiguredDatabaseFlowOpensWithoutMistakingLegacyUrlAndTableForRegisteredIds() throws Exception {
        Fixture fixture = new Fixture(); fixture.nonTableTask("manual");
        var dsl = new Pipeline.Dsl(1, List.of(
                new Pipeline.Node("source", "source.oracle", "manual", "source", 0, 0,
                        Map.of("host", "fixture.invalid", "table", "ORDERS", "incrementalColumn", "UPDATED_AT")),
                new Pipeline.Node("sink", "sink.jdbc", "manual", "sink", 500, 0,
                        Map.of("jdbcUrl", "jdbc:mysql://fixture.invalid/test", "table", "orders_copy"))), List.of());
        fixture.jdbc.update("update nifi_pipeline_t set dsl_json=? where id='p-manual'", fixture.json.writeValueAsString(dsl));
        fixture.jdbc.update("update data_access_agg_task_t set target_db_id='jdbc:mysql://fixture.invalid/test',target_table_id='orders_copy'");
        try (var tenant = TenantContext.use("ga")) {
            assertThat(fixture.byTask("manual")).containsEntry("standalone", true).containsEntry("readOnly", false)
                    .containsEntry("relationshipUnavailable", false).containsEntry("warnings", List.of());
            fixture.jdbc.update("update data_access_agg_task_t set target_db_id='unknown-resource'");
            assertThat(fixture.taskResponse("manual").getStatusCode().value()).isEqualTo(403);
        }
        verifyNoInteractions(fixture.nifi);
    }

    @Test
    void allScopeCannotReadForeignPipelineOrTreatUnknownTableAsNonTableTask() throws Exception {
        Fixture fixture = new Fixture(); fixture.nonTableTask("kafka");
        fixture.scope(DataScopeAuthorizationService.Scope.ALL);
        try (var tenant = TenantContext.use("ga")) {
            assertThat(fixture.byPipeline("p-kafka")).containsEntry("readOnly", false);
            fixture.jdbc.update("update data_access_agg_task_t set source_table_id='unknown'");
            assertThat(fixture.taskResponse("kafka").getStatusCode().value()).isEqualTo(403);
            fixture.jdbc.update("update data_access_agg_task_t set source_table_id=null");
            fixture.jdbc.update("update nifi_pipeline_t set tenant_id='other'");
            assertThat(fixture.taskResponse("kafka").getStatusCode().value()).isEqualTo(403);
            fixture.jdbc.update("update nifi_pipeline_t set tenant_id='ga'");
            fixture.jdbc.update("update data_access_agg_task_t set tenant_id='other'");
            assertThat(fixture.taskResponse("kafka").getStatusCode().value()).isEqualTo(404);
        }
        verifyNoInteractions(fixture.nifi);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void twentyOrHundredTasksUseFiveReadsAndOneScopeDecisionWithoutNifi(int count) throws Exception {
        Fixture fixture = new Fixture();
        for (int i = 0; i < count; i++) fixture.task("t" + i, "root", "out" + i, "source", "target");
        fixture.resetSqlCounts();
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.byTask("t0");
            assertThat(items(graph)).hasSize(count);
            assertThat(graph).containsEntry("rootSourceTableId", "root").containsEntry("selectedPipelineId", "p-t0");
        }
        assertThat(fixture.reads.get()).isEqualTo(5);
        verifyNoInteractions(fixture.scopes);
        verifyNoMoreInteractions(fixture.scopes);
        verifyNoInteractions(fixture.nifi);
    }

    @Test
    void twoRealHopTasksReturnOneChainAndPreserveIndependentDslIds() throws Exception {
        Fixture fixture = new Fixture();
        fixture.task("first", "root", "ods", "source", "target");
        fixture.task("second", "ods", "dst", "target", "target");
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.byPipeline("p-second");
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
        fixture.resetSqlCounts();
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.byTask("first");
            assertThat(items(graph).getFirst()).doesNotContainKeys("nativeUrl", "processGroupId").containsKey("unavailableReason");
            assertThat(((Map<?, ?>) graph.get("nativeCanvas")).get("mode")).isEqualTo("NONE");
        }
        assertThat(fixture.reads.get()).isEqualTo(5);
        assertThat(fixture.writes.get()).isZero();
    }

    @Test
    void graphOverTaskLimitOpensOnlyTheAuthorizedSelectedDesign() throws Exception {
        Fixture fixture = new Fixture();
        for (int i = 0; i < 101; i++) fixture.task("t" + i, "root", "out" + i, "source", "target");
        fixture.resetSqlCounts();
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.byTask("t0");
            assertThat(graph).containsEntry("readOnly", false).containsEntry("relationshipUnavailable", true);
            assertThat(items(graph)).extracting(item -> item.get("taskId")).containsExactly("t0");
            assertThat(graph.get("warnings").toString()).contains("100");
        }
        assertThat(fixture.reads.get()).isEqualTo(6);
        verifyNoInteractions(fixture.nifi);
    }

    @ParameterizedTest
    @ValueSource(strings = {"source", "target"})
    void everyRootAndTargetSourceIgnoresMetadataMenuRole(String hidden) throws Exception {
        Fixture fixture = new Fixture(); fixture.task("t", "root", "ods", "source", "target");
        fixture.deny(hidden);
        try (var tenant = TenantContext.use("ga")) {
            assertThat(fixture.byTask("t")).isNotEmpty();
            verifyNoInteractions(fixture.scopes);
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
            assertThat(fixture.taskResponse("t").getStatusCode().is4xxClientError()).isTrue();
        }
    }

    @Test
    void foreignOrHiddenSecondSinkInLegacyMultiTargetDslRejectsTheWholeGraph() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("t", "root", "ods", "source", "target");
        fixture.datasource("hidden"); fixture.table("secret", "hidden", "root"); fixture.jdbc.update("update db_datasource_t set tenant_id='other' where tid='hidden'");
        Pipeline.Dsl original = fixture.dsl("root", "ods", "source", "target");
        List<Pipeline.Node> nodes = new ArrayList<>(original.nodes());
        nodes.add(new Pipeline.Node("hidden-sink", "sink.jdbc", "隐藏目标", "sink", 700, 0,
                Map.of("registeredDatasourceId", "hidden", "targetTableId", "secret")));
        fixture.jdbc.update("update nifi_pipeline_t set dsl_json=?", fixture.json.writeValueAsString(new Pipeline.Dsl(1, nodes, original.edges())));
        try (var tenant = TenantContext.use("ga")) {
            assertThat(fixture.taskResponse("t").getStatusCode().value()).isEqualTo(403);
        }
    }

    @Test
    void missingOrCyclicAncestryOpensTheSavedTaskWithoutInventingChainLinks() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("t", "root", "ods", "source", "target");
        fixture.jdbc.update("update db_table_t set source_table_id='missing' where tid='root'");
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> missing = fixture.byTask("t");
            assertThat(missing).containsEntry("readOnly", false).containsEntry("relationshipUnavailable", true).containsEntry("links", List.of());
            assertThat(missing.get("warnings").toString()).contains("关联不完整");
            fixture.jdbc.update("update db_table_t set source_table_id='ods' where tid='root'");
            Map<String, Object> cyclic = fixture.byTask("t");
            assertThat(cyclic).containsEntry("readOnly", false).containsEntry("relationshipUnavailable", true).containsEntry("links", List.of());
            assertThat(cyclic.get("warnings").toString()).contains("循环");
        }
    }

    @Test
    void legacySelfReferenceIsARootMarkerForDirectAndMultiHopTaskEntries() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("a", "root", "ods", "source", "target");
        fixture.task("b", "ods", "dst", "target", "target"); fixture.task("c", "dst", "final", "target", "target");
        fixture.jdbc.update("update db_table_t set source_table_id='root' where tid='root'");
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> direct = fixture.byTask("a");
            Map<String, Object> downstream = fixture.byTask("c");
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
            assertThat(items(fixture.byTask("a"))).hasSize(2);
        }
    }

    @Test
    void authorizedButIncorrectDslSourceOpensReadOnlyWithoutCreatingAFalseLink() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("a", "root", "ods", "source", "target");
        fixture.table("another", "source", null);
        fixture.jdbc.update("update nifi_pipeline_t set dsl_json=?", fixture.json.writeValueAsString(fixture.dsl("another", "ods", "source", "target")));
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.byTask("a");
            assertThat(graph).containsEntry("readOnly", false).containsEntry("relationshipUnavailable", true).containsEntry("links", List.of());
            assertThat(graph.get("warnings").toString()).contains("不一致");
        }
    }

    @Test
    void tooDeepChainStopsTraversalAndOpensOnlyTheSavedSelectedTask() throws Exception {
        Fixture fixture = new Fixture(); String previous = "root";
        for (int i = 0; i < 18; i++) { fixture.table("level" + i, "source", previous); previous = "level" + i; }
        fixture.task("deep", previous, "out", "source", "target");
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.byTask("deep");
            assertThat(graph).containsEntry("readOnly", false).containsEntry("relationshipUnavailable", true);
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
        fixture.resetSqlCounts();
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.byTask("t0");
            assertThat(graph).containsEntry("readOnly", false).containsEntry("relationshipUnavailable", true).containsEntry("selectedPipelineId", "p-t0");
            assertThat(items(graph)).hasSize(1);
            Pipeline saved = (Pipeline) items(graph).getFirst().get("pipeline");
            assertThat(fixture.json.writeValueAsString(saved.dsl())).isEqualTo(original);
            assertThat(saved.dsl().nodes().getFirst().config()).containsEntry("registeredDatasourceId", "source");
            assertThat(graph.get("warnings").toString()).contains("任务来源库与登记表不一致");
        }
        // One additional batched pipeline read distinguishes a valid push storage bridge
        // from a changed registration before falling back to the selected saved design.
        assertThat(fixture.reads.get()).isEqualTo(7);
        verifyNoInteractions(fixture.scopes);
        assertThat(fixture.writes.get()).isZero();
        verifyNoInteractions(fixture.nifi);
    }

    @ParameterizedTest
    @ValueSource(strings = {"source", "target", "replacement"})
    void sourceDatabaseChangeKeepsTenantRecordChecksWithoutMenuRole(String hidden) throws Exception {
        Fixture fixture = new Fixture(); fixture.task("t", "root", "ods", "source", "target");
        fixture.datasource("replacement");
        fixture.jdbc.update("update db_table_t set datasource_id='replacement' where tid='root'");
        fixture.deny(hidden);
        try (var tenant = TenantContext.use("ga")) {
            assertThat(fixture.byTask("t")).isNotEmpty();
            verifyNoInteractions(fixture.scopes);
        }
    }

    @Test
    void softDeletedSameTenantRegistrationAllowsOnlyAuthorizedHistoricalInspection() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("t", "root", "ods", "source", "target");
        fixture.jdbc.update("update db_table_t set is_del=1 where tid='root'");
        try (var tenant = TenantContext.use("ga")) {
            Map<String, Object> graph = fixture.byTask("t");
            assertThat(graph).containsEntry("readOnly", false).containsEntry("relationshipUnavailable", true);
            assertThat(items(graph)).hasSize(1);
            assertThat(items(graph).getFirst()).doesNotContainKeys("nativeUrl", "processGroupId");
            fixture.deny("source");
            assertThat(fixture.byTask("t")).containsEntry("relationshipUnavailable", true);
            verifyNoInteractions(fixture.scopes);
        }
    }

    @Test
    void unrelatedBrokenTaskDoesNotHideSelectedDesignAndMalformedSelectedDslStillFails() throws Exception {
        Fixture fixture = new Fixture(); fixture.task("a", "root", "ods", "source", "target");
        fixture.task("b", "root", "other", "source", "target");
        fixture.jdbc.update("update data_access_agg_task_t set target_db_id='source' where tid='b'");
        try (var tenant = TenantContext.use("ga")) {
            assertThat(items(fixture.byTask("a"))).extracting(item -> item.get("taskId")).containsExactly("a");
            fixture.jdbc.update("update nifi_pipeline_t set dsl_json='broken' where id='p-a'");
            assertThat(fixture.taskResponse("a").getBody().toString()).contains("无法读取");
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
            Map<String, Object> graph = fixture.nativeByTask("a");
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
            Map<String, Object> graph = fixture.nativeByTask("a");
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
            Map<String, Object> graph = fixture.nativeByTask("a");
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
            Map<String, Object> graph = fixture.nativeByTask("a");
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
            Map<String, Object> graph = fixture.nativeByTask("a");
            assertThat(((Map<?, ?>) graph.get("nativeCanvas")).get("mode")).isEqualTo("SELECT_TASK");
            assertThat(items(graph)).hasSize(2);
        }
    }

    @Test
    void contextMissingOrControlDatabaseCannotReadTenantPipelines() {
        Fixture fixture = new Fixture();
        var missing = fixture.taskResponse("a");
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(missing.getBody()).isInstanceOfSatisfying(CommonResponse.class, body -> {
            assertThat(body.getCode()).isEqualTo(403);
            assertThat(body.getMsg()).contains("未绑定租户");
        });
        try (var tenant = TenantContext.use("ga"); var control = TenantContext.control()) {
            assertThat(fixture.taskResponse("a").getStatusCode().value()).isEqualTo(403);
        }
        try (var tenant = TenantContext.use("ga"); var ignored = TenantContext.ignore()) {
            assertThat(fixture.taskResponse("a").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void taskRoutesKeepMissingBlankAndRepeatedQueryParametersDistinct() {
        Fixture fixture = new Fixture();
        try (var tenant = TenantContext.use("ga")) {
            for (var route : List.of(Fixture.TASK, Fixture.NATIVE)) {
                var missing = fixture.route(route, null, null);
                assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(missing.getBody()).isInstanceOfSatisfying(CommonResponse.class, body -> {
                    assertThat(body.getCode()).isEqualTo(20001);
                    assertThat(body.getMsg()).isEqualTo("缺少必要参数: accessTaskId");
                });
                var blank = fixture.route(route, null, List.of("  "));
                assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                var rejected = fixture.route(route, null, List.of("task", "other"));
                assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                var payload = fixture.json.valueToTree(rejected.getBody());
                assertThat(payload.size()).isEqualTo(4);
                assertThat(payload.path("code").asInt()).isEqualTo(404);
                assertThat(payload.path("success").asBoolean()).isFalse();
                assertThat(payload.path("error").asText()).isEqualTo(payload.path("msg").asText()).isNotBlank();
            }
        }
    }

    @SuppressWarnings("unchecked") private static List<Map<String, Object>> items(Map<String, Object> graph) { return (List<Map<String, Object>>) graph.get("items"); }

    private static final List<String> QUERY_ARGUMENTS = List.of("kind", "identifier", "byPipeline", "ids");

    private static MagicScript script(String relativePath) {
        try {
            String raw = Files.readString(Path.of("../magic", relativePath));
            return MagicScript.create(raw.split("================================", 2)[1], null);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static final class Fixture {
        private static final String COMMON = "function/01.通用函数/";
        private static final String API = "api/14.NiFi流程接口/01.接口/01.流程管理/";
        private static final MagicScript QUERY = script(COMMON + "38.NiFi接入任务链路读取.ms");
        private static final MagicScript CHAIN = script(COMMON + "39.NiFi接入任务链路.ms");
        private static final MagicScript HTTP = script(COMMON + "42.NiFi任务链路接口响应.ms");
        private static final MagicScript TASK = script(API + "01.按任务查询接入链路.ms");
        private static final MagicScript PIPELINE = script(API + "02.按流程查询接入链路.ms");
        private static final MagicScript NATIVE = script(API + "03.查询任务链路原生入口.ms");
        private static final List<String> CHAIN_ARGUMENTS = List.of("identifier", "byPipeline", "nativeView");

        static {
            MagicResourceLoader.addFunctionLoader((context, name) -> switch (name) {
                case "/common/nifiTaskChainRows" -> function(QUERY, QUERY_ARGUMENTS);
                case "/common/nifiAccessTaskChain" -> function(CHAIN, CHAIN_ARGUMENTS);
                case "/common/nifiAccessTaskChainHttp" -> function(HTTP, CHAIN_ARGUMENTS);
                default -> null;
            });
        }

        final ObjectMapper json = new ObjectMapper();
        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger writes = new AtomicInteger();
        final JdbcTemplate jdbc = new JdbcTemplate(new FixtureDataSource(reads, writes));
        final DataScopeAuthorizationService scopes = mock(DataScopeAuthorizationService.class);
        final NifiClient nifi = mock(NifiClient.class);
        final RequestModule request = mock(RequestModule.class);

        Fixture() {
            MagicResourceLoader.addModule("tenantRuntime", new TenantMagicModule(null));
            MagicResourceLoader.addModule("tableRead", new StructuredTableReadMagicModule(jdbc));
            MagicResourceLoader.addModule("nifiTaskChainRuntime", new NifiTaskChainRuntime(json, nifi));
            MagicResourceLoader.addModule("request", request);
            jdbc.execute("create table db_datasource_t(tid varchar(64) primary key, tenant_id varchar(64), db_name varchar(128), created_by varchar(64), org_id varchar(64), is_del int)");
            jdbc.execute("create table db_table_t(tid varchar(64) primary key, tenant_id varchar(64), datasource_id varchar(64), source_table_id varchar(64), table_name varchar(128), table_name_cn varchar(128), is_del int)");
            jdbc.execute("create table data_access_agg_task_t(tid varchar(64) primary key, tenant_id varchar(64), task_name varchar(128), pipeline_id varchar(64), source_db_id varchar(64), target_db_id varchar(64), source_table_id varchar(64), target_table_id varchar(64), task_status int, created_time bigint, is_del int)");
            jdbc.execute("create table nifi_pipeline_t(id varchar(64) primary key, tenant_id varchar(64), name varchar(128), description varchar(128), status varchar(32), dsl_json clob, nifi_process_group_id varchar(64), last_deployed_hash varchar(64), node_mapping_json clob, created_at bigint, updated_at bigint, last_deployed_at bigint, last_stopped_at bigint, last_bulletin_id bigint, is_del int)");
            when(scopes.visibleDataSources(eq("MENU:2081000000000000002"), any())).thenAnswer(invocation -> invocation.getArgument(1));
            datasource("source"); datasource("target"); table("root", "source", null);
        }
        private static Function<Object[], Object> function(MagicScript script, List<String> arguments) {
            return args -> {
                var values = new LinkedHashMap<String, Object>();
                for (int index = 0; index < args.length; index++) {
                    values.put(arguments.get(index), args[index]);
                }
                return script.execute(new MagicScriptContext(values));
            };
        }

        ResponseEntity<?> route(MagicScript script, String id, List<String> parameters) {
            when(request.getValues("accessTaskId")).thenReturn(parameters);
            return (ResponseEntity<?>) script.execute(new MagicScriptContext(
                    id == null ? Map.of() : Map.of("path", Map.of("id", id))));
        }

        ResponseEntity<?> taskResponse(String id) {
            return route(TASK, null, List.of(id));
        }

        Map<String, Object> byTask(String id) {
            return rawBody(taskResponse(id));
        }

        Map<String, Object> byPipeline(String id) {
            return rawBody(route(PIPELINE, id, null));
        }

        Map<String, Object> nativeByTask(String id) {
            return rawBody(route(NATIVE, null, List.of(id)));
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> rawBody(ResponseEntity<?> response) {
            assertThat(response.getStatusCode()).as("HTTP body: %s", response.getBody()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isInstanceOf(Map.class);
            return (Map<String, Object>) response.getBody();
        }

        void resetSqlCounts() {
            reads.set(0);
            writes.set(0);
        }

        void deny(String hidden) {
            when(scopes.visibleDataSources(eq("MENU:2081000000000000002"), any())).thenAnswer(invocation -> {
                List<Map<String, Object>> rows = invocation.getArgument(1);
                return rows.stream().filter(row -> !hidden.equals(row.get("tid"))).toList();
            });
        }
        void datasource(String id) { jdbc.update("insert into db_datasource_t values(?, 'ga', ?, 'user', 'org', 0)", id, id); }
        void scope(DataScopeAuthorizationService.Scope scope) {
            when(scopes.currentScope(DataScopeAuthorizationService.DATA_SOURCE_RESOURCE)).thenReturn(
                    new DataScopeAuthorizationService.ScopeDecision("DATA_SOURCE", scope, "user", List.of("org"), List.of("role"), true));
        }
        void nonTableTask(String id) throws Exception {
            jdbc.update("insert into data_access_agg_task_t(tid,tenant_id,task_name,pipeline_id,created_time,is_del) values(?, 'ga', ?, ?, ?, 0)", id, id, "p-" + id, System.nanoTime());
            var dsl = new Pipeline.Dsl(1, List.of(new Pipeline.Node("source", "source.kafka", "Kafka 来源", "source", 0, 0, Map.of("topic", "example"))), List.of());
            jdbc.update("insert into nifi_pipeline_t(id,tenant_id,name,status,dsl_json,is_del) values(?, 'ga', ?, 'SAVED', ?, 0)", "p-" + id, id, json.writeValueAsString(dsl));
        }
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

    private static final class FixtureDataSource extends DriverManagerDataSource {
        private final AtomicInteger reads;
        private final AtomicInteger writes;

        FixtureDataSource(AtomicInteger reads, AtomicInteger writes) {
            super("jdbc:h2:mem:task-chain-" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            this.reads = reads;
            this.writes = writes;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getMetaData")) {
                            var metadata = connection.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(),
                                    new Class<?>[]{DatabaseMetaData.class}, (ignored, operation, parameters) -> {
                                        if (operation.getName().equals("getURL")) {
                                            return "jdbc:mysql://localhost/validation_task_chain";
                                        }
                                        return invoke(metadata, operation, parameters);
                                    });
                        }
                        if (method.getName().equals("prepareStatement") && args[0] instanceof String sql) {
                            if (sql.stripLeading().regionMatches(true, 0, "SELECT", 0, 6)) {
                                reads.incrementAndGet();
                            } else {
                                writes.incrementAndGet();
                            }
                        }
                        return invoke(connection, method, args);
                    });
        }

        private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }

}
