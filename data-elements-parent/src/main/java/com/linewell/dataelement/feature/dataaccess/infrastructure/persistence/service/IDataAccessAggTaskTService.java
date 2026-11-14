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

    DataAccessAggTaskT findByPipelineId(String pipelineId);

    boolean updateLatestMonitorState(String pipelineId, LatencyMonitorSnapshot snapshot);
}
