package com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@TableName("nifi_pipeline_t")
@ApiModel(value = "NifiPipelineT对象", description = "NiFi Pipeline 主存储表")
public class NifiPipelineT {

    @TableId(value = "id", type = IdType.INPUT)
    @ApiModelProperty(value = "Pipeline 主键")
    private String id;

    @ApiModelProperty(value = "名称")
    private String name;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "状态")
    private String status;

    @ApiModelProperty(value = "DSL 配置 JSON")
    private String dslJson;

    @ApiModelProperty(value = "DSL 哈希值")
    private String dslHash;

    @ApiModelProperty(value = "dsl 版本")
    private Long dslVersion;

    @ApiModelProperty(value = "NiFi Process Group ID")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nifiProcessGroupId;

    @ApiModelProperty(value = "最近部署哈希")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastDeployedHash;

    @ApiModelProperty(value = "最近部署时间")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long lastDeployedAt;

    @ApiModelProperty(value = "最近停止时间")
    private Long lastStoppedAt;

    @ApiModelProperty(value = "节点映射 JSON")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nodeMappingJson;

    @ApiModelProperty(value = "最近 Bulletin ID")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long lastBulletinId;

    @ApiModelProperty(value = "创建时间")
    private Long createdAt;

    @ApiModelProperty(value = "更新时间")
    private Long updatedAt;

    @ApiModelProperty(value = "删除标记（0 否 1 是）")
    private Integer isDel;
}
