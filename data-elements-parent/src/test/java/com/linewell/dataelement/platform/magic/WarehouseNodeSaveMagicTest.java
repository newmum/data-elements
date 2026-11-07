package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
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
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.LowerColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

/** Runs the deployed script against isolated dictionary, binding and source records. */
class WarehouseNodeSaveMagicTest {
    private static final String DICTIONARY_ROOT = "8961bb1c926143a89dd9d0b0e0adb4ef";
    private static final String RESOURCE_ID = "dwm_center_node_save_01";

    @Test
    void savesTwentyAndOneHundredDomainsWithTheSameNumberOfReads() throws Exception {
        for (String mode : List.of("MySQL", "PostgreSQL")) {
            var readCounts = new ArrayList<Integer>();
            for (int size : List.of(20, 100)) {
                var fixture = new Fixture(size, mode);
                fixture.saved(fixture.batch(fixture.domains()));
                readCounts.add(fixture.reads);
                assertThat(fixture.name("layer")).isEqualTo("Updated layer");
                assertThat(fixture.jdbc.queryForList(
                        """
                        SELECT dict_code FROM sym_dict_t
                         WHERE parent_id='layer' AND tenant_id='tenant-a' AND is_del=0 ORDER BY sort_no
                        """,
                        String.class)).containsExactlyElementsOf(fixture.domains().stream()
                                .map(domain -> domain.get("dictCode").toString()).toList());
                assertThat(fixture.activeBindings()).isEqualTo(size + 1);
                assertThat(fixture.jdbc.queryForObject("""
                        SELECT COUNT(*) FROM db_datasource_t
                         WHERE tenant_id='tenant-a' AND node_id='center-node'
                        """, Integer.class)).isEqualTo(size + 1);
                assertThat(fixture.bindingSource("domain-0")).isEqualTo("source-0");
                assertThat(fixture.bindingSource("layer")).isEqualTo("layer-source");
                assertThat(fixture.name("foreign-domain")).isEqualTo("Foreign domain");
                System.out.printf("Warehouse batch mode=%s, domains=%d, SQL reads=%d, SQL writes=%d%n",
                        mode, size, fixture.reads, fixture.writes);
            }
            assertThat(readCounts).containsExactly(7, 7);
        }
    }

    @Test
    void rejectsEachForeignDeletedOrUnrelatedDomainAndSourceBeforeWriting() throws Exception {
        for (String badDomain : List.of("foreign-domain", "deleted-domain", "other-layer-domain", "missing-domain")) {
            var fixture = new Fixture(2, "MySQL");
            var domains = fixture.domains();
            domains.getLast().put("targetId", badDomain);
            fixture.rejected(fixture.batch(domains));
            assertThat(fixture.writes).isZero();
            assertThat(fixture.name("layer")).isEqualTo("Original layer");
        }
        for (String badSource : List.of("foreign-source", "deleted-source", "missing-source")) {
            var fixture = new Fixture(2, "PostgreSQL");
            var domains = fixture.domains();
            domains.getLast().put("datasourceId", badSource);
            fixture.rejected(fixture.batch(domains));
            assertThat(fixture.writes).isZero();
            assertThat(fixture.activeBindings()).isZero();
            assertThat(fixture.name("domain-0")).isEqualTo("Original 0");
        }
    }

    @Test
    void refusesRepeatedIdsAndResultingSiblingCodes() throws Exception {
        var fixture = new Fixture(3, "MySQL");
        var repeatedId = fixture.domains();
        repeatedId.getLast().put("targetId", "domain-0");
        fixture.rejected(fixture.batch(repeatedId));
        assertThat(fixture.writes).isZero();

        var repeatedCode = fixture.domains();
        repeatedCode.getLast().put("dictCode", "new-0");
        fixture.rejected(fixture.batch(repeatedCode));
        assertThat(fixture.writes).isZero();

        var omittedSibling = fixture.domains();
        omittedSibling.removeLast();
        omittedSibling.getFirst().put("dictCode", "old-2");
        fixture.rejected(fixture.batch(omittedSibling));
        assertThat(fixture.writes).isZero();
    }

