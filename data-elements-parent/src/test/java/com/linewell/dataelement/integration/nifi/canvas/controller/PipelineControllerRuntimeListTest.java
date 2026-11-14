package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ssssssss.magicapi.core.service.MagicAPIService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PipelineControllerRuntimeListTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void flowPageDelegatesOnceAndReturnsTheLegacyRawResponse(int size) throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var nifi = mock(NifiClient.class);
        var repo = mock(PipelineRepository.class);
        var magic = mock(MagicAPIService.class);
        var controller = new PipelineController(repo, nifi, magic, null, jdbc, null, null, null, null);
        var parameters = Map.<String, Object>of("page", 2, "size", size,
                "sortField", "nodeCount", "sortDir", "asc", "q", "%_");
        when(magic.invoke("/common/nifiPipelinePage", parameters))
                .thenReturn(Map.of("page", 2, "size", size, "total", 1L, "list", List.of()));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        try (var scope = TenantContext.use("tenant-a")) {
            mvc.perform(get("/nifi/api/pipelines/page").param("page", "2").param("size", String.valueOf(size))
                            .param("sortField", "nodeCount").param("sortDir", "asc").param("q", "%_")
                            .param("tenantId", "tenant-b"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"page\":2,\"size\":" + size + ",\"total\":1,\"list\":[]}", true));
        }
        verify(magic).invoke("/common/nifiPipelinePage", parameters);
        verifyNoMoreInteractions(magic);
        verifyNoInteractions(jdbc, repo, nifi);
    }

    @Test
    void flowPageRequiresAnIndustryTenantBeforeInvokingMagic() {
        var magic = mock(MagicAPIService.class);
        var controller = new PipelineController(null, null, magic, null, null, null, null, null, null);

        assertThatThrownBy(() -> controller.listPage(1, 20, null, null, null))
                .hasMessage("当前请求未绑定租户");
        try (var tenant = TenantContext.use("tenant-a"); var control = TenantContext.control()) {
            assertThatThrownBy(() -> controller.listPage(1, 20, null, null, null))
                    .hasMessage("流程查询必须绑定行业租户");
        }
        verifyNoInteractions(magic);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100})
    void nestedRootSnapshotServesEveryTenantRowWithOneNifiStatusRead(int size) {
        PipelineRepository repo = mock(PipelineRepository.class);
        NifiClient nifi = mock(NifiClient.class);
        List<Pipeline> tenantPipelines = new ArrayList<>();
        ObjectNode root = snapshot("root", 0, 0);
        ObjectNode folder = snapshot("folder", 0, 0);
        children(root).add(wrapper(folder));
        for (int i = 0; i < size; i++) {
            tenantPipelines.add(pipeline("tenant-flow-" + i, "pg-" + i));
            children(folder).add(wrapper(snapshot("pg-" + i, i, i + 1)));
        }
        // A root status can contain other tenants' process groups. Only repository rows are returned.
        children(folder).add(wrapper(snapshot("foreign-pg", 999, 999)));
        when(repo.findAll()).thenReturn(tenantPipelines);
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.getProcessGroupStatus("root")).thenReturn(response(root));

        List<Map<String, Object>> rows = controller(repo, nifi).runtimeList();

        assertThat(rows).hasSize(size);
        assertThat(rows).extracting(row -> row.get("processGroupId"))
                .doesNotContain("foreign-pg");
        assertThat(rows.get(size - 1)).containsEntry("queuedCount", (long) size)
                .containsEntry("activeThreads", (long) (size - 1))
                .doesNotContainKey("nifiStatusError");
        verify(repo, times(1)).findAll();
        verify(nifi, times(1)).getRootProcessGroupId();
        verify(nifi, times(1)).getProcessGroupStatus("root");
        verifyNoMoreInteractions(nifi);
    }

    @Test
    void missingProcessGroupHasErrorWithoutPerGroupFallback() {
        PipelineRepository repo = mock(PipelineRepository.class);
        NifiClient nifi = mock(NifiClient.class);
        when(repo.findAll()).thenReturn(List.of(pipeline("present", "pg-present"), pipeline("gone", "pg-gone")));
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        ObjectNode root = snapshot("root", 0, 0);
        children(root).add(wrapper(snapshot("pg-present", 2, 3)));
        when(nifi.getProcessGroupStatus("root")).thenReturn(response(root));

        List<Map<String, Object>> rows = controller(repo, nifi).runtimeList();

        assertThat(rows.get(0)).containsEntry("queuedCount", 3L).doesNotContainKey("nifiStatusError");
        assertThat(rows.get(1)).containsKey("nifiStatusError").doesNotContainKey("queuedCount");
        verify(nifi).getRootProcessGroupId();
        verify(nifi).getProcessGroupStatus("root");
        verifyNoMoreInteractions(nifi);
    }

    @Test
    void rootFailureMarksRowsAndDoesNotQueryEachGroup() {
        PipelineRepository repo = mock(PipelineRepository.class);
        NifiClient nifi = mock(NifiClient.class);
        when(repo.findAll()).thenReturn(List.of(pipeline("first", "pg-first"), pipeline("second", "pg-second")));
        when(nifi.getRootProcessGroupId()).thenReturn("root");
        when(nifi.getProcessGroupStatus("root")).thenThrow(new IllegalStateException("NiFi unavailable"));

        List<Map<String, Object>> rows = controller(repo, nifi).runtimeList();

        assertThat(rows).allSatisfy(row -> assertThat(row)
                .containsEntry("nifiStatusError", "NiFi unavailable").doesNotContainKey("queuedCount"));
        verify(nifi).getRootProcessGroupId();
        verify(nifi).getProcessGroupStatus("root");
        verifyNoMoreInteractions(nifi);
    }

    @Test
    void noDeployedProcessGroupDoesNotCallNifi() {
        PipelineRepository repo = mock(PipelineRepository.class);
        NifiClient nifi = mock(NifiClient.class);
        when(repo.findAll()).thenReturn(List.of(pipeline("draft", null)));

        List<Map<String, Object>> rows = controller(repo, nifi).runtimeList();

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).doesNotContainKey("nifiStatusError");
        verifyNoInteractions(nifi);
    }

    private static PipelineController controller(PipelineRepository repo, NifiClient nifi) {
        return new PipelineController(repo, nifi, null, null, null, null, null, null, null);
    }

    private static Pipeline pipeline(String id, String groupId) {
        return new Pipeline(id, id, null, 1L, 2L, null,
                groupId, PipelineStatus.RUNNING, null, null, null, null, null);
    }

    private static ObjectNode snapshot(String id, long activeThreads, long queuedCount) {
        ObjectNode snapshot = JSON.createObjectNode();
        snapshot.put("id", id);
        snapshot.put("activeThreadCount", activeThreads);
        snapshot.put("queuedCount", queuedCount);
        snapshot.put("flowFilesIn", 7);
        snapshot.put("flowFilesOut", 5);
        return snapshot;
    }

    private static ArrayNode children(ObjectNode snapshot) {
        JsonNode existing = snapshot.get("processGroupStatusSnapshots");
        return existing instanceof ArrayNode array ? array : snapshot.putArray("processGroupStatusSnapshots");
    }

    private static ObjectNode wrapper(ObjectNode snapshot) {
        ObjectNode wrapper = JSON.createObjectNode();
        wrapper.put("id", snapshot.path("id").asText());
        wrapper.set("processGroupStatusSnapshot", snapshot);
        return wrapper;
    }

    private static JsonNode response(ObjectNode root) {
        ObjectNode response = JSON.createObjectNode();
        response.putObject("processGroupStatus").set("aggregateSnapshot", root);
        return response;
    }
}
