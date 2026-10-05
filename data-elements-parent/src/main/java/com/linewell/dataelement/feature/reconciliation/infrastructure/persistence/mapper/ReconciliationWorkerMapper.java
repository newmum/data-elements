package com.linewell.dataelement.feature.reconciliation.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

@InterceptorIgnore(tenantLine = "true")
public interface ReconciliationWorkerMapper {

    IPage<Map<String, Object>> selectPendingRuns(IPage<Map<String, Object>> page);

    int claimRun(Map<String, Object> params);

    Integer selectRequestedBucketCount(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId
    );

    int deleteBuckets(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId
    );

    int insertBucket(Map<String, Object> params);

    int markRunRunning(Map<String, Object> params);

    IPage<Map<String, Object>> selectCandidateBuckets(IPage<Map<String, Object>> page);

    int claimBucket(Map<String, Object> params);

    int completeBucket(Map<String, Object> params);

    int cancelBucket(Map<String, Object> params);

    Map<String, Object> selectBucket(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId,
            @Param("bucketId") String bucketId
    );

    Map<String, Object> selectBucketState(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId
    );

    int updateBucketCompleted(Map<String, Object> params);

    Map<String, Object> selectBucketSums(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId
    );

    int finishRun(Map<String, Object> params);

    Integer selectAttempt(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId,
            @Param("bucketId") String bucketId
    );

    int updateBucketFailure(Map<String, Object> params);

    int failRun(Map<String, Object> params);

    int cancelRun(Map<String, Object> params);

    int cancelPendingBuckets(Map<String, Object> params);

    Integer selectCancelRequested(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId
    );

    int recoverInitializingRuns(@Param("staleBefore") LocalDateTime staleBefore);

    int recoverRunningBuckets(
            @Param("maxAttempts") int maxAttempts,
            @Param("staleBefore") LocalDateTime staleBefore
    );

    IPage<Map<String, Object>> selectCancelledRuns(IPage<Map<String, Object>> page);

    IPage<Map<String, Object>> selectTerminalRuns(IPage<Map<String, Object>> page);

    long countRunDiffs(
            @Param("tenantId") String tenantId,
            @Param("runId") String runId
    );

    int updateCheckpoint(Map<String, Object> params);

    int insertDiffBatch(@Param("rows") List<Map<String, Object>> rows);
}
