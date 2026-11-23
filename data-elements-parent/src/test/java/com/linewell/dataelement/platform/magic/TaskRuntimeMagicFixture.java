package com.linewell.dataelement.platform.magic;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver.RuntimeNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.magic.module.TaskRuntimeMagicModule;
import com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeStatusService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;

/** 脚本测试使用真实运行状态模块，只替换 NiFi 网络边界；关闭时释放采样线程和租户上下文。 */
final class TaskRuntimeMagicFixture implements AutoCloseable {
    static void bindingColumns(JdbcTemplate jdbc) {
        jdbc.execute("alter table nifi_pipeline_t add if not exists node_mapping_json clob");
        String functions = DatasourceTypeFacetsMagicTest.JsonFunctions.class.getName();
        jdbc.execute("create alias if not exists JSON_VALID for '" + functions + ".jsonValid'");
        jdbc.execute("create alias if not exists JSON_EXTRACT for '" + functions + ".jsonExtract'");
        jdbc.execute("create alias if not exists JSON_UNQUOTE for '" + functions + ".jsonUnquote'");
    }
    final TaskRuntimeMagicModule module;
    private final TaskRuntimeStatusService service;
    private final TenantContext.Scope tenant;
    private final NifiClient nifi = mock(NifiClient.class);
    private final RuntimeNode node;
    private final NifiNodeRuntimeResolver resolver = mock(NifiNodeRuntimeResolver.class);
    private final java.util.List<Map<String,Object>> configurations = new java.util.ArrayList<>();
    private final Map<String,RuntimeNode> nodes = new java.util.HashMap<>();

    TaskRuntimeMagicFixture(String tenantId) {
        this(tenantId, emptyRootStatus());
    }

    TaskRuntimeMagicFixture(String tenantId, JsonNode response) {
        String id = "tenant-a".equals(tenantId) ? "node-default" : "test-node";
        node = new RuntimeNode(tenantId, id, "test-node", "http://test.invalid", "", "", false, "root");
        when(resolver.resolve()).thenReturn(node);
        configurations.add(Map.of("tid", id, "network_code", "network-a", "node_name", "默认节点"));
        nodes.put(id,node);
        when(resolver.samplingConfigurations()).thenReturn(configurations);
        when(resolver.runtimeNode(anyMap())).thenAnswer(call -> nodes.get(((Map<?,?>)call.getArgument(0)).get("tid")));
        when(nifi.getRuntimeRootStatus(node)).thenReturn(response);
        service = new TaskRuntimeStatusService(resolver, nifi);
        module = new TaskRuntimeMagicModule(service);
        tenant = TenantContext.use(tenantId);
    }

    void addNode(String id, JsonNode response) {
        var added = new RuntimeNode(node.tenantId(),id,id,"http://"+id+".invalid","","",false,"root");
        nodes.put(id,added);
        configurations.add(Map.of("tid",id,"network_code","network-b","node_name",id));
        if (response == null) when(nifi.getRuntimeRootStatus(added)).thenThrow(new IllegalStateException("offline"));
        else when(nifi.getRuntimeRootStatus(added)).thenReturn(response);
    }

    static JsonNode emptyRootStatus() {
        var response = JsonNodeFactory.instance.objectNode();
        response.putObject("processGroupStatus").putObject("aggregateSnapshot").put("id", "root");
        return response;
    }

    static void bindTableFamily(Map<String, Object> inputs) throws IOException {
        String source = CanonicalMagicSources.byPath("function/01.通用函数/09.接入任务关联表批量读取.ms",
                "35e3408567e34c46abc946414c3405da").replaceAll("(?m)^import [^\\r\\n]+\\R", "");
        var script = MagicScript.create(source, null);
        inputs.put("accessTableFamily", (Function<Object[], Object>) arguments -> script.execute(
                new MagicScriptContext(Map.of("rootIds", arguments[0], "db", inputs.get("db"),
                        "tenantRuntime", inputs.get("tenantRuntime"), "tableRead", inputs.get("tableRead")))));
    }

    static StructuredTableReadMagicModule structuredReads(JdbcTemplate jdbc, Consumer<String> count) {
        return structuredReads(jdbc, count, "jdbc:mysql://access-task-validation");
    }

    static StructuredTableReadMagicModule structuredReads(JdbcTemplate jdbc, Consumer<String> count, String dialectUrl) {
        return new StructuredTableReadMagicModule(new JdbcTemplate(jdbc.getDataSource()) {
            @Override public <T> T execute(ConnectionCallback<T> callback) {
                return super.execute((ConnectionCallback<T>) connection -> callback.doInConnection(
                        (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("prepareStatement")) {
                                        String sql = (String) args[0];
                                        count.accept(sql);
                                        if (sql.startsWith("WITH RECURSIVE ") && sql.contains("structured_walk_")) {
                                            return H2RecursiveCteParameters.prepareStructured(connection, sql);
                                        }
                                    }
                                    if (dialectUrl != null && method.getName().equals("getMetaData")) {
                                        var metadata = connection.getMetaData();
                                        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                                (unused, metaMethod, metaArgs) -> metaMethod.getName().equals("getURL")
                                                        ? dialectUrl
                                                        : invoke(metadata, metaMethod, metaArgs));
                                    }
                                    return invoke(connection, method, args);
                                })));
            }
        });
    }

    private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    void verifyPageRootReads(java.util.List<?> ids) {
        if (ids == null) { verifySingleRootRead(); return; }
        for (var entry : nodes.entrySet()) {
            if (ids.contains(entry.getKey())) verify(nifi, times(1)).getRuntimeRootStatus(entry.getValue());
            else verify(nifi, atMost(1)).getRuntimeRootStatus(entry.getValue());
        }
    }

    void verifySingleRootRead() {
        verify(nifi, times(1)).getRuntimeRootStatus(node);
    }

    @Override public void close() {
        try { service.close(); } finally { tenant.close(); }
    }
}