    @Test
    void rollsBackDictionaryAndSourceChangesWhenTheLastBindingWriteFails() throws Exception {
        var fixture = new Fixture(2, "PostgreSQL");
        fixture.jdbc.execute("""
                ALTER TABLE dwm_center_layer_source_t
                ADD CONSTRAINT validation_binding_failure CHECK (target_id <> 'domain-1')
                """);
        Object result = null;
        RuntimeException failure = null;
        try {
            result = fixture.run(fixture.batch(fixture.domains()));
        } catch (RuntimeException exception) {
            failure = exception;
        }
        assertThat(result instanceof ExitValue || failure != null).isTrue();
        assertThat(fixture.writes).isPositive();
        assertThat(fixture.activeBindings()).isZero();
        assertThat(fixture.name("layer")).isEqualTo("Original layer");
        assertThat(fixture.name("domain-0")).isEqualTo("Original 0");
        assertThat(fixture.jdbc.queryForObject(
                "SELECT node_id FROM db_datasource_t WHERE tid='source-0'", String.class)).isEqualTo("previous-node");
    }

    @Test
    void preservesLegacyLayerAndSingleDomainCallsAndAllowsRebindingAfterUnlink() throws Exception {
        var fixture = new Fixture(2, "MySQL");
        fixture.saved(Map.of("targetType", "layer", "targetId", "layer", "name", "Legacy layer",
                "layerCode", "dwd", "datasourceId", "layer-source", "applyToAllDomains", true));
        assertThat(fixture.activeBindings()).isEqualTo(3);
        assertThat(fixture.bindingSource("domain-0")).isEqualTo("layer-source");
        assertThat(fixture.name("layer")).isEqualTo("Legacy layer");

        fixture.saved(Map.of("targetType", "domain", "targetId", "domain-0", "name", "Legacy domain",
                "layerCode", "old-0", "datasourceId", ""));
        assertThat(fixture.bindingSource("domain-0")).isNull();
        assertThat(fixture.name("domain-0")).isEqualTo("Legacy domain");

        fixture.saved(Map.of("targetType", "domain", "targetId", "domain-0",
                "layerCode", "old-0", "datasourceId", "source-0"));
        assertThat(fixture.bindingSource("domain-0")).isEqualTo("source-0");
        fixture.saved(Map.of("targetType", "domain", "targetId", "domain-0", "datasourceId", ""));
        assertThat(fixture.bindingSource("domain-0")).isNull();
    }

    @Test
    void refusesMissingTenantAndOutOfTreeLayers() throws Exception {
        var fixture = new Fixture(1, "MySQL");
        var missingTenant = fixture.run(fixture.batch(fixture.domains()), "");
        assertThat(exitCode(missingTenant)).isEqualTo(401);
        assertThat(fixture.reads).isZero();
        fixture.rejected(Map.of("layerId", "unrelated-root", "name", "Invalid",
                "datasourceId", "", "domains", List.of()));
        assertThat(fixture.writes).isZero();
    }

    @Test
    void preservesBothLegacyRecordsAndRollsBackWhenUnlinkWouldConflict() throws Exception {
        var fixture = new Fixture(2, "MySQL");
        fixture.saved(fixture.batch(fixture.domains()));
        fixture.jdbc.update("""
                INSERT INTO dwm_center_layer_source_t(tid,tenant_id,target_id,target_type,is_del,datasource_id)
                VALUES('old-deleted','tenant-a','domain-1','domain',1,'source-0')
                """);
        var domains = fixture.domains();
        domains.getFirst().put("dictName", "Must roll back");
        domains.getLast().put("datasourceId", "");
        assertThat(exitCode(fixture.run(fixture.batch(domains)))).isEqualTo(409);
        assertThat(fixture.name("domain-0")).isEqualTo("Updated 0");
        assertThat(fixture.bindingSource("domain-1")).isEqualTo("source-1");
        assertThat(fixture.jdbc.queryForObject(
                "SELECT is_del FROM dwm_center_layer_source_t WHERE tid='old-deleted'", Integer.class)).isEqualTo(1);
    }

    private static int exitCode(Object result) {
        return ((Number) assertInstanceOf(ExitValue.class, result).getValues()[0]).intValue();
    }

