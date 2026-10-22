package com.linewell.dataelement.dataservice.flowserve;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.Scope;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService.ScopeDecision;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class FlowServeCanvasReadServiceTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final DataScopeAuthorizationService authorization = mock(DataScopeAuthorizationService.class);
    final List<String> databases = new ArrayList<>();
    final FlowServeCanvasReadService service = new FlowServeCanvasReadService(tenant -> { databases.add(tenant); return jdbc; }, authorization, new ObjectMapper());

    @ParameterizedTest @EnumSource(Scope.class)
    void selectedFlowUsesTheSameTenantAndServiceObjectScopeAsTheList(Scope scope) {
        when(authorization.currentScope("DATA_SOURCE")).thenReturn(new ScopeDecision("DATA_SOURCE", scope, "user-a", List.of("org-a", "org-b"), List.of(), true));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of("id", "flow-a", "dsl", "{\"flowId\":\"flow-a\",\"nodes\":[]}", "status", "draft", "lock_version", 2, "updated_at", "2026-10-20")));
        try (var tenant = TenantContext.use("tenant-a")) {
            assertThat(service.flow("flow-a")).containsEntry("lockVersion", 2);
        }
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(), args.capture());
        assertThat(databases).containsExactly("tenant-a");
        assertThat(args.getValue()).startsWith("flow-a", "tenant-a");
        assertThat(sql.getValue()).contains("a.tenant_id=f.tenant_id", "a.is_del=0", "f.deleted=0", "a.flow_id=f.id OR a.tid=f.id");
        if (scope == Scope.OWNER) { assertThat(sql.getValue()).contains("a.created_by=?"); assertThat(args.getValue()).contains("user-a"); }
        if (scope == Scope.ORG) { assertThat(sql.getValue()).contains("a.org_id IN (?,?)"); assertThat(args.getValue()).contains("org-a", "org-b"); }
        verifyNoMoreInteractions(jdbc);
    }

    @Test void missingOrForeignFlowIsNotReturnedAndMissingTenantDoesNotReachTheDatabase() {
        assertThatThrownBy(() -> service.flow("flow-a")).hasMessageContaining("租户");
        verifyNoInteractions(jdbc, authorization);
        when(authorization.currentScope("DATA_SOURCE")).thenReturn(new ScopeDecision("DATA_SOURCE", Scope.ALL, "user-a", List.of(), List.of(), true));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        try (var tenant = TenantContext.use("tenant-b")) {
            assertThatThrownBy(() -> service.flow("flow-a")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("数据范围");
        }
        assertThat(databases).containsExactly("tenant-b");
    }

    @Test void emptyOrganizationScopeIsExplicitlyDenied() {
        when(authorization.currentScope("DATA_SOURCE")).thenReturn(new ScopeDecision("DATA_SOURCE", Scope.ORG, "user-a", List.of(), List.of(), true));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        try (var tenant = TenantContext.use("tenant-a")) { assertThatThrownBy(() -> service.flow("flow-a")).isInstanceOf(ResponseStatusException.class); }
        verify(jdbc).queryForList(contains("AND 1=0"), any(Object[].class));
    }

    @ParameterizedTest @ValueSource(ints = {20, 100})
    void runtimeChoicesUseOneBoundedQueryAndNoPerNodeRequests(int count) {
        var rows = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < count; i++) rows.add(new LinkedHashMap<>(Map.of("id", "node-" + i, "deployTokenSet", 1, "localNode", 0, "defaultNode", 1, "enabled", true)));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(rows);
        try (var tenant = TenantContext.use("tenant-a")) {
            var result = service.runtimeNodes();
            assertThat(result).hasSize(count);
            assertThat(result.getFirst()).containsEntry("deployTokenSet", true).containsEntry("localNode", false);
        }
        verify(jdbc).setMaxRows(101);
        verify(jdbc).queryForList(contains("WHERE tenant_id=?"), eq("tenant-a"));
        verifyNoMoreInteractions(jdbc);
        verifyNoInteractions(authorization);
    }

    @Test void moreThanOneHundredRuntimeChoicesAreRejected() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(java.util.Collections.nCopies(101, Map.of("id", "node")));
        try (var tenant = TenantContext.use("tenant-a")) {
            assertThatThrownBy(service::runtimeNodes).isInstanceOf(ResponseStatusException.class).hasMessageContaining("100");
        }
    }
}
