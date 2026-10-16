package com.linewell.dataelement.feature.diagnostics;

import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.Question;
import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.Selection;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/sym/ai/diagnostics")
public class DiagnosticController {
    private final DiagnosticService diagnostics;
    public DiagnosticController(DiagnosticService diagnostics) { this.diagnostics = diagnostics; }
    @GetMapping("/capabilities") public Map<String, Object> capabilities() {
        DiagnosticEvents.currentActor();
        return Map.of("enabled", diagnostics.available(), "idleMinutes", 30);
    }
    @PostMapping public Map<String, Object> create(@RequestBody Selection selection) {
        return Map.of("sessionId", diagnostics.create(DiagnosticEvents.currentActor(), selection), "idleMinutes", 30);
    }
    @DeleteMapping("/{sessionId}/messages/{generationId}")
    public Map<String, Object> stop(@PathVariable String sessionId, @PathVariable String generationId) {
        diagnostics.cancel(DiagnosticEvents.currentActor(), sessionId, generationId);
        return Map.of("stopped", true);
    }
    @PostMapping(value = "/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter messages(@PathVariable String sessionId, @RequestBody(required = false) Question question,
                              jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-store"); response.setHeader("X-Accel-Buffering", "no");
        try { return diagnostics.messages(DiagnosticEvents.currentActor(), sessionId, question); }
        finally { com.linewell.dataelement.platform.tenant.domain.TenantContext.clear(); }
    }
}
