package com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessTaskMonitorSnapT;
import java.util.List;

public interface IDataAccessTaskMonitorSnapTService extends IService<DataAccessTaskMonitorSnapT> {

    List<DataAccessTaskMonitorSnapT> listLatestByTaskId(String taskId, int limit);
}
