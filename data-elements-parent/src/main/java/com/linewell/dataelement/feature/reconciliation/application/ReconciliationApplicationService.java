package com.linewell.dataelement.feature.reconciliation.application;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.reconciliation.infrastructure.jdbc.JdbcReconciliationEngine;
import com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.ReconciliationRepository;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Current-tenant operations used by editable Magic APIs. */
@Service
public class ReconciliationApplicationService {

    private final ReconciliationRepository repository;
    private final JdbcReconciliationEngine engine;

    public ReconciliationApplicationService(ReconciliationRepository repository, JdbcReconciliationEngine engine) {
        this.repository = repository;
        this.engine = engine;
    }

    public Map<String, Object> policyPage(String keyword, int page, int size) {
        return repository.policyPage(tenantId(), keyword, positive(page), bounded(size));
    }

    public Map<String, Object> runPage(String status, String keyword, int page, int size) {
        return repository.runPage(tenantId(), status, keyword, positive(page), bounded(size));
    }

    public Map<String, Object> diffPage(String runId, String type, int page, int size) {
        return repository.diffPage(tenantId(), runId, type, positive(page), bounded(size));
    }

    public Map<String, Object> policyDetail(String policyId) {
        return repository.policyDetail(tenantId(), policyId);
    }

    public Map<String, Object> runDetail(String runId) {
        return repository.runDetail(tenantId(), runId);
    }

    public List<Map<String, Object>> accessTasks() {
        return repository.accessTasks(tenantId());
    }

    public String persistPolicy(Map<String, Object> body) {
        return repository.savePolicy(tenantId(), operator(), body);
    }

    public Map<String, Object> inspectPolicy(String policyId) {
        return engine.precheck(repository.loadPolicyPlan(tenantId(), policyId));
    }

    public boolean persistPrecheck(String policyId, Map<String, Object> result) {
        repository.savePrecheck(tenantId(), policyId, result);
        return true;
    }

    public boolean setPolicyEnabled(String policyId, boolean enabled) {
        repository.enablePolicy(tenantId(), policyId, enabled);
        return true;
    }

    public List<String> accessEventPolicyIds(String accessTaskId) {
        return repository.accessEventPolicyIds(tenantId(), accessTaskId);
    }

    public String createRun(String policyId, String triggerType, String accessRunId,
                            String lowerWatermark, String upperWatermark, String requestId) {
        return repository.createRun(tenantId(), policyId, triggerType, accessRunId,
                lowerWatermark, upperWatermark, requestId, operator());
    }

    public boolean requestCancel(String runId) {
        repository.requestCancel(tenantId(), runId);
        return true;
    }

    public Map<String, Object> summary(int days) {
        if (days < 1 || days > 366) {
            throw new IllegalArgumentException("统计天数必须在 1 至 366 天之间");
        }
        return repository.summary(tenantId(), LocalDateTime.now().minusDays(days));
    }

    private String tenantId() {
        return TenantContext.requireTenantId();
    }

    private String operator() {
        return StpUtil.isLogin() ? String.valueOf(StpUtil.getLoginId()) : "system";
    }

    private int positive(int value) {
        return Math.max(1, value);
    }

    private int bounded(int value) {
        return Math.max(1, Math.min(200, value));
    }
}
