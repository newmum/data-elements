package com.linewell.dataelement.feature.identity.domain;

import java.util.Date;
import lombok.Data;

@Data
public class IdentityDirectoryEntry {
    private String id;
    private String code;
    private String name;
    private String parentId;
    private String appId;
    private String description;
    private String phone;
    private Integer status;
    private Integer sortNum;
    private Date createTime;
    private Date updateTime;
}
