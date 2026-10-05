package com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * Platform-control persistence for reconciliation.
 *
 * <p>Every tenant-scoped statement carries an explicit tenant predicate. The
 * mapper ignores the automatic tenant interceptor because scheduler methods
 * intentionally scan all tenants.</p>
 */
@InterceptorIgnore(tenantLine = "true")
public interface ReconciliationControlMapper {

    IPage<Map<String, Object>> selectPolicyPage(
            IPage<Map<String, Object>> page,
            @Param("tenantId") String tenantId,
            @Param("keyword") String keyword
    );

    IPage<Map<String, Object>> selectRunPage(
            IPage<Map<String, Object>> page,
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("keyword") String keyword
    );

    IPage<Map<String, Object>> selectDiffPage(
            IPage<Map<String, Object>> page,
            @Param("tenantId") String tenantId,
            @Param("runId") String runId,
            @Param("diffType") String diffType
    );

    Map<String, Object> selectPolicyDetail(
            @Param("tenantId") String tenantId,
            @Param("policyId") String policyId
    );

    Map<String, Object> selectRunDetail(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId
    );

    List<Map<String, Object>> selectBuckets(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId
    );

    List<Map<String, Object>> selectDiffSummary(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId
    );

    List<Map<String, Object>> selectAccessTasks(@Param("tenantId") String tenantId);

    Map<String, Object> selectAccessTask(
            @Param("tenantId") String tenantId,
            @Param("taskId") String taskId
    );

    int insertPolicy(Map<String, Object> policy);

    int updatePolicy(Map<String, Object> policy);

    int softDeleteRules(
            @Param("tenantId") String tenantId,
            @Param("policyId") String policyId,
            @Param("operator") String operator,
            @Param("updatedTime") LocalDateTime updatedTime
    );

    List<Map<String, Object>> selectAccessFieldMappings(
            @Param("tenantId") String tenantId,
            @Param("taskId") String taskId
    );

    int insertRule(Map<String, Object> rule);

    Map<String, Object> selectRunPlan(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId
    );

    Map<String, Object> selectPolicyPlan(
            @Param("tenantId") String tenantId,
            @Param("policyId") String policyId
    );

    int updatePrecheck(Map<String, Object> params);

    String selectPrecheckStatus(
            @Param("tenantId") String tenantId,
            @Param("policyId") String policyId
    );

    int updatePolicyEnabled(Map<String, Object> params);

    String selectLatestSuccessfulUpperWatermark(
            @Param("tenantId") String tenantId,
            @Param("policyId") String policyId
    );

    int insertRun(Map<String, Object> run);

    String selectRunIdByDedupe(
            @Param("tenantId") String tenantId,
            @Param("dedupeKey") String dedupeKey
    );

    int touchPolicyLastRun(
            @Param("tenantId") String tenantId,
            @Param("policyId") String policyId,
            @Param("updatedTime") LocalDateTime updatedTime
    );

    int requestCancel(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId,
            @Param("updatedTime") LocalDateTime updatedTime
    );

    IPage<Map<String, Object>> selectDuePolicies(IPage<Map<String, Object>> page);

    int updateNextRun(
            @Param("tenantId") String tenantId,
            @Param("policyId") String policyId,
            @Param("nextRunAt") LocalDateTime nextRunAt
    );

    List<Map<String, Object>> selectRules(
            @Param("tenantId") String tenantId,
            @Param("policyId") String policyId
    );

    List<String> selectAccessEventPolicyIds(
            @Param("tenantId") String tenantId,
            @Param("accessTaskId") String accessTaskId
    );

    long countPolicies(
            @Param("tenantId") String tenantId,
            @Param("status") Integer status
    );

    List<Map<String, Object>> selectRunStatusSummary(
            @Param("tenantId") String tenantId,
            @Param("createdSince") LocalDateTime createdSince
    );

    long countOpenDiffs(@Param("tenantId") String tenantId);

    Map<String, Object> selectEndpoint(
            @Param("tenantId") String tenantId,
            @Param("datasourceId") String datasourceId,
            @Param("tableId") String tableId
    );
}
