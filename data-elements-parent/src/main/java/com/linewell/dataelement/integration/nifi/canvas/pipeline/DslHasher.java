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
    public static final String CURRENT_COMPILER_REVISION = "nifi-sync-policy-serial-full-v31";
    private static final String DATE_FETCH_COMPILER_REVISION = "direct-date-fetch-source-record-window-v3-"
            + com.linewell.dataelement.integration.nifi.canvas.compile.DateTableFetchPolicy.BUNDLE_VERSION;
    private static final String HIVE_PARTITION_COMPILER_REVISION = "hive-partition-complete-candidate-v1";
    private static final String HIVE_TARGET_COMPILER_REVISION = "hive-global-codecs-all-columns-v3";
    private static final String FIELD_MAPPING_COMPILER_REVISION = "field-mapping-lookup-dialect-utf8-v2";
    private static final String JAVA_MAPPING_COMPILER_REVISION = "java-record-engine-"
            + com.linewell.dataelement.mapping.core.MappingEngine.engineVersion() + "-functions-"
            + com.linewell.dataelement.mapping.core.FunctionRegistry.libraryVersion()
            + "-failure-retry-self-loop-invalid-retained-v2";
    private static final String FILE_TRANSFER_COMPILER_REVISION = "ftp-file-dataset-parser-v2";

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
        if (dsl.nodes() != null && dsl.nodes().stream().anyMatch(node -> node != null && "source".equals(node.category())
                && !List.of("source.api", "source.ftp", "source.sftp", "source.kafka", "source.minio", "source.hdfs",
                    "source.hbase", "source.elasticsearch", "source.iotdb").contains(node.manifestKey()))) {
            effectiveRevision += "|" + DATE_FETCH_COMPILER_REVISION;
        }
        if (dsl.nodes() != null && dsl.nodes().stream().anyMatch(HiveSinkPublicationPolicy::isHiveSink)) effectiveRevision += "|" + HIVE_TARGET_COMPILER_REVISION;
        if (dsl.nodes() != null && dsl.nodes().stream().anyMatch(node -> HiveSinkPublicationPolicy.isHiveSink(node)
                && node.config() != null && node.config().get("partitionPlan") instanceof Map<?, ?> plan
                && Boolean.TRUE.equals(plan.get("enabled")))) effectiveRevision += "|" + HIVE_PARTITION_COMPILER_REVISION;
        if (usesFieldMapping(dsl)) effectiveRevision += "|" + FIELD_MAPPING_COMPILER_REVISION;
        if(usesJavaMapping(dsl))effectiveRevision+="|"+JAVA_MAPPING_COMPILER_REVISION;
        if (dsl.nodes() != null && dsl.nodes().stream().anyMatch(node -> node != null
                && ("source.ftp".equals(node.manifestKey()) || "source.sftp".equals(node.manifestKey())))) {
            effectiveRevision += "|" + FILE_TRANSFER_COMPILER_REVISION;
        }
        try {
            return sha256((baseHash + "|" + effectiveRevision)
                    .getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Deployment hash failed: " + e.getMessage(), e);
        }
    }

    public static boolean usesJavaMapping(Pipeline.Dsl dsl){
        if(dsl==null||dsl.nodes()==null)return false;
        for(var node:HiveSinkPublicationPolicy.forPublication(dsl).nodes())if(node!=null&&node.config()!=null&&node.config().containsKey("mappings")){
            try{Object raw=node.config().get("mappings");var spec=raw instanceof String text?SORTED.readTree(text):SORTED.valueToTree(raw);if(spec.path("schemaVersion").asText().equals("2.0")||spec.path("executionEngine").asText().equals("JAVA_RECORD"))return true;}catch(Exception ignored){}
        }
        return false;
    }

    private static boolean usesFieldMapping(Pipeline.Dsl dsl) {
        if (dsl.nodes() == null) return false;
        return dsl.nodes().stream().anyMatch(node -> node != null
                && ("transform.field-mapping".equals(node.manifestKey())
                    || "transform.field-enrichment".equals(node.manifestKey())));
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
