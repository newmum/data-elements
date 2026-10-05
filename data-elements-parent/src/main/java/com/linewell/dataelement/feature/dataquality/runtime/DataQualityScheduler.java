package com.linewell.dataelement.feature.dataquality.runtime;

import com.linewell.dataelement.feature.dataquality.config.DataQualityProperties;
import com.linewell.dataelement.feature.dataquality.infrastructure.persistence.DataQualityRepository;
import com.linewell.dataelement.platform.tenant.application.TenantExecutionCatalog;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DataQualityScheduler {

    private final DataQualityProperties properties;
    private final DataQualityRepository repository;
    private final TenantExecutionCatalog tenantCatalog;

    public DataQualityScheduler(
            DataQualityProperties properties,
            DataQualityRepository repository,
            TenantExecutionCatalog tenantCatalog
    ) {
        this.properties = properties;
        this.repository = repository;
        this.tenantCatalog = tenantCatalog;
    }

    public void schedule() {
        if (!properties.isEnabled() || !properties.getRole().controlEnabled()) {
            return;
        }
        for (String tenantId : tenantCatalog.activeTenantIds()) {
            try {
                TenantContext.run(tenantId, this::scheduleTenant);
            } catch (Exception exception) {
                log.error("Schedule data quality tasks failed, tenantId={}", tenantId, exception);
            }
        }
    }

    private void scheduleTenant() {
        LocalDateTime now = LocalDateTime.now();
        for (Map<String, Object> row : repository.dueTasks(now)) {
            String tenantId = String.valueOf(row.get("tenant_id"));
            String taskId = String.valueOf(row.get("tid"));
            TenantContext.run(tenantId, () -> {
                String requestId = "cron:" + row.get("next_run_at");
                repository.createRun(tenantId, taskId, "CRON", requestId, "scheduler");
                String cron = String.valueOf(row.get("cron_expression"));
                LocalDateTime next = CronExpression.parse(cron).next(now);
                repository.updateNextRun(
                        tenantId,
                        taskId,
                        next == null ? now.plusYears(10) : next
                );
            });
        }
    }
}
