package com.linewell.dataelement.feature.dataquality.api;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.dataquality.infrastructure.jdbc.JdbcDataQualityEngine;
import com.linewell.dataelement.feature.dataquality.infrastructure.persistence.DataQualityRepository;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** Tenant-enforcing persistence and streaming primitives for editable quality APIs. */
@Component
@MagicModule("dataQuality")
public class DataQualityMagicModule {

    private final DataQualityRepository repository;
    private final JdbcDataQualityEngine engine;

    public DataQualityMagicModule(DataQualityRepository repository, JdbcDataQualityEngine engine) {
        this.repository = repository;
        this.engine = engine;
    }

    @Comment("读取当前租户的质量概览")
    public Map<String, Object> summary() { return repository.summary(tenantId()); }

    @Comment("按租户读取质检任务分页")
    public Map<String, Object> taskPage(String status, String keyword, int page, int size, String kind) {
        return repository.taskPage(tenantId(), status, keyword, positive(page), bounded(size), kind);
    }

    @Comment("读取任务及其规则")
    public Map<String, Object> taskDetail(String taskId) { return repository.taskDetail(tenantId(), taskId); }

    @Comment("在仓储事务中保存任务及规则，返回任务标识")
    public String persistTask(Map<String, Object> body) {
        return repository.saveTask(tenantId(), operator(), body);
    }

    @Comment("跨数据源执行质检预检，不改变任务状态")
    public Map<String, Object> inspectTask(String taskId) {
        return engine.precheck(repository.loadPlan(tenantId(), taskId, "precheck-" + taskId));
    }

    @Comment("保存预检结果供启用校验")
    public boolean persistPrecheck(String taskId, Map<String, Object> result) {
        repository.savePrecheck(tenantId(), taskId, result);
        return true;
    }

    @Comment("启停任务；仓储层强制检查预检状态")
    public boolean setEnabled(String taskId, boolean enabled) {
        repository.enable(tenantId(), taskId, enabled);
        return true;
    }

    @Comment("软删除任务并保留执行历史")
    public boolean deleteTask(String taskId) { return repository.deleteTask(tenantId(), taskId, operator()); }

    @Comment("创建幂等手动质检实例")
    public String createRun(String taskId, String requestId) {
        return repository.createRun(tenantId(), taskId, "MANUAL", requestId, operator());
    }

    @Comment("按租户读取质检实例分页")
    public Map<String, Object> runPage(String status, String keyword, int page, int size, String kind) {
        return repository.runPage(tenantId(), status, keyword, positive(page), bounded(size), kind);
    }

    @Comment("读取实例指标和分片")
    public Map<String, Object> runDetail(String runId) { return repository.runDetail(tenantId(), runId); }

    @Comment("读取当前实例的问题样例分页")
    public Map<String, Object> issuePage(String runId, String status, String severity, int page, int size) {
        return repository.issuePage(tenantId(), runId, status, severity, positive(page), bounded(size));
    }

    @Comment("标记质检实例取消")
    public boolean cancel(String runId) { repository.cancel(tenantId(), runId); return true; }

    @Comment("读取可质检数据源")
    public List<Map<String, Object>> datasources() { return repository.datasourceOptions(tenantId()); }

    @Comment("读取数据源下已登记的数据表")
    public List<Map<String, Object>> tables(String datasourceId) {
        return repository.tableOptions(tenantId(), datasourceId);
    }

    @Comment("读取登记表字段")
    public List<Map<String, Object>> columns(String tableId) {
        return repository.columnOptions(tenantId(), tableId);
    }

    @Comment("批量读取当前租户至多 50 张有效数据表的字段，按 tableId 分组")
    public Map<String,List<Map<String,Object>>> columnsBatch(List<String> tableIds) {
        return repository.columnOptionsBatch(tenantId(),tableIds);
    }

    private String tenantId() { return TenantContext.requireTenantId(); }
    private String operator() { return StpUtil.isLogin() ? String.valueOf(StpUtil.getLoginId()) : "system"; }
    private int positive(int value) { return Math.max(1, value); }
    private int bounded(int value) { return Math.max(1, Math.min(200, value)); }
}
