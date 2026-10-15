package com.linewell.dataelement.integration.nifi.canvas.errors;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FlowFailureDetailsTest {
    @Test void preservesNativeResponseAndNestedCauseWithoutSecrets() {
        var error = new IllegalStateException("候选流程失败", new NifiException("HTTP failed",400,
                "{\"message\":\"Record Reader invalid\",\"password\":\"private-value\",\"token\":\"secret-value\"}"));
        assertThat(FlowFailureDetails.describe(error)).contains("候选流程失败", "Record Reader invalid", "HTTP 400", "已隐藏")
                .doesNotContain("private-value", "secret-value");
    }
    @Test void nullMessagesAndLargeResponsesAlwaysHaveBoundedDetails() {
        assertThat(FlowFailureDetails.describe(new IllegalStateException())).isEqualTo("IllegalStateException");
        assertThat(FlowFailureDetails.describe(new RuntimeException("x".repeat(50000)))).hasSizeLessThan(12100);
        assertThat(FlowFailureDetails.safe("jdbc:mysql://user:private-value@host/db?password=secret-value&x=1"))
                .doesNotContain("private-value", "secret-value").contains("&x=1");
    }
}
