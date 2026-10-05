package com.linewell.dataelement.platform.tenant.domain;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Request and job scoped tenant identity.
 *
 * <p>The context is intentionally thread local. Async and scheduled work must
 * enter an explicit tenant scope instead of inheriting a web request by accident.</p>
 */
public final class TenantContext {

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static String getTenantId() {
        State state = CURRENT.get();
        return state == null ? null : state.tenantId();
    }

    public static String requireTenantId() {
        String tenantId = getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantAccessException("TENANT-CONTEXT-MISSING", "当前请求未绑定租户");
        }
        return tenantId;
    }

    public static boolean isIgnored() {
        State state = CURRENT.get();
        return state != null && state.ignoreIsolation();
    }

    public static boolean usesControlDatabase() {
        State state = CURRENT.get();
        return state != null && state.controlDatabase();
    }

    public static Scope use(String tenantId) {
        String normalized = normalize(tenantId);
        State previous = CURRENT.get();
        CURRENT.set(new State(normalized, false, false));
        return new Scope(previous);
    }

    /**
     * Replaces the tenant bound to the current request without creating a
     * nested scope. This is reserved for request bootstrapping flows that
     * establish a tenant only after authentication succeeds; the web
     * interceptor clears the binding when the request completes.
     */
    public static void bind(String tenantId) {
        CURRENT.set(new State(normalize(tenantId), false, false));
    }

    public static Scope ignore() {
        State previous = CURRENT.get();
        CURRENT.set(new State(
                previous == null ? null : previous.tenantId(),
                true,
                previous == null || previous.controlDatabase()
        ));
        return new Scope(previous);
    }

    public static Scope control() {
        State previous = CURRENT.get();
        CURRENT.set(new State(
                previous == null ? null : previous.tenantId(),
                previous != null && previous.ignoreIsolation(),
                true
        ));
        return new Scope(previous);
    }

    public static <T> T call(String tenantId, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        try (Scope ignored = use(tenantId)) {
            return supplier.get();
        }
    }

    public static void run(String tenantId, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        try (Scope ignored = use(tenantId)) {
            runnable.run();
        }
    }

    public static void clear() {
        CURRENT.remove();
    }

    private static String normalize(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new TenantAccessException("TENANT-ID-MISSING", "租户ID不能为空");
        }
        String value = tenantId.trim();
        if (!value.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new TenantAccessException("TENANT-ID-INVALID", "租户ID格式不正确");
        }
        return value;
    }

    private record State(
            String tenantId,
            boolean ignoreIsolation,
            boolean controlDatabase
    ) {
    }

    public static final class Scope implements AutoCloseable {

        private final State previous;
        private boolean closed;

        private Scope(State previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
            closed = true;
        }
    }
}
