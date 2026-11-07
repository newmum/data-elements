package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

class ResourceCenterDatasourceOptionsMagicTest {
    private static final String RESOURCE_ID = "dwm_center_datasource_options_01";
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 1, 1, 12, 0);

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void readsTwentyAndOneHundredOptionsInThreeSqlStatements(String dialect) throws IOException {
        var fixture = new Fixture(dialect);
        fixture.dictionary("node-type", "tenant-a", "nodeType", "节点类型", null, 0);
        fixture.dictionary("node-type", "tenant-b", "nodeType", "外部根节点", null, 0);
        fixture.dictionary("business", "tenant-a", "business", "业务库", "node-type", 0);
        fixture.dictionary("business", "tenant-b", "business", "错误标签", "node-type", 0);
        fixture.dictionary("foreign", "tenant-b", "foreign", "外部标签", "node-type", 0);
        fixture.dictionary("deleted", "tenant-a", "deleted", "失效标签", "node-type", 1);
        fixture.dictionary("unrelated", "tenant-a", "unrelated", "其他标签", "foreign", 0);
        fixture.dictionary("deleted-root", "tenant-a", "nodeType", "失效父节点", null, 1);
        fixture.dictionary("wrong-parent", "tenant-a", "other", "错误父标签", "deleted-root", 0);

        fixture.datasource("foreign-source", "tenant-b", "Foreign", "business", 0, TIME.plusDays(1), TIME);
        fixture.datasource("deleted-source", "tenant-a", "Deleted", "business", 1, TIME.plusDays(1), TIME);
        fixture.datasource("inactive-source", "tenant-a", "Inactive", "business", null, TIME.plusDays(1), TIME);
        fixture.datasource("validation_hidden", "tenant-a", "Hidden by id", "business", 0, TIME.plusDays(1), TIME);
        fixture.datasource("hidden-by-name", "tenant-a", "validation_hidden", "business", 0, TIME.plusDays(1), TIME);

        var expected = new ArrayList<Expected>();
        int previousSize = 0;
        for (int size : List.of(20, 100)) {
            for (int index = previousSize; index < size; index++) {
                String id = index == 0 ? "validationX_source" : "source-" + index;
                String name = index == 0 ? "业务 validationX_name" : String.format("来源 %03d", 100 - index);
                String node = switch (index) {
                    case 1 -> "foreign";
                    case 2 -> "deleted";
                    case 3 -> "unrelated";
                    case 4 -> "wrong-parent";
                    default -> "business";
                };
                var updated = TIME.plusSeconds(index / 4);
                var created = TIME.plusSeconds(index / 2);
                fixture.datasource(id, "tenant-a", name, node, 0, updated, created);
                expected.add(new Expected(id, name, updated, created));
            }
            var response = fixture.options(Map.of("tenantId", "tenant-b", "keyword", ""));
            var rows = StructuredValues.objectRows(response.get("list"));
            assertEquals(size, ((Number) response.get("total")).intValue());
            assertEquals(size, rows.size());
            var ordered = expected.stream().sorted(Comparator.comparing(Expected::updated).reversed()
                    .thenComparing(Expected::created, Comparator.reverseOrder()).thenComparing(Expected::name))
                    .map(Expected::id).toList();
            assertEquals(ordered, rows.stream().map(row -> row.get("tid")).toList());
            for (var row : rows) {
                assertEquals(Set.of("label", "value", "tid", "dbName", "dbType", "nodeId", "nodeName", "showConnect", "connectionStatus"), row.keySet());
                assertEquals(row.get("tid"), row.get("value"));
                assertEquals(row.get("dbName"), row.get("label"));
                assertEquals("mysql", row.get("dbType"));
                if ("business".equals(row.get("nodeId"))) {
                    assertEquals("业务库", row.get("nodeName"));
                } else {
                    assertNull(row.get("nodeName"));
                }
            }
            assertEquals(3, fixture.source.reads);
            previousSize = size;
        }
        assertEquals(96, ((Number) fixture.options(Map.of("keyword", "  MySQL 业务库 ")).get("total")).intValue());
        assertEquals(3, fixture.source.reads);
        var denied = fixture.run(Map.of(), "");
        assertEquals(401, ((Number) assertInstanceOf(ExitValue.class, denied).getValues()[0]).intValue());
        assertEquals(0, fixture.source.reads);
        System.out.printf("Resource center datasource options %s: 20/100 rows each 3 SQL%n", dialect);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MySQL", "PostgreSQL"})
    void searchesOnlyTheLatestFiveHundredCandidatesUsingLiteralText(String dialect) throws IOException {
        var fixture = new Fixture(dialect);
        fixture.jdbc.update("""
                INSERT INTO db_datasource_t(tid,tenant_id,db_name,db_type,show_connect,connection_status,node_id,is_del,updated_time,created_time)
                SELECT CONCAT('source-',seq_no),'tenant-a',CONCAT('source-',seq_no),'mysql',1,0,NULL,0,
                    TIMESTAMP '2026-01-02 00:00:00', TIMESTAMP '2026-01-02 00:00:00'
                FROM SYSTEM_RANGE(1,499) AS seq(seq_no)
                """);
        fixture.datasource("window-edge", "tenant-a", "budget_100%", null, 0, TIME.plusDays(1), TIME);
        fixture.datasource("window-outside", "tenant-a", "outside_100%", null, 0, TIME, TIME);
        fixture.datasource("validation_top", "tenant-a", "Rejected", null, 0, TIME.plusDays(10), TIME);

        assertEquals(100, ((Number) fixture.options(Map.of()).get("total")).intValue());
        assertEquals(2, fixture.source.reads);
        var result = fixture.options(Map.of("keyword", "  _100% "));
        assertEquals(List.of("window-edge"), StructuredValues.objectRows(result.get("list")).stream().map(row -> row.get("tid")).toList());
        assertEquals(2, fixture.source.reads);
        assertEquals(List.of(), fixture.options(Map.of("keyword", "outside_100%")).get("list"));
        assertEquals(2, fixture.source.reads);
        assertEquals(List.of(), fixture.options(Map.of("keyword", "' OR 1=1 --")).get("list"));
    }

    public record Session(String tenant) {
        public String id() { return tenant; }
    }

    private record Expected(String id, String name, LocalDateTime updated, LocalDateTime created) { }

    private static final class Fixture {
        final CountedSource source;
        final JdbcTemplate jdbc;
        final SQLModule db;
        final StructuredTableReadMagicModule tableRead;
        final MagicScript script;

        Fixture(String dialect) throws IOException {
            source = new CountedSource(dialect);
            jdbc = new JdbcTemplate(source);
            jdbc.execute("""
                    CREATE TABLE db_datasource_t (tid VARCHAR, tenant_id VARCHAR, db_name VARCHAR, db_type VARCHAR,
                        show_connect INT, connection_status INT, node_id VARCHAR, is_del INT, updated_time TIMESTAMP, created_time TIMESTAMP)
                    """);
            jdbc.execute("CREATE TABLE sym_dict_t(tid VARCHAR,tenant_id VARCHAR,dict_code VARCHAR,dict_name VARCHAR,parent_id VARCHAR,is_del INT)");
            tableRead = new StructuredTableReadMagicModule(jdbc);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setDialectAdapter(new DialectAdapter());
            db.setResultProvider(new DefaultResultProvider("resource-center-options-validation"));
            var mapper = new ColumnMapperAdapter();
            mapper.add(new DefaultColumnMapperProvider());
            mapper.add(new CamelColumnMapperProvider());
            db.setColumnMapperProvider(mapper);
            db.setColumnMapRowMapper(mapper.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setNamedTableInterceptors(List.of());
            db.setSqlInterceptors(List.of());
            var body = CanonicalMagicSources.byId(RESOURCE_ID).replaceAll("(?m)^import (tenantRuntime|tableRead)\\s*\\R", "");
            script = MagicScript.create(body, null);
        }

        void datasource(String id, String tenant, String name, String node, Integer deleted, LocalDateTime updated, LocalDateTime created) {
            jdbc.update("INSERT INTO db_datasource_t VALUES (?,?,?,'mysql',1,0,?,?,?,?)", id, tenant, name, node, deleted, updated, created);
        }

        void dictionary(String id, String tenant, String code, String name, String parent, int deleted) {
            jdbc.update("INSERT INTO sym_dict_t VALUES (?,?,?,?,?,?)", id, tenant, code, name, parent, deleted);
        }

        Map<String, Object> options(Map<String, Object> body) {
            return StructuredValues.objectMap(run(body, "tenant-a"));
        }

        Object run(Map<String, Object> body, String tenant) {
            source.reads = 0;
            var vars = Map.of("tenantRuntime", new Session(tenant), "tableRead", tableRead, "db", db, "body", body);
            return TenantContext.call("tenant-a", () -> script.execute(new MagicScriptContext(vars)));
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        final String dialect;
        int reads;

        CountedSource(String dialect) {
            super("jdbc:h2:mem:resource_options_" + UUID.randomUUID() + ";MODE=" + dialect
                    + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            this.dialect = dialect.toLowerCase(Locale.ROOT);
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
                                            ? "jdbc:" + dialect + "://resource-options-validation"
                                            : invoke(metadata, metaMethod, metaArguments));
                        }
                        if ("prepareStatement".equals(method.getName())
                                && arguments[0].toString().stripLeading().regionMatches(true, 0, "select", 0, 6)) {
                            reads++;
                        }
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
