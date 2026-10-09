package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DslHasherDeploymentHashTest {

    @Test
    void deploymentHashIsStableFixedWidthAndChangesWithCompilerRevision() {
        Pipeline.Dsl dsl = new Pipeline.Dsl(1, List.of(), List.of());

        String first = DslHasher.deploymentHash(dsl, "nifi-sftp-routing-v1");

        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(DslHasher.deploymentHash(dsl, "nifi-sftp-routing-v1")).isEqualTo(first);
        assertThat(DslHasher.deploymentHash(dsl, "nifi-sftp-routing-v2")).isNotEqualTo(first);
    }

    @Test
    void hiveHdfsCompilerChangeLeavesOtherDeployedFlowsOnTheirOriginalHash() throws Exception {
        Pipeline.Dsl jdbc = singleSink("sink.jdbc", Map.of("table", "EVENT_LOG"));
        Pipeline.Dsl hiveRecordPut = singleSink("sink.hive", Map.of("hiveWriteMode", "HIVE_RECORD_PUT"));
        Pipeline.Dsl hiveHdfs = singleSink("sink.hive", Map.of("hiveWriteMode", "LINEWELL_HDFS"));
        Pipeline.Dsl nativeHdfs = singleSink("sink.hive", Map.of("hiveWriteMode", "HDFS_BATCH"));
        String revision = DslHasher.CURRENT_COMPILER_REVISION;

        assertThat(DslHasher.deploymentHash(jdbc, revision)).isEqualTo(previousDeploymentHash(jdbc, revision));
        assertThat(DslHasher.deploymentHash(hiveRecordPut, revision))
                .isEqualTo(previousDeploymentHash(hiveRecordPut, revision));
        assertThat(DslHasher.deploymentHash(hiveHdfs, revision))
                .isNotEqualTo(previousDeploymentHash(hiveHdfs, revision));
        assertThat(DslHasher.deploymentHash(nativeHdfs, revision))
                .isNotEqualTo(previousDeploymentHash(nativeHdfs, revision));
    }

    private static Pipeline.Dsl singleSink(String manifestKey, Map<String, Object> config) {
        return new Pipeline.Dsl(1,
                List.of(new Pipeline.Node("sink", manifestKey, "Target", "sink", 0, 0, config)), List.of());
    }

    private static String previousDeploymentHash(Pipeline.Dsl dsl, String revision) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((DslHasher.hash(dsl) + "|" + revision).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
