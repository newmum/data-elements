package com.linewell.dataelement.feature.diagnostics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

class DiagnosticServiceTest {
    private final DiagnosticStore store = mock(DiagnosticStore.class);
    private final DiagnosticGateway gateway = mock(DiagnosticGateway.class);
    private final NifiDiagnosticEvidence nifi = mock(NifiDiagnosticEvidence.class);
    private final DiagnosticKnowledge knowledge = mock(DiagnosticKnowledge.class);
    private DiagnosticService service;
    private final Actor actor = new Actor("tenant-a", "alice");
    private final String id = "a".repeat(32), generation = "b".repeat(32);
    @BeforeEach void setup() {
        TenantContext.bind("tenant-a");
        var properties = new DiagnosticProperties(); properties.setEnabled(true); properties.setBaseUrl("http://127.0.0.1/v1"); properties.setApiKey("fake-test"); properties.setModel("test");
        service = new DiagnosticService(properties, store, nifi, knowledge, gateway);
        when(store.key(any(), anyString(), anyString())).thenAnswer(call -> call.getArgument(1) + ":" + call.getArgument(2));
        when(store.lock(actor)).thenReturn("lock");
        when(store.load(actor, "session", id, Session.class)).thenReturn(new Session(new Selection("client", null, null, null, "connection refused"), List.of()));
        when(knowledge.match(any())).thenReturn(List.of());
    }
    @AfterEach void close() { service.close(); TenantContext.clear(); }
    @Test void workerReceivesTenantAndSavesOnlyCompletedConversation() throws Exception {
        CountDownLatch saved = new CountDownLatch(1);
        doAnswer(call -> { saved.countDown(); return null; }).when(store).save(eq(actor), eq("session"), eq(id), any());
        doAnswer(call -> { assertThat(TenantContext.requireTenantId()).isEqualTo("tenant-a"); return "建议"; }).when(gateway).stream(any(), any(), any());
        service.messages(actor, id, new Question("help", generation));
        assertThat(saved.await(3, TimeUnit.SECONDS)).isTrue();
        verify(gateway, timeout(1000)).stream(any(), any(), any());
        verify(store).save(eq(actor), eq("session"), eq(id), argThat(value -> value instanceof Session s && s.messages().size() == 2));
        assertThat(TenantContext.requireTenantId()).isEqualTo("tenant-a");
    }
    @Test void cancellationTargetsOneGenerationAndDoesNotSavePartialAnswer() throws Exception {
        CountDownLatch started = new CountDownLatch(1), finished = new CountDownLatch(1);
        doAnswer(call -> {
            DiagnosticRun run = call.getArgument(1); started.countDown();
            try { while (!run.stopped()) Thread.sleep(10); } finally { finished.countDown(); }
            run.check(); return "partial";
        }).when(gateway).stream(any(), any(), any());
        service.messages(actor, id, new Question("help", generation));
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        service.cancel(actor, id, generation);
        assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
        verify(store, never()).save(eq(actor), eq("session"), anyString(), any());
        verify(store).save(actor, "stop", generation, true);
    }
    @Test void disabledFeatureNeverReadsRedisOrCallsGateway() {
        service.close();
        service = new DiagnosticService(new DiagnosticProperties(), store, nifi, knowledge, gateway);
        assertThatThrownBy(() -> service.create(actor, new Selection("client", null, null, null, "test"))).hasMessageContaining("503");
        verifyNoInteractions(store, nifi, knowledge, gateway);
    }
    @Test void deniedFlowNeverCallsGatewayOrCollectsEvidence() {
        when(store.load(actor, "session", id, Session.class)).thenReturn(new Session(new Selection("nifi", null, "flow", "error", null), List.of()));
        when(nifi.authorize(any())).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.messages(actor, id, new Question("help", generation))).hasMessageContaining("403");
        verify(nifi, never()).collect(any()); verifyNoInteractions(gateway);
    }
}
