package com.linewell.dataelement.model.user;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * @Description: 统一身份认证平台 用户模型
 * @Author: gaoZhenWen
 * @Date: 2022/7/26 17:40
 * @Copyright: Fujian Linewell Software Co., Ltd. All rights reserved.
 */
@Data
public class AuthUser implements Serializable {

    private String id;
    private String userName;
    private String realName;
    private String status;
    private String idCard;
    private String phone;
    private String email;
    private String nation;
    private String faxPhone;
    private String fixedPhone;
    private String dateBirth;
    private String sortNum;
    private String avatar;
    private Date createTime;
    private Date updateTime;
    private String password;

}
