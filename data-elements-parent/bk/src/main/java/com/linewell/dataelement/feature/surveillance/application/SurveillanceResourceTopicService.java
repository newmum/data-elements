package com.linewell.dataelement.feature.surveillance.application;

import com.linewell.dataelement.feature.surveillance.domain.SurveillanceResourceTopic;
import com.linewell.dataelement.feature.surveillance.infrastructure.SurveillanceResourceTopicRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class SurveillanceResourceTopicService {
    private static final String ENGINE = "structured-surveillance";
    private final SurveillanceResourceTopicRepository repository;
    private final SurveillanceKafkaConnectionService kafkaConnections;

    public SurveillanceResourceTopicService(SurveillanceResourceTopicRepository repository,
                                            SurveillanceKafkaConnectionService kafkaConnections) {
        this.repository = repository;
        this.kafkaConnections = kafkaConnections;
    }

    public List<SurveillanceResourceTopic> list(String tenantId, String engineCode,
                                                 String channelCode, String status) {
        return repository.findAll(tenantId, normalizeEngine(engineCode), channelCode, status);
    }

    public SurveillanceResourceTopic create(String tenantId, ResourceTopicCommand command) {
        String engine = normalizeEngine(command.engineCode());
        validate(command, null);
        String resourceCode = normalizeCode(command.resourceCode(), command.inputTopic());
        String resultTopic = command.resultTopic().trim();
        kafkaConnections.requireExistingTopic(tenantId, engine, SurveillanceKafkaConnectionService.INPUT, command.inputTopic());
        kafkaConnections.requireExistingTopic(tenantId, engine, SurveillanceKafkaConnectionService.OUTPUT, resultTopic);
        try {
            return repository.insert(new SurveillanceResourceTopic(null, tenantId, engine,
                    command.channelCode().trim().toLowerCase(Locale.ROOT), resourceCode,
                    command.sourceTable().trim(), command.inputTopic().trim(), resultTopic,
                    command.identifierType().trim().toUpperCase(Locale.ROOT), command.keyField().trim(),
                    command.schemaJson(), status(command.status()), null, null));
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("资源 Topic、资源编码或结果 Topic 已存在", exception);
        }
    }

    public SurveillanceResourceTopic update(String tenantId, String resourceCode, ResourceTopicCommand command) {
        String engine = normalizeEngine(command.engineCode());
        validate(command, resourceCode);
        String normalizedCode = normalizeCode(resourceCode, command.inputTopic());
        String resultTopic = command.resultTopic().trim();
        SurveillanceResourceTopic existing = repository.find(tenantId, engine, normalizedCode)
                .orElseThrow(() -> new IllegalArgumentException("布控资源 Topic 不存在: " + normalizedCode));
        if (!existing.inputTopic().equals(command.inputTopic().trim())) {
            kafkaConnections.requireExistingTopic(tenantId, engine, SurveillanceKafkaConnectionService.INPUT, command.inputTopic());
        }
        if (!existing.resultTopic().equals(resultTopic)) {
            kafkaConnections.requireExistingTopic(tenantId, engine, SurveillanceKafkaConnectionService.OUTPUT, resultTopic);
        }
        try {
            return repository.update(new SurveillanceResourceTopic(null, tenantId, engine,
                    command.channelCode().trim().toLowerCase(Locale.ROOT), normalizedCode,
                    command.sourceTable().trim(), command.inputTopic().trim(), resultTopic,
                    command.identifierType().trim().toUpperCase(Locale.ROOT), command.keyField().trim(),
                    command.schemaJson(), status(command.status()), null, null));
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("资源 Topic 或结果 Topic 已被其他资源占用", exception);
        }
    }

    public void disable(String tenantId, String engineCode, String resourceCode) {
        repository.softDelete(tenantId, normalizeEngine(engineCode), resourceCode);
    }

    private void validate(ResourceTopicCommand command, String resourceCode) {
        required(command.channelCode(), "channelCode");
        required(command.sourceTable(), "sourceTable");
        required(command.inputTopic(), "inputTopic");
        required(command.resultTopic(), "resultTopic");
        required(command.identifierType(), "identifierType");
        required(command.keyField(), "keyField");
        String channel = command.channelCode().trim().toLowerCase(Locale.ROOT);
        if (!List.of("person", "mobile", "vehicle").contains(channel)) {
            throw new IllegalArgumentException("不支持的布控通道: " + channel);
        }
        if (resourceCode != null && resourceCode.isBlank()) throw new IllegalArgumentException("resourceCode 不能为空");
    }

    private String normalizeEngine(String value) {
        return value == null || value.isBlank() ? ENGINE : value.trim();
    }

    private static String normalizeCode(String value, String fallback) {
        String source = value == null || value.isBlank() ? fallback : value;
        String code = source == null ? "resource" : source.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.isBlank()) return "resource_" + Integer.toUnsignedString(source.hashCode(), 36);
        return code;
    }

    private String status(String value) {
        String status = value == null || value.isBlank() ? "ACTIVE" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "DISABLED").contains(status)) throw new IllegalArgumentException("status 只支持 ACTIVE 或 DISABLED");
        return status;
    }

    private void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
    }

    public record ResourceTopicCommand(String engineCode, String channelCode, String resourceCode,
                                       String sourceTable, String inputTopic, String resultTopic,
                                       String identifierType, String keyField, String schemaJson,
                                       String status) {
    }
}
