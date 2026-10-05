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
 * 资产数据表(父表)
 * </p>
 *
 * @author zwenbo
 * @since 2026-05-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("da_asset_t")
@ApiModel(value="DaAssetT对象", description="资产数据表(父表)")
public class DaAssetT implements Serializable {

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

    @ApiModelProperty(value = "资产类型（ 应用系统app 0 数据库dv 1 数据库表table 2 数据目录catalog 3 文件file 4  文件夹dir 5）")
    private String assetType;

    @ApiModelProperty(value = "是否删除")
    private Integer isDel;

    @ApiModelProperty(value = "注册时间")
    private LocalDateTime regTime;

    @ApiModelProperty(value = "资产状态: 0 带注册 1 审批中 2 已注册")
    private Integer assetStatus;

    @ApiModelProperty(value = "资产审批状态：0-未提交、1-审批中、2-通过、3-不通过、4-撤回")
    private Integer flowStatus;

    @ApiModelProperty(value = "数据目录个数（应用、库表的目录）")
    private String dataCatalogNum;

    @ApiModelProperty(value = "表数量个数")
    private String tableNum;

    @ApiModelProperty(value = "审批单id(进入审批流程时存在，审批结束移除)")
    private String flowOrderId;


}
