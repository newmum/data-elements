package com.linewell.dataelement.integration.nifi.canvas.monitor;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessAggTaskT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessTaskMonitorSnapT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessAggTaskTService;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessTaskMonitorSnapTService;
import com.linewell.dataelement.integration.nifi.canvas.config.NifiProperties;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.linewell.dataelement.platform.persistence.id.NumericId;
import com.linewell.dataelement.platform.tenant.application.TenantExecutionCatalog;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "nifi", name = "provenance-sync-enabled", havingValue = "true", matchIfMissing = true)
public class ProvenanceExecutionSyncJob {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceExecutionSyncJob.class);
    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter NIFI_EVENT_TIME_FORMATTER_MS =
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss.SSS z", java.util.Locale.ENGLISH);
    private static final DateTimeFormatter NIFI_EVENT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss z", java.util.Locale.ENGLISH);

    private final PipelineRepository pipelineRepository;
    private final NifiClient nifiClient;
    private final IDataAccessAggTaskTService taskService;
    private final IDataAccessTaskMonitorSnapTService snapService;
    private final NifiProperties nifiProperties;
    private final ObjectMapper objectMapper;
    private final TenantExecutionCatalog tenantCatalog;

    public ProvenanceExecutionSyncJob(PipelineRepository pipelineRepository,
                                      NifiClient nifiClient,
                                      IDataAccessAggTaskTService taskService,
                                      IDataAccessTaskMonitorSnapTService snapService,
                                      NifiProperties nifiProperties,
                                      ObjectMapper objectMapper,
                                      TenantExecutionCatalog tenantCatalog) {
        this.pipelineRepository = pipelineRepository;
        this.nifiClient = nifiClient;
        this.taskService = taskService;
        this.snapService = snapService;
        this.nifiProperties = nifiProperties;
        this.objectMapper = objectMapper;
        this.tenantCatalog = tenantCatalog;
    }

    public void sync() {
        // 执行记录同步由 provenanceSyncEnabled 单独控制；不能随看板延迟监控开关关闭，
        // 否则“最新运行时间 / 结束时间”会永久没有回写。
        if (!Boolean.TRUE.equals(nifiProperties.provenanceSyncEnabled())) {
            log.debug("NiFi execution-record sync is disabled by nifi.provenance-sync-enabled");
            return;
        }
        for (String tenantId : tenantCatalog.activeTenantIds()) {
            try {
                TenantContext.run(tenantId, this::syncTenant);
            } catch (Exception exception) {
                log.warn("Provenance sync failed for tenant {}: {}", tenantId, exception.getMessage());
            }
        }
    }

    /** Execute in one tenant's routed physical database. */
    private void syncTenant() {
        int pageNo = 1;
        int pageSize = nifiProperties.monitorBatchSize();
        while (true) {
            // 同步已部署流程的最近执行历史；停止的流程也需要显示最后一次运行和结束时间。
            // syncOne 会先校验是否有关联任务，因此不会为无任务流程访问 NiFi。
            List<Pipeline> page = pipelineRepository.findMonitorablePage(pageNo, pageSize);
            if (page.isEmpty()) {
                break;
            }
            for (Pipeline pipeline : page) {
                if (pipeline.nifiProcessGroupId() == null || pipeline.nifiProcessGroupId().isBlank()) {
                    continue;
                }
                try {
                    syncOne(pipeline);
                } catch (Exception e) {
                    log.warn("Provenance sync failed for pipeline {}: {}", pipeline.id(), e.getMessage());
                }
            }
            if (page.size() < pageSize) {
                break;
            }
            pageNo++;
        }
    }

    private void syncOne(Pipeline pipeline) {
        DataAccessAggTaskT task = taskService.findByPipelineId(pipeline.id());
        if (task == null) {
            return;
        }

        // 1）按 process-group 提交 provenance 查询。
        JsonNode submit = nifiClient.submitProvenance(pipeline.nifiProcessGroupId(), 200);
        String provenanceId = text(submit.path("provenance").path("id"));
        if (provenanceId == null) {
            return;
        }
        // 2）轮询等待查询完成并拉取事件结果。
        JsonNode result = waitProvenanceFinished(provenanceId, 10_000L);
        List<JsonNode> events = extractEvents(result);

        LocalDateTime now = LocalDateTime.now();
        Instant strongLatestInstant = findStrongLatestRunningInstant(pipeline, events);
        LocalDateTime latestEventTime = toLocalDateTime(strongLatestInstant);
        String latestType = latestEventType(events);
        // 当前为轻量错误判定，后续可按业务规则扩展更严格映射。
        boolean hasErrorLikeEvent = hasErrorLikeEvent(events);
        // 对已停止、部署失败或异常的流程，最后一条 provenance 事件就是本轮执行的结束时间。
        // 运行中的流程仍只在 NiFi 事件明确结束时写入结束时间。
        boolean ended = pipeline.status() != PipelineStatus.RUNNING || isEndedByLatestType(latestType);

        String lastRunning = latestEventTime == null ? null : DT_FORMATTER.format(latestEventTime);
        String lastStatusValue = buildLastStatusValue(events, latestType, hasErrorLikeEvent);
        String monitorMsg = "provenanceSync " + lastStatusValue;
        String endRunning = ended
                ? (latestEventTime == null ? DT_FORMATTER.format(now) : DT_FORMATTER.format(latestEventTime))
                : null;

        // 3）回写任务主表的最近运行信息摘要。
        LambdaUpdateWrapper<DataAccessAggTaskT> updateWrapper = new LambdaUpdateWrapper<DataAccessAggTaskT>()
                .eq(DataAccessAggTaskT::getTid, task.getTid())
                .eq(DataAccessAggTaskT::getIsDel, 0)
                .set(DataAccessAggTaskT::getLastRunning, lastRunning)
                .set(DataAccessAggTaskT::getLastStatusValue, lastStatusValue)
                .set(DataAccessAggTaskT::getScheduleRunning, DT_FORMATTER.format(now));
        if (endRunning != null && !endRunning.isBlank()) {
            updateWrapper.set(DataAccessAggTaskT::getEndRunning, endRunning);
        }
        // A DROP/EXPIRE means a FlowFile did not reach its downstream terminal
        // processor.  Do not leave the access task displayed as normal merely
        // because the start request itself was accepted by NiFi.
        if (hasErrorLikeEvent) {
            updateWrapper.set(DataAccessAggTaskT::getTaskStatus, 2);
        }
        taskService.update(updateWrapper);

        // 4）写入一条监控快照，便于时间线追踪与审计。
        DataAccessTaskMonitorSnapT snap = new DataAccessTaskMonitorSnapT()
                .setTid(NumericId.nextId())
                .setTaskId(task.getTid())
                .setPipelineId(pipeline.id())
                .setProcessGroupId(pipeline.nifiProcessGroupId())
                .setMonitorTime(now)
                .setDelayLevel(hasErrorLikeEvent ? "ERROR" : "NORMAL")
                .setMonitorStatus(hasErrorLikeEvent ? "ERROR" : "SUCCESS")
                .setMonitorMsg(monitorMsg)
                .setCreatedTime(now)
                .setIsDel(0);
        snapService.save(snap);

        // 5）清理 NiFi 侧临时 provenance 查询资源（尽力而为）。
        nifiClient.deleteProvenance(provenanceId);
    }

    /**
     * 强一致“最新运行时间”判定：
     * 1. PG provenance 事件时间（本次查询结果）
     * 2. 关键处理器 provenance 事件时间（按 nodeMapping 主处理器逐个查）
     * 3. PG 状态时间（statsLastRefreshed / statsLastRefreshedAt 等）
     * 4. Bulletin 时间
     * 取四类时间中的最大值作为 lastRunning。
     */
    private Instant findStrongLatestRunningInstant(Pipeline pipeline, List<JsonNode> pgEvents) {
        List<Instant> candidates = new ArrayList<>();

        // PG provenance（本次已查）
        Instant pgLatest = findLatestEventTime(pgEvents);
        if (pgLatest != null) {
            candidates.add(pgLatest);
        }

        // 关键处理器 provenance（提高一致性）
        Set<String> processorIds = collectPrimaryProcessorIds(pipeline);
        for (String processorId : processorIds) {
            try {
                JsonNode submit = nifiClient.submitProvenance(processorId, 100);
                String pid = text(submit.path("provenance").path("id"));
                if (pid == null) {
                    continue;
                }
                try {
                    JsonNode one = waitProvenanceFinished(pid, 5_000L);
                    Instant t = findLatestEventTime(extractEvents(one));
                    if (t != null) {
                        candidates.add(t);
                    }
                } finally {
                    nifiClient.deleteProvenance(pid);
                }
            } catch (Exception e) {
                log.debug("query processor provenance failed, processorId={}, err={}", processorId, e.getMessage());
            }
        }

        // PG status 时间
        try {
            JsonNode status = nifiClient.getProcessGroupStatus(pipeline.nifiProcessGroupId());
            Instant statusTime = parseStatusInstant(status);
            if (statusTime != null) {
                candidates.add(statusTime);
            }
        } catch (Exception e) {
            log.debug("query process-group status failed, pgId={}, err={}", pipeline.nifiProcessGroupId(), e.getMessage());
        }

        // Bulletin 时间
        try {
            JsonNode board = nifiClient.getBulletinBoard(pipeline.nifiProcessGroupId(), 0L);
            Instant bulletinLatest = parseLatestBulletinInstant(board);
            if (bulletinLatest != null) {
                candidates.add(bulletinLatest);
            }
        } catch (Exception e) {
            log.debug("query bulletin failed, pgId={}, err={}", pipeline.nifiProcessGroupId(), e.getMessage());
        }

        return candidates.stream().max(Comparator.naturalOrder()).orElse(null);
    }

    private JsonNode waitProvenanceFinished(String provenanceId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        JsonNode latest = null;
        while (System.currentTimeMillis() < deadline) {
            latest = nifiClient.getProvenance(provenanceId);
            if (latest.path("provenance").path("finished").asBoolean(false)) {
                return latest;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return latest;
            }
        }
        return latest;
    }

    private List<JsonNode> extractEvents(JsonNode root) {
        List<JsonNode> list = new ArrayList<>();
        if (root == null) {
            return list;
        }
        JsonNode events = root.path("provenance").path("results").path("provenanceEvents");
        if (events.isArray()) {
            events.forEach(list::add);
        }
        // 统一按事件时间倒序（最新在前），并只保留最新 200 条。
        list.sort((a, b) -> {
            Instant ta = parseEventInstant(a);
            Instant tb = parseEventInstant(b);
            if (ta == null && tb == null) {
                return 0;
            }
            if (ta == null) {
                return 1;
            }
            if (tb == null) {
                return -1;
            }
            return tb.compareTo(ta);
        });
        if (list.size() > 200) {
            return new ArrayList<>(list.subList(0, 200));
        }
        return list;
    }

    private Instant findLatestEventTime(List<JsonNode> events) {
        return events.stream()
                .map(this::parseEventInstant)
                .filter(i -> i != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private String latestEventType(List<JsonNode> events) {
        Map<Instant, String> byTime = new LinkedHashMap<>();
        for (JsonNode event : events) {
            Instant t = parseInstant(text(event.path("eventTime")));
            if (t == null) {
                continue;
            }
            byTime.put(t, text(event.path("eventType")));
        }
        return byTime.entrySet().stream()
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    private boolean hasErrorLikeEvent(List<JsonNode> events) {
        for (JsonNode event : events) {
            String type = text(event.path("eventType"));
            if (type == null) {
                continue;
            }
            String t = type.trim().toUpperCase();
            if ("DROP".equals(t) || "EXPIRE".equals(t)) {
                return true;
            }
        }
        return false;
    }

    private String buildLastStatusValue(List<JsonNode> events, String latestType, boolean hasErrorLikeEvent) {
        // 结构化 JSON：便于前端解析、扩展字段与后续统计。
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("events", events.size());
        status.put("latestType", latestType == null ? "UNKNOWN" : latestType);
        status.put("errorLike", hasErrorLikeEvent);
        status.put("syncTime", DT_FORMATTER.format(LocalDateTime.now()));
        try {
            return objectMapper.writeValueAsString(status);
        } catch (Exception e) {
            return "{\"events\":" + events.size()
                    + ",\"latestType\":\"" + (latestType == null ? "UNKNOWN" : latestType)
                    + "\",\"errorLike\":" + hasErrorLikeEvent + "}";
        }
    }

    private boolean isEndedByLatestType(String latestType) {
        if (latestType == null || latestType.isBlank()) {
            return false;
        }
        String t = latestType.trim().toUpperCase();
        return "DROP".equals(t) || "EXPIRE".equals(t) || "TERMINATE".equals(t);
    }

    private Set<String> collectPrimaryProcessorIds(Pipeline pipeline) {
        Set<String> set = new HashSet<>();
        NifiNodeMapping mapping = pipeline.nodeMapping();
        if (mapping == null || mapping.primaryProcessorIds() == null) {
            return set;
        }
        for (String id : mapping.primaryProcessorIds().values()) {
            if (id != null && !id.isBlank()) {
                set.add(id);
            }
        }
        return set;
    }

    private Instant parseStatusInstant(JsonNode statusRoot) {
        if (statusRoot == null) {
            return null;
        }
        JsonNode snapshot = statusRoot.path("processGroupStatus").path("aggregateSnapshot");
        String[] fields = new String[]{
                "statsLastRefreshed",
                "statsLastRefreshedAt",
                "lastRefreshed",
                "timestamp"
        };
        for (String field : fields) {
            Instant t = parseInstant(text(snapshot.path(field)));
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    private Instant parseLatestBulletinInstant(JsonNode bulletinRoot) {
        if (bulletinRoot == null) {
            return null;
        }
        JsonNode bulletins = bulletinRoot.path("bulletinBoard").path("bulletins");
        if (!bulletins.isArray()) {
            return null;
        }
        Instant latest = null;
        for (JsonNode b : bulletins) {
            JsonNode one = b.path("bulletin");
            Instant t = parseInstant(text(one.path("timestamp")));
            if (t == null) {
                t = parseInstant(text(one.path("bulletinTimestamp")));
            }
            if (t != null && (latest == null || t.isAfter(latest))) {
                latest = t;
            }
        }
        return latest;
    }

    private Instant parseEventInstant(JsonNode event) {
        if (event == null) {
            return null;
        }
        // 优先标准时间戳字段，避免展示时间字段引入时区偏差。
        Instant ts = parseInstant(text(event.path("eventTimestamp")));
        if (ts != null) {
            return ts;
        }
        return parseInstant(text(event.path("eventTime")));
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // 1) NiFi event_data 常见格式: 05/21/2026 07:50:03.645 UTC
        try {
            return ZonedDateTime.parse(value, NIFI_EVENT_TIME_FORMATTER_MS).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        // 2) NiFi 兼容格式(无毫秒): 05/21/2026 07:50:03 UTC
        try {
            return ZonedDateTime.parse(value, NIFI_EVENT_TIME_FORMATTER).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        // 3) ISO-8601: 2026-05-21T07:50:03.645Z
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // 4) 本地时间字符串（无时区）兜底按系统时区解析
            try {
                return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
