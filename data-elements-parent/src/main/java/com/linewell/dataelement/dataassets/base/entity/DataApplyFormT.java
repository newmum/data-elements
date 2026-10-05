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
 * 数据申请单
 * </p>
 *
 * @author zwenbo
 * @since 2026-05-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("data_apply_form_t")
@ApiModel(value="DataApplyFormT对象", description="数据申请单")
public class DataApplyFormT implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键")
    @TableId(value = "tid", type = IdType.ASSIGN_ID)
    private String tid;

    @ApiModelProperty(value = "申请人ID")
    private String applyUserId;

    @ApiModelProperty(value = "申请人名称")
    private String applyUserName;

    @ApiModelProperty(value = "申请部门ID")
    private String applyOrgId;

    @ApiModelProperty(value = "申请单名称")
    private String applyName;

    @ApiModelProperty(value = "目录资产主键(多个，隔开)")
    private String catalogIds;

    @ApiModelProperty(value = "类型：apply-资源申请（订阅申请）、need-需求申请")
    private String type;

    @ApiModelProperty(value = "资产审批状态：0-未提交（草稿）、1-审批中、2-通过、3-不通过、4-撤回 5-驳回")
    private Integer flowStatus;

    @ApiModelProperty(value = "租户号")
    private String tenantId;

    @ApiModelProperty(value = "乐观锁")
    private String revision;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createdTime;

    @ApiModelProperty(value = "更新人")
    private String updatedBy;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updatedTime;

    @ApiModelProperty(value = "创建人")
    private String createdBy;

    @ApiModelProperty(value = "是否删除")
    private Integer isDel;


}
