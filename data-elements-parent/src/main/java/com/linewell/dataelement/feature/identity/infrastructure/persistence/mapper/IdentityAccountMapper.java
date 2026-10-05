package com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.entity.IdentityAccountEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

@InterceptorIgnore(tenantLine = "true")
public interface IdentityAccountMapper extends BaseMapper<IdentityAccountEntity> {

    IdentityAccountEntity selectActiveByUserName(@Param("userName") String userName);

    IdentityAccountEntity selectTenantAccount(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId
    );

    long countTenantAccounts(
            @Param("tenantId") String tenantId,
            @Param("userName") String userName,
            @Param("realName") String realName,
            @Param("ids") List<String> ids
    );

    List<IdentityAccountEntity> selectTenantAccounts(
            @Param("tenantId") String tenantId,
            @Param("userName") String userName,
            @Param("realName") String realName,
            @Param("ids") List<String> ids,
            @Param("offset") long offset,
            @Param("limit") long limit
    );
}
