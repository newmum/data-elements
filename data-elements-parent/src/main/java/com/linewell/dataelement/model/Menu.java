package com.linewell.dataelement.model;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Description: 菜单实体类
 * @Author: Assistant
 * @Date: 2025/4/11
 * @Copyright: Fujian Linewell Software Co., Ltd. All rights reserved.
 */
@Data
@AllArgsConstructor
public class Menu implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 菜单ID
     */
    private String id;

    /**
     * 菜单名称
     */
    private String name;

    /**
     * 父级菜单ID
     */
    private String parentId;

    /**
     * 静态图标
     */
    private String staticIcon;

    /**
     * 动态图片
     */
    private String dynamicImg;

    /**
     * 状态 (0:正常 1:停用)
     */
    private Integer status;

    /**
     * 资源类型 (0:菜单 1:按钮)
     */
    private Integer resourceType;

    /**
     * 编号
     */
    private String sno;

    /**
     * URL类型 (0:内部链接 1:外部链接)
     */
    private Integer urlType;

    /**
     * 访问URL
     */
    private String url;

    /**
     * 方法名
     */
    private String methodName;

    /**
     * 创建人ID
     */
    private String createId;

    /**
     * 更新人ID
     */
    private String updateId;

    /**
     * 删除标识 (0:未删除 1:已删除)
     */
    private Integer deleted;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 排序号
     */
    private Integer sortNum;

    /**
     * 编码
     */
    private String code;

    /**
     * 打开方式 (0:当前窗口 1:新窗口)
     */
    private Integer openType;

    /**
     * 顶级菜单ID
     */
    private String topId;

    /**
     * 应用ID
     */
    private String appId;
    /**
     * 子菜单列表
     */
    List<Menu> children;

    //创建构建方法
    public Menu(String id, String name, String parentId, String staticIcon, String dynamicImg, Integer status, Integer resourceType, String sno, Integer urlType, String url, String methodName, String createId, String updateId, Integer deleted, Date createTime, Date updateTime, Integer sortNum, String code, Integer openType, String topId, String appId) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.staticIcon = staticIcon;
        this.dynamicImg = dynamicImg;
        this.status = status;
        this.resourceType = resourceType;
        this.sno = sno;
        this.urlType = urlType;
        this.url = url;
        this.methodName = methodName;
        this.createId = createId;
        this.updateId = updateId;
        this.deleted = deleted;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.sortNum = sortNum;
        this.code = code;
        this.openType = openType;
        this.topId = topId;
        this.appId = appId;
    }
}
