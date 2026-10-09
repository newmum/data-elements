package com.linewell.dataelement.dataassets.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.identity.application.DataScopeAuthorizationService;
import com.linewell.dataelement.integration.nifi.canvas.mapping.FieldMappingService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DictionaryConnectionBatchTest {
    private static final String RESOURCE = "MENU:2081000000000000002";

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void compilingDictionaryFieldsUsesOneConnectionReadAndOneRoleDecision(int count) {
        var jdbc = mock(JdbcTemplate.class);
        var scopes = mock(DataScopeAuthorizationService.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> mappings = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String id = "dictionary-" + i;
            rows.add(Map.of("tid", id, "tenant_id", "police", "db_type", "ORACLE", "show_connect", 1));
            mappings.add(Map.of("from", "/code_" + i, "to", "/label_" + i, "lookup", Map.of("multiValue", true,
                    "query", "SELECT code,label FROM dictionary WHERE code IN (:codes)", "dataSource", Map.of("datasourceId", id))));
        }
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(rows);
        when(scopes.visibleDataSources(eq(RESOURCE), anyCollection())).thenReturn(rows);
        try (var ignored = TenantContext.use("police")) {
            var resolver = new DataSourceConnectionPropertyResolver(jdbc, new ObjectMapper(), scopes);
            assertThat(new FieldMappingService(resolver).compilePlan(Map.of("version", "1.0", "mappings", mappings)).lookups()).hasSize(count);
        }
        verify(jdbc, times(1)).queryForList(contains("AND tenant_id = ? AND is_del = 0"), any(Object[].class));
        verify(scopes, times(1)).visibleDataSources(RESOURCE, rows);
    }

    @Test
    void missingCrossTenantAndRoleDeniedIdsRejectTheWholeBatch() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("create table db_datasource_t(tid varchar(32),tenant_id varchar(32),is_del int,db_type varchar(32),owner varchar(32))");
        jdbc.update("insert into db_datasource_t values('allowed','police',0,'ORACLE','me'),('denied','police',0,'ORACLE','other'),('foreign','other-tenant',0,'MYSQL','me')");
        var scopes = mock(DataScopeAuthorizationService.class);
        when(scopes.visibleDataSources(eq(RESOURCE), anyCollection())).thenAnswer(invocation -> {
            Collection<Map<String, Object>> input = invocation.getArgument(1);
            assertThat(input).allSatisfy(row -> assertThat(row.get("tenant_id")).isEqualTo("police"));
            return input.stream().filter(row -> "me".equals(row.get("owner"))).toList();
        });
        var resolver = new DataSourceConnectionPropertyResolver(jdbc, new ObjectMapper(), scopes);
        assertThat(resolver.resolveBatch("police", List.of("allowed", "allowed"), RESOURCE)).containsOnlyKeys("allowed");
        for (String id : List.of("foreign", "denied", "missing")) {
            assertThatThrownBy(() -> resolver.resolveBatch("police", List.of("allowed", id), RESOURCE))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("无访问权限");
        }
        assertThatThrownBy(() -> resolver.resolveBatch("", List.of("allowed"), RESOURCE)).hasMessageContaining("租户");
    }
}
