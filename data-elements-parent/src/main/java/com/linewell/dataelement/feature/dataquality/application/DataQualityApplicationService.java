package com.linewell.dataelement.feature.dataquality.application;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.dataquality.infrastructure.jdbc.JdbcDataQualityEngine;
import com.linewell.dataelement.feature.dataquality.infrastructure.persistence.DataQualityRepository;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Current-tenant operations used by editable Magic APIs. */
@Service
public class DataQualityApplicationService {

    private final DataQualityRepository repository;
    private final JdbcDataQualityEngine engine;

    public DataQualityApplicationService(DataQualityRepository repository, JdbcDataQualityEngine engine) {
        this.repository = repository;
        this.engine = engine;
    }

    public Map<String, Object> summary() { return repository.summary(tenantId()); }

    public Map<String, Object> taskPage(String status, String keyword, int page, int size, String kind) {
        return repository.taskPage(tenantId(), status, keyword, positive(page), bounded(size), kind);
    }

    public Map<String, Object> taskDetail(String taskId) { return repository.taskDetail(tenantId(), taskId); }

    public String persistTask(Map<String, Object> body) {
        return repository.saveTask(tenantId(), operator(), body);
    }

    public Map<String, Object> inspectTask(String taskId) {
        return engine.precheck(repository.loadPlan(tenantId(), taskId, "precheck-" + taskId));
    }

    public boolean persistPrecheck(String taskId, Map<String, Object> result) {
        repository.savePrecheck(tenantId(), taskId, result);
        return true;
    }

    public boolean setEnabled(String taskId, boolean enabled) {
        repository.enable(tenantId(), taskId, enabled);
        return true;
    }

    public boolean deleteTask(String taskId) { return repository.deleteTask(tenantId(), taskId, operator()); }

    public String createRun(String taskId, String requestId) {
        return repository.createRun(tenantId(), taskId, "MANUAL", requestId, operator());
    }

    public Map<String, Object> runPage(String status, String keyword, int page, int size, String kind) {
        return repository.runPage(tenantId(), status, keyword, positive(page), bounded(size), kind);
    }

    public Map<String, Object> runDetail(String runId) { return repository.runDetail(tenantId(), runId); }

    public Map<String, Object> issuePage(String runId, String status, String severity, int page, int size) {
        return repository.issuePage(tenantId(), runId, status, severity, positive(page), bounded(size));
    }

    public boolean cancel(String runId) { repository.cancel(tenantId(), runId); return true; }

    public List<Map<String, Object>> datasources() { return repository.datasourceOptions(tenantId()); }

    public List<Map<String, Object>> tables(String datasourceId) {
        return repository.tableOptions(tenantId(), datasourceId);
    }

    public List<Map<String, Object>> columns(String tableId) {
        return repository.columnOptions(tenantId(), tableId);
    }

    public Map<String,List<Map<String,Object>>> columnsBatch(List<String> tableIds) {
        return repository.columnOptionsBatch(tenantId(),tableIds);
    }

    private String tenantId() { return TenantContext.requireTenantId(); }
    private String operator() { return StpUtil.isLogin() ? String.valueOf(StpUtil.getLoginId()) : "system"; }
    private int positive(int value) { return Math.max(1, value); }
    private int bounded(int value) { return Math.max(1, Math.min(200, value)); }
}
