package com.linewell.dataelement.feature.surveillance.api;

import com.linewell.dataelement.feature.surveillance.application.SurveillanceResourceTopicService;
import com.linewell.dataelement.feature.surveillance.domain.SurveillanceResourceTopic;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/surveillance/resource-topics")
public class SurveillanceResourceTopicController {
    private final SurveillanceResourceTopicService service;

    public SurveillanceResourceTopicController(SurveillanceResourceTopicService service) {
        this.service = service;
    }

    @GetMapping
    public List<SurveillanceResourceTopic> list(
            @RequestParam(defaultValue = "structured-surveillance") String engineCode,
            @RequestParam(required = false) String channelCode,
            @RequestParam(required = false) String status) {
        return service.list(TenantContext.requireTenantId(), engineCode, channelCode, status);
    }

    @PostMapping
    public SurveillanceResourceTopic create(@RequestBody SurveillanceResourceTopicService.ResourceTopicCommand command) {
        try {
            return service.create(TenantContext.requireTenantId(), command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @PutMapping("/{resourceCode}")
    public SurveillanceResourceTopic update(@PathVariable String resourceCode,
                                             @RequestBody SurveillanceResourceTopicService.ResourceTopicCommand command) {
        try {
            return service.update(TenantContext.requireTenantId(), resourceCode, command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    @DeleteMapping("/{resourceCode}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable String resourceCode,
                        @RequestParam(defaultValue = "structured-surveillance") String engineCode) {
        service.disable(TenantContext.requireTenantId(), engineCode, resourceCode);
    }
}
