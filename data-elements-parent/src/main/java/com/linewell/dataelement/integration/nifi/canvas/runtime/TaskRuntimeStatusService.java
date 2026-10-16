package com.linewell.dataelement.integration.nifi.canvas.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver.RuntimeNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Demand-driven shared sampling. No task-table writes and no per-task remote requests. */
@Service
public class TaskRuntimeStatusService {
    private final NifiNodeRuntimeResolver resolver;
    private final NifiClient nifi;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Semaphore connections = new Semaphore(512);
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "nifi-runtime-clock"); t.setDaemon(true); return t; });
    private final ExecutorService readers = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), r -> { Thread t = new Thread(r, "nifi-runtime-reader"); t.setDaemon(true); return t; });
    private final ExecutorService writers = Executors.newVirtualThreadPerTaskExecutor();
    public TaskRuntimeStatusService(NifiNodeRuntimeResolver resolver, NifiClient nifi) {
        this.resolver = resolver; this.nifi = nifi;
        clock.scheduleWithFixedDelay(this::tick, 250, 250, TimeUnit.MILLISECONDS);
    }
    static final class Entry {
        volatile RuntimeNode node;
        final Set<Subscriber> subscribers = ConcurrentHashMap.newKeySet();
        volatile RuntimeStatusIndex index;
        volatile long observedAt, attemptedAt, nextRead, lastAccess = System.currentTimeMillis(), generation, readyGeneration = -1;
        volatile String error = "";
        int failures;
        final AtomicBoolean queued = new AtomicBoolean();
        Entry(RuntimeNode node) { this.node = node; }
    }
    final class Subscriber {
        final SseEmitter emitter = new SseEmitter(60000L);
        final AtomicBoolean writing = new AtomicBoolean();
        volatile Map<String, Object> pending;
        volatile ScheduledFuture<?> renewal;
        volatile boolean disconnected;
        Subscriber(Entry entry) {
            Runnable remove = () -> {
                if (entry.subscribers.remove(this)) connections.release();
                if (renewal != null) renewal.cancel(false);
            };
            emitter.onCompletion(remove); emitter.onTimeout(remove); emitter.onError(e -> remove.run());
        }
        void offer(Map<String, Object> event) {
            if (disconnected) return;
            pending = event;
            if (!writing.compareAndSet(false, true)) return;
            writers.submit(() -> {
                try {
                    Map<String, Object> next;
                    while ((next = pending) != null) { pending = null; emitter.send(SseEmitter.event().name("runtime").data(next)); }
                } catch (IOException e) {
                    // The servlet container finishes a failed socket and calls onError.
                    // Completing it again can try to flush an already unusable response.
                    disconnected = true; pending = null;
                } catch (Exception e) { disconnected = true; pending = null; emitter.completeWithError(e); }
                finally { writing.set(false); if (pending != null) offer(pending); }
            });
        }
    }
    private synchronized Entry current() {
        TenantContext.requireTenantId();
        RuntimeNode node = resolver.resolve();
        entries.entrySet().removeIf(e -> e.getValue().subscribers.isEmpty() && System.currentTimeMillis() - e.getValue().lastAccess > 60000);
        if (!entries.containsKey(node.tokenCacheKey()) && entries.size() >= 16) throw new IllegalStateException("实时状态节点繁忙，请稍后重试");
        Entry entry = entries.computeIfAbsent(node.tokenCacheKey(), key -> new Entry(node));
        if (!entry.node.equals(node)) { entry.node = node; entry.generation++; entry.nextRead = 0; }
        entry.lastAccess = System.currentTimeMillis();
        return entry;
    }
    private void refresh(Entry entry) {
      synchronized (entry) {
        long now = System.currentTimeMillis();
        if (now < entry.nextRead) return;
        long generation = entry.generation;
        try {
            RuntimeStatusIndex index = RuntimeStatusIndex.parse(nifi.getRuntimeRootStatus(entry.node));
            if (generation != entry.generation) return;
            entry.index = index; entry.readyGeneration = generation; entry.observedAt = System.currentTimeMillis(); entry.error = ""; entry.failures = 0;
            entry.nextRead = entry.observedAt + 2000;
        } catch (Exception e) {
            entry.failures++;
            int status = e instanceof com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException n ? n.status()
                    : e instanceof org.springframework.web.client.HttpStatusCodeException h ? h.getStatusCode().value() : 0;
            entry.error = status == 401 || status == 403 ? "NiFi 认证或授权失败（HTTP " + status + "），请核对节点配置"
                    : status > 0 ? "NiFi 状态读取失败（HTTP " + status + "）"
                    : e instanceof IllegalStateException ? e.getMessage() : "NiFi 状态读取超时或连接失败，请检查节点网络";
            entry.nextRead = System.currentTimeMillis() + Math.min(30000L, 2000L << Math.min(4, entry.failures - 1));
        } finally { entry.attemptedAt = System.currentTimeMillis(); }
      }
    }
    private void tick() {
        try {
            long now = System.currentTimeMillis();
            for (Entry entry : entries.values()) {
                if (entry.subscribers.isEmpty()) continue;
                if (now >= entry.nextRead && entry.queued.compareAndSet(false, true)) {
                    try { readers.submit(() -> { try { refresh(entry); broadcast(entry); } finally { entry.queued.set(false); } }); }
                    catch (RejectedExecutionException e) { entry.queued.set(false); }
                }
                if (now / 15000 != (now - 250) / 15000) broadcast(entry);
            }
        } catch (Exception ignored) { /* One disconnected viewer must not terminate shared sampling. */ }
    }
    private void broadcast(Entry entry) { Map<String, Object> event = event(entry); entry.subscribers.forEach(s -> s.offer(event)); }
    private Map<String, Object> event(Entry entry) {
        // Invalidation metadata only: never send the root tree or another role's task IDs.
        return Map.of("revision", revision(entry), "fresh", fresh(entry), "observedAt", entry.observedAt,
                "checkedAt", entry.attemptedAt, "message", entry.error);
    }
    private String revision(Entry entry) { return (entry.index == null ? "unavailable" : entry.index.revision()) + (fresh(entry) ? ":ok" : ":unavailable"); }
    private boolean fresh(Entry entry) { return entry.index != null && entry.readyGeneration == entry.generation && entry.error.isEmpty() && System.currentTimeMillis() - entry.observedAt < 10000; }
    public Map<String, Object> snapshot() {
        Entry entry = current(); refresh(entry);
        Map<String, Object> result = new HashMap<>(event(entry));
        result.put("index", entry.index); result.put("nodeId", entry.node.id());
        for (int status = 0; status <= 3; status++) {
            List<List<String>> chunks = new ArrayList<>();
            if (entry.index != null && fresh(entry)) {
                final int wanted = status;
                List<String> ids = entry.index.groups().entrySet().stream().filter(e -> e.getValue().taskStatus() == wanted).map(Map.Entry::getKey).toList();
                for (int offset = 0; offset < ids.size(); offset += 500) chunks.add(ids.subList(offset, Math.min(offset + 500, ids.size())));
            }
            result.put("groups" + status, chunks);
        }
        return result;
    }
    public SseEmitter subscribe() {
        Entry entry = current();
        if (!connections.tryAcquire()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "实时状态连接繁忙，请稍后重试");
        Subscriber subscriber = new Subscriber(entry); entry.subscribers.add(subscriber);
        // Finish normally before MVC's fallback timeout, then authenticate the renewal.
        subscriber.renewal = clock.schedule(subscriber.emitter::complete, 55, TimeUnit.SECONDS);
        subscriber.offer(event(entry));
        return subscriber.emitter;
    }
    public void invalidateTenant() {
        String tenantId = TenantContext.getTenantId();
        entries.values().stream().filter(e -> e.node.tenantId().equals(tenantId)).forEach(e -> { e.generation++; e.nextRead = 0; });
    }
    public Map<String, Object> describe(Map<String, Object> snapshot, String pg, String lifecycle) {
        boolean deployed = pg != null && !pg.isBlank();
        RuntimeStatusIndex index = (RuntimeStatusIndex) snapshot.get("index");
        RuntimeStatusIndex.Group group = deployed && index != null ? index.groups().get(pg) : null;
        String state = !deployed ? "DEPLOY_FAILED".equals(lifecycle) ? "INVALID" : "RUNNING".equals(lifecycle) ? "UNKNOWN" : "UNDEPLOYED"
                : !Boolean.TRUE.equals(snapshot.get("fresh")) ? "UNKNOWN" : group == null ? "MISSING" : group.state();
        int taskStatus = switch (state) { case "RUNNING", "STOPPING" -> 1; case "INVALID", "RUN_ERROR" -> 2; case "UNKNOWN", "MISSING" -> 3; default -> 0; };
        String label = switch (state) { case "RUNNING" -> "运行中"; case "STOPPING" -> "停止中"; case "STOPPED" -> "已停止"; case "EMPTY" -> "空流程"; case "INVALID", "RUN_ERROR" -> "运行异常"; case "MISSING" -> "流程不存在"; case "UNDEPLOYED" -> "未部署"; default -> "状态待确认"; };
        Map<String, Object> value = new HashMap<>();
        value.put("taskStatus", taskStatus); value.put("runtimeState", state); value.put("runtimeLabel", label);
        value.put("running", group != null && group.running()); value.put("runtimeFresh", Boolean.TRUE.equals(snapshot.get("fresh")));
        value.put("runtimeObservedAt", snapshot.get("observedAt")); value.put("runtimeMessage", snapshot.get("message"));
        value.put("invalidProcessorCount", group == null ? 0 : group.invalidCount());
        return value;
    }
    @PreDestroy public void close() { entries.values().forEach(e -> e.subscribers.forEach(s -> s.emitter.complete())); clock.shutdownNow(); readers.shutdownNow(); writers.shutdownNow(); }
}
