package com.linewell.dataelement.feature.diagnostics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.Evidence;
import java.time.Instant;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Reviewed, versioned runbooks; no internet requests or embedding service required. */
@Component
public class DiagnosticKnowledge {
    public record Runbook(String id, List<String> terms, String content, String reference) {}
    private final List<Runbook> runbooks;
    public DiagnosticKnowledge(ObjectMapper json) throws java.io.IOException {
        try (var input = new ClassPathResource("diagnostics/runbooks.json").getInputStream()) {
            runbooks = List.of(json.readValue(input, Runbook[].class));
        }
    }
    public List<Evidence> match(List<Evidence> evidence) {
        String text = evidence.stream().map(Evidence::content).reduce("", (a, b) -> a + "\n" + b).toLowerCase(Locale.ROOT);
        String error = evidence.isEmpty() ? "" : evidence.getFirst().content().toLowerCase(Locale.ROOT);
        java.util.function.ToIntFunction<Runbook> score = r -> r.terms().stream().mapToInt(term -> error.contains(term.toLowerCase(Locale.ROOT)) ? 10 : text.contains(term.toLowerCase(Locale.ROOT)) ? 1 : 0).sum();
        return runbooks.stream().filter(r -> score.applyAsInt(r) > 0).sorted(Comparator.comparingInt(score).reversed())
                .limit(5).map(r -> new Evidence("runbook-" + r.id(), "故障手册 " + r.id(), Instant.now(), r.content() + "\n参考: " + r.reference())).toList();
    }
}
