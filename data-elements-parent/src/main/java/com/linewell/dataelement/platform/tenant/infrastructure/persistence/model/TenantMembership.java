package com.linewell.dataelement.platform.tenant.infrastructure.persistence.model;

import lombok.Data;

@Data
public class TenantMembership {

    private String tenantId;
    private String code;
    private String name;
    private Integer status;
    private Integer initFlag;
    private Integer isDefault;
}
