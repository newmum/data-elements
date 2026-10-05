package com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

@InterceptorIgnore(tenantLine = "true")
public interface RegisteredJdbcDataSourceMapper {

    Map<String, Object> selectEndpoint(
            @Param("tenantId") String tenantId,
            @Param("datasourceId") String datasourceId,
            @Param("tableId") String tableId
    );
}

