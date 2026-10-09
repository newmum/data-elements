package com.linewell.dataelement.feature.reconciliation.api;

import com.linewell.dataelement.feature.reconciliation.application.ReconciliationApplicationService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** Magic API adapter; current-tenant operations are enforced by the application service. */
@Component
@MagicModule("reconcile")
public class ReconciliationMagicModule {

    private final ReconciliationApplicationService service;

    public ReconciliationMagicModule(ReconciliationApplicationService service) {
        this.service = service;
    }

    @Comment("按当前租户读取对账策略分页；Magic 接口负责筛选和分页默认值")
    public Map<String, Object> policyPage(String keyword, int page, int size) {
        return service.policyPage(keyword, page, size);
    }

    @Comment("按当前租户读取执行实例分页")
    public Map<String, Object> runPage(String status, String keyword, int page, int size) {
        return service.runPage(status, keyword, page, size);
    }

    @Comment("按当前租户读取脱敏差异明细分页")
    public Map<String, Object> diffPage(String runId, String type, int page, int size) {
        return service.diffPage(runId, type, page, size);
    }

    @Comment("读取当前租户的策略和字段规则")
    public Map<String, Object> policyDetail(String policyId) {
        return service.policyDetail(policyId);
    }

    @Comment("读取当前租户的执行实例、分桶和差异汇总")
    public Map<String, Object> runDetail(String runId) {
        return service.runDetail(runId);
    }

    @Comment("读取当前租户可供策略关联的接入任务")
    public List<Map<String, Object>> accessTasks() {
        return service.accessTasks();
    }

    @Comment("在仓储事务中保存策略和字段规则，并返回策略标识")
    public String persistPolicy(Map<String, Object> body) {
        return service.persistPolicy(body);
    }

    @Comment("运行跨数据源连接、主键和字段的预检；不修改策略状态")
    public Map<String, Object> inspectPolicy(String policyId) {
        return service.inspectPolicy(policyId);
    }

    @Comment("保存预检结果，供启用策略时作强制校验")
    public boolean persistPrecheck(String policyId, Map<String, Object> result) {
        return service.persistPrecheck(policyId, result);
    }

    @Comment("变更策略启停状态；仓储层强制验证预检通过")
    public boolean setPolicyEnabled(String policyId, boolean enabled) {
        return service.setPolicyEnabled(policyId, enabled);
    }

    @Comment("查询接入任务关联的已启用事件触发策略")
    public List<String> accessEventPolicyIds(String accessTaskId) {
        return service.accessEventPolicyIds(accessTaskId);
    }

    @Comment("幂等创建执行实例；仓储层保留去重键、策略快照和事务约束")
    public String createRun(String policyId, String triggerType, String accessRunId,
                            String lowerWatermark, String upperWatermark, String requestId) {
        return service.createRun(policyId, triggerType, accessRunId, lowerWatermark, upperWatermark, requestId);
    }

    @Comment("设置执行实例取消标记，实际流式扫描由 Worker 安全停止")
    public boolean requestCancel(String runId) {
        return service.requestCancel(runId);
    }

    @Comment("汇总当前租户最近指定天数的策略、实例和待处理差异")
    public Map<String, Object> summary(int days) {
        return service.summary(days);
    }
}
