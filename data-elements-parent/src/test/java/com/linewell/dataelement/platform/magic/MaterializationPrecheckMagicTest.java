package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.hutool.json.JSONUtil;
import com.linewell.dataelement.platform.magic.module.JsonModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

class MaterializationPrecheckMagicTest {
    private static final String COMMAND_ID = "2ebdc2a6af4b5e2989cd8add98c849fa";

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void precheckUsesOneCollisionReadAndDdlPreviewForTwentyAndOneHundredTargets(String dialect) throws IOException {
        var fixture = new Fixture(dialect);
        fixture.registration("tenant-b", "validation-target-source", "TARGET_019", 0);
        fixture.registration("tenant-a", "validation-other-source", "TARGET_019", 0);
        fixture.registration("tenant-a", "validation-target-source", "TARGET_019", 1);
        for (int size : List.of(20, 100, 101)) {
            var items = new ArrayList<Map<String, Object>>();
            for (int index = 0; index < size; index++) {
                items.add(item(String.format("target_%03d", index)));
            }
            var result = fixture.precheck(items);
            assertEquals("READY", result.get("state"), () -> String.valueOf(result.get("errors")));
            assertEquals("validated-binding", result.get("bindingHash"));
            assertEquals(List.of(), result.get("errors"));
            var savedItems = StructuredValues.objectRows(result.get("items"));
            assertEquals(size, savedItems.size());
            var expectedNames = items.stream().map(item -> item.get("targetName").toString()).toList();
            assertEquals(expectedNames, fixture.previewWindows.stream().flatMap(List::stream).toList());
            assertEquals(size > 100 ? List.of(100, 1) : List.of(size), fixture.previewWindows.stream().map(List::size).toList());
            assertEquals(expectedNames.stream().map(name -> "DDL " + name).toList(),
                    savedItems.stream().map(item -> item.get("ddl")).toList());
            assertTrue(savedItems.stream().allMatch(item -> StructuredValues.objectRows(item.get("columns")).size() == 1));
            assertEquals(size > 100 ? 2 : 1, fixture.source.collisionReads);
            assertEquals(1, fixture.session.connectionResolutions);
        }
        System.out.printf("Materialization precheck %s: 20/100 targets each 1 collision SQL and 1 DDL preview, 101 targets 2 of each%n", dialect);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void localCollisionUsesDatabaseCollationAndPreservesEarlierPreviewResults(String dialect) throws IOException {
        var fixture = new Fixture(dialect);
        // Accent-insensitive database equality must still match an ASCII target name.
        fixture.registration("tenant-a", "validation-target-source", "Résumé", 0);
        var result = fixture.precheck(List.of(item("first"), completed("finished", "original DDL"), item("resume"), item("later")));
        assertEquals("BLOCKED", result.get("state"));
        assertNull(result.get("bindingHash"));
        assertEquals(List.of("目标表已登记，不覆盖：resume"), result.get("errors"));
        assertEquals("DDL first\n\noriginal DDL", result.get("ddl"));
        assertEquals(List.of(List.of("first")), fixture.previewWindows);
        assertEquals(1, fixture.source.collisionReads);
        var savedItems = StructuredValues.objectRows(result.get("items"));
        assertEquals("original DDL", savedItems.get(1).get("ddl"));
        assertFalse(savedItems.get(2).containsKey("ddl"));
        assertFalse(savedItems.get(3).containsKey("ddl"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void previewFailurePrecedesLaterCollisionAndKeepsSucceededItemsInOrder(String dialect) throws IOException {
        var fixture = new Fixture(dialect);
        fixture.registration("tenant-a", "validation-target-source", "LATER_CONFLICT", 0);
        fixture.failedPreview = "fail_preview";
        var result = fixture.precheck(List.of(completed("initial", "initial DDL"), item("first"),
                completed("finished", "original DDL"), item("fail_preview"), item("later_conflict"),
                completed("unreached", "unreached DDL")));
        assertEquals("BLOCKED", result.get("state"));
        assertEquals(List.of("验证 DDL 生成失败"), result.get("errors"));
        assertEquals("initial DDL\n\nDDL first\n\noriginal DDL", result.get("ddl"));
        assertEquals(List.of(List.of("first", "fail_preview")), fixture.previewWindows);
        assertEquals(1, fixture.source.collisionReads);
        var savedItems = StructuredValues.objectRows(result.get("items"));
        assertFalse(savedItems.get(3).containsKey("columns"));
        assertEquals("unreached DDL", savedItems.getLast().get("ddl"));
    }

    private static Map<String, Object> item(String name) {
        return Map.of("id", name + "-item", "targetName", name, "state", "PENDING", "designEntityId", "validation-entity");
    }

    private static Map<String, Object> completed(String name, String ddl) {
        return Map.of("id", name + "-item", "targetName", name, "state", "SUCCEEDED", "ddl", ddl);
    }

    public static final class Session {
        int connectionResolutions;

        public String id() {
            return TenantContext.requireTenantId();
        }

        public Map<String, String> resolve(String tenant, String source) {
            connectionResolutions++;
            assertEquals("tenant-a", tenant);
            assertEquals("validation-target-source", source);
            return Map.of("dbType", "mysql", "database", "validation_database");
        }
    }

    private static final class Fixture {
        final CountedSource source;
        final JdbcTemplate jdbc;
        final SQLModule db;
        final StructuredTableReadMagicModule tableRead;
        final MagicScript script;
        final Session session = new Session();
        final List<List<String>> previewWindows = new ArrayList<>();
        String failedPreview = "";

        Fixture(String dialect) throws IOException {
            source = new CountedSource(dialect);
            jdbc = new JdbcTemplate(source);
            jdbc.execute("SET COLLATION ENGLISH STRENGTH PRIMARY");
            jdbc.execute("CREATE TABLE db_table_t (tenant_id VARCHAR, datasource_id VARCHAR, table_name VARCHAR, is_del INT)");
            jdbc.execute("""
                    CREATE TABLE res_materialization_plan(tid VARCHAR, tenant_id VARCHAR, plan_json TEXT, plan_status VARCHAR,
                        revision_no INT, updated_by VARCHAR, created_time TIMESTAMP, updated_time TIMESTAMP, is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE res_resource_operation_log(tid VARCHAR, tenant_id VARCHAR, actor_id VARCHAR, action_name VARCHAR,
                        object_id VARCHAR, object_name VARCHAR, detail_text VARCHAR, created_time TIMESTAMP)
                    """);
            tableRead = new StructuredTableReadMagicModule(jdbc);
            var dataSources = new MagicDynamicDataSource();
            dataSources.setDefault(source);
            db = new SQLModule(dataSources);
            db.setDataSourceNode(dataSources.getDataSource());
            db.setDialectAdapter(new DialectAdapter());
            db.setResultProvider(new DefaultResultProvider("materialization-precheck-validation"));
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.add(new LowerColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setSqlInterceptors(List.of());
            db.setNamedTableInterceptors(List.of());
            String canonical = CanonicalMagicSources.byId(COMMAND_ID)
                    .replaceAll("(?m)^import (?!java\\.|cn\\.hutool\\.)[^\\r\\n]*\\R", "")
                    .replace("StpUtil.getLoginIdAsString()", "'validation-user'")
                    .replace("unwrap(generateDDL())", "unwrap(preview(body))");
            script = MagicScript.create(canonical, null);
        }

        void registration(String tenant, String source, String name, int deleted) {
            jdbc.update("INSERT INTO db_table_t VALUES (?,?,?,?)", tenant, source, name, deleted);
        }

        Map<String, Object> precheck(List<Map<String, Object>> items) {
            jdbc.update("DELETE FROM res_materialization_plan");
            var field = Map.of("id", "validation-column", "name", "id", "logicalType", "Int32", "nullable", "false");
            var entity = Map.of("id", "validation-entity", "displayName", "Validation table", "fields", List.of(field));
            var plan = Map.of("name", "Validation plan", "bindingId", "validation-binding", "items", items,
                    "revision", Map.of("entities", List.of(entity)));
            jdbc.update("INSERT INTO res_materialization_plan VALUES ('validation-plan','tenant-a',?,'DRAFT',1,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",
                    JSONUtil.toJsonStr(plan));
            source.collisionReads = 0;
            previewWindows.clear();
            session.connectionResolutions = 0;
            Function<Object[], Object> validateModel = args -> Map.of("errors", List.of());
            Function<Object[], Object> planning = args -> Map.of(
                    "bindings", List.of(Map.of("id", "validation-binding", "databaseId", "validation-domain",
                            "sourceId", "validation-target-source", "catalog", "validation_database", "category", "RELATIONAL",
                            "state", "VALID", "checkedHash", "validated-binding", "currentHash", "validated-binding")),
                    "databases", List.of(Map.of("id", "validation-domain", "mode", "MANAGED", "state", "ACTIVE")));
            Function<Object[], Object> preview = args -> preview(StructuredValues.objectMap(args[0]));
            var variables = Map.of("db", db, "tableRead", tableRead, "tenantRuntime", session, "connectionProperties", session,
                    "jsons", new JsonModule(), "readPlanning", planning, "validateModel", validateModel, "preview", preview,
                    "input", Map.of("id", "validation-plan", "method", "POST", "action", "precheck", "version", 1),
                    "body", Map.of("validation", true));
            return StructuredValues.objectMap(TenantContext.call("tenant-a", () -> script.execute(new MagicScriptContext(variables))));
        }

        private Object preview(Map<String, Object> request) {
            assertEquals(true, request.get("canvas"));
            assertEquals(true, request.get("canvasBatchGenerate"));
            assertEquals("validation-target-source", request.get("dbId"));
            var targets = StructuredValues.objectRows(request.get("targets"));
            assertTrue(targets.size() <= 100);
            var names = targets.stream().map(target -> StructuredValues.objectMap(target.get("propList")).get("tableName").toString()).toList();
            previewWindows.add(names);
            var results = new ArrayList<Map<String, Object>>();
            for (String name : names) {
                if (name.equals(failedPreview)) {
                    return Map.of("success", true, "data", Map.of("canvasBatchGenerate", true, "results", results,
                            "error", Map.of("index", results.size(), "tableName", name, "message", "验证 DDL 生成失败")));
                }
                results.add(Map.of("tableName", name, "ddl", "DDL " + name));
            }
            return Map.of("success", true, "data", Map.of("canvasBatchGenerate", true, "results", results));
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        final String dialect;
        int collisionReads;

        CountedSource(String dialect) {
            super("jdbc:h2:mem:materialization_precheck_" + UUID.randomUUID()
                    + ";MODE=" + dialect + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            this.dialect = dialect.toLowerCase(java.util.Locale.ROOT);
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection actual = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if ("getMetaData".equals(method.getName())) {
                            var metadata = actual.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (ignored, metaMethod, metaArguments) -> "getURL".equals(metaMethod.getName())
                                            ? "jdbc:" + dialect + "://materialization-precheck-validation"
                                            : invoke(metadata, metaMethod, metaArguments));
                        }
                        if ("prepareStatement".equals(method.getName())) {
                            String sql = arguments[0].toString().toLowerCase(java.util.Locale.ROOT);
                            if (sql.stripLeading().startsWith("select") && sql.contains("db_table_t")) {
                                collisionReads++;
                            }
                        }
                        return invoke(actual, method, arguments);
                    });
        }

        private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        }
    }
}
