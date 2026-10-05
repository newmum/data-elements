package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import com.fasterxml.jackson.databind.JsonNode;
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Resolves the effective pipeline state from local lifecycle and NiFi runtime signals. */
@Service
public class PipelineRuntimeStatusResolver {

    private static final Logger log = LoggerFactory.getLogger(PipelineRuntimeStatusResolver.class);

    private final NifiClient nifiClient;

    public PipelineRuntimeStatusResolver(NifiClient nifiClient) {
        this.nifiClient = nifiClient;
    }

    public PipelineStatus resolve(PipelineStatus localStatus, JsonNode rawStatus, String processGroupId) {
        if (rawStatus == null) {
            return localStatus;
        }

        JsonNode snapshot = rawStatus.path("processGroupStatus").path("aggregateSnapshot");
        int activeThreadCount = parseCount(snapshot.path("activeThreadCount"));
        boolean hasStoppedLike = false;
        boolean checkedProcessorState = false;

        if (processGroupId != null && !processGroupId.isBlank()) {
            try {
                JsonNode flow = nifiClient.get("/flow/process-groups/" + processGroupId, JsonNode.class);
                JsonNode processors = flow.path("processGroupFlow").path("flow").path("processors");
                if (processors.isArray() && !processors.isEmpty()) {
                    checkedProcessorState = true;
                    for (JsonNode processor : processors) {
                        String state = processor.path("component").path("state").asText("");
                        if ("RUNNING".equalsIgnoreCase(state) || "STARTING".equalsIgnoreCase(state)) {
                            return PipelineStatus.RUNNING;
                        }
                        if ("STOPPED".equalsIgnoreCase(state)
                                || "DISABLED".equalsIgnoreCase(state)
                                || "INVALID".equalsIgnoreCase(state)) {
                            hasStoppedLike = true;
                        }
                    }
                }
            } catch (Exception exception) {
                log.debug("Resolve runtime status from NiFi flow failed, pgId={}, error={}",
                        processGroupId, exception.getMessage());
            }
        }

        if (activeThreadCount > 0) {
            if (checkedProcessorState && hasStoppedLike
                    && (localStatus == PipelineStatus.STOPPING || localStatus == PipelineStatus.STOPPED)) {
                return localStatus;
            }
            return PipelineStatus.RUNNING;
        }
        if (hasStoppedLike) {
            return PipelineStatus.STOPPED;
        }
        if (localStatus == PipelineStatus.STOPPING || localStatus == PipelineStatus.RUNNING) {
            return PipelineStatus.STOPPED;
        }
        return localStatus;
    }

    static int parseCount(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }
        if (node.isInt() || node.isLong()) {
            return node.asInt(0);
        }
        String text = node.asText("").trim();
        if (text.isBlank()) {
            return 0;
        }
        int splitIndex = text.indexOf('/');
        if (splitIndex > 0) {
            text = text.substring(0, splitIndex).trim();
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
