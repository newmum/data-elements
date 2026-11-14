package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.api.DataScopeMagicModule;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.Scope;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.ScopeDecision;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.platform.tenant.api.TenantMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.linewell.dataelement.utils.StructuredValues;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.function.MagicScriptLambdaFunction;

class FlowServeCanvasReadMagicTest {
    private static final String API = "api/06.数据服务/01.服务编排/01.流程管理/";
    private static final String HELPER = "function/01.通用函数/44.云梯画布只读接口响应.ms";
    private static final String TENANT = "tenant-a";
    private static final Timestamp TIME = Timestamp.valueOf("2026-10-20 09:01:02.123456");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(Scope.class)
    void bothServiceKeysUseTheCurrentTenantAndRoleScope(Scope scope) throws Exception {
        var f = new Fixture();
        f.scope(scope, List.of("org-a"));
        f.flow("by-flow-id", TENANT, 0, "by-flow-id");
        f.flow("legacy-tid", TENANT, 0, "legacy-tid");
        f.flow("deleted-flow", TENANT, 1, "deleted-flow");
        f.flow("other-tenant", "tenant-b", 0, "other-tenant");
        f.flow("only-deleted-service", TENANT, 0, "only-deleted-service");
        f.flow("other-scope", TENANT, 0, "other-scope");
        f.service("service-a", "by-flow-id", TENANT, 0, "user-a", "org-a");
        f.service("service-b", "by-flow-id", TENANT, 0, "user-a", "org-a");
        f.service("legacy-tid", null, TENANT, 0, "user-a", "org-a");
        f.service("service-deleted-flow", "deleted-flow", TENANT, 0, "user-a", "org-a");
        f.service("service-other-tenant", "other-tenant", "tenant-b", 0, "user-a", "org-a");
        f.service("service-deleted", "only-deleted-service", TENANT, 1, "user-a", "org-a");
        f.service("service-other-user", "other-scope", TENANT, 0, "user-b", "org-b");
        f.service("service-foreign", "only-deleted-service", "tenant-b", 0, "user-a", "org-a");

        assertThat(f.readFlow("by-flow-id").getSuccess()).isTrue();
        assertThat(f.selects).hasSize(1);
        var flow = StructuredValues.objectMap(f.readFlow("legacy-tid").getData());
        assertThat(flow).containsEntry("status", "DRAFT").containsEntry("lockVersion", 2)
                .containsEntry("updatedAt", TIME.toString());
        assertThat(((JsonNode) flow.get("flow")).path("flowId").asText()).isEqualTo("legacy-tid");
        assertThat(f.selects).hasSize(1);
        for (String id : List.of("deleted-flow", "other-tenant", "only-deleted-service", "missing-flow")) {
            assertError(f.readFlow(id), 500, "404 NOT_FOUND ", "服务流程不存在或不在当前数据范围内");
            assertThat(f.selects).hasSize(1);
        }
        var otherScope = f.readFlow("other-scope");
        if (scope == Scope.ALL) {
            assertThat(otherScope.getSuccess()).isTrue();
        } else {
            assertError(otherScope, 500, "404 NOT_FOUND ", "当前数据范围");
        }
        assertThat(f.selects).hasSize(1);
    }

    @Test
    void tenantAndScopeFailuresNeverReturnAFlow() throws Exception {
        var f = new Fixture();
        assertError(f.run(f.flowGet, Map.of("id", "flow-a")), 403, "当前请求未绑定租户", "租户");
        assertThat(f.selects).isEmpty();
        verifyNoInteractions(f.authorization);

        assertError(f.readFlow("x".repeat(65)), 500, "400 BAD_REQUEST ", "流程标识无效");
        assertThat(f.selects).isEmpty();
        verifyNoInteractions(f.authorization);

        f.flow("flow-a", TENANT, 0, "flow-a");
        f.service("service-a", "flow-a", TENANT, 0, "user-a", "org-a");
        f.scope(Scope.ORG, List.of());
        assertError(f.readFlow("flow-a"), 500, "404 NOT_FOUND ", "当前数据范围");
        assertThat(f.selects).hasSizeLessThanOrEqualTo(1);

        f.scope(Scope.ORG, IntStream.rangeClosed(1, 1001).mapToObj(index -> "org-" + index).toList());
        assertError(f.readFlow("flow-a"), 500, "403 FORBIDDEN ", "部门数据范围超出查询上限");
        assertThat(f.selects).isEmpty();
    }

