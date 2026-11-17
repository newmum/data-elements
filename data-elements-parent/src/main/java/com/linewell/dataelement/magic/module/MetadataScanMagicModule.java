package com.linewell.dataelement.magic.module;

import com.linewell.dataelement.integration.metadata.TableInventoryProbe;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

/** 跨进程任务占用、提交后调度及物理元数据适配。 */
@Component
@MagicModule("metadataScan")
public class MetadataScanMagicModule {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataScanMagicModule.class);

    private final TableInventoryProbe probe;
    private final RedissonClient redis;
    private final ExecutorService workers;

    public MetadataScanMagicModule(TableInventoryProbe probe, RedissonClient redis) {
        this.probe = probe;
        this.redis = redis;
        this.workers = Executors.newFixedThreadPool(2,
                Thread.ofPlatform().daemon().name("table-metadata-collection-", 0).factory());
    }

    @Comment("检查已登记来源的连接器配置")
    public void validate(String datasourceId) {
        probe.validate(datasourceId);
    }

    @Comment("读取物理表清单及数据库所有者")
    public TableInventoryProbe.Inventory scan(String datasourceId) throws Exception {
        return probe.scan(datasourceId);
    }

    public boolean active(String jobId) {
        return lock(TenantContext.requireTenantId(), jobId).isLocked();
    }

    public boolean holdsLease(String jobId) {
        return lock(TenantContext.requireTenantId(), jobId).isHeldByThread(Long.parseLong(jobId));
    }

    @Comment("在当前租户事务中取得任务占用，提交后回到同一租户执行，回滚时释放")
    public void afterCommit(String jobId, Runnable action, Runnable rejected) {
        String tenantId = TenantContext.requireTenantId();
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive() || workers.isShutdown()) {
            throw new IllegalStateException("采集任务需要可用的事务和执行器");
        }
        long owner = Long.parseLong(jobId);
        RLock lease = lock(tenantId, jobId);
        // Job IDs supply the same owner handle on the request and worker threads. Redis renews live owners.
        if (!lease.tryLockAsync(0, -1, TimeUnit.MILLISECONDS, owner).toCompletableFuture().join()) {
            throw new IllegalStateException("采集任务已经受理");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    workers.execute(() -> run(tenantId, lease, owner, action, rejected));
                } catch (RejectedExecutionException unavailable) {
                    // Run compensating work on a different thread, away from the already committed transaction.
                    Thread.startVirtualThread(() -> run(tenantId, lease, owner, null, rejected));
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) release(lease, owner);
            }
        });
    }

    private void run(String tenantId, RLock lease, long owner, Runnable action, Runnable rejected) {
        try {
            TenantContext.run(tenantId, () -> {
                try {
                    if (action != null && lease.isHeldByThread(owner)) {
                        action.run();
                        return;
                    }
                } catch (RuntimeException failure) {
                    LOG.warn("Metadata scan callback failed: {}", failure.getClass().getSimpleName());
                }
                rejected.run();
            });
        } finally {
            release(lease, owner);
        }
    }

    private RLock lock(String tenantId, String jobId) {
        if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()
                || !jobId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("采集任务占用需要有效的行业租户和任务标识");
        }
        return redis.getLock("data-elements:metadata-collection:" + tenantId + ":" + jobId);
    }

    private static void release(RLock lease, long owner) {
        try {
            if (lease.isHeldByThread(owner)) lease.unlockAsync(owner).toCompletableFuture().join();
        } catch (RuntimeException failure) {
            LOG.warn("Metadata scan lease release failed: {}", failure.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void stop() {
        workers.shutdown();
    }
}
