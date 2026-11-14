package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

/** Options use active tenant records and ignore the datasource management menu scope. */
class TargetDatasourceOptionsScopeMagicTest {
    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void optionsOnlyIncludeVisibleDatasourcesFromTheCurrentTenant(int size) throws Exception {
        var fixture = new Fixture();
        var jdbc = fixture.jdbc;
        jdbc.execute("create table sym_dict_t(tid varchar(64),tenant_id varchar(64),dict_code varchar(64),parent_id varchar(64),is_del int,sort_no int,dict_name varchar(64))");
        jdbc.execute("create table dwm_center_layer_source_t(tenant_id varchar(64),target_type varchar(32),target_id varchar(64),is_del int,updated_time timestamp,datasource_id varchar(64))");
        jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(64),is_del int,show_connect int,db_name varchar(64),db_type varchar(32),asset_status varchar(32))");
        jdbc.update("insert into sym_dict_t values('root','police','dataSourceType','0',0,0,'source types')");
        jdbc.update("insert into sym_dict_t values('ods','police','ods','root',0,0,'ODS')");
        jdbc.update("insert into sym_dict_t values('domain-a','police','yuanshi','ods',0,1,'原始库')");
        jdbc.update("insert into sym_dict_t values('domain-b','police','huiju','ods',0,2,'汇聚库')");
        jdbc.update("insert into dwm_center_layer_source_t values('police','domain','domain-a',0,current_timestamp,'visible-db')");
        jdbc.update("insert into dwm_center_layer_source_t values('police','domain','domain-b',0,current_timestamp,'hidden-db')");
        jdbc.update("insert into db_datasource_t values('visible-db','police',0,1,'visible','mysql','active')");
        jdbc.update("insert into db_datasource_t values('hidden-db','police',0,1,'hidden','hive','active')");
        for (int i = 1; i < size; i++) {
            String domainId = "domain-extra-" + i;
            String datasourceId = "visible-db-" + i;
            jdbc.update("insert into sym_dict_t values(?,'police','huiju','ods',0,?,'额外汇聚库')", domainId, i + 2);
            jdbc.update("insert into dwm_center_layer_source_t values('police','domain',?,0,current_timestamp,?)", domainId, datasourceId);
            jdbc.update("insert into db_datasource_t values(?,'police',0,1,'visible','mysql','active')", datasourceId);
        }
        // A corrupt cross-tenant binding must not expand the current tenant's choices.
        jdbc.update("insert into sym_dict_t values('foreign-domain','police','yuanshi','ods',0,999,'跨租户绑定')");
        jdbc.update("insert into dwm_center_layer_source_t values('police','domain','foreign-domain',0,current_timestamp,'foreign-db')");
        jdbc.update("insert into db_datasource_t values('foreign-db','marine',0,1,'foreign','mysql','active')");

        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(Map.of()));
        assertEquals(size+1, response.get("total"));
        List<?> choices = assertInstanceOf(List.class, response.get("list"));
        assertEquals("visible-db", ((Map<?, ?>) choices.getFirst()).get("value"));
        assertTrue(choices.stream().allMatch(row -> ((Map<?, ?>) row).get("value").toString().startsWith("visible-db") || ((Map<?,?>)row).get("value").equals("hidden-db")));
        assertEquals(5, fixture.source.queries, "20/100 choices must use five fixed bulk queries");
        assertEquals(0, fixture.scope.calls, "Datasource options do not invoke metadata-menu scope");
        System.out.println("ODS target options: " + size + " choices, " + fixture.source.queries + " SQL reads");
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void distributionOptionsUseDepartmentSourcesAndExcludeWarehouseAndOtherScopes(int size) throws Exception {
        var fixture = new Fixture();
        var jdbc = fixture.jdbc;
        fixture.distributionTables();
        jdbc.update("insert into rm_org_t values('dept-1','police','科信总队',2,0),('dept-2','police','情指中心',2,0),('dept-3','police','运行保障',1,0),('foreign-org','marine','其他租户部门',3,0),('deleted-org','police','已删除部门',4,1)");
        jdbc.update("insert into sym_dict_t values('root','police','dataSourceType','0',0),('ods','police','ods','root',0),('domain','police','huiju','ods',0)");
        for (int i = 0; i < size; i++) {
            jdbc.update("insert into db_datasource_t values(?,'police',0,1,?,'postgresql',2,?,'owner')",
                    "visible-db-" + i, "业务数据库" + (i % 5), i % 2 == 0 ? "dept-1" : "dept-2");
        }
        jdbc.update("insert into db_datasource_t values('hidden-db','police',0,1,null,'mysql',2,'DEPT-3','owner'),"
                + "('foreign-db','marine',0,1,'其他租户库','mysql',2,'dept-1','owner'),"
                + "('foreign-org-db','police',0,1,'错绑部门库','mysql',2,'foreign-org','owner'),"
                + "('deleted-org-db','police',0,1,'失效部门库','mysql',2,'deleted-org','owner'),"
                + "('warehouse-domain','police',0,1,'汇聚库','hive',2,'dept-1','owner'),"
                + "('warehouse-layer','police',0,1,'ODS主库','mysql',2,'dept-1','owner'),"
                + "('unregistered','police',0,1,'未登记库','mysql',0,'dept-1','owner'),"
                + "('deleted-db','police',1,1,'已删除库','mysql',2,'dept-1','owner'),"
                + "('incomplete','police',0,1,'登记中库','mysql',1,'dept-1','owner'),"
                + "('no-connection','police',0,0,'无连接库','mysql',2,'dept-1','owner')");
        jdbc.update("insert into dwm_center_layer_source_t values('police','domain','domain',0,'warehouse-domain'),('police','layer','ods',0,'warehouse-layer'),"
                + "('marine','domain','domain',0,'visible-db-0'),('police','domain','domain',1,'visible-db-1')");
        Map<?, ?> response = assertInstanceOf(Map.class, fixture.run(Map.of("taskCategory", "distribution", "tenantId", "marine")));
        var choices = StructuredValues.objectRows(response.get("list"));
        assertEquals(size + 3, choices.size());
        assertEquals(List.of("DEPT-3", "dept-1", "dept-2"),
                StructuredValues.objectRows(response.get("options")).stream().map(row -> row.get("value")).toList());
        assertTrue(choices.stream().allMatch(choice -> {
            String id = choice.get("value").toString();
            return id.startsWith("visible-db-") || List.of("incomplete", "no-connection", "hidden-db").contains(id);
        }));
        assertEquals(2L, choices.stream().filter(row -> Boolean.TRUE.equals(row.get("disabled"))).count());
        assertTrue(choices.stream().filter(row -> row.get("value").toString().startsWith("visible-db-"))
                .allMatch(row -> "postgresql".equals(row.get("dbType"))));
        assertEquals(3, fixture.source.queries, "20/100 distribution choices use three fixed bulk queries");
        assertEquals(0, fixture.scope.calls, "Current-tenant options do not invoke metadata-menu scope");
        var expectedOrder = jdbc.queryForList("""
                SELECT d.tid FROM db_datasource_t d JOIN rm_org_t o ON o.id=d.org_id
                WHERE d.tid LIKE 'visible-db-%' OR d.tid IN ('hidden-db','incomplete','no-connection')
                ORDER BY o.sort_num,o.id,d.db_name,d.tid
                """, String.class);
        assertEquals(expectedOrder, choices.stream().map(row -> row.get("tid")).toList());
        assertEquals("hidden-db", choices.getFirst().get("label"));
        System.out.println("Distribution target options: " + size + " choices, 3 SQL reads");
    }

    @Test
    void fullDistributionWindowAllowsOneThousandAndRejectsOverflowBeforeSorting() throws Exception {
        var fixture = new Fixture();
        fixture.distributionTables();
        var body = Map.<String, Object>of("taskCategory", "distribution");
        assertEquals(0, assertInstanceOf(Map.class, fixture.run(body)).get("total"));
        assertEquals(1, fixture.source.queries);

        fixture.jdbc.update("INSERT INTO rm_org_t VALUES ('dept-1','police','部门',1,0)");
        fixture.jdbc.update("""
                INSERT INTO db_datasource_t
                SELECT CONCAT('source-',seq_no),'police',0,1,'业务库','mysql',2,'dept-1','owner'
                FROM SYSTEM_RANGE(1,1000) AS seq(seq_no)
                """);
        assertEquals(1000, assertInstanceOf(Map.class, fixture.run(body)).get("total"));
        assertEquals(4, fixture.source.queries);

        fixture.jdbc.update("INSERT INTO db_datasource_t VALUES ('source-overflow','police',0,1,'额外库','mysql',2,'dept-1','owner')");
        var result = assertInstanceOf(ExitValue.class, fixture.run(body));
        assertEquals(400, ((Number) result.getValues()[0]).intValue());
        assertEquals(2, fixture.source.queries);
    }

    public static class Scope {
        int calls;
        public String id() { return "police"; }
        public List<Map<String, Object>> visibleDataSourcesFor(String resource, Collection<Map<String, Object>> rows) {
            assertEquals("MENU:2081000000000000002", resource);
            calls++;
            assertTrue(rows.stream().allMatch(row -> "police".equals(row.get("tenantId"))));
            return rows.stream().filter(row -> !"hidden-db".equals(row.get("tid"))).toList();
        }
    }

    private static final class Fixture {
        final CountedSource source = new CountedSource();
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final Scope scope = new Scope();
        final SQLModule db;
        final StructuredTableReadMagicModule tableRead = new StructuredTableReadMagicModule(jdbc);

        Fixture() {
            var sources = new MagicDynamicDataSource();
            sources.setDefault(source);
            db = new SQLModule(sources);
            db.setDataSourceNode(sources.getDataSource());
            db.setDialectAdapter(new DialectAdapter());
            var columns = new ColumnMapperAdapter();
            columns.add(new DefaultColumnMapperProvider());
            columns.add(new CamelColumnMapperProvider());
            columns.setDefault("camel");
            db.setColumnMapperProvider(columns);
            db.setSqlInterceptors(List.of());
            db.setNamedTableInterceptors(List.of());
            db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
            db.setRowMapColumnMapper(value -> value);
        }

        void distributionTables() {
            jdbc.execute("create table sym_dict_t(tid varchar(64),tenant_id varchar(64),dict_code varchar(64),parent_id varchar(64),is_del int)");
            jdbc.execute("create table dwm_center_layer_source_t(tenant_id varchar(64),target_type varchar(32),target_id varchar(64),is_del int,datasource_id varchar(64))");
            jdbc.execute("create table rm_org_t(id varchar_ignorecase(64),tenant_id varchar(64),name varchar(64),sort_num int,deleted int)");
            jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(64),is_del int,show_connect int,db_name varchar(64),db_type varchar(32),asset_status int,org_id varchar_ignorecase(64),created_by varchar(64))");
        }

        Object run(Map<String, Object> body) throws Exception {
            String classifier = CanonicalMagicSources.byPath("function/01.通用函数/20.ODS来源库分类.ms",
                    "d2138de2de28456aa57d9ade2305eed7").replaceAll("(?m)^import [^\\r\\n]+\\R", "");
            String options = CanonicalMagicSources.byId("ods_target_datasource_options_01")
                    .replaceAll("(?m)^import [^\\r\\n]+\\R", "");
            MagicScript script = MagicScript.create("var odsSourceClassifier = () => {\n" + classifier + "\n};\n" + options, null);
            var values = Map.of("db", db, "tableRead", tableRead, "tenantRuntime", scope, "dataScope", scope, "body", body);
            source.queries = 0;
            return TenantContext.call(scope.id(), () -> script.execute(new MagicScriptContext(values)));
        }
    }

    private static final class CountedSource extends DriverManagerDataSource {
        int queries;

        CountedSource() {
            super("jdbc:h2:mem:target_options_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        }

        @Override public Connection getConnection() throws SQLException {
            var actual = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getMetaData")) {
                            var metadata = actual.getMetaData();
                            return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                    (ignored, metaMethod, metaArgs) -> metaMethod.getName().equals("getURL")
                                            ? "jdbc:mysql://target-options-validation" : invoke(metadata, metaMethod, metaArgs));
                        }
                        if (method.getName().equals("prepareStatement")
                                && args[0].toString().stripLeading().regionMatches(true, 0, "select", 0, 6)) {
                            queries++;
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
