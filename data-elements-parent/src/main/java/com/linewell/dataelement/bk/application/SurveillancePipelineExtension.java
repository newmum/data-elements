package com.linewell.dataelement.bk.application;

import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import com.linewell.dataelement.integration.nifi.canvas.lifecycle.PipelineGroupingPolicy;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** NiFi group placement for the optional surveillance engine. */
@Component
public class SurveillancePipelineExtension implements PipelineGroupingPolicy {
    @Override
    public Optional<Group> group(Pipeline pipeline) {
        return pipeline != null && pipeline.name() != null
                && pipeline.name().startsWith("结构化数据布控引擎流组")
                ? Optional.of(new Group("布控引擎组", "structured-surveillance")) : Optional.empty();
    }
}
