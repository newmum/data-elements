package com.linewell.dataelement.model.common;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * @Description: 数据资产平台 统一响应模型
 * @Author: gaoZhenWen
 * @Date: 2021/11/16 22:04
 */
@Data
public class CommonResponse<T> {
    private Integer code;
    private String msg;
    //private String desc;
    private Boolean success;
    private T data;
    public CommonResponse() {}
    public CommonResponse(StatusCodeEnum statusCodeEnum) {
        this.code = statusCodeEnum.getCode();
        this.msg = statusCodeEnum.getMsg();
        //this.desc = statusCodeEnum.getDesc();
        this.success = statusCodeEnum.getSuccess();
    }
    public CommonResponse(StatusCodeEnum statusCodeEnum, T data) {
        this.code = statusCodeEnum.getCode();
        this.msg = statusCodeEnum.getMsg();
        //this.desc = statusCodeEnum.getDesc();
        this.success = statusCodeEnum.getSuccess();
        this.data = data;
    }

    public CommonResponse(T data) {
        this.code = StatusCodeEnum.SUCCESS.getCode();
        this.msg = StatusCodeEnum.SUCCESS.getMsg();
        //this.desc = StatusCodeEnum.SUCCESS.getDesc();
        this.success = StatusCodeEnum.SUCCESS.getSuccess();
        this.data = data;
    }

    public CommonResponse(BizException bizException) {
        this.code = bizException.getCode();
        this.msg = bizException.getMessage();
        this.success = false;
        //this.desc = bizException.getDesc();
    }

    public CommonResponse(NullPointerException nullPointerException) {
        this.code = -1;
        //this.desc = "未知错误";
        this.msg = "unknown";
        this.success = false;
        nullPointerException.printStackTrace();
    }

    public CommonResponse(HttpMessageNotReadableException exception) {
        this.code = StatusCodeEnum.HTTP_MESSAGE_NOT_READABLE_EXCEPTION.getCode();
        //this.desc = StatusCodeEnum.HTTP_MESSAGE_NOT_READABLE_EXCEPTION.getDesc();
        this.msg = StatusCodeEnum.HTTP_MESSAGE_NOT_READABLE_EXCEPTION.getMsg();
        this.success = false;
        this.data = null;
        exception.printStackTrace();
    }

    public CommonResponse(Exception exception) {
        this.code = -2;
        //this.desc = "未知错误：" + exception.getMessage();
        this.msg = "unknown";
        this.success = false;
        exception.printStackTrace();
    }

    public static CommonResponse success(Object data) {
        return new CommonResponse(data);
    }

    public static CommonResponse success(Integer code, Object data) {
        CommonResponse response = new CommonResponse(data);
        response.setCode(code);
        response.setSuccess(true);
        return response;
    }

    public static CommonResponse success(Integer code, String msg, String desc, Object data) {
        CommonResponse response = new CommonResponse(data);
        response.setCode(code);
        response.setMsg(msg);
        //response.setDesc(desc);
        response.setSuccess(true);
        return response;
    }

    public static CommonResponse fail(Integer code, String msg, String desc) {
        CommonResponse response = new CommonResponse();
        response.setCode(code);
        response.setMsg(msg);
        //response.setDesc(desc);
        response.setSuccess(false);
        return response;
    }

    public static CommonResponse fail(StatusCodeEnum statusCodeEnum) {
        return fail(statusCodeEnum, null);
    }

    public static CommonResponse fail(StatusCodeEnum statusCodeEnum, String desc) {
        CommonResponse response = new CommonResponse();
        response.setCode(statusCodeEnum.getCode());
        response.setMsg(StringUtils.isNotBlank(desc) ? desc:statusCodeEnum.getMsg());
        //response.setDesc(ObjectUtil.isEmpty(desc)? statusCodeEnum.getDesc():desc);
        response.setSuccess(false);
        return response;
    }

}
