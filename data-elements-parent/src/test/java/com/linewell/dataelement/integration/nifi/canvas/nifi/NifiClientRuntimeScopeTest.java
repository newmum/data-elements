package com.linewell.dataelement.integration.nifi.canvas.nifi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver.RuntimeNode;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class NifiClientRuntimeScopeTest {
    @ParameterizedTest @ValueSource(ints = {20, 100})
    void resolvesTenantNodeOnceBeforeCreatingTwentyOrOneHundredProcessors(int count) {
        var resolver = mock(NifiNodeRuntimeResolver.class);
        RuntimeNode node = node("tenant-a");
        when(resolver.resolve()).thenReturn(node);
        var builder = RestClient.builder().baseUrl("http://nifi.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        when(resolver.restClient(node)).thenReturn(builder.build());
        server.expect(requestTo("http://nifi.test/nifi-api/access/token")).andRespond(withSuccess("test-token", MediaType.TEXT_PLAIN));
        server.expect(ExpectedCount.times(count), requestTo("http://nifi.test/nifi-api/process-groups/group/processors"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("{\"component\":{\"id\":\"processor\"}}", MediaType.APPLICATION_JSON));
        var client = new NifiClient(resolver, new ObjectMapper());
        try (var tenant = TenantContext.use("tenant-a"); var scope = client.bindRuntimeNode()) {
            for (int i = 0; i < count; i++) client.createProcessor("group", "org.apache.nifi.processors.standard.ExecuteStreamCommand",
                    "script-" + i, 0, 0, Map.of("Command Path", "python3"), null, null);
        }
        server.verify();
        verify(resolver, times(1)).resolve();
    }

    @Test void nestedScopeIsReleasedAndCannotBeReusedAcrossTenants() {
        var resolver = mock(NifiNodeRuntimeResolver.class);
        when(resolver.resolve()).thenAnswer(ignored -> node(TenantContext.requireTenantId()));
        var client = new NifiClient(resolver, new ObjectMapper());
        try (var tenant = TenantContext.use("tenant-a"); var outer = client.bindRuntimeNode()) {
            try (var inner = client.bindRuntimeNode()) { assertThat(client.getRootProcessGroupId()).isEqualTo("root-tenant-a"); }
            try (var otherTenant = TenantContext.use("tenant-b")) {
                assertThatThrownBy(client::getRootProcessGroupId).hasMessageContaining("租户上下文发生变化");
            }
            assertThat(client.getRootProcessGroupId()).isEqualTo("root-tenant-a");
        }
        try (var tenant = TenantContext.use("tenant-b"); var next = client.bindRuntimeNode()) {
            assertThat(client.getRootProcessGroupId()).isEqualTo("root-tenant-b");
        }
        verify(resolver, times(2)).resolve();
    }

    @Test void failedCompilationDoesNotLeaveItsNodeBoundToTheWorkerThread() {
        var resolver = mock(NifiNodeRuntimeResolver.class);
        when(resolver.resolve()).thenReturn(node("tenant-a"), node("tenant-b"));
        var client = new NifiClient(resolver, new ObjectMapper());
        assertThatThrownBy(() -> {
            try (var tenant = TenantContext.use("tenant-a"); var scope = client.bindRuntimeNode()) {
                throw new IllegalStateException("compile failed");
            }
        }).hasMessage("compile failed");
        try (var tenant = TenantContext.use("tenant-b")) {
            assertThat(client.getRootProcessGroupId()).isEqualTo("root-tenant-b");
        }
        verify(resolver, times(2)).resolve();
    }

    private RuntimeNode node(String tenant) {
        return new RuntimeNode(tenant, "node-" + tenant, "test", "http://nifi.test", "test-user", "test-password", false, "root-" + tenant);
    }
}
