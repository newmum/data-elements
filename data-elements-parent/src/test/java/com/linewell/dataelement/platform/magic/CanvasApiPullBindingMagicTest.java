package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.linewell.dataelement.dataassets.runtime.DataAssetRuntimeAdapter;
import com.linewell.dataelement.dataassets.runtime.DataSourceConnectionPropertyResolver;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessAggTaskTService;
import com.linewell.dataelement.integration.nifi.canvas.controller.PipelineController;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineTaskLifecycleSynchronizer;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantRoutingDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.ssssssss.magicapi.core.service.MagicAPIService;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class CanvasApiPullBindingMagicTest {
    private static final String TENANT = "tenant";
    private static final String CATALOG = "catalog";
    private static final String SOURCE = "source";
    private static final String TABLE = "table";
    private static final String PIPELINE = "new-flow";
    private static final String OLD_PIPELINE = "old-flow";
    private static final String FUNCTION = "/common/bindCanvasApiPullRules";
    private static final String TASK_FUNCTION = "/common/ensureNifiCatalogAccessTask";
    private static final String CAPTURE = "{\"accessMode\":\"capture\"}";

    private Fixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new Fixture(new MySqlModeDataSource());
    }

    @Test
    void optionalBindingCommitsOnItsOwnConnectionWhenTheCanvasTransactionRollsBack() {
        try (var scope = TenantContext.use(TENANT)) {
            fixture.transactions.executeWithoutResult(status -> {
                assertThat(fixture.controller.create(pipeline(null), CATALOG).id()).isEqualTo(PIPELINE);
                status.setRollbackOnly();
            });

            assertThat(fixture.rowCount("canvas_probe")).isZero();
            assertThat(fixture.rowCount("data_access_agg_task_t")).isZero();
            assertThat(fixture.rule("current")).containsEntry("nifi_task_id", PIPELINE).containsEntry("updated_by", "system");
            verify(fixture.magicApi).invoke(FUNCTION, Map.of("datasourceId", SOURCE, "sourceTableId", TABLE,
                    "pipelineId", PIPELINE));
        }
    }

    @Test
    void databaseFailureInTheOptionalBindingStillCommitsTheCanvasAndItsTask() {
        try (var scope = TenantContext.use(TENANT)) {
            fixture.jdbc.execute("alter table data_pull_rule_t add check (nifi_task_id <> 'new-flow')");

            Pipeline result = fixture.transactions.execute(status -> fixture.controller.create(pipeline(null), CATALOG));

            assertThat(result.id()).isEqualTo(PIPELINE);
            assertThat(fixture.rowCount("canvas_probe")).isEqualTo(1);
            assertThat(fixture.rowCount("data_access_agg_task_t")).isEqualTo(1);
            verify(fixture.magicApi).invoke(eq(FUNCTION), anyMap());
            assertThat(fixture.rule("current")).containsEntry("nifi_task_id", OLD_PIPELINE)
                    .containsEntry("updated_by", "original");
        }
    }

    @Test
    void failureOfTheRequiredTaskAssociationRollsBackTheCanvasAndSkipsTheBinding() {
        doThrow(new IllegalStateException("required task association failed"))
                .when(fixture.lifecycle).ensureCatalogAccessTask(eq(CATALOG), any());
        try (var scope = TenantContext.use(TENANT)) {
            assertThatThrownBy(() -> fixture.transactions.execute(status -> fixture.controller.create(pipeline(null), CATALOG)))
                    .isInstanceOf(IllegalStateException.class).hasMessage("required task association failed");

            assertThat(fixture.rowCount("canvas_probe")).isZero();
            verifyNoInteractions(fixture.magicApi);
            assertThat(fixture.rule("current")).containsEntry("nifi_task_id", OLD_PIPELINE);
        }
    }

    @Test
    void actualMagicTaskWriteCommitsWithTheCanvasWithoutTheRetiredCatalogView() throws Exception {
        PipelineController controller = fixture.withActualTaskAssociation();
        try (var scope = TenantContext.use(TENANT)) {
            assertThat(fixture.transactions.execute(status -> controller.create(pipeline(null), " catalog ")).id())
                    .isEqualTo(PIPELINE);
            assertThat(fixture.rowCount("canvas_probe")).isEqualTo(1);
            assertThat(fixture.rowCount("data_access_agg_task_t")).isEqualTo(1);
            assertThat(fixture.jdbc.queryForMap("select * from data_access_agg_task_t where pipeline_id=?", PIPELINE))
                    .containsEntry("tenant_id", TENANT).containsEntry("data_catalog_id", CATALOG)
                    .containsEntry("source_table_id", TABLE).containsEntry("source_db_id", SOURCE)
                    .containsEntry("task_status", 0).containsEntry("created_by", null).containsEntry("updated_by", null);
        }
    }

    @Test
    void actualMagicTaskWriteRollsBackWithTheCanvasAndRejectsForeignCatalogs() throws Exception {
        PipelineController controller = fixture.withActualTaskAssociation();
        try (var scope = TenantContext.use(TENANT)) {
            fixture.transactions.executeWithoutResult(status -> {
                controller.create(pipeline(null), CATALOG);
                assertThat(fixture.rowCount("data_access_agg_task_t")).isEqualTo(1);
                status.setRollbackOnly();
            });
            assertThat(fixture.rowCount("canvas_probe")).isZero();
            assertThat(fixture.rowCount("data_access_agg_task_t")).isZero();

            fixture.jdbc.update("insert into da_catalog_t(tid, tenant_id, is_del) values ('foreign', 'other-tenant', 0)");
            assertThatThrownBy(() -> fixture.transactions.execute(status -> controller.create(pipeline(null), "foreign")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("无法关联接入任务");
            assertThat(fixture.rowCount("canvas_probe")).isZero();
            assertThat(fixture.rowCount("data_access_agg_task_t")).isZero();
        }
    }

    @ParameterizedTest
    @CsvSource({"inline, , ", "registered, registered-db, registered-table", "selected, selected-db, "})
    void magicPreservesPermanentTargetReferences(String kind, String expectedDb, String expectedTable) throws Exception {
        fixture.withActualTaskAssociation();
        var config = new LinkedHashMap<String, Object>(Map.of("jdbcUrl", "jdbc:mysql://fixture.invalid/test", "table", "copy"));
        if ("registered".equals(kind)) {
            config.put("registeredDatasourceId", "registered-db");
            config.put("targetTableId", "registered-table");
        } else if ("selected".equals(kind)) {
            config.put("selectedDatabaseId", "selected-db");
        }
        var dsl = new Pipeline.Dsl(1, List.of(new Pipeline.Node("sink", "sink.jdbc", "目标", "sink", 0, 0, config)), List.of());
        try (var scope = TenantContext.use(TENANT)) {
            fixture.magicApi.invoke(TASK_FUNCTION, Map.of("rawCatalogTid", " undefined ", "pipelineId", PIPELINE,
                    "pipelineName", "流程", "dsl", dsl));
            assertThat(fixture.jdbc.queryForMap("select * from data_access_agg_task_t where pipeline_id=?", PIPELINE))
                    .containsEntry("target_db_id", expectedDb).containsEntry("target_table_id", expectedTable)
                    .containsEntry("data_catalog_id", null);
        }
    }

    @Test
    void reusingAnExistingCanvasDoesNotPublishABindingOrCreateAnAccessTask() {
        Pipeline existing = pipeline(OLD_PIPELINE);
        when(fixture.repository.findById(OLD_PIPELINE)).thenReturn(Optional.of(existing));
        when(fixture.repository.update(eq(OLD_PIPELINE), any())).thenAnswer(call ->
                call.<UnaryOperator<Pipeline>>getArgument(1).apply(existing));
        try (var scope = TenantContext.use(TENANT)) {
            fixture.insertTask(OLD_PIPELINE);

            Pipeline result = fixture.transactions.execute(status -> fixture.controller.create(pipeline(null), CATALOG));

            assertThat(result.id()).isEqualTo(OLD_PIPELINE);
            verify(fixture.repository, never()).save(any());
            verifyNoInteractions(fixture.magicApi, fixture.lifecycle);
            assertThat(fixture.rule("current")).containsEntry("updated_by", "original");
        }
    }

    @ParameterizedTest
    @CsvSource(value = {
            "'  API  ' | '{\"older\":{\"ACCESSMODE\":  \"CaPtUrE\"}}' | true",
            "api | 'legacy \"accessMode\": \"pull\" damaged text' | true",
            "api | '{\"accessMode\":\"push\"}' | false",
            "api | | false",
            "mysql | '{\"accessMode\":\"capture\"}' | false"
    }, delimiter = '|')
    void magicPreservesTheLegacyDatasourcePredicate(String type, String config, boolean applies) {
        try (var scope = TenantContext.use(TENANT)) {
            fixture.jdbc.update("update db_datasource_t set db_type=?,pool_cfg=? where tid=?", type, config, SOURCE);

            var result = fixture.invoke(SOURCE, TABLE, PIPELINE);

            assertThat(result).containsEntry("applicable", applies);
            if (applies) {
                assertThat(result).containsEntry("boundRules", 1).containsEntry("pipelineId", PIPELINE)
                        .containsEntry("message", "表级拉取规则已绑定 NiFi 任务；可在画布中部署、手动启动或按 Cron 调度");
            } else {
                assertThat(result).doesNotContainKey("boundRules")
                        .containsEntry("message", "当前来源不是 API 数据拉取，未绑定表规则");
            }
            assertThat(fixture.rule("current")).containsEntry("nifi_task_id", applies ? PIPELINE : OLD_PIPELINE);
        }
    }

    @Test
    void magicFiltersByTenantDatasourceAndActiveRuleAndKeepsApplicationAuditValues() {
        try (var scope = TenantContext.use(TENANT)) {
            fixture.insertRule("foreign", "other-tenant", SOURCE, TABLE, 0);
            fixture.insertRule("deleted", TENANT, SOURCE, TABLE, 1);
            fixture.insertRule("different-source", TENANT, "other-source", "other-table", 0);
            var before = LocalDateTime.now().minusSeconds(1);

            assertThat(fixture.invoke(SOURCE, TABLE, PIPELINE)).containsEntry("boundRules", 1);
            assertThat(fixture.rule("current"))
                    .containsEntry("nifi_task_id", PIPELINE).containsEntry("updated_by", "system");
            assertThat(((Timestamp) fixture.rule("current").get("updated_time")).toLocalDateTime())
                    .isBetween(before, LocalDateTime.now().plusSeconds(1));
            assertThat(fixture.invoke(SOURCE, TABLE, PIPELINE)).containsEntry("boundRules", 1);

            assertThat(fixture.invoke(SOURCE, "other-table", PIPELINE)).containsEntry("boundRules", 0)
                    .containsEntry("message", "当前表尚未同步出 API 拉取规则，请返回第二步保存表标注后重试");
            fixture.jdbc.update("delete from data_pull_rule_t where tid=?", "current");
            assertThat(fixture.invoke(SOURCE, TABLE, PIPELINE)).containsEntry("boundRules", 0);
            fixture.insertRule("replacement", TENANT, SOURCE, TABLE, 0);
            assertThat(fixture.invoke(SOURCE, TABLE, PIPELINE)).containsEntry("boundRules", 1);

            for (String untouched : List.of("foreign", "deleted", "different-source")) {
                assertThat(fixture.rule(untouched)).containsEntry("nifi_task_id", OLD_PIPELINE)
                        .containsEntry("updated_by", "original");
            }
        }
    }

    @Test
    void magicRejectsMissingDeletedAndOtherTenantDatasourcesAndRequiresContext() {
        assertThatThrownBy(() -> fixture.invoke(SOURCE, TABLE, PIPELINE)).hasStackTraceContaining("租户");
        try (var scope = TenantContext.use(TENANT)) {
            assertThatThrownBy(() -> fixture.invoke(SOURCE, TABLE, "  ")).hasStackTraceContaining("NiFi 流程标识不能为空");
            fixture.insertDatasource("deleted", TENANT, 1);
            fixture.insertDatasource("foreign", "other-tenant", 0);
            for (String source : List.of("missing", "deleted", "foreign")) {
                assertThatThrownBy(() -> fixture.invoke(source, TABLE, PIPELINE))
                        .hasStackTraceContaining("数据源不存在或不属于当前租户");
            }
            assertThat(fixture.rule("current")).containsEntry("nifi_task_id", OLD_PIPELINE);
        }
    }

    private static Pipeline pipeline(String id) {
        return new Pipeline(id, "流程", null, null, null, new Pipeline.Dsl(1, List.of(), List.of()),
                null, null, null, null, null, null, null);
    }

    private static final class MySqlModeDataSource extends DriverManagerDataSource {
        MySqlModeDataSource() {
            super("jdbc:h2:mem:canvas-pull-" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        try {
                            if (!"getMetaData".equals(method.getName())) return method.invoke(connection, args);
                            // Select the regular MySQL pagination dialect while keeping actual H2 transactions.
                            var metadata = connection.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (ignored, operation, parameters) -> {
                                        try {
                                            return "getURL".equals(operation.getName()) ? "jdbc:mysql://localhost/validation"
                                                    : operation.invoke(metadata, parameters);
                                        } catch (InvocationTargetException error) {
                                            throw error.getCause();
                                        }
                                    });
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                    });
        }
    }

    static final class Fixture {
        final JdbcTemplate jdbc;
        final TransactionTemplate transactions;
        final PipelineRepository repository = mock(PipelineRepository.class);
        final PipelineTaskLifecycleSynchronizer lifecycle = mock(PipelineTaskLifecycleSynchronizer.class);
        final MagicAPIService magicApi = mock(MagicAPIService.class);
        final PipelineController controller;
        private final DataAssetRuntimeAdapter assets = mock(DataAssetRuntimeAdapter.class);
        private final DataSourceTransactionManager manager;
        private final Map<String, Object> modules;
        private final MagicScript script;

        Fixture(DataSource physical) throws Exception {
            var properties = new TenantDatabaseProperties();
            properties.setTenantBindings(Map.of(TENANT, "tenant-fixture", "other-tenant", "tenant-fixture"));
            var routing = new TenantRoutingDataSource(properties);
            routing.setTargetDataSources(Map.of("tenant-fixture", physical));
            routing.setDefaultTargetDataSource(physical);
            routing.afterPropertiesSet();
            jdbc = new JdbcTemplate(routing);
            manager = new DataSourceTransactionManager(routing);
            transactions = new TransactionTemplate(manager);
            modules = Map.of("tenantRuntime", new TenantMagicModule(null),
                    "tableRead", new StructuredTableReadMagicModule(jdbc),
                    "tableWrite", new StructuredTableWriteMagicModule(jdbc));
            String canonical = CanonicalMagicSources.byPath("function/01.通用函数/35.NiFi画布拉取规则绑定.ms",
                    "cbcc1df7d1d14e748af883e9ed23105f");
            MagicScript.create(canonical, null).compile();
            script = MagicScript.create(canonical.replaceAll("(?m)^import (tenantRuntime|tableRead|tableWrite)\\R", ""), null);
            when(magicApi.invoke(eq(FUNCTION), anyMap())).thenAnswer(call -> executeMagic(call.getArgument(1)));
            when(assets.queryById(CATALOG)).thenReturn(Map.of("source_table_id", TABLE));
            var connections = mock(DataSourceConnectionPropertyResolver.class);
            controller = new PipelineController(repository, null, magicApi, assets, jdbc, connections, manager, lifecycle, null);
            when(repository.save(any())).thenAnswer(call -> {
                jdbc.update("insert into canvas_probe (tid) values (?)", PIPELINE);
                return pipeline(PIPELINE);
            });
            doAnswer(call -> {
                insertTask(call.<Pipeline>getArgument(1).id());
                return null;
            }).when(lifecycle).ensureCatalogAccessTask(eq(CATALOG), any());

            jdbc.execute("create table canvas_probe (tid varchar(40) primary key)");
            jdbc.execute("""
                    create table data_access_agg_task_t (tid varchar(40) primary key, tenant_id varchar(40),
                        data_catalog_id varchar(40), pipeline_id varchar(40), is_del int, created_time timestamp(6),
                        task_name varchar(255), task_desc varchar(500), task_status int,
                        source_db_id varchar(64), source_table_id varchar(32), source_table_primary_key varchar(255),
                        source_table_increment_key varchar(255), target_db_id varchar(64), target_table_id varchar(32),
                        target_table_primary_key varchar(255), updated_time timestamp(6),
                        created_by varchar(32), updated_by varchar(32))
                    """);
            jdbc.execute("""
                    create table db_datasource_t (tid varchar(40) primary key, tenant_id varchar(40), is_del int,
                        db_name varchar(100), db_type varchar(40), pool_cfg text,
                        username varchar(100), password varchar(100), jdbc_url varchar(100))
                    """);
            jdbc.execute("""
                    create table db_table_t (tid varchar(40) primary key, tenant_id varchar(40), is_del int,
                        datasource_id varchar(40), table_name varchar(100), table_name_en varchar(100),
                        table_name_cn varchar(100), table_comment varchar(100), table_type varchar(40),
                        source_catalog_id varchar(32))
                    """);
            jdbc.execute("""
                    create table data_pull_rule_t (tid varchar(40) primary key, tenant_id varchar(40), is_del int,
                        datasource_id varchar(40), source_table_id varchar(40), nifi_task_id varchar(40),
                        updated_by varchar(40), updated_time timestamp(6), unique (tenant_id, source_table_id, is_del))
                    """);
            insertDatasource(SOURCE, TENANT, 0);
            jdbc.update("insert into db_table_t (tid, tenant_id, is_del, datasource_id) values (?, ?, 0, ?)", TABLE, TENANT, SOURCE);
            insertRule("current", TENANT, SOURCE, TABLE, 0);
        }

        private PipelineController withActualTaskAssociation() throws Exception {
            jdbc.execute("create table da_catalog_t(tid varchar(32), tenant_id varchar(40), is_del int, source_table_id varchar(32))");
            jdbc.execute("create table da_prop_t (tid varchar(32), tenant_id varchar(40), is_del int, parent_id varchar(32),"
                    + " prop_name varchar(40), prop_value varchar(100), created_time timestamp(6), updated_time timestamp(6))");
            jdbc.update("insert into da_catalog_t(tid, tenant_id, is_del) values (?, ?, 0)", CATALOG, TENANT);
            jdbc.update("insert into da_prop_t(tid, tenant_id, is_del, parent_id, prop_name, prop_value)"
                    + " values ('source-table-prop', ?, 0, ?, 'sourceTableId', ?), ('source-db-prop', ?, 0, ?, 'dbId', ?)",
                    TENANT, CATALOG, TABLE, TENANT, CATALOG, SOURCE);
            var dynamic = new MagicDynamicDataSource();
            dynamic.setDefault(jdbc.getDataSource());
            var db = new SQLModule(dynamic);
            db.setDataSourceNode(dynamic.getDataSource());
            db.setResultProvider(new DefaultResultProvider("validation"));
            db.setDialectAdapter(new DialectAdapter());
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(column -> column);
            db.setNamedTableInterceptors(List.of((mode, table) -> {
                table.column("created_by", "must-not-overwrite");
                table.column("updated_by", "must-not-overwrite");
            }));
            db.setSqlInterceptors(List.of());
            String canonical = CanonicalMagicSources.byPath("function/01.通用函数/41.NiFi画布接入任务关联.ms",
                    "fbbbebbeee9346e1813e9b1bf635eb09");
            MagicScript.create(canonical, null).compile();
            var task = MagicScript.create(canonical.replaceAll("(?m)^import (tenantRuntime|tableRead|tableWrite)\\R", ""), null);
            when(magicApi.invoke(eq(TASK_FUNCTION), anyMap())).thenAnswer(call -> {
                var bindings = new LinkedHashMap<String, Object>(modules);
                bindings.put("db", db);
                bindings.putAll(call.getArgument(1));
                return task.execute(new MagicScriptContext(bindings));
            });
            var taskLifecycle = new PipelineTaskLifecycleSynchronizer(mock(IDataAccessAggTaskTService.class), assets, magicApi);
            return new PipelineController(repository, null, magicApi, assets, jdbc,
                    mock(DataSourceConnectionPropertyResolver.class), manager, taskLifecycle, null);
        }

        private Map<?, ?> executeMagic(Map<String, Object> parameters) {
            var bindings = new LinkedHashMap<>(modules);
            bindings.putAll(parameters);
            return (Map<?, ?>) script.execute(new MagicScriptContext(bindings));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> invoke(String source, String table, String flow) {
            return transactions.execute(status -> (Map<String, Object>) executeMagic(Map.of(
                    "datasourceId", source, "sourceTableId", table, "pipelineId", flow, "tenantId", "forged")));
        }

        void insertDatasource(String datasourceId, String tenant, int deleted) {
            jdbc.update("insert into db_datasource_t (tid, tenant_id, is_del, db_type, pool_cfg) values (?, ?, ?, 'api', ?)",
                    datasourceId, tenant, deleted, CAPTURE);
        }

        void insertTask(String flow) {
            jdbc.update("""
                    insert into data_access_agg_task_t (tid, tenant_id, data_catalog_id, pipeline_id, is_del, created_time)
                    values ('task', ?, ?, ?, 0, current_timestamp)
                    """, TENANT, CATALOG, flow);
        }

        void insertRule(String id, String tenant, String datasourceId, String table, int deleted) {
            jdbc.update("""
                    insert into data_pull_rule_t (tid, tenant_id, is_del, datasource_id, source_table_id,
                        nifi_task_id, updated_by, updated_time) values (?, ?, ?, ?, ?, ?, 'original', current_timestamp)
                    """, id, tenant, deleted, datasourceId, table, OLD_PIPELINE);
        }

        Map<String, Object> rule(String id) {
            return jdbc.queryForMap("select nifi_task_id, updated_by, updated_time from data_pull_rule_t where tid=?", id);
        }

        int rowCount(String table) {
            return jdbc.queryForObject("select count(*) from " + table, Integer.class);
        }
    }
}
