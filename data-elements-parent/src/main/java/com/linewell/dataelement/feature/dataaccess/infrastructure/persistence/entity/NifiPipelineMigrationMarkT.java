package com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@TableName("nifi_pipeline_migration_mark_t")
@ApiModel(value = "NifiPipelineMigrationMarkT对象", description = "NiFi Pipeline 迁移标记表")
public class NifiPipelineMigrationMarkT {

    @TableId(value = "mark_key", type = IdType.INPUT)
    @ApiModelProperty(value = "标记键")
    private String markKey;

    @ApiModelProperty(value = "标记值")
    private String markValue;

    @ApiModelProperty(value = "创建时间戳")
    private Long createdAt;
}
