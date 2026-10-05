package com.linewell.dataelement.integration.nifi.canvas.controller;

import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineRepository;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Returns NiFi provenance / lineage data for a single canvas node.
 *
 * <p>Implements §6.2 of the spec:
 * <ol>
 *   <li>Resolve canvas node -> NiFi processor id via {@link Pipeline#nodeMapping()}.</li>
 *   <li>{@code POST /provenance} to grab recent flowfile events for the processor.</li>
 *   <li>For the latest event: {@code POST /provenance/lineage} with the event id.</li>
 *   <li>Poll the lineage entity until {@code finished=true}, return its node/edge graph.</li>
 *   <li>Cleanup the provenance + lineage requests.</li>
 * </ol>
 */
@RestController
@RequestMapping("/nifi/api/pipelines/{id}/nodes/{nodeId}/lineage")
@Api(tags = "NiFi 血缘")
public class LineageController {

    private static final Logger log = LoggerFactory.getLogger(LineageController.class);
    private static final int MAX_POLL = 20;
    private static final long POLL_DELAY_MS = 250L;

    private final PipelineRepository repo;
    private final NifiClient nifi;

    public LineageController(PipelineRepository repo, NifiClient nifi) {
        this.repo = repo;
        this.nifi = nifi;
    }

    @PostMapping
    @Operation(summary = "计算血缘(compute)")
    public ResponseEntity<?> compute(@PathVariable String id, @PathVariable String nodeId,
                                     @RequestParam(defaultValue = "10") int maxEvents) {
        if (maxEvents < 1 || maxEvents > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "事件数量必须为 1–100"));
        }
        Pipeline p = repo.findById(id).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        if (p.nifiProcessGroupId() == null || p.nodeMapping() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "流程尚未部署，无法查询血缘"));
        }
        LinkedHashSet<String> processorIds = new LinkedHashSet<>();
        if (p.nodeMapping().primaryProcessorIds() != null) {
            String primary = p.nodeMapping().primaryProcessorIds().get(nodeId);
            if (primary != null && !primary.isBlank()) processorIds.add(primary);
        }
        if (p.nodeMapping().processorIdToCanvasNode() != null) {
            p.nodeMapping().processorIdToCanvasNode().forEach((processorId, canvasId) -> {
                if (nodeId.equals(canvasId) && processorId != null && !processorId.isBlank()) processorIds.add(processorId);
            });
        }
        if (processorIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "节点未映射到 NiFi 处理器"));
        }

        String provId = null;
        String lineageId = null;
        String clusterNodeId = null;
        try {
            List<JsonNode> events = new ArrayList<>();
            long searchDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            for (String processorId : processorIds) {
                if (System.nanoTime() >= searchDeadline) throw new NifiException("节点事件查询超时，请稍后重试", 504, null);
                JsonNode prov = nifi.submitProcessorProvenance(processorId, maxEvents);
                provId = prov.path("provenance").path("id").asText(null);
                if (provId == null) return ResponseEntity.status(502).body(Map.of("error", "NiFi 未返回 provenance id"));
                JsonNode results = pollProvenance(provId, searchDeadline).path("provenance").path("results");
                rejectQueryErrors(results, "事件");
                for (JsonNode event : results.path("provenanceEvents")) {
                    // Defence in depth against an upstream ignoring the filter.
                    String eventProcessor = event.path("componentId").asText(event.path("processorId").asText(""));
                    if (processorId.equals(eventProcessor)) events.add(event);
                }
                nifi.deleteProvenance(provId);
                provId = null;
                // The primary processor can legitimately emit no event; use
                // another processor materialized by the same owned canvas node.
                if (!events.isEmpty()) break;
            }
            if (events.isEmpty()) {
                return ResponseEntity.ok(Map.of("nodes", java.util.List.of(), "edges", java.util.List.of(),
                        "message", "暂无血缘事件"));
            }
            // Use the most recent event as the lineage anchor.
            JsonNode anchor = events.stream().max(java.util.Comparator.comparingLong(event -> event.path("eventId").asLong(-1))).orElseThrow();
            String eventId = anchor.path("eventId").asText(null);
            if (eventId == null) {
                return ResponseEntity.status(502).body(Map.of("error", "事件缺少 eventId"));
            }
            clusterNodeId = anchor.path("clusterNodeId").asText(null);
            JsonNode lineage = nifi.submitLineage(eventId, clusterNodeId);
            lineageId = lineage.path("lineage").path("id").asText(null);
            if (lineageId == null) {
                return ResponseEntity.status(502).body(Map.of("error", "NiFi 未返回 lineage id"));
            }
            JsonNode complete = pollLineage(lineageId, clusterNodeId);
            JsonNode graph = complete.path("lineage").path("results");
            rejectQueryErrors(graph, "血缘");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("anchorEventId", eventId);
            body.put("nodes", graph.path("nodes"));
            body.put("links", graph.path("links"));
            body.put("events", events.stream().map(LineageController::eventSummary).toList());
            return ResponseEntity.ok(body);
        } catch (NifiException e) {
            log.warn("Lineage for {}/{} failed: {}", id, nodeId, e.getMessage());
            return ResponseEntity.status(e.status() > 0 ? e.status() : 502)
                    .body(Map.of("error", e.getMessage()));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(503).body(Map.of("error", "polling interrupted"));
        } finally {
            if (lineageId != null) nifi.deleteLineage(lineageId, clusterNodeId);
            if (provId != null) nifi.deleteProvenance(provId);
        }
    }

    private static Map<String, Object> eventSummary(JsonNode event) {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (String name : List.of("eventId", "eventTime", "eventType", "componentId", "componentName", "clusterNodeId")) {
            if (event.hasNonNull(name)) summary.put(name, event.get(name));
        }
        return summary; // Never return FlowFile attributes, content pointers or transit URLs.
    }

    private static void rejectQueryErrors(JsonNode results, String name) {
        if (results.path("errors").isArray() && !results.path("errors").isEmpty()) {
            throw new NifiException("NiFi " + name + "查询失败，请检查事件存储及节点权限", 502, null);
        }
    }

    private JsonNode pollProvenance(String provId, long deadline) throws InterruptedException {
        for (int i = 0; i < MAX_POLL; i++) {
            if (System.nanoTime() >= deadline) break;
            JsonNode r = nifi.getProvenance(provId);
            if (r.path("provenance").path("finished").asBoolean(false)) return r;
            Thread.sleep(POLL_DELAY_MS);
        }
        throw new NifiException("Provenance query timed out", 504, null);
    }

    private JsonNode pollLineage(String lineageId, String clusterNodeId) throws InterruptedException {
        for (int i = 0; i < MAX_POLL; i++) {
            JsonNode r = nifi.getLineage(lineageId, clusterNodeId);
            if (r.path("lineage").path("finished").asBoolean(false)) return r;
            Thread.sleep(POLL_DELAY_MS);
        }
        throw new NifiException("Lineage query timed out", 504, null);
    }
}
