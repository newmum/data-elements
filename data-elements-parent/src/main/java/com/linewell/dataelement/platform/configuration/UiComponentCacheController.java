package com.linewell.dataelement.platform.configuration;

import com.linewell.dataelement.model.common.CommonResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrative refresh boundary for the shared low-code component bundle. */
@RestController
@RequestMapping("/sym/component/cache")
public class UiComponentCacheController {

    private final UiComponentCacheService cacheService;

    public UiComponentCacheController(UiComponentCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @PostMapping("/refresh")
    public CommonResponse<?> refresh() {
        return CommonResponse.success(cacheService.refresh());
    }
}
