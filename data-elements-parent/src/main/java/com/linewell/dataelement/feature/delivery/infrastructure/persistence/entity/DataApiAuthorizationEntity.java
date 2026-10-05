package com.linewell.dataelement.feature.delivery.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("data_api_authorization_t")
public class DataApiAuthorizationEntity {

    @TableId(value = "tid", type = IdType.INPUT)
    private String tid;
    private String applyFormId;
    private String catalogId;
    private String apiId;
    private String applicationId;
    private String applyUserId;
    private String authorizationStatus;
    private String gatewaySyncStatus;
    private String authorizationScope;
    private String gatewayMessage;
    private LocalDateTime authorizedTime;
    private LocalDateTime expireTime;
    private String tenantId;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    private Integer isDel;
}
