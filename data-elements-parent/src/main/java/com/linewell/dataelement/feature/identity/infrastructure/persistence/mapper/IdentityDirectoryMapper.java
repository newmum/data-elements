package com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linewell.dataelement.feature.identity.domain.IdentityDirectoryEntry;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface IdentityDirectoryMapper {

    IPage<IdentityDirectoryEntry> selectRolePage(
            Page<IdentityDirectoryEntry> page,
            @Param("tenantId") String tenantId,
            @Param("code") String code,
            @Param("name") String name
    );

    IPage<IdentityDirectoryEntry> selectOrganizationPage(
            Page<IdentityDirectoryEntry> page,
            @Param("tenantId") String tenantId,
            @Param("name") String name
    );

    List<IdentityDirectoryEntry> selectAllOrganizations(@Param("tenantId") String tenantId);

    List<IdentityDirectoryEntry> selectRoles(
            @Param("ids") List<String> ids,
            @Param("tenantId") String tenantId
    );

    List<IdentityDirectoryEntry> selectOrganizations(
            @Param("ids") List<String> ids,
            @Param("tenantId") String tenantId
    );
}
