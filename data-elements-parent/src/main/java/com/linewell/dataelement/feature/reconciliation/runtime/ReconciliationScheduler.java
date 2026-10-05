package com.linewell.dataelement.feature.reconciliation.runtime;

import com.linewell.dataelement.feature.reconciliation.application.ReconciliationService;
import com.linewell.dataelement.feature.reconciliation.config.ReconciliationProperties;
import com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.ReconciliationRepository;
import com.linewell.dataelement.platform.tenant.application.TenantExecutionCatalog;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReconciliationScheduler {

    private final ReconciliationProperties properties;
    private final ReconciliationRepository repository;
    private final ReconciliationService service;
    private final TenantExecutionCatalog tenantCatalog;

    public ReconciliationScheduler(
            ReconciliationProperties properties,
            ReconciliationRepository repository,
            ReconciliationService service,
            TenantExecutionCatalog tenantCatalog
    ) {
        this.properties = properties;
        this.repository = repository;
        this.service = service;
        this.tenantCatalog = tenantCatalog;
    }

    public void scheduleDuePolicies() {
        if (!properties.isEnabled() || !properties.getRole().controlEnabled()) {
            return;
        }
        for (String tenantId : tenantCatalog.activeTenantIds()) {
            try {
                TenantContext.run(tenantId, this::scheduleTenant);
            } catch (Exception exception) {
                log.error(
                        "Schedule reconciliation policies failed, tenantId={}",
                        tenantId,
                        exception
                );
            }
        }
    }

    private void scheduleTenant() {
        for (Map<String, Object> policy : repository.duePolicies()) {
            String tenantId = String.valueOf(policy.get("tenant_id"));
            String policyId = String.valueOf(policy.get("tid"));
            String cron = String.valueOf(policy.get("cron_expression"));
            LocalDateTime now = LocalDateTime.now();
            try {
                CronExpression expression = CronExpression.parse(cron);
                service.scheduledRun(tenantId, policyId, now);
                LocalDateTime next = expression.next(now);
                if (next == null) {
                    throw new IllegalArgumentException("Cron 表达式没有下一次执行时间");
                }
                repository.updateNextRun(tenantId, policyId, next);
            } catch (Exception e) {
                log.error(
                        "Schedule reconciliation policy failed, tenantId={}, policyId={}",
                        tenantId,
                        policyId,
                        e
                );
                repository.updateNextRun(tenantId, policyId, now.plusMinutes(5));
            }
        }
    }
}
