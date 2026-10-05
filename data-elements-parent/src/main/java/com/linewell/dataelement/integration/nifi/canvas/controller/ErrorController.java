package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.integration.nifi.canvas.errors.ErrorService;
import com.linewell.dataelement.integration.nifi.canvas.errors.FlowError;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Error inspection endpoints. Mounted under both {@code /pipelines} (legacy) and
 * {@code /flows} (new spec) to keep both clients happy.
 */
@RestController
@RequestMapping("/nifi")
@Api(tags = "ErrorController NiFi 错误信息")
public class ErrorController {

    private final ErrorService errors;
    private final PipelineRepository pipelines;
    private final NifiClient nifi;

    public ErrorController(ErrorService errors, PipelineRepository pipelines, NifiClient nifi) {
        this.errors = errors;
        this.pipelines = pipelines;
        this.nifi = nifi;
    }

    @GetMapping({"/api/pipelines/{id}/errors", "/api/flows/{id}/errors"})
    @Operation(summary = "查询错误列表(list)")
    public List<FlowError> list(@PathVariable String id) {
        return errors.list(id);
    }

    @GetMapping({"/api/pipelines/{id}/bulletins/live", "/api/flows/{id}/bulletins/live"})
    @Operation(summary = "按需从 Leafy/NiFi 读取当前公告，不写入本地错误历史")
    public ResponseEntity<?> liveBulletins(@PathVariable String id) {
        Optional<Pipeline> found = pipelines.findById(id);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String processGroupId = found.get().nifiProcessGroupId();
        if (processGroupId == null || processGroupId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "流程尚未部署"));
        }
        JsonNode bulletins = nifi.getBulletinBoard(processGroupId, 0L)
                .path("bulletinBoard").path("bulletins");
        List<Map<String, Object>> result = new ArrayList<>();
        if (bulletins.isArray()) {
            for (JsonNode item : bulletins) {
                JsonNode bulletin = item.path("bulletin");
                String level = bulletin.path("level").asText("");
                if (!"ERROR".equalsIgnoreCase(level) && !"WARNING".equalsIgnoreCase(level)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.path("id").asText(""));
                row.put("level", level);
                row.put("sourceName", bulletin.path("sourceName").asText(""));
                row.put("message", bulletin.path("message").asText(""));
                row.put("timestamp", bulletin.path("timestamp").asText(""));
                result.add(row);
            }
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping({"/api/pipelines/delete/{id}/errors", "/api/flows/delete/{id}/errors"})
    @Operation(summary = "清空错误(clear)")
    public ResponseEntity<?> clear(@PathVariable String id) {
        errors.clear(id);
        return ResponseEntity.noContent().build();
    }
}
