package com.linewell.dataelement.integration.nifi.canvas.controller;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Preserve actionable preflight failures without exposing SQL or connection settings. */
@RestControllerAdvice(assignableTypes = PipelineController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IncrementalFieldErrorAdvice {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> rejected(ResponseStatusException error) {
        String message = error.getReason() == null ? "流程配置校验失败" : error.getReason();
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("code", error.getStatusCode().value(),
                "success", false, "msg", message, "error", message));
    }
}
