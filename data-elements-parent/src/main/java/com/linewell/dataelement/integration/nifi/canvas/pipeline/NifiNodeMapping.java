package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

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
        Map<String, EdgeEndpoint> edgeProcessorEndpoints // canvasEdgeId -> NiFi connection + source/target processor ids
) {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record EdgeEndpoint(String sourceProcessorId, String targetProcessorId, String connectionId) {}
}
