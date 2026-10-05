package com.linewell.dataelement.feature.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.entity.IdentityUserOrganizationRelationEntity;
import com.linewell.dataelement.feature.identity.infrastructure.persistence.mapper.IdentityUserOrganizationRelationMapper;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityTenantRelationService {

    private final IdentityUserOrganizationRelationMapper organizationRelationMapper;

    public IdentityTenantRelationService(
            IdentityUserOrganizationRelationMapper organizationRelationMapper
    ) {
        this.organizationRelationMapper = organizationRelationMapper;
    }

    @Transactional
    public void saveOrganization(String userId, String organizationId, String tenantId) {
        if (tenantId == null || !tenantId.equals(TenantContext.requireTenantId())) {
            throw new TenantAccessException(
                    "IDENTITY-TENANT-MISMATCH",
                    "用户组织关系租户与当前会话不一致"
            );
        }
        if (userId == null || userId.isBlank() || organizationId == null || organizationId.isBlank()) {
            throw new TenantAccessException("IDENTITY-PARAM-INVALID", "用户和所属机构不能为空");
        }
        organizationRelationMapper.delete(
                new LambdaQueryWrapper<IdentityUserOrganizationRelationEntity>()
                        .eq(IdentityUserOrganizationRelationEntity::getUserId, userId)
        );
        IdentityUserOrganizationRelationEntity relation =
                new IdentityUserOrganizationRelationEntity();
        relation.setId(NumericId.nextId());
        relation.setTenantId(tenantId);
        relation.setOrgId(organizationId);
        relation.setUserId(userId);
        relation.setSortNum(0);
        relation.setJobType("1");
        organizationRelationMapper.insert(relation);
    }
}
