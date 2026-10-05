package com.linewell.dataelement.platform.tenant.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.entity.SymTenantT;
import java.util.List;
import org.apache.ibatis.annotations.Param;

@InterceptorIgnore(tenantLine = "true")
public interface SymTenantTMapper extends BaseMapper<SymTenantT> {

    String selectActiveId(@Param("tenantId") String tenantId);

    SymTenantT selectActiveAccountConfig(@Param("tenantId") String tenantId);

    List<SymTenantT> selectLoginOptions();

    List<SymTenantT> selectTenantList();

    SymTenantT selectCurrent(@Param("tenantId") String tenantId);

    int updateTenant(SymTenantT tenant);
}
