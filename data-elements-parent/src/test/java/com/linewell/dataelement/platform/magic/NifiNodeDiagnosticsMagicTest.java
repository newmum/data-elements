package com.linewell.dataelement.platform.magic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.model.common.CommonResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.linewell.dataelement.magic.runtime.resource.MagicResourceReloadController;
import org.ssssssss.magicapi.core.resource.Resource;
import org.ssssssss.magicapi.core.service.MagicResourceService;
import org.ssssssss.script.MagicScript;
import org.ssssssss.script.MagicScriptContext;
import org.ssssssss.script.runtime.ExitValue;

class NifiNodeDiagnosticsMagicTest {
    private final NifiClient nifiClient = mock(NifiClient.class);

    private Object diagnostics(String tenantId) throws Exception {
        var script = CanonicalMagicSources.byId("ods_nifi_node_page_01").replaceAll("(?m)^import [^\\r\\n]*\\r?\\n", "");
        var tenant = new NifiNodeListMagicTest.Scope();
        tenant.tenant = tenantId;
        return MagicScript.create(script, null).execute(new MagicScriptContext(Map.of(
                "tenantRuntime", tenant, "nifi", nifiClient, "CommonResponse", CommonResponse.class,
                "ResponseEntity", ResponseEntity.class, "body", Map.of("action", "diagnostics", "nodeId", "node-1"))));
    }

    @Test
    void missingTenantCannotResolveEvenAnExistingNode() throws Exception {
        assertThat(((ExitValue) diagnostics("")).getValues()).contains(401);
        verifyNoInteractions(nifiClient);
    }

    @Test
    void reloadUsesTheConfiguredMvcRouteWithNoMagicRouteRegistered() throws Exception {
        var store = mock(Resource.class);
        var registry = mock(MagicResourceService.class);
        var reload = new MagicResourceReloadController(store, registry);
        var mvc = MockMvcBuilders.standaloneSetup(reload)
                .addPlaceholderValue("data-element.magic.resource-reload-path", "/validation/resource-reload").build();
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/validation/resource-reload")).andReturn().getResponse();
        assertThat(result.getStatus()).isEqualTo(200);
        assertThat(new ObjectMapper().readTree(result.getContentAsString()).path("data").path("success").asBoolean()).isTrue();
        var order = inOrder(store, registry);
        order.verify(store).readAll();
        order.verify(registry).refresh();
    }

    @Test
    void responseContainsOnlySelectedSystemMetrics() throws Exception {
        var snapshot = new ObjectMapper().readTree("""
                {"systemDiagnostics":{"aggregateSnapshot":{"heapUtilization":"41%",
                "usedHeapBytes":1073741824,"maxHeapBytes":4294967296,
                "processorLoadAverage":1.25,"availableProcessors":8,"totalThreads":75,
                "statsLastRefreshed":"2026-10-04 10:00:00","secret":"do-not-return"}}}
                """);
        when(nifiClient.systemDiagnostics("node-1")).thenReturn(snapshot);
        var response = (ResponseEntity<?>) diagnostics("tenant-1");
        var data = (Map<?, ?>) ((CommonResponse<?>) response.getBody()).getData();
        assertThat(data.get("heapUtilization")).isEqualTo("41%");
        assertThat(data.get("totalThreads")).isEqualTo(75);
        assertThat(data.keySet().stream().map(Object::toString)).doesNotContain("secret");
        verify(nifiClient).systemDiagnostics("node-1");
    }
}
