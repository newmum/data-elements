package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * The user-saved pipeline = canvas DSL + a few metadata fields.
 *
 * <p>Persisted as JSON under {@code data/pipelines/{id}.json}. Unknown fields
 * are ignored on read so older files (without {@code status} / {@code lastDeployedHash})
 * still load cleanly.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Pipeline(
        String id,
        String name,
        String description,
        Long createdAt,
        Long updatedAt,
        Dsl dsl,
        // Filled in by run/stop endpoints; null if never deployed.
        String nifiProcessGroupId,
        // Lifecycle status (defaults to DRAFT for newly created records).
        PipelineStatus status,
        // SHA-256 of the canonicalized DSL at the time of the last successful deploy.
        // Used by /start to decide between fast-restart and full redeploy.
        String lastDeployedHash,
        Long lastDeployedAt,
        Long lastStoppedAt,
        // Mapping between canvas nodes and NiFi components for lineage / bulletin lookup.
        NifiNodeMapping nodeMapping,
        // Last bulletin id processed (so polling only fetches new bulletins).
        Long lastBulletinId
) {
    public Pipeline {
        if (status == null) status = PipelineStatus.DRAFT;
    }

    public record Dsl(int version, List<Node> nodes, List<Edge> edges) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Node(
            String id,
            String manifestKey,
            String label,
            String category,
            double x,
            double y,
            Map<String, Object> config
    ) {}

    public record Edge(String id, String source, String target, String outlet) {}
}
