package com.linewell.dataelement.platform.integration.pingao;

import cn.dev33.satoken.stp.StpUtil;
import com.linewell.dataelement.model.common.CommonResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Protected operational endpoints; the existing options route remains a Magic API. */
@RestController
@RequestMapping("/dst/application")
public class PingaoApplicationController {

    private final PingaoApplicationProperties properties;
    private final PingaoApplicationSyncService service;

    public PingaoApplicationController(
            PingaoApplicationProperties properties, PingaoApplicationSyncService service) {
        this.properties = properties;
        this.service = service;
    }

    @PostMapping("/pingao-sync")
    public CommonResponse<?> synchronize() {
        if (properties.isEnforceManualSyncPermission()) {
            requireManagePermission();
        }
        return CommonResponse.success(service.syncCurrentTenant());
    }

    @GetMapping("/pingao-sync-status")
    public CommonResponse<?> syncStatus() {
        requireManagePermission();
        return CommonResponse.success(service.syncState());
    }

    @GetMapping("/duplicate-preview")
    public CommonResponse<?> duplicatePreview() {
        requireManagePermission();
        return CommonResponse.success(service.duplicatePreview());
    }

    @GetMapping("/legacy-purge-preview")
    public CommonResponse<?> legacyPurgePreview() {
        requireManagePermission();
        return CommonResponse.success(service.legacyPurgePreview());
    }

    @PostMapping("/legacy-purge")
    public CommonResponse<?> purgeLegacy(@RequestBody(required = false) Map<String, Object> body) {
        requireManagePermission();
        Object rawIds = body == null ? null : body.get("ids");
        List<String> ids = rawIds instanceof List<?> list
                ? list.stream().map(String::valueOf).filter(value -> !value.isBlank()).toList()
                : List.of();
        return CommonResponse.success(service.purgeLegacy(ids));
    }

    private void requireManagePermission() {
        StpUtil.checkPermission(properties.getManualSyncPermission());
    }
}
