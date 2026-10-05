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
 * 审批单资产关联表
 * </p>
 *
 * @author zwenbo
 * @since 2026-05-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("da_order_asset_rela")
@ApiModel(value="DaOrderAssetRela对象", description="审批单资产关联表")
public class DaOrderAssetRela implements Serializable {

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

    @ApiModelProperty(value = "审批单id")
    private String flowOrderId;

    @ApiModelProperty(value = "审批类型：0-登记审批(数据直接落库)（regist）、1-变更审批 (change) 2-升级审批 (update) 3-接入审批 (access)")
    private String flowType;

    @ApiModelProperty(value = "资产审批状态：0-未提交、1-审批中、2-通过、3-不通过、4-撤回 5-驳回")
    private Integer flowStatus;

    @ApiModelProperty(value = "资产id(或其关联类型id)")
    private String assetId;

    @ApiModelProperty(value = "资产类型（应用系统 0 app 数据库 db 1 数据库表 table 2 数据目录 catalog 3 文件 file 4  文件夹 dir 5）")
    private String assetType;

    @ApiModelProperty(value = "资产版本号 （变更、升级）")
    private String assetVersion;

    @ApiModelProperty(value = "资产版本数据数据快照(变更、升级)")
    private String assetSnapshotData;

    @ApiModelProperty(value = "新资产数据（变更、升级）")
    private String assetNewData;

    @ApiModelProperty(value = "流程实例id")
    private byte[] instanceId;


}
