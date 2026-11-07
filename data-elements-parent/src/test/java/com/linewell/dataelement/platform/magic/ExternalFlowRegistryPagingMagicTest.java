package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
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

class ExternalFlowRegistryPagingMagicTest {
    private static final String HISTORY = "94c9cc1afcd742a6a2486fce18adaa77";
    private static final String OPTIONS = "28d9a96c33d34c84a628add5d44d7b8e";
    private static final String TENANT = "validation-tenant";
    private static final String FOREIGN = "foreign-tenant";
    private static final String HASH = "a".repeat(64);
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final Set<String> PAGE_KEYS = Set.of("pageNum", "pageSize", "total", "list");

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void historyKeepsScopedJoinAndStableNullLastPagination(String dialect) throws IOException {
        var fixture = new Fixture(dialect, HISTORY);
        fixture.pipeline("foreign-pipeline", FOREIGN, "Excluded", 0, 1, HASH, 0);
        fixture.pipeline("deleted-pipeline", TENANT, "Excluded", 1, 1, HASH, 0);
        fixture.registration("foreign-link", TENANT, "foreign-pipeline", "Excluded", TIME);
        fixture.registration("deleted-link", TENANT, "deleted-pipeline", "Excluded", TIME);
        fixture.registration("missing-link", TENANT, "missing-pipeline", "Excluded", TIME);
        for (int i = 0; i < 105; i++) {
            String id = "pipeline-" + id(i);
            fixture.pipeline(id, TENANT, i == 104 ? null : (i == 1 ? "Searchable" : "Same name"), 0, 1, HASH, i);
            fixture.registration("ref-" + id(i), TENANT, id, "External-" + id(i), TIME.plusSeconds(i / 5));
        }
        fixture.registration("foreign-ref", FOREIGN, "pipeline-000", "Excluded", TIME);
        var expected = IntStream.range(0, 105).boxed()
                .sorted(Comparator.comparingInt((Integer i) -> i / 5).reversed().thenComparingInt(i -> i))
                .map(i -> "ref-" + id(i)).toList();

        for (int size : List.of(20, 100)) {
            var page = fixture.page(Map.of("pageSize", size, "tenantId", FOREIGN), 2);
            assertEquals(105, total(page));
            assertEquals(expected.subList(0, size), ids(page, "tid"));
            for (var row : rows(page)) {
                assertEquals(Set.of("tid", "externalFlowId", "pipelineId", "pipelineName", "pipelineStatus",
                        "processGroupId", "sourceFormat", "createdTime"), row.keySet());
                assertEquals("READY", row.get("pipelineStatus"));
                assertEquals("CANVAS_DSL_V1", row.get("sourceFormat"));
            }
        }
        for (String direction : List.of("asc", "desc")) {
            var sorted = fixture.page(Map.of("pageNum", 2, "pageSize", 100,
                    "sortField", "pipelineName", "sortDir", direction), 2);
            assertEquals("ref-104", ids(sorted, "tid").getLast());
            assertNull(rows(sorted).getLast().get("pipelineName"));
            var finalPage = direction.equals("asc")
                    ? List.of("ref-101", "ref-102", "ref-103", "ref-001", "ref-104")
                    : List.of("ref-100", "ref-101", "ref-102", "ref-103", "ref-104");
            assertEquals(finalPage, ids(sorted, "tid"));
        }
        assertEquals(List.of("ref-001"), ids(fixture.page(Map.of("keyword", " Searchable "), 2), "tid"));
        assertEquals(List.of("ref-020"), ids(fixture.page(Map.of("keyword", "External-02_",
                "sortField", "externalFlowId", "sortDir", "asc", "pageSize", 1), 2), "tid"));
        assertEquals(List.of(), ids(fixture.page(Map.of("keyword", "Excluded"), 1), "tid"));
        assertEquals(List.of(), ids(fixture.page(Map.of("pageNum", 7), 1), "tid"));
        assertThrows(RuntimeException.class, () -> fixture.run(Map.of("sortField", "tid"), TENANT));
        assertEquals(0, fixture.source.reads);
        fixture.assertRejected(401, Map.of("tenantId", TENANT), "");
        fixture.assertRejected(400, Map.of("keyword", "x".repeat(101)), TENANT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void pipelineOptionsKeepNativeOrderAndOnlyReturnActiveSavedDsl(String dialect) throws IOException {
        var fixture = new Fixture(dialect, OPTIONS);
        fixture.pipeline("foreign", FOREIGN, "Excluded", 0, 1, HASH, 500);
        fixture.pipeline("deleted", TENANT, "Excluded", 1, 1, HASH, 500);
        fixture.pipeline("unsaved", TENANT, "Excluded", 0, 1, null, 500);
        fixture.pipeline("empty", TENANT, "Excluded", 0, 1, "", 500);
        fixture.pipeline("v2", TENANT, "Excluded", 0, 2, HASH, 500);
        for (int i = 0; i < 105; i++) {
            fixture.pipeline("pipeline-" + id(i), TENANT, i == 1 ? "Searchable" : "Pipeline",
                    0, 1, HASH, i / 5);
        }
        var expected = IntStream.range(0, 105).mapToObj(i -> "pipeline-" + id(104 - i)).toList();
        for (int size : List.of(20, 100)) {
            var page = fixture.page(Map.of("pageSize", size, "tenantId", FOREIGN), 2);
            assertEquals(105, total(page));
            assertEquals(expected.subList(0, size), ids(page, "id"));
            for (var row : rows(page)) {
                assertEquals(Set.of("id", "name", "status", "processGroupId"), row.keySet());
                assertEquals("READY", row.get("status"));
                assertEquals("group-1", row.get("processGroupId"));
            }
        }
        assertEquals(expected.subList(100, 105), ids(fixture.page(Map.of("pageNum", 2, "pageSize", 100), 2), "id"));
        assertEquals(List.of("pipeline-001"), ids(fixture.page(Map.of("keyword", " Searchable "), 2), "id"));
        assertEquals(List.of("pipeline-009"), ids(fixture.page(Map.of("keyword", "pipeline-00_", "pageSize", 1), 2), "id"));
        assertEquals(0, total(fixture.page(Map.of("keyword", "Excluded"), 1)));
        fixture.assertRejected(401, Map.of("tenantId", TENANT), "");
        fixture.assertRejected(400, Map.of("pageNum", 1001), TENANT);
        fixture.assertRejected(400, Map.of("pageSize", 101), TENANT);
        fixture.assertRejected(400, Map.of("keyword", "x".repeat(101)), TENANT);
    }

    private static String id(int index) { return String.format("%03d", index); }
    private static int total(Map<String, Object> page) { return ((Number) page.get("total")).intValue(); }
    private static List<Map<String, Object>> rows(Map<String, Object> page) {
        return StructuredValues.objectRows(page.get("list"));
    }
    private static List<Object> ids(Map<String, Object> page, String field) {
        return rows(page).stream().map(row -> row.get(field)).toList();
    }

    public record Session(String id) { }

    private static final class Fixture {
        final CountedSource source;
        final JdbcTemplate jdbc;
        final SQLModule db;
        final StructuredTableReadMagicModule tableRead;
        final MagicScript script;

        Fixture(String dialect, String resourceId) throws IOException {
            source = new CountedSource(dialect);
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE nifi_pipeline_t(id VARCHAR PRIMARY KEY, tenant_id VARCHAR, name VARCHAR,
                        status VARCHAR, nifi_process_group_id VARCHAR, updated_at BIGINT, is_del INT,
                        dsl_version BIGINT, dsl_hash VARCHAR(64))
                    """);
            jdbc.execute("""
                    CREATE TABLE nifi_pipeline_external_ref_t(tid VARCHAR PRIMARY KEY, tenant_id VARCHAR,
                        pipeline_id VARCHAR, external_flow_id VARCHAR, source_format VARCHAR, created_time TIMESTAMP)
                    """);
            tableRead = new StructuredTableReadMagicModule(jdbc);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setDialectAdapter(new DialectAdapter());
            db.setResultProvider(new DefaultResultProvider("external-flow-registry-validation"));
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of());
            script = MagicScript.create(CanonicalMagicSources.byId(resourceId)
                    .replaceAll("(?m)^import [^\\r\\n]+\\R", ""), null);
        }

        void pipeline(String id, String tenant, String name, int deleted, int version, String hash, long updated) {
            jdbc.update("INSERT INTO nifi_pipeline_t VALUES (?,?,?,'READY','group-1',?,?,?,?)",
                    id, tenant, name, updated, deleted, version, hash);
        }

        void registration(String id, String tenant, String pipeline, String external, LocalDateTime created) {
            jdbc.update("INSERT INTO nifi_pipeline_external_ref_t VALUES (?,?,?,?,'CANVAS_DSL_V1',?)",
                    id, tenant, pipeline, external, created);
        }

        Object run(Map<String, Object> body, String tenant) {
            source.reads = 0;
            var values = Map.of("tenantRuntime", new Session(tenant), "body", body, "db", db,
                    "tableRead", tableRead, "tableSort", new TableSortMagicModule());
            return TenantContext.call(TENANT, () -> script.execute(new MagicScriptContext(values)));
        }

        Map<String, Object> page(Map<String, Object> body, int expectedReads) {
            var result = StructuredValues.objectMap(run(body, TENANT));
            assertEquals(PAGE_KEYS, result.keySet());
            assertEquals(expectedReads, source.reads);
            return result;
        }

        void assertRejected(int status, Map<String, Object> body, String tenant) {
            var result = assertInstanceOf(ExitValue.class, run(body, tenant));
            assertEquals(status, ((Number) result.getValues()[0]).intValue());
            assertEquals(0, source.reads);
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        private final String dialect;
        int reads;

        CountedSource(String dialect) {
            super("jdbc:h2:mem:flow_registry_" + UUID.randomUUID() + ";MODE=" + dialect
                    + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            this.dialect = dialect.toLowerCase(Locale.ROOT);
        }

        @Override
        public Connection getConnection() throws SQLException {
            var actual = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("getMetaData".equals(method.getName())) {
                            var metadata = actual.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (ignored, metaMethod, metaArgs) -> "getURL".equals(metaMethod.getName())
                                            ? "jdbc:" + dialect + "://external-registry-validation"
                                            : invoke(metadata, metaMethod, metaArgs));
                        }
                        if ("prepareStatement".equals(method.getName())
                                && args[0].toString().stripLeading().regionMatches(true, 0, "select", 0, 6)) {
                            reads++;
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
