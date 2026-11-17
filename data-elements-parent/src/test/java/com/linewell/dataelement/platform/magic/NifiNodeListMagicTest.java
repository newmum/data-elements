package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataservice.flowserve.module.FlowHttpModule;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.platform.tenant.infrastructure.datasource.TenantDataSourceRegistry;
import com.linewell.dataelement.utils.LocalCache;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
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
        assertEquals("unknown", ((Map<?, ?>) rows.getFirst()).get("healthStatus"));
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

    @Test
    void serviceHealthUsesOnlyTheSavedCurrentTenantTarget() throws Exception {
        var fixture = new Fixture(0);
        fixture.jdbc.execute("create table service_node_t(tid varchar(64),tenant_id varchar(64),is_del int,local_node int,base_url varchar(255),management_url varchar(255))");
        var paths = new ArrayList<String>();
        var upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            upstream.createContext("/", request -> {
                try (request) {
                    paths.add(request.getRequestMethod() + " " + request.getRequestURI().getPath());
                    request.getResponseHeaders().set("Location", "/must-not-follow");
                    request.sendResponseHeaders(302, -1);
                }
            });
            upstream.start();
            String base = "http://127.0.0.1:" + upstream.getAddress().getPort();
            fixture.jdbc.update("insert into service_node_t values('own','police',0,0,?,null),('foreign','other',0,0,?,null)", base, base);
            var unauthorized = fixture.serviceHealth(Map.of("action", "health", "nodeId", "foreign",
                    "tenantId", "other", "localNode", true));
            assertEquals(404, assertInstanceOf(ExitValue.class, unauthorized).getValues()[0]);
            assertTrue(paths.isEmpty());

            fixture.counter.reads = 0;
            var response = assertInstanceOf(ResponseEntity.class, fixture.serviceHealth(Map.of("action", "health",
                    "nodeId", "own", "managementUrl", base + "/caller-injected", "localNode", true)));
            var result = assertInstanceOf(Map.class, assertInstanceOf(CommonResponse.class, response.getBody()).getData());
            assertEquals(false, result.get("success"));
            assertEquals(302, result.get("status"));
            assertEquals(List.of("GET /api/v1/runtime/health"), paths);
            assertEquals(1, fixture.counter.reads);
        } finally {
            upstream.stop(0);
        }
    }

    @Test
    void savesAndReconnectsToAnAnonymousDefaultNodeAndListsItsLastHealthCheck() throws Exception {
        var fixture = new Fixture(0);
        var exchanges = Collections.synchronizedList(new ArrayList<String>());
        var rejectConnection = new AtomicBoolean();
        var upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            upstream.createContext("/nifi-api/", request -> {
                try (request) {
                    request.getRequestBody().readAllBytes();
                    exchanges.add(request.getRequestMethod() + " " + request.getRequestURI().getPath()
                            + " Authorization=" + request.getRequestHeaders().getFirst("Authorization"));
                    byte[] response = "{\"id\":\"validation\",\"component\":{\"id\":\"validation\"}}".getBytes(StandardCharsets.UTF_8);
                    request.getResponseHeaders().set("Content-Type", "application/json");
                    request.sendResponseHeaders(rejectConnection.get() ? 503 : 200, response.length);
                    request.getResponseBody().write(response);
                }
            });
            upstream.start();
            var form = Map.<String, Object>of("nodeName", "validation", "nodeCode", "validation", "networkCode", "GAW",
                    "baseUrl", "http://127.0.0.1:" + upstream.getAddress().getPort(), "authUsername", "", "authPassword", "");
            String id = (String) assertInstanceOf(Map.class, fixture.save(form)).get("tid");
            assertEquals(1, fixture.jdbc.queryForObject("select is_default from nifi_node_t where tid=?", Integer.class, id));
            var registry = mock(TenantDataSourceRegistry.class);
            when(registry.dataSourceForTenant("police")).thenReturn(fixture.jdbc.getDataSource());
            var resolver = new NifiNodeRuntimeResolver(registry, new TenantProperties());
            var client = new NifiClient(resolver, new ObjectMapper());
            fixture.resolver = resolver;
            fixture.client = client;
            var probeRequest = Map.<String, Object>of("action", "health", "nodeType", "sync", "nodeId", id);
            try (var tenant = TenantContext.use("police")) {
                var forbidden = fixture.probe(Map.of("action", "health", "nodeType", "sync", "nodeId", "foreign", "tenantId", "foreign"));
                assertEquals(false, forbidden.get("success"));
                assertTrue(exchanges.isEmpty());
                var success = fixture.probe(probeRequest);
                assertEquals(true, success.get("success"));
                var initialListRow = fixture.firstNode();
                assertEquals("reachable", initialListRow.get("healthStatus"));
                assertEquals(success.get("message"), initialListRow.get("healthMessage"));
                assertEquals(success.get("healthTime"), initialListRow.get("healthTime"));
                assertEquals(((Number) success.get("latencyMs")).longValue(), ((Number) initialListRow.get("healthLatencyMs")).longValue());
                fixture.scope.tenant = "foreign";
                var otherListRow = fixture.firstNode();
                assertEquals("foreign", otherListRow.get("tid"));
                assertEquals("unknown", otherListRow.get("healthStatus"));
                fixture.scope.tenant = "police";
                try (var node = client.bindRuntimeNode()) {
                    assertEquals("validation", client.createProcessGroup("root", "validation", 0, 0).id());
                }
                client.getRuntimeRootStatus(resolver.resolve());
                rejectConnection.set(true);
                var failure = fixture.probe(probeRequest);
                assertEquals(false, failure.get("success"));
                var failedListRow = fixture.firstNode();
                assertEquals("unreachable", failedListRow.get("healthStatus"));
                assertEquals(failure.get("healthTime"), failedListRow.get("healthTime"));
                fixture.jdbc.update("update nifi_node_t set updated_time=? where tid=?", Timestamp.valueOf("2030-11-16 00:00:00.123456"), id);
                assertEquals("unknown", fixture.firstNode().get("healthStatus"));
            }
            assertEquals(List.of("GET /nifi-api/flow/process-groups/root Authorization=null",
                    "POST /nifi-api/process-groups/root/process-groups Authorization=null",
                    "GET /nifi-api/flow/process-groups/root/status Authorization=null",
                    "GET /nifi-api/flow/process-groups/root Authorization=null"), exchanges);
        } finally {
            upstream.stop(0);
        }
    }

    @Test
    void credentialValidationPreservesTheOldDefaultAndMaskedPassword() throws Exception {
        var fixture = new Fixture(0);
        var form = new LinkedHashMap<String, Object>(Map.of("nodeName", "validation", "nodeCode", "validation",
                "networkCode", "GAW", "baseUrl", "http://example.test", "authUsername", "validation", "authPassword", "test-password"));
        String id = (String) assertInstanceOf(Map.class, fixture.save(form)).get("tid");
        var invalid = new LinkedHashMap<>(form);
        invalid.put("nodeCode", "invalid");
        invalid.put("baseUrl", "https://example.test");
        invalid.put("isDefault", true);
        invalid.put("authUsername", "");
        invalid.put("authPassword", "");
        assertEquals(400, assertInstanceOf(ExitValue.class, fixture.save(invalid)).getValues()[0]);
        invalid.put("baseUrl", "http://example.test");
        invalid.put("authUsername", "validation");
        assertEquals(400, assertInstanceOf(ExitValue.class, fixture.save(invalid)).getValues()[0]);
        assertEquals(1, fixture.jdbc.queryForObject("select is_default from nifi_node_t where tid=?", Integer.class, id));

        form.put("tid", id);
        form.put("isDefault", true);
        form.put("authPassword", "");
        assertInstanceOf(Map.class, fixture.save(form));
        assertEquals("test-password", fixture.jdbc.queryForObject("select auth_password from nifi_node_t where tid=?", String.class, id));
        form.put("authUsername", "");
        assertInstanceOf(Map.class, fixture.save(form));
        assertNull(fixture.jdbc.queryForObject("select auth_password from nifi_node_t where tid=?", String.class, id));
    }

    static class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final Counter counter = new Counter();
        final Scope scope = new Scope();
        NifiNodeRuntimeResolver resolver;
        NifiClient client;
        Fixture(int size) {
            var source = new DriverManagerDataSource("jdbc:h2:mem:nifi_nodes_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("create table sym_dict_t(tid varchar(64),tenant_id varchar(64),dict_code varchar(32),parent_id varchar(64),dict_name varchar(64),icon varchar(64),is_del int)");
            jdbc.execute("create table nifi_node_t(tid varchar(64),tenant_id varchar(64),is_del int,node_name varchar(64),node_code varchar(64),network_type varchar(64),network_code varchar(32),is_default int,updated_time timestamp,auth_password varchar(64))");
            jdbc.update("insert into sym_dict_t values('root','police','SSWL','0','网络',null,0),('gaw','police','GAW','root','公安网','network-icon',0),('foreign-root','foreign','SSWL','0','其他网络',null,0)");
            for (int i = 0; i < size; i++) jdbc.update("insert into nifi_node_t values(?,'police',0,?,?,'公安网',null,0,current_timestamp,'must-not-return')", "own-"+i, "节点"+String.format("%03d",i), "code-"+i);
            jdbc.update("insert into nifi_node_t values('foreign','foreign',0,'foreign','foreign','公安网',null,0,current_timestamp,'secret'),('deleted','police',1,'deleted','deleted','公安网',null,0,current_timestamp,'secret')");
            jdbc.execute("alter table nifi_node_t add (base_url varchar(255), root_process_group_id varchar(128), auth_username varchar(64), insecure_tls int, jdbc_driver_locations varchar(4096), remark varchar(500), enabled int, created_time timestamp)");
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
        Map<?, ?> firstNode() throws Exception {
            return assertInstanceOf(Map.class, ((List<?>) run(Map.of()).get("list")).getFirst());
        }
        Map<?, ?> probe(Map<String, Object> body) throws Exception {
            var response = assertInstanceOf(ResponseEntity.class, runRaw(body));
            assertEquals(200, response.getStatusCode().value());
            return assertInstanceOf(Map.class, assertInstanceOf(CommonResponse.class, response.getBody()).getData());
        }
        Object runRaw(Map<String, Object> body) throws Exception {
            var bindings = new LinkedHashMap<String, Object>();
            bindings.put("db",db); bindings.put("tenantRuntime",scope); bindings.put("tableSort",new TableSortMagicModule()); bindings.put("body",body);
            bindings.put("ObjectMapper", ObjectMapper.class);
            bindings.put("Map", Map.class);
            bindings.put("Timestamp", Timestamp.class);
            bindings.put("LocalCache", LocalCache.class);
            bindings.put("SimpleDateFormat", SimpleDateFormat.class);
            bindings.put("CommonResponse", CommonResponse.class);
            bindings.put("ResponseEntity", ResponseEntity.class);
            bindings.put("nodeResolver", resolver);
            bindings.put("nifi", client);
            var script = CanonicalMagicSources.byId("ods_nifi_node_page_01").replaceAll("(?m)^import [^\\r\\n]*\\r?\\n", "");
            return MagicScript.create(script,null).execute(new MagicScriptContext(bindings));
        }
        Object save(Map<String, Object> body) throws Exception {
            var script = CanonicalMagicSources.byId("ods_nifi_node_save_01").replaceFirst("(?m)^import tenantRuntime\\R", "");
            return MagicScript.create(script, null).execute(new MagicScriptContext(
                    Map.of("db", db, "tenantRuntime", scope, "body", body)));
        }
        Object serviceHealth(Map<String, Object> body) throws Exception {
            var script = CanonicalMagicSources.byId("ods_service_node_page_01").replaceAll("(?m)^import [^\\r\\n]*\\r?\\n", "");
            return MagicScript.create(script, null).execute(new MagicScriptContext(Map.of(
                    "db", db, "tenantRuntime", scope, "httpx", new FlowHttpModule(null, new ObjectMapper()), "body", body,
                    "URI", URI.class, "CommonResponse", CommonResponse.class, "ResponseEntity", ResponseEntity.class)));
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
