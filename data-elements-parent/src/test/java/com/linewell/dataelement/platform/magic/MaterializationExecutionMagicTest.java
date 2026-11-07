package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONUtil;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.LowerColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class MaterializationExecutionMagicTest {
    @Test
    void historyAndPlanReadsAreFixedForTwentyAndOneHundredPreviouslyCreatedItems() throws Exception {
        for (int size : List.of(20, 100)) {
            var fixture = new Fixture(size);
            for (int index = 0; index < size; index++) {
                fixture.jdbc.update("""
                        INSERT INTO res_materialization_execution(tid,tenant_id,plan_id,item_id,physical_created,is_del)
                        VALUES (?,'tenant-a','plan-a',?,1,0)
                        """, "old-" + index, "item-" + index);
            }
            var result = fixture.runPlan("retry", 1);
            assertEquals("FAILED", result.get("state"));
            assertEquals(0, fixture.physicalCalls);
            assertEquals(1, fixture.source.historyReads);
            assertEquals(1, fixture.source.planReads);
            assertEquals(1, fixture.planningCalls);
            assertTrue(items(result).stream().allMatch(item -> "FAILED".equals(item.get("state"))));
        }
    }

    @Test
    void cancellationDuringFirstPhysicalOperationSettlesWithoutAdmittingTheNextTable() throws Exception {
        var fixture = new Fixture(2);
        fixture.onPhysical = () -> assertEquals("CANCELLING", fixture.runPlan("cancel", fixture.version()).get("state"));
        var result = fixture.runPlan("execute", 1);
        assertEquals("PARTIAL", result.get("state"));
        assertEquals(List.of("SUCCEEDED", "CANCELLED"), items(result).stream().map(item -> item.get("state")).toList());
        assertEquals(1, fixture.physicalCalls);
        assertEquals("PARTIAL", fixture.jdbc.queryForObject("SELECT plan_status FROM res_materialization_plan", String.class));
    }

    @Test
    void changedBindingAfterClaimStopsBeforeAnyPhysicalCall() throws Exception {
        var fixture = new Fixture(2);
        fixture.changeBindingAfterPrecheck = true;
        var result = fixture.runPlan("execute", 1);
        assertEquals("FAILED", result.get("state"));
        assertEquals(List.of("FAILED", "CANCELLED"), items(result).stream().map(item -> item.get("state")).toList());
        assertEquals(0, fixture.physicalCalls);
    }

    @Test
    void removedBindingAfterClaimDoesNotLeaveThePlanRunning() throws Exception {
        var fixture = new Fixture(2);
        fixture.removeBindingAfterPrecheck = true;
        var result = fixture.runPlan("execute", 1);
        assertEquals("FAILED", result.get("state"));
        assertEquals(0, fixture.physicalCalls);
    }

    @Test
    void staleClaimDoesNotCreateExecutionOrCallPhysicalAdapter() throws Exception {
        var fixture = new Fixture(2);
        fixture.onPlanning = () -> fixture.jdbc.update("UPDATE res_materialization_plan SET revision_no=2");
        var result = assertInstanceOf(ExitValue.class, fixture.run("execute", 1));
        assertEquals(409, ((Number) result.getValues()[0]).intValue());
        assertEquals(0, fixture.physicalCalls);
        assertEquals(0, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM res_materialization_execution", Integer.class));
    }

    @Test
    void successfulPhysicalCreateWithFailedRegistrationIsNeverRepeatedOnRetry() throws Exception {
        var fixture = new Fixture(1);
        fixture.jdbc.execute("ALTER TABLE db_table_t ADD CONSTRAINT reject_registration CHECK (table_name <> 'target_0')");
        assertEquals("FAILED", fixture.runPlan("execute", 1).get("state"));
        assertEquals(1, fixture.physicalCalls);
        assertEquals(1, fixture.jdbc.queryForObject("SELECT physical_created FROM res_materialization_execution", Integer.class));
        assertEquals("FAILED", fixture.runPlan("retry", fixture.version()).get("state"));
        assertEquals(1, fixture.physicalCalls);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<?, ?> result) {
        return (List<Map<String, Object>>) result.get("items");
    }

    public static class Session {
        public String id() { return "tenant-a"; }
        public String getLoginIdAsString() { return "actor-a"; }
    }

    public static class Connections {
        public Map<String, Object> resolve(String tenant, String source) {
            assertEquals("tenant-a", tenant);
            assertEquals("source-a", source);
            return Map.of("dbType", "mysql", "database", "warehouse");
        }
    }

    public static class Jsons {
        public String toJSONStr(Object value) { return JSONUtil.toJsonStr(value); }
    }

    private static final class Fixture {
        final CountedSource source = new CountedSource();
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final SQLModule db;
        final MagicScript script;
        int physicalCalls;
        int planningCalls;
        boolean changeBindingAfterPrecheck;
        boolean removeBindingAfterPrecheck;
        Runnable onPlanning = () -> {};
        Runnable onPhysical = () -> {};

        Fixture(int size) throws IOException {
            jdbc.execute("""
                    CREATE TABLE res_materialization_plan(tid VARCHAR(64),tenant_id VARCHAR(64),plan_json VARCHAR(2000000),
                      plan_status VARCHAR(32),revision_no INT,updated_by VARCHAR(64),updated_time TIMESTAMP,
                      created_time TIMESTAMP,is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE res_materialization_execution(tid VARCHAR(64),tenant_id VARCHAR(64),plan_id VARCHAR(64),
                      item_id VARCHAR(64),execution_status VARCHAR(32),execution_mode VARCHAR(32),ddl_text CLOB,
                      physical_created INT,started_time TIMESTAMP,finished_time TIMESTAMP,target_table_id VARCHAR(64),
                      execution_summary VARCHAR(255),failure_summary VARCHAR(255),created_by VARCHAR(64),created_time TIMESTAMP,
                      updated_by VARCHAR(64),updated_time TIMESTAMP,is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE res_resource_operation_log(tid VARCHAR(64),tenant_id VARCHAR(64),actor_id VARCHAR(64),
                      action_name VARCHAR(64),object_id VARCHAR(64),object_name VARCHAR(64),detail_text VARCHAR(255),created_time TIMESTAMP)
                    """);
            jdbc.execute("""
                    CREATE TABLE db_table_t(tid VARCHAR(64),tenant_id VARCHAR(64),datasource_id VARCHAR(64),
                      table_name VARCHAR(64),table_name_en VARCHAR(64),table_name_cn VARCHAR(64),table_comment VARCHAR(255),
                      table_type VARCHAR(32),business_type VARCHAR(64),field_count INT,record_count BIGINT,asset_desc VARCHAR(255),
                      asset_status INT,flow_status INT,resource_domain_id VARCHAR(64),resource_owner VARCHAR(64),
                      resource_state VARCHAR(32),resource_model_id VARCHAR(64),resource_plan_id VARCHAR(64),resource_revision_no INT,
                      revision INT,created_by VARCHAR(64),created_time TIMESTAMP,updated_by VARCHAR(64),updated_time TIMESTAMP,
                      is_del INT,resource_field_map CLOB)
                    """);
            jdbc.execute("""
                    CREATE TABLE db_table_column_t(tid VARCHAR(64),tenant_id VARCHAR(64),table_id VARCHAR(64),
                      column_name VARCHAR(64),column_comment VARCHAR(255),data_type VARCHAR(64),column_type VARCHAR(64),
                      length INT,precision_length INT,scale INT,nullable INT,primary_key INT,is_unique INT,indexed INT,
                      ordinal_position INT,data_standard_id VARCHAR(64),revision INT,created_by VARCHAR(64),created_time TIMESTAMP,
                      updated_by VARCHAR(64),updated_time TIMESTAMP,is_del INT)
                    """);
            var items = new ArrayList<Map<String, Object>>();
            var entities = new ArrayList<Map<String, Object>>();
            for (int index = 0; index < size; index++) {
                String entity = "entity-" + index;
                items.add(Map.of("id", "item-" + index, "targetName", "target_" + index, "designEntityId", entity, "state", "PENDING"));
                entities.add(Map.of("id", entity, "fields", List.of(Map.of("id", "field-" + index,
                        "name", "validation_field", "logicalType", "String", "length", 32))));
            }
            var plan = Map.of("id", "plan-a", "name", "Validation", "modelId", "model-a", "bindingId", "binding-a",
                    "bindingHash", "hash-a", "items", items, "revision", Map.of("entities", entities), "attempt", 0);
            jdbc.update("INSERT INTO res_materialization_plan(tid,tenant_id,plan_json,plan_status,revision_no,is_del) VALUES "
                    + "('plan-a','tenant-a',?,'READY',1,0)", JSONUtil.toJsonStr(plan));
            var dynamic = new MagicDynamicDataSource();
            dynamic.setDefault(source);
            db = new SQLModule(dynamic);
            db.setDataSourceNode(dynamic.getDataSource());
            db.setDialectAdapter(new DialectAdapter());
            db.setResultProvider(new DefaultResultProvider("materialization-execution-test"));
            var mappers = new ColumnMapperAdapter();
            mappers.add(new DefaultColumnMapperProvider());
            mappers.add(new CamelColumnMapperProvider());
            mappers.add(new LowerColumnMapperProvider());
            mappers.setDefault("camel");
            db.setColumnMapperProvider(mappers);
            db.setColumnMapRowMapper(mappers.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of());
            String body = CanonicalMagicSources.byId("2ebdc2a6af4b5e2989cd8add98c849fa")
                    .replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.)[^\\r\\n]*\\r?\\n", "");
            script = MagicScript.create("var generateDDL = () => render(body);\n"
                    + "var createPhysicalTable = () => physical(body);\n" + body, null);
        }

        int version() {
            return jdbc.queryForObject("SELECT revision_no FROM res_materialization_plan", Integer.class);
        }

        Map<?, ?> runPlan(String action, int version) {
            return assertInstanceOf(Map.class, run(action, version));
        }

        Object run(String action, int version) {
            var session = new Session();
            var variables = Map.<String, Object>ofEntries(
                    Map.entry("db", db), Map.entry("tableRead", new StructuredTableReadMagicModule(jdbc)),
                    Map.entry("input", Map.of("method", "PUT", "id", "plan-a", "action", action, "version", version,
                            "body", Map.of("confirmExecution", true))), Map.entry("body", Map.of()),
                    Map.entry("tenantRuntime", session), Map.entry("StpUtil", session), Map.entry("jsons", new Jsons()),
                    Map.entry("connectionProperties", new Connections()),
                    Map.entry("readPlanning", (Function<Object[], Object>) args -> {
                        planningCalls++;
                        onPlanning.run();
                        if (removeBindingAfterPrecheck && planningCalls > 1) {
                            return Map.of("bindings", List.of(), "databases", List.of());
                        }
                        String hash = changeBindingAfterPrecheck && planningCalls > 1 ? "changed" : "hash-a";
                        return Map.of("bindings", List.of(Map.of("id", "binding-a", "databaseId", "domain-a", "state", "VALID",
                                        "currentHash", hash, "checkedHash", hash, "sourceId", "source-a", "category", "RELATIONAL")),
                                "databases", List.of(Map.of("id", "domain-a", "state", "ACTIVE", "mode", "MANAGED")));
                    }),
                    Map.entry("validateModel", (Function<Object[], Object>) args -> Map.of("valid", true, "errors", List.of())),
                    Map.entry("render", (Function<Object[], Object>) args -> {
                        var request = assertInstanceOf(Map.class, args[0]);
                        var targets = assertInstanceOf(List.class, request.get("targets"));
                        var rendered = new ArrayList<Map<String, Object>>();
                        for (Object target : targets) {
                            var props = assertInstanceOf(Map.class, assertInstanceOf(Map.class, target).get("propList"));
                            rendered.add(Map.of("tableName", props.get("tableName"), "ddl", "CREATE TABLE validation (id INT)"));
                        }
                        return Map.of("canvasBatchGenerate", true, "results", rendered);
                    }),
                    Map.entry("physical", (Function<Object[], Object>) args -> {
                        physicalCalls++;
                        Runnable hook = onPhysical;
                        onPhysical = () -> {};
                        hook.run();
                        return Map.of("created", true, "status", "created");
                    }));
            return TenantContext.call("tenant-a", () -> script.execute(new MagicScriptContext(variables)));
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        int historyReads;
        int planReads;

        CountedSource() {
            super("jdbc:h2:mem:materialization_execution_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getMetaData")) {
                            DatabaseMetaData metadata = connection.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (p, metaMethod, metaArgs) -> metaMethod.getName().equals("getURL")
                                            ? "jdbc:mysql://validation" : invoke(metadata, metaMethod, metaArgs));
                        }
                        if (method.getName().equals("prepareStatement")) {
                            String sql = String.valueOf(args[0]).stripLeading().toLowerCase(Locale.ROOT);
                            if (sql.startsWith("select")) {
                                if (sql.contains("res_materialization_execution")) historyReads++;
                                if (sql.contains("res_materialization_plan")) planReads++;
                            }
                        }
                        return invoke(connection, method, args);
                    });
        }

        private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }
}
