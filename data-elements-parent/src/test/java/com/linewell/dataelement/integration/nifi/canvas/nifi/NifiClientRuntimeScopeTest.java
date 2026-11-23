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
    @Test void selectedAnonymousNodeDoesNotInheritTheCredentialedNodesToken() {
        var resolver = mock(NifiNodeRuntimeResolver.class);
        var secured = new RuntimeNode("tenant-a", "secured", "secured", "https://secured-nifi.test",
                "validation-user", "validation-password", false, "root");
        var anonymous = new RuntimeNode("tenant-a", "anonymous", "anonymous", "http://anonymous-nifi.test",
                "", "", false, "root");
        var securedBuilder = RestClient.builder().baseUrl(secured.baseUrl());
        var anonymousBuilder = RestClient.builder().baseUrl(anonymous.baseUrl());
        var securedServer = MockRestServiceServer.bindTo(securedBuilder).build();
        var anonymousServer = MockRestServiceServer.bindTo(anonymousBuilder).build();
        when(resolver.resolveAssignedNode("secured")).thenReturn(secured);
        when(resolver.resolveAssignedNode("anonymous")).thenReturn(anonymous);
        when(resolver.restClient(secured)).thenReturn(securedBuilder.build());
        when(resolver.restClient(anonymous)).thenReturn(anonymousBuilder.build());
        securedServer.expect(requestTo("https://secured-nifi.test/nifi-api/access/token"))
                .andRespond(withSuccess("secured-token", MediaType.TEXT_PLAIN));
        securedServer.expect(ExpectedCount.times(2), requestTo("https://secured-nifi.test/nifi-api/process-groups/group"))
                .andExpect(header("Authorization", "Bearer secured-token"))
                .andRespond(withSuccess("{\"component\":{\"id\":\"group\"}}", MediaType.APPLICATION_JSON));
        anonymousServer.expect(requestTo("http://anonymous-nifi.test/nifi-api/process-groups/group"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("{\"component\":{\"id\":\"group\"}}", MediaType.APPLICATION_JSON));
        var client = new NifiClient(resolver, new ObjectMapper());
        try (var tenant = TenantContext.use("tenant-a")) {
            for (String id : new String[]{"secured", "anonymous", "secured"}) {
                try (var scope = client.bindAssignedNode(id)) {
                    assertThat(client.getProcessGroup("group").path("component").path("id").asText()).isEqualTo("group");
                }
            }
        }
        verify(resolver, never()).resolve();
        securedServer.verify();
        anonymousServer.verify();
    }
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
        verify(resolver, times(1)).restClient(node);
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

    @Test void selectedNodeReceivesGroupRequestsAndSuppliesTheDriverAndNativeLink() {
        var resolver = mock(NifiNodeRuntimeResolver.class);
        var fallback = node("tenant-a");
        var selected = new RuntimeNode("tenant-a", "node-secondary", "secondary",
                "http://secondary-nifi.test", "", "", false, "root-secondary", "MYSQL=/opt/secondary/mysql.jar");
        when(resolver.resolve()).thenReturn(fallback);
        when(resolver.resolveAssignedNode("node-secondary")).thenReturn(selected);
        var builder = RestClient.builder().baseUrl("http://secondary-nifi.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        when(resolver.restClient(selected)).thenReturn(builder.build());
        server.expect(requestTo("http://secondary-nifi.test/nifi-api/process-groups/root-secondary/process-groups"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("{\"component\":{\"id\":\"group\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://secondary-nifi.test/nifi-api/process-groups/group"))
                .andRespond(withSuccess("{\"component\":{\"id\":\"group\"}}", MediaType.APPLICATION_JSON));
        var client = new NifiClient(resolver, new ObjectMapper());
        try (var tenant = TenantContext.use("tenant-a"); var outer = client.bindRuntimeNode()) {
            try (var scope = client.bindAssignedNode("node-secondary"); var compiler = client.bindRuntimeNode()) {
                assertThat(client.getRootProcessGroupId()).isEqualTo("root-secondary");
                client.createProcessGroup("root-secondary", "validation", 0, 0);
                assertThat(client.getProcessGroup("group").path("component").path("id").asText()).isEqualTo("group");
                assertThat(client.jdbcDriverLocation("MYSQL")).isEqualTo("/opt/secondary/mysql.jar");
            }
            assertThat(client.getRootProcessGroupId()).isEqualTo("root-tenant-a");
            assertThat(client.assignedNodeUiUrl("node-secondary", "group"))
                    .isEqualTo("http://secondary-nifi.test/nifi/#/process-groups/group");
        }
        server.verify();
        verify(resolver, times(1)).resolve();
        verify(resolver, never()).restClient(fallback);
    }

    @Test void tasksWithTheSameGroupIdStillUseTheirOwnNifiServers() {
        var resolver = mock(NifiNodeRuntimeResolver.class);
        var first = new RuntimeNode("tenant-a", "first", "first", "http://first-nifi.test", "", "", false, "root");
        var second = new RuntimeNode("tenant-a", "second", "second", "http://second-nifi.test", "", "", false, "root");
        var firstBuilder = RestClient.builder().baseUrl(first.baseUrl());
        var secondBuilder = RestClient.builder().baseUrl(second.baseUrl());
        var firstServer = MockRestServiceServer.bindTo(firstBuilder).build();
        var secondServer = MockRestServiceServer.bindTo(secondBuilder).build();
        when(resolver.resolveAssignedNode("first")).thenReturn(first);
        when(resolver.resolveAssignedNode("second")).thenReturn(second);
        when(resolver.restClient(first)).thenReturn(firstBuilder.build());
        when(resolver.restClient(second)).thenReturn(secondBuilder.build());
        firstServer.expect(requestTo("http://first-nifi.test/nifi-api/process-groups/group"))
                .andRespond(withSuccess("{\"component\":{\"name\":\"first-task\"}}", MediaType.APPLICATION_JSON));
        secondServer.expect(requestTo("http://second-nifi.test/nifi-api/process-groups/group"))
                .andRespond(withSuccess("{\"component\":{\"name\":\"second-task\"}}", MediaType.APPLICATION_JSON));
        var client = new NifiClient(resolver, new ObjectMapper());
        try (var tenant = TenantContext.use("tenant-a")) {
            try (var selected = client.bindAssignedNode("first")) {
                assertThat(client.getProcessGroup("group").path("component").path("name").asText()).isEqualTo("first-task");
            }
            try (var selected = client.bindAssignedNode("second")) {
                assertThat(client.getProcessGroup("group").path("component").path("name").asText()).isEqualTo("second-task");
                assertThat(client.assignedNodeUiUrl("second", "group")).startsWith("http://second-nifi.test/nifi/");
            }
        }
        verify(resolver, never()).resolve();
        firstServer.verify();
        secondServer.verify();
    }

    private RuntimeNode node(String tenant) {
        return new RuntimeNode(tenant, "node-" + tenant, "test", "http://nifi.test", "test-user", "test-password", false, "root-" + tenant);
    }
}
