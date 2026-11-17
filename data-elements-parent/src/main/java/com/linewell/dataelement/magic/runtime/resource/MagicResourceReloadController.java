package com.linewell.dataelement.magic.runtime.resource;

import com.linewell.dataelement.model.common.CommonResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssssssss.magicapi.core.resource.Resource;
import org.ssssssss.magicapi.core.service.MagicResourceService;

/** Reloads the resource store and registry without depending on a registered Magic API. */
@RestController
public class MagicResourceReloadController {
    private final Resource databaseResource;
    private final MagicResourceService resources;

    public MagicResourceReloadController(@Qualifier("magicDatabaseResource") Resource databaseResource,
                                         MagicResourceService resources) {
        this.databaseResource = databaseResource;
        this.resources = resources;
    }

    @PostMapping("${data-element.magic.resource-reload-path}")
    public CommonResponse<Map<String, Object>> reload() {
        long started = System.currentTimeMillis();
        databaseResource.readAll();
        resources.refresh();
        return CommonResponse.success(Map.of("success", true, "elapsedMs", System.currentTimeMillis() - started));
    }
}
