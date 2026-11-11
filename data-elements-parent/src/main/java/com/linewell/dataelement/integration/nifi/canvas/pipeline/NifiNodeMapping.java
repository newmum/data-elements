package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.Set;

/**
 * Mapping between canvas node ids and the NiFi components they materialize as.
 *
 * <p>Used by lineage queries (canvas-node -> NiFi processor) and bulletin
 * resolution (NiFi processor id -> canvas node id).
 *
 * <p>{@code primaryProcessorIds} is the list of processor ids representing the
 * "head" processor of each canvas node — used as the starting point for
 * provenance/lineage queries.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NifiNodeMapping(
        Map<String, String> primaryProcessorIds,        // canvasNodeId -> NiFi primary processor id
        Map<String, String> processorIdToCanvasNode,    // NiFi processor id -> canvasNodeId  (reverse index)
        Map<String, EdgeEndpoint> edgeProcessorEndpoints,
        Map<String, Map<String, AttributeBaseline>> attributeBaselines,
        Set<String> oneShotSources,
        Boolean cleanupConfirmationRequired,
        Candidate candidate
) {
        public NifiNodeMapping(Map<String, String> primary, Map<String, String> reverse, Map<String, EdgeEndpoint> edges) {
            this(primary, reverse, edges, null, null, null, null);
        }

        public NifiNodeMapping withAttributes(Map<String, Map<String, AttributeBaseline>> baselines) {
            return new NifiNodeMapping(primaryProcessorIds, processorIdToCanvasNode, edgeProcessorEndpoints, baselines, oneShotSources, cleanupConfirmationRequired, candidate);
        }

        public NifiNodeMapping withRuntimePolicy(Set<String> oneShot, boolean cleanupConfirmation) {
        return new NifiNodeMapping(primaryProcessorIds, processorIdToCanvasNode, edgeProcessorEndpoints,
                attributeBaselines, oneShot, cleanupConfirmation, candidate);
    }

    public NifiNodeMapping withEndpoints(Map<String, String> primary, Map<String, String> reverse, Map<String, EdgeEndpoint> edges) {
        return new NifiNodeMapping(primary, reverse, edges, attributeBaselines, oneShotSources, cleanupConfirmationRequired, candidate);
    }

    public NifiNodeMapping withCandidate(Candidate next) {
            return new NifiNodeMapping(primaryProcessorIds, processorIdToCanvasNode, edgeProcessorEndpoints,
                    attributeBaselines, oneShotSources, cleanupConfirmationRequired, next);
        }

        public record AttributeBaseline(String processorId, String designValue, String nativeValue) {}

        public record Candidate(String processGroupId, String deploymentHash, String designHash, NifiNodeMapping mapping,
                                Map<String, String> planVersionIds) {}

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record EdgeEndpoint(String sourceProcessorId, String targetProcessorId, String connectionId) {}
}
