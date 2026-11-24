package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataassets.runtime.DataAssetRuntimeAdapter;
import com.linewell.dataelement.elasticsearch.EsCommonService;
import com.linewell.dataelement.feature.identity.api.DataScopeMagicModule;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.NifiMutationAspect;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupOrganizer;
import com.linewell.dataelement.integration.nifi.canvas.mapping.MappingProjectionService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveDownstreamFixtures;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.function.FailableSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class NifiPipelineWriteMagicTest {
    private static final String TENANT = "tenant-a";
    private static final String API = "api/09.海通流程接口/";
    private static final String COMMON = "function/01.通用函数/";
    private final Map<String, Object> bindings = new LinkedHashMap<>();
    private final MappingProjectionService projections = mock(MappingProjectionService.class);
    private final DataScopeMagicModule scope = mock(DataScopeMagicModule.class);
    private final NifiClient nifi = mock(NifiClient.class);
    private final com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeStatusService runtimeStatus = mock(com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeStatusService.class);
    private final EsCommonService index = mock(EsCommonService.class);
    private final List<String> readSql = new ArrayList<>();
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private Parameters request;

    @BeforeEach
    void setUp() throws Throwable {
        var physical = new DriverManagerDataSource("jdbc:h2:mem:nifi_write_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(physical);
        jdbc.execute("""
                CREATE TABLE nifi_pipeline_t(id varchar(32) primary key, tenant_id varchar(32), is_del int, name varchar(32),
                    description varchar(100), created_at bigint, updated_at bigint, status varchar(32), dsl_json clob,
                    dsl_hash varchar(64), dsl_version bigint, nifi_process_group_id varchar(32), last_deployed_hash varchar(64),
                    last_deployed_at bigint, last_stopped_at bigint, node_mapping_json clob, last_bulletin_id bigint);
                CREATE TABLE data_access_agg_task_t(tid varchar(32), tenant_id varchar(32), is_del int,
                    data_catalog_id varchar(32), pipeline_id varchar(32), process_group_id varchar(32), created_time timestamp, updated_time timestamp,
                    created_by varchar(32), updated_by varchar(32), task_name varchar(32), task_desc varchar(100), task_status int,
                    source_db_id varchar(32), source_table_id varchar(32), source_table_primary_key varchar(32),
                    source_table_increment_key varchar(32), target_db_id varchar(32), target_table_id varchar(32), target_table_primary_key varchar(32));
                CREATE TABLE da_catalog_t(tid varchar(32), tenant_id varchar(32), is_del int,
                    source_table_id varchar(32), created_by varchar(32), org_id varchar(32),market_view_count bigint);
                CREATE TABLE da_prop_t(tid varchar(32),tenant_id varchar(32),parent_id varchar(32),data_type varchar(32),
                    prop_name varchar(32),prop_value clob,is_del int,updated_time timestamp,prop_type varchar(32));
                CREATE TABLE db_table_t(tid varchar(32), tenant_id varchar(32), is_del int, datasource_id varchar(32), source_catalog_id varchar(32));
                CREATE TABLE db_datasource_t(tid varchar(32), tenant_id varchar(32), is_del int, db_type varchar(20),
                    pool_cfg clob, created_by varchar(32), org_id varchar(32));
                CREATE TABLE data_pull_rule_t(tid varchar(32), tenant_id varchar(32), is_del int, datasource_id varchar(32),
                    source_table_id varchar(32), nifi_task_id varchar(32), updated_by varchar(32), updated_time timestamp);
                CREATE TABLE projection_probe(pipeline_id varchar(32));
                INSERT INTO da_catalog_t VALUES ('catalog','tenant-a',0,'source-table','user','org',0),
                    ('foreign','tenant-b',0,'source-table','user','org',0);
                INSERT INTO db_table_t VALUES ('source-table','tenant-a',0,'source-db','catalog');
                INSERT INTO db_datasource_t VALUES ('source-db','tenant-a',0,'api','{"accessMode":"capture"}','user','org');
                INSERT INTO da_prop_t VALUES ('prop','tenant-a','catalog','catalog','sourceTableId','source-table',0,CURRENT_TIMESTAMP,'0');
                INSERT INTO data_pull_rule_t VALUES ('rule','tenant-a',0,'source-db','source-table','old',null,null),
                    ('foreign','tenant-b',0,'source-db','source-table','foreign',null,null);
                """);
        var dynamic = new MagicDynamicDataSource();
        dynamic.setDefault(physical);
        var db = new SQLModule(dynamic);
        db.setDataSourceNode(dynamic.getDataSource());
        db.setResultProvider(new DefaultResultProvider("validation"));
        db.setDialectAdapter(new DialectAdapter());
        var columns = new ColumnMapperAdapter();
        columns.add(new DefaultColumnMapperProvider());
        db.setColumnMapperProvider(columns);
        db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
        db.setRowMapColumnMapper(column -> column);
        db.setSqlInterceptors(List.of());
        db.setNamedTableInterceptors(List.of());
        var manager = new DataSourceTransactionManager(physical);
        transaction = new TransactionTemplate(manager);
        var mutation = mock(NifiMutationAspect.class);
        when(mutation.withMutationLock(any())).thenAnswer(call -> call.<FailableSupplier<?, ?>>getArgument(0).get());
        when(scope.resource(any())).thenReturn(Map.of("scope", "ALL"));
        bindings.put("associateTask", (MagicScriptLambdaFunction) (context, args) -> {
            insertTask("task", (String) args[0], (String) args[1]);
            return null;
        });
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        bindings.putAll(Map.ofEntries(Map.entry("db", db), Map.entry("tableRead", TaskRuntimeMagicFixture.structuredReads(jdbc, readSql::add)),
                Map.entry("tableWrite", new StructuredTableWriteMagicModule(jdbc)), Map.entry("mapper", new ObjectMapper()),
                Map.entry("tenantRuntime", new TenantMagicModule(null)), Map.entry("mutation", mutation),
                Map.entry("projections", projections), Map.entry("dataScope", scope),
                Map.entry("nifi", nifi), Map.entry("runtimeStatus",runtimeStatus), Map.entry("organizer", new PipelineGroupOrganizer(nifi, jdbc)), Map.entry("index", index),
                Map.entry("transactionManager", manager), Map.entry("log", LoggerFactory.getLogger(getClass()))));
        register("respond", "35.海通流程列表超文本响应.ms", "nifi_pipe_list_response_v1", "action");
        register("existingCanvas", "42.海通目录关联画布.ms", "71a48eec67605a2d5da8ce93b2cc02cb", "catalogId");
        register("decodeCanvas", "43.海通解码保存画布.ms", "e06d4eb123494419e67f70f2fc03bceb", "row");
        register("writeCanvas", "44.海通持久化画布设计.ms", "3fce842fc70d1d259e9938d81f963e11", "submitted", "pipelineId", "tenantId", "verifyRevision");
        register("withRequest", "45.海通画布写入请求.ms", "44856f9262b98c0fb011f77d4c78abf1", "bodySignature", "input", "action");
        register("groupScope", "46.海通删除校验原生组.ms", "40fabd244d0a1e4572f9e8803b25be8d", "targets", "rootId", "owner");
        register("withAuthorizedGroups", "47.海通删除原生组响应.ms", "ec36858da4464ae3a44f14db2cc44f23",
                "parameterName", "deleteAuthorizedGroups");
    }

    @Test
    void candidateSwitchRollsBackRuntimeAndMappingVersionWhenTaskSyncFails() throws Exception {
        var canvas = canvas(create(null));
        var mappingVersions = mock(com.linewell.dataelement.integration.nifi.canvas.mapping.MappingVersionStore.class);
        bindings.put("transactions", transaction);
        bindings.put("mappingVersions", mappingVersions);
        var failSync = new java.util.concurrent.atomic.AtomicBoolean(true);
        bindings.put("syncTask", (MagicScriptLambdaFunction) (context, args) -> {
            if (failSync.get()) {
                throw new IllegalStateException("task sync failed");
            }
            return null;
        });
        register("writeRuntime", "52.海通更新画布运行记录.ms", "nifi_canvas_runtime_write_v1", "pipelineId", "tenantId", "changes");
        register("bindCandidate", "54.海通部署候选绑定.ms", "nifi_candidate_bind_v1", "canvas", "candidate", "tenantId");
        doAnswer(call -> {
            jdbc.update("INSERT INTO projection_probe VALUES (?)", canvas.id());
            return null;
        }).when(mappingVersions).activate(eq(canvas), anyMap(), eq("candidate"));
        var candidate = new com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping.Candidate("candidate",
                "a".repeat(64), canvas.designHash(), null, Map.of("rule", "fixed-version"));
        var variables = new LinkedHashMap<>(bindings);
        variables.putAll(Map.of("canvas", canvas, "candidate", candidate, "tenant", TENANT));
        var script = MagicScript.create("return bindCandidate(canvas, candidate, tenant)", null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> TenantContext.call(TENANT,
                () -> script.execute(new MagicScriptContext(variables)))).hasStackTraceContaining("task sync failed");
        assertThat(jdbc.queryForMap("SELECT status,nifi_process_group_id,last_deployed_hash FROM nifi_pipeline_t WHERE id=?", canvas.id()))
                .containsEntry("status", "SAVED").containsEntry("nifi_process_group_id", null).containsEntry("last_deployed_hash", null);
        assertThat(count("projection_probe")).isZero();

        failSync.set(false);
        var switched = (Pipeline) TenantContext.call(TENANT, () -> script.execute(new MagicScriptContext(variables)));
        assertThat(switched.status()).isEqualTo(PipelineStatus.STOPPED);
        assertThat(switched.nifiProcessGroupId()).isEqualTo("candidate");
        assertThat(count("projection_probe")).isOne();
        assertThat(jdbc.queryForObject("SELECT last_deployed_hash FROM nifi_pipeline_t WHERE id=?", String.class, canvas.id()))
                .isEqualTo(candidate.deploymentHash());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {20, 100})
    void undeployReadsCanvasAndTaskBindingsOnceAndCommitsOnlyAfterNativeCleanup(int size) throws Exception {
        var canvas = canvas(create(null));
        jdbc.update("UPDATE nifi_pipeline_t SET status='STOPPED',nifi_process_group_id='owned' WHERE id=?", canvas.id());
        var tasks = new ArrayList<Object[]>();
        for (int i = 0; i < size; i++) {
            tasks.add(new Object[]{"task-" + i, TENANT, canvas.id(), "owned"});
        }
        tasks.add(new Object[]{"foreign", "tenant-b", canvas.id(), "foreign-group"});
        jdbc.batchUpdate("INSERT INTO data_access_agg_task_t(tid,tenant_id,pipeline_id,process_group_id,is_del) VALUES(?,?,?,?,0)", tasks);
        bindings.put("transactions", transaction);
        var errorStore = mock(com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService.class);
        bindings.put("errorStore", errorStore);
        bindings.put("lineage", mock(com.linewell.dataelement.feature.metadatagovernance.application.LineageAnalysisService.class));
        bindings.put("syncTask", (MagicScriptLambdaFunction) (context, args) -> {
            if (args[1].equals("group")) {
                jdbc.update("UPDATE data_access_agg_task_t SET process_group_id=NULL WHERE tenant_id=? AND pipeline_id=?", TENANT, canvas.id());
            }
            return null;
        });
        register("respond", "53.海通生命周期错误响应.ms", "nifi_lifecycle_response_v1", "action");
        register("findCanvas", "48.海通读取租户画布.ms", "nifi_pipeline_find_v1", "flowId", "tenantId");
        register("writeRuntime", "52.海通更新画布运行记录.ms", "nifi_canvas_runtime_write_v1", "pipelineId", "tenantId", "changes");
        var ownGroup = new ObjectMapper().createObjectNode();
        ownGroup.putObject("component").put("id", "owned").put("parentGroupId", "root")
                .put("comments", "data-elements:v1:" + TENANT + ":pipeline:" + canvas.id());
        when(nifi.getProcessGroup("owned")).thenReturn(ownGroup);
        org.mockito.Mockito.doThrow(new IllegalStateException("queue cleanup failed")).doNothing().when(nifi).cleanupProcessGroup("owned");
        readSql.clear();
        assertThat(endpoint("06.部署与启停/05.卸载流程.ms", "nifi_pipeline_undeploy_post_v1", canvas.id(), Map.of(), null).getStatusCode().value()).isEqualTo(500);
        assertThat(readSql).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT nifi_process_group_id FROM nifi_pipeline_t WHERE id=?", String.class, canvas.id())).isEqualTo("owned");

        readSql.clear();
        assertThat(endpoint("06.部署与启停/05.卸载流程.ms", "nifi_pipeline_undeploy_post_v1", canvas.id(), Map.of(), null).getStatusCode().value()).isEqualTo(200);
        assertThat(readSql).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_access_agg_task_t WHERE tid LIKE 'task-%' AND process_group_id IS NULL", Long.class)).isEqualTo(size);
        assertThat(jdbc.queryForObject("SELECT process_group_id FROM data_access_agg_task_t WHERE tid='foreign'", String.class)).isEqualTo("foreign-group");
        verify(nifi, never()).getProcessGroup("foreign-group");
    }

    @Test
    void createUsesNewIdAndBindsCurrentTenantOnceWhileReusePreservesState() throws Exception {
        var created = canvas(create("catalog"));
        assertThat(created.status()).isEqualTo(PipelineStatus.SAVED);
        assertThat(created.id()).matches("[0-9]{17,19}");
        assertThat(rule("rule")).isEqualTo(created.id());
        assertThat(rule("foreign")).isEqualTo("foreign");
        jdbc.update("UPDATE nifi_pipeline_t SET status='RUNNING',nifi_process_group_id='native' WHERE id=?", created.id());
        var reused = canvas(create("catalog"));
        assertThat(reused.id()).isEqualTo(created.id());
        assertThat(reused.status()).isEqualTo(PipelineStatus.RUNNING);
        assertThat(reused.nifiProcessGroupId()).isEqualTo("native");
        assertThat(count("data_access_agg_task_t")).isOne();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(value = {
            "'  API  ' | '{\"older\":{\"ACCESSMODE\":  \"CaPtUrE\"}}' | true",
            "api | 'legacy \"accessMode\": \"pull\" damaged text' | true",
            "api | '{\"accessMode\":\"push\"}' | false",
            "api | | false",
            "mysql | '{\"accessMode\":\"capture\"}' | false"
    }, delimiter = '|')
    void createBindsOnlyCurrentActiveRulesForHistoricalApiPullConfigurations(String type, String config, boolean applies)
            throws Exception {
        jdbc.update("UPDATE db_datasource_t SET db_type=?,pool_cfg=? WHERE tid='source-db'", type, config);
        jdbc.update("INSERT INTO data_pull_rule_t VALUES ('deleted','tenant-a',1,'source-db','source-table','old',null,null),"
                + " ('other-source','tenant-a',0,'different-db','source-table','old',null,null),"
                + " ('other-table','tenant-a',0,'source-db','different-table','old',null,null)");
        var before = java.time.LocalDateTime.now().minusSeconds(1);
        var created = canvas(create("catalog"));
        assertThat(rule("rule")).isEqualTo(applies ? created.id() : "old");
        if (applies) {
            var audit = jdbc.queryForMap("SELECT updated_by,updated_time FROM data_pull_rule_t WHERE tid='rule'");
            assertThat(audit.get("updated_by")).isEqualTo("system");
            assertThat(((java.sql.Timestamp) audit.get("updated_time")).toLocalDateTime())
                    .isBetween(before, java.time.LocalDateTime.now().plusSeconds(1));
        }
        assertThat(rule("deleted")).isEqualTo("old");
        assertThat(rule("other-source")).isEqualTo("old");
        assertThat(rule("other-table")).isEqualTo("old");
        assertThat(rule("foreign")).isEqualTo("foreign");
    }

    @Test
    void validCatalogStillCreatesWhenSourceOrRuleIsUnavailable() throws Exception {
        jdbc.update("UPDATE db_datasource_t SET is_del=1 WHERE tid='source-db'");
        canvas(create("catalog"));
        assertThat(rule("rule")).isEqualTo("old");
        jdbc.update("DELETE FROM data_access_agg_task_t");
        jdbc.update("UPDATE db_datasource_t SET is_del=0 WHERE tid='source-db'");
        jdbc.update("DELETE FROM data_pull_rule_t WHERE tid='rule'");
        canvas(create("catalog"));
        assertThat(count("nifi_pipeline_t")).isEqualTo(2);
        assertThat(rule("foreign")).isEqualTo("foreign");
    }

    @Test
    void independentBindingSurvivesOuterRollbackAndFailureDoesNotPreventCreation() throws Exception {
        var endpoint = script(API + "02.流程设计/02.创建流程.ms", "faf0e7885f4429edbeaa5add552fe155");
        var id = TenantContext.call(TENANT, () -> transaction.execute(status -> {
            var created = canvas(invoke(endpoint, "", Map.of("catalogTid", new String[]{"catalog"}), createBody()));
            status.setRollbackOnly();
            return created.id();
        }));
        assertThat(count("nifi_pipeline_t")).isZero();
        assertThat(count("data_access_agg_task_t")).isZero();
        assertThat(rule("rule")).isEqualTo(id);
        jdbc.update("UPDATE data_pull_rule_t SET nifi_task_id='old' WHERE tid='rule'");
        jdbc.execute("ALTER TABLE data_pull_rule_t ADD CHECK (nifi_task_id IN ('old','foreign'))");
        canvas(create("catalog"));
        assertThat(count("nifi_pipeline_t")).isOne();
        assertThat(count("data_access_agg_task_t")).isOne();
        assertThat(rule("rule")).isEqualTo("old");
    }

    @Test
    void taskAndCanvasRollbackTogetherOnFailureAndForeignCatalogIsRejected() throws Exception {
        assertThat(create("foreign").getBody()).isNotInstanceOf(Pipeline.class);
        bindings.put("associateTask", (MagicScriptLambdaFunction) (context, args) -> {
            insertTask("failure", (String) args[0], (String) args[1]);
            throw new IllegalStateException("validation task failure");
        });
        assertThat(create("catalog").getBody()).isNotInstanceOf(Pipeline.class);
        assertThat(count("nifi_pipeline_t")).isZero();
        assertThat(count("data_access_agg_task_t")).isZero();
        assertThat(rule("rule")).isEqualTo("old");
    }

    @Test
    void canonicalCreationAndTaskAssociationUseOneTransactionWithoutLegacyAssetView() throws Exception {
        bindings.put("catalogProjection", mock(DataAssetRuntimeAdapter.class));
        register("associateTask", "27.海通画布接入任务关联.ms", "fbbbebbeee9346e1813e9b1bf635eb09",
                "rawCatalogTid", "pipelineId", "pipelineName", "dsl");
        var created = canvas(create(" source-table "));
        assertThat(jdbc.queryForMap("SELECT * FROM data_access_agg_task_t WHERE pipeline_id=?", created.id()))
                .containsEntry("tenant_id", TENANT).containsEntry("data_catalog_id", "catalog")
                .containsEntry("source_table_id", "source-table").containsEntry("task_status", 0)
                .containsEntry("created_by", null).containsEntry("updated_by", null);
    }

    @Test
    void designRevisionPrecedesTimestampAndSaveLeavesNativeStateAndDescription() throws Exception {
        var created = canvas(create(null));
        jdbc.update("UPDATE nifi_pipeline_t SET status='RUNNING',nifi_process_group_id='native',updated_at=updated_at+1 WHERE id=?", created.id());
        var response = save(created.id(), "新设计", Map.of("expectedUpdatedAt", new String[]{"0"}));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        var saved = canvas(save(created.id(), "新设计", Map.of("expectedUpdatedAt", new String[]{"0"},
                "expectedDesignHash", new String[]{created.designHash()})));
        assertThat(saved.name()).isEqualTo("新设计");
        assertThat(saved.description()).isEqualTo("已保存说明");
        assertThat(saved.status()).isEqualTo(PipelineStatus.RUNNING);
        assertThat(saved.nifiProcessGroupId()).isEqualTo("native");
        assertThat(save(created.id(), "冲突", Map.of("expectedDesignHash", new String[]{created.designHash()}))
                .getStatusCode().value()).isEqualTo(409);
        jdbc.update("UPDATE nifi_pipeline_t SET tenant_id='tenant-b' WHERE id=?", created.id());
        assertThat(save(created.id(), "越权", Map.of()).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void projectionAndChangeRollbackTogether() throws Exception {
        var created = canvas(create(null));
        doAnswer(call -> {
            jdbc.update("INSERT INTO projection_probe VALUES(?)", call.getArgument(0, Pipeline.class).id());
            throw new IllegalStateException("validation projection failure");
        }).when(projections).persist(any());
        assertThat(save(created.id(), "保存失败", Map.of()).getBody()).isNotInstanceOf(Pipeline.class);
        assertThat(count("projection_probe")).isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM nifi_pipeline_t WHERE id=?", String.class, created.id())).isEqualTo(created.name());
    }

    @Test void legacySaveRepairsOnlyTheVerifiedNodeAndKeepsDesignWritableWhileOriginalNodeIsOffline()throws Exception {
        var created=canvas(create(null));
        String mapping="{\"primaryProcessorIds\":{\"source\":\"processor\"},\"oneShotSources\":[\"processor\"],\"cleanupConfirmationRequired\":true}";
        jdbc.update("UPDATE nifi_pipeline_t SET status='RUNNING',nifi_process_group_id='native',node_mapping_json=? WHERE id=?",mapping,created.id());
        when(runtimeStatus.resolveLegacyOwner(any())).thenReturn("original-node");
        var repaired=canvas(save(created.id(),"历史设计",Map.of()));
        assertThat(repaired.nodeMapping().runtimeNodeId()).isEqualTo("original-node");
        assertThat(repaired.nodeMapping().primaryProcessorIds()).containsEntry("source","processor");
        assertThat(repaired.nodeMapping().oneShotSources()).containsExactly("processor");
        assertThat(repaired.nifiProcessGroupId()).isEqualTo("native");assertThat(repaired.status()).isEqualTo(PipelineStatus.RUNNING);
        var persisted=new ObjectMapper().readTree(jdbc.queryForObject("SELECT node_mapping_json FROM nifi_pipeline_t WHERE id=?",String.class,created.id()));
        assertThat(persisted.path("runtimeNodeId").asText()).isEqualTo("original-node");
        jdbc.update("UPDATE nifi_pipeline_t SET node_mapping_json=? WHERE id=?",mapping,created.id());
        when(runtimeStatus.resolveLegacyOwner(any())).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"原节点暂不可达"));
        var offline=canvas(save(created.id(),"离线仍保存",Map.of()));
        assertThat(offline.name()).isEqualTo("离线仍保存");assertThat(offline.nodeMapping().runtimeNodeId()).isNull();
        assertThat(offline.nifiProcessGroupId()).isEqualTo("native");verify(nifi,never()).deleteProcessGroup(any());
    }

    @Test
    void savesHiveRawDesignAndRetainsDescriptionAcrossSuccessiveRevisions() throws Exception {
        var loaded = canvas(create(null));
        var source = new Pipeline.Node("s", "source.mysql", "来源", "source", 0, 0, Map.of());
        var mapping = new Pipeline.Node("m", "transform.field-enrichment", "字段映射", "transform", 300, 0, Map.of());
        var hive = HiveDownstreamFixtures.rawTarget(new Pipeline.Node("h", "sink.hive", "Hive", "sink", 600, 0, Map.of()));
        var dsl = new Pipeline.Dsl(1, List.of(source, mapping, hive), List.of(
                new Pipeline.Edge("sm", "s", "m", "default"), new Pipeline.Edge("mh", "m", "h", "default")));
        var first = canvas(endpoint("02.流程设计/05.保存流程.ms", "660b71dbe633aaa4bd79dd68e0baad70", loaded.id(),
                Map.of("expectedUpdatedAt", new String[]{loaded.updatedAt().toString()}), Map.of("name", "Hive", "dsl", dsl)));
        assertThat(first.dsl().nodes()).extracting(Pipeline.Node::id).containsExactly("s", "h");
        assertThat(first.dsl().nodes().getFirst().config()).containsEntry("schedulingPeriod", "1 day");
        assertThat(first.dsl().nodes().getLast()).isEqualTo(hive);
        assertThat(first.dsl().edges()).containsExactly(new Pipeline.Edge("mh-hive-raw-0", "s", "h", "default"));
        assertThat(first.description()).isEqualTo("已保存说明");
        var next = canvas(save(loaded.id(), "再保存", Map.of("expectedDesignHash", new String[]{first.designHash()})));
        assertThat(next.description()).isEqualTo(first.description());
        assertThat(next.updatedAt()).isGreaterThan(first.updatedAt());
    }

    @Test
    void missingBodyKeepsTheLegacyDiagnosticEnvelope() throws Exception {
        var response = endpoint("02.流程设计/02.创建流程.ms", "faf0e7885f4429edbeaa5add552fe155", "", Map.of(), null);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var error = (CommonResponse<?>) response.getBody();
        assertThat(error.getCode()).isEqualTo(500);
        assertThat(error.getMsg()).startsWith("Required request body is missing: public ")
                .contains("PipelineController.create(");
    }

    @Test
    void sourceTableAliasCannotBypassLinkedCatalogOwnerScope() throws Exception {
        when(scope.resource(any())).thenReturn(Map.of("scope", "OWNER", "userId", "user"));
        jdbc.update("UPDATE da_catalog_t SET created_by='different-user' WHERE tid='catalog'");
        jdbc.update("INSERT INTO db_table_t VALUES('catalog','tenant-a',0,'source-db',null)");
        assertThat(create("source-table").getBody()).isNotInstanceOf(Pipeline.class);
        assertThat(create("catalog").getBody()).isNotInstanceOf(Pipeline.class);
        assertThat(count("nifi_pipeline_t")).isZero();
        assertThat(count("data_access_agg_task_t")).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {20, 100})
    void deleteCanvasBatchesCatalogMirrorsAndKeepsForeignBindings(int size) throws Exception {
        var created = canvas(create(null));
        var params = new ArrayList<Object[]>();
        for (int i = 0; i < size; i++) {
            params.add(new Object[]{"catalog-" + i, "task-" + i, "prop-" + i});
        }
        jdbc.batchUpdate("INSERT INTO da_catalog_t(tid,tenant_id,is_del,market_view_count) VALUES(?,'tenant-a',0,0)",
                params.stream().map(p -> new Object[]{p[0]}).toList());
        jdbc.batchUpdate("INSERT INTO data_access_agg_task_t(tid,tenant_id,is_del,data_catalog_id,pipeline_id,task_status) VALUES(?,'tenant-a',0,?,?,1)",
                params.stream().map(p -> new Object[]{p[1], p[0], created.id()}).toList());
        jdbc.batchUpdate("INSERT INTO da_prop_t VALUES(?,'tenant-a',?,'catalog','accessTaskId',?,0,CURRENT_TIMESTAMP,'0')",
                params.stream().map(p -> new Object[]{p[2], p[0], p[1]}).toList());
        jdbc.update("UPDATE da_prop_t SET prop_value='different-task' WHERE tid='prop-0'");
        jdbc.update("INSERT INTO da_prop_t VALUES('foreign-prop','tenant-b','catalog-0','catalog','accessTaskId','task-0',0,CURRENT_TIMESTAMP,'0')");
        readSql.clear();
        assertThat(deleteCanvas(created.id()).getStatusCode().value()).isEqualTo(204);
        assertThat(readSql).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM da_prop_t WHERE prop_value=''", Integer.class)).isEqualTo(size - 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_access_agg_task_t WHERE is_del=1 AND task_status=0", Integer.class))
                .isEqualTo(size + 1);
        assertThat(jdbc.queryForObject("SELECT CAST(prop_value AS VARCHAR) FROM da_prop_t WHERE tid='foreign-prop'", String.class))
                .isEqualTo("task-0");
        verify(index).bulkMergeCatalogProjections(eq("dataassets"), org.mockito.ArgumentMatchers.argThat(ids -> ids.size() == size - 1), any(), any());
        assertThat(deleteCanvas(created.id()).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void deleteCanvasWaitsForNativeCleanupAndRetriesAfterFailure() throws Exception {
        var created = canvas(create(null));
        var leaf = group("native", "root", "data-elements:v1:" + TENANT + ":pipeline:" + created.id());
        when(nifi.getProcessGroup("native")).thenReturn(leaf);
        when(nifi.listProcessGroups("root")).thenReturn(List.of(leaf));
        jdbc.update("UPDATE nifi_pipeline_t SET nifi_process_group_id='native' WHERE id=?", created.id());
        org.mockito.Mockito.doThrow(new NifiException("validation queue drain failed", 504, null)).doNothing()
                .when(nifi).cleanupProcessGroup("native");
        assertThat(deleteCanvas(created.id()).getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT is_del FROM nifi_pipeline_t WHERE id=?", Integer.class, created.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT is_del FROM data_access_agg_task_t WHERE pipeline_id=?", Integer.class, created.id())).isZero();
        assertThat(deleteCanvas(created.id()).getStatusCode().value()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT is_del FROM nifi_pipeline_t WHERE id=?", Integer.class, created.id())).isOne();
    }

    @Test
    void nativeTreeAuthorizesEveryGroupBeforeAnyCleanupAndUsesPostOrder() throws Exception {
        var parent = group("parent", "root", "data-elements:v1:" + TENANT + ":application:validation");
        var own = group("child", "parent", "data-elements:v1:" + TENANT + ":pipeline:validation");
        var foreign = group("foreign", "parent", "data-elements:v1:tenant-b:pipeline:other");
        when(nifi.getProcessGroup("parent")).thenReturn(parent);
        when(nifi.listProcessGroups("parent")).thenReturn(List.of(own, foreign));
        assertThat(deleteTree("parent").getStatusCode().value()).isEqualTo(404);
        verify(nifi, never()).cleanupProcessGroup(any());
        assertThat(deleteTree("root").getStatusCode().value()).isEqualTo(404);
        when(nifi.listProcessGroups("parent")).thenReturn(List.of(own));
        assertThat(endpoint("07.流程组/01.按组标识删除流程组.ms", "0f217152c7fa1555211a98ac7d1db9d4", "",
                Map.of("processGroupId", new String[]{"parent"}), null).getStatusCode().value()).isEqualTo(409);
        assertThat(deleteTree("parent").getStatusCode().value()).isEqualTo(200);
        var order = inOrder(nifi);
        order.verify(nifi).cleanupProcessGroup("child");
        order.verify(nifi).cleanupProcessGroup("parent");
    }

    private ResponseEntity<?> deleteCanvas(String id) throws Exception {
        return endpoint("02.流程设计/06.删除流程.ms", "c6f76e36f0106bc81f93a2f372c80571", id, Map.of(), null);
    }

    private ResponseEntity<?> deleteTree(String id) throws Exception {
        return endpoint("07.流程组/02.按父组标识删除流程树.ms", "5d340458be2a074424507a72e8f13c6e", "",
                Map.of("parentPgId", new String[]{id}), null);
    }

    private static com.fasterxml.jackson.databind.JsonNode group(String id, String parent, String marker) {
        var node = new ObjectMapper().createObjectNode().put("id", id);
        node.putObject("component").put("id", id).put("parentGroupId", parent).put("comments", marker);
        return node;
    }

    private ResponseEntity<?> create(String catalog) throws Exception {
        var params = catalog == null ? Map.<String, String[]>of() : Map.of("catalogTid", new String[]{catalog});
        return endpoint("02.流程设计/02.创建流程.ms", "faf0e7885f4429edbeaa5add552fe155", "", params, createBody());
    }

    private static Map<String, Object> createBody() {
        return Map.of("id", "caller-id", "name", "初始设计", "description", "已保存说明", "dsl", emptyDesign());
    }

    private ResponseEntity<?> save(String id, String name, Map<String, String[]> params) throws Exception {
        return endpoint("02.流程设计/05.保存流程.ms", "660b71dbe633aaa4bd79dd68e0baad70", id, params,
                Map.of("name", name, "status", "DRAFT", "nifiProcessGroupId", "caller-group", "dsl", emptyDesign()));
    }

    private static Map<String, Object> emptyDesign() {
        return Map.of("version", 1, "nodes", List.of(), "edges", List.of());
    }

    private ResponseEntity<?> endpoint(String path, String id, String canvasId, Map<String, String[]> params,
            Map<String, Object> body) throws Exception {
        return invoke(script(API + path, id), canvasId, params, body);
    }

    private ResponseEntity<?> invoke(MagicScript script, String canvasId, Map<String, String[]> params, Map<String, Object> body) {
        var bindings = new LinkedHashMap<>(this.bindings);
        request = new Parameters(params);
        bindings.put("body", body);
        bindings.putAll(Map.of("request", request, "param", Map.of("id", canvasId)));
        return TenantContext.call(TENANT, () -> (ResponseEntity<?>) script.execute(new MagicScriptContext(bindings)));
    }

    private void register(String alias, String path, String id, String... parameters) throws Exception {
        var script = script(COMMON + path, id);
        bindings.put(alias, (MagicScriptLambdaFunction) (context, args) -> {
            var scope = new LinkedHashMap<>(bindings);
            scope.put("request", request);
            for (int i = 0; i < parameters.length; i++) {
                scope.put(parameters[i], args[i]);
            }
            return script.execute(new MagicScriptContext(scope));
        });
    }

    private static MagicScript script(String path, String id) throws Exception {
        var source = CanonicalMagicSources.byPath(path, id);
        MagicScript.create(source, null).compile();
        return MagicScript.create(source.replaceAll("(?m)^import (?:'@[^\\r\\n]+|tenantRuntime|tableRead|tableWrite|dataScope|request|log|[^\\r\\n]+ as (?:mapper|mutation|catalogProjection|projections|transactions|transactionManager|organizer|nifi|index|mappingVersions|errorStore|lineage|runtimeStatus))\\R", ""), null);
    }

    private String rule(String id) {
        return jdbc.queryForObject("SELECT nifi_task_id FROM data_pull_rule_t WHERE tid=?", String.class, id);
    }

    private void insertTask(String id, String catalog, String flow) {
        jdbc.update("INSERT INTO data_access_agg_task_t(tid,tenant_id,is_del,data_catalog_id,pipeline_id,created_time)"
                + " VALUES(?,?,0,?,?,CURRENT_TIMESTAMP)", id, TENANT, catalog, flow);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static Pipeline canvas(ResponseEntity<?> response) {
        assertThat(response.getBody()).isInstanceOf(Pipeline.class);
        return (Pipeline) response.getBody();
    }

    public record Parameters(Map<String, String[]> values) {
        public List<String> getValues(String name) {
            var raw = values.get(name);
            return raw == null ? null : List.of(raw);
        }
    }
}
