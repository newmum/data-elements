package com.linewell.dataelement.platform.magic;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver.RuntimeNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.runtime.TaskRuntimeMagicModule;
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
    final TaskRuntimeMagicModule module;
    private final TaskRuntimeStatusService service;
    private final TenantContext.Scope tenant;
    private final NifiClient nifi = mock(NifiClient.class);
    private final RuntimeNode node;

    TaskRuntimeMagicFixture(String tenantId) {
        this(tenantId, emptyRootStatus());
    }

    TaskRuntimeMagicFixture(String tenantId, JsonNode response) {
        var resolver = mock(NifiNodeRuntimeResolver.class);
        node = new RuntimeNode(tenantId, "test-node", "test-node", "https://test.invalid", "", "", false, "root");
        when(resolver.resolve()).thenReturn(node);
        when(nifi.getRuntimeRootStatus(node)).thenReturn(response);
        service = new TaskRuntimeStatusService(resolver, nifi);
        module = new TaskRuntimeMagicModule(service);
        tenant = TenantContext.use(tenantId);
    }

    static JsonNode emptyRootStatus() {
        var response = JsonNodeFactory.instance.objectNode();
        response.putObject("processGroupStatus").putObject("aggregateSnapshot").put("id", "root");
        return response;
    }

    static void bindTableFamily(Map<String, Object> inputs) throws IOException {
        String source = CanonicalMagicSources.byPath("function/01.通用函数/21.接入任务关联表批量读取.ms",
                "35e3408567e34c46abc946414c3405da").replaceAll("(?m)^import [^\\r\\n]+\\R", "");
        var script = MagicScript.create(source, null);
        inputs.put("accessTableFamily", (Function<Object[], Object>) arguments -> script.execute(
                new MagicScriptContext(Map.of("rootIds", arguments[0], "db", inputs.get("db"),
                        "tenantRuntime", inputs.get("tenantRuntime"), "tableRead", inputs.get("tableRead")))));
    }

    static StructuredTableReadMagicModule structuredReads(JdbcTemplate jdbc, Consumer<String> count) {
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
                                    if (method.getName().equals("getMetaData")) {
                                        var metadata = connection.getMetaData();
                                        return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                                                (unused, metaMethod, metaArgs) -> metaMethod.getName().equals("getURL")
                                                        ? "jdbc:mysql://access-task-validation"
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

    void verifySingleRootRead() {
        verify(nifi, times(1)).getRuntimeRootStatus(node);
    }

    @Override public void close() {
        try { service.close(); } finally { tenant.close(); }
    }
}
