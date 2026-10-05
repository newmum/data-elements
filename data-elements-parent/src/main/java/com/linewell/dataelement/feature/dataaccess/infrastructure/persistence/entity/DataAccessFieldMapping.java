package com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("data_access_field_mapping")
@ApiModel(value = "DataAccessFieldMapping对象", description = "数据接入任务字段映射表")
public class DataAccessFieldMapping implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键")
    @TableId(value = "tid", type = IdType.ASSIGN_ID)
    private String tid;

    @ApiModelProperty(value = "任务id")
    private String taskId;

    private String sourceField;

    private String sourceFieldCn;

    private Integer sourceFieldLength;

    private String sourceDataType;

    private String targetField;

    private String targetFieldCn;

    private Integer targetFieldLength;

    private String targetDataType;

    private Integer dictEnable;

    private String dictValue;

    private String dictCode;

    private Integer funcEnable;

    private String funcValue;

    private String funcCode;

    private Integer sortNo;

    private String tenantId;

    private String revision;

    private String createdBy;

    private LocalDateTime createdTime;

    private String updatedBy;

    private LocalDateTime updatedTime;

    private Integer isDel;
}
