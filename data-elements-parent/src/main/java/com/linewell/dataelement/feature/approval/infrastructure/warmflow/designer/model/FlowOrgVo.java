package com.linewell.dataelement.feature.approval.infrastructure.warmflow.designer.model;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 流程设计器 - 部门/组织 VO
 *
 * @author data-element
 */
@Data
public class FlowOrgVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private String id;

    /**
     * 组织名称
     */
    private String name;

    /**
     * 父级ID
     */
    private String parentId;

    /**
     * 组织编码
     */
    private String serialNumber;

    /**
     * 组织性质
     */
    private String nature;

    /**
     * 状态（0 启用 1 停用）
     */
    private Integer status;

    /**
     * 排序号
     */
    private Integer sortNum;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
