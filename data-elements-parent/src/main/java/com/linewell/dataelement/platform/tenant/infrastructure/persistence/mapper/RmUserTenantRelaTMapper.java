package com.linewell.dataelement.platform.tenant.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.entity.RmUserTenantRelaT;
import com.linewell.dataelement.platform.tenant.infrastructure.persistence.model.TenantMembership;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

@InterceptorIgnore(tenantLine = "true")
public interface RmUserTenantRelaTMapper extends BaseMapper<RmUserTenantRelaT> {

    List<TenantMembership> selectAvailable(@Param("userId") String userId);

    List<TenantMembership> selectCompatible(@Param("userId") String userId);

    TenantMembership selectMembership(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId
    );

    List<TenantMembership> selectTenantOptions(@Param("userId") String userId);

    int clearDefault(
            @Param("userId") String userId,
            @Param("operator") String operator,
            @Param("updatedTime") LocalDateTime updatedTime
    );

    String selectRelationId(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId
    );

    int updateAssignment(RmUserTenantRelaT relation);
}
