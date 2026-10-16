package com.linewell.dataelement.feature.diagnostics;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.diagnostics.DiagnosticModels.Evidence;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DiagnosticKnowledgeTest {
    @ParameterizedTest @CsvSource({"DB-CONNECTION-001,connection", "DB-AUTH-003,authentication", "No suitable driver,driver", "字段映射类型失败,mapping", "Controller service INVALID,controller-service", "backpressure queued,queue", "DEPLOY_FAILED,deployment", "DB-TIMEOUT-002,timeout"})
    void typicalFailureSelectsItsReviewedRunbook(String error, String expected) throws Exception {
        var knowledge = new DiagnosticKnowledge(new ObjectMapper());
        assertThat(knowledge.match(List.of(new Evidence("error", "test", Instant.now(), error))))
                .extracting(Evidence::id).contains("runbook-" + expected);
    }
}
