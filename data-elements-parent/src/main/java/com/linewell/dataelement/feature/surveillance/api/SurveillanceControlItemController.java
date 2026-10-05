package com.linewell.dataelement.feature.surveillance.api;

import com.linewell.dataelement.feature.surveillance.domain.SurveillanceControlItem;
import com.linewell.dataelement.feature.surveillance.infrastructure.SurveillanceControlItemRepository;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/surveillance/control-items")
public class SurveillanceControlItemController {
    private static final String ENGINE = "structured-surveillance";
    private final SurveillanceControlItemRepository repository;

    public SurveillanceControlItemController(SurveillanceControlItemRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<SurveillanceControlItem> list(
            @RequestParam(defaultValue = ENGINE) String engineCode,
            @RequestParam(required = false) String channelCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return repository.findAll(TenantContext.requireTenantId(), engineCode, channelCode, status, keyword);
    }
}
