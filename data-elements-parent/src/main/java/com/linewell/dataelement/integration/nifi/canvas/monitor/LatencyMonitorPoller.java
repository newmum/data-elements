package com.linewell.dataelement.integration.nifi.canvas.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessAggTaskT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessTaskMonitorSnapT;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiProperties;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessAggTaskTService;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessTaskMonitorSnapTService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.platform.tenant.application.TenantExecutionCatalog;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.time.LocalDateTime;
import java.util.List;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "nifi", name = "monitor-enabled", havingValue = "true", matchIfMissing = true)
public class LatencyMonitorPoller {

    private static final Logger log = LoggerFactory.getLogger(LatencyMonitorPoller.class);

    private final PipelineRepository repo;
    private final NifiClient nifi;
    private final IDataAccessAggTaskTService taskService;
    private final IDataAccessTaskMonitorSnapTService snapService;
    private final LatencyMonitorSupport support;
    private final NifiProperties props;
    private final TenantExecutionCatalog tenantCatalog;

    public LatencyMonitorPoller(PipelineRepository repo, NifiClient nifi,
                                IDataAccessAggTaskTService taskService,
                                IDataAccessTaskMonitorSnapTService snapService,
                                LatencyMonitorSupport support,
                                NifiProperties props,
                                TenantExecutionCatalog tenantCatalog) {
        this.repo = repo;
        this.nifi = nifi;
        this.taskService = taskService;
        this.snapService = snapService;
        this.support = support;
        this.props = props;
        this.tenantCatalog = tenantCatalog;
    }

    public void poll() {
        if (!Boolean.TRUE.equals(props.monitorEnabled())) {
            return;
        }
        for (String tenantId : tenantCatalog.activeTenantIds()) {
            try {
                TenantContext.run(tenantId, this::pollTenant);
            } catch (Exception e) {
                log.warn("Latency monitor poll failed for tenant {}: {}", tenantId, e.getMessage());
            }
        }
    }

    /** Poll one physical tenant database while its NiFi node configuration is in scope. */
    private void pollTenant() {
        int pageNo = 1;
        int pageSize = props.monitorBatchSize();
        while (true) {
            List<Pipeline> page = repo.findRunningPage(pageNo, pageSize);
            if (page.isEmpty()) {
                break;
            }
            for (Pipeline p : page) {
                if (p.nifiProcessGroupId() == null || p.nifiProcessGroupId().isBlank()) {
                    continue;
                }
                try {
                    pollOne(p);
                } catch (Exception e) {
                    log.warn("Latency monitor poll failed for {}: {}", p.id(), e.getMessage());
                }
            }
            if (page.size() < pageSize) {
                break;
            }
            pageNo++;
        }
    }

    private void pollOne(Pipeline pipeline) {
        DataAccessAggTaskT task = taskService.findByPipelineId(pipeline.id());
        if (task == null) {
            return;
        }
        LatencyMonitorSnapshot snapshot;
        try {
            JsonNode raw = nifi.getProcessGroupStatus(pipeline.nifiProcessGroupId());
            snapshot = support.fromStatus(raw);
        } catch (NifiException e) {
            snapshot = support.errorSnapshot("nifi status fetch failed: " + e.getMessage());
        } catch (Exception e) {
            snapshot = support.errorSnapshot("monitor parse failed: " + e.getMessage());
        }

        taskService.updateLatestMonitorState(pipeline.id(), snapshot);
        snapService.save(toSnap(task, pipeline, snapshot));
    }

    private DataAccessTaskMonitorSnapT toSnap(DataAccessAggTaskT task, Pipeline pipeline, LatencyMonitorSnapshot snapshot) {
        LocalDateTime now = LocalDateTime.now();
        return new DataAccessTaskMonitorSnapT()
            .setTid(NumericId.nextId())
            .setTaskId(task.getTid())
            .setPipelineId(pipeline.id())
            .setProcessGroupId(pipeline.nifiProcessGroupId())
            .setMonitorTime(snapshot.monitorTime())
            .setDelayLevel(snapshot.delayLevel())
            .setDelayMsEstimate(snapshot.delayMsEstimate())
            .setThresholdMs(snapshot.thresholdMs())
            .setIsTimeout(snapshot.isTimeout())
            .setQueuedCount(snapshot.queuedCount())
            .setQueuedBytes(snapshot.queuedBytes())
            .setActiveThreadCount(snapshot.activeThreadCount())
            .setFlowFilesIn(snapshot.flowFilesIn())
            .setFlowFilesOut(snapshot.flowFilesOut())
            .setBytesIn(snapshot.bytesIn())
            .setBytesOut(snapshot.bytesOut())
            .setMonitorStatus(snapshot.monitorStatus())
            .setMonitorMsg(snapshot.monitorMsg())
            .setCreatedTime(now)
            .setIsDel(0);
    }
}
