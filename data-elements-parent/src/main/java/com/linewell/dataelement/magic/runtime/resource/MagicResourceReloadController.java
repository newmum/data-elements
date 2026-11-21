package com.linewell.dataelement.magic.runtime.resource;

import com.linewell.dataelement.model.common.CommonResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssssssss.magicapi.core.service.MagicResourceService;

/** Reloads the resource store and registry without depending on a registered Magic API. */
@RestController
public class MagicResourceReloadController {
    private final MagicResourceService resources;

    public MagicResourceReloadController(MagicResourceService resources) {
        this.resources = resources;
    }

    @PostMapping("${data-element.magic.resource-reload-path}")
    public CommonResponse<Map<String, Object>> reload() {
        long started = System.currentTimeMillis();
        resources.refresh();
        return CommonResponse.success(Map.of("success", true, "elapsedMs", System.currentTimeMillis() - started));
    }
}
