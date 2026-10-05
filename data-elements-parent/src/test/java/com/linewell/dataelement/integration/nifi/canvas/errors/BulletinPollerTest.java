package com.linewell.dataelement.integration.nifi.canvas.errors;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.platform.tenant.application.TenantExecutionCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;

class BulletinPollerTest {

    private final PipelineRepository repository = mock(PipelineRepository.class);
    private final NifiClient nifi = mock(NifiClient.class);
    private final ErrorService errors = mock(ErrorService.class);
    private final TenantExecutionCatalog tenants = mock(TenantExecutionCatalog.class);
    private final BulletinPoller poller = new BulletinPoller(repository, nifi, errors, tenants);

    @Test
    void stoppedPipelineDoesNotContactNifi() {
        when(tenants.activeTenantIds()).thenReturn(List.of("tenant-a"));
        when(repository.findAll()).thenReturn(List.of(pipeline("stopped", PipelineStatus.STOPPED)));

        poller.poll();

        verifyNoInteractions(nifi, errors);
    }

    @Test
    void runningPipelineStillPollsBulletins() throws Exception {
        when(tenants.activeTenantIds()).thenReturn(List.of("tenant-a"));
        when(repository.findAll()).thenReturn(List.of(pipeline("running", PipelineStatus.RUNNING)));
        when(nifi.getBulletinBoard("process-group", 0L))
                .thenReturn(new ObjectMapper().readTree("{\"bulletinBoard\":{\"bulletins\":[]}}"));

        poller.poll();

        verify(nifi).getBulletinBoard("process-group", 0L);
    }

    private static Pipeline pipeline(String id, PipelineStatus status) {
        return new Pipeline(id, id, null, null, null, null, "process-group", status,
                null, null, null, null, null);
    }
}
