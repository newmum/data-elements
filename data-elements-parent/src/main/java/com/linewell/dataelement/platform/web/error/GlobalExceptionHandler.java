package com.linewell.dataelement.platform.web.error;

import com.linewell.dataelement.model.common.BizException;
import com.linewell.dataelement.model.common.CommonResponse;
import com.linewell.dataelement.model.common.StatusCodeEnum;
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@ControllerAdvice
@ResponseBody
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理自定义业务异常
     */
    @ExceptionHandler(BizException.class)
    public CommonResponse handleBizException(BizException e) {
        logger.error("业务异常: {}", e.getMessage(), e);
        return CommonResponse.fail(e.getCode(), e.getMsg(), e.getDesc());
    }

    @ExceptionHandler(TenantAccessException.class)
    public CommonResponse handleTenantAccessException(TenantAccessException e) {
        logger.warn("Tenant access rejected, code={}, message={}", e.getCode(), e.getMessage());
        return CommonResponse.fail(403, e.getMessage(), e.getCode());
    }

    /**
     * 处理参数验证异常(MethodArgumentNotValidException)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResponse handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        logger.error("参数验证异常: {}", e.getMessage(), e);
        BindingResult bindingResult = e.getBindingResult();
        String errorMsg = bindingResult.getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return CommonResponse.fail(StatusCodeEnum.PARAMS_UNVALID.getCode(), errorMsg, "参数验证失败");
    }

    /**
     * 处理参数验证异常(BindException)
     */
    @ExceptionHandler(BindException.class)
    public CommonResponse handleBindException(BindException e) {
        logger.error("参数绑定异常: {}", e.getMessage(), e);
        BindingResult bindingResult = e.getBindingResult();
        String errorMsg = bindingResult.getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return CommonResponse.fail(StatusCodeEnum.PARAMS_UNVALID.getCode(), errorMsg, "参数绑定失败");
    }



    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public CommonResponse handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        logger.error("参数类型不匹配异常: {}", e.getMessage(), e);
        return CommonResponse.fail(StatusCodeEnum.PARAMS_UNVALID.getCode(),
            "参数[" + e.getName() + "]类型错误，期望类型: " + e.getRequiredType().getSimpleName(),
            "参数类型不匹配");
    }

    /**
     * 处理缺失请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public CommonResponse handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        logger.error("缺失请求参数异常: {}", e.getMessage(), e);
        return CommonResponse.fail(StatusCodeEnum.PARAMS_NOT_FOUND.getCode(),
            "缺少必要参数: " + e.getParameterName(),
            "参数缺失");
    }

    /**
     * 处理重复键异常
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public CommonResponse handleDuplicateKeyException(DuplicateKeyException e) {
        logger.error("数据库重复键异常: {}", e.getMessage(), e);
        return CommonResponse.fail(StatusCodeEnum.BUSI_EXCEPTION.getCode(), "数据已存在", "重复键异常");
    }

    /**
     * 处理HTTP请求方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public CommonResponse handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        logger.error("HTTP请求方法不支持异常: {}", e.getMessage(), e);
        return CommonResponse.fail(StatusCodeEnum.HTTP_METHOD_NOT_SUPPORTED_EXCEPTION.getCode(),
            "请求方法[" + e.getMethod() + "]不被支持",
            "请求方法不支持");
    }

    /**
     * 处理404异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public CommonResponse handleNoHandlerFoundException(NoHandlerFoundException e, HttpServletRequest request) {
        logger.error("404异常: 请求路径 {} 不存在", request.getRequestURL(), e);
        return CommonResponse.fail(StatusCodeEnum.NOT_FOUND.getCode(), "请求的资源不存在", "404错误");
    }

    /**
     * 处理SaToken相关异常
     */
    @ExceptionHandler(cn.dev33.satoken.exception.NotLoginException.class)
    public CommonResponse handleNotLoginException(cn.dev33.satoken.exception.NotLoginException e) {
        logger.error("未登录异常: {}", e.getMessage(), e);
        return CommonResponse.fail(401, "凭证已过期", "未登录异常");
    }

    /**
     * 处理SaToken权限异常
     */
    @ExceptionHandler(cn.dev33.satoken.exception.NotPermissionException.class)
    public CommonResponse handleNotPermissionException(cn.dev33.satoken.exception.NotPermissionException e) {
        logger.error("权限不足异常: {}", e.getMessage(), e);
        return CommonResponse.fail(403, "权限不足", "权限不足");
    }

    /**
     * 处理其他所有异常
     */
    @ExceptionHandler(Exception.class)
    public CommonResponse handleException(Exception e) {
        logger.error("系统异常: {}", e.getMessage(), e);
        return CommonResponse.fail(StatusCodeEnum.ERROR, e.getMessage());
    }
}
