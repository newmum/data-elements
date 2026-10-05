package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.NifiPipelineT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper.NifiPipelineTMapper;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import java.util.function.UnaryOperator;

/**
 * Database-backed pipeline storage.
 */
@Component
public class PipelineRepository {

    private static final Logger log = LoggerFactory.getLogger(PipelineRepository.class);

    private final ObjectMapper mapper;
    private final NifiPipelineTMapper nifiPipelineTMapper;

    public PipelineRepository(ObjectMapper mapper, NifiPipelineTMapper nifiPipelineTMapper) {
        this.mapper = mapper;
        this.nifiPipelineTMapper = nifiPipelineTMapper;
    }

    @PostConstruct
    void init() {
        log.info("PipelineRepository ready with database storage");
    }

    public synchronized List<Pipeline> findAll() {
        requireTenantDatabase();
        List<NifiPipelineT> rows = nifiPipelineTMapper.selectList(new LambdaQueryWrapper<NifiPipelineT>()
                .eq(NifiPipelineT::getIsDel, 0)
                .orderByDesc(NifiPipelineT::getUpdatedAt));
        List<Pipeline> list = new ArrayList<>(rows.size());
        for (NifiPipelineT row : rows) {
            toPipeline(row).ifPresent(list::add);
        }
        return list.stream()
                .sorted(Comparator.comparing(Pipeline::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public synchronized List<Pipeline> findRunningPage(long pageNo, long pageSize) {
        requireTenantDatabase();
        Page<NifiPipelineT> page = new Page<>(pageNo, pageSize);
        Page<NifiPipelineT> result = nifiPipelineTMapper.selectPage(page, new LambdaQueryWrapper<NifiPipelineT>()
                .eq(NifiPipelineT::getIsDel, 0)
                .eq(NifiPipelineT::getStatus, PipelineStatus.RUNNING.name())
                .isNotNull(NifiPipelineT::getNifiProcessGroupId)
                .orderByAsc(NifiPipelineT::getUpdatedAt));
        List<Pipeline> list = new ArrayList<>(result.getRecords().size());
        for (NifiPipelineT row : result.getRecords()) {
            toPipeline(row).ifPresent(list::add);
        }
        return list;
    }

    /**
     * Returns deployed flows that can provide an execution history, regardless of their current
     * lifecycle state.  Task monitoring must also show the last execution for stopped flows.
     */
    public synchronized List<Pipeline> findMonitorablePage(long pageNo, long pageSize) {
        requireTenantDatabase();
        Page<NifiPipelineT> page = new Page<>(pageNo, pageSize);
        Page<NifiPipelineT> result = nifiPipelineTMapper.selectPage(page, new LambdaQueryWrapper<NifiPipelineT>()
                .eq(NifiPipelineT::getIsDel, 0)
                .isNotNull(NifiPipelineT::getNifiProcessGroupId)
                .ne(NifiPipelineT::getNifiProcessGroupId, "")
                .orderByAsc(NifiPipelineT::getUpdatedAt));
        List<Pipeline> list = new ArrayList<>(result.getRecords().size());
        for (NifiPipelineT row : result.getRecords()) {
            toPipeline(row).ifPresent(list::add);
        }
        return list;
    }

    public synchronized Optional<Pipeline> findById(String id) {
        requireTenantDatabase();
        NifiPipelineT row = nifiPipelineTMapper.selectById(id);
        if (row == null || Integer.valueOf(1).equals(row.getIsDel())) {
            return Optional.empty();
        }
        return toPipeline(row);
    }

    public synchronized Pipeline save(Pipeline p) {
        long now = System.currentTimeMillis();
        String id = (p.id() == null || p.id().isBlank()) ? NumericId.nextId() : p.id();
        Optional<Pipeline> existing = findById(id);
        Long createdAt = p.createdAt() != null
                ? p.createdAt()
                : existing.map(Pipeline::createdAt).orElse(now);
        // Status: keep existing status (don't reset to DRAFT just because user re-saved).
        // Newly saved pipelines that lack a status default to SAVED (since the file now exists).
        PipelineStatus status = p.status() != null
                ? p.status()
                : existing.map(Pipeline::status).orElse(PipelineStatus.SAVED);
        if (status == PipelineStatus.DRAFT) status = PipelineStatus.SAVED;

        Pipeline merged = new Pipeline(
                id,
                p.name(),
                p.description(),
                createdAt,
                now,
                p.dsl(),
                p.nifiProcessGroupId() != null
                        ? p.nifiProcessGroupId()
                        : existing.map(Pipeline::nifiProcessGroupId).orElse(null),
                status,
                p.lastDeployedHash() != null
                        ? p.lastDeployedHash()
                        : existing.map(Pipeline::lastDeployedHash).orElse(null),
                p.lastDeployedAt() != null
                        ? p.lastDeployedAt()
                        : existing.map(Pipeline::lastDeployedAt).orElse(null),
                p.lastStoppedAt() != null
                        ? p.lastStoppedAt()
                        : existing.map(Pipeline::lastStoppedAt).orElse(null),
                p.nodeMapping() != null
                        ? p.nodeMapping()
                        : existing.map(Pipeline::nodeMapping).orElse(null),
                p.lastBulletinId() != null
                        ? p.lastBulletinId()
                        : existing.map(Pipeline::lastBulletinId).orElse(null)
        );
        upsert(merged);
        return merged;
    }

    /**
     * Apply a mutation to the existing record and persist it. Throws if missing.
     */
    public synchronized Pipeline update(String id, UnaryOperator<Pipeline> mutator) {
        Pipeline cur = findById(id).orElseThrow(() -> new IllegalStateException("Pipeline not found: " + id));
        Pipeline next = mutator.apply(cur);
        if (next == null) throw new IllegalStateException("Mutator returned null for pipeline " + id);
        Pipeline stamped = new Pipeline(
                next.id(), next.name(), next.description(), next.createdAt(),
                System.currentTimeMillis(), next.dsl(),
                next.nifiProcessGroupId(), next.status(),
                next.lastDeployedHash(), next.lastDeployedAt(), next.lastStoppedAt(),
                next.nodeMapping(), next.lastBulletinId());
        upsert(stamped);
        return stamped;
    }

    /** Convenience: only change the lifecycle status. */
    public synchronized Pipeline updateStatus(String id, PipelineStatus status) {
        return update(id, cur -> withStatus(cur, status));
    }

    /** Unconditional metadata-only update (used by run endpoint to record nifiProcessGroupId). */
    public synchronized Pipeline updateProcessGroupId(String id, String pgId) {
        return update(id, cur -> new Pipeline(cur.id(), cur.name(), cur.description(),
                cur.createdAt(), cur.updatedAt(), cur.dsl(),
                pgId, cur.status(),
                cur.lastDeployedHash(), cur.lastDeployedAt(), cur.lastStoppedAt(),
                cur.nodeMapping(), cur.lastBulletinId()));
    }

    public synchronized boolean delete(String id) {
        requireTenantDatabase();
        NifiPipelineT row = nifiPipelineTMapper.selectById(id);
        if (row == null || Integer.valueOf(1).equals(row.getIsDel())) {
            return false;
        }
        row.setIsDel(1);
        row.setUpdatedAt(System.currentTimeMillis());
        return nifiPipelineTMapper.updateById(row) > 0;
    }

    private void upsert(Pipeline p) {
        requireTenantDatabase();
        try {
            NifiPipelineT row = toEntity(p);
            NifiPipelineT existing = nifiPipelineTMapper.selectById(p.id());
            if (existing == null) {
                nifiPipelineTMapper.insert(row);
            } else {
                nifiPipelineTMapper.updateById(row);
            }
        } catch (IOException e) {
            throw new RuntimeException("Save pipeline " + p.id() + " failed: " + e.getMessage(), e);
        }
    }

    private static void requireTenantDatabase() {
        TenantContext.requireTenantId();
        if (TenantContext.usesControlDatabase()) {
            throw new TenantAccessException("TENANT-DATABASE-REQUIRED", "流程数据必须在当前租户数据库中访问");
        }
    }

    private static Pipeline withStatus(Pipeline p, PipelineStatus s) {
        return new Pipeline(p.id(), p.name(), p.description(), p.createdAt(), p.updatedAt(),
                p.dsl(), p.nifiProcessGroupId(), s,
                p.lastDeployedHash(), p.lastDeployedAt(), p.lastStoppedAt(),
                p.nodeMapping(), p.lastBulletinId());
    }

    private Optional<Pipeline> toPipeline(NifiPipelineT row) {
        try {
            if (row == null) {
                return Optional.empty();
            }
            Pipeline.Dsl dsl = null;
            if (row.getDslJson() != null && !row.getDslJson().isBlank()) {
                dsl = mapper.readValue(row.getDslJson(), Pipeline.Dsl.class);
            }
            NifiNodeMapping nodeMapping = null;
            if (row.getNodeMappingJson() != null && !row.getNodeMappingJson().isBlank()) {
                nodeMapping = mapper.readValue(row.getNodeMappingJson(), NifiNodeMapping.class);
            }
            PipelineStatus status = parseStatus(row.getStatus());
            return Optional.of(new Pipeline(
                    row.getId(),
                    row.getName(),
                    row.getDescription(),
                    row.getCreatedAt(),
                    row.getUpdatedAt(),
                    dsl,
                    row.getNifiProcessGroupId(),
                    status,
                    row.getLastDeployedHash(),
                    row.getLastDeployedAt(),
                    row.getLastStoppedAt(),
                    nodeMapping,
                    row.getLastBulletinId()
            ));
        } catch (IOException e) {
            log.warn("Skipping unreadable pipeline row {}: {}", row == null ? "null" : row.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    private NifiPipelineT toEntity(Pipeline p) throws IOException {
        NifiPipelineT row = new NifiPipelineT();
        row.setId(p.id());
        row.setName(p.name());
        row.setDescription(p.description());
        row.setStatus(p.status() == null ? PipelineStatus.SAVED.name() : p.status().name());
        row.setDslJson(p.dsl() == null ? null : mapper.writeValueAsString(p.dsl()));
        row.setDslHash(p.dsl() == null ? null : DslHasher.hash(p.dsl()));
        row.setDslVersion(p.dsl() == null ? null : (long) p.dsl().version());
        row.setNifiProcessGroupId(p.nifiProcessGroupId());
        row.setLastDeployedHash(p.lastDeployedHash());
        row.setLastDeployedAt(p.lastDeployedAt());
        row.setLastStoppedAt(p.lastStoppedAt());
        row.setNodeMappingJson(p.nodeMapping() == null ? null : mapper.writeValueAsString(p.nodeMapping()));
        row.setLastBulletinId(p.lastBulletinId());
        row.setCreatedAt(p.createdAt());
        row.setUpdatedAt(p.updatedAt());
        row.setIsDel(0);
        return row;
    }

    private PipelineStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return PipelineStatus.SAVED;
        }
        if ("STOP".equalsIgnoreCase(value)) {
            return PipelineStatus.STOPPED;
        }
        for (PipelineStatus status : PipelineStatus.values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return PipelineStatus.SAVED;
    }

    /** Internal helper: a sanity placeholder for tests/IDE; kept private. */
    @SuppressWarnings("unused")
    private List<Pipeline> empty() { return new ArrayList<>(); }
}
