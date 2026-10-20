package com.linewell.dataelement.feature.surveillance.api;

import com.linewell.dataelement.feature.surveillance.application.SurveillanceKafkaConnectionService;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/surveillance/kafka-connection")
public class SurveillanceKafkaConnectionController {
    private final SurveillanceKafkaConnectionService service;

    public SurveillanceKafkaConnectionController(SurveillanceKafkaConnectionService service) {
        this.service = service;
    }

    @GetMapping
    public SurveillanceKafkaConnectionService.Connection get(
            @RequestParam(defaultValue = "structured-surveillance") String engineCode,
            @RequestParam(defaultValue = "INPUT") String role) {
        return run(() -> service.get(TenantContext.requireTenantId(), engineCode, role));
    }

    @PutMapping
    public SurveillanceKafkaConnectionService.Connection save(
            @RequestParam(defaultValue = "INPUT") String role,
            @RequestBody SurveillanceKafkaConnectionService.ConnectionCommand command) {
        try {
            return service.save(TenantContext.requireTenantId(), role, command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/test")
    public SurveillanceKafkaConnectionService.Connection test(
            @RequestParam(defaultValue = "structured-surveillance") String engineCode,
            @RequestParam(defaultValue = "INPUT") String role) {
        return run(() -> service.test(TenantContext.requireTenantId(), engineCode, role));
    }

    @GetMapping("/topics")
    public List<String> topics(@RequestParam(defaultValue = "structured-surveillance") String engineCode,
                               @RequestParam(defaultValue = "INPUT") String role) {
        return run(() -> service.topics(TenantContext.requireTenantId(), engineCode, role));
    }

    @PostMapping("/topics")
    public SurveillanceKafkaConnectionService.TopicCreation createTopic(
            @RequestParam(defaultValue = "structured-surveillance") String engineCode,
            @RequestParam(defaultValue = "OUTPUT") String role,
            @RequestBody TopicCommand command) {
        try {
            return service.createTopic(TenantContext.requireTenantId(), engineCode, role, command.topicName());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private <T> T run(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }

    public record TopicCommand(String topicName) {}
}
