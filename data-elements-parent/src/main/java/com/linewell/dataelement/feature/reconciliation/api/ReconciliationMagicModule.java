package com.linewell.dataelement.feature.reconciliation.api;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.feature.reconciliation.infrastructure.jdbc.JdbcReconciliationEngine;
import com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.ReconciliationRepository;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/**
 * Reconciliation infrastructure exposed to Magic API.
 * Request validation and use-case sequencing live in editable Magic endpoints.
 * This adapter retains tenant enforcement, transactional repository writes,
 * JDBC streaming checks and the authenticated operator identity.
 */
@Component
@MagicModule("reconcile")
public class ReconciliationMagicModule {

    private final ReconciliationRepository repository;
    private final JdbcReconciliationEngine engine;

    public ReconciliationMagicModule(ReconciliationRepository repository, JdbcReconciliationEngine engine) {
        this.repository = repository;
        this.engine = engine;
    }

    @Comment("按当前租户读取对账策略分页；Magic 接口负责筛选和分页默认值")
    public Map<String, Object> policyPage(String keyword, int page, int size) {
        return repository.policyPage(tenantId(), keyword, positive(page), bounded(size));
    }

    @Comment("按当前租户读取执行实例分页")
    public Map<String, Object> runPage(String status, String keyword, int page, int size) {
        return repository.runPage(tenantId(), status, keyword, positive(page), bounded(size));
    }

    @Comment("按当前租户读取脱敏差异明细分页")
    public Map<String, Object> diffPage(String runId, String type, int page, int size) {
        return repository.diffPage(tenantId(), runId, type, positive(page), bounded(size));
    }

    @Comment("读取当前租户的策略和字段规则")
    public Map<String, Object> policyDetail(String policyId) {
        return repository.policyDetail(tenantId(), policyId);
    }

    @Comment("读取当前租户的执行实例、分桶和差异汇总")
    public Map<String, Object> runDetail(String runId) {
        return repository.runDetail(tenantId(), runId);
    }

    @Comment("读取当前租户可供策略关联的接入任务")
    public List<Map<String, Object>> accessTasks() {
        return repository.accessTasks(tenantId());
    }

    @Comment("在仓储事务中保存策略和字段规则，并返回策略标识")
    public String persistPolicy(Map<String, Object> body) {
        return repository.savePolicy(tenantId(), operator(), body);
    }

    @Comment("运行跨数据源连接、主键和字段的预检；不修改策略状态")
    public Map<String, Object> inspectPolicy(String policyId) {
        return engine.precheck(repository.loadPolicyPlan(tenantId(), policyId));
    }

    @Comment("保存预检结果，供启用策略时作强制校验")
    public boolean persistPrecheck(String policyId, Map<String, Object> result) {
        repository.savePrecheck(tenantId(), policyId, result);
        return true;
    }

    @Comment("变更策略启停状态；仓储层强制验证预检通过")
    public boolean setPolicyEnabled(String policyId, boolean enabled) {
        repository.enablePolicy(tenantId(), policyId, enabled);
        return true;
    }

    @Comment("查询接入任务关联的已启用事件触发策略")
    public List<String> accessEventPolicyIds(String accessTaskId) {
        return repository.accessEventPolicyIds(tenantId(), accessTaskId);
    }

    @Comment("幂等创建执行实例；仓储层保留去重键、策略快照和事务约束")
    public String createRun(String policyId, String triggerType, String accessRunId,
                            String lowerWatermark, String upperWatermark, String requestId) {
        return repository.createRun(tenantId(), policyId, triggerType, accessRunId,
                lowerWatermark, upperWatermark, requestId, operator());
    }

    @Comment("设置执行实例取消标记，实际流式扫描由 Worker 安全停止")
    public boolean requestCancel(String runId) {
        repository.requestCancel(tenantId(), runId);
        return true;
    }

    @Comment("汇总当前租户最近指定天数的策略、实例和待处理差异")
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
