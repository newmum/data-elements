package com.linewell.dataelement.feature.reconciliation.application;

import com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.ReconciliationRepository;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** Schedules one bounded, idempotent run; HTTP use cases are defined in Magic API. */
@Service
public class ReconciliationService {

    private final ReconciliationRepository repository;

    public ReconciliationService(ReconciliationRepository repository) {
        this.repository = repository;
    }

    public String scheduledRun(String tenantId, String policyId, LocalDateTime scheduledAt) {
        return TenantContext.call(tenantId, () -> {
            String upperWatermark = scheduledAt.toString();
            String lowerWatermark = repository.latestSuccessfulUpperWatermark(tenantId, policyId);
            return repository.createRun(tenantId, policyId, "CRON", null,
                    lowerWatermark, upperWatermark, upperWatermark, "scheduler");
        });
    }
}
