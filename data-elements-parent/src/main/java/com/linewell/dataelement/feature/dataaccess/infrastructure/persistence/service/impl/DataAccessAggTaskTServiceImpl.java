package com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.linewell.dataelement.dataassets.base.entity.DaAssetT;
import com.linewell.dataelement.dataassets.base.entity.DataPropT;
import com.linewell.dataelement.dataassets.base.service.IDaAssetTService;
import com.linewell.dataelement.dataassets.base.service.IDataPropTService;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessAggTaskT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper.DataAccessAggTaskTMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessAggTaskTService;
import com.linewell.dataelement.integration.nifi.canvas.monitor.LatencyMonitorSnapshot;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.linewell.dataelement.platform.persistence.id.NumericId;

import org.springframework.stereotype.Service;

/**
 * <p>
 * 数据接入任务表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-05-13
 */
@Service
public class DataAccessAggTaskTServiceImpl extends ServiceImpl<DataAccessAggTaskTMapper, DataAccessAggTaskT> implements IDataAccessAggTaskTService {

    private final IDaAssetTService daAssetTService;
    private final IDataPropTService dataPropTService;

    public DataAccessAggTaskTServiceImpl(IDaAssetTService daAssetTService, IDataPropTService dataPropTService) {
        this.daAssetTService = daAssetTService;
        this.dataPropTService = dataPropTService;
    }

    @Override
    public String ensureTaskForCatalog(String catalogId, String pipelineId, String pipelineName, Object dsl) {
        catalogId = normalizeCatalogId(catalogId);
        DataAccessAggTaskT linked = findByPipelineId(pipelineId);
        if (linked != null) {
            linked.setTaskName(isBlank(pipelineName) ? linked.getTaskName() : pipelineName);
            fillFromDsl(linked, dsl);
            linked.setUpdatedTime(LocalDateTime.now());
            updateById(linked);
            return linked.getTid();
        }
        //兼容手动创建的任务
        if (!isBlank(catalogId)) {
            DataAccessAggTaskT byCatalog = list(new LambdaQueryWrapper<DataAccessAggTaskT>()
                    .eq(DataAccessAggTaskT::getDataCatalogId, catalogId)
                    .eq(DataAccessAggTaskT::getIsDel, 0))
                    .stream().findFirst().orElse(null);
            if (byCatalog != null) {
                if (!isBlank(byCatalog.getPipelineId()) && !byCatalog.getPipelineId().equals(pipelineId)) {
                    throw new IllegalStateException("该目录已关联流程，请重新打开已保存的任务后编辑");
                }
                // A catalog may already point to a canvas the user has edited.  A
                // later deploy of another pipeline must not silently steal that
                // task link; otherwise reopening the task appears to reset the
                // saved canvas.  Only fill a genuinely missing pipeline link.
                if (isBlank(byCatalog.getPipelineId()) && !isBlank(pipelineId)) {
                    byCatalog.setPipelineId(pipelineId);
                    byCatalog.setTaskName(isBlank(pipelineName) ? byCatalog.getTaskName() : pipelineName);
                    fillFromDsl(byCatalog, dsl);
                    byCatalog.setUpdatedTime(LocalDateTime.now());
                    updateById(byCatalog);
                }
                return byCatalog.getTid();
            }
        }

        DataAccessAggTaskT task = new DataAccessAggTaskT();
        LocalDateTime now = LocalDateTime.now();
        task.setTid(simpleId());
        task.setTenantId(com.linewell.dataelement.platform.tenant.domain.TenantContext.requireTenantId());
        task.setDataCatalogId(catalogId);
        task.setPipelineId(pipelineId);
        task.setTaskName(isBlank(pipelineName) ? "数据接入任务" : pipelineName);
        task.setTaskDesc("由流程画布创建");
        task.setTaskStatus(0);
        task.setIsDel(0);
        task.setCreatedTime(now);
        task.setUpdatedTime(now);

        if (!isBlank(catalogId)) {
            fillFromCatalog(task, catalogId);
        }
        fillFromDsl(task, dsl);
        save(task);
        return task.getTid();
    }

