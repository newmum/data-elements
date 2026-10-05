package com.linewell.dataelement.platform.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ssssssss.magicapi.core.resource.Resource;
import org.ssssssss.magicapi.core.service.MagicResourceService;

class NodeConfigurationDiagnosticsTest {
    private final NifiClient nifiClient = mock(NifiClient.class);
    private final NodeConfigurationController controller = new NodeConfigurationController(
            mock(MagicResourceService.class), mock(Resource.class), nifiClient);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void missingTenantCannotResolveEvenAnExistingNode() {
        assertThatThrownBy(() -> controller.systemDiagnostics(Map.of("nodeId", "node-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("租户上下文");
        verifyNoInteractions(nifiClient);
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
        try (var ignored = TenantContext.use("tenant-1")) {
            var result = controller.systemDiagnostics(Map.of("nodeId", "node-1"));
            assertThat(result.getSuccess()).isTrue();
            assertThat(result.getData()).containsEntry("heapUtilization", "41%")
                    .containsEntry("totalThreads", 75)
                    .doesNotContainKey("secret");
        }
        verify(nifiClient).systemDiagnostics("node-1");
    }
}
