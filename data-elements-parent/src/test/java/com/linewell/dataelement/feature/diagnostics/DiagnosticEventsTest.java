package com.linewell.dataelement.feature.diagnostics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class DiagnosticEventsTest {
    private final DiagnosticStore store = mock(DiagnosticStore.class);
    private final ExecutorService captures = mock(ExecutorService.class);
    private DiagnosticEvents events;
    @BeforeEach void setup() {
        var properties = new DiagnosticProperties(); properties.setEnabled(true); properties.setBaseUrl("http://localhost/v1"); properties.setApiKey("fake-test"); properties.setModel("test");
        // Complete the supplied task deterministically; success assertions must not race a 150ms wall clock.
        when(captures.submit(any(Runnable.class))).thenAnswer(call -> {
            FutureTask<Void> saved = new FutureTask<>(call.getArgument(0, Runnable.class), null);
            saved.run();
            return saved;
        });
        events = new DiagnosticEvents(store, properties, captures); TenantContext.bind("tenant-a");
        when(store.id()).thenReturn("a".repeat(32));
    }
    @AfterEach void close() { events.close(); TenantContext.clear(); }
    @Test void snapshotUsesCurrentOwnerAndContainsNoCredential() {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsString).thenReturn("alice");
            Map<String, Object> error = new LinkedHashMap<>(Map.of("code", "DB-AUTH-003", "detail", "password=fake-secret Authorization: Bearer fake-bearer"));
            events.attach(error, "POST", "/registered/connection");
            assertThat(error).containsEntry("diagnosticRef", "a".repeat(32));
            var snapshot = ArgumentCaptor.forClass(Evidence.class);
            verify(store).save(eq(new Actor("tenant-a", "alice")), eq("event"), anyString(), snapshot.capture());
            assertThat(snapshot.getValue().content()).doesNotContain("fake-secret", "fake-bearer");
        }
    }
    @Test void recursionAndPermissionFailuresNeverProduceReferences() {
        events.attach(new LinkedHashMap<>(Map.of("code", "MAGIC-BUSINESS-403")), "GET", "/somewhere");
        events.attach(new LinkedHashMap<>(Map.of("code", "ERROR")), "POST", "/sym/ai/diagnostics/session/messages");
        verifyNoInteractions(store);
    }
    @Test void storageFailureLeavesOriginalErrorIntact() {
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsString).thenReturn("alice");
            doThrow(new IllegalStateException("redis offline")).when(store).save(any(), anyString(), anyString(), any());
            Map<String, Object> error = new LinkedHashMap<>(Map.of("code", "API-FAILED", "message", "original"));
            events.attach(error, "GET", "/test");
            assertThat(error).containsExactlyInAnyOrderEntriesOf(Map.of("code", "API-FAILED", "message", "original"));
            verify(store).save(eq(new Actor("tenant-a", "alice")), eq("event"), anyString(), any());
        }
    }

    @Test void captureTimeoutCancelsTheTaskAndLeavesOriginalErrorIntact() throws Exception {
        Future<?> saved = mock(Future.class);
        doReturn(saved).when(captures).submit(any(Runnable.class));
        when(saved.get(150, TimeUnit.MILLISECONDS)).thenThrow(new TimeoutException("capture deadline"));
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsString).thenReturn("alice");
            Map<String, Object> original = Map.of("code", "API-FAILED", "message", "original");
            Map<String, Object> error = new LinkedHashMap<>(original);
            events.attach(error, "GET", "/test");
            assertThat(error).containsExactlyInAnyOrderEntriesOf(original);
            verify(saved).get(150, TimeUnit.MILLISECONDS);
            verify(saved).cancel(true);
            verify(store, never()).save(any(), anyString(), anyString(), any());
        }
    }

    @Test void fullCaptureQueueLeavesOriginalErrorIntact() {
        doThrow(new RejectedExecutionException("capture queue full")).when(captures).submit(any(Runnable.class));
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsString).thenReturn("alice");
            Map<String, Object> original = Map.of("code", "API-FAILED", "message", "original");
            Map<String, Object> error = new LinkedHashMap<>(original);
            events.attach(error, "GET", "/test");
            assertThat(error).containsExactlyInAnyOrderEntriesOf(original);
            verify(captures).submit(any(Runnable.class));
            verify(store, never()).save(any(), anyString(), anyString(), any());
        }
    }

    @Test void interruptedCapturePreservesInterruptAndCancelsTheTask() throws Exception {
        Future<?> saved = mock(Future.class);
        doReturn(saved).when(captures).submit(any(Runnable.class));
        when(saved.get(150, TimeUnit.MILLISECONDS)).thenThrow(new InterruptedException("request interrupted"));
        try (var login = mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsString).thenReturn("alice");
            Map<String, Object> original = Map.of("code", "API-FAILED", "message", "original");
            Map<String, Object> error = new LinkedHashMap<>(original);
            events.attach(error, "GET", "/test");
            assertThat(error).containsExactlyInAnyOrderEntriesOf(original);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(saved).cancel(true);
        } finally {
            Thread.interrupted();
        }
    }

    @Test void springUsesTheProductionCaptureConstructor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DiagnosticStore.class, () -> store);
            context.registerBean(DiagnosticProperties.class, DiagnosticProperties::new);
            context.register(DiagnosticEvents.class);
            context.refresh();
            assertThat(context.getBean(DiagnosticEvents.class)).isNotNull();
        }
    }
}
