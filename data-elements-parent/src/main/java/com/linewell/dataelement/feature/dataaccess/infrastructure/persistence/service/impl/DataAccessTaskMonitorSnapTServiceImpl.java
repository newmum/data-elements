package com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessTaskMonitorSnapT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper.DataAccessTaskMonitorSnapTMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessTaskMonitorSnapTService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DataAccessTaskMonitorSnapTServiceImpl extends ServiceImpl<DataAccessTaskMonitorSnapTMapper, DataAccessTaskMonitorSnapT>
    implements IDataAccessTaskMonitorSnapTService {

    @Override
    public List<DataAccessTaskMonitorSnapT> listLatestByTaskId(String taskId, int limit) {
        int size = limit <= 0 ? 20 : limit;
        List<DataAccessTaskMonitorSnapT> all = list(new LambdaQueryWrapper<DataAccessTaskMonitorSnapT>()
            .eq(DataAccessTaskMonitorSnapT::getTaskId, taskId)
            .eq(DataAccessTaskMonitorSnapT::getIsDel, 0)
            .orderByDesc(DataAccessTaskMonitorSnapT::getMonitorTime));
        return all.size() <= size ? all : all.subList(0, size);
    }
}
