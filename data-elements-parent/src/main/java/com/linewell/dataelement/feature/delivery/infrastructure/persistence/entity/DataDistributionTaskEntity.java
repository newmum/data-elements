package com.linewell.dataelement.feature.delivery.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("data_distribution_task_t")
public class DataDistributionTaskEntity {

    @TableId(value = "tid", type = IdType.INPUT)
    private String tid;
    private String applyFormId;
    private String catalogId;
    private String catalogName;
    private String sourceTableId;
    private String sourceTableName;
    private String taskCode;
    private String taskName;
    private String taskStatus;
    private Integer progress;
    private String executionEngine;
    private String executionMode;
    private String statusMessage;
    private LocalDateTime startedTime;
    private LocalDateTime finishedTime;
    private String tenantId;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    private Integer isDel;
}
