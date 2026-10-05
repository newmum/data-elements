package com.linewell.dataelement.platform.configuration;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.model.common.CommonResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Transactional mutation boundary for cached system configuration.
 */
@RestController
@RequestMapping("/sym/system-config")
public class SystemConfigController {

    private final SystemConfigService service;

    public SystemConfigController(SystemConfigService service) {
        this.service = service;
    }

    @PostMapping("/save")
    public CommonResponse<?> save(@RequestBody Map<String, Object> body) {
        return CommonResponse.success(service.save(body, String.valueOf(StpUtil.getLoginId())));
    }

    @PostMapping("/delete")
    public CommonResponse<?> delete(@RequestBody Map<String, Object> body) {
        return CommonResponse.success(service.delete(body, String.valueOf(StpUtil.getLoginId())));
    }

    @PostMapping("/cache/evict")
    public CommonResponse<?> evict(@RequestBody Map<String, Object> body) {
        service.evict(
                String.valueOf(body.getOrDefault("tenantId", "GLOBAL")),
                String.valueOf(body.get("configGroup"))
        );
        return CommonResponse.success(true);
    }

}
