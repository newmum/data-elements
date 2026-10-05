package com.linewell.dataelement.integration.nifi.canvas.errors;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import com.linewell.dataelement.platform.tenant.application.TenantExecutionCatalog;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Polls NiFi bulletins every 3s for each running / errored pipeline and
 * records them into {@link ErrorService} as RUNTIME errors.
 *
 * <p>Also flips {@link PipelineStatus#RUNNING} -> {@link PipelineStatus#RUN_ERROR}
 * when a new ERROR-severity bulletin arrives.
 */
@Component
public class BulletinPoller {

    private static final Logger log = LoggerFactory.getLogger(BulletinPoller.class);
    private static final Map<String, Long> AFTER_IDS = new HashMap<>();

    private final PipelineRepository repo;
    private final NifiClient nifi;
    private final ErrorService errors;
    private final TenantExecutionCatalog tenantCatalog;

    public BulletinPoller(PipelineRepository repo, NifiClient nifi, ErrorService errors,
                          TenantExecutionCatalog tenantCatalog) {
        this.repo = repo;
        this.nifi = nifi;
        this.errors = errors;
        this.tenantCatalog = tenantCatalog;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void primeFromDisk() {
        for (String tenantId : tenantCatalog.activeTenantIds()) {
            TenantContext.run(tenantId, () -> {
                for (Pipeline p : repo.findAll()) {
                    if (p.lastBulletinId() != null) {
                        AFTER_IDS.put(bulletinKey(p), p.lastBulletinId());
                    }
                }
            });
        }
    }

    public void poll() {
        for (String tenantId : tenantCatalog.activeTenantIds()) {
            try {
                TenantContext.run(tenantId, this::pollTenant);
            } catch (Exception e) {
                log.warn("Bulletin poll failed for tenant {}: {}", tenantId, e.getMessage());
            }
        }
    }

    /** Poll one physical tenant database while its NiFi node configuration is in scope. */
    private void pollTenant() {
        for (Pipeline p : repo.findAll()) {
            if (p.nifiProcessGroupId() == null) continue;
            PipelineStatus s = p.status();
            // A stopped process group cannot emit new runtime bulletins. Polling it also
            // forces an unnecessary NiFi node lookup on every scheduler pass.
            if (s != PipelineStatus.RUNNING && s != PipelineStatus.RUN_ERROR) continue;
            try {
                pollOne(p);
            } catch (NifiException e) {
                log.debug("Bulletin poll for {} failed: {}", p.id(), e.getMessage());
            } catch (Exception e) {
                log.warn("Bulletin poll for {} failed: {}", p.id(), e.toString());
            }
        }
    }

    private void pollOne(Pipeline p) {
        String bulletinKey = bulletinKey(p);
        long after = AFTER_IDS.getOrDefault(bulletinKey, p.lastBulletinId() == null ? 0L : p.lastBulletinId());
        JsonNode board = nifi.getBulletinBoard(p.nifiProcessGroupId(), after);
        JsonNode bulletins = board.path("bulletinBoard").path("bulletins");
        if (!bulletins.isArray() || bulletins.isEmpty()) return;

        long maxId = after;
        boolean sawError = false;
        NifiNodeMapping mapping = p.nodeMapping();
        for (JsonNode b : bulletins) {
            long id = b.path("id").asLong();
            if (id > maxId) maxId = id;
            JsonNode body = b.path("bulletin");
            if (body.isMissingNode()) continue;
            String level = body.path("level").asText("INFO");
            // Only ERROR / WARNING are interesting
            if (!"ERROR".equalsIgnoreCase(level) && !"WARNING".equalsIgnoreCase(level)) continue;
            String sourceId = body.path("sourceId").asText(null);
            String sourceName = body.path("sourceName").asText(null);
            String message = body.path("message").asText("(no message)");
            String category = body.path("category").asText(null);

            String canvasNodeId = (mapping != null && sourceId != null)
                    ? mapping.processorIdToCanvasNode().get(sourceId)
                    : null;

            FlowError err = new FlowError(
                    null,
                    "ERROR".equalsIgnoreCase(level) ? FlowError.ErrorLevel.ERROR : FlowError.ErrorLevel.WARNING,
                    FlowError.ErrorPhase.RUNTIME,
                    canvasNodeId,
                    sourceName,
                    null,
                    truncate(message, 200),
                    category == null ? null : "[" + category + "] " + message,
                    null,
                    Instant.now());
            errors.add(p.id(), err);
            if (err.level() == FlowError.ErrorLevel.ERROR) sawError = true;
        }

        long finalMax = maxId;
        boolean shouldFlipToError = sawError && p.status() == PipelineStatus.RUNNING;
        repo.update(p.id(), cur -> new Pipeline(cur.id(), cur.name(), cur.description(),
                cur.createdAt(), cur.updatedAt(), cur.dsl(),
                cur.nifiProcessGroupId(),
                shouldFlipToError ? PipelineStatus.RUN_ERROR : cur.status(),
                cur.lastDeployedHash(), cur.lastDeployedAt(), cur.lastStoppedAt(),
                cur.nodeMapping(), finalMax));
        AFTER_IDS.put(bulletinKey, maxId);
    }

    private static String bulletinKey(Pipeline pipeline) {
        String tenantId = TenantContext.requireTenantId();
        return tenantId + ":" + pipeline.id();
    }

    private static String truncate(String s, int n) {
        if (s == null) return null;
        return s.length() > n ? s.substring(0, n) + "..." : s;
    }
}