    public record Session(String tenant) {
        public String id() { return tenant; }
        public String getLoginIdAsString() { return "validation-actor"; }
    }

    private static final class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final StructuredTableReadMagicModule tableRead;
        final MagicScript script;
        final int size;
        int reads;
        int writes;

        Fixture(int size, String mode) throws Exception {
            this.size = size;
            var source = new DriverManagerDataSource("jdbc:h2:mem:warehouse_node_" + UUID.randomUUID()
                    + ";MODE=" + mode + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "") {
                @Override public Connection getConnection() throws SQLException {
                    var actual = super.getConnection();
                    return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                            (proxy, method, arguments) -> {
                                if (!method.getName().equals("getMetaData")) {
                                    return invoke(actual, method, arguments);
                                }
                                var metadata = actual.getMetaData();
                                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                        (unused, metaMethod, metaArguments) -> metaMethod.getName().equals("getURL")
                                                ? "jdbc:" + mode.toLowerCase(Locale.ROOT) + "://warehouse-node-validation"
                                                : invoke(metadata, metaMethod, metaArguments));
                            });
                }
            };
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE sym_dict_t(
                        tid VARCHAR(64) PRIMARY KEY, tenant_id VARCHAR(32), is_del INT, dict_name VARCHAR(255),
                        dict_code VARCHAR(255), parent_id VARCHAR(64), tree_path VARCHAR(256), level_no INT, sort_no INT,
                        revision_no INT DEFAULT 1 NOT NULL, updated_by VARCHAR(64), updated_time TIMESTAMP, biz_type VARCHAR(64))
                    """);
            jdbc.execute("""
                    CREATE TABLE db_datasource_t(
                        tid VARCHAR(64) PRIMARY KEY, tenant_id VARCHAR(32), is_del INT, db_name VARCHAR(255),
                        node_id VARCHAR(128), updated_time TIMESTAMP, updated_by VARCHAR(64), is_enable INT,
                        asset_status INT, revision INT, org_id VARCHAR(32), created_by VARCHAR(32))
                    """);
            jdbc.execute("""
                    CREATE TABLE dwm_center_layer_source_t(
                        tid VARCHAR(64) PRIMARY KEY, tenant_id VARCHAR(32), target_id VARCHAR(64), target_type VARCHAR(16),
                        layer_code VARCHAR(32), datasource_id VARCHAR(64), is_del INT, created_time TIMESTAMP,
                        updated_time TIMESTAMP, configuration_json CLOB, revision_no INT DEFAULT 1 NOT NULL,
                        UNIQUE (target_id,target_type,is_del))
                    """);
            dict(DICTIONARY_ROOT, "tenant-a", "0", "Data source type", "dataSourceType", 0, 0, 0);
            dict("layer", "tenant-a", DICTIONARY_ROOT, "Original layer", "dwd", 0, 1, 1);
            dict("other-layer", "tenant-a", DICTIONARY_ROOT, "Other layer", "ods", 0, 1, 2);
            dict("unrelated-root", "tenant-a", "0", "Unrelated", "otherType", 0, 0, 0);
            dict("node-root", "tenant-a", "0", "Node type", "nodeType", 0, 0, 0);
            dict("center-node", "tenant-a", "node-root", "数据中心", "center", 0, 1, 1);
            dict("foreign-domain", "tenant-b", "layer", "Foreign domain", "foreign", 0, 2, 1);
            dict("deleted-domain", "tenant-a", "layer", "Deleted domain", "deleted", 1, 2, 1);
            dict("other-layer-domain", "tenant-a", "other-layer", "Other layer domain", "other-layer-domain", 0, 2, 1);
            for (int index = 0; index < size; index++) {
                dict("domain-" + index, "tenant-a", "layer", "Original " + index, "old-" + index, 0, 2, index + 1);
                source("source-" + index, "tenant-a", 0);
            }
            source("layer-source", "tenant-a", 0);
            source("foreign-source", "tenant-b", 0);
            source("deleted-source", "tenant-a", 1);

            var dynamic = new MagicDynamicDataSource();
            dynamic.setDefault(source);
            db = new SQLModule(dynamic);
            db.setDataSourceNode(dynamic.getDataSource());
            db.setDialectAdapter(new DialectAdapter());
            db.setResultProvider(new DefaultResultProvider("warehouse-node-save-test"));
            var mappers = new ColumnMapperAdapter();
            mappers.add(new DefaultColumnMapperProvider());
            mappers.add(new CamelColumnMapperProvider());
            mappers.add(new LowerColumnMapperProvider());
            mappers.setDefault("camel");
            db.setColumnMapperProvider(mappers);
            db.setColumnMapRowMapper(mappers.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of(new SQLInterceptor() {
                @Override public void preHandle(BoundSql bound, RequestEntity request) {
                    if (bound.getSql().stripLeading().toLowerCase(Locale.ROOT).startsWith("select")) {
                        reads++;
                    } else {
                        writes++;
                    }
                }
            }));
            tableRead = new StructuredTableReadMagicModule(new JdbcTemplate(source) {
                @Override public <T> T execute(ConnectionCallback<T> action) {
                    reads++;
                    return super.execute(action);
                }
            });
            String body = CanonicalMagicSources.byId(RESOURCE_ID)
                    .replaceAll("(?m)^import (?!cn\\.hutool\\.|java\\.)[^\\r\\n]*\\r?\\n", "");
            script = MagicScript.create(body, null);
        }

        void dict(String id, String tenant, String parent, String name, String code, int deleted, int level, int sort) {
            String path = switch (level) {
                case 0 -> "0." + id + ".";
                case 1 -> "0." + parent + "." + id + ".";
                default -> "0." + DICTIONARY_ROOT + "." + parent + "." + id + ".";
            };
            jdbc.update("""
                    INSERT INTO sym_dict_t(tid,tenant_id,parent_id,dict_name,dict_code,is_del,level_no,sort_no,tree_path)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """, id, tenant, parent, name, code, deleted, level, sort, path);
        }

        void source(String id, String tenant, int deleted) {
            jdbc.update("""
                    INSERT INTO db_datasource_t(tid,tenant_id,is_del,db_name,node_id,is_enable,asset_status,revision)
                    VALUES(?,?,?,?,'previous-node',1,2,1)
                    """, id, tenant, deleted, id);
        }

        List<Map<String, Object>> domains() {
            var domains = new ArrayList<Map<String, Object>>();
            for (int index = 0; index < size; index++) {
                domains.add(new LinkedHashMap<>(Map.of("targetId", "domain-" + index, "datasourceId", "source-" + index,
                        "dictName", "Updated " + index, "dictCode", "new-" + index, "sortNo", index + 1)));
            }
            return domains;
        }

        Map<String, Object> batch(List<Map<String, Object>> domains) {
            return Map.of("layerId", "layer", "name", "Updated layer", "datasourceId", "layer-source", "domains", domains);
        }

        Object run(Map<String, Object> body) { return run(body, "tenant-a"); }

        Object run(Map<String, Object> body, String tenant) {
            reads = 0;
            writes = 0;
            var session = new Session(tenant);
            var context = new MagicScriptContext(Map.of("body", body, "db", db, "tenantRuntime", session,
                    "StpUtil", session, "tableRead", tableRead));
            return tenant.isEmpty() ? script.execute(context) : TenantContext.call(tenant, () -> script.execute(context));
        }

        void saved(Map<String, Object> body) {
            assertThat(assertInstanceOf(Map.class, run(body)).get("success")).isEqualTo(true);
        }

        void rejected(Map<String, Object> body) {
            assertThat(exitCode(run(body))).isBetween(400, 499);
        }

        int activeBindings() {
            return jdbc.queryForObject("""
                    SELECT COUNT(*) FROM dwm_center_layer_source_t WHERE tenant_id='tenant-a' AND is_del=0
                    """, Integer.class);
        }

        String name(String id) {
            return jdbc.queryForObject("SELECT dict_name FROM sym_dict_t WHERE tid=?", String.class, id);
        }

        String bindingSource(String target) {
            return jdbc.queryForList("""
                    SELECT datasource_id FROM dwm_center_layer_source_t WHERE target_id=? AND tenant_id='tenant-a' AND is_del=0
                    """, String.class, target).stream().findFirst().orElse(null);
        }
    }

    private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
