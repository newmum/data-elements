package com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper;

import com.linewell.dataelement.feature.identity.domain.IdentityOrganization;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface IdentityUserLookupMapper {

    List<String> selectUserIdsByOrgIds(
            @Param("orgIds") List<String> orgIds,
            @Param("tenantId") String tenantId
    );

    List<String> selectUserIdsByRoleCode(
            @Param("appId") String appId,
            @Param("roleCode") String roleCode,
            @Param("tenantId") String tenantId
    );

    IdentityOrganization selectOrganizationById(@Param("organizationId") String organizationId);

    List<String> selectRoleCodes(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId,
            @Param("appId") String appId
    );

    List<String> selectRoleCodesForUsers(
            @Param("userIds") List<String> userIds,
            @Param("tenantId") String tenantId,
            @Param("appId") String appId
    );

    /**
     * Resolves role identifiers instead of role codes so configurable data scopes
     * never need to depend on a role's display name or code.
     */
    List<String> selectRoleIds(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId
    );

    int countActiveRole(
            @Param("roleId") String roleId,
            @Param("tenantId") String tenantId
    );

    String selectRoleDataScope(@Param("roleId") String roleId, @Param("tenantId") String tenantId);

    List<String> selectRoleDataScopes(@Param("roleIds") List<String> roleIds, @Param("tenantId") String tenantId);

    int updateRoleDataScope(@Param("roleId") String roleId, @Param("tenantId") String tenantId, @Param("scope") String scope);

    List<String> selectOrganizationIds(
            @Param("userId") String userId,
            @Param("tenantId") String tenantId
    );

    List<String> selectOrganizationIdsForUsers(
            @Param("userIds") List<String> userIds,
            @Param("tenantId") String tenantId
    );
}
