package com.linewell.dataelement.platform.magic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.dataservice.flowserve.module.FlowHttpModule;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.magic.runtime.route.RegisteredMagicFunctionInvoker;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.tenant.config.TenantProperties;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
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
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

/** Execute the editable script against the actual Magic SQL module, including pagination. */
class NifiNodeListMagicTest {
    @Test void nodeOptionsExposeSafeReadinessWithConstantQueriesForTwentyAndHundredRows() throws Exception {
        var reads = new java.util.ArrayList<Integer>();
        for (int count : new int[]{20, 100}) {
            var fixture = new Fixture(count);
            fixture.jdbc.execute("alter table sym_dict_t add sort_no int default 0");
            fixture.jdbc.update("update nifi_node_t set enabled=1,base_url='http://anonymous.example',auth_username=null,auth_password=null where tenant_id='police'");
            fixture.counter.reads = 0;
            String source = CanonicalMagicSources.byId("ods_nifi_node_access_options_01")
                    .replaceAll("(?m)^import [^\\r\\n]*\\r?\\n", "");
            var result = assertInstanceOf(Map.class, MagicScript.create(source, null).execute(new MagicScriptContext(Map.of(
                    "body", Map.of(), "db", fixture.db, "tenantRuntime", fixture.scope,
                    "nodeConfiguration", NifiNodeConfigurationMagicTest.function()))));
            var rows = assertInstanceOf(List.class, result.get("list"));
            assertEquals(count, rows.size());
            for (Object value : rows) {
                var row = assertInstanceOf(Map.class, value);
                assertEquals(true, row.get("configurationValid"));
                assertEquals("ANONYMOUS_HTTP", row.get("authenticationMode"));
                assertFalse(row.containsKey("authPassword"));
                assertFalse(row.containsKey("authUsername"));
            }
            reads.add(fixture.counter.reads);
        }
        assertEquals(reads.getFirst(), reads.getLast());
        System.out.println("NiFi node option readiness 20/100: SQL reads=" + reads);
    }
    @Test void storedPasswordIsReadOnlyOnExplicitSingleNodeRequestAndNeverInLists() throws Exception {
        var fixture=new Fixture(1);
        var listed=fixture.firstNode();
        assertEquals(true,listed.get("hasAuthPassword"));
        assertFalse(listed.containsKey("authPassword"));assertFalse(listed.containsKey("auth_password"));
        try(var tenant=TenantContext.use("police")) {
            fixture.counter.reads=0;
            var response=assertInstanceOf(ResponseEntity.class,fixture.runRaw(Map.of("action","password","nodeId","own-0")));
            assertEquals("no-store",response.getHeaders().getFirst("Cache-Control"));
            var data=assertInstanceOf(Map.class,assertInstanceOf(CommonResponse.class,response.getBody()).getData());
            assertEquals(Map.of("nodeId","own-0","authPassword","must-not-return"),data);
            assertEquals(1,fixture.counter.reads);
            for(String id:List.of("foreign","deleted")) {
                var denied=assertInstanceOf(ExitValue.class,fixture.runRaw(Map.of("action","password","nodeId",id,"tenantId","foreign")));
                assertEquals(404,denied.getValues()[0]);
            }
            assertEquals(400,assertInstanceOf(ExitValue.class,fixture.runRaw(Map.of("action","password"))).getValues()[0]);
        }
    }
    @ParameterizedTest @ValueSource(ints={20,100})
    void samplingConfigurationsUseOneTenantScopedRead(int size) throws Exception {
        var fixture=new Fixture(size);
        fixture.jdbc.update("update nifi_node_t set enabled=1");
        fixture.jdbc.update("insert into nifi_node_t(tid,tenant_id,is_del,enabled) values('disabled','police',0,0)");
        try(var tenant=TenantContext.use("police")) {
            var rows=fixture.runtimeResolver().samplingConfigurations();
            assertEquals(size,rows.size());assertEquals(1,fixture.configurationReads);
            assertTrue(rows.stream().allMatch(row->row.get("tid").toString().startsWith("own-")));
        }
        fixture.jdbc.update("insert into nifi_node_t(tid,tenant_id,is_del,enabled) select 'overflow-'||node_index,'police',0,1 from system_range(1,101) generated_nodes(node_index)");
        try(var tenant=TenantContext.use("police")) {
            assertThrows(RuntimeException.class,()->fixture.runtimeResolver().samplingConfigurations());
        }
    }
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
        fixture.jdbc.execute("create table service_node_t(tid varchar(64),tenant_id varchar(64),is_del int,local_node int,base_url varchar(255),management_url varchar(255), updated_time timestamp default current_timestamp)");
        NodeHealthSnapshotMagicTest.columns(fixture.jdbc,"service_node_t");
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
            fixture.jdbc.update("insert into service_node_t(tid,tenant_id,is_del,local_node,base_url,management_url) values('own','police',0,0,?,null),('foreign','other',0,0,?,null)", base, base);
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
            assertEquals(List.of("GET /validation/runtime/status"), paths);
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
            var resolver = fixture.runtimeResolver();
            var client = new NifiClient(resolver, new ObjectMapper());
            fixture.resolver = resolver;
            fixture.client = client;
            var probeRequest = Map.<String, Object>of("action", "health", "nodeType", "sync", "nodeId", id);
            try (var tenant = TenantContext.use("police")) {
                var forbidden = fixture.probe(Map.of("action", "health", "nodeType", "sync", "nodeId", "foreign", "tenantId", "foreign"));
                assertEquals(false, forbidden.get("success"));
                assertEquals(1, fixture.configurationReads);
                var diagnostic = fixture.probe(Map.of("action", "diagnostics", "nodeId", "deleted"));
                assertEquals(false, diagnostic.get("success"));
                assertTrue(exchanges.isEmpty());
                var success = fixture.probe(probeRequest);
                assertEquals(true, success.get("success"));
                var metrics = fixture.probe(Map.of("action", "diagnostics", "nodeId", id));
                assertEquals(false, metrics.get("success"));
                assertEquals("NiFi 未返回系统诊断快照", metrics.get("message"));
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
                    "GET /nifi-api/system-diagnostics Authorization=null",
                    "POST /nifi-api/process-groups/root/process-groups Authorization=null",
                    "GET /nifi-api/flow/process-groups/root/status Authorization=null",
                    "GET /nifi-api/flow/process-groups/root Authorization=null"), exchanges);
        } finally {
            upstream.stop(0);
        }
    }

    @Test
    void structuredRuntimeSelectionPreservesLegacyNullsAndOneRead() throws Exception {
        var fixture = new Fixture(0);
        fixture.jdbc.update("""
                insert into nifi_node_t(tid, tenant_id, is_del, node_code, base_url, is_default, enabled, updated_time) values
                  ('fallback','police',0,'fallback','http://validation.invalid',null,1,'2026-01-06 00:00:00'),
                  ('disabled','police',0,'disabled','http://validation.invalid',2,0,'2026-01-06 00:00:00'),
                  ('older','police',0,'old','http://validation.invalid',1,1,'2026-01-01 00:00:00'),
                  ('selected','police',null,'chosen','http://validation.invalid/',1,null,'2026-01-05 00:00:00')
                """);
        var resolver = fixture.runtimeResolver();
        try (var tenant = TenantContext.use("police")) {
            assertNull(fixture.readConfiguration(null, null));
            assertEquals(0, fixture.configurationReads);
            assertEquals("selected", resolver.resolve().id());
            assertEquals(1, fixture.configurationReads);
            assertEquals("http://validation.invalid", resolver.resolveAssignedNode("fallback").baseUrl());
            assertEquals(2, fixture.configurationReads);
            assertTrue(assertThrows(IllegalStateException.class, () -> resolver.resolveAssignedNode("selected")).getMessage().contains("已停用"));
            assertTrue(assertThrows(IllegalStateException.class, () -> resolver.resolveAssignedNode("foreign")).getMessage().contains("不属于当前租户"));
            assertThrows(IllegalStateException.class, () -> resolver.resolveAssignedNode("deleted"));
            assertEquals(5, fixture.configurationReads);
        }
        assertNull(TenantContext.getTenantId());
        assertEquals("selected", resolver.resolve().id());
        assertEquals(6, fixture.configurationReads);
        assertNull(TenantContext.getTenantId());
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

    @Test
    void editingDefaultNodeUpdatesOnlyThatNodeAndSwitchingClearsOnlyThePreviousDefault() throws Exception {
        var fixture = new Fixture(3);
        fixture.jdbc.update("""
                update nifi_node_t set network_code='GAW', is_default=1, updated_time='2026-01-01 00:00:00'
                where tid in ('own-0','foreign','deleted')
                """);
        fixture.jdbc.update("update nifi_node_t set network_code='GAW',updated_time='2026-01-01 00:00:00' where tid='own-1'");
        fixture.jdbc.update("update nifi_node_t set network_code='HLW',is_default=1 where tid='own-2'");
        String otherNodes = "select * from nifi_node_t where tid in ('own-2','foreign','deleted') order by tid";
        var unchanged = fixture.jdbc.queryForList(otherNodes);
        var peer = fixture.jdbc.queryForMap("select * from nifi_node_t where tid='own-1'");
        var form = new LinkedHashMap<String, Object>(Map.of("tid", "own-0", "nodeName", "validation",
                "nodeCode", "code-0", "networkCode", "GAW", "baseUrl", "http://example.test",
                "authUsername", "validation", "isDefault", true,
                "jdbcDriverDirectory", "GAUSSDB_OPENGAUSS=/opt/nifi/validation/opengauss-jdbc.jar"));
        assertEquals("own-0", assertInstanceOf(Map.class, fixture.save(form)).get("tid"));
        assertEquals(peer, fixture.jdbc.queryForMap("select * from nifi_node_t where tid='own-1'"));
        assertEquals(unchanged, fixture.jdbc.queryForList(otherNodes));

        form.put("tid", "own-1");
        form.put("nodeCode", "code-1");
        assertEquals("own-1", assertInstanceOf(Map.class, fixture.save(form)).get("tid"));
        assertEquals(List.of(0, 1), fixture.jdbc.queryForList(
                "select is_default from nifi_node_t where tid in ('own-0','own-1') order by tid", Integer.class));
        assertEquals(unchanged, fixture.jdbc.queryForList(otherNodes));
    }

    static class Fixture {
        final JdbcTemplate jdbc;
        final SQLModule db;
        final Counter counter = new Counter();
        final Scope scope = new Scope();
        int configurationReads;
        NifiNodeRuntimeResolver resolver;
        NifiClient client;

        NifiNodeRuntimeResolver runtimeResolver() {
            var bridge = mock(RegisteredMagicFunctionInvoker.class);
            when(bridge.invoke(eq("nifi_runtime_node_read_v1"), anyMap())).thenAnswer(invocation -> {
                Map<String, Object> inputs = invocation.getArgument(1);
                return readConfiguration(inputs.get("nodeId"), inputs.get("consume"),inputs.get("allNodes"));
            });
            var properties = new TenantProperties();
            properties.setDefaultTenantId("police");
            return new NifiNodeRuntimeResolver(bridge, properties);
        }

        Object readConfiguration(Object id, Object consume) throws Exception {
            // Magic imports bind positional arguments using the published parameter order.
            // Do not replace this boundary with named arguments: that hid the health regression.
            String content=java.nio.file.Files.readString(java.nio.file.Path.of("..","magic","function","01.通用函数","78.读取NiFi运行节点配置.ms"));
            var metadata=new ObjectMapper().readTree(content.substring(0,content.indexOf("================================")));
            Object[] arguments={id,consume};
            var inputs=new LinkedHashMap<String,Object>();
            for(int i=0;i<metadata.path("parameters").size();i++)
                inputs.put(metadata.path("parameters").get(i).path("name").asText(),i<arguments.length?arguments[i]:null);
            return executeConfiguration(inputs);
        }
        Object readConfiguration(Object id, Object consume,Object allNodes) throws Exception {
            var inputs=new LinkedHashMap<String,Object>();
            inputs.put("nodeId",id);inputs.put("allNodes",allNodes);inputs.put("consume",consume);
            return executeConfiguration(inputs);
        }
        Object executeConfiguration(Map<String,Object> inputs) throws Exception {
            var actual = CanonicalMagicSources.byPath("function/01.通用函数/78.读取NiFi运行节点配置.ms",
                    "nifi_runtime_node_read_v1").replaceAll("(?m)^import (?:tableRead|tenantRuntime)\\R", "");
            actual = actual.replace("import '@/common/nifinodeconfig' as nodeConfiguration", "");
            var bindings = new LinkedHashMap<String, Object>();
            bindings.putAll(inputs);
            bindings.put("nodeConfiguration", NifiNodeConfigurationMagicTest.function());
            bindings.put("tenantRuntime", new com.linewell.dataelement.platform.tenant.api.TenantMagicModule(null));
            bindings.put("tableRead", TaskRuntimeMagicFixture.structuredReads(jdbc, sql -> configurationReads++));
            return MagicScript.create(actual, null).execute(new MagicScriptContext(bindings));
        }
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
            NodeHealthSnapshotMagicTest.columns(jdbc,"nifi_node_t");
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
            bindings.put("tableRead",TaskRuntimeMagicFixture.structuredReads(jdbc,sql->counter.reads++));
            bindings.put("ObjectMapper", ObjectMapper.class);
            bindings.put("Map", Map.class);
            bindings.put("Timestamp", Timestamp.class);
            bindings.put("LocalCache", LocalCache.class);
            bindings.put("SimpleDateFormat", SimpleDateFormat.class);
            bindings.put("CommonResponse", CommonResponse.class);
            bindings.put("ResponseEntity", ResponseEntity.class);
            bindings.put("nodeResolver", resolver);
            bindings.put("readConfiguration", (MagicScriptLambdaFunction) (ctx, args) -> {
                try { return readConfiguration(args[0], args[1]); }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            bindings.put("nifi", client);
            bindings.put("nodeHealth", (MagicScriptLambdaFunction)(ctx,args)->NodeHealthSnapshotMagicTest.execute(db,jdbc,scope,args[0]));
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
                    "URI", URI.class, "CommonResponse", CommonResponse.class, "ResponseEntity", ResponseEntity.class,
                    "flowRuntime", new FlowRuntimePaths(), "nodeHealth", (MagicScriptLambdaFunction)(ctx,args)->NodeHealthSnapshotMagicTest.execute(db,jdbc,scope,args[0]))));
        }
    }
    public static class Scope { String tenant = "police"; public String id() { return tenant; } }
    public static class FlowRuntimePaths {
        public String runtimeHealthPath() { return "/validation/runtime/status"; }
    }
    static class Counter implements SQLInterceptor {
        int reads;
        @Override public void preHandle(BoundSql sql, RequestEntity request) {
            if (sql.getSql().stripLeading().toLowerCase().startsWith("select")) reads++;
        }
    }
}
