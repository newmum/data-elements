package com.linewell.dataelement.feature.assetcenter.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssetCenterQueryServiceBatchSummaryTest {

    @Test
    void resolvesPageEnrichmentWithConstantQueryCount() {
        AssetCenterStore store = mock(AssetCenterStore.class);
        when(store.tenant()).thenReturn("tenant-a");
        when(store.user()).thenReturn("user-a");
        when(store.organizations()).thenReturn(List.of("org-a"));
        when(store.column("data_market_collect_t", "asset_type")).thenReturn(true);
        when(store.table("da_catalog_publication_head_t")).thenReturn(true);
        when(store.object(any())).thenAnswer(call -> call.getArgument(0));
        when(store.rows(anyString(), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("FROM rm_org_t")) return List.of(Map.of("id", "org-a", "name", "部门甲"));
            if (sql.contains("FROM sym_application_t")) return List.of(Map.of("tid", "app-a", "app_name", "系统甲"));
            if (sql.contains("FROM data_market_collect_t")) return List.of(Map.of("asset_type", "TABLE", "asset_id", "table-a"));
            if (sql.contains("FROM da_catalog_publication_head_t")) return List.of(Map.of("catalog_id", "catalog-a", "current_publication_id", "pub-a", "availability", "PUBLISHED"));
            if (sql.contains("FROM da_catalog_publication_t")) return List.of(Map.of("tid", "pub-a", "title_snapshot", "已发布目录", "metadata_snapshot", Map.of("channels", List.of("API")), "visibility_scope", Map.of("visibility", "TENANT")));
            throw new AssertionError(sql);
        });

        AssetCenterQueryService service = new AssetCenterQueryService(store);
        List<Map<String,Object>> rows = List.of(
                AssetCenterStore.map("kind", "TABLE", "id", "table-a", "name", "表甲", "code", "t_a", "department_id", "org-a", "application_id", "app-a", "created_by", "user-a"),
                AssetCenterStore.map("kind", "CATALOG", "id", "catalog-a", "name", "目录甲", "code", "c_a", "department_id", "org-a", "application_id", "app-a", "created_by", "user-b"));
        List<Map<String,Object>> result = service.summaries(rows, true);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsEntry("departmentName", "部门甲").containsEntry("favorite", true);
        assertThat(result.get(1)).containsEntry("applicationName", "系统甲").containsEntry("listingStatus", "LISTED");
        assertThat((Object) ((Map<?,?>) result.get(1).get("object")).get("name")).isEqualTo("已发布目录");
        verify(store, times(5)).rows(anyString(), any(Object[].class));
        verify(store, never()).one(anyString(), any(Object[].class));
    }
}
