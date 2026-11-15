package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;

import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.config.TenantDatabaseProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantRoutingDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class NifiCatalogTaskAssociationMagicTest {
    private static final String TENANT = "tenant";
    private static final String CATALOG = "catalog";
    private static final String SOURCE = "source";
    private static final String TABLE = "table";
    private static final String PIPELINE = "new-flow";

    private Fixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new Fixture(new MySqlModeDataSource());
    }

    @ParameterizedTest
    @CsvSource({"inline, , ", "registered, registered-db, registered-table", "selected, selected-db, "})
    void magicPreservesPermanentTargetReferences(String kind, String expectedDb, String expectedTable) throws Exception {
        var db = fixture.withActualTaskAssociation();
        var config = new LinkedHashMap<String, Object>(Map.of("jdbcUrl", "jdbc:mysql://fixture.invalid/test", "table", "copy"));
        if ("registered".equals(kind)) {
            config.put("registeredDatasourceId", "registered-db");
            config.put("targetTableId", "registered-table");
        } else if ("selected".equals(kind)) {
            config.put("selectedDatabaseId", "selected-db");
        }
        var dsl = new Pipeline.Dsl(1, List.of(new Pipeline.Node("sink", "sink.jdbc", "目标", "sink", 0, 0, config)), List.of());
        try (var scope = TenantContext.use(TENANT)) {
            fixture.executeTask(db, Map.of("rawCatalogTid", " undefined ", "pipelineId", PIPELINE,
                    "pipelineName", "流程", "dsl", dsl));
            assertThat(fixture.jdbc.queryForMap("select * from data_access_agg_task_t where pipeline_id=?", PIPELINE))
                    .containsEntry("target_db_id", expectedDb).containsEntry("target_table_id", expectedTable)
                    .containsEntry("data_catalog_id", null);
        }
    }

    private static final class MySqlModeDataSource extends DriverManagerDataSource {
        MySqlModeDataSource() {
            super("jdbc:h2:mem:canvas-pull-" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        try {
                            if (!"getMetaData".equals(method.getName())) return method.invoke(connection, args);
                            // Select the regular MySQL pagination dialect while keeping actual H2 transactions.
                            var metadata = connection.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (ignored, operation, parameters) -> {
                                        try {
                                            return "getURL".equals(operation.getName()) ? "jdbc:mysql://localhost/validation"
                                                    : operation.invoke(metadata, parameters);
                                        } catch (InvocationTargetException error) {
                                            throw error.getCause();
                                        }
                                    });
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                    });
        }
    }

    static final class Fixture {
        final JdbcTemplate jdbc;
        private final Map<String, Object> modules;
        private MagicScript taskScript;

        Fixture(DataSource physical) throws Exception {
            var properties = new TenantDatabaseProperties();
            properties.setTenantBindings(Map.of(TENANT, "tenant-fixture", "other-tenant", "tenant-fixture"));
            var routing = new TenantRoutingDataSource(properties);
            routing.setTargetDataSources(Map.of("tenant-fixture", physical));
            routing.setDefaultTargetDataSource(physical);
            routing.afterPropertiesSet();
            jdbc = new JdbcTemplate(routing);
            modules = Map.of("tenantRuntime", new TenantMagicModule(null),
                    "tableRead", new StructuredTableReadMagicModule(jdbc),
                    "tableWrite", new StructuredTableWriteMagicModule(jdbc));
            jdbc.execute("""
                    create table data_access_agg_task_t (tid varchar(40) primary key, tenant_id varchar(40),
                        data_catalog_id varchar(40), pipeline_id varchar(40), is_del int, created_time timestamp(6),
                        task_name varchar(255), task_desc varchar(500), task_status int,
                        source_db_id varchar(64), source_table_id varchar(32), source_table_primary_key varchar(255),
                        source_table_increment_key varchar(255), target_db_id varchar(64), target_table_id varchar(32),
                        target_table_primary_key varchar(255), updated_time timestamp(6),
                        created_by varchar(32), updated_by varchar(32))
                    """);
            jdbc.execute("""
                    create table db_datasource_t (tid varchar(40) primary key, tenant_id varchar(40), is_del int,
                        db_name varchar(100), db_type varchar(40), pool_cfg text,
                        username varchar(100), password varchar(100), jdbc_url varchar(100))
                    """);
            jdbc.execute("""
                    create table db_table_t (tid varchar(40) primary key, tenant_id varchar(40), is_del int,
                        datasource_id varchar(40), table_name varchar(100), table_name_en varchar(100),
                        table_name_cn varchar(100), table_comment varchar(100), table_type varchar(40),
                        source_catalog_id varchar(32))
                    """);
        }

        private SQLModule withActualTaskAssociation() throws Exception {
            jdbc.execute("create table da_catalog_t(tid varchar(32), tenant_id varchar(40), is_del int, source_table_id varchar(32))");
            jdbc.execute("create table da_prop_t (tid varchar(32), tenant_id varchar(40), is_del int, parent_id varchar(32),"
                    + " prop_name varchar(40), prop_value varchar(100), created_time timestamp(6), updated_time timestamp(6))");
            jdbc.update("insert into da_catalog_t(tid, tenant_id, is_del) values (?, ?, 0)", CATALOG, TENANT);
            jdbc.update("insert into da_prop_t(tid, tenant_id, is_del, parent_id, prop_name, prop_value)"
                    + " values ('source-table-prop', ?, 0, ?, 'sourceTableId', ?), ('source-db-prop', ?, 0, ?, 'dbId', ?)",
                    TENANT, CATALOG, TABLE, TENANT, CATALOG, SOURCE);
            var dynamic = new MagicDynamicDataSource();
            dynamic.setDefault(jdbc.getDataSource());
            var db = new SQLModule(dynamic);
            db.setDataSourceNode(dynamic.getDataSource());
            db.setResultProvider(new DefaultResultProvider("validation"));
            db.setDialectAdapter(new DialectAdapter());
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            db.setColumnMapperProvider(columns);
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(column -> column);
            db.setNamedTableInterceptors(List.of((mode, table) -> {
                table.column("created_by", "must-not-overwrite");
                table.column("updated_by", "must-not-overwrite");
            }));
            db.setSqlInterceptors(List.of());
            String canonical = CanonicalMagicSources.byPath("function/01.通用函数/27.NiFi画布接入任务关联.ms",
                    "fbbbebbeee9346e1813e9b1bf635eb09");
            MagicScript.create(canonical, null).compile();
            var task = MagicScript.create(canonical.replaceAll("(?m)^import (tenantRuntime|tableRead|tableWrite)\\R", ""), null);
            taskScript = task;
            return db;
        }

        private void executeTask(SQLModule db, Map<String, Object> values) {
            var bindings = new LinkedHashMap<String, Object>(modules);
            bindings.put("db", db);
            bindings.putAll(values);
            taskScript.execute(new MagicScriptContext(bindings));
        }

    }
}
