package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper.NifiPipelineTMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PipelineRepositoryTenantScopeTest {
    private final NifiPipelineTMapper mapper = mock(NifiPipelineTMapper.class);
    private final PipelineRepository repository = new PipelineRepository(new ObjectMapper(), mapper);

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void missingTenantNeverQueriesPipelineTable() {
        assertThrows(TenantAccessException.class, repository::findAll);
        assertThrows(TenantAccessException.class, () -> repository.findRunningPage(1, 20));
        assertThrows(TenantAccessException.class, () -> repository.findMonitorablePage(1, 20));
        assertThrows(TenantAccessException.class, () -> repository.findById("flow"));
        assertThrows(TenantAccessException.class, () -> repository.delete("flow"));
        verifyNoInteractions(mapper);
    }

    @Test
    void controlScopeCannotReadTenantFlowsEvenWhenItRetainsATenantId() {
        try (var tenant = TenantContext.use("tenant-a"); var control = TenantContext.control()) {
            assertThrows(TenantAccessException.class, repository::findAll);
            assertThrows(TenantAccessException.class, () -> repository.findById("flow"));
            assertThrows(TenantAccessException.class, () -> repository.delete("flow"));
            verifyNoInteractions(mapper);
        }
    }

    @Test
    void tenantScopeCanReadFlows() {
        when(mapper.selectList(any())).thenReturn(List.of());
        try (var tenant = TenantContext.use("tenant-a")) {
            assertTrue(repository.findAll().isEmpty());
        }
    }
}
