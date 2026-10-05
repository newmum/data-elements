package com.linewell.dataelement.dataassets.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 数据资产公用扩展属性（应用系统、数据源、数据表、数据目录、数据服务）
 * </p>
 *
 * @author zwenbo
 * @since 2026-05-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("da_prop_t")
@ApiModel(value="DataPropT对象", description="数据资产公用扩展属性")
public class DataPropT implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键")
    @TableId(value = "tid", type = IdType.ASSIGN_ID)
    private String tid;

    @ApiModelProperty(value = "租户号")
    private String tenantId;

    @ApiModelProperty(value = "乐观锁")
    private String revision;

    @ApiModelProperty(value = "创建人")
    private String createdBy;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdTime;

    @ApiModelProperty(value = "更新人")
    private String updatedBy;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updatedTime;

    @ApiModelProperty(value = "是否删除")
    private Integer isDel;

    @ApiModelProperty(value = "资产要素id")
    private String propName;

    @ApiModelProperty(value = "属性值")
    private String propValue;

    @ApiModelProperty(value = "属性类型：0 文本 1 数值 3 字典 4 日期  5 文件 6 日期区间 7 时间区间")
    private String propType;

    @ApiModelProperty(value = "数据资产id(appId,dbId,tablId,fileId,apiId,dirId)汇聚接入任务 aggTaskId")
    private String parentId;

    @ApiModelProperty(value = "资产类型")
    private String dataType;

    @ApiModelProperty(value = "字典类型（大类id）")
    private String dictTypeId;


}
