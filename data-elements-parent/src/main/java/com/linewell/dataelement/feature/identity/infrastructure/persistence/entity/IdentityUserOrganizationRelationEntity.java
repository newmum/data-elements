package com.linewell.dataelement.feature.identity.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("rm_user_org_rela_t")
public class IdentityUserOrganizationRelationEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String tenantId;
    private String orgId;
    private String userId;
    private Integer sortNum;
    private String jobType;
}
