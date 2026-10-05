package com.linewell.dataelement.integration.nifi.canvas.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
}
