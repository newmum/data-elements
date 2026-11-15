package com.linewell.dataelement.bk.application;

import static org.assertj.core.api.Assertions.assertThat;
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import org.junit.jupiter.api.Test;

class SurveillancePipelineExtensionTest {
    private final SurveillancePipelineExtension extension = new SurveillancePipelineExtension();

    @Test
    void onlySavedGroupNamesSelectSurveillancePlacement() {
        var separate = new Pipeline(null, "结构化数据布控引擎", null, null, null, null,
                null, null, null, null, null, null, null);
        assertThat(extension.group(separate)).isEmpty();
        var grouped = new Pipeline(null, "结构化数据布控引擎流组 - 测试", null, null, null, null,
                null, null, null, null, null, null, null);
        assertThat(extension.group(grouped)).hasValueSatisfying(group -> {
            assertThat(group.name()).isEqualTo("布控引擎组");
            assertThat(group.identity()).isEqualTo("structured-surveillance");
        });
    }
}
