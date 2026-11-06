package com.linewell.dataelement.shared.error;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ErrorDetailSanitizerTest {
    @Test void removesCredentialsAndBusinessLiteralsBeforePersistence() {
        String raw = "password=pw1 client_secret=secret2 Authorization: Bearer abc.def.ghi\n"
                + "{\"access_token\":\"secret3\",\"apiKey\":\"secret4\"}\n"
                + "jdbc:mysql://user:secret5@host/db?password=secret6&x=1\n"
                + "select * from t where person='张三' and id=123456789012345678\n"
                + "-----BEGIN PRIVATE KEY-----\nprivate-material\n-----END PRIVATE KEY-----";
        assertThat(ErrorDetailSanitizer.text(raw)).doesNotContain("pw1", "secret2", "abc.def.ghi", "secret3", "secret4", "secret5", "secret6", "张三", "123456789012345678", "private-material")
                .contains("已脱敏", "select * from t");
    }
    @Test void embeddedBusinessPayloadAndBindParametersAreNotEvidence() {
        assertThat(ErrorDetailSanitizer.errorText("SQLException: record={\"name\":\"private-person\"}\n### SQL: select x from t where x=10\nParameters: private-value"))
                .doesNotContain("private-person", "private-value", "where x=10").contains("SQLException", "原文已移除");
    }
    @Test void hugeInputHasExplicitTruncationMarker() {
        assertTimeout(Duration.ofSeconds(3), () ->
                assertThat(ErrorDetailSanitizer.text("a".repeat(100000))).hasSizeLessThan(33000).contains("证据不完整"));
    }
    @Test void longTokensWithUrlAndEmailDelimitersStayBoundedAndRedacted() {
        assertTimeout(Duration.ofSeconds(3), () -> {
            String prefix = "a".repeat(30000);
            assertThat(ErrorDetailSanitizer.text(prefix + "@example.invalid"))
                    .isEqualTo("[邮箱已脱敏]");
            assertThat(ErrorDetailSanitizer.text(prefix + " http://user:secret:with:colons@host/db"))
                    .doesNotContain("user:secret", "with:colons").contains("http://[已脱敏]@host/db");
            assertThat(ErrorDetailSanitizer.text(prefix + "://host/path invalid@host"))
                    .endsWith("://host/path invalid@host");
            assertThat(ErrorDetailSanitizer.text("jdbc:mysql://user:secret@host/db"))
                    .isEqualTo("jdbc:mysql://[已脱敏]@host/db");
        });
    }
}
