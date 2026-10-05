package com.linewell.dataelement.feature.delivery.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("fs_call_log")
public class FlowServiceCallLogEntity {

    @TableId("id")
    private Long id;
    private String flowId;
    private String serviceName;
    private String method;
    private String path;
    private Integer status;
    private Integer success;
    private Long durationMs;
    private String errorMessage;
    private LocalDateTime createdAt;
    private String tenantId;
}
