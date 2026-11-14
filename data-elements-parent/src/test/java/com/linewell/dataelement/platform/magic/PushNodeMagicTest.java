package com.linewell.dataelement.platform.magic;

import static com.linewell.dataelement.platform.magic.PushRegistrySyncMagicTest.rejected;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.utils.StructuredValues;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class PushNodeMagicTest {
    private static final String PAGE = "ods_nifi_node_page_01";
    private static final String SAVE = "ods_nifi_node_save_01";
    private static final String DELETE = "ods_nifi_node_delete_01";

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void previewAndSelectedNodeSynchronizationKeepConstantReads(int count) throws Exception {
        try (var upstream = new NodeUpstream()) {
            var fixture = fixture(count, upstream.url());
            var dryRun = StructuredValues.objectMap(fixture.call(Map.of("datasourceId", "source", "nodeId", "node", "dryRun", true)));
            assertEquals(true, dryRun.get("valid"));
            assertEquals(3, fixture.reads.get());
            assertEquals(0, upstream.registryCalls.get());

            var synced = StructuredValues.objectMap(fixture.call(Map.of("datasourceId", "source", "nodeId", "node")));
            assertEquals(true, synced.get("synced"));
            assertEquals(11, fixture.reads.get());
            assertEquals(1, upstream.registryCalls.get());
            assertEquals(count, upstream.tables);
            assertEquals("test-only-service-key", upstream.serviceKey);
            assertTrue(upstream.requestId.startsWith("node-registry-node-source-"));
            assertEquals("REGISTRY_SYNC", upstream.deliveryType);
            assertFalse(upstream.requestBody.contains("must-not-leak"));
            assertEquals("SUCCESS", syncStatus(fixture));

            rejected(fixture.call(Map.of("datasourceId", "source", "nodeId", "foreign-node")), 403);
            rejected(fixture.call(Map.of("datasourceId", "foreign", "nodeId", "node")), 403);
            rejected(fixture.call(Map.of("datasourceId", "source", "nodeId", "disabled-node")), 409);
            fixture.scope.denied = true;
            assertEquals(true, StructuredValues.objectMap(fixture.call(Map.of("datasourceId", "source", "nodeId", "node", "dryRun", true))).get("valid"));
            assertEquals(1, upstream.registryCalls.get());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void pushNodeHealthIsScopedAndDisplayedWithoutPerRowQueries(int count) throws Exception {
        try (var upstream = new NodeUpstream()) {
            var fixture = fixture(0, upstream.url());
            fixture.jdbc.update("DELETE FROM push_node_t WHERE tid='disabled-node'");
            for (int i = 1; i < count; i++) {
                fixture.jdbc.update("INSERT INTO push_node_t(tid,tenant_id,node_name,node_code,network_code,base_url,enabled,is_default,is_del) "
                        + "VALUES(?,'ga','validation node',?,'GAW',?,1,0,0)", "node-" + i, "code-" + i, upstream.url());
            }
            assertEquals(count, pushNodes(fixture).size());
            assertEquals(2, fixture.reads.get());

            var check = Map.<String, Object>of("nodeType", "push", "action", "health", "tid", "node");
            assertEquals(true, StructuredValues.objectMap(call(fixture, PAGE, check)).get("success"));
            assertEquals("reachable", currentNode(fixture).get("healthStatus"));
            assertEquals(2, fixture.reads.get());

            fixture.jdbc.update("UPDATE push_node_t SET base_url=? WHERE tid='node'", upstream.url() + "/missing");
            assertEquals("unknown", currentNode(fixture).get("healthStatus"));
            assertEquals(false, StructuredValues.objectMap(call(fixture, PAGE, check)).get("success"));
            assertEquals("unreachable", currentNode(fixture).get("healthStatus"));
            rejected(call(fixture, PAGE, Map.of("nodeType", "push", "action", "health", "tid", "foreign-node")), 404);
            assertEquals(2, upstream.healthCalls.get());
        }
    }

    @Test
    void savesAndDeletesPushNodesAndRecordsUpstreamFailure() throws Exception {
        try (var upstream = new NodeUpstream()) {
            var fixture = fixture(1, upstream.url());
            fixture.jdbc.update("UPDATE push_node_t SET is_del=1");
            assertTrue(pushNodes(fixture).isEmpty());
            fixture.jdbc.update("UPDATE push_node_t SET is_del=0");

            var form = new LinkedHashMap<String, Object>(Map.of("nodeType", "push", "networkCode", "HLW",
                    "nodeName", "Validation node", "nodeCode", "validation", "baseUrl", upstream.url(), "enabled", false));
            var saved = StructuredValues.objectMap(call(fixture, SAVE, form));
            var id = saved.get("tid");
            assertNotNull(id);
            assertEquals(1, fixture.jdbc.queryForObject("SELECT is_default FROM push_node_t WHERE tid=?", Integer.class, id));
            rejected(call(fixture, SAVE, form), 409);
            form.put("tid", "foreign-node");
            rejected(call(fixture, SAVE, form), 404);
            form.remove("tid");
            form.put("nodeCode", "validation-bad-url");
            form.put("baseUrl", "http://validation:validation@localhost");
            rejected(call(fixture, SAVE, form), 400);

            rejected(call(fixture, DELETE, Map.of("nodeType", "push", "tid", id)), 400);
            rejected(call(fixture, DELETE, Map.of("nodeType", "push", "tid", "foreign-node")), 404);
            call(fixture, DELETE, Map.of("nodeType", "push", "tid", "disabled-node"));
            assertEquals(1, fixture.jdbc.queryForObject("SELECT is_del FROM push_node_t WHERE tid='disabled-node'", Integer.class));

            upstream.registryStatus = 503;
            rejected(fixture.call(Map.of("datasourceId", "source", "nodeId", "node")), 503);
            assertEquals("FAILED", syncStatus(fixture));
        }
    }

    private static PushRegistrySyncMagicTest.Fixture fixture(int count, String url) throws Exception {
        var fixture = new PushRegistrySyncMagicTest.Fixture(count);
        for (String id : List.of("node", "foreign-node", "disabled-node")) {
            fixture.jdbc.update("INSERT INTO push_node_t(tid,tenant_id,network_code,node_name,node_code,base_url,enabled,is_default,is_del) "
                            + "VALUES(?,?,'GAW',?,?,?, ?,0,0)",
                    id, "foreign-node".equals(id) ? "ct" : "ga", id, id, url, "disabled-node".equals(id) ? 0 : 1);
        }
        fixture.jdbc.execute("CREATE TABLE sym_dict_t(tid VARCHAR,tenant_id VARCHAR,dict_code VARCHAR,parent_id VARCHAR,is_del INT)");
        fixture.jdbc.update("INSERT INTO sym_dict_t VALUES('network','ga','SSWL','0',0),('gaw','ga','GAW','network',0),('hlw','ga','HLW','network',0)");
        return fixture;
    }

    private static Object call(PushRegistrySyncMagicTest.Fixture fixture, String metadataId, Map<String, Object> body) throws Exception {
        String script = CanonicalMagicSources.byId(metadataId).replaceAll("(?m)^import (tenantRuntime|tableSort)\\R", "");
        fixture.reads.set(0);
        var context = Map.of("body", body, "db", fixture.db, "tenantRuntime", fixture.scope, "tableSort", new TableSortMagicModule());
        return MagicScript.create(script, null).execute(new MagicScriptContext(context));
    }

    private static List<Map<String, Object>> pushNodes(PushRegistrySyncMagicTest.Fixture fixture) throws Exception {
        return StructuredValues.objectRows(StructuredValues.objectMap(call(fixture, PAGE, Map.of("nodeType", "push"))).get("list"));
    }

    private static Map<String, Object> currentNode(PushRegistrySyncMagicTest.Fixture fixture) throws Exception {
        return pushNodes(fixture).stream().filter(node -> "node".equals(node.get("tid"))).findFirst().orElseThrow();
    }

    private static String syncStatus(PushRegistrySyncMagicTest.Fixture fixture) {
        return fixture.jdbc.queryForObject("SELECT last_sync_status FROM push_node_t WHERE tid='node'", String.class);
    }

    private static final class NodeUpstream implements AutoCloseable {
        private final HttpServer server;
        final AtomicInteger healthCalls = new AtomicInteger();
        final AtomicInteger registryCalls = new AtomicInteger();
        volatile int registryStatus = 200;
        volatile int tables;
        volatile String serviceKey;
        volatile String requestId;
        volatile String deliveryType;
        volatile String requestBody;

        NodeUpstream() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var mapper = new ObjectMapper();
            server.createContext("/api/v1/data-push", request -> {
                try (request) {
                    byte[] input = request.getRequestBody().readAllBytes();
                    var payload = mapper.readTree(input);
                    requestBody = new String(input, java.nio.charset.StandardCharsets.UTF_8);
                    serviceKey = request.getRequestHeaders().getFirst("X-Standalone-Registry-Key");
                    requestId = request.getRequestHeaders().getFirst("Idempotency-Key");
                    deliveryType = payload.path("deliveryType").asText();
                    tables = payload.path("datasource").path("tables").size();
                    registryCalls.incrementAndGet();
                    byte[] response = mapper.writeValueAsBytes(Map.of("datasourceId", "source"));
                    request.getResponseHeaders().set("Content-Type", "application/json");
                    request.sendResponseHeaders(registryStatus, response.length);
                    request.getResponseBody().write(response);
                }
            });
            server.createContext("/", request -> {
                try (request) {
                    healthCalls.incrementAndGet();
                    boolean available = "/actuator/health".equals(request.getRequestURI().getPath());
                    byte[] response = mapper.writeValueAsBytes(Map.of("status", available ? "UP" : "DOWN"));
                    request.getResponseHeaders().set("Content-Type", "application/json");
                    request.sendResponseHeaders(available ? 200 : 503, response.length);
                    request.getResponseBody().write(response);
                }
            });
            server.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
