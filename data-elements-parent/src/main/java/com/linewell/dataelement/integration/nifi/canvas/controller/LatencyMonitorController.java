package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessAggTaskT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.entity.DataAccessTaskMonitorSnapT;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessAggTaskTService;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.monitor.LatencyMonitorSupport;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.linewell.dataelement.feature.dataaccess.infrastructure.persistence.service.IDataAccessTaskMonitorSnapTService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/nifi/api/monitor")
@Api(tags = "NiFi 延时监控")
public class LatencyMonitorController {

    private final IDataAccessAggTaskTService taskService;
    private final IDataAccessTaskMonitorSnapTService snapService;
    private final PipelineRepository pipelineRepository;
    private final NifiClient nifiClient;
    private final LatencyMonitorSupport monitorSupport;

    public LatencyMonitorController(IDataAccessAggTaskTService taskService,
                                    IDataAccessTaskMonitorSnapTService snapService,
                                    PipelineRepository pipelineRepository,
                                    NifiClient nifiClient,
                                    LatencyMonitorSupport monitorSupport) {
        this.taskService = taskService;
        this.snapService = snapService;
        this.pipelineRepository = pipelineRepository;
        this.nifiClient = nifiClient;
        this.monitorSupport = monitorSupport;
    }

    @GetMapping("/pipelines/{pipelineId}")
    @ApiOperation(value = "查询任务最新监控状态")
    @Operation(summary = "查询任务最新监控状态(latest)")
    public ResponseEntity<?> latest(@ApiParam(value = "pipeline 配置 ID", required = true)
                                    @PathVariable String pipelineId) {
        DataAccessAggTaskT task = taskService.findByPipelineId(pipelineId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }

    @GetMapping("/pipelines/{pipelineId}/live")
    @Operation(summary = "按需从 Leafy/NiFi 读取流程当前运行状态，不写入监控快照")
    public ResponseEntity<?> live(@PathVariable String pipelineId) {
        Optional<Pipeline> found = pipelineRepository.findById(pipelineId);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String processGroupId = found.get().nifiProcessGroupId();
        if (processGroupId == null || processGroupId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "流程尚未部署"));
        }
        JsonNode status = nifiClient.getProcessGroupStatus(processGroupId);
        return ResponseEntity.ok(Map.of(
                "pipelineId", pipelineId,
                "processGroupId", processGroupId,
                "snapshot", monitorSupport.fromStatus(status)));
    }

    @GetMapping("/tasks/{taskId}/snaps")
    @ApiOperation(value = "查询任务监控历史快照")
    @Operation(summary = "查询任务监控历史快照(snaps)")
    public ResponseEntity<?> snaps(@ApiParam(value = "任务 ID", required = true)
                                   @PathVariable String taskId,
                                   @ApiParam(value = "返回条数", required = false)
                                   @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
        List<DataAccessTaskMonitorSnapT> snaps = snapService.listLatestByTaskId(taskId, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskId", taskId);
        body.put("count", snaps.size());
        body.put("list", snaps);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/pipelines/{pipelineId}/cycles")
    @ApiOperation(value = "查询 pipeline 周期执行信息(基于 NiFi Provenance)")
    @Operation(summary = "查询 pipeline 周期执行信息(cycles)")
    public ResponseEntity<?> cycles(@ApiParam(value = "pipeline 配置 ID", required = true)
                                    @PathVariable String pipelineId,
                                    @ApiParam(value = "最大事件数", required = false)
                                    @RequestParam(value = "maxResults", required = false, defaultValue = "200") int maxResults,
                                    @ApiParam(value = "开始时间(ISO-8601)", required = false)
                                    @RequestParam(value = "fromTime", required = false) String fromTime,
                                    @ApiParam(value = "结束时间(ISO-8601)", required = false)
                                    @RequestParam(value = "toTime", required = false) String toTime) {
        Optional<Pipeline> pipelineOpt = pipelineRepository.findById(pipelineId);
        if (pipelineOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Pipeline pipeline = pipelineOpt.get();
        String pgId = pipeline.nifiProcessGroupId();
        if (pgId == null || pgId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "pipeline 未部署，无法查询执行事件"));
        }

        JsonNode submit = nifiClient.submitProvenance(pgId, Math.max(1, maxResults));
        String provenanceId = submit.path("provenance").path("id").asText(null);
        if (provenanceId == null || provenanceId.isBlank()) {
            return ResponseEntity.status(502).body(Map.of("error", "NiFi provenance 查询创建失败"));
        }

        try {
            JsonNode result = waitProvenanceFinished(provenanceId, 10_000L);
            List<Map<String, Object>> events = extractEvents(result, parseInstant(fromTime), parseInstant(toTime));
            Map<String, Object> summary = buildSummary(events);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("pipelineId", pipelineId);
            body.put("processGroupId", pgId);
            body.put("summary", summary);
            body.put("list", events);
            body.put("count", events.size());
            body.put("finished", result != null && result.path("provenance").path("finished").asBoolean(false));
            return ResponseEntity.ok(body);
        } finally {
            nifiClient.deleteProvenance(provenanceId);
        }
    }

    private JsonNode waitProvenanceFinished(String provenanceId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        JsonNode latest = null;
        while (System.currentTimeMillis() < deadline) {
            latest = nifiClient.getProvenance(provenanceId);
            boolean finished = latest.path("provenance").path("finished").asBoolean(false);
            if (finished) {
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

    private List<Map<String, Object>> extractEvents(JsonNode root, Instant from, Instant to) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (root == null) {
            return list;
        }
        JsonNode events = root.path("provenance").path("results").path("provenanceEvents");
        if (!events.isArray()) {
            return list;
        }
        for (JsonNode event : events) {
            Instant eventTime = parseEventTime(event.path("eventTime").asText(null));
            if (from != null && eventTime != null && eventTime.isBefore(from)) {
                continue;
            }
            if (to != null && eventTime != null && eventTime.isAfter(to)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("eventId", event.path("id").asText(null));
            row.put("eventTime", event.path("eventTime").asText(null));
            row.put("eventType", event.path("eventType").asText(null));
            row.put("componentId", event.path("componentId").asText(null));
            row.put("componentName", event.path("componentName").asText(null));
            row.put("flowFileUuid", event.path("flowFileUuid").asText(null));
            row.put("relationship", event.path("relationship").asText(null));
            row.put("fileSize", event.path("fileSize").asText(null));
            row.put("details", event.path("details").asText(null));
            row.put("transitUri", event.path("transitUri").asText(null));
            list.add(row);
        }
        list.sort(Comparator.comparing(m -> String.valueOf(m.get("eventTime")), Comparator.nullsLast(Comparator.naturalOrder())));
        return list;
    }

    private Map<String, Object> buildSummary(List<Map<String, Object>> events) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("firstEventTime", events.isEmpty() ? null : events.get(0).get("eventTime"));
        summary.put("lastEventTime", events.isEmpty() ? null : events.get(events.size() - 1).get("eventTime"));
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        for (Map<String, Object> event : events) {
            String type = event.get("eventType") == null ? "UNKNOWN" : String.valueOf(event.get("eventType"));
            typeCounts.put(type, typeCounts.getOrDefault(type, 0) + 1);
        }
        summary.put("eventTypeCount", typeCounts);
        summary.put("totalEvents", events.size());
        return summary;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    private Instant parseEventTime(String value) {
        return parseInstant(value);
    }
}
