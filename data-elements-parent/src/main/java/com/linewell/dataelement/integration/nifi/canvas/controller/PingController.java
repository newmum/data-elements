package com.linewell.dataelement.integration.nifi.canvas.controller;

import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/nifi/api")
@Api(tags = "NiFi 健康检查")
public class PingController {

    @GetMapping("/ping")
    @Operation(summary = "健康检查(ping)")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
