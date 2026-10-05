package com.linewell.dataelement.feature.surveillance.application;

import com.linewell.dataelement.feature.surveillance.domain.SurveillanceRuleProvider;
import com.linewell.dataelement.feature.surveillance.domain.SurveillanceRuleSnapshot;
import com.linewell.dataelement.feature.surveillance.infrastructure.SurveillanceRuleSnapshotRepository;
import com.linewell.dataelement.feature.surveillance.infrastructure.SurveillanceControlItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SurveillanceRuleService {
    private final Map<CacheKey, SurveillanceRuleSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, SurveillanceRuleProvider> providers = new LinkedHashMap<>();
    private final SurveillanceRuleSnapshotRepository repository;
    private final SurveillanceControlItemRepository controlItemRepository;
    private final SurveillanceStatisticsService statisticsService;

    public SurveillanceRuleService(List<SurveillanceRuleProvider> providerList) {
        this(providerList, null, null, null);
    }

    public SurveillanceRuleService(List<SurveillanceRuleProvider> providerList,
                                   SurveillanceRuleSnapshotRepository repository) {
        this(providerList, repository, null, null);
    }

    public SurveillanceRuleService(List<SurveillanceRuleProvider> providerList,
                                   SurveillanceRuleSnapshotRepository repository,
                                   SurveillanceControlItemRepository controlItemRepository) {
        this(providerList, repository, controlItemRepository, null);
    }

    @Autowired
    public SurveillanceRuleService(List<SurveillanceRuleProvider> providerList,
                                   SurveillanceRuleSnapshotRepository repository,
                                   SurveillanceControlItemRepository controlItemRepository,
                                   SurveillanceStatisticsService statisticsService) {
        this.repository = repository;
        this.controlItemRepository = controlItemRepository;
        this.statisticsService = statisticsService;
        if (providerList != null) {
            providerList.forEach(provider -> providers.put(provider.type().toUpperCase(Locale.ROOT), provider));
        }
    }

    public SurveillanceRuleSnapshot put(String tenantId, SurveillanceRuleSnapshot snapshot) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("租户不能为空");
        validate(snapshot);
        CacheKey key = new CacheKey(tenantId, snapshot.engineCode(), snapshot.channelCode());
        SurveillanceRuleSnapshot previous = snapshots.get(key);
        if (previous == null && repository != null) {
            previous = repository.findLatest(tenantId, snapshot.engineCode(), snapshot.channelCode()).orElse(null);
        }
        if (previous != null && snapshot.version() < previous.version()) {
            throw new IllegalArgumentException("规则快照版本不能回退");
        }
        snapshots.put(key, snapshot);
        if (repository != null) repository.save(tenantId, snapshot);
        return snapshot;
    }

    public SurveillanceRuleSnapshot current(String tenantId, String engineCode, String channelCode) {
        CacheKey key = new CacheKey(tenantId, engineCode, channelCode);
        SurveillanceRuleSnapshot cached = snapshots.get(key);
        if (cached != null) return cached;
        if (repository == null) return null;
        SurveillanceRuleSnapshot loaded = repository.findLatest(tenantId, engineCode, channelCode).orElse(null);
        if (loaded != null) snapshots.putIfAbsent(key, loaded);
        return loaded;
    }

    public SurveillanceRuleSnapshot refresh(String tenantId, String engineCode, String channelCode,
                                             String sourceType, String sourceRef) {
        SurveillanceRuleProvider provider = providers.get(normalize(sourceType).toUpperCase(Locale.ROOT));
        if (provider == null) throw new IllegalArgumentException("不支持的布控信息源: " + sourceType);
        SurveillanceRuleSnapshot loaded = provider.load(engineCode, channelCode, sourceRef);
        SurveillanceRuleSnapshot channelSnapshot = loaded.channelCode().equalsIgnoreCase("all")
                ? filterChannel(loaded, channelCode)
                : loaded;
        SurveillanceRuleSnapshot normalized = channelSnapshot.usesNormalizedModel()
                ? new SurveillanceRuleSnapshot(
                engineCode, channelCode, channelSnapshot.version(), channelSnapshot.generatedAt(),
                channelSnapshot.expiresAt(), channelSnapshot.digest(), normalize(sourceType).toUpperCase(Locale.ROOT), sourceRef,
                channelSnapshot.status(), channelSnapshot.ruleDefinitions(), channelSnapshot.controlItems())
                : new SurveillanceRuleSnapshot(
                engineCode, channelCode, channelSnapshot.version(), channelSnapshot.generatedAt(),
                channelSnapshot.expiresAt(), channelSnapshot.digest(), normalize(sourceType).toUpperCase(Locale.ROOT), sourceRef,
                channelSnapshot.status(), channelSnapshot.rules());
        return put(tenantId, normalized);
    }

    private SurveillanceRuleSnapshot filterChannel(SurveillanceRuleSnapshot snapshot, String channelCode) {
        String subjectType = switch (channelCode.toLowerCase(Locale.ROOT)) {
            case "person" -> "PERSON";
            case "mobile" -> "MOBILE";
            case "vehicle" -> "VEHICLE";
            default -> null;
        };
        if (subjectType == null) return snapshot;
        List<SurveillanceRuleSnapshot.RuleDefinition> definitions = snapshot.ruleDefinitions().stream()
                .filter(definition -> subjectType.equalsIgnoreCase(definition.subjectType()))
                .toList();
        List<SurveillanceRuleSnapshot.ControlItem> items = snapshot.controlItems().stream()
                .filter(item -> subjectType.equalsIgnoreCase(item.subjectType()))
                .toList();
        return new SurveillanceRuleSnapshot(snapshot.engineCode(), channelCode, snapshot.version(),
                snapshot.generatedAt(), snapshot.expiresAt(), snapshot.digest(), snapshot.sourceType(),
                snapshot.sourceRef(), snapshot.status(), definitions, items);
    }

    public BatchMatchResponse match(String tenantId, BatchMatchRequest request) {
        requireText(request.engineCode(), "engineCode");
        requireText(request.channelCode(), "channelCode");
        if (controlItemRepository != null) {
            BatchMatchResponse response = matchControlItems(tenantId, request);
            recordStatistics(tenantId, request, response);
            return response;
        }
        SurveillanceRuleSnapshot snapshot = current(tenantId, request.engineCode(), request.channelCode());
        if (snapshot == null) throw new IllegalStateException("布控规则快照尚未加载");
        if (snapshot.expiresAt() != null && !Instant.now().isBefore(snapshot.expiresAt())) {
            throw new IllegalStateException("布控规则快照已过期");
        }

        Instant now = Instant.now();
        List<MatchResult> results = new ArrayList<>();
        for (Event event : request.events()) {
            List<MatchHit> matches = snapshot.rules().stream()
                    .filter(rule -> active(rule, now))
                    .filter(rule -> matches(event.data(), rule.match(), request.mappings()))
                    .sorted(Comparator.comparingInt(SurveillanceRuleSnapshot.Rule::priority).reversed())
                    .map(rule -> new MatchHit(rule.controlItemId(), rule.ruleCode(), rule.subjectType(),
                            snapshot.version(), rule.metadata()))
                    .toList();
            results.add(new MatchResult(request.engineCode(), request.channelCode(), snapshot.version(),
                    event.eventId(), event.occurredAt(), event.data(), !matches.isEmpty(), matches,
                    request.resourceCode(), request.inputTopic(), request.batchId()));
        }
        BatchMatchResponse response = new BatchMatchResponse(snapshot.version(), results,
                request.resourceCode(), request.inputTopic(), request.batchId());
        recordStatistics(tenantId, request, response);
        return response;
    }

    private void recordStatistics(String tenantId, BatchMatchRequest request, BatchMatchResponse response) {
        if (statisticsService == null || request.resourceCode() == null || request.resourceCode().isBlank()) return;
        String resultTopic = request.resultTopic() == null || request.resultTopic().isBlank()
                ? request.channelCode() + ".result." + request.resourceCode() : request.resultTopic();
        statisticsService.recordMatchBatch(tenantId, request.engineCode(), request.channelCode(),
                request.resourceCode(), request.inputTopic(), resultTopic, request.batchId(), request.events(), response.results());
    }

    private BatchMatchResponse matchControlItems(String tenantId, BatchMatchRequest request) {
        String channel = request.channelCode().toLowerCase(Locale.ROOT);
        String eventField = SurveillanceIdentifierCodec.eventField(channel);
        Map<String, String> eventIdentifiers = new LinkedHashMap<>();
        for (Event event : request.events()) {
            String fieldPath = request.mappings().stream()
                    .filter(mapping -> eventField.equals(mapping.rule()))
                    .map(FieldMapping::event)
                    .findFirst()
                    .orElse(eventField);
            Object value = readPath(event.data(), fieldPath);
            String identifier = value == null ? "" : String.valueOf(value);
            if (!identifier.isBlank()) eventIdentifiers.put(event.eventId(), identifier);
        }
        Map<String, List<SurveillanceControlItemRepository.ControlItemMatch>> matchesByIdentifier =
                controlItemRepository.findActiveMatches(tenantId, request.engineCode(), channel, Set.copyOf(eventIdentifiers.values()));
        long maxSnapshotVersion = 0;
        List<MatchResult> results = new ArrayList<>();
        for (Event event : request.events()) {
            String identifier = eventIdentifiers.get(event.eventId());
            List<SurveillanceControlItemRepository.ControlItemMatch> rows = identifier == null
                    ? List.of() : matchesByIdentifier.getOrDefault(identifier, List.of());
            List<MatchHit> hits = rows.stream()
                    .map(row -> new MatchHit(row.controlItemId(), row.ruleCode(), row.subjectType(),
                            row.snapshotVersion(), row.attributes()))
                    .toList();
            maxSnapshotVersion = Math.max(maxSnapshotVersion,
                    rows.stream().mapToLong(SurveillanceControlItemRepository.ControlItemMatch::snapshotVersion).max().orElse(0));
            results.add(new MatchResult(request.engineCode(), request.channelCode(),
                    hits.isEmpty() ? 0 : hits.getFirst().snapshotVersion(), event.eventId(),
                    event.occurredAt(), event.data(), !hits.isEmpty(), hits,
                    request.resourceCode(), request.inputTopic(), request.batchId()));
        }
        return new BatchMatchResponse(maxSnapshotVersion, results,
                request.resourceCode(), request.inputTopic(), request.batchId());
    }

    private boolean active(SurveillanceRuleSnapshot.Rule rule, Instant now) {
        return "ACTIVE".equalsIgnoreCase(rule.status())
                && (rule.effectiveFrom() == null || !now.isBefore(rule.effectiveFrom()))
                && (rule.effectiveTo() == null || now.isBefore(rule.effectiveTo()));
    }

    private boolean matches(Map<String, Object> event, Map<String, Object> rule,
                            List<FieldMapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            for (Map.Entry<String, Object> entry : rule.entrySet()) {
                if (!equalValue(event.get(entry.getKey()), entry.getValue())) return false;
            }
            return true;
        }
        return mappings.stream().allMatch(mapping ->
                equalValue(readPath(event, mapping.event()), readPath(rule, mapping.rule())));
    }

    private Object readPath(Map<String, Object> value, String path) {
        if (value == null || path == null || path.isBlank()) return null;
        Object current = value;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(segment);
        }
        return current;
    }

    private boolean equalValue(Object left, Object right) {
        if (left == null || right == null) return left == right;
        return Objects.toString(left).trim().equals(Objects.toString(right).trim());
    }

    private void validate(SurveillanceRuleSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("规则快照不能为空");
        requireText(snapshot.engineCode(), "engineCode");
        requireText(snapshot.channelCode(), "channelCode");
        if (snapshot.version() < 1) throw new IllegalArgumentException("规则快照版本必须大于 0");
        if (!"ACTIVE".equalsIgnoreCase(snapshot.status())) throw new IllegalArgumentException("规则快照状态必须为 ACTIVE");
        if (snapshot.usesNormalizedModel()) {
            Map<String, SurveillanceRuleSnapshot.RuleDefinition> definitions = new LinkedHashMap<>();
            snapshot.ruleDefinitions().forEach(definition -> {
                requireText(definition.ruleCode(), "ruleCode");
                requireText(definition.subjectType(), "subjectType");
                definitions.put(definition.ruleCode(), definition);
            });
            snapshot.controlItems().forEach(item -> {
                requireText(item.controlItemId(), "controlItemId");
                requireText(item.ruleCode(), "controlItem.ruleCode");
                if (!definitions.containsKey(item.ruleCode())) {
                    throw new IllegalArgumentException("布控数据引用了不存在的规则定义: " + item.ruleCode());
                }
            });
        }
        snapshot.rules().forEach(rule -> requireText(rule.ruleId(), "ruleId"));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
    }

    public record BatchMatchRequest(String engineCode, String channelCode, List<Event> events,
                                    List<FieldMapping> mappings, String resourceCode,
                                    String inputTopic, String batchId, String resultTopic) {
        public BatchMatchRequest(String engineCode, String channelCode, List<Event> events,
                                  List<FieldMapping> mappings) {
            this(engineCode, channelCode, events, mappings, null, null, null, null);
        }

        public BatchMatchRequest(String engineCode, String channelCode, List<Event> events,
                                  List<FieldMapping> mappings, String resourceCode,
                                  String inputTopic, String batchId) {
            this(engineCode, channelCode, events, mappings, resourceCode, inputTopic, batchId, null);
        }

        public BatchMatchRequest {
            events = events == null ? List.of() : List.copyOf(events);
            mappings = mappings == null ? List.of() : List.copyOf(mappings);
            if (events.isEmpty()) throw new IllegalArgumentException("events 不能为空");
            if (events.size() > 10000) throw new IllegalArgumentException("单次最多匹配 10000 条事件，请按批拆分");
            mappings.forEach(mapping -> {
                if (mapping == null || mapping.event() == null || mapping.event().isBlank()
                        || mapping.rule() == null || mapping.rule().isBlank()) {
                    throw new IllegalArgumentException("字段映射必须同时提供 event 和 rule");
                }
            });
        }
    }

    public record Event(String eventId, Instant occurredAt, Map<String, Object> data) {
        public Event {
            if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId 不能为空");
            data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
        }
    }

    public record FieldMapping(String event, String rule) {}

    public record MatchResult(String engineCode, String channelCode, long snapshotVersion, String eventId,
                              Instant occurredAt, Map<String, Object> data, boolean matched,
                              List<MatchHit> matches, String resourceCode, String inputTopic,
                              String batchId) {}

    public record MatchHit(String controlItemId, String ruleCode, String subjectType,
                           long snapshotVersion, Map<String, Object> attributes) {}

    public record BatchMatchResponse(long snapshotVersion, List<MatchResult> results,
                                     String resourceCode, String inputTopic, String batchId,
                                     long inputCount, long matchedCount, long unmatchedCount) {
        public BatchMatchResponse(long snapshotVersion, List<MatchResult> results) {
            this(snapshotVersion, results, null, null, null,
                    results == null ? 0 : results.size(),
                    results == null ? 0 : results.stream().filter(MatchResult::matched).count(),
                    results == null ? 0 : results.stream().filter(result -> !result.matched()).count());
        }

        public BatchMatchResponse(long snapshotVersion, List<MatchResult> results,
                                  String resourceCode, String inputTopic, String batchId) {
            this(snapshotVersion, results, resourceCode, inputTopic, batchId,
                    results == null ? 0 : results.size(),
                    results == null ? 0 : results.stream().filter(MatchResult::matched).count(),
                    results == null ? 0 : results.stream().filter(result -> !result.matched()).count());
        }
    }

    private record CacheKey(String tenantId, String engineCode, String channelCode) {}
}
