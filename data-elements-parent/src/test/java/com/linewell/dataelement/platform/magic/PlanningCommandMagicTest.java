package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class PlanningCommandMagicTest {
    private static final String COMMAND_ID = "fa237a4aca795e95b6a1086b2e3ed012";
    private static final String READ_ID = "4e0ee97e906054229d56e22da830272e";

    @Test
    void bindingUsesCurrentTenantAndRollsBackWhenTheSourceMarkFails() throws IOException {
        var fixture = new Fixture();
        var binding = Map.<String, Object>of("targetId", "domain-a", "sourceId", "source-a",
                "schema", "validation", "category", "RELATIONAL");
        assertStatus(404, fixture.run("bindings", "POST", "", "", null,
                Map.of("targetId", "domain-a", "sourceId", "foreign-only")));
        assertStatus(404, fixture.run("bindings", "POST", "", "", null,
                Map.of("targetId", "foreign-domain", "sourceId", "source-a")));

        fixture.jdbc.execute("ALTER TABLE db_datasource_t ADD CONSTRAINT validation_node CHECK (node_id <> 'node-center')");
        assertThrows(RuntimeException.class, () -> fixture.run("bindings", "POST", "", "", null, binding));
        assertEquals(0, fixture.bindingCount());
        assertNull(fixture.sourceNode("tenant-a"));
        fixture.jdbc.execute("ALTER TABLE db_datasource_t DROP CONSTRAINT validation_node");

        var saved = result(fixture.run("bindings", "POST", "", "", null, binding));
        String id = (String) saved.get("id");
        assertEquals(true, saved.get("saved"));
        assertEquals("node-center", fixture.sourceNode("tenant-a"));
        assertNull(fixture.sourceNode("tenant-b"));
        assertEquals("NOT_CHECKED", fixture.projectedBinding().get("state"));

        var checked = result(fixture.run("bindings", "POST", id, "check", 1, Map.of()));
        assertEquals(true, checked.get("checked"));
        assertEquals(false, checked.get("connectionTested"));
        assertEquals("VALID", fixture.projectedBinding().get("state"));
        assertStatus(409, fixture.run("bindings", "POST", id, "check", 1, Map.of()));

        var cleared = result(fixture.run("bindings", "POST", "", "", 2,
                Map.of("targetId", "domain-a", "sourceId", "")));
        assertEquals(true, cleared.get("cleared"));
        assertEquals(0, fixture.bindingCount());
        assertEquals(3, fixture.jdbc.queryForObject("SELECT revision_no FROM dwm_center_layer_source_t WHERE tid=?", Integer.class, id));
        assertEquals("node-center", fixture.sourceNode("tenant-a"));
    }

    @Test
    void siblingCodesAndNextSortAreScopedAndExcludeTheEditedRecord() throws IOException {
        var fixture = new Fixture();
        fixture.dictionary("foreign-sibling", "tenant-b", "同名", "spare", "layer-a", 99, 2);
        assertStatus(409, fixture.run("databases", "POST", "", "", null,
                Map.of("layerId", "layer-a", "name", "验证分库", "code", "eXiStInG")));
        assertStatus(404, fixture.run("databases", "POST", "", "", null,
                Map.of("layerId", "foreign-layer", "name", "验证分库", "code", "spare")));

        var saved = result(fixture.run("databases", "POST", "", "", null,
                Map.of("layerId", "layer-a", "name", "验证分库", "code", "spare")));
        String id = (String) saved.get("id");
        assertEquals(List.of(4, 1, 0), fixture.dictionaryState(id, "tenant-a"));
        assertEquals("root.layer-a." + id, fixture.dictionaryPath(id));

        var edited = result(fixture.run("databases", "PUT", id, "", 1,
                Map.of("layerId", "layer-a", "name", "新名称", "code", "SPARE")));
        assertEquals(true, edited.get("saved"));
        assertEquals(List.of(4, 2, 0), fixture.dictionaryState(id, "tenant-a"));

        fixture.dictionary("negative-sort", "tenant-a", "空分库", "other", "layer-b", -3, 2);
        var moved = result(fixture.run("databases", "PUT", id, "", 2,
                Map.of("layerId", "layer-b", "name", "新名称", "code", "SPARE")));
        assertEquals(true, moved.get("saved"));
        assertEquals(List.of(-2, 3, 0), fixture.dictionaryState(id, "tenant-a"));
        assertEquals("root.layer-b." + id, fixture.dictionaryPath(id));
    }

    @Test
    void archiveChecksLiveReferencesAndRejectsAChangeAfterPreflight() throws IOException {
        var fixture = new Fixture();
        fixture.jdbc.update("""
                INSERT INTO da_prop_t VALUES
                ('invalid','tenant-a','resourceCenterDraft','not-json',0),
                ('other-tenant','tenant-b','resourceCenterDraft','{"classificationId":"class-a"}',0),
                ('other-type','tenant-a','other','{"classificationId":"class-a"}',0),
                ('active','tenant-a','resourceCenterDraft','{"classificationId":"class-a"}',0),
                ('blank','tenant-a','resourceCenterDraft',NULL,0)
                """);
        assertStatus(409, fixture.run("classifications", "DELETE", "class-a", "", 1, Map.of()));
        fixture.jdbc.update("UPDATE da_prop_t SET is_del=1 WHERE tid='active'");
        fixture.beforeWrite = () -> fixture.concurrent.update("""
                UPDATE sym_dict_t SET revision_no=2 WHERE tenant_id='tenant-a' AND tid='class-a'
                """);
        assertStatus(409, fixture.run("classifications", "DELETE", "class-a", "", 1, Map.of()));
        assertEquals(List.of(1, 2, 0), fixture.dictionaryState("class-a", "tenant-a"));
        var archived = result(fixture.run("classifications", "DELETE", "class-a", "", 2, Map.of()));
        assertEquals(true, archived.get("removed"));
        assertEquals(List.of(1, 3, 1), fixture.dictionaryState("class-a", "tenant-a"));
        assertEquals(List.of(1, 1, 0), fixture.dictionaryState("class-a", "tenant-b"));

        fixture.jdbc.update("INSERT INTO res_logical_model VALUES ('tenant-a','domain-a',0), ('tenant-b','domain-a',0)");
        assertStatus(409, fixture.run("databases", "POST", "domain-a", "archive", 1, Map.of()));
        fixture.jdbc.update("UPDATE res_logical_model SET is_del=1 WHERE tenant_id='tenant-a'");
        assertEquals(true, result(fixture.run("databases", "POST", "domain-a", "archive", 1, Map.of())).get("removed"));
    }

    @Test
    void reorderRollsBackTheFirstUpdateIfTheNeighborVersionChanges() throws IOException {
        var fixture = new Fixture();
        fixture.beforeWrite = () -> fixture.concurrent.update("""
                UPDATE sym_dict_t SET revision_no=2 WHERE tenant_id='tenant-a' AND tid='layer-b'
                """);
        assertStatus(409, fixture.run("layers", "POST", "layer-a", "move", 1, Map.of("direction", "down")));
        assertEquals(List.of(1, 1, 0), fixture.dictionaryState("layer-a", "tenant-a"));
        assertEquals(List.of(2, 2, 0), fixture.dictionaryState("layer-b", "tenant-a"));

        assertEquals(true, result(fixture.run("layers", "POST", "layer-a", "move", 1, Map.of("direction", "down"))).get("moved"));
        assertEquals(List.of(2, 2, 0), fixture.dictionaryState("layer-a", "tenant-a"));
        assertEquals(List.of(1, 3, 0), fixture.dictionaryState("layer-b", "tenant-a"));
    }

    private static Map<String, Object> result(Object value) {
        return StructuredValues.objectMap(assertInstanceOf(Map.class, value));
    }

    private static void assertStatus(int expected, Object value) {
        var exit = assertInstanceOf(ExitValue.class, value);
        assertEquals(expected, ((Number) exit.getValues()[0]).intValue());
    }

    public static class Session {
        public String id() {
            return TenantContext.requireTenantId();
        }

        public String getLoginIdAsString() {
            return "validation-actor";
        }

        public Map<String, Object> dataSource() {
            return Map.of("scope", "ALL", "userId", getLoginIdAsString(), "organizationIds", List.of());
        }
    }

    private static final class Fixture {
        private final FixtureSource source = new FixtureSource();
        private final JdbcTemplate jdbc = new JdbcTemplate(source);
        private final JdbcTemplate concurrent = new JdbcTemplate(new DriverManagerDataSource(source.getUrl()));
        private final StructuredTableReadMagicModule tableRead = new StructuredTableReadMagicModule(jdbc);
        private final Session session = new Session();
        private final JsonModule jsons = new JsonModule();
        private final SQLModule db;
        private final MagicScript command;
        private final MagicScript read;
        private Runnable beforeWrite = () -> {};

        Fixture() throws IOException {
            jdbc.execute("""
                    CREATE TABLE sym_dict_t (tid VARCHAR, tenant_id VARCHAR, dict_name VARCHAR, dict_code VARCHAR,
                      parent_id VARCHAR, sort_no INT, dict_desc VARCHAR, planning_config_json VARCHAR, revision_no INT,
                      created_time TIMESTAMP, updated_time TIMESTAMP, is_del INT, level_no INT, tree_path VARCHAR,
                      created_by VARCHAR, updated_by VARCHAR, PRIMARY KEY (tenant_id,tid))
                    """);
            jdbc.execute("""
                    CREATE TABLE dwm_center_layer_source_t (tid VARCHAR PRIMARY KEY, target_id VARCHAR, target_type VARCHAR,
                      datasource_id VARCHAR, configuration_json VARCHAR, revision_no INT, created_time TIMESTAMP,
                      updated_time TIMESTAMP, tenant_id VARCHAR, is_del INT, layer_code VARCHAR)
                    """);
            jdbc.execute("""
                    CREATE TABLE db_datasource_t (tid VARCHAR, tenant_id VARCHAR, db_name VARCHAR, db_type VARCHAR,
                      created_by VARCHAR, org_id VARCHAR, updated_time TIMESTAMP, is_del INT, node_id VARCHAR,
                      PRIMARY KEY (tenant_id,tid))
                    """);
            jdbc.execute("CREATE TABLE db_table_t (datasource_id VARCHAR, tenant_id VARCHAR, is_del INT)");
            jdbc.execute("CREATE TABLE res_logical_model (tenant_id VARCHAR, database_id VARCHAR, is_del INT)");
            jdbc.execute("CREATE TABLE da_prop_t (tid VARCHAR, tenant_id VARCHAR, prop_name VARCHAR, prop_value VARCHAR, is_del INT)");
            DatasourceTypeFacetsMagicTest.registerJsonFunctions(jdbc);

            dictionary("root", "tenant-a", "分层根", "dataSourceType", null, 0, 0);
            dictionary("layer-a", "tenant-a", "自定义层一", "CUSTOM_A", "root", 1, 1);
            dictionary("layer-b", "tenant-a", "自定义层二", "CUSTOM_B", "root", 2, 1);
            dictionary("domain-a", "tenant-a", "验证分域", "existing", "layer-a", 3, 2);
            dictionary("foreign-layer", "tenant-b", "外租户分层", "OTHER", "root", 1, 1);
            dictionary("foreign-domain", "tenant-b", "外租户分域", "OTHER", "layer-a", 1, 2);
            dictionary("class-root", "tenant-a", "分类根", "resourceCatalogClass", null, 0, 0);
            dictionary("class-a", "tenant-a", "验证分类", "class", "class-root", 1, 1);
            dictionary("class-a", "tenant-b", "外租户分类", "class", "class-root", 1, 1);
            dictionary("node-root", "tenant-a", "节点类型", "nodeType", null, 0, 0);
            dictionary("node-center", "tenant-a", "数据中心", "center", "node-root", 1, 1);
            dictionary("a-foreign-center", "tenant-b", "数据中心", "center", "node-root", 1, 1);
            jdbc.update("""
                    INSERT INTO db_datasource_t VALUES
                    ('source-a','tenant-a','验证数据源','mysql','validation-actor','org-a',NULL,0,NULL),
                    ('source-a','tenant-b','外租户同号','mysql','validation-actor','org-a',NULL,0,NULL),
                    ('foreign-only','tenant-b','外租户数据源','mysql','validation-actor','org-a',NULL,0,NULL)
                    """);

            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setDialectAdapter(new DialectAdapter());
            db.setResultProvider(new DefaultResultProvider("planning-command-validation"));
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(new SQLInterceptor() {
                @Override
                public void preHandle(BoundSql statement, RequestEntity request) {
                    if (statement.getSql().stripLeading().regionMatches(true, 0, "UPDATE", 0, 6)) {
                        Runnable hook = beforeWrite;
                        beforeWrite = () -> {};
                        hook.run();
                    }
                }
            }));
            command = script(COMMAND_ID);
            read = script(READ_ID);
        }

        private static MagicScript script(String id) throws IOException {
            return MagicScript.create(CanonicalMagicSources.byId(id)
                    .replaceAll("(?m)^import (?!cn\\.hutool\\.)[^\\r\\n]*\\R", ""), null);
        }

        void dictionary(String id, String tenant, String name, String code, String parent, int sort, int level) {
            String path = parent == null ? id : (level == 2 ? "root." : "") + parent + "." + id;
            jdbc.update("""
                    INSERT INTO sym_dict_t (tid,tenant_id,dict_name,dict_code,parent_id,sort_no,revision_no,is_del,level_no,tree_path)
                    VALUES (?,?,?,?,?,?,1,0,?,?)
                    """, id, tenant, name, code, parent, sort, level, path);
        }

        List<Integer> dictionaryState(String id, String tenant) {
            return jdbc.queryForObject("SELECT sort_no,revision_no,is_del FROM sym_dict_t WHERE tid=? AND tenant_id=?",
                    (row, index) -> List.of(row.getInt(1), row.getInt(2), row.getInt(3)), id, tenant);
        }

        String dictionaryPath(String id) {
            return jdbc.queryForObject("SELECT tree_path FROM sym_dict_t WHERE tid=? AND tenant_id='tenant-a'", String.class, id);
        }

        String sourceNode(String tenant) {
            return jdbc.queryForObject("SELECT node_id FROM db_datasource_t WHERE tid='source-a' AND tenant_id=?", String.class, tenant);
        }

        int bindingCount() {
            return jdbc.queryForObject("SELECT COUNT(*) FROM dwm_center_layer_source_t WHERE is_del=0 AND tenant_id='tenant-a'", Integer.class);
        }

        Map<String, Object> projectedBinding() {
            return TenantContext.call("tenant-a", () -> StructuredValues.objectRows(result(runRead()).get("bindings")).getFirst());
        }

        private Object runRead() {
            return read.execute(new MagicScriptContext(Map.of("input", Map.of(), "db", db, "tenantRuntime", session,
                    "tableRead", tableRead, "jsons", jsons, "dataScope", session)));
        }

        Object run(String kind, String method, String id, String action, Integer version, Map<String, Object> payload) {
            var input = new java.util.LinkedHashMap<String, Object>(Map.of("kind", kind, "method", method, "id", id,
                    "action", action, "body", payload));
            if (version != null) {
                input.put("version", version);
            }
            Function<Object[], Object> readPlanning = args -> result(runRead());
            var vars = Map.of("input", input, "db", db, "tenantRuntime", session, "tableRead", tableRead,
                    "jsons", jsons, "StpUtil", session, "readPlanning", readPlanning);
            return TenantContext.call("tenant-a", () -> command.execute(new MagicScriptContext(vars)));
        }
    }

    private static final class FixtureSource extends DriverManagerDataSource {
        FixtureSource() {
            super("jdbc:h2:mem:planning_command_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
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
                                            ? "jdbc:mysql://planning-command-validation" : invoke(metadata, metaMethod, metaArgs));
                        }
                        return invoke(actual, method, args);
                    });
        }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        }
    }
}
