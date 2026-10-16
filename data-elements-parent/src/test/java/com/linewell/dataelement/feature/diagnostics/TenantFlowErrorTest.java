package com.linewell.dataelement.feature.diagnostics;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linewell.dataelement.integration.nifi.canvas.errors.*;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TenantFlowErrorTest {
    @TempDir Path root;
    @AfterEach void clear() { TenantContext.clear(); }
    @Test void sameFlowIdCannotReadOrClearAnotherTenantAndUnscopedLegacyIsIgnored() throws Exception {
        Files.createDirectories(root.resolve("errors"));
        Files.writeString(root.resolve("errors/flow-1.json"), "[{\"message\":\"ambiguous legacy\"}]");
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var store = new ErrorService(root.toString(), mapper);
        assertThatThrownBy(() -> store.list("flow-1")).hasMessageContaining("租户");
        try (var ignored = TenantContext.use("tenant-a")) {
            assertThat(store.list("flow-1")).isEmpty();
            store.add("flow-1", new FlowError(null, FlowError.ErrorLevel.ERROR, FlowError.ErrorPhase.RUNTIME, null, null, null, "A", null, null, Instant.now()));
        }
        try (var ignored = TenantContext.use("tenant-b")) { assertThat(store.list("flow-1")).isEmpty(); store.clear("flow-1"); }
        try (var ignored = TenantContext.use("tenant-a")) {
            assertThat(store.list("flow-1")).singleElement().extracting(FlowError::message).isEqualTo("A");
            assertThat(new ErrorService(root.toString(), mapper).list("flow-1")).hasSize(1);
        }
        assertThat(Files.exists(root.resolve("errors/flow-1.json"))).isTrue();
    }
}
