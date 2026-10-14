package com.linewell.dataelement.mapping.core;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

/** One fixed clock and stable generator seed per input; contains no connections or credentials. */
public record ExecutionContext(Instant clock, ZoneId zone, Locale locale, String inputIdentity) {
    public ExecutionContext {
        if (clock == null || zone == null || locale == null || inputIdentity == null) throw new IllegalArgumentException("执行上下文不完整");
    }
    public String generatedId(long row, String rule) {
        return UUID.nameUUIDFromBytes((inputIdentity + ":" + row + ":" + rule).getBytes(StandardCharsets.UTF_8)).toString();
    }
    /** All UUID bits are retained; explicit lengths avoid ambiguous namespace concatenation. */
    public String compactGeneratedId(long row, String rule) {
        String seed = "haitong:uuid32:v1:" + inputIdentity.length() + ":" + inputIdentity
                + ":" + row + ":" + rule.length() + ":" + rule;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }
}
