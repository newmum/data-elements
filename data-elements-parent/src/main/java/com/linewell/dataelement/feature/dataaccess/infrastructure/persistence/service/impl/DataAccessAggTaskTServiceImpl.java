package com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessAggTaskT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper.DataAccessAggTaskTMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessAggTaskTService;
import com.linewell.dataelement.integration.nifi.canvas.monitor.LatencyMonitorSnapshot;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.time.LocalDateTime;

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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
