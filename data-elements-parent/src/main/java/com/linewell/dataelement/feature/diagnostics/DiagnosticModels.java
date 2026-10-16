package com.linewell.dataelement.feature.diagnostics;

import java.time.Instant;
import java.util.List;

public final class DiagnosticModels {
    private DiagnosticModels() {}
    public record Actor(String tenantId, String userId) {}
    public record Evidence(String id, String source, Instant observedAt, String content) {}
    public record Selection(String source, String diagnosticRef, String pipelineId, String errorId, String clientError) {}
    public record Message(String role, String content) {}
    public record Session(Selection selection, List<Message> messages) {}
    public record Question(String message, String generationId) {
        public Question(String message) { this(message, null); }
    }
}
