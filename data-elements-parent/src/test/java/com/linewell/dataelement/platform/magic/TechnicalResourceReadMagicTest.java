package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class TechnicalResourceReadMagicTest {
    private static final String RESOURCE_ID = "490931832d8a5b87a46bdb77544bb02c";

    @Test
    void pagesTwentyAndOneHundredWithFixedReadsAndAppendsRegisteredTables() throws IOException {
        var fixture = new Fixture();
        fixture.jdbc.execute("""
                INSERT INTO db_table_t(tid,tenant_id,datasource_id,table_name,field_count,is_del)
                SELECT 'table-' || LPAD(n,3,'0'), 'tenant-a', 'source-a', 'validation_' || LPAD(n,3,'0'), 1, 0
                  FROM SYSTEM_RANGE(0,104) AS numbers(n)
                """);
        fixture.jdbc.execute("""
                INSERT INTO db_table_column_t(tid,table_id,tenant_id,column_name,column_type,ordinal_position,is_del)
                SELECT 'column-' || LPAD(n,3,'0'), 'table-' || LPAD(n,3,'0'), 'tenant-a', 'identifier', 'INTEGER', 1, 0
                  FROM SYSTEM_RANGE(0,104) AS numbers(n)
                """);
        fixture.jdbc.update("UPDATE db_table_t SET resource_domain_id = 'domain-a' WHERE tid = 'table-000'");
        fixture.table("registered", "tenant-a", "source-a", "registered_outside_filter", "domain-b", 0);
        fixture.table("wrong-tenant", "tenant-b", "source-a", "validation_foreign", "domain-a", 0);
        fixture.table("foreign-source", "tenant-a", "source-b", "validation_foreign_source", "domain-a", 0);
        fixture.table("deleted-source", "tenant-a", "source-deleted", "validation_deleted_source", "domain-a", 0);
        fixture.table("deleted-table", "tenant-a", "source-a", "validation_deleted_table", "domain-a", 1);
        fixture.jdbc.update("""
                INSERT INTO db_table_column_t(tid,table_id,tenant_id,column_name,is_del) VALUES
                ('column-foreign','table-000','tenant-b','validation_foreign_column',0),
                ('column-deleted','table-000','tenant-a','validation_deleted_column',1)
                """);

        List<Map<String, Object>> shortPage = List.of();
        int firstReads = 0;
        for (int size : List.of(20, 100)) {
            var result = fixture.result(Map.of("pageSize", size, "entityKeyword", "validation_"));
            var pagination = StructuredValues.objectMap(result.get("entityPagination"));
            assertEquals(105, ((Number) pagination.get("total")).intValue());
            assertEquals(size + 1, ((Number) pagination.get("loaded")).intValue());
            assertEquals(List.of("source-a"), ids(result, "sources"));
            assertEquals(List.of("table-000", "registered"), ids(result, "resources"));
            var entities = rows(result, "entities");
            assertEquals(size + 1, entities.size());
            assertEquals("table-000", entities.getFirst().get("id"));
            assertEquals("registered", entities.getLast().get("id"));
            assertEquals(List.of("column-000"), ids(entities.getFirst(), "fields"));
            assertEquals("declared_complete", entities.getFirst().get("shapeBasis"));
            assertEquals("MYSQL", rows(result, "sources").getFirst().get("engine"));
            assertFalse(rows(result, "sources").getFirst().containsKey("poolCfg"));
            assertNull(result.get("qualityError"));
            if (size == 20) {
                firstReads = fixture.source.reads;
                shortPage = entities.subList(0, size);
            } else {
                assertEquals(firstReads, fixture.source.reads);
                assertEquals(shortPage, entities.subList(0, shortPage.size()));
            }
        }
        assertTrue(firstReads <= 12, "The scoped page and its dependencies must have a fixed read budget");

        assertEquals(List.of("table-000"), ids(fixture.result(Map.of("entityIds", List.of("table-000"))), "entities"));
        for (String unavailable : List.of("wrong-tenant", "foreign-source", "deleted-source", "deleted-table")) {
            var error = assertInstanceOf(ExitValue.class,
                    fixture.run(Map.of("entityIds", List.of("table-000", unavailable))));
            assertEquals(404, ((Number) error.getValues()[0]).intValue());
        }
    }

    @Test
    void qualityObservationsAreBoundedScopedAndIndependentOfTechnicalProjection() throws IOException {
        var fixture = new Fixture();
        fixture.table("table-a", "tenant-a", "source-a", "validation_table", null, 0);
        fixture.jdbc.update("""
                INSERT INTO dq_quality_task_t VALUES
                ('task-a','tenant-a','table-a',0), ('task-foreign','tenant-b','foreign-table',0),
                ('task-deleted','tenant-a','deleted-table',1)
                """);
        fixture.jdbc.execute("""
                INSERT INTO dq_quality_run_t
                SELECT 'run-' || LPAD(n,3,'0'), 'task-a', 'tenant-a', 'SUCCEEDED',
                       CASE WHEN n=204 THEN 0 ELSE 5 END, 1, 80, NULL, NULL,
                       DATEADD(SECOND,n,TIMESTAMP '2026-01-01 00:00:00'),0
                  FROM SYSTEM_RANGE(0,204) AS numbers(n)
                """);
        fixture.jdbc.update("""
                INSERT INTO dq_quality_run_t VALUES
                ('run-foreign-task','task-foreign','tenant-a','SUCCEEDED',5,1,80,NULL,NULL,TIMESTAMP '2027-01-01 00:00:00',0),
                ('run-foreign-tenant','task-a','tenant-b','SUCCEEDED',5,1,80,NULL,NULL,TIMESTAMP '2027-01-01 00:00:00',0),
                ('run-deleted-task','task-deleted','tenant-a','SUCCEEDED',5,1,80,NULL,NULL,TIMESTAMP '2027-01-01 00:00:00',0),
                ('run-deleted','task-a','tenant-a','SUCCEEDED',5,1,80,NULL,NULL,TIMESTAMP '2027-01-01 00:00:00',1)
                """);
        fixture.jdbc.execute("""
                INSERT INTO dq_quality_issue_sample_t
                SELECT 'issue-' || LPAD(n,3,'0'), 'run-204', 'tenant-a', 'validation_column', 'HIGH', 'OPEN',
                       DATEADD(SECOND,n,TIMESTAMP '2026-01-01 00:00:00'), 'validation-sensitive-value'
                  FROM SYSTEM_RANGE(0,204) AS numbers(n)
                """);
        fixture.jdbc.update("""
                INSERT INTO dq_quality_issue_sample_t VALUES
                ('issue-foreign-tenant','run-204','tenant-b','validation_column','HIGH','OPEN',TIMESTAMP '2027-01-01 00:00:00','private'),
                ('issue-foreign-run','run-foreign-tenant','tenant-a','validation_column','HIGH','OPEN',TIMESTAMP '2027-01-01 00:00:00','private'),
                ('issue-foreign-task','run-foreign-task','tenant-a','validation_column','HIGH','OPEN',TIMESTAMP '2027-01-01 00:00:00','private'),
                ('issue-deleted-run','run-deleted','tenant-a','validation_column','HIGH','OPEN',TIMESTAMP '2027-01-01 00:00:00','private')
                """);

        var result = fixture.result(Map.of());
        assertNull(result.get("qualityError"));
        var results = rows(result, "results");
        var issues = rows(result, "issues");
        assertEquals(200, results.size());
        assertEquals(200, issues.size());
        assertEquals("run-204", results.getFirst().get("id"));
        assertEquals("run-005", results.getLast().get("id"));
        assertEquals("table-a", results.getFirst().get("entityId"));
        assertNull(results.getFirst().get("score"));
        assertEquals("issue-204", issues.getFirst().get("id"));
        assertEquals("issue-005", issues.getLast().get("id"));
        assertEquals("table-a", issues.getFirst().get("entityId"));
        assertFalse(result.toString().contains("validation-sensitive-value"));

        fixture.jdbc.execute("DROP TABLE dq_quality_issue_sample_t");
        var degraded = fixture.result(Map.of());
        assertEquals(List.of("table-a"), ids(degraded, "entities"));
        assertTrue(rows(degraded, "issues").isEmpty());
        var error = assertInstanceOf(String.class, degraded.get("qualityError"));
        assertFalse(error.isBlank());
        assertFalse(error.contains("dq_quality_issue_sample_t"));
    }

    private static List<Map<String, Object>> rows(Map<String, Object> result, String key) {
        return StructuredValues.objectRows(result.get(key));
    }

    private static List<Object> ids(Map<String, Object> result, String key) {
        return rows(result, key).stream().map(row -> row.get("id")).toList();
    }

    public static class Session {
        public String id() {
            return TenantContext.requireTenantId();
        }

        public String getLoginIdAsString() {
            return "actor-a";
        }
    }

    private static final class Fixture {
        private final CountedSource source = new CountedSource();
        private final JdbcTemplate jdbc = new JdbcTemplate(source);
        private final SQLModule db;
        private final StructuredTableReadMagicModule tableRead = new StructuredTableReadMagicModule(jdbc);
        private final MagicScript script;

        Fixture() throws IOException {
            jdbc.execute("""
                    CREATE TABLE db_datasource_t (tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, db_name VARCHAR, db_type VARCHAR,
                      database_type VARCHAR, connection_status VARCHAR, is_enable INT, org_id VARCHAR, app_id VARCHAR,
                      revision INT, updated_time TIMESTAMP, pool_cfg VARCHAR, is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE db_table_t (tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, datasource_id VARCHAR, table_name VARCHAR,
                      table_name_cn VARCHAR, table_comment VARCHAR, table_type VARCHAR, field_count INT, revision INT,
                      created_time TIMESTAMP, updated_time TIMESTAMP, resource_domain_id VARCHAR, resource_owner VARCHAR,
                      asset_desc VARCHAR, resource_plan_id VARCHAR, resource_state VARCHAR, resource_model_id VARCHAR,
                      resource_field_map VARCHAR, resource_revision_no INT, is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE db_table_column_t (tid VARCHAR PRIMARY KEY, table_id VARCHAR, tenant_id VARCHAR, column_type VARCHAR,
                      data_type VARCHAR, column_name VARCHAR, column_comment VARCHAR, length INT, precision_length INT,
                      scale INT, nullable VARCHAR, ordinal_position INT, primary_key INT, is_unique INT,
                      data_standard_id VARCHAR, is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE da_metadata_t (tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, meta_name VARCHAR, meta_code VARCHAR,
                      standard_encode VARCHAR, biz_def VARCHAR, data_type_id VARCHAR, field_type VARCHAR, field_length INT,
                      numeric_precision INT, numeric_scale INT, data_category_name VARCHAR, publish_status INT, version_no INT,
                      updated_time TIMESTAMP, created_time TIMESTAMP, is_nullable VARCHAR, is_del INT)
                    """);
            jdbc.execute("CREATE TABLE dq_quality_task_t(tid VARCHAR PRIMARY KEY, tenant_id VARCHAR, table_id VARCHAR, is_del INT)");
            jdbc.execute("""
                    CREATE TABLE dq_quality_run_t (tid VARCHAR PRIMARY KEY, task_id VARCHAR, tenant_id VARCHAR, status VARCHAR,
                      checked_count INT, violation_count INT, quality_score INT, started_at TIMESTAMP, finished_at TIMESTAMP,
                      created_time TIMESTAMP, is_del INT)
                    """);
            jdbc.execute("""
                    CREATE TABLE dq_quality_issue_sample_t (tid VARCHAR PRIMARY KEY, run_id VARCHAR, tenant_id VARCHAR,
                      column_name VARCHAR, severity VARCHAR, issue_status VARCHAR, created_time TIMESTAMP, row_snapshot VARCHAR)
                    """);
            jdbc.update("""
                    INSERT INTO db_datasource_t (tid,tenant_id,db_name,db_type,is_enable,pool_cfg,is_del) VALUES
                    ('source-a','tenant-a','验证数据源','mysql',1,'validation-sensitive-value',0),
                    ('source-b','tenant-b','外租户数据源','mysql',1,NULL,0),
                    ('source-deleted','tenant-a','已删除数据源','mysql',1,NULL,1)
                    """);
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setDialectAdapter(new DialectAdapter());
            db.setResultProvider(new DefaultResultProvider("technical-read-validation"));
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
            db.setSqlInterceptors(List.of());
            db.setNamedTableInterceptors(List.of());
            String body = CanonicalMagicSources.byId(RESOURCE_ID)
                    .replaceAll("(?m)^import (?!cn\\.hutool\\.)[^\\r\\n]*\\R", "");
            script = MagicScript.create(body, null);
        }

        void table(String id, String tenant, String dataSource, String name, String domain, int deleted) {
            jdbc.update("""
                    INSERT INTO db_table_t(tid,tenant_id,datasource_id,table_name,resource_domain_id,is_del) VALUES (?,?,?,?,?,?)
                    """, id, tenant, dataSource, name, domain, deleted);
        }

        Object run(Map<String, Object> input) {
            source.reads = 0;
            var session = new Session();
            Function<Object[], Object> user = args -> Map.of("userName", "验证账号", "orgId", "org-a");
            var vars = Map.of("input", input, "db", db, "tenantRuntime", session, "tableRead", tableRead,
                    "StpUtil", session, "currentUser", user);
            return TenantContext.call("tenant-a", () -> script.execute(new MagicScriptContext(vars)));
        }

        Map<String, Object> result(Map<String, Object> input) {
            return StructuredValues.objectMap(assertInstanceOf(Map.class, run(input)));
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        private int reads;

        CountedSource() {
            super("jdbc:h2:mem:technical_read_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
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
                                            ? "jdbc:mysql://technical-read-validation" : invoke(metadata, metaMethod, metaArgs));
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
