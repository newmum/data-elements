package com.linewell.dataelement.platform.scheduling;

import com.linewell.dataelement.feature.dataquality.runtime.DataQualityScheduler;
import com.linewell.dataelement.feature.dataquality.runtime.DataQualityWorker;
import com.linewell.dataelement.feature.reconciliation.runtime.ReconciliationScheduler;
import com.linewell.dataelement.feature.reconciliation.runtime.ReconciliationWorker;
import com.linewell.dataelement.integration.nifi.canvas.errors.BulletinPoller;
import com.linewell.dataelement.integration.nifi.canvas.monitor.LatencyMonitorPoller;
import com.linewell.dataelement.integration.nifi.canvas.monitor.ProvenanceExecutionSyncJob;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/**
 * Exposes the existing execution engines to Magic API task scripts. Scheduling,
 * cron expressions and enable/disable switches live in the task editor only.
 */
@Component
@MagicModule("systemJobs")
public class MagicJobModule {

    private final ReconciliationScheduler reconciliationScheduler;
    private final ReconciliationWorker reconciliationWorker;
    private final DataQualityScheduler dataQualityScheduler;
    private final DataQualityWorker dataQualityWorker;
    private final BulletinPoller bulletinPoller;
    private final ObjectProvider<LatencyMonitorPoller> latencyMonitorPoller;
    private final ObjectProvider<ProvenanceExecutionSyncJob> provenanceExecutionSyncJob;
    private final JdbcTemplate controlJdbc;

    public MagicJobModule(
            ReconciliationScheduler reconciliationScheduler,
            ReconciliationWorker reconciliationWorker,
            DataQualityScheduler dataQualityScheduler,
            DataQualityWorker dataQualityWorker,
            BulletinPoller bulletinPoller,
            ObjectProvider<LatencyMonitorPoller> latencyMonitorPoller,
            ObjectProvider<ProvenanceExecutionSyncJob> provenanceExecutionSyncJob,
            @Qualifier("controlDataSource") DataSource controlDataSource) {
        this.reconciliationScheduler = reconciliationScheduler;
        this.reconciliationWorker = reconciliationWorker;
        this.dataQualityScheduler = dataQualityScheduler;
        this.dataQualityWorker = dataQualityWorker;
        this.bulletinPoller = bulletinPoller;
        this.latencyMonitorPoller = latencyMonitorPoller;
        this.provenanceExecutionSyncJob = provenanceExecutionSyncJob;
        this.controlJdbc = new JdbcTemplate(controlDataSource);
    }

    @Comment("运行系统任务；任务的执行时间和启停状态由 Magic API 定时任务配置")
    public String run(@Comment("任务编码") String job) {
        if (!List.of("reconciliation-schedule", "reconciliation-worker",
                "data-quality-schedule", "data-quality-worker", "leafy-bulletins",
                "leafy-latency", "leafy-provenance").contains(job)) {
            throw new IllegalArgumentException("Unknown Magic task: " + job);
        }
        LocalDateTime started = LocalDateTime.now();
        long startedNanos = System.nanoTime();
        controlJdbc.update("""
                INSERT INTO api_job_t
                    (tid, job_code, run_count, last_started_at, last_status, last_error)
                VALUES (?, ?, 1, ?, 'RUNNING', NULL)
                ON DUPLICATE KEY UPDATE run_count=run_count+1,
                    last_started_at=VALUES(last_started_at), last_status='RUNNING', last_error=NULL
                """, UUID.randomUUID().toString().replace("-", ""), job, started);
        try {
            String result = execute(job);
            finish(job, startedNanos, "disabled".equals(result) ? "DISABLED" : "COMPLETED", null);
            return result;
        } catch (RuntimeException exception) {
            String error = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            finish(job, startedNanos, "FAILED", error);
            throw exception;
        }
    }

    @Comment("查看全部系统任务最近一次执行情况")
    public List<Map<String, Object>> status() {
        return controlJdbc.queryForList("""
                SELECT job_code, run_count, last_started_at, last_finished_at,
                       last_duration_ms, last_status, last_error
                  FROM api_job_t ORDER BY job_code
                """);
    }

    private void finish(String job, long startedNanos, String status, String error) {
        controlJdbc.update("""
                UPDATE api_job_t
                   SET last_finished_at=?, last_duration_ms=?, last_status=?, last_error=?
                 WHERE job_code=?
                """, LocalDateTime.now(), (System.nanoTime() - startedNanos) / 1_000_000L,
                status, error == null ? null : error.substring(0, Math.min(error.length(), 1024)), job);
    }

    private String execute(String job) {
        switch (job) {
            case "reconciliation-schedule" -> reconciliationScheduler.scheduleDuePolicies();
            case "reconciliation-worker" -> reconciliationWorker.poll();
            case "data-quality-schedule" -> dataQualityScheduler.schedule();
            case "data-quality-worker" -> dataQualityWorker.poll();
            case "leafy-bulletins" -> bulletinPoller.poll();
            case "leafy-latency" -> {
                LatencyMonitorPoller monitor = latencyMonitorPoller.getIfAvailable();
                if (monitor == null) return "disabled";
                monitor.poll();
            }
            case "leafy-provenance" -> {
                ProvenanceExecutionSyncJob sync = provenanceExecutionSyncJob.getIfAvailable();
                if (sync == null) return "disabled";
                sync.sync();
            }
            default -> throw new IllegalArgumentException("Unknown Magic task: " + job);
        }
        return "completed";
    }
}
