package com.linewell.dataelement.bk.api;

import com.linewell.dataelement.bk.domain.SurveillanceControlItem;
import com.linewell.dataelement.bk.infrastructure.SurveillanceControlItemRepository;
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

    @GetMapping("/page")
    public java.util.Map<String,Object> page(
        @RequestParam(defaultValue=ENGINE) String engineCode,@RequestParam(required=false) String channelCode,
        @RequestParam(required=false) String keyword,@RequestParam(defaultValue="1") int pageNo,
        @RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String sortField,
        @RequestParam(required=false) String sortDir) {
        return java.util.Map.of("code",0,"msg","success","data",repository.page(TenantContext.requireTenantId(),engineCode,channelCode,keyword,pageNo,pageSize,sortField,sortDir));
    }
}
