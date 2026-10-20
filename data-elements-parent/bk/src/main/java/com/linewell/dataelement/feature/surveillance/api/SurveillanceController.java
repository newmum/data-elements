package com.linewell.dataelement.feature.surveillance.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.surveillance.application.SurveillanceRuleService;
import com.linewell.dataelement.feature.surveillance.domain.SurveillanceRuleSnapshot;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/surveillance")
public class SurveillanceController {
    private final SurveillanceRuleService service;
    private final ObjectMapper mapper;

    public SurveillanceController(SurveillanceRuleService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping("/match/batch")
    public SurveillanceRuleService.BatchMatchResponse match(
            @RequestBody JsonNode body,
            @RequestHeader(value = "X-Surveillance-Engine", required = false) String engineHeader,
            @RequestHeader(value = "X-Surveillance-Channel", required = false) String channelHeader,
            @RequestHeader(value = "X-Surveillance-Resource", required = false) String resourceHeader,
            @RequestHeader(value = "X-Surveillance-Input-Topic", required = false) String inputTopicHeader,
            @RequestHeader(value = "X-Surveillance-Result-Topic", required = false) String resultTopicHeader,
            @RequestHeader(value = "X-Surveillance-Batch-Id", required = false) String batchIdHeader) {
        try {
            SurveillanceRuleService.BatchMatchRequest request = toRequest(body, engineHeader, channelHeader,
                    resourceHeader, inputTopicHeader, resultTopicHeader, batchIdHeader);
            return service.match(TenantContext.requireTenantId(), request);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    private SurveillanceRuleService.BatchMatchRequest toRequest(JsonNode body, String engineHeader,
                                                                String channelHeader, String resourceHeader,
                                                                String inputTopicHeader, String resultTopicHeader,
                                                                String batchIdHeader) {
        if (body != null && body.isObject() && body.has("events")) {
            try {
                SurveillanceRuleService.BatchMatchRequest request = mapper.treeToValue(body, SurveillanceRuleService.BatchMatchRequest.class);
                return new SurveillanceRuleService.BatchMatchRequest(
                        first(request.engineCode(), engineHeader), first(request.channelCode(), channelHeader),
                        request.events(), request.mappings(), first(request.resourceCode(), resourceHeader),
                        first(request.inputTopic(), inputTopicHeader), first(request.batchId(), batchIdHeader),
                        first(request.resultTopic(), resultTopicHeader));
            } catch (Exception exception) {
                throw new IllegalArgumentException("布控匹配请求格式不正确: " + exception.getMessage(), exception);
            }
        }
        if (body == null || !body.isArray()) {
            throw new IllegalArgumentException("匹配请求必须是 events 数组或对象");
        }
        String engine = engineHeader == null ? "" : engineHeader.trim();
        String channel = channelHeader == null ? "" : channelHeader.trim();
        java.util.List<SurveillanceRuleService.Event> events = new java.util.ArrayList<>();
        int index = 0;
        for (JsonNode item : body) {
            java.util.Map<String, Object> data = mapper.convertValue(item,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
            Object rawId = data.get("eventId");
            if (rawId == null) rawId = data.get("id");
            String eventId = rawId == null || String.valueOf(rawId).isBlank()
                    ? stableEventId(data, index) : String.valueOf(rawId);
            Instant occurredAt = parseInstant(data.get("occurredAt"));
            events.add(new SurveillanceRuleService.Event(eventId, occurredAt, data));
            index++;
        }
        return new SurveillanceRuleService.BatchMatchRequest(engine, channel, events, java.util.List.of(),
                resourceHeader, inputTopicHeader, batchIdHeader, resultTopicHeader);
    }

    private String first(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String stableEventId(java.util.Map<String, Object> data, int index) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsString(data).getBytes(StandardCharsets.UTF_8));
            return "payload-" + java.util.HexFormat.of().formatHex(digest, 0, 12);
        } catch (Exception ignored) {
            return "flowfile-" + index;
        }
    }

    private Instant parseInstant(Object value) {
        if (value == null) return null;
        try { return Instant.parse(String.valueOf(value)); } catch (Exception ignored) { return null; }
    }

    @PutMapping("/rule-snapshots/{engineCode}/{channelCode}")
    public SurveillanceRuleSnapshot putSnapshot(@PathVariable String engineCode,
                                                @PathVariable String channelCode,
                                                @RequestBody SurveillanceRuleSnapshot body) {
        try {
            SurveillanceRuleSnapshot normalized = body.usesNormalizedModel()
                    ? new SurveillanceRuleSnapshot(engineCode, channelCode, body.version(), body.generatedAt(),
                    body.expiresAt(), body.digest(), body.sourceType(), body.sourceRef(), body.status(),
                    body.ruleDefinitions(), body.controlItems())
                    : new SurveillanceRuleSnapshot(engineCode, channelCode, body.version(), body.generatedAt(),
                    body.expiresAt(), body.digest(), body.sourceType(), body.sourceRef(), body.status(), body.rules());
            return service.put(TenantContext.requireTenantId(), normalized);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/rule-snapshots/{engineCode}/{channelCode}")
    public SurveillanceRuleSnapshot snapshot(@PathVariable String engineCode,
                                             @PathVariable String channelCode) {
        SurveillanceRuleSnapshot snapshot = service.current(TenantContext.requireTenantId(), engineCode, channelCode);
        if (snapshot == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "规则快照不存在");
        return snapshot;
    }

    @PostMapping("/rule-snapshots/refresh")
    public SurveillanceRuleSnapshot refresh(@RequestParam String engineCode,
                                            @RequestParam String channelCode,
                                            @RequestParam String sourceType,
                                            @RequestParam String sourceRef) {
        try {
            return service.refresh(TenantContext.requireTenantId(), engineCode, channelCode, sourceType, sourceRef);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getMessage(), exception);
        }
    }
}