    @Test
    void mismatchedOrInvalidSavedDslReturnsTheExistingConflictEnvelope() throws Exception {
        var f = new Fixture();
        f.scope(Scope.ALL, List.of());
        f.flow("flow-a", TENANT, 0, "other-id");
        f.flow("flow-b", TENANT, 0, "flow-b");
        f.jdbc.update("UPDATE fs_flow SET dsl = ? WHERE id = ?", "{broken", "flow-b");
        f.service("a", "flow-a", TENANT, 0, "user-a", "org-a");
        f.service("b", "flow-b", TENANT, 0, "user-a", "org-a");
        for (String id : List.of("flow-a", "flow-b")) {
            assertError(f.readFlow(id), 500, "409 CONFLICT ", "服务编排内容无效");
            assertThat(f.selects).hasSize(1);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void runtimeNodesUseOneReadAndOnlyExposeTokenPresence(int count) throws Exception {
        var f = new Fixture();
        f.seedNodes(count);
        var nodes = StructuredValues.objectRows(f.readNodes().getData());
        assertThat(nodes).hasSize(count);
        assertThat(nodes.getFirst()).containsOnlyKeys("id", "name", "businessDomain", "serviceType", "baseUrl",
                        "managementUrl", "deployTokenSet", "localNode", "defaultNode", "enabled", "createdAt")
                .containsEntry("id", "node-000").containsEntry("deployTokenSet", true)
                .containsEntry("localNode", false).containsEntry("defaultNode", true).containsEntry("enabled", false);
        assertThat(nodes.get(1)).containsEntry("id", "node-001").containsEntry("deployTokenSet", false);
        assertThat(nodes.get(2)).containsEntry("id", "node-002").containsEntry("deployTokenSet", false);
        assertThat(nodes).extracting(row -> row.get("id")).doesNotContain("foreign-node");
        assertThat(f.selects).hasSize(1);
        verifyNoInteractions(f.authorization);
    }

    @Test
    void moreThanOneHundredNodesAreRejectedAndHeadUsesTheSameResponse() throws Exception {
        var f = new Fixture();
        f.scope(Scope.ALL, List.of());
        f.flow("flow-a", TENANT, 0, "flow-a");
        f.service("service-a", "flow-a", TENANT, 0, "user-a", "org-a");
        f.seedNodes(101);
        assertError(f.readNodes(), 500, "400 BAD_REQUEST ", "运行节点超过100项");
        assertThat(f.selects).hasSize(1);
        try (var tenant = TenantContext.use(TENANT)) {
            var path = Map.of("id", (Object) "flow-a");
            assertThat(f.run(f.flowHead, path)).isEqualTo(f.run(f.flowGet, path));
            assertThat(f.run(f.nodesHead, Map.of()).getMsg()).isEqualTo(f.run(f.nodesGet, Map.of()).getMsg());
        }
    }

    private static void assertError(CommonResponse<?> response, int code, String prefix, String message) {
        assertThat(response.getCode()).isEqualTo(code);
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMsg()).startsWith(prefix).contains(message);
        if (code == 500) {
            assertThat(response.getError()).containsEntry("type", "org.springframework.web.server.ResponseStatusException");
        }
    }

    private static MagicScript script(String path, String id, String... bindings) throws IOException {
        String original = CanonicalMagicSources.byPath(path, id);
        MagicScript.create(original, null).compile();
        String executable = original;
        for (String binding : bindings) {
            executable = executable.replaceFirst("(?m)^import " + java.util.regex.Pattern.quote(binding) + "\\R", "");
        }
        return MagicScript.create(executable, null);
    }

    private static MagicScript endpoint(String file, String id) throws IOException {
        return script(API + file, id, "'@/common/flowServeCanvasReadHttp' as readCanvas");
    }

    private static final class Fixture {
        final DataScopeAuthorizationService authorization = mock(DataScopeAuthorizationService.class);
        final ReadCountingSource source = new ReadCountingSource();
        final List<String> selects = source.selects;
        final JdbcTemplate jdbc = new JdbcTemplate(source);
        final StructuredTableReadMagicModule tableRead = new StructuredTableReadMagicModule(jdbc);
        final MagicScript helper = script(HELPER, "flowserve_canvas_read_http_v1", "dataScope", "tableRead",
                "tenantRuntime", "com.fasterxml.jackson.databind.ObjectMapper as objectMapper");
        final MagicScript flowGet = endpoint("06.查询画布流程详情.ms", "flowserve_canvas_flow_get_v1");
        final MagicScript flowHead = endpoint("07.核验画布流程详情.ms", "flowserve_canvas_flow_head_v1");
        final MagicScript nodesGet = endpoint("08.查询画布运行节点.ms", "flowserve_canvas_nodes_get_v1");
        final MagicScript nodesHead = endpoint("09.核验画布运行节点.ms", "flowserve_canvas_nodes_head_v1");

        Fixture() throws IOException {
            jdbc.execute("CREATE TABLE fs_flow (id VARCHAR(64), tenant_id VARCHAR(64), deleted INT, dsl CLOB,"
                    + " status VARCHAR(32), lock_version INT, updated_at TIMESTAMP(6))");
            jdbc.execute("CREATE TABLE api_info_t (tid VARCHAR(64), flow_id VARCHAR(64), tenant_id VARCHAR(64),"
                    + " is_del INT, created_by VARCHAR(64), org_id VARCHAR(64))");
            jdbc.execute("CREATE TABLE fs_runtime_node (id VARCHAR(64), tenant_id VARCHAR(64), name VARCHAR(80),"
                    + " business_domain VARCHAR(64), service_type VARCHAR(64), base_url VARCHAR(128),"
                    + " management_url VARCHAR(128), deploy_token VARCHAR(128), local_node INT,"
                    + " default_node INT, enabled BOOLEAN, created_at TIMESTAMP(6))");
        }

        void scope(Scope scope, List<String> organizations) {
            when(authorization.currentScope(DataScopeAuthorizationService.DATA_SOURCE_RESOURCE)).thenReturn(
                    new ScopeDecision("DATA_SOURCE", scope, "user-a", organizations, List.of(), true));
        }

        void flow(String id, String tenant, int deleted, String insideDsl) {
            jdbc.update("INSERT INTO fs_flow VALUES(?,?,?,?,?,?,?)", id, tenant, deleted,
                    "{\"flowId\":\"" + insideDsl + "\",\"name\":\"验证草稿\",\"nodes\":[]}", "DRAFT", 2, TIME);
        }

        void service(String id, String flow, String tenant, int deleted, String user, String org) {
            jdbc.update("INSERT INTO api_info_t VALUES(?,?,?,?,?,?)", id, flow, tenant, deleted, user, org);
        }

        void seedNodes(int count) {
            List<Object[]> rows = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                String token = index == 1 ? "" : index == 2 ? null : "validation-token-fixture";
                rows.add(new Object[] {String.format("node-%03d", index), TENANT, "same-name", "validation", "http",
                        "http://localhost.invalid", null, token, index % 2, index == 0 ? 1 : 0, index != 0, TIME});
            }
            rows.add(new Object[] {"foreign-node", "tenant-b", "name", "external", "http", "http://localhost.invalid",
                    null, "foreign-validation-fixture", 0, 1, true, TIME});
            jdbc.batchUpdate("INSERT INTO fs_runtime_node VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", rows);
        }

        CommonResponse<?> readFlow(String id) {
            return TenantContext.call(TENANT, () -> run(flowGet, Map.of("id", id)));
        }

        CommonResponse<?> readNodes() {
            return TenantContext.call(TENANT, () -> run(nodesGet, Map.of()));
        }

        CommonResponse<?> run(MagicScript endpoint, Map<String, ?> path) {
            selects.clear();
            MagicScriptLambdaFunction readCanvas = (runtime, args) -> {
                Map<String, Object> variables = new LinkedHashMap<>();
                variables.put("flowRequested", args[0]);
                variables.put("flowId", args[1]);
                variables.put("dataScope", new DataScopeMagicModule(authorization));
                variables.put("tableRead", tableRead);
                variables.put("tenantRuntime", new TenantMagicModule(null));
                variables.put("objectMapper", MAPPER);
                return helper.execute(new MagicScriptContext(variables));
            };
            var response = (ResponseEntity<?>) endpoint.execute(new MagicScriptContext(
                    Map.of("readCanvas", readCanvas, "path", path)));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            return (CommonResponse<?>) response.getBody();
        }
    }

    private static final class ReadCountingSource extends DriverManagerDataSource {
        final List<String> selects = new ArrayList<>();

        ReadCountingSource() {
            super("jdbc:h2:mem:flowserve_canvas_" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        try {
                            if (method.getName().equals("getMetaData")) {
                                DatabaseMetaData metadata = connection.getMetaData();
                                return Proxy.newProxyInstance(getClass().getClassLoader(),
                                        new Class<?>[]{DatabaseMetaData.class}, (ignored, lookup, values) -> {
                                            if (lookup.getName().equals("getURL")) return "jdbc:mysql://localhost/flowserve_validation";
                                            try {
                                                return lookup.invoke(metadata, values);
                                            } catch (InvocationTargetException failure) {
                                                throw failure.getCause();
                                            }
                                        });
                            }
                            if (method.getName().equals("prepareStatement") && arguments[0] instanceof String sql
                                    && sql.stripLeading().toUpperCase(Locale.ROOT).startsWith("SELECT")) {
                                selects.add(sql);
                            }
                            return method.invoke(connection, arguments);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
        }
    }
}
