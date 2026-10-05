package com.linewell.dataelement.platform.tenant.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("sym_tenant_t")
public class SymTenantT {

    @TableId(value = "tid", type = IdType.INPUT)
    private String tid;
    private String code;
    private String name;
    private Integer status;
    private Integer initFlag;
    private String isolationMode;
    private String jsonConfig;
    private Integer sortNo;
    private LocalDateTime createdTime;
    private String createdBy;
    private LocalDateTime updatedTime;
    private String updatedBy;
    private Integer isDel;
    private String tenantId;
}
