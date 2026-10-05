package com.linewell.dataelement.model.common;

import cn.hutool.core.util.ObjectUtil;
import lombok.Data;

/**
 * @Description: 数据资产平台 业务异常
 * @Author: gaoZhenWen
 * @Date: 2022/7/26 17:53
 * @Copyright: Fujian Linewell Software Co., Ltd. All rights reserved.
 */
@Data
public class BizException extends RuntimeException {
    static final long serialVersionUID = 1L;
    private Integer code;
    private String desc;
    private String msg;
    public BizException(){
        super();
    }
    public BizException(StatusCodeEnum statusCodeEnum) {
        super(statusCodeEnum.getMsg());
        this.code = statusCodeEnum.getCode();
        this.msg = statusCodeEnum.getMsg();
        this.desc = statusCodeEnum.getDesc();
    }
    public BizException(StatusCodeEnum statusCodeEnum, String desc) {
        super(statusCodeEnum.getMsg());
        this.code = statusCodeEnum.getCode();
        this.msg = statusCodeEnum.getMsg();
        this.desc = ObjectUtil.isEmpty(desc) ? statusCodeEnum.getDesc(): desc;
    }
    public BizException(Integer code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }
    public BizException(Integer code, String desc, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
        this.desc = desc;
    }

}
