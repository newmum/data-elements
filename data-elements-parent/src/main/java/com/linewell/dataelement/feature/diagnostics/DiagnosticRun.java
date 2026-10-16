package com.linewell.dataelement.feature.diagnostics;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cancellation closes a blocked HTTP body and interrupts only this diagnostic's worker. */
final class DiagnosticRun {
    private final AtomicBoolean stopped = new AtomicBoolean();
    volatile Thread worker;
    Runnable finish = () -> {};
    private volatile CompletableFuture<?> pending;
    private volatile Closeable body;
    void pending(CompletableFuture<?> value) { pending = value; if (stopped.get()) value.cancel(true); }
    void body(Closeable value) { body = value; if (stopped.get()) close(value); }
    boolean stopped() { return stopped.get(); }
    void check() { if (stopped.get() || Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException(); }
    void cancel() {
        if (stopped.getAndSet(true)) return;
        if (pending != null) pending.cancel(true);
        close(body);
        Thread thread = worker;
        if (thread != null && thread != Thread.currentThread()) thread.interrupt();
        finish.run();
    }
    private static void close(Closeable value) { if (value != null) try { value.close(); } catch (Exception ignored) {} }
}
