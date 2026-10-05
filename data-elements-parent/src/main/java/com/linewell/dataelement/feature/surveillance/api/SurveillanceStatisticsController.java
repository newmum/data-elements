package com.linewell.dataelement.feature.surveillance.api;

import com.linewell.dataelement.feature.surveillance.application.SurveillanceStatisticsService;
import com.linewell.dataelement.feature.surveillance.domain.SurveillanceTopicDailyStat;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/surveillance/statistics")
public class SurveillanceStatisticsController {
    private final SurveillanceStatisticsService service;

    public SurveillanceStatisticsController(SurveillanceStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/daily")
    public List<SurveillanceTopicDailyStat> daily(
            @RequestParam(defaultValue = "structured-surveillance") String engineCode,
            @RequestParam(required = false) String channelCode,
            @RequestParam(required = false) String resourceCode,
            @RequestParam(required = false) String statDate) {
        LocalDate date = statDate == null || statDate.isBlank() ? null : LocalDate.parse(statDate);
        return service.query(TenantContext.requireTenantId(), engineCode, channelCode, resourceCode, date);
    }

    @PostMapping("/failure")
    public void failure(@RequestBody FailureMetricCommand command) {
        service.recordFailureBatch(TenantContext.requireTenantId(),
                command.engineCode() == null ? "structured-surveillance" : command.engineCode(),
                command.channelCode(), command.resourceCode(), command.inputTopic(), command.resultTopic(),
                command.batchId(), command.statDate(), command.inputCount(), command.failureCount());
    }

    public record FailureMetricCommand(String engineCode, String channelCode, String resourceCode,
                                       String inputTopic, String resultTopic, String batchId,
                                       LocalDate statDate, long inputCount, long failureCount) {
    }
}
