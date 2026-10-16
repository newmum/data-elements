package com.linewell.dataelement.feature.diagnostics;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = DiagnosticController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DiagnosticControllerAdvice {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> rejected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("message", error.getReason() == null ? "诊断请求失败" : error.getReason()));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> failed(Exception error) {
        int status = error instanceof cn.dev33.satoken.exception.NotLoginException ? 401
                : error instanceof com.linewell.dataelement.platform.tenant.domain.TenantAccessException ? 403
                : error instanceof org.springframework.http.converter.HttpMessageNotReadableException ? 400 : 503;
        return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(Map.of("message", status == 401 ? "登录已失效，请重新登录" : status == 403 ? "当前行业租户会话无效" : status == 400 ? "诊断请求格式不正确" : "诊断服务暂不可用，请稍后重试"));
    }
}
