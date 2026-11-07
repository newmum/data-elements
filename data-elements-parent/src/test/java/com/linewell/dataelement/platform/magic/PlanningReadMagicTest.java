package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
import org.junit.jupiter.api.Test;
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
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class PlanningReadMagicTest {
    private static final String RESOURCE_ID = "4e0ee97e906054229d56e22da830272e";
    private static final String GATEWAY_ID = "b6aaff9e31925977b94369d347e7c32a";
    private static final Scope ALL = new Scope("ALL", "actor-a", List.of());
    private static final Scope OWNER = new Scope("OWNER", "actor-a", List.of());
    private static final Scope ORG = new Scope("ORG", "actor-a", List.of("org-a"));

    @Test
    void fixedReadCountAtTwentyAndOneHundredBindings() throws IOException {
        var fixture = new Fixture();
        fixture.dictionary("layer", "tenant-a", "分层", "ODS", "root", 1, 0);
        fixture.jdbc.update("INSERT INTO db_datasource_t VALUES ('source','tenant-a','验证源','mysql','actor-a','org-a',NULL,0)");
        fixture.jdbc.update("INSERT INTO db_table_t VALUES ('source','tenant-a',0),('source','tenant-b',0),('source','tenant-a',1)");

        int previousSize = 0;
        int reads = 0;
        for (int size : List.of(20, 100)) {
            for (int index = previousSize; index < size; index++) {
                String domain = "domain-" + index;
                fixture.dictionary(domain, "tenant-a", domain, "domain", "layer", index, 0);
                fixture.binding("binding-" + index, domain, "source", "tenant-a", 0);
            }
            var result = StructuredValues.objectMap(fixture.run(ALL));
            assertEquals(size, rows(result, "databases").size());
            assertEquals(size, rows(result, "bindings").size());
            assertEquals(1L, rows(result, "bindings").getFirst().get("tableCount"));
            if (previousSize == 0) reads = fixture.source.reads;
            assertEquals(reads, fixture.source.reads);
            assertEquals(12, reads);
            previousSize = size;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void gatewayAggregatesOnlyAuthorizedDomainsBeforePaging(String dialect) throws IOException {
        var fixture = new Fixture(dialect);
        fixture.jdbc.execute("ALTER TABLE db_table_t ADD resource_domain_id VARCHAR");
        fixture.dictionary("layer", "tenant-a", "分层", "ODS", "root", 1, 0);
        fixture.jdbc.update("""
                INSERT INTO db_datasource_t VALUES
                ('validation-owner','tenant-a','本人数据源','mysql','actor-a','org-b',NULL,0),
                ('validation-org','tenant-a','部门数据源','postgresql','actor-b','org-a',NULL,0),
                ('validation-deleted-source','tenant-a','删除源','mysql','actor-a','org-a',NULL,1),
                ('validation-foreign-source','tenant-b','外部源','mysql','actor-a','org-a',NULL,0)
                """);
        fixture.jdbc.update("""
                INSERT INTO db_table_t VALUES
                ('validation-owner','tenant-b',0,'validation-domain-000'),
                ('validation-owner','tenant-a',1,'validation-domain-000'),
                ('validation-deleted-source','tenant-a',0,'validation-domain-000'),
                ('validation-foreign-source','tenant-a',0,'validation-domain-000')
                """);

        int previous = 0;
        var ids = new ArrayList<String>();
        for (int size : List.of(20, 100)) {
            for (int index = previous; index < size; index++) {
                String id = String.format("validation-domain-%03d", index);
                ids.add(id);
                fixture.dictionary(id, "tenant-a", id, id, "layer", index, 0);
                fixture.binding("validation-binding-" + index, id, index % 2 == 0 ? "validation-owner" : "validation-org", "tenant-a", 0);
                fixture.jdbc.update("INSERT INTO db_table_t VALUES('validation-owner','tenant-a',0,?),('validation-org','tenant-a',0,?)", id, id);
                if (index % 2 == 1) {
                    fixture.jdbc.update("INSERT INTO db_table_t VALUES('validation-org','tenant-a',0,?)", id);
                }
            }
            for (Scope scope : List.of(ALL, OWNER, ORG)) {
                var result = StructuredValues.objectMap(fixture.gateway(scope, Map.of("table", "databases", "ids", ids,
                        "size", size, "sortField", "count", "sortDir", "desc")));
                assertEquals(size, ((Number) result.get("total")).intValue());
                assertEquals(size, ((List<?>) result.get("keys")).size());
                assertEquals(scope == OWNER ? "validation-domain-000" : "validation-domain-001", ((List<?>) result.get("keys")).getFirst());
                var statistics = StructuredValues.objectMap(result.get("statistics"));
                for (int index = 0; index < size; index++) {
                    var stats = StructuredValues.objectMap(statistics.get(ids.get(index)));
                    int expected = (scope == ALL ? 2 : 1) + (scope != OWNER && index % 2 == 1 ? 1 : 0);
                    assertEquals(expected, ((Number) stats.get("count")).intValue());
                }
                assertEquals(13, fixture.source.reads);
            }
            previous = size;
        }

        var first = StructuredValues.objectMap(fixture.gateway(ALL, Map.of("table", "databases", "ids", ids,
                "size", 20, "sortField", "count", "sortDir", "desc")));
        assertEquals("validation-domain-039", ((List<?>) first.get("keys")).getLast());
        assertEquals(13, fixture.source.reads);
        var organizationWithoutMembership = StructuredValues.objectMap(fixture.gateway(new Scope("ORG", "actor-a", List.of()),
                Map.of("table", "databases", "ids", List.of(ids.getFirst()))));
        assertEquals(0, StructuredValues.objectMap(StructuredValues.objectMap(organizationWithoutMembership.get("statistics")).get(ids.getFirst())).get("count"));
        assertEquals(12, fixture.source.reads);

        var layers = StructuredValues.objectMap(fixture.gateway(ALL, Map.of("table", "layers", "ids", List.of("layer"))));
        assertEquals(100, StructuredValues.objectMap(StructuredValues.objectMap(layers.get("statistics")).get("layer")).get("count"));
        assertEquals(12, fixture.source.reads);
        var bindings = StructuredValues.objectMap(fixture.gateway(ALL, Map.of("table", "bindings", "ids", List.of("layer:layer", "domain:" + ids.getFirst()))));
        assertEquals(2, ((Number) bindings.get("total")).intValue());
        assertEquals(12, fixture.source.reads);
        var noMatch = StructuredValues.objectMap(fixture.gateway(ALL, Map.of("table", "databases", "ids", ids, "keyword", "validation-no-match")));
        assertEquals(0, ((Number) noMatch.get("total")).intValue());
        assertEquals(12, fixture.source.reads);

        fixture.dictionary("validation-foreign-domain", "tenant-b", "外部", "foreign", "layer", 0, 0);
        fixture.dictionary("validation-deleted-domain", "tenant-a", "删除", "deleted", "layer", 0, 1);
        for (String unauthorized : List.of("validation-foreign-domain", "validation-deleted-domain", "validation-missing-domain")) {
            assertStatus(404, fixture.gateway(ALL, Map.of("table", "databases", "ids", List.of(ids.getFirst(), unauthorized))));
            assertEquals(12, fixture.source.reads);
        }
        System.out.printf("Planning gateway %s: ALL/ORG/OWNER 20/100 domains each 13 SQL, layers/bindings 12 SQL%n", dialect);
    }

    @Test
    void preservesParentAndBindingOrderAndMasksSourcesByTenantAndScope() throws IOException {
        var fixture = new Fixture();
        fixture.dictionary("null-layer", "tenant-a", "零", "DST", "root", null, 0);
        fixture.dictionary("first-layer", "tenant-a", "A", "ODS", "root", 1, 0);
        fixture.dictionary("second-layer", "tenant-a", "B", "DWD", "root", 1, 0);
        fixture.dictionary("gone-layer", "tenant-a", "删除", "unused", "root", 2, 1);
        fixture.dictionary("foreign-layer", "tenant-b", "外部", "unused", "root", 2, 0);
        fixture.dictionary("null-domain", "tenant-a", "Z", "null-domain", "null-layer", 99, 0);
        fixture.dictionary("later-domain", "tenant-a", "B", "later-domain", "first-layer", 9, 0);
        fixture.dictionary("first-domain", "tenant-a", "A", "first-domain", "second-layer", 1, 0);
        fixture.dictionary("gone-domain", "tenant-a", "删除", "unused", "gone-layer", 0, 0);
        fixture.dictionary("foreign-domain", "tenant-a", "外部", "unused", "foreign-layer", 0, 0);
        fixture.jdbc.update("""
                INSERT INTO db_datasource_t VALUES
                ('owner','tenant-a','本人数据源','mysql','actor-a','org-b',NULL,0),
                ('same-org','tenant-a','部门数据源','postgresql','actor-b','org-a',NULL,0),
                ('foreign','tenant-b','外租户数据源','mysql','actor-a','org-a',NULL,0),
                ('deleted','tenant-a','已删除数据源','mysql','actor-a','org-a',NULL,1)
                """);
        fixture.jdbc.update("""
                INSERT INTO db_table_t VALUES ('owner','tenant-a',0), ('owner','tenant-b',0), ('same-org','tenant-a',0),
                ('foreign','tenant-a',0), ('deleted','tenant-a',0)
                """);
        fixture.binding("a", "first-domain", "deleted", "tenant-a", 0);
        fixture.binding("b", "first-domain", "owner", "tenant-a", 0);
        fixture.binding("c", "later-domain", "same-org", "tenant-a", 0);
        fixture.binding("d", "null-domain", "foreign", "tenant-a", 0);
        fixture.binding("e", "first-domain", "foreign", "tenant-b", 0);
        fixture.binding("f", "first-domain", "owner", "tenant-a", 1);

        var owned = StructuredValues.objectMap(fixture.run(OWNER));
        assertEquals(List.of("null-layer", "first-layer", "second-layer"), ids(owned, "layers"));
        assertEquals(List.of("null-domain", "first-domain", "later-domain"), ids(owned, "databases"));
        assertEquals(List.of("class"), ids(owned, "classifications"));
        var firstDomain = rows(owned, "databases").get(1);
        assertEquals("b", firstDomain.get("bindingId"));
        assertEquals("本人数据源", firstDomain.get("sourceName"));
        assertEquals(1L, firstDomain.get("tableCount"));
        assertEquals("REFERENCE", rows(owned, "databases").getFirst().get("mode"));
        assertEquals(List.of("d", "c", "b", "a"), ids(owned, "bindings"));
        assertEquals(List.of("INVALID", "INVALID", "NOT_CHECKED", "INVALID"),
                rows(owned, "bindings").stream().map(binding -> binding.get("state")).toList());

        var organization = StructuredValues.objectMap(fixture.run(ORG));
        var orgBindings = rows(organization, "bindings");
        assertEquals("部门数据源", orgBindings.get(1).get("sourceName"));
        assertEquals(1L, orgBindings.get(1).get("tableCount"));
        assertEquals("", orgBindings.get(2).get("sourceName"));
        assertEquals(0, orgBindings.get(2).get("tableCount"));

        var all = StructuredValues.objectMap(fixture.run(ALL));
        assertEquals(List.of("", "部门数据源", "本人数据源", ""),
                rows(all, "bindings").stream().map(binding -> binding.get("sourceName")).toList());

        var emptyOrg = StructuredValues.objectMap(fixture.run(new Scope("ORG", "actor-a", List.of())));
        assertEquals(List.of("INVALID", "INVALID", "INVALID", "INVALID"),
                rows(emptyOrg, "bindings").stream().map(binding -> binding.get("state")).toList());
        assertStatus(403, fixture.run(new Scope("ALL", "", List.of())));
        assertEquals(0, fixture.source.reads);
    }

    @Test
    void rejectsAnOverflowBeforeFetchingBindingDetails() throws IOException {
        var fixture = new Fixture();
        fixture.jdbc.update("""
                INSERT INTO dwm_center_layer_source_t
                SELECT 'binding-' || n, 'target-' || n, 'domain', 'source', NULL, 1, NULL, NULL, 'tenant-a', 0
                FROM SYSTEM_RANGE(1, 1001) AS numbers(n)
                """);
        assertStatus(400, fixture.run(ALL));
        assertEquals(7, fixture.source.reads);
    }

    private static void assertStatus(int expected, Object result) {
        var exit = assertInstanceOf(ExitValue.class, result);
        assertEquals(expected, ((Number) exit.getValues()[0]).intValue());
    }

    private static List<Map<String, Object>> rows(Map<String, Object> result, String key) {
        return StructuredValues.objectRows(result.get(key));
    }

    private static List<Object> ids(Map<String, Object> result, String key) {
        return rows(result, key).stream().map(row -> row.get("id")).toList();
    }

    public record Scope(String mode, String userId, List<String> organizationIds) {
        public String id() {
            return TenantContext.requireTenantId();
        }

        public Map<String, Object> dataSource() {
            return Map.of("scope", mode, "userId", userId, "organizationIds", organizationIds);
        }
    }

    private static final class Fixture {
        private final CountedSource source;
        private final JdbcTemplate jdbc;
        private final SQLModule db;
        private final StructuredTableReadMagicModule tableRead;
        private final MagicScript script;

        Fixture() throws IOException {
            this("MySQL");
        }

        Fixture(String dialect) throws IOException {
            source = new CountedSource(dialect);
            jdbc = new JdbcTemplate(source);
            tableRead = new StructuredTableReadMagicModule(jdbc);
            jdbc.execute("""
                    CREATE TABLE sym_dict_t (tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, dict_name VARCHAR, dict_code VARCHAR,
                      parent_id VARCHAR, sort_no INT, dict_desc VARCHAR, planning_config_json VARCHAR, revision_no INT,
                      created_time TIMESTAMP, updated_time TIMESTAMP, is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE dwm_center_layer_source_t (tid VARCHAR PRIMARY KEY, target_id VARCHAR, target_type VARCHAR,
                      datasource_id VARCHAR, configuration_json VARCHAR, revision_no INT, created_time TIMESTAMP,
                      updated_time TIMESTAMP, tenant_id VARCHAR, is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE db_datasource_t (tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, db_name VARCHAR, db_type VARCHAR,
                      created_by VARCHAR, org_id VARCHAR, updated_time TIMESTAMP, is_del INT)
                    """);
            jdbc.execute("CREATE TABLE db_table_t (datasource_id VARCHAR, tenant_id VARCHAR, is_del INT)");
            dictionary("root", "tenant-a", "分层根", "dataSourceType", null, 0, 0);
            dictionary("class-root", "tenant-a", "分类根", "resourceCatalogClass", null, 0, 0);
            dictionary("class", "tenant-a", "验证目录", "class", "class-root", 0, 0);

            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setDialectAdapter(new DialectAdapter());
            db.setResultProvider(new DefaultResultProvider("planning-read-validation"));
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setSqlInterceptors(List.of());
            db.setNamedTableInterceptors(List.of());
            String body = CanonicalMagicSources.byId(RESOURCE_ID)
                    .replaceAll("(?m)^import (tenantRuntime|dataScope|tableRead|jsons)\\s*\\R", "");
            script = MagicScript.create(body, null);
        }

        void dictionary(String id, String tenant, String name, String code, String parent, Integer order, int deleted) {
            jdbc.update("INSERT INTO sym_dict_t VALUES (?,?,?,?,?,?,NULL,NULL,1,NULL,NULL,?)",
                    id, tenant, name, code, parent, order, deleted);
        }

        void binding(String id, String domain, String dataSource, String tenant, int deleted) {
            jdbc.update("INSERT INTO dwm_center_layer_source_t VALUES (?,?,'domain',?,NULL,1,NULL,NULL,?,?)",
                    id, domain, dataSource, tenant, deleted);
        }

        Object run(Scope scope) {
            source.reads = 0;
            return readPlanning(scope);
        }

        private Object readPlanning(Scope scope) {
            var vars = Map.of("input", Map.of("tenantId", "tenant-b"), "db", db, "tenantRuntime", scope,
                    "dataScope", scope, "tableRead", tableRead, "jsons", new JsonModule());
            return TenantContext.call("tenant-a", () -> script.execute(new MagicScriptContext(vars)));
        }

        Object gateway(Scope scope, Map<String, Object> query) throws IOException {
            String gateway = CanonicalMagicSources.byId(GATEWAY_ID)
                    .replaceAll("(?m)^import (tenantRuntime|dataScope|tableRead|tableSort|'[^\\r\\n]+)[^\\r\\n]*\\r?\\n", "");
            var script = MagicScript.create(gateway, null);
            Function<Object[], Object> planningRead = arguments -> readPlanning(scope);
            var variables = Map.of("db", db, "tenantRuntime", scope, "dataScope", scope, "tableRead", tableRead,
                    "tableSort", new TableSortMagicModule(), "planningRead", planningRead,
                    "body", Map.of("kind", "table", "method", "GET", "body", query));
            source.reads = 0;
            return TenantContext.call("tenant-a", () -> script.execute(new MagicScriptContext(variables)));
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        private final String dialect;
        private int reads;

        CountedSource(String dialect) {
            super("jdbc:h2:mem:planning_read_" + UUID.randomUUID() + ";MODE=" + dialect + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
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
                                    (ignored, metaMethod, metaArgs) -> "getURL".equals(metaMethod.getName())
                                            ? "jdbc:" + dialect + "://planning-read-validation" : invoke(metadata, metaMethod, metaArgs));
                        }
                        if ("prepareStatement".equals(method.getName())
                                && ((String) arguments[0]).stripLeading().regionMatches(true, 0, "SELECT", 0, 6)) reads++;
                        return invoke(actual, method, arguments);
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
