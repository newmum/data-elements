package com.linewell.dataelement.feature.identity.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("rm_user_t")
public class IdentityAccountEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String realName;
    private String userName;
    private String password;
    private String phone;
    private String idCard;
    private String gender;
    private LocalDate dateBirth;
    private String nation;
    private Integer status;
    private String appFileId;
    private String officeAddress;
    private String jobNumber;
    private Integer userType;
    private String originPosition;
    private String originRank;
    private String authorizedStrength;
    private Integer isTemporary;
    private String createId;
    private LocalDateTime createTime;
    private String updateId;
    private LocalDateTime updateTime;
    private Integer deleted;
}
