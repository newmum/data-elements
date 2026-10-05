package com.linewell.dataelement.feature.approval.infrastructure.warmflow.designer.model;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 流程设计器 - 角色 VO
 *
 * @author data-element
 */
@Data
public class FlowRoleVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private String id;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色编码
     */
    private String codeNum;

    /**
     * 应用ID
     */
    private String appId;

    /**
     * 角色描述
     */
    private String description;

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
