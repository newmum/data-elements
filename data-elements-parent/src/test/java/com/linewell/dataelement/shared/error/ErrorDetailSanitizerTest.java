package com.linewell.dataelement.shared.error;

import static org.assertj.core.api.Assertions.*;
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
        assertThat(ErrorDetailSanitizer.text("a".repeat(100000))).hasSizeLessThan(33000).contains("证据不完整");
    }
}
