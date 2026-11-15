package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.model.common.StatusCodeEnum;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.magicapi.modules.servlet.RequestModule;
import org.ssssssss.script.MagicResourceLoader;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

class NifiLineageMagicTest {
    private static final String ROUTE = "api/10.NiFi流程接口/01.接口/16.查询节点血缘.ms";
    private static final String RESOURCE_ID = "0a119f2475974554a8b3547dd70febf0";
    private static final String DSL = "{\"version\":1,\"nodes\":[],\"edges\":[]}";
    private static final String MAPPING = """
            {"primaryProcessorIds":{"m":"head"},"processorIdToCanvasNode":{"head":"m","tail":"m","foreign":"another-node"}}
            """;

    @Test
    void fallsBackWithinSameNodeAndReturnsLatestEventWithoutFlowFileSecrets() throws Exception {
        var fixture = new Fixture();
        fixture.deployed();
        fixture.search("head", "q1", "{\"provenanceEvents\":[{\"eventId\":999,\"componentId\":\"foreign\"}]}");
        fixture.search("tail", "q2", """
                {"provenanceEvents":[
                    {"eventId":9007199254740992,"componentId":"tail","clusterNodeId":"older"},
                    {"eventId":9007199254740993,"processorId":"tail","clusterNodeId":"cluster",
                     "attributes":[{"password":"must-not-return"}],"transitUri":"secret"},
                    {"eventId":9007199254740993,"componentId":"tail","clusterNodeId":"same-id-later"}
                ]}
                """);
        when(fixture.nifi.submitLineage("9007199254740993", "cluster"))
                .thenReturn(fixture.json("{\"lineage\":{\"id\":\"l1\"}}"));
        when(fixture.nifi.getLineage("l1", "cluster")).thenReturn(fixture.json("""
                {"lineage":{"finished":true,"results":{"nodes":[{"id":"event"}],"links":[]}}}
                """));

        var response = fixture.compute("owned", "m", null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = fixture.mapper.valueToTree(response.getBody());
        assertThat(body.path("anchorEventId").asText()).isEqualTo("9007199254740993");
        assertThat(body.path("nodes").size()).isEqualTo(1);
        assertThat(body.path("links").isArray()).isTrue();
        assertThat(body.path("events").size()).isEqualTo(3);
        assertThat(body.toString()).doesNotContain("must-not-return", "transitUri", "attributes", "processorId", "foreign");
        verify(fixture.nifi, never()).submitProcessorProvenance("foreign", 10);
        var order = inOrder(fixture.nifi, fixture.scope);
        order.verify(fixture.nifi).bindRuntimeNode();
        order.verify(fixture.nifi).submitProcessorProvenance("head", 10);
        order.verify(fixture.nifi).deleteProvenance("q1");
        order.verify(fixture.nifi).submitProcessorProvenance("tail", 10);
        order.verify(fixture.nifi).deleteProvenance("q2");
        order.verify(fixture.nifi).getLineage("l1", "cluster");
        order.verify(fixture.nifi).deleteLineage("l1", "cluster");
        order.verify(fixture.scope).close();
        fixture.assertReadOnly(1);
    }

    @Test
    void genuineEmptyEventStoreIsNotInventedLineage() throws Exception {
        var fixture = new Fixture();
        fixture.deployed();
        fixture.search("head", "q1", "{\"provenanceEvents\":[]}");
        fixture.search("tail", "q2", "{\"provenanceEvents\":[]}");

        var response = fixture.compute("owned", "m", List.of("10"), null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = fixture.mapper.valueToTree(response.getBody());
        assertThat(body.path("nodes").size()).isZero();
        assertThat(body.path("edges").size()).isZero();
        assertThat(body.path("message").asText()).isEqualTo("暂无血缘事件");
        assertThat(body.has("links")).isFalse();
        verify(fixture.nifi, never()).submitLineage(anyString(), any());
        fixture.assertReadOnly(1);
    }

    @Test
    void queryErrorsAreNotPresentedAsSuccessfulEmptyStateAndCloseNativeRequests() throws Exception {
        var fixture = new Fixture();
        fixture.deployed();
        fixture.search("head", "q1", "{\"errors\":[\"repository error\"],\"provenanceEvents\":[]}");

        var response = fixture.compute("owned", "m", List.of("10"), null);

        fixture.assertError(response, 502, "NiFi 事件查询失败，请检查事件存储及节点权限");
        var order = inOrder(fixture.nifi, fixture.scope);
        order.verify(fixture.nifi).deleteProvenance("q1");
        order.verify(fixture.scope).close();
        fixture.assertReadOnly(1);
    }

    @Test
    void lineageFailureClosesNativeRequestUsingTheSameCluster() throws Exception {
        var fixture = new Fixture();
        fixture.deployed();
        fixture.search("head", "q1", """
                {"provenanceEvents":[{"eventId":7,"componentId":"head","clusterNodeId":"cluster"}]}
                """);
        when(fixture.nifi.submitLineage("7", "cluster")).thenReturn(fixture.json("{\"lineage\":{\"id\":\"l1\"}}"));
        when(fixture.nifi.getLineage("l1", "cluster")).thenReturn(fixture.json("""
                {"lineage":{"finished":true,"results":{"errors":["unavailable"],"nodes":[],"links":[]}}}
                """));

        var response = fixture.compute("owned", "m", List.of("10"), null);

        fixture.assertError(response, 502, "NiFi 血缘查询失败，请检查事件存储及节点权限");
        var order = inOrder(fixture.nifi, fixture.scope);
        order.verify(fixture.nifi).deleteProvenance("q1");
        order.verify(fixture.nifi).deleteLineage("l1", "cluster");
        order.verify(fixture.scope).close();
        verify(fixture.nifi, never()).submitProcessorProvenance("tail", 10);
        fixture.assertReadOnly(1);
    }

    @Test
    void invalidParametersAreRejectedBeforeQueryingThePipelineOrNifi() throws Exception {
        var fixture = new Fixture();
        fixture.deployed();

        fixture.assertError(fixture.compute("owned", "m", List.of("0"), null), 400, "事件数量必须为 1–100");
        fixture.assertError(fixture.compute("owned", "m", List.of("101"), null), 400, "事件数量必须为 1–100");
        for (String malformed : List.of("not-a-number", "10.5", "2147483648")) {
            var response = fixture.compute("owned", "m", List.of(malformed), null);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            JsonNode body = fixture.mapper.valueToTree(response.getBody());
            assertThat(body.path("code").asInt()).isEqualTo(StatusCodeEnum.PARAMS_UNVALID.getCode());
            assertThat(body.path("msg").asText()).isEqualTo("参数[maxEvents]类型错误，期望类型: int");
            assertThat(body.path("success").asBoolean()).isFalse();
        }
        verifyNoInteractions(fixture.nifi, fixture.scope);
        fixture.assertReadOnly(0);
    }

    @Test
    void acceptsSpringDefaultFirstValueAndBracketAliasInServletOrder() throws Exception {
        var fixture = new Fixture();
        fixture.insert("owned", "tenant-a", "group", DSL, "{\"primaryProcessorIds\":{\"m\":\"head\"}}", 0);
        for (int limit : List.of(1, 10, 16, 100)) {
            when(fixture.nifi.submitProcessorProvenance("head", limit))
                    .thenReturn(fixture.json("{\"provenance\":{\"id\":\"q\"}}"));
        }
        when(fixture.nifi.getProvenance("q"))
                .thenReturn(fixture.json("{\"provenance\":{\"finished\":true,\"results\":{\"provenanceEvents\":[]}}}"));

        for (var response : List.of(
                fixture.compute("owned", "m", null, null),
                fixture.compute("owned", "m", List.of(""), null),
                fixture.compute("owned", "m", List.of("1", "101"), List.of("100")),
                fixture.compute("owned", "m", null, List.of("100", "0")),
                fixture.compute("owned", "m", List.of(" 0 x 1 0 "), null))) {
            assertThat(response.getStatusCode().value()).isEqualTo(200);
        }

        verify(fixture.nifi, times(2)).submitProcessorProvenance("head", 10);
        verify(fixture.nifi).submitProcessorProvenance("head", 1);
        verify(fixture.nifi).submitProcessorProvenance("head", 100);
        verify(fixture.nifi).submitProcessorProvenance("head", 16);
        verify(fixture.nifi, times(5)).bindRuntimeNode();
        verify(fixture.scope, times(5)).close();
        fixture.assertReadOnly(5);
    }

    @Test
    void hiddenOrUnreadablePipelinesAndUnknownNodesDoNotReachNifi() throws Exception {
        var fixture = new Fixture();
        fixture.deployed();
        fixture.insert("foreign", "tenant-b", "group", DSL, MAPPING, 0);
        fixture.insert("deleted", "tenant-a", "group", DSL, MAPPING, 1);
        fixture.insert("broken-dsl", "tenant-a", "group", "{\"version\":{}}", MAPPING, 0);
        fixture.insert("broken-mapping", "tenant-a", "group", DSL, "[1]", 0);
        fixture.insert("undeployed", "tenant-a", null, DSL, MAPPING, 0);

        for (String hidden : List.of("foreign", "deleted", "broken-dsl", "broken-mapping", "unknown")) {
            var response = fixture.compute(hidden, "m", null, null);
            assertThat(response.getStatusCode().value()).as("pipeline %s", hidden).isEqualTo(404);
            assertThat(response.getBody()).isNull();
        }
        fixture.assertError(fixture.compute("owned", "missing", null, null), 400, "节点未映射到 NiFi 处理器");
        fixture.assertError(fixture.compute("undeployed", "m", null, null), 400, "流程尚未部署，无法查询血缘");
        verifyNoInteractions(fixture.nifi, fixture.scope);
        fixture.assertReadOnly(7);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void processorsWithinOneNodeUseOnePipelineReadAndOneNativeBinding(int count) throws Exception {
        var fixture = new Fixture();
        Map<String, String> processors = new LinkedHashMap<>();
        IntStream.range(0, count).forEach(index -> processors.put("processor-" + index, "m"));
        processors.put("foreign", "another-node");
        fixture.insert("owned", "tenant-a", "group", DSL, fixture.mapper.writeValueAsString(Map.of(
                "primaryProcessorIds", Map.of("m", "processor-0"), "processorIdToCanvasNode", processors)), 0);
        when(fixture.nifi.submitProcessorProvenance(anyString(), anyInt()))
                .thenAnswer(call -> fixture.json("{\"provenance\":{\"id\":\"" + call.getArgument(0) + "\"}}"));
        when(fixture.nifi.getProvenance(anyString()))
                .thenReturn(fixture.json("{\"provenance\":{\"finished\":true,\"results\":{\"provenanceEvents\":[]}}}"));

        var response = fixture.compute("owned", "m", null, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(fixture.nifi).bindRuntimeNode();
        verify(fixture.nifi, times(count)).submitProcessorProvenance(anyString(), anyInt());
        verify(fixture.nifi, times(count)).deleteProvenance(anyString());
        verify(fixture.nifi, never()).submitProcessorProvenance("foreign", 10);
        verify(fixture.scope).close();
        fixture.assertReadOnly(1);
    }

    private static final class Fixture {
        private final ObjectMapper mapper = new ObjectMapper();
        private final NifiClient nifi = mock(NifiClient.class);
        private final NifiClient.RuntimeScope scope = mock(NifiClient.RuntimeScope.class);
        private final RequestModule request = mock(RequestModule.class);
        private final ReadCountingDataSource dataSource = new ReadCountingDataSource();
        private final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        private final MagicScript script;

        Fixture() throws IOException {
            script = MagicScript.create(CanonicalMagicSources.byPath(ROUTE, RESOURCE_ID), null);
            jdbc.execute("""
                    create table nifi_pipeline_t(id varchar(64) primary key, tenant_id varchar(64),
                        nifi_process_group_id varchar(64), dsl_json clob, node_mapping_json clob, is_del integer)
                    """);
            when(nifi.bindRuntimeNode()).thenReturn(scope);
        }

        void deployed() {
            insert("owned", "tenant-a", "group", DSL, MAPPING, 0);
        }

        void insert(String id, String tenant, String group, String dsl, String mapping, int deleted) {
            jdbc.update("insert into nifi_pipeline_t values(?,?,?,?,?,?)", id, tenant, group, dsl, mapping, deleted);
        }

        JsonNode json(String value) throws IOException {
            return mapper.readTree(value);
        }

        void search(String processor, String id, String results) throws IOException {
            when(nifi.submitProcessorProvenance(processor, 10))
                    .thenReturn(json("{\"provenance\":{\"id\":\"" + id + "\"}}"));
            when(nifi.getProvenance(id))
                    .thenReturn(json("{\"provenance\":{\"finished\":true,\"results\":" + results + "}}"));
        }

        ResponseEntity<?> compute(String id, String nodeId, List<String> limit, List<String> bracketLimit) throws Exception {
            when(request.getValues("maxEvents")).thenReturn(limit);
            when(request.getValues("maxEvents[]")).thenReturn(bracketLimit);
            var modules = Map.of("request", request, "tenantRuntime", new TenantMagicModule(null),
                    "tableRead", new StructuredTableReadMagicModule(jdbc));
            var variables = Map.<String, Object>of("path", Map.of("id", id, "nodeId", nodeId));
            synchronized (MagicResourceLoader.class) {
                var oldModules = new LinkedHashMap<String, Object>();
                var loader = MagicResourceLoader.class.getDeclaredField("classLoader");
                loader.setAccessible(true);
                @SuppressWarnings("unchecked")
                var previous = (Function<String, Object>) loader.get(null);
                modules.forEach((name, module) -> {
                    if (MagicResourceLoader.getModuleNames().contains(name)) {
                        oldModules.put(name, MagicResourceLoader.loadModule(name));
                    }
                    MagicResourceLoader.addModule(name, module);
                });
                MagicResourceLoader.setClassLoader(name -> {
                    if (name.equals(NifiClient.class.getName())) {
                        return nifi;
                    }
                    if (name.equals(ObjectMapper.class.getName())) {
                        return mapper;
                    }
                    return previous.apply(name);
                });
                try (var tenant = TenantContext.use("tenant-a")) {
                    dataSource.measuring = true;
                    return (ResponseEntity<?>) script.execute(new MagicScriptContext(variables));
                } finally {
                    dataSource.measuring = false;
                    MagicResourceLoader.setClassLoader(previous);
                    for (String name : modules.keySet()) {
                        if (oldModules.containsKey(name)) {
                            MagicResourceLoader.addModule(name, oldModules.get(name));
                        } else {
                            MagicResourceLoader.getModuleNames().remove(name);
                        }
                    }
                }
            }
        }

        void assertError(ResponseEntity<?> response, int status, String message) {
            assertThat(response.getStatusCode().value()).isEqualTo(status);
            assertThat(mapper.valueToTree(response.getBody()).path("error").asText()).isEqualTo(message);
        }

        void assertReadOnly(int reads) {
            assertThat(dataSource.reads).isEqualTo(reads);
            assertThat(dataSource.writes).isZero();
        }
    }

    private static final class ReadCountingDataSource extends DriverManagerDataSource {
        private boolean measuring;
        private int reads;
        private int writes;

        ReadCountingDataSource() {
            super("jdbc:h2:mem:validation-lineage-" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection actual = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if (measuring && method.getName().equals("prepareStatement") && args[0] instanceof String sql) {
                            if (sql.stripLeading().regionMatches(true, 0, "SELECT", 0, 6)) {
                                reads++;
                            } else {
                                writes++;
                            }
                        }
                        try {
                            return method.invoke(actual, args);
                        } catch (InvocationTargetException error) {
                            throw error.getCause();
                        }
                    });
        }
    }
}
