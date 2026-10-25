package com.linewell.dataelement.platform.audit;

import java.util.UUID;
import org.slf4j.MDC;

/** Request correlation only. A trace never grants access to an identity or tenant. */
public final class AuditTrace {
    private AuditTrace() { }
    public static String random() { return UUID.randomUUID().toString().replace("-", ""); }
    public static String requestId() { return value("request_id"); }
    public static String traceId() { return value("trace_id"); }
    private static String value(String key) {
        String value = MDC.get(key);
        return value == null ? random() : value;
    }
}