    @Override
    public DataAccessAggTaskT findByPipelineId(String pipelineId) {
        if (isBlank(pipelineId)) {
            return null;
        }
        return list(new LambdaQueryWrapper<DataAccessAggTaskT>()
                .eq(DataAccessAggTaskT::getPipelineId, pipelineId)
                .eq(DataAccessAggTaskT::getIsDel, 0))
                .stream().findFirst().orElse(null);
    }

    @Override
    public boolean updateTaskProcessGroup(String pipelineId, String processGroupId) {
        if (isBlank(pipelineId)) {
            return false;
        }
        return update(new LambdaUpdateWrapper<DataAccessAggTaskT>()
                .eq(DataAccessAggTaskT::getPipelineId, pipelineId)
                .eq(DataAccessAggTaskT::getIsDel, 0)
                .set(DataAccessAggTaskT::getProcessGroupId, processGroupId)
                .set(DataAccessAggTaskT::getUpdatedTime, LocalDateTime.now()));
    }

    @Override
    public boolean updateLatestMonitorState(String pipelineId, LatencyMonitorSnapshot snapshot) {
        if (isBlank(pipelineId) || snapshot == null) {
            return false;
        }
        return update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<DataAccessAggTaskT>()
                .eq(DataAccessAggTaskT::getPipelineId, pipelineId)
                .eq(DataAccessAggTaskT::getIsDel, 0)
                .set(DataAccessAggTaskT::getMonitorTime, snapshot.monitorTime())
                .set(DataAccessAggTaskT::getDelayLevel, snapshot.delayLevel())
                .set(DataAccessAggTaskT::getDelayMsEstimate, snapshot.delayMsEstimate())
                .set(DataAccessAggTaskT::getThresholdMs, snapshot.thresholdMs())
                .set(DataAccessAggTaskT::getIsTimeout, snapshot.isTimeout())
                .set(DataAccessAggTaskT::getQueuedCount, snapshot.queuedCount())
                .set(DataAccessAggTaskT::getQueuedBytes, snapshot.queuedBytes())
                .set(DataAccessAggTaskT::getActiveThreadCount, snapshot.activeThreadCount())
                .set(DataAccessAggTaskT::getFlowFilesIn, snapshot.flowFilesIn())
                .set(DataAccessAggTaskT::getFlowFilesOut, snapshot.flowFilesOut())
                .set(DataAccessAggTaskT::getBytesIn, snapshot.bytesIn())
                .set(DataAccessAggTaskT::getBytesOut, snapshot.bytesOut())
                .set(DataAccessAggTaskT::getMonitorStatus, snapshot.monitorStatus())
                .set(DataAccessAggTaskT::getMonitorMsg, snapshot.monitorMsg())
                .set(DataAccessAggTaskT::getUpdatedTime, LocalDateTime.now()));
    }

    private void fillFromCatalog(DataAccessAggTaskT task, String catalogId) {
        DaAssetT catalog = daAssetTService.list(new LambdaQueryWrapper<DaAssetT>()
                        .eq(DaAssetT::getTid, catalogId)
                        .eq(DaAssetT::getIsDel, 0))
                .stream().findFirst().orElse(null);
        if (catalog != null && isBlank(task.getTenantId())) {
            task.setTenantId(catalog.getTenantId());
        }
        List<DataPropT> props = dataPropTService.list(new LambdaQueryWrapper<DataPropT>()
                .eq(DataPropT::getParentId, catalogId)
                .eq(DataPropT::getIsDel, 0));
        if (props == null || props.isEmpty()) {
            return;
        }
        Map<String, String> propMap = new HashMap<>();
        for (DataPropT prop : props) {
            if (prop.getPropName() != null) {
                propMap.put(prop.getPropName(), prop.getPropValue());
            }
        }

        if (isBlank(task.getTaskName())) {
            task.setTaskName(firstNonBlankFromProps(propMap, "name", "catalogName", "title"));
        }
        if (isBlank(task.getTaskDesc())) {
            task.setTaskDesc(firstNonBlankFromProps(propMap, "description", "desc", "remark"));
        }
        task.setSourceDbId(firstNonBlankWithCurrent(task.getSourceDbId(), propMap, "dbId", "sourceDbId"));
        task.setSourceTableId(firstNonBlankWithCurrent(task.getSourceTableId(), propMap, "sourceTableId"));
        task.setSourceTablePrimaryKey(firstNonBlankWithCurrent(task.getSourceTablePrimaryKey(), propMap, "sourceTablePrimaryKey"));
        task.setSourceTableIncrementKey(firstNonBlankWithCurrent(task.getSourceTableIncrementKey(), propMap, "sourceTableIncrementKey", "incrementalColumn"));
        task.setTargetDbId(firstNonBlankWithCurrent(task.getTargetDbId(), propMap, "targetDbId"));
        task.setTargetTableId(firstNonBlankWithCurrent(task.getTargetTableId(), propMap, "targetTableId"));
        task.setTargetTablePrimaryKey(firstNonBlankWithCurrent(task.getTargetTablePrimaryKey(), propMap, "targetTablePrimaryKey"));
    }

    @SuppressWarnings("unchecked")
    private void fillFromDsl(DataAccessAggTaskT task, Object dslObj) {
        if (dslObj instanceof com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline.Dsl typed) {
            dslObj = Map.of("nodes", typed.nodes() == null ? List.of() : typed.nodes().stream()
                    .map(node -> Map.of("category", node.category() == null ? "" : node.category(),
                            "config", node.config() == null ? Map.of() : node.config())).toList());
        }
        if (!(dslObj instanceof Map<?, ?> dsl)) {
            return;
        }
        Object nodesObj = dsl.get("nodes");
        if (!(nodesObj instanceof Iterable<?> nodes)) {
            return;
        }
        for (Object item : nodes) {
            if (!(item instanceof Map<?, ?> node)) {
                continue;
            }
            String category = stringVal(node.get("category"));
            Object configObj = node.get("config");
            if (!(configObj instanceof Map<?, ?> configRaw)) {
                continue;
            }
            Map<Object, Object> config = (Map<Object, Object>) configRaw;
            if ("source".equalsIgnoreCase(category)) {
                String datasourceId = firstNonBlank(config, "registeredDatasourceId", "selectedDatabaseId", "sourceDbId", "dbId");
                if (!isBlank(datasourceId)) task.setSourceDbId(datasourceId);
                task.setSourceTableId(firstNonBlank(config, "sourceTableId"));
                task.setSourceTablePrimaryKey(firstNonBlank(config, "primaryKey", "pk", "updateKeys"));
                task.setSourceTableIncrementKey(firstNonBlank(config, "incrementalColumn", "incrementKey"));
            } else if ("sink".equalsIgnoreCase(category)) {
                task.setTargetDbId(firstNonBlank(config, "targetDbId", "dbId", "jdbcUrl"));
                task.setTargetTableId(firstNonBlank(config, "targetTableId", "table"));
                task.setTargetTablePrimaryKey(firstNonBlank(config, "updateKeys", "primaryKey", "pk"));
            }
        }
    }

    private String firstNonBlank(Map<Object, Object> map, String... keys) {
        for (String key : keys) {
            String v = stringVal(map.get(key));
            if (!isBlank(v)) {
                return v;
            }
        }
        return null;
    }

    private String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlankWithCurrent(String current, Map<String, String> map, String... keys) {
        if (!isBlank(current)) {
            return current;
        }
        return firstNonBlankFromProps(map, keys);
    }

    private String firstNonBlankFromProps(Map<String, String> map, String... keys) {
        for (String key : keys) {
            String v = map.get(key);
            if (!isBlank(v)) {
                return v;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeCatalogId(String catalogId) {
        if (catalogId == null) {
            return null;
        }
        String normalized = catalogId.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if ("null".equalsIgnoreCase(normalized) || "undefined".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private String simpleId() {
        return NumericId.nextId();
    }
}
