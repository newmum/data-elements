package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.LowerColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class ResourceCommandMagicTest {
    @Test
    void registrationRollsBackWhenAuditFailsAndUpdatesOnlyTheCurrentTenant() throws Exception {
        var fixture = new Fixture();
        fixture.jdbc.execute("ALTER TABLE res_resource_operation_log ADD CONSTRAINT audit_rejected CHECK (1 = 0)");
        assertThrows(RuntimeException.class, fixture::register);
        assertEquals("ARCHIVED", fixture.state("tenant-a"));
        assertEquals(1, fixture.version("tenant-a"));
        assertEquals(0, fixture.metadata.refreshes);

        fixture.jdbc.execute("ALTER TABLE res_resource_operation_log DROP CONSTRAINT audit_rejected");
        assertInstanceOf(Map.class, fixture.register());
        assertEquals("ACTIVE", fixture.state("tenant-a"));
        assertEquals(2, fixture.version("tenant-a"));
        assertEquals("ARCHIVED", fixture.state("tenant-b"));
        assertEquals(1, fixture.version("tenant-b"));
        assertEquals("", fixture.jdbc.queryForObject("SELECT asset_desc FROM db_table_t WHERE tenant_id='tenant-a'", String.class));
        assertEquals(1, fixture.jdbc.queryForObject("SELECT COUNT(*) FROM res_resource_operation_log WHERE tenant_id='tenant-a'", Integer.class));
        assertEquals(1, fixture.metadata.refreshes);
    }

    @Test
    void archiveHonorsPublishedReferencesAndRejectsAStaleDatabaseVersion() throws Exception {
        var fixture = new Fixture();
        fixture.jdbc.update("UPDATE db_table_t SET resource_state='ACTIVE' WHERE tenant_id='tenant-a'");
        fixture.jdbc.update("INSERT INTO da_catalog_t VALUES ('foreign','tenant-b','resource-a',0),('visible','tenant-a','resource-a',0)");
        fixture.jdbc.update("INSERT INTO res_catalog_publish_request VALUES ('foreign','tenant-b','foreign','PUBLISHED',0),"
                + "('published','tenant-a','visible','PUBLISHED',0)");
        assertStatus(409, fixture.archive());
        assertEquals("ACTIVE", fixture.state("tenant-a"));
        fixture.jdbc.update("UPDATE res_catalog_publish_request SET is_del=1 WHERE tenant_id='tenant-a'");
        fixture.jdbc.update("UPDATE db_table_t SET resource_revision_no=2 WHERE tenant_id='tenant-a'");
        assertStatus(409, fixture.archive());
        assertEquals("ACTIVE", fixture.state("tenant-a"));
        fixture.snapshotVersion = 2;
        var result = assertInstanceOf(Map.class, fixture.archive());
        assertEquals("ARCHIVED", result.get("state"));
        assertEquals(3, fixture.version("tenant-a"));
        assertEquals(1, fixture.version("tenant-b"));
    }

    @Test
    void previewRequestsOnlyLiveTenantColumnsAndMasksPersonalInformation() throws Exception {
        var fixture = new Fixture();
        fixture.jdbc.update("INSERT INTO db_table_column_t VALUES ('tenant-a','resource-a','title',0,1,0),"
                + "('tenant-a','resource-a','mobile',0,2,0),('tenant-a','resource-a','password',0,3,0),"
                + "('tenant-a','resource-a','deleted_field',0,4,1),('tenant-b','resource-a','foreign_field',0,5,0)");
        var result = assertInstanceOf(Map.class, fixture.run(Map.of("id", "resource-a", "method", "GET")));
        assertEquals(List.of(Map.of("title", "Validation", "mobile", "***")), result.get("records"));
    }

    private static void assertStatus(int code, Object value) {
        var result = assertInstanceOf(ExitValue.class, value);
        assertEquals(code, ((Number) result.getValues()[0]).intValue());
    }

    public static class Session {
        public String id() { return "tenant-a"; }
        public String getLoginIdAsString() { return "actor-a"; }
    }

    public static class Metadata {
        int refreshes;
        public void refreshRegistrationStatus(String id, boolean rebuild) { refreshes++; }
    }

    private static DriverManagerDataSource validationSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:resource_command_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1") {
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
                            return invoke(connection, method, args);
                        });
            }
        };
    }

    private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    private static class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final MagicScript script;
        final Metadata metadata = new Metadata();
        int snapshotVersion = 1;

        Fixture() throws IOException {
            var dataSource = validationSource();
            jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("""
                    CREATE TABLE db_table_t (tid VARCHAR(64), tenant_id VARCHAR(64), datasource_id VARCHAR(64),
                      table_name VARCHAR(64), table_name_cn VARCHAR(64), resource_domain_id VARCHAR(64),
                      resource_state VARCHAR(32), resource_revision_no INT, resource_owner VARCHAR(128),
                      business_type VARCHAR(64), asset_desc VARCHAR(255), asset_status INT, flow_status INT,
                      reg_time TIMESTAMP, updated_time TIMESTAMP, updated_by VARCHAR(64), is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE res_resource_operation_log (tid VARCHAR(64), tenant_id VARCHAR(64), actor_id VARCHAR(64),
                      action_name VARCHAR(64), object_id VARCHAR(64), object_name VARCHAR(64), detail_text VARCHAR(255), created_time TIMESTAMP)
                    """);
            jdbc.execute("CREATE TABLE da_catalog_t (tid VARCHAR(64), tenant_id VARCHAR(64), source_table_id VARCHAR(64), is_del INT)");
            jdbc.execute("CREATE TABLE res_catalog_publish_request (tid VARCHAR(64), tenant_id VARCHAR(64), catalog_id VARCHAR(64), request_status VARCHAR(32), is_del INT)");
            jdbc.execute("CREATE TABLE db_table_column_t (tenant_id VARCHAR(64), table_id VARCHAR(64), column_name VARCHAR(64), is_masking INT, ordinal_position INT, is_del INT)");
            for (String tenant : List.of("tenant-a", "tenant-b")) {
                jdbc.update("""
                        INSERT INTO db_table_t(tid,tenant_id,datasource_id,table_name,resource_domain_id,resource_state,resource_revision_no,asset_desc,is_del)
                        VALUES ('resource-a',?,'source-a','test_table','old','ARCHIVED',1,'previous',0)
                        """, tenant);
            }
            var sources = new MagicDynamicDataSource();
            sources.setDefault(dataSource);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
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
            String source = CanonicalMagicSources.byId("4049d07b6bcc53f2b88f544ff0f62d22")
                    .replaceAll("(?m)^import (?!cn\\.hutool\\.)[^\\r\\n]*\\r?\\n", "");
            script = MagicScript.create("var sampleData = () => captureSample(body);\n" + source, null);
        }

        String state(String tenant) {
            return jdbc.queryForObject("SELECT resource_state FROM db_table_t WHERE tenant_id=?", String.class, tenant);
        }

        int version(String tenant) {
            return jdbc.queryForObject("SELECT resource_revision_no FROM db_table_t WHERE tenant_id=?", Integer.class, tenant);
        }

        Object register() throws Exception {
            return run(Map.of("method", "POST", "body", Map.of("entityId", "resource-a", "databaseId", "domain-a", "owner", "Validation")));
        }

        Object archive() throws Exception {
            return run(Map.of("method", "PUT", "id", "resource-a", "action", "archive", "version", snapshotVersion));
        }

        Object run(Map<String, Object> input) throws Exception {
            var session = new Session();
            Function<Object[], Object> models = args -> Map.of("resources", List.of(new LinkedHashMap<>(Map.of(
                    "id", "resource-a", "version", snapshotVersion, "structure", Map.of("sourceId", "source-a")))));
            Function<Object[], Object> sample = args -> {
                var request = assertInstanceOf(Map.class, args[0]);
                assertEquals(List.of("title", "mobile"), request.get("columns"));
                return Map.of("rows", List.of(new LinkedHashMap<>(Map.of("title", "Validation", "mobile", "01000000000"))));
            };
            var variables = Map.<String, Object>ofEntries(
                    Map.entry("db", db), Map.entry("tableRead", new StructuredTableReadMagicModule(jdbc)),
                    Map.entry("input", input), Map.entry("body", Map.of()),
                    Map.entry("tenantRuntime", session), Map.entry("StpUtil", session), Map.entry("metadataAsset", metadata),
                    Map.entry("readModels", models), Map.entry("captureSample", sample),
                    Map.entry("readPlanning", (Function<Object[], Object>) args -> Map.of("databases", List.of(Map.of("id", "domain-a", "state", "ACTIVE")))),
                    Map.entry("technicalRead", (Function<Object[], Object>) args -> Map.of("sources", List.of(Map.of("id", "source-a")))));
            return TenantContext.call("tenant-a", () -> script.execute(new MagicScriptContext(variables)));
        }
    }
}
