package com.linewell.dataelement.feature.approval.infrastructure.warmflow.designer.model;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 流程设计器 - 用户 VO
 *
 * @author data-element
 */
@Data
public class FlowUserVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    private String id;

    /**
     * 用户名（登录账号）
     */
    private String userName;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 状态（0 启用 1 停用）
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
