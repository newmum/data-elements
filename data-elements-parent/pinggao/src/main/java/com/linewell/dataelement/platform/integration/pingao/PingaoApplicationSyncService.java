package com.linewell.dataelement.platform.integration.pingao;

import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates a complete Pingao snapshot; failed remote reads leave the last local snapshot untouched. */
@Service
public class PingaoApplicationSyncService {

    private final PingaoApplicationProperties properties;
    private final PingaoApplicationGateway gateway;
    private final PingaoApplicationRepository repository;
    private final StringRedisTemplate redisTemplate;

    public PingaoApplicationSyncService(
            PingaoApplicationProperties properties,
            PingaoApplicationGateway gateway,
            PingaoApplicationRepository repository,
            StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.gateway = gateway;
        this.repository = repository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public Map<String, Object> syncCurrentTenant() {
        String tenantId = TenantContext.requireTenantId();
        return synchronize(tenantId);
    }

    @Transactional
    public Map<String, Object> synchronize(String tenantId) {
        if (!properties.isEnabled()) throw new IllegalStateException("品高应用同步未启用");
        if (!properties.isConfigured()) throw new IllegalStateException("品高应用同步配置不完整");
        String lockKey = properties.getSyncLockKey() + ":" + tenantId;
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, properties.getSyncLockTtl());
        if (!Boolean.TRUE.equals(acquired)) throw new IllegalStateException("品高应用同步正在执行");
        try {
            List<PingaoAbility> remote = gateway.listApplications();
            Map<String, PingaoAbility> unique = new LinkedHashMap<>();
            int skipped = 0;
            for (PingaoAbility ability : remote) {
                if (!ability.isUsable()) {
                    skipped++;
                    continue;
                }
                unique.put(ability.id(), ability);
            }
            Instant syncedAt = Instant.now();
            PingaoApplicationRepository.SyncWriteResult writeResult =
                    repository.replacePingaoSnapshot(tenantId, List.copyOf(unique.values()), syncedAt);
            long online = unique.values().stream().filter(PingaoAbility::isOnline).count();
            repository.recordSuccessfulSync(tenantId, syncedAt, remote.size(), online);
            return Map.of(
                    "tenantId", tenantId,
                    "fetched", remote.size(),
                    "unique", unique.size(),
                    "online", online,
                    "inserted", writeResult.inserted(),
                    "updated", writeResult.updated(),
                    "skipped", skipped,
                    "syncedAt", syncedAt.toString());
        } catch (RuntimeException exception) {
            // Persist only the state transition; remote response details may be sensitive.
            repository.recordFailedSync(tenantId, Instant.now());
            throw exception;
        } finally {
            String current = redisTemplate.opsForValue().get(lockKey);
            if (lockToken.equals(current)) redisTemplate.delete(lockKey);
        }
    }

    public List<Map<String, Object>> selectableOptions() {
        return repository.registrationOptions(TenantContext.requireTenantId());
    }

    public PingaoApplicationRepository.SyncState syncState() {
        return repository.syncState(TenantContext.requireTenantId());
    }

    public PingaoApplicationRepository.DuplicatePreview duplicatePreview() {
        return repository.duplicatePreview(TenantContext.requireTenantId());
    }

    public PingaoApplicationRepository.LegacyPurgePreview legacyPurgePreview() {
        return repository.legacyPurgePreview(TenantContext.requireTenantId());
    }

    @Transactional
    public Map<String, Object> purgeLegacy(List<String> ids) {
        PingaoApplicationRepository.LegacyPurgePreview preview = legacyPurgePreview();
        boolean hasBlockedSelection = preview.referenced().stream().anyMatch(item ->
                ids == null || ids.isEmpty() || ids.contains(String.valueOf(item.get("tid"))));
        if (hasBlockedSelection) {
            throw new IllegalStateException("仍有数据源引用历史应用，不能删除");
        }
        int deleted = repository.softDeleteLegacy(TenantContext.requireTenantId(), ids);
        return Map.of("deleted", deleted, "dataOrigin", PingaoApplicationRepository.LEGACY);
    }
}
