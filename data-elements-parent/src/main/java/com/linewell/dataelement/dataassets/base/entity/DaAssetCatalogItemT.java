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
 * 资源目录数据项
 * </p>
 *
 * @author zwenbo
 * @since 2026-05-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("da_asset_catalog_item_t")
@ApiModel(value="DaAssetCatalogItemT对象", description="资源目录数据项")
public class DaAssetCatalogItemT implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "主键")
    @TableId(value = "tid", type = IdType.ASSIGN_ID)
    private String tid;

    @ApiModelProperty(value = "资产主键")
    private String catalogId;

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

    @ApiModelProperty(value = "字段名称")
    private String colName;

    @ApiModelProperty(value = "字段英文名")
    private String colEn;

    @ApiModelProperty(value = "数据项类型")
    private String colType;

    @ApiModelProperty(value = "注释信息")
    private String colComment;

    @ApiModelProperty(value = "长度")
    private Long colLength;

    @ApiModelProperty(value = "是否主键")
    private String isPk;

    @ApiModelProperty(value = "是否脱敏")
    private Integer isMasking;

    @ApiModelProperty(value = "是否外键")
    private String isFk;

    @ApiModelProperty(value = "是否为空")
    private String isNullable;

    @ApiModelProperty(value = "时间格式")
    private String dateFormat;

    @ApiModelProperty(value = "数据敏感级别")
    private String markLvl;

    @ApiModelProperty(value = "单位")
    private String colUnit;

    @ApiModelProperty(value = "数据精度")
    private String colPrecision;

    @ApiModelProperty(value = "脱敏规则")
    private String maskingId;

    @ApiModelProperty(value = "序号")
    private Integer sortNo;

    @ApiModelProperty(value = "共享类型")
    private String shareType;

    @ApiModelProperty(value = "共享条件")
    private String shareCondition;

    @ApiModelProperty(value = "是否字典")
    private Boolean isDict;

    @ApiModelProperty(value = "字典名称")
    private String dictName;

    @ApiModelProperty(value = "最大长度")
    private String colMaxLength;

    @ApiModelProperty(value = "默认值")
    private String defaultValue;

    @ApiModelProperty(value = "数据标准id")
    private String dataStandardId;

    @ApiModelProperty(value = "质检规则id，多个用，分隔")
    private String qualityRule;

    @ApiModelProperty(value = "启用码表： 0 不启用 1 启用")
    private Integer enableCodeTable;

    @ApiModelProperty(value = "码表id")
    private String codeTableId;

    @ApiModelProperty(value = "来源表字段id")
    private String sourceTableColumnId;

    @ApiModelProperty(value = "目标表字段id")
    private String targetTableColumnId;


}
