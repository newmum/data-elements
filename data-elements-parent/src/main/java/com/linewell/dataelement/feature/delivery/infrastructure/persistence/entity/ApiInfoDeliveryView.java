package com.linewell.dataelement.feature.delivery.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("api_info_t")
public class ApiInfoDeliveryView {

    @TableId("tid")
    private String tid;
    private String serviceName;
    private String serviceCode;
    private String method;
    private String publishAddress;
    private String publishAddressFull;
    private String uri;
    private String version;
    private Integer isDel;
}
