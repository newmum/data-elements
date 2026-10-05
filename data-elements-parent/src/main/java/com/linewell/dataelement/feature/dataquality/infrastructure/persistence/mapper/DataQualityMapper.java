package com.linewell.dataelement.feature.dataquality.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

@InterceptorIgnore(tenantLine = "true")
public interface DataQualityMapper {
    IPage<Map<String, Object>> selectTaskPage(IPage<?> page, @Param("tenantId") String tenantId,
            @Param("status") String status, @Param("keyword") String keyword,@Param("kind") String kind);
    IPage<Map<String, Object>> selectRunPage(IPage<?> page, @Param("tenantId") String tenantId,
            @Param("status") String status, @Param("keyword") String keyword,@Param("kind") String kind);
    IPage<Map<String, Object>> selectIssuePage(IPage<?> page, @Param("tenantId") String tenantId,
            @Param("runId") String runId, @Param("status") String status,
            @Param("severity") String severity);
    Map<String, Object> selectTask(@Param("tenantId") String tenantId, @Param("taskId") String taskId);
    Map<String, Object> lockTask(@Param("tenantId") String tenantId, @Param("taskId") String taskId);
    int activeRuns(@Param("tenantId") String tenantId, @Param("taskId") String taskId);
    int deleteTask(Map<String,Object> values);
    List<Map<String, Object>> selectTaskRules(@Param("tenantId") String tenantId, @Param("taskId") String taskId);
    Map<String, Object> selectRun(@Param("tenantId") String tenantId, @Param("runId") String runId);
    List<Map<String, Object>> selectRunMetrics(@Param("tenantId") String tenantId, @Param("runId") String runId);
    List<Map<String, Object>> selectRunShards(@Param("tenantId") String tenantId, @Param("runId") String runId);
    List<Map<String, Object>> selectDatasourceOptions(@Param("tenantId") String tenantId);
    List<Map<String, Object>> selectTableOptions(@Param("tenantId") String tenantId, @Param("datasourceId") String datasourceId);
    List<Map<String, Object>> selectColumnOptions(@Param("tenantId") String tenantId, @Param("tableId") String tableId);
    List<Map<String, Object>> selectColumnOptionsBatch(@Param("tenantId") String tenantId, @Param("tableIds") List<String> tableIds);
    List<String> selectValidTableIds(@Param("tenantId") String tenantId, @Param("datasourceId") String datasourceId,
            @Param("tableIds") List<String> tableIds);
    Map<String, Object> selectSummary(@Param("tenantId") String tenantId, @Param("since") LocalDateTime since);
    List<Map<String, Object>> selectDimensionSummary(@Param("tenantId") String tenantId, @Param("since") LocalDateTime since);
    int insertTask(Map<String, Object> values);
    int updateTask(Map<String, Object> values);
    int softDeleteRules(Map<String, Object> values);
    int insertTaskRule(Map<String, Object> values);
    int updatePrecheck(Map<String, Object> values);
    int updateTaskEnabled(Map<String, Object> values);
    int insertRun(Map<String, Object> values);
    String selectRunIdByDedupe(@Param("tenantId") String tenantId, @Param("dedupeKey") String dedupeKey);
    int touchTaskRun(Map<String, Object> values);
    int requestCancel(Map<String, Object> values);
    IPage<Map<String, Object>> selectDueTasks(IPage<?> page, @Param("now") LocalDateTime now);
    int updateNextRun(Map<String, Object> values);

    IPage<Map<String, Object>> selectPendingRuns(IPage<?> page);
    int claimRun(Map<String, Object> values);
    int deleteRunShards(@Param("tenantId") String tenantId, @Param("runId") String runId);
    int insertShard(Map<String, Object> values);
    int markRunRunning(Map<String, Object> values);
    IPage<Map<String, Object>> selectCandidateShards(IPage<?> page);
    int claimShard(Map<String, Object> values);
    Map<String, Object> selectShard(@Param("tenantId") String tenantId, @Param("runId") String runId,
            @Param("shardId") String shardId);
    int completeShard(Map<String, Object> values);
    int updateShardFailure(Map<String, Object> values);
    Integer selectShardAttempt(@Param("tenantId") String tenantId, @Param("runId") String runId,
            @Param("shardId") String shardId);
    int upsertMetric(Map<String, Object> values);
    Map<String,Object> lockRun(@Param("tenantId") String tenantId,@Param("runId") String runId);
    int insertMetric(Map<String,Object> values);
    int addMetric(Map<String,Object> values);
    int insertIssue(Map<String, Object> values);
    Map<String, Object> selectShardState(@Param("tenantId") String tenantId, @Param("runId") String runId);
    Map<String, Object> selectRunSums(@Param("tenantId") String tenantId, @Param("runId") String runId);
    int finishRun(Map<String, Object> values);
    int refreshMetricRates(@Param("tenantId") String tenantId,@Param("runId") String runId);
    int failRun(Map<String, Object> values);
    int cancelRun(Map<String, Object> values);
    int cancelPendingShards(Map<String, Object> values);
    Integer selectCancelRequested(@Param("tenantId") String tenantId, @Param("runId") String runId);
    int recoverInitializingRuns(@Param("staleBefore") LocalDateTime staleBefore);
    int recoverRunningShards(@Param("maxAttempts") int maxAttempts, @Param("staleBefore") LocalDateTime staleBefore);
}
