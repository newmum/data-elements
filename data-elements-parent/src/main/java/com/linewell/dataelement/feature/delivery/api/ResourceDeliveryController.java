package com.linewell.dataelement.feature.delivery.api;

import com.linewell.dataelement.feature.delivery.application.ResourceDeliveryService;
import com.linewell.dataelement.model.common.CommonResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/dataassets/delivery")
public class ResourceDeliveryController {

    private final ResourceDeliveryService resourceDeliveryService;

    public ResourceDeliveryController(ResourceDeliveryService resourceDeliveryService) {
        this.resourceDeliveryService = resourceDeliveryService;
    }

    @GetMapping("/detail")
    public CommonResponse<Map<String, Object>> detail(
            @RequestParam String applyFormId
    ) {
        return CommonResponse.success(resourceDeliveryService.detail(applyFormId));
    }
}
