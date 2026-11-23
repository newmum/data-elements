package com.linewell.dataelement.integration.nifi.canvas.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver.RuntimeNode;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiNodeRuntimeResolver.NodeNotConfiguredException;
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
    private final Set<Subscriber> unconfigured = ConcurrentHashMap.newKeySet();
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
        volatile String name = "", networkCode = "";
        int failures;
        final AtomicBoolean queued = new AtomicBoolean();
        Entry(RuntimeNode node) { this.node = node; }
    }
    final class Subscriber {
        final SseEmitter emitter = new SseEmitter(60000L);
        final AtomicBoolean writing = new AtomicBoolean();
        volatile Map<String, Object> pending;
        volatile ScheduledFuture<?> renewal;
        volatile ScheduledFuture<?> heartbeat;
        volatile boolean disconnected;
        List<Entry> sources;
        String defaultNodeId;
        Set<String> requestedIds;
        Subscriber(Entry entry) {
            this(List.of(entry), null);
        }
        Subscriber(List<Entry> sources, String defaultNodeId) {
            this.sources = sources;
            this.defaultNodeId = defaultNodeId;
            Runnable remove = () -> {
                boolean removed = unconfigured.remove(this);
                for (Entry entry : sources) removed |= entry.subscribers.remove(this);
                if (removed) connections.release();
                if (renewal != null) renewal.cancel(false);
                if (heartbeat != null) heartbeat.cancel(false);
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
    private synchronized Entry current(String assignedNodeId) {
        TenantContext.requireTenantId();
        RuntimeNode node = assignedNodeId == null || assignedNodeId.isBlank()
                ? resolver.resolve() : resolver.resolveAssignedNode(assignedNodeId);
        return currentNode(node);
    }
    private synchronized Entry currentNode(RuntimeNode node) {
        entries.entrySet().removeIf(e -> e.getValue().subscribers.isEmpty() && System.currentTimeMillis() - e.getValue().lastAccess > 60000);
        if (!entries.containsKey(node.tokenCacheKey()) && entries.size() >= 512) throw new IllegalStateException("实时状态节点繁忙，请稍后重试");
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
    private void broadcast(Entry entry) {
        entry.subscribers.forEach(s -> s.offer(s.requestedIds != null ? aggregateEvent(s.sources, s.requestedIds)
                : s.defaultNodeId == null ? event(entry) : aggregateEvent(s.sources)));
    }
    private Map<String, Object> event(Entry entry) {
        // Invalidation metadata only: never send the root tree or another role's task IDs.
        return Map.of("revision", revision(entry), "fresh", fresh(entry), "observedAt", entry.observedAt,
                "checkedAt", entry.attemptedAt, "message", entry.error);
    }
    private String revision(Entry entry) { return (entry.index == null ? "unavailable" : entry.index.revision()) + (fresh(entry) ? ":ok" : ":unavailable"); }
    private boolean fresh(Entry entry) { return entry.index != null && entry.readyGeneration == entry.generation && entry.error.isEmpty() && System.currentTimeMillis() - entry.observedAt < 10000; }
    public Map<String, Object> snapshot() {
        return snapshotForNode(null);
    }
    public Map<String, Object> snapshotForNode(String assignedNodeId) {
        Entry entry = current(assignedNodeId); refresh(entry);
        return snapshot(entry);
    }
    private Map<String, Object> snapshot(Entry entry) {
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

    private List<Entry> samplingEntries() {
        return samplingEntries(null);
    }
    private List<Entry> samplingEntries(Set<String> nodeIds) {
        return samplingEntries(nodeIds, false);
    }
    private List<Entry> samplingEntries(Set<String> nodeIds, boolean includeDefault) {
        var configurations = resolver.samplingConfigurations();
        var result = new ArrayList<Entry>();
        for (var row : configurations) {
            if (nodeIds != null && !nodeIds.contains(Objects.toString(row.get("tid"), ""))
                    && !(includeDefault && row == configurations.getFirst())) continue;
            RuntimeNode node = resolver.runtimeNode(row);
            Entry entry = currentNode(node);
            entry.name = Objects.toString(row.get("node_name"), node.code());
            entry.networkCode = Objects.toString(row.get("network_code"), "");
            entry.lastAccess = System.currentTimeMillis();
            result.add(entry);
        }
        return result;
    }

    private CompletableFuture<Void> scheduleRead(Entry entry) {
        if (System.currentTimeMillis() < entry.nextRead || !entry.queued.compareAndSet(false, true))
            return CompletableFuture.completedFuture(null);
        var done = new CompletableFuture<Void>();
        try {
            readers.submit(() -> {
                try { refresh(entry); broadcast(entry); done.complete(null); }
                catch (RuntimeException failure) { done.completeExceptionally(failure); }
                finally { entry.queued.set(false); }
            });
        } catch (RejectedExecutionException busy) { entry.queued.set(false); done.complete(null); }
        return done;
    }

    private Map<String, Object> aggregateEvent(List<Entry> sources) {
        return aggregateEvent(sources, null);
    }
    private Map<String, Object> aggregateEvent(List<Entry> sources, Set<String> requestedIds) {
        int available = (int) sources.stream().filter(this::fresh).count();
        int total = requestedIds == null ? sources.size() : requestedIds.size();
        String revision = sources.stream().map(e -> e.node.id() + "=" + revision(e)).sorted()
                .collect(java.util.stream.Collectors.joining("|"));
        long observed = sources.stream().mapToLong(e -> e.observedAt).filter(t -> t > 0).min().orElse(0);
        return Map.of("revision", revision.isEmpty() ? "unavailable" : revision,
                "fresh", requestedIds != null ? available == total : total > 0 && available == total,
                "partial", available > 0 && available < total,
                "observedAt", observed, "checkedAt", sources.stream().mapToLong(e -> e.attemptedAt).max().orElse(0),
                "message", requestedIds != null ? total == 0 ? "本页暂无已部署任务节点"
                        : available == total ? "" : "本页任务关联 " + total + " 个节点，" + available + " 个已确认，" + (total - available) + " 个待确认"
                        : sources.isEmpty() ? "当前租户尚未配置 NiFi 节点，请在节点管理中配置并启用后查看实时状态"
                        : available == sources.size() ? "" : "部分节点状态待确认，已连通节点的任务继续显示");
    }

    /** Read each configured node once, with a bounded first-response wait and shared backoff. */
    public Map<String, Object> snapshotAll() {
        return snapshotForNodes(null);
    }
    public Map<String, Object> snapshotForNodes(Set<String> nodeIds) {
        return snapshotForNodes(nodeIds, false);
    }
    public Map<String, Object> snapshotForNodes(Set<String> nodeIds, boolean includeDefault) {
        TenantContext.requireTenantId();
        List<Entry> sources = samplingEntries(nodeIds, includeDefault);
        CompletableFuture<?>[] pending = sources.stream().map(this::scheduleRead).toArray(CompletableFuture[]::new);
        try { CompletableFuture.allOf(pending).get(1200, TimeUnit.MILLISECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch (ExecutionException | TimeoutException ignored) { /* Slow nodes complete independently. */ }
        var result = new HashMap<String, Object>(aggregateEvent(sources, nodeIds));
        var nodes = new ArrayList<Map<String, Object>>();
        var configurations = new LinkedHashMap<String, Object>();
        for (Entry entry : sources) {
            var node = snapshot(entry);
            node.put("nodeName", entry.name);
            node.put("networkCode", entry.networkCode);
            node.put("nodeUrl", entry.node.baseUrl());
            node.put("runningGroups", fresh(entry) ? entry.index.groups().entrySet().stream()
                    .filter(e -> e.getValue().running()).map(Map.Entry::getKey).toList() : List.of());
            nodes.add(node);
            configurations.put(entry.node.id(), node);
        }
        result.put("nodes", nodes);
        result.put("byNode", configurations);
        result.put("defaultNodeId", sources.isEmpty() ? "" : sources.getFirst().node.id());
        // Historical flows did not persist a node ID. Resolve their actual PG across
        // reachable nodes; duplicated PGs are ambiguous and must never use the default.
        var owners = new HashMap<String, String>();
        var ambiguous = new HashSet<String>();
        for (Entry entry : sources) if (fresh(entry)) for (String group : entry.index.groups().keySet()) {
            if (owners.putIfAbsent(group, entry.node.id()) != null) ambiguous.add(group);
        }
        ambiguous.forEach(owners::remove);
        result.put("legacyOwners", owners);
        result.put("ambiguousGroups", ambiguous);
        for (var node : nodes) {
            Entry entry = sources.stream().filter(e -> e.node.id().equals(node.get("nodeId"))).findFirst().orElseThrow();
            for (int status = 0; status <= 3; status++) {
                final int wanted = status;
                var ids = fresh(entry) ? entry.index.groups().entrySet().stream()
                        .filter(e -> entry.node.id().equals(owners.get(e.getKey())) && e.getValue().taskStatus() == wanted)
                        .map(Map.Entry::getKey).toList() : List.<String>of();
                var chunks = new ArrayList<List<String>>();
                for (int offset = 0; offset < ids.size(); offset += 500) chunks.add(ids.subList(offset, Math.min(offset + 500, ids.size())));
                node.put("legacyGroups" + status, chunks);
            }
            node.put("legacyRunningGroups", fresh(entry) ? entry.index.groups().entrySet().stream()
                    .filter(e -> entry.node.id().equals(owners.get(e.getKey())) && e.getValue().running())
                    .map(Map.Entry::getKey).toList() : List.of());
        }
        var watchSources = sources.stream().filter(e -> {
            try { e.node.requireValidConnection(); return true; } catch (RuntimeException invalid) { return false; }
        }).toList();
        result.put("watchNodeIds", watchSources.stream().map(e -> e.node.id()).toList());
        result.put("watchRevision", aggregateEvent(watchSources).get("revision"));
        return result;
    }

    /** Only sample the current tenant's explicitly selected nodes. Missing bindings stay unavailable. */
    public SseEmitter subscribeForNodes(Set<String> nodeIds) {
        TenantContext.requireTenantId();
        var sources = samplingEntries(nodeIds);
        if (!connections.tryAcquire()) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "实时状态连接繁忙，请稍后重试");
        var subscriber = new Subscriber(sources, "scoped");
        subscriber.requestedIds = Set.copyOf(nodeIds);
        sources.forEach(e -> e.subscribers.add(subscriber));
        if (sources.isEmpty()) unconfigured.add(subscriber);
        subscriber.heartbeat = clock.scheduleAtFixedRate(() -> subscriber.offer(aggregateEvent(sources, nodeIds)), 15, 15, TimeUnit.SECONDS);
        subscriber.offer(aggregateEvent(sources, nodeIds));
        subscriber.renewal = clock.schedule(subscriber.emitter::complete, 55, TimeUnit.SECONDS);
        return subscriber.emitter;
    }

    public SseEmitter subscribeAll() {
        var sources = samplingEntries();
        if (sources.isEmpty()) {
            Entry empty = new Entry(null);
            empty.error = "当前租户尚未配置 NiFi 节点，请在节点管理中配置并启用后查看实时状态";
            sources = List.of(empty);
        }
        if (!connections.tryAcquire()) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "实时状态连接繁忙，请稍后重试");
        String defaultId = sources.getFirst().node == null ? null : sources.getFirst().node.id();
        var subscriber = new Subscriber(sources, defaultId);
        sources.forEach(e -> e.subscribers.add(subscriber));
        if (defaultId == null) {
            unconfigured.add(subscriber);
            subscriber.heartbeat = clock.scheduleAtFixedRate(() -> subscriber.offer(event(subscriber.sources.getFirst())), 15, 15, TimeUnit.SECONDS);
            subscriber.offer(event(sources.getFirst()));
        } else subscriber.offer(aggregateEvent(sources));
        subscriber.renewal = clock.schedule(subscriber.emitter::complete, 55, TimeUnit.SECONDS);
        return subscriber.emitter;
    }
    public SseEmitter subscribe() {
        Entry entry;
        boolean configured = true;
        try {
            entry = current(null);
        } catch (NodeNotConfiguredException missing) {
            // Missing configuration is a normal first-install state. Keep the SSE
            // contract so MVC does not attempt to serialize a JSON error as a stream.
            entry = new Entry(null);
            entry.error = missing.getMessage();
            configured = false;
        }
        if (!connections.tryAcquire()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "实时状态连接繁忙，请稍后重试");
        Subscriber subscriber = new Subscriber(entry); entry.subscribers.add(subscriber);
        if (!configured) {
            unconfigured.add(subscriber);
            Map<String, Object> unavailable = event(entry);
            subscriber.heartbeat = clock.scheduleAtFixedRate(() -> subscriber.offer(unavailable), 15, 15, TimeUnit.SECONDS);
        }
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
        value.put("running", group != null && group.running() && Boolean.TRUE.equals(snapshot.get("fresh")));
        value.put("runtimeFresh", !deployed && !"RUNNING".equals(lifecycle)
                || Boolean.TRUE.equals(snapshot.get("fresh")) && (!deployed || group != null));
        value.put("runtimeObservedAt", snapshot.get("observedAt")); value.put("runtimeMessage", snapshot.get("message"));
        value.put("invalidProcessorCount", group == null ? 0 : group.invalidCount());
        return value;
    }
    @PreDestroy public void close() { entries.values().forEach(e -> e.subscribers.forEach(s -> s.emitter.complete())); unconfigured.forEach(s -> s.emitter.complete()); clock.shutdownNow(); readers.shutdownNow(); writers.shutdownNow(); }
}
