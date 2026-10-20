package com.linewell.dataelement.feature.surveillance.application;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class SurveillancePipelineExtensionTest {
    private final SurveillancePipelineExtension extension = new SurveillancePipelineExtension();

    @Test
    void preservesThreeChannelTemplateAndMatchEndpoint() {
        assertThat(extension.supports("structured-surveillance")).isTrue();
        assertThat(extension.supports(null)).isFalse();
        var pipeline = extension.template();
        assertThat(pipeline.dsl().nodes()).hasSize(9);
        assertThat(pipeline.dsl().edges()).hasSize(6);
        assertThat(pipeline.dsl().nodes().stream().filter(node -> "transform.surveillance-match".equals(node.manifestKey())))
                .hasSize(3).allSatisfy(node -> assertThat(node.config()).containsEntry("matchUrl", "/api/v1/surveillance/match/batch"));
        assertThat(extension.group(pipeline)).isEmpty();
    }
}
