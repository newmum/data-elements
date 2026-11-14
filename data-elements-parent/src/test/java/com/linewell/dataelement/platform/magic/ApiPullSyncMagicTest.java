package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.api.DataScopeMagicModule;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.Scope;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.ScopeDecision;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class ApiPullSyncMagicTest {
    private static final String TENANT = "validation-tenant";
    private static final String SOURCE = "validation-source";
    private static final String USER = "validation-user";
    private static final String ORG = "validation-org";
    private static final String FOREIGN = "foreign-tenant";

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void createsAndRepeatsTheSameBatchWithoutResettingLiveRuleFields(int count) throws Exception {
        var fixture = new Fixture(count);
        var first = result(fixture.call(TENANT));
        assertThat(first).containsEntry("datasourceId", SOURCE).containsEntry("definitions", count)
                .containsEntry("services", count).containsEntry("rules", count);
        assertThat(entries(first)).hasSize(count);
        assertThat(entries(first).getFirst()).containsEntry("tableId", "table-0").containsEntry("ruleReady", true);
        assertThat(fixture.reads()).hasSize(4);
        assertThat(fixture.lockedTables()).containsExactly("db_datasource_t", "data_pull_service_t", "data_pull_rule_t");
        assertThat(fixture.writes()).hasSize(2).allMatch(sql -> sql.startsWith("insert"));
        assertThat(fixture.rows("data_pull_service_t")).isEqualTo(count);
        assertThat(fixture.rows("data_pull_rule_t")).isEqualTo(count);

        fixture.jdbc.update("""
                UPDATE data_pull_rule_t SET nifi_task_id='validation-flow',enabled=1,updated_by='original-editor'
                WHERE tenant_id=? AND datasource_id=? AND source_table_id='table-0'
                """, TENANT, SOURCE);
        assertThat(result(fixture.call(TENANT))).isEqualTo(first);
        assertThat(fixture.reads()).hasSize(4);
        assertThat(fixture.lockedTables()).containsExactly("db_datasource_t", "data_pull_service_t", "data_pull_rule_t");
        assertThat(fixture.writes()).hasSize(2).allMatch(sql -> sql.startsWith("update"));
        assertThat(fixture.jdbc.queryForMap("""
                SELECT nifi_task_id,enabled,updated_by,trigger_mode FROM data_pull_rule_t WHERE source_table_id='table-0'
                """)).containsEntry("nifi_task_id", "validation-flow").containsEntry("enabled", 1)
                .containsEntry("updated_by", "original-editor").containsEntry("trigger_mode", "CRON");
    }

    @Test
    void deniesForeignTenantRestrictedRoleCredentialsAndConflictingRuleBeforeWriting() throws Exception {
        var fixture = new Fixture(1);
        assertThatThrownBy(() -> fixture.call(FOREIGN)).hasMessageContaining("数据源不存在或不属于当前租户");
        assertThat(fixture.writes()).isEmpty();

        fixture.scope = Scope.OWNER;
        fixture.actor = "other-actor";
        assertExit(fixture.call(TENANT), 403);
        assertThat(fixture.writes()).isEmpty();
        fixture.scope = Scope.ORG;
        fixture.organizations = List.of("other-org");
        assertExit(fixture.call(TENANT), 403);
        assertThat(fixture.writes()).isEmpty();

        fixture.organizations = List.of(ORG);
        fixture.definitions.getFirst().put("authToken", "invalid-validation-token");
        fixture.storeDefinitions();
        assertThat(fixture.call(TENANT)).isInstanceOfSatisfying(ExitValue.class, exit -> {
            assertThat(((Number) exit.getValues()[0]).intValue()).isEqualTo(400);
            assertThat(exit.getValues()[1]).isEqualTo("API 登记请使用凭据引用");
        });
        assertThat(fixture.reads()).hasSize(1);
        assertThat(fixture.writes()).isEmpty();
        fixture.definitions.getFirst().remove("authToken");
        fixture.storeDefinitions();
        fixture.jdbc.update("""
                INSERT INTO data_pull_rule_t(tid,tenant_id,datasource_id,source_table_id,is_del)
                VALUES ('wrong-rule',?,'other-source','table-0',0)
                """, TENANT);
        assertExit(fixture.call(TENANT), 409);
        assertThat(fixture.writes()).isEmpty();
        assertThat(fixture.rows("data_pull_service_t")).isZero();
        assertThat(fixture.rows("data_pull_rule_t")).isOne();
    }

    @Test
    void suppressesWritesForEmptyDefinitionsAndCreatesServiceBeforeTableRegistration() throws Exception {
        var empty = new Fixture(0);
        assertThat(result(empty.call(TENANT))).containsEntry("definitions", 0).containsEntry("services", 0)
                .containsEntry("rules", 0).containsEntry("entries", List.of());
        assertThat(empty.reads()).hasSize(1);
        assertThat(empty.writes()).isEmpty();

        var unregistered = new Fixture(1);
        unregistered.jdbc.update("DELETE FROM db_table_t WHERE tenant_id=? AND tid='table-0'", TENANT);
        var services = result(unregistered.call(TENANT));
        assertThat(services).containsEntry("services", 1).containsEntry("rules", 0);
        assertThat(entries(services).getFirst()).containsEntry("ruleReady", false).doesNotContainKeys("tableId", "ruleId");
        assertThat(unregistered.rows("data_pull_service_t")).isOne();
        assertThat(unregistered.rows("data_pull_rule_t")).isZero();
        assertThat(result(unregistered.call(TENANT))).isEqualTo(services);
    }

    @Test
    void rollsBackServiceAndEarlierRuleWritesWhenLaterRuleIsRejected() throws Exception {
        var fixture = new Fixture(20);
        fixture.jdbc.execute("ALTER TABLE data_pull_rule_t ADD CONSTRAINT rejected_validation_endpoint CHECK (endpoint_path <> '/blocked')");
        fixture.definitions.getLast().put("endpointPath", "/blocked");
        fixture.storeDefinitions();

        assertThatThrownBy(() -> fixture.call(TENANT)).hasStackTraceContaining("rejected_validation_endpoint");
        assertThat(fixture.writes()).hasSize(2);
        assertThat(fixture.rows("data_pull_service_t")).isZero();
        assertThat(fixture.rows("data_pull_rule_t")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"service", "rule"})
    void usesCurrentRowsWhenAnIndependentRetirementCommitsBeforeLocking(String target) throws Exception {
        var fixture = new Fixture(2);
        var initial = entries(result(fixture.call(TENANT)));
        String idKey = target + "Id";
        String otherIdKey = target.equals("service") ? "ruleId" : "serviceId";
        String table = "data_pull_" + target + "_t";
        fixture.trace.beforeLock(table, () -> assertThat(fixture.independent.update(
                "UPDATE " + table + " SET is_del=1 WHERE tid=?", initial.getFirst().get(idKey))).isOne());

        var replaced = entries(result(fixture.call(TENANT)));
        assertThat(replaced.getFirst().get(idKey)).isNotEqualTo(initial.getFirst().get(idKey));
        assertThat(replaced.getFirst().get(otherIdKey)).isEqualTo(initial.getFirst().get(otherIdKey));
        assertThat(replaced.getLast()).isEqualTo(initial.getLast());
        assertThat(fixture.jdbc.queryForObject("SELECT service_id FROM data_pull_rule_t WHERE tid=?", String.class,
                replaced.getFirst().get("ruleId"))).isEqualTo(replaced.getFirst().get("serviceId"));
        assertThat(fixture.rows("data_pull_service_t")).isEqualTo(2);
        assertThat(fixture.rows("data_pull_rule_t")).isEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(Object actual) {
        assertThat(actual).isInstanceOf(Map.class);
        return (Map<String, Object>) actual;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> entries(Map<?, ?> result) {
        return (List<Map<String, Object>>) result.get("entries");
    }

    private static void assertExit(Object actual, int code) {
        assertThat(actual).isInstanceOfSatisfying(ExitValue.class, exit ->
                assertThat(((Number) exit.getValues()[0]).intValue()).isEqualTo(code));
    }

    private static final class Fixture {
        private final DriverManagerDataSource source = new DriverManagerDataSource("jdbc:h2:mem:api_pull_sync_"
                + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        final StatementTrace trace = new StatementTrace(source);
        final JdbcTemplate jdbc = new JdbcTemplate(trace);
        final JdbcTemplate independent = new JdbcTemplate(source);
        final ObjectMapper json = new ObjectMapper();
        final List<Map<String, Object>> definitions = new ArrayList<>();
        final SQLModule db;
        final MagicScript script;
        final MagicScriptLambdaFunction definitionError;
        Scope scope = Scope.ALL;
        String actor = USER;
        List<String> organizations = List.of(ORG);

        Fixture(int count) throws Exception {
            String canonical = CanonicalMagicSources.byId("ods_api_pull_sync_01");
            MagicScript.create(canonical, null).compile();
            script = MagicScript.create(canonical.replaceAll(
                    "(?m)^import (tenantRuntime|dataScope|tableRead|tableWrite|'@/common/apiPullDefinitionError' as apiPullDefinitionError)[^\\r\\n]*\\R",
                    ""), null);
            definitionError = ApiPullMagicFixture.definitionError();
            var sources = new MagicDynamicDataSource();
            sources.setDefault(trace);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setColumnMapRowMapper(new DefaultColumnMapperProvider().getColumnMapRowMapper());
            db.setRowMapColumnMapper(values -> values);
            db.setSqlInterceptors(List.of());
            db.setNamedTableInterceptors(List.of());
            createSchema();
            for (int index = 0; index < count; index++) {
                var definition = new LinkedHashMap<String, Object>(Map.of("tableName", "validation_" + index,
                        "serviceName", "服务 " + index, "tableComment", "验证数据表", "baseUrl", "https://validation.example",
                        "endpointPath", "/items", "authType", "NONE"));
                if (index == 0) {
                    definition.put("captureSchedule", Map.of("cronExpression", "0 0 0 * * ?"));
                }
                definitions.add(definition);
                addTable("table-" + index, TENANT, SOURCE, " VALIDATION_" + index + " ");
            }
            jdbc.update("""
                    INSERT INTO db_datasource_t(tid,tenant_id,db_type,org_id,created_by,is_del) VALUES(?,?,'API',?,?,0)
                    """, SOURCE, TENANT, ORG, USER);
            storeDefinitions();
            addTable("foreign-table", FOREIGN, SOURCE, "validation_0");
        }

        Object call(String tenant) {
            var dataScope = new DataScopeMagicModule(new DataScopeAuthorizationService(null, null, json) {
                @Override
                public ScopeDecision currentScope(String code) {
                    assertThat(code).isEqualTo("MENU:2081000000000000002");
                    return new ScopeDecision(code, scope, actor, organizations, List.of(), true);
                }
            });
            trace.statements.clear();
            var inputs = Map.of("body", Map.of("datasourceId", SOURCE, "tenantId", FOREIGN), "db", db,
                    "tenantRuntime", new TenantMagicModule(null), "dataScope", dataScope,
                    "tableRead", new StructuredTableReadMagicModule(jdbc), "tableWrite", new StructuredTableWriteMagicModule(jdbc),
                    "apiPullDefinitionError", definitionError);
            return TenantContext.call(tenant, () -> script.execute(new MagicScriptContext(inputs)));
        }

        List<String> reads() { return trace.statements.stream().filter(sql -> sql.startsWith("select")).toList(); }

        List<String> writes() {
            return trace.statements.stream().filter(sql -> sql.startsWith("insert") || sql.startsWith("update")).toList();
        }

        List<String> lockedTables() {
            return reads().stream().filter(sql -> sql.endsWith("for update"))
                    .map(sql -> sql.substring(sql.indexOf(" from ") + 6, sql.indexOf(" where ")))
                    .map(table -> table.replace("\"", "").replace("`", "")).toList();
        }

        int rows(String table) {
            return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND is_del=0", Integer.class, TENANT);
        }

        void storeDefinitions() throws Exception {
            jdbc.update("UPDATE db_datasource_t SET pool_cfg=? WHERE tid=?", json.writeValueAsString(
                    Map.of("accessMode", "capture", "apiPullItems", definitions)), SOURCE);
        }

        private void addTable(String id, String tenant, String datasource, String name) {
            jdbc.update("INSERT INTO db_table_t(tid,tenant_id,datasource_id,table_name,is_del) VALUES(?,?,?,?,0)",
                    id, tenant, datasource, name);
        }

        private void createSchema() {
            jdbc.execute("""
                    CREATE TABLE db_datasource_t(tid VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32),db_type VARCHAR(20),
                        pool_cfg CLOB,org_id VARCHAR(32),created_by VARCHAR(32),is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE db_table_t(tid VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32),
                        datasource_id VARCHAR(32),table_name VARCHAR(128),is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE data_pull_service_t(tid VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32),datasource_id VARCHAR(32),
                        service_code VARCHAR(96),service_name VARCHAR(128),base_url VARCHAR(1000),protocol VARCHAR(16),
                        service_type VARCHAR(32),common_headers_json CLOB,auth_type VARCHAR(48),credential_ref VARCHAR(255),
                        created_time TIMESTAMP(6),updated_time TIMESTAMP(6),is_del INT,
                        UNIQUE(tenant_id,datasource_id,service_code,is_del))
                    """);
            jdbc.execute("""
                    CREATE TABLE data_pull_rule_t(tid VARCHAR(32) PRIMARY KEY,tenant_id VARCHAR(32),datasource_id VARCHAR(32),
                        source_table_id VARCHAR(32),service_id VARCHAR(32),rule_name VARCHAR(128),endpoint_method VARCHAR(12),
                        endpoint_path VARCHAR(1000),request_template_json CLOB,response_config_json CLOB,pagination_config_json CLOB,
                        incremental_config_json CLOB,runtime_config_json CLOB,trigger_mode VARCHAR(16),cron_expression VARCHAR(128),
                        enabled INT,created_time TIMESTAMP(6),updated_time TIMESTAMP(6),is_del INT,nifi_task_id VARCHAR(64),
                        updated_by VARCHAR(32),UNIQUE(tenant_id,source_table_id,is_del))
                    """);
        }
    }

    private static final class StatementTrace extends DelegatingDataSource {
        final List<String> statements = new ArrayList<>();
        private String lockedTable;
        private Runnable beforeLock;

        StatementTrace(DriverManagerDataSource delegate) { super(delegate); }

        void beforeLock(String table, Runnable action) {
            lockedTable = table;
            beforeLock = action;
        }

        @Override
        public Connection getConnection() throws SQLException {
            var jdbc = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")) {
                            String sql = ((String) args[0]).strip().toLowerCase(Locale.ROOT);
                            statements.add(sql);
                            if (beforeLock != null && sql.endsWith("for update") && sql.contains(lockedTable)) {
                                var action = beforeLock;
                                beforeLock = null;
                                action.run();
                            }
                        }
                        try {
                            return method.invoke(jdbc, args);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }
    }
}
