package com.linewell.dataelement.platform.tenant.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("rm_user_tenant_rela_t")
public class RmUserTenantRelaT {

    @TableId(value = "tid", type = IdType.INPUT)
    private String tid;
    private String userId;
    private String tenantId;
    private Integer isDefault;
    private Integer status;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    private Integer isDel;
}
