package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

/** The materialization choices must not reveal target databases outside the role's data scope. */
class TargetDatasourceOptionsScopeMagicTest {
    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void optionsOnlyIncludeVisibleDatasourcesFromTheCurrentTenant(int size) throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:target_options_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        var sources = new MagicDynamicDataSource();
        sources.setDefault(source);
        var db = new SQLModule(sources);
        db.setDataSourceNode(sources.getDataSource());
        var columns = new ColumnMapperAdapter();
        columns.add(new DefaultColumnMapperProvider());
        columns.add(new CamelColumnMapperProvider());
        columns.setDefault("camel");
        db.setColumnMapperProvider(columns);
        var counter = new QueryCounter();
        db.setSqlInterceptors(List.of(counter));
        db.setNamedTableInterceptors(List.of());
        db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
        db.setRowMapColumnMapper(value -> value);
        var jdbc = new JdbcTemplate(source);
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

        var scope = new Scope();
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("db", db);
        inputs.put("tenantRuntime", scope);
        inputs.put("dataScope", scope);
        inputs.put("body", Map.of());
        String script = CanonicalMagicSources.byId("ods_target_datasource_options_01")
                .replaceAll("(?m)^import [^\\n]*\\n", "");
        Map<?, ?> response = assertInstanceOf(Map.class,
                MagicScript.create(script, null).execute(new MagicScriptContext(inputs)));
        assertEquals(size, response.get("total"));
        var choices = assertInstanceOf(List.class, response.get("list"));
        assertEquals("visible-db", ((Map<?, ?>) choices.getFirst()).get("value"));
        assertTrue(choices.stream().allMatch(row -> ((Map<?, ?>) row).get("value").toString().startsWith("visible-db")));
        assertEquals(5, counter.queries, "20/100 choices must use five fixed bulk queries");
        assertEquals(1, scope.calls, "Resolve and apply role scope to all datasource rows once");
        System.out.println("ODS target options: " + size + " visible choices, " + counter.queries + " SQL reads; role and tenant exclusions verified");
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void distributionOptionsUseDepartmentSourcesAndExcludeWarehouseAndOtherScopes(int size) throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:distribution_options_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        var sources = new MagicDynamicDataSource();
        sources.setDefault(source);
        var db = new SQLModule(sources);
        db.setDataSourceNode(sources.getDataSource());
        var columns = new ColumnMapperAdapter();
        columns.add(new DefaultColumnMapperProvider());
        columns.add(new CamelColumnMapperProvider());
        columns.setDefault("camel");
        db.setColumnMapperProvider(columns);
        var counter = new QueryCounter();
        db.setSqlInterceptors(List.of(counter));
        db.setNamedTableInterceptors(List.of());
        db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper());
        db.setRowMapColumnMapper(value -> value);
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("create table sym_dict_t(tid varchar(64),tenant_id varchar(64),dict_code varchar(64),parent_id varchar(64),is_del int)");
        jdbc.execute("create table dwm_center_layer_source_t(tenant_id varchar(64),target_type varchar(32),target_id varchar(64),is_del int,datasource_id varchar(64))");
        jdbc.execute("create table rm_org_t(id varchar(64),tenant_id varchar(64),name varchar(64),sort_num int,deleted int)");
        jdbc.execute("create table db_datasource_t(tid varchar(64),tenant_id varchar(64),is_del int,show_connect int,db_name varchar(64),db_type varchar(32),asset_status int,org_id varchar(64),created_by varchar(64))");
        jdbc.update("insert into rm_org_t values('dept-1','police','科信总队',1,0),('dept-2','police','情指中心',2,0),('foreign-org','marine','其他租户部门',3,0),('deleted-org','police','已删除部门',4,1)");
        jdbc.update("insert into sym_dict_t values('root','police','dataSourceType','0',0),('ods','police','ods','root',0),('domain','police','huiju','ods',0)");
        for (int i = 0; i < size; i++) {
            jdbc.update("insert into db_datasource_t values(?,'police',0,1,?,'postgresql',2,?,'owner')",
                    "visible-db-" + i, "业务数据库" + i, i % 2 == 0 ? "dept-1" : "dept-2");
        }
        jdbc.update("insert into db_datasource_t values('hidden-db','police',0,1,'无权库','mysql',2,'dept-1','owner'),"
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
        var scope = new Scope();
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("db", db);
        inputs.put("tenantRuntime", scope);
        inputs.put("dataScope", scope);
        inputs.put("body", Map.of("taskCategory", "distribution", "tenantId", "marine"));
        String script = CanonicalMagicSources.byId("ods_target_datasource_options_01")
                .replaceAll("(?m)^import [^\\n]*\\n", "");
        Map<?, ?> response = assertInstanceOf(Map.class, MagicScript.create(script, null).execute(new MagicScriptContext(inputs)));
        var choices = assertInstanceOf(List.class, response.get("list"));
        assertEquals(size + 2, choices.size());
        assertEquals(2, ((List<?>) response.get("options")).size());
        assertTrue(choices.stream().allMatch(row -> {
            var choice = (Map<?, ?>) row;
            String id = choice.get("value").toString();
            return id.startsWith("visible-db-") || id.equals("incomplete") || id.equals("no-connection");
        }));
        assertEquals(2L, choices.stream().filter(row -> Boolean.TRUE.equals(((Map<?, ?>) row).get("disabled"))).count());
        assertTrue(choices.stream().filter(row -> ((Map<?, ?>) row).get("value").toString().startsWith("visible-db-"))
                .allMatch(row -> "postgresql".equals(((Map<?, ?>) row).get("dbType"))));
        assertEquals(1, counter.queries, "20/100 distribution choices use one bounded join query");
        assertEquals(1, scope.calls, "Apply the ingestion tree role scope to the whole candidate batch");
        System.out.println("Distribution target options: " + size + " selectable choices, " + counter.queries
                + " SQL reads; department, ODS bindings, tenant and role exclusions verified");
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

    private static final class QueryCounter implements SQLInterceptor {
        int queries;
        @Override public void preHandle(BoundSql sql, RequestEntity request) {
            if (sql.getSql().stripLeading().toLowerCase().startsWith("select")) queries++;
        }
    }
}
