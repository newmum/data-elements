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

    void verifySingleRootRead() {
        verify(nifi, times(1)).getRuntimeRootStatus(node);
    }

    @Override public void close() {
        try { service.close(); } finally { tenant.close(); }
    }
}
