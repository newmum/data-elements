package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.api.DataScopeMagicModule;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.Scope;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.ScopeDecision;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.dialect.MySQLDialect;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class ApiPullTaskBindingMagicTest {
    private static final Timestamp ORIGINAL_TIME = Timestamp.valueOf("2000-01-01 00:00:00");
    private static final String RESOURCE_ID = "ods_api_pull_bind_task_01";

    @Test
    void bindsOneRuleUsingDatabaseClockAndRetainsExistingResultOnRepeat() throws Exception {
        var fixture = new Fixture();
        LocalDateTime before = fixture.databaseTime();

        var first = result(fixture.call("ga", "source", "table", "pipeline"));

        assertThat(first).containsOnlyKeys("applicable", "pipelineId", "boundRules", "message")
                .containsEntry("applicable", true)
                .containsEntry("pipelineId", "pipeline")
                .containsEntry("boundRules", 1);
        assertThat(first.get("message")).isEqualTo("表级拉取规则已绑定 NiFi 任务；可在画布中部署、手动启动或按 Cron 调度");
        var saved = fixture.rule("rule");
        assertThat(saved).containsEntry("nifi_task_id", "pipeline").containsEntry("updated_by", "system");
        assertThat(fixture.updatedTime()).isBetween(before, fixture.databaseTime());

        assertThat(result(fixture.call("ga", "source", "table", "pipeline"))).isEqualTo(first);
        assertThat(fixture.rule("rule").get("nifi_task_id")).isEqualTo("pipeline");
        fixture.assertOtherRulesUntouched();
    }

    @Test
    void skipsNonApiSourcesAndReportsMissingRuleWithoutWriting() throws Exception {
        var fixture = new Fixture();
        var original = fixture.rule("rule");
        var skipped = result(fixture.call("ga", "database", "table", "pipeline"));
        assertThat(skipped).containsOnlyKeys("applicable", "message").containsEntry("applicable", false);
        assertThat(skipped.get("message")).isEqualTo("当前来源不是 API 数据拉取，未绑定表规则");

        var missing = result(fixture.call("ga", "source", "missing-rule-table", "missing-rule-pipeline"));
        assertThat(missing).containsOnlyKeys("applicable", "pipelineId", "boundRules", "message")
                .containsEntry("applicable", true)
                .containsEntry("pipelineId", "missing-rule-pipeline")
                .containsEntry("boundRules", 0);
        assertThat(missing.get("message")).isEqualTo("当前表尚未同步出 API 拉取规则，请返回第二步保存表标注后重试");
        assertThat(fixture.rule("rule")).isEqualTo(original);
        fixture.assertOtherRulesUntouched();
    }

    @Test
    void rejectsForeignOrUnrelatedSourcesTablesPipelinesAndTasksWithoutWriting() throws Exception {
        var fixture = new Fixture();
        var original = fixture.rule("rule");

        assertRejected(fixture.call("ga", "foreign-source", "foreign-table", "foreign-pipeline"));
        assertRejected(fixture.call("other", "source", "table", "pipeline"));
        assertRejected(fixture.call("ga", "source", "other-table", "pipeline"));
        assertRejected(fixture.call("ga", "source", "foreign-table", "pipeline"));
        assertRejected(fixture.call("ga", "source", "table", "foreign-pipeline"));
        assertRejected(fixture.call("ga", "source", "table", "unrelated-pipeline"));
        assertRejected(fixture.call("ga", "source", "table", "deleted-pipeline"));
        assertRejected(fixture.call("ga", "source", "table", "deleted-task-pipeline"));
        assertRejected(fixture.call("ga", "source", "table", "missing-pipeline"));
        assertRejected(fixture.call("ga", "source", "table", "pipeline' OR '1'='1"));

        assertThat(fixture.rule("rule")).isEqualTo(original);
        fixture.assertOtherRulesUntouched();
    }

    @Test
    void rejectsMissingIdentifiersBeforeWritingAndIgnoresForgedTenantInput() throws Exception {
        var fixture = new Fixture();
        var original = fixture.rule("rule");

        assertRejected(fixture.call("ga", "", "table", "pipeline"));
        assertRejected(fixture.call("ga", "source", " ", "pipeline"));
        assertRejected(fixture.call("ga", "source", "table", " "));
        assertThat(fixture.rule("rule")).isEqualTo(original);

        assertThat(result(fixture.call("ga", "source", "table", "pipeline")))
                .containsEntry("boundRules", 1);
        fixture.assertOtherRulesUntouched();
    }

    @Test
    void enforcesSourceScopeBeforeUpdatingAnApiRule() throws Exception {
        var fixture = new Fixture();
        var original = fixture.rule("rule");
        fixture.scope = Scope.OWNER;
        fixture.actor = "other-user";
        assertRejected(fixture.call("ga", "source", "table", "pipeline"));
        fixture.scope = Scope.ORG;
        fixture.organizations = List.of("other-org");
        assertRejected(fixture.call("ga", "source", "table", "pipeline"));
        assertThat(fixture.rule("rule")).isEqualTo(original);

        fixture.organizations = List.of("org");
        assertThat(result(fixture.call("ga", "source", "table", "pipeline"))).containsEntry("boundRules", 1);
        fixture.scope = Scope.OWNER;
        fixture.actor = "owner";
        assertThat(result(fixture.call("ga", "source", "table", "pipeline"))).containsEntry("boundRules", 1);
        fixture.assertOtherRulesUntouched();
    }

    @Test
    void downstreamRequestBindsTheProvenUpstreamPipelineAndRejectsForgedSinkProvenance() throws Exception {
        var fixture = new Fixture();
        fixture.addDownstream();

        var binding = result(fixture.call("ga", "source", "table", "downstream"));
        assertThat(binding).containsEntry("pipelineId", "pipeline").containsEntry("boundRules", 1);
        assertThat(fixture.rule("rule")).containsEntry("nifi_task_id", "pipeline");

        var before = fixture.rule("rule");
        fixture.jdbc.update("UPDATE nifi_pipeline_t SET dsl_json=? WHERE id='downstream'", """
                {"version":1,"nodes":[{"id":"sink","category":"sink","manifestKey":"sink.jdbc",
                  "config":{"accessChain":{"rootSourceTableId":"other-table","sourceTableId":"first-target",
                      "targetTableId":"second-target","targetIndex":1}}}],
                  "accessChain":{"rootSourceTableId":"table","targetTableIds":["first-target","second-target"]}}
                """);
        assertRejected(fixture.call("ga", "source", "table", "downstream"));
        assertThat(fixture.rule("rule")).isEqualTo(before);

        fixture.addDownstreamDsl();
        fixture.jdbc.update("UPDATE data_access_agg_task_t SET is_del=1 WHERE tid='task'");
        assertRejected(fixture.call("ga", "source", "table", "downstream"));
        assertThat(fixture.rule("rule")).isEqualTo(before);
        fixture.assertOtherRulesUntouched();
    }

    @Test
    void resolvesTwentyAndHundredHopChainsWithTheSameFiveStructuredReads() throws Exception {
        for (int count : List.of(20, 100)) {
            var fixture = new Fixture();
            var lastPipeline = fixture.addChain(count);

            assertThat(result(fixture.call("ga", "source", "table", lastPipeline)))
                    .containsEntry("pipelineId", "pipeline").containsEntry("boundRules", 1);
            assertThat(fixture.rule("rule")).containsEntry("nifi_task_id", "pipeline");
            assertThat(fixture.structuredQueries).hasSize(5);
            assertThat(fixture.structuredQueries.getLast()).contains(" IN (");
            fixture.assertOtherRulesUntouched();
            System.out.printf("API pull tasks: %d hops, 5 structured SELECTs including batched topology, first hop bound%n", count);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(Object output) {
        assertThat(output).isInstanceOf(Map.class);
        return (Map<String, Object>) output;
    }

    private static void assertRejected(Object value) {
        assertThat(value).isInstanceOfSatisfying(ExitValue.class, exit -> {
            assertThat(((Number) exit.getValues()[0]).intValue()).isBetween(400, 499);
        });
    }

    static final class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final StructuredTableReadMagicModule tableRead;
        final StructuredTableWriteMagicModule tableWrite;
        final DataScopeMagicModule dataScope;
        final List<String> structuredQueries = new ArrayList<>();
        Scope scope = Scope.ALL;
        String actor = "owner";
        List<String> organizations = List.of("org");

        Fixture() {
            var source = new DriverManagerDataSource("jdbc:h2:mem:api_pull_bind_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
            jdbc = new JdbcTemplate(source);
            createSchema(jdbc);
            seed();
            tableRead = TaskRuntimeMagicFixture.structuredReads(jdbc, structuredQueries::add);
            tableWrite = new StructuredTableWriteMagicModule(jdbc);
            dataScope = new DataScopeMagicModule(new DataScopeAuthorizationService(null, null, new ObjectMapper()) {
                @Override
                public ScopeDecision currentScope(String resourceCode) {
                    return new ScopeDecision(resourceCode, scope, actor, organizations, List.of(), true);
                }
            });
            var dynamic = new MagicDynamicDataSource();
            dynamic.setDefault(source);
            db = new SQLModule(dynamic);
            db.setDataSourceNode(dynamic.getDataSource());
            db.setResultProvider(new DefaultResultProvider("validation"));
            var dialects = new DialectAdapter();
            dialects.add(new MySQLDialect() { @Override public boolean match(String url) { return url.contains(":h2:"); } });
            db.setDialectAdapter(dialects);
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of());
        }

        Object call(String tenant, String source, String table, String pipeline) throws Exception {
            String canonical = CanonicalMagicSources.byId(RESOURCE_ID);
            return execute(canonical, tenant, source, table, pipeline, Map.of());
        }

        Object execute(String canonical, String tenant, String source, String table, String pipeline,
                Map<String, Object> overrides) {
            MagicScript.create(canonical, null).compile();
            String script = canonical.replaceAll("(?m)^import (tenantRuntime|tableRead|tableWrite|dataScope|jsons)[^\\r\\n]*\\R", "");
            var body = Map.of("datasourceId", source, "sourceTableId", table, "pipelineId", pipeline, "tenantId", "forged");
            structuredQueries.clear();
            var bindings = new LinkedHashMap<String, Object>(Map.of("body", body, "db", db,
                    "tenantRuntime", new TenantMagicModule(null), "tableRead", tableRead,
                    "tableWrite", tableWrite, "dataScope", dataScope));
            bindings.putAll(overrides);
            return TenantContext.call(tenant, () -> MagicScript.create(script, null).execute(new MagicScriptContext(
                    bindings)));
        }

        Map<String, Object> rule(String id) {
            return jdbc.queryForObject("SELECT nifi_task_id,updated_by,updated_time FROM data_pull_rule_t WHERE tid=?",
                    (row, index) -> Map.of("nifi_task_id", row.getString(1), "updated_by", row.getString(2),
                            "updated_time", row.getTimestamp(3)), id);
        }

        void assertOtherRulesUntouched() {
            for (String id : List.of("other-rule", "foreign-rule", "retired-rule")) {
                assertThat(rule(id)).containsEntry("nifi_task_id", "old-pipeline")
                        .containsEntry("updated_by", "original-editor")
                        .containsEntry("updated_time", ORIGINAL_TIME);
            }
        }

        LocalDateTime databaseTime() {
            return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)", (row, index) -> row.getTimestamp(1).toLocalDateTime());
        }

        LocalDateTime updatedTime() {
            return ((Timestamp) rule("rule").get("updated_time")).toLocalDateTime();
        }

        private static void createSchema(JdbcTemplate jdbc) {
            jdbc.execute("""
                    CREATE TABLE db_datasource_t(tid VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32),
                        db_type VARCHAR(20),pool_cfg VARCHAR(2000),org_id VARCHAR(32),created_by VARCHAR(32),is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE db_table_t(tid VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32),
                        datasource_id VARCHAR(32),table_name VARCHAR(100),source_table_id VARCHAR(32),is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE nifi_pipeline_t(id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(32),
                        status VARCHAR(32),dsl_json CLOB,is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE data_access_agg_task_t(tid VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32),
                        source_db_id VARCHAR(64),source_table_id VARCHAR(32),target_db_id VARCHAR(64),target_table_id VARCHAR(32),
                        task_type_id VARCHAR(32),pipeline_id VARCHAR(64),is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE data_pull_rule_t(tid VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32),
                        datasource_id VARCHAR(32),source_table_id VARCHAR(32),service_id VARCHAR(32),
                        rule_name VARCHAR(128),endpoint_path VARCHAR(1000),created_time TIMESTAMP(6),
                        nifi_task_id VARCHAR(64),updated_by VARCHAR(32),updated_time TIMESTAMP(6),is_del INT,
                        UNIQUE (tenant_id,source_table_id,is_del))
                    """);
        }

        private void seed() {
            jdbc.update("""
                    INSERT INTO db_datasource_t(tid,tenant_id,db_type,pool_cfg,org_id,created_by,is_del)
                    VALUES ('source','ga','api','{"accessMode":"capture"}','org','owner',0),
                           ('other-source','ga','api','{"accessMode":"pull"}','org','owner',0),
                           ('database','ga','mysql','{"accessMode":"capture"}','org','owner',0),
                           ('foreign-source','other','api','{"accessMode":"pull"}','org','owner',0)
                    """);
            jdbc.update("""
                    INSERT INTO db_table_t(tid,tenant_id,datasource_id,table_name,is_del)
                    VALUES ('table','ga','source','validation_table',0),
                           ('other-table','ga','other-source','other_table',0),
                           ('foreign-table','other','foreign-source','foreign_table',0),
                           ('missing-rule-table','ga','source','unbound_table',0)
                    """);
            jdbc.update("""
                    INSERT INTO db_table_t(tid,tenant_id,datasource_id,table_name,source_table_id,is_del)
                    VALUES ('first-target','ga','database','first_target','table',0),
                           ('missing-rule-target','ga','database','missing_rule_target','missing-rule-table',0)
                    """);
            jdbc.update("""
                    INSERT INTO nifi_pipeline_t(id,tenant_id,status,is_del)
                    VALUES ('pipeline','ga','CREATED',0),('unrelated-pipeline','ga','CREATED',0),
                           ('foreign-pipeline','other','CREATED',0),('deleted-pipeline','ga','CREATED',1),
                           ('deleted-task-pipeline','ga','CREATED',0),('missing-rule-pipeline','ga','CREATED',0)
                    """);
            jdbc.update("""
                    INSERT INTO data_access_agg_task_t(tid,tenant_id,source_db_id,source_table_id,target_db_id,
                        target_table_id,task_type_id,pipeline_id,is_del)
                    VALUES ('task','ga','source','table','database','first-target','table_access','pipeline',0),
                           ('unrelated-task','ga','other-source','other-table','database','first-target','table_access','unrelated-pipeline',0),
                           ('foreign-task','other','foreign-source','foreign-table','database','first-target','table_access','foreign-pipeline',0),
                           ('deleted-pipeline-task','ga','source','table','database','first-target','table_access','deleted-pipeline',0),
                           ('deleted-task','ga','source','table','database','first-target','table_access','deleted-task-pipeline',1),
                           ('missing-rule-task','ga','source','missing-rule-table','database','missing-rule-target','table_access','missing-rule-pipeline',0)
                    """);
            addRule("rule", "ga", "source", "table", 0);
            addRule("other-rule", "ga", "other-source", "other-table", 0);
            addRule("foreign-rule", "other", "foreign-source", "foreign-table", 0);
            addRule("retired-rule", "ga", "source", "table", 1);
        }

        void addDownstream() {
            jdbc.update("""
                    INSERT INTO db_table_t(tid,tenant_id,datasource_id,table_name,source_table_id,is_del)
                    VALUES ('second-target','ga','database','second_target','table',0)
                    """);
            jdbc.update("INSERT INTO nifi_pipeline_t(id,tenant_id,status,is_del) VALUES ('downstream','ga','SAVED',0)");
            jdbc.update("""
                    INSERT INTO data_access_agg_task_t(tid,tenant_id,source_db_id,source_table_id,target_db_id,
                        target_table_id,task_type_id,pipeline_id,is_del)
                    VALUES ('downstream-task','ga','database','first-target','database','second-target','table_access','downstream',0)
                    """);
            addDownstreamDsl();
        }

        void addDownstreamDsl() {
            jdbc.update("UPDATE nifi_pipeline_t SET dsl_json=? WHERE id='downstream'", """
                    {"version":1,"nodes":[{"id":"sink","category":"sink","manifestKey":"sink.jdbc",
                      "config":{"accessChain":{"rootSourceTableId":"table","sourceTableId":"first-target",
                          "targetTableId":"second-target","targetIndex":1}}}],
                      "accessChain":{"rootSourceTableId":"table","targetTableIds":["first-target","second-target"]}}
                    """);
        }

        String addChain(int count) throws Exception {
            var targets = new ArrayList<String>();
            targets.add("first-target");
            String lastSource = "table";
            String lastPipeline = "pipeline";
            for (int position = 1; position < count; position++) {
                lastSource = targets.getLast();
                String next = "chain-target-" + position;
                lastPipeline = "chain-pipeline-" + position;
                jdbc.update("""
                        INSERT INTO db_table_t(tid,tenant_id,datasource_id,table_name,source_table_id,is_del)
                        VALUES (?,'ga','database',?,'table',0)
                        """, next, next);
                jdbc.update("INSERT INTO nifi_pipeline_t(id,tenant_id,status,is_del) VALUES (?,'ga','SAVED',0)", lastPipeline);
                jdbc.update("""
                        INSERT INTO data_access_agg_task_t(tid,tenant_id,source_db_id,source_table_id,target_db_id,
                            target_table_id,task_type_id,pipeline_id,is_del)
                        VALUES (?,'ga','database',?,'database',?,'table_access',?,0)
                        """, "chain-task-" + position, lastSource, next, lastPipeline);
                targets.add(next);
            }
            var link = Map.of("rootSourceTableId", "table", "sourceTableId", lastSource,
                    "targetTableId", targets.getLast(), "targetIndex", count - 1);
            var sink = Map.of("id", "sink", "category", "sink", "manifestKey", "sink.jdbc", "config", Map.of("accessChain", link));
            var design = Map.of("nodes", List.of(sink), "accessChain", Map.of("rootSourceTableId", "table", "targetTableIds", targets));
            jdbc.update("UPDATE nifi_pipeline_t SET dsl_json=? WHERE id=?", new ObjectMapper().writeValueAsString(design), lastPipeline);
            return lastPipeline;
        }

        private void addRule(String id, String tenant, String source, String table, int deleted) {
            jdbc.update("""
                    INSERT INTO data_pull_rule_t(tid,tenant_id,datasource_id,source_table_id,service_id,rule_name,
                        endpoint_path,created_time,nifi_task_id,updated_by,updated_time,is_del)
                    VALUES (?,?,?,?,'validation-service','Validation rule','/validation',?,'old-pipeline','original-editor',?,?)
                    """, id, tenant, source, table, ORIGINAL_TIME, ORIGINAL_TIME, deleted);
        }
    }
}
