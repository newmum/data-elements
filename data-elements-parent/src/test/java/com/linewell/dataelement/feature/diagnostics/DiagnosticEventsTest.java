package com.linewell.dataelement.feature.diagnostics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class DiagnosticEventsTest {
    private final DiagnosticStore store = mock(DiagnosticStore.class);
    private DiagnosticEvents events;
    @BeforeEach void setup() {
        var properties = new DiagnosticProperties(); properties.setEnabled(true); properties.setBaseUrl("http://localhost/v1"); properties.setApiKey("fake-test"); properties.setModel("test");
        events = new DiagnosticEvents(store, properties); TenantContext.bind("tenant-a");
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
        }
    }
}
