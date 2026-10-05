package com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessFieldMapping;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper.DataAccessFieldMappingMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessFieldMappingService;
import org.springframework.stereotype.Service;

@Service
public class DataAccessFieldMappingServiceImpl extends ServiceImpl<DataAccessFieldMappingMapper, DataAccessFieldMapping>
    implements IDataAccessFieldMappingService {
}
