package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.linewell.dataelement.model.dataasset.DataAssetPropBatchSaveRequest;
import com.linewell.dataelement.model.dataasset.DataAssetQueryByIdRequest;
import com.linewell.dataelement.dataassets.runtime.DataAssetRuntimeAdapter;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessAggTaskT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessAggTaskTService;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Keeps data-access task state aligned with the NiFi pipeline lifecycle. */
@Service
public class PipelineTaskLifecycleSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(PipelineTaskLifecycleSynchronizer.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IDataAccessAggTaskTService taskService;
    private final DataAssetRuntimeAdapter dataAssetRuntimeAdapter;

    public PipelineTaskLifecycleSynchronizer(
            IDataAccessAggTaskTService taskService,
            DataAssetRuntimeAdapter dataAssetRuntimeAdapter
    ) {
        this.taskService = taskService;
        this.dataAssetRuntimeAdapter = dataAssetRuntimeAdapter;
    }

    /** Ensures that a catalog and its access task keep the same task identifier. */
    public void ensureCatalogAccessTask(String rawCatalogTid, Pipeline pipeline) {
        String catalogTid = normalizeCatalogTid(rawCatalogTid);
        try {
            String taskId = taskService.ensureTaskForCatalog(
                    catalogTid,
                    pipeline.id(),
                    pipeline.name(),
                    pipeline.dsl());
            if (isBlank(taskId) || isBlank(catalogTid)) {
                return;
            }
            DataAccessAggTaskT linked = taskService.findByPipelineId(pipeline.id());
            if (linked != null && !catalogTid.equals(linked.getDataCatalogId())) {
                return; // An independently saved copy must not replace the original catalog task.
            }
            synchronizeLegacyCatalogLink(catalogTid, taskId, pipeline.id());
        } catch (Exception exception) {
            log.warn("Synchronize access task failed for pipeline {} catalog {}: {}",
                    pipeline.id(), catalogTid, exception.getMessage());
            throw new IllegalStateException("无法关联接入任务：" + exception.getMessage(), exception);
        }
    }

    /**
     * Mirrors a task identifier into the retired asset-catalog model when that
     * model is present. Current tenant databases associate a pipeline through
     * {@code data_access_agg_task_t} and the registered {@code db_table_t}
     * identifiers, so an imported production tenant may intentionally omit
     * {@code da_asset_t}. A missing legacy table must never prevent deployment.
     */
    private void synchronizeLegacyCatalogLink(String catalogTid, String taskId, String pipelineId) {
        try {
            DataAssetQueryByIdRequest query = new DataAssetQueryByIdRequest();
            query.setTid(catalogTid);
            Map<String, Object> catalog = dataAssetRuntimeAdapter.queryById(query);
            String accessTaskId = catalog == null ? null : stringValue(catalog.get("accessTaskId"));
            if (taskId.equals(accessTaskId)) {
                return;
            }
            DataAssetPropBatchSaveRequest request = new DataAssetPropBatchSaveRequest();
            request.setTids(List.of(catalogTid));
            request.setProps(Map.of("accessTaskId", taskId));
            dataAssetRuntimeAdapter.propBatchSaveOrUpdate(request);
        } catch (Exception exception) {
            // da_asset_t belongs to the retired asset-catalog model. The task
            // row has already been persisted above, so retain the deployable
            // current-model association and only skip this optional mirror.
            log.info("Skip legacy asset-catalog task mirror for pipeline {} catalog {}: {}",
                    pipelineId, catalogTid, exception.getMessage());
        }
    }

    public void updateProcessGroup(String pipelineId, String processGroupId) {
        taskService.updateTaskProcessGroup(pipelineId, processGroupId);
    }

    public void unlinkCatalogTasks(String pipelineId) {
        List<DataAccessAggTaskT> tasks = taskService.list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DataAccessAggTaskT>()
                .eq(DataAccessAggTaskT::getPipelineId, pipelineId).eq(DataAccessAggTaskT::getIsDel, 0));
        for (DataAccessAggTaskT task : tasks) {
            if (isBlank(task.getDataCatalogId())) continue;
            clearLegacyCatalogLink(task, pipelineId);
        }
    }

    /** Clears the optional legacy mirror without making pipeline deletion depend on it. */
    private void clearLegacyCatalogLink(DataAccessAggTaskT task, String pipelineId) {
        try {
            DataAssetQueryByIdRequest query = new DataAssetQueryByIdRequest();
            query.setTid(task.getDataCatalogId());
            Map<String, Object> catalog = dataAssetRuntimeAdapter.queryById(query);
            if (catalog == null || !task.getTid().equals(stringValue(catalog.get("accessTaskId")))) return;
            DataAssetPropBatchSaveRequest request = new DataAssetPropBatchSaveRequest();
            request.setTids(List.of(task.getDataCatalogId()));
            request.setProps(Map.of("accessTaskId", ""));
            dataAssetRuntimeAdapter.propBatchSaveOrUpdate(request);
        } catch (Exception exception) {
            log.info("Skip legacy asset-catalog task unlink for pipeline {} catalog {}: {}",
                    pipelineId, task.getDataCatalogId(), exception.getMessage());
        }
    }

    public void started(String pipelineId) {
        String now = now();
        taskService.update(taskUpdate(pipelineId)
                .set(DataAccessAggTaskT::getTaskStatus, 1)
                .set(DataAccessAggTaskT::getScheduleRunning, now)
                .set(DataAccessAggTaskT::getScheduleRunningStart, now)
                .set(DataAccessAggTaskT::getUpdatedTime, LocalDateTime.now()));
    }

    public void stopped(String pipelineId) {
        String now = now();
        taskService.update(taskUpdate(pipelineId)
                .set(DataAccessAggTaskT::getTaskStatus, 0)
                .set(DataAccessAggTaskT::getScheduleRunning, now)
                .set(DataAccessAggTaskT::getScheduleRunningEnd, now)
                .set(DataAccessAggTaskT::getUpdatedTime, LocalDateTime.now()));
    }

    public void statusChanged(String pipelineId, PipelineStatus status) {
        taskService.update(taskUpdate(pipelineId)
                .set(DataAccessAggTaskT::getTaskStatus, taskStatus(status))
                .set(DataAccessAggTaskT::getScheduleRunning, now())
                .set(DataAccessAggTaskT::getUpdatedTime, LocalDateTime.now()));
    }

    public void statusOnly(String pipelineId, int taskStatus) {
        taskService.update(taskUpdate(pipelineId)
                .set(DataAccessAggTaskT::getTaskStatus, taskStatus)
                .set(DataAccessAggTaskT::getUpdatedTime, LocalDateTime.now()));
    }

    private LambdaUpdateWrapper<DataAccessAggTaskT> taskUpdate(String pipelineId) {
        return new LambdaUpdateWrapper<DataAccessAggTaskT>()
                .eq(DataAccessAggTaskT::getPipelineId, pipelineId)
                .eq(DataAccessAggTaskT::getIsDel, 0);
    }

    static int taskStatus(PipelineStatus status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case RUNNING -> 1;
            case DEPLOY_FAILED -> 2;
            default -> 0;
        };
    }

    static String normalizeCatalogTid(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        if ("null".equalsIgnoreCase(normalized) || "undefined".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String now() {
        return DATE_TIME_FORMATTER.format(LocalDateTime.now());
    }
}
