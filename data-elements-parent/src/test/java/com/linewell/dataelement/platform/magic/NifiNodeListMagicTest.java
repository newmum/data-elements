package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.datasource.model.MagicDynamicDataSource;
import org.ssssssss.magicapi.modules.db.BoundSql;
import org.ssssssss.magicapi.modules.db.ColumnMapperAdapter;
import org.ssssssss.magicapi.modules.db.SQLModule;
import org.ssssssss.magicapi.modules.db.inteceptor.SQLInterceptor;
import org.ssssssss.magicapi.modules.db.provider.CamelColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.provider.DefaultColumnMapperProvider;
import org.ssssssss.magicapi.modules.db.dialect.DialectAdapter;
import org.ssssssss.magicapi.modules.db.dialect.MySQLDialect;
import org.ssssssss.magicapi.core.interceptor.DefaultResultProvider;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

/** Execute the editable script against the actual Magic SQL module, including pagination. */
class NifiNodeListMagicTest {
    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void boundedListPreservesAllCurrentTenantNodesWithFixedSql(int size) throws Exception {
        var fixture = new Fixture(size);
        var response = fixture.run(Map.of("tenantId", "foreign", "sortField", "nodeName", "sortDir", "asc"));
        var rows = (List<?>) response.get("list");
        assertEquals(size, response.get("total"));
        assertEquals(size, rows.size());
        assertTrue(rows.stream().allMatch(row -> ((Map<?, ?>) row).get("tid").toString().startsWith("own-")));
        assertEquals("公安网", ((Map<?, ?>) rows.getFirst()).get("networkName"));
        assertEquals("GAW", ((Map<?, ?>) rows.getFirst()).get("networkCode"));
        assertEquals("network-icon", ((Map<?, ?>) rows.getFirst()).get("networkIcon"));
        assertTrue(rows.stream().allMatch(row -> !((Map<?, ?>) row).containsKey("authPassword")));
        assertEquals(4, fixture.counter.reads, "20/100 nodes use two dictionary reads and one bounded page/count");
        System.out.println("Node configuration: " + size + " nodes, 1 list request, 4 SQL; tenant/deleted exclusions verified");
    }

    @Test
    void keywordAndEmptyResultsKeepTheResponseContract() throws Exception {
        var fixture = new Fixture(20);
        var response = fixture.run(Map.of("keyword", "节点019"));
        assertEquals(1, response.get("total"));
        assertEquals("own-19", ((Map<?, ?>) ((List<?>) response.get("list")).getFirst()).get("tid"));
        assertEquals(0, fixture.run(Map.of("keyword", "不存在")).get("total"));
        fixture.jdbc.update("delete from nifi_node_t where tenant_id='police'");
        assertEquals(List.of(), fixture.run(Map.of()).get("list"));
    }

    @Test
    void foreignAndDeletedNodesDoNotConsumeTheCurrentTenantLimit() throws Exception {
        var fixture = new Fixture(1000);
        assertEquals(1000, fixture.run(Map.of()).get("total"));
        assertEquals(4, fixture.counter.reads);
    }

    @Test
    void excessNodesAreReportedWithoutReturningATruncatedList() throws Exception {
        var fixture = new Fixture(1001);
        var exit = assertInstanceOf(ExitValue.class, fixture.runRaw(Map.of()));
        assertEquals(422, exit.getValues()[0]);
        assertEquals(4, fixture.counter.reads);
    }

    @Test
    void missingSessionTenantIsRejectedBeforeAnyRead() throws Exception {
        var fixture = new Fixture(20);
        fixture.scope.tenant = "";
        var exit = assertInstanceOf(ExitValue.class, fixture.runRaw(Map.of("tenantId", "police")));
        assertEquals(401, exit.getValues()[0]);
        assertEquals(0, fixture.counter.reads);
    }

    static class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final Counter counter = new Counter();
        final Scope scope = new Scope();
        Fixture(int size) {
            var source = new DriverManagerDataSource("jdbc:h2:mem:nifi_nodes_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("create table sym_dict_t(tid varchar(64),tenant_id varchar(64),dict_code varchar(32),parent_id varchar(64),dict_name varchar(64),icon varchar(64),is_del int)");
            jdbc.execute("create table nifi_node_t(tid varchar(64),tenant_id varchar(64),is_del int,node_name varchar(64),node_code varchar(64),network_type varchar(64),network_code varchar(32),is_default int,updated_time timestamp,auth_password varchar(64))");
            jdbc.update("insert into sym_dict_t values('root','police','SSWL','0','网络',null,0),('gaw','police','GAW','root','公安网','network-icon',0),('foreign-root','foreign','SSWL','0','其他网络',null,0)");
            for (int i = 0; i < size; i++) jdbc.update("insert into nifi_node_t values(?,'police',0,?,?,'公安网',null,0,current_timestamp,'must-not-return')", "own-"+i, "节点"+String.format("%03d",i), "code-"+i);
            jdbc.update("insert into nifi_node_t values('foreign','foreign',0,'foreign','foreign','公安网',null,0,current_timestamp,'secret'),('deleted','police',1,'deleted','deleted','公安网',null,0,current_timestamp,'secret')");
            var sources = new MagicDynamicDataSource(); sources.setDefault(source);
            db = new SQLModule(sources); db.setDataSourceNode(sources.getDataSource());
            var dialects = new DialectAdapter();
            dialects.add(new MySQLDialect() { @Override public boolean match(String url) { return url.contains(":h2:"); } });
            db.setDialectAdapter(dialects); db.setResultProvider(new DefaultResultProvider("nifi-node-list-test"));
            var columns = new ColumnMapperAdapter(); columns.add(new DefaultColumnMapperProvider()); columns.add(new CamelColumnMapperProvider()); columns.setDefault("camel");
            db.setColumnMapperProvider(columns); db.setColumnMapRowMapper(columns.getDefaultColumnMapRowMapper()); db.setRowMapColumnMapper(value -> value);
            db.setSqlInterceptors(List.of(counter)); db.setNamedTableInterceptors(List.of());
        }
        Map<?, ?> run(Map<String, Object> body) throws Exception {
            return assertInstanceOf(Map.class, runRaw(body));
        }
        Object runRaw(Map<String, Object> body) throws Exception {
            var bindings = new LinkedHashMap<String, Object>();
            bindings.put("db",db); bindings.put("tenantRuntime",scope); bindings.put("tableSort",new TableSortMagicModule()); bindings.put("body",body);
            var script = CanonicalMagicSources.byId("ods_nifi_node_page_01").replaceAll("(?m)^import [^\\r\\n]*\\r?\\n", "");
            return MagicScript.create(script,null).execute(new MagicScriptContext(bindings));
        }
    }
    public static class Scope { String tenant = "police"; public String id() { return tenant; } }
    static class Counter implements SQLInterceptor {
        int reads;
        @Override public void preHandle(BoundSql sql, RequestEntity request) {
            if (sql.getSql().stripLeading().toLowerCase().startsWith("select")) reads++;
        }
    }
}
