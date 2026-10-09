package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Computes a stable SHA-256 hash of a {@link Pipeline.Dsl}.
 *
 * <p>Stable means: independent of the in-memory order of nodes / edges /
 * config map keys. Used to decide between fast-restart and full redeploy.
 */
public final class DslHasher {

    /** Current NiFi compiler contract; shared by deployment and safe historical lineage recovery. */
    public static final String CURRENT_COMPILER_REVISION = "nifi-execsqlrecord-avro-logical-types-v30";
    private static final String HIVE_HDFS_COMPILER_REVISION = "hive-hdfs-record-chain-v1";

    private static final ObjectMapper SORTED = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private DslHasher() {}

    public static String hash(Pipeline.Dsl dsl) {
        if (dsl == null) return null;
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("version", dsl.version());
        canonical.put("nodes", canonicalNodes(dsl.nodes()));
        canonical.put("edges", canonicalEdges(dsl.edges()));
        try {
            byte[] json = SORTED.writeValueAsBytes(canonical);
            return sha256(json);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("DSL hash failed: " + e.getMessage(), e);
        }
    }

    /**
     * Produces a fixed-width deployment fingerprint for a DSL/compiler pair.
     * Database storage reserves 64 characters for a SHA-256 hex digest, so the
     * compiler revision must be hashed with the DSL hash rather than appended.
     */
    public static String deploymentHash(Pipeline.Dsl dsl, String compilerRevision) {
        String baseHash = hash(dsl);
        if (baseHash == null) return null;
        String effectiveRevision = compilerRevision == null ? "" : compilerRevision;
        if (usesHiveHdfsSink(dsl)) effectiveRevision += "|" + HIVE_HDFS_COMPILER_REVISION;
        try {
            return sha256((baseHash + "|" + effectiveRevision)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Deployment hash failed: " + e.getMessage(), e);
        }
    }

    private static boolean usesHiveHdfsSink(Pipeline.Dsl dsl) {
        if (dsl.nodes() == null) return false;
        for (Pipeline.Node node : dsl.nodes()) {
            if (node == null || !"sink.hive".equals(node.manifestKey())) continue;
            Object configured = node.config() == null ? null : node.config().get("hiveWriteMode");
            String mode = configured == null ? "" : configured.toString().trim().toUpperCase(Locale.ROOT);
            if (mode.isEmpty() || "LINEWELL_HDFS".equals(mode) || "LINEWELL".equals(mode)
                    || "HDFS_BATCH".equals(mode) || "BATCH".equals(mode)) return true;
        }
        return false;
    }

    private static String sha256(byte[] input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(input));
    }

    private static List<Map<String, Object>> canonicalNodes(List<Pipeline.Node> nodes) {
        return nodes.stream()
                .sorted(Comparator.comparing(Pipeline.Node::id))
                .map(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", n.id());
                    m.put("manifestKey", n.manifestKey());
                    m.put("label", n.label());
                    m.put("category", n.category());
                    // Position deliberately excluded — moving a node should NOT trigger redeploy.
                    m.put("config", n.config() == null ? Map.of() : new TreeMap<>(n.config()));
                    return m;
                })
                .toList();
    }

    private static List<Map<String, Object>> canonicalEdges(List<Pipeline.Edge> edges) {
        return edges.stream()
                .sorted(Comparator
                        .comparing(Pipeline.Edge::source)
                        .thenComparing(Pipeline.Edge::target)
                        .thenComparing(e -> e.outlet() == null ? "" : e.outlet()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("source", e.source());
                    m.put("target", e.target());
                    m.put("outlet", e.outlet());
                    return m;
                })
                .toList();
    }
}
