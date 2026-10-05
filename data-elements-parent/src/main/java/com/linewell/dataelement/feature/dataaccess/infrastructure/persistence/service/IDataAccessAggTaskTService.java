package com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service;

import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessAggTaskT;
import com.linewell.dataelement.integration.nifi.canvas.monitor.LatencyMonitorSnapshot;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 数据接入任务表 服务类
 * </p>
 *
 * @author author
 * @since 2026-05-13
 */
public interface IDataAccessAggTaskTService extends IService<DataAccessAggTaskT> {

    /**
     * Ensure there is an access task for the given catalog. If it does not exist, create one.
     *
     * @return access task id
     */
    String ensureTaskForCatalog(String catalogId, String pipelineId, String pipelineName, Object dsl);

    DataAccessAggTaskT findByPipelineId(String pipelineId);

    boolean updateTaskProcessGroup(String pipelineId, String processGroupId);

    boolean updateLatestMonitorState(String pipelineId, LatencyMonitorSnapshot snapshot);
}
