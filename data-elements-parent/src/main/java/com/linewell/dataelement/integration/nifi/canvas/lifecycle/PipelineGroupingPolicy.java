package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline;
import java.util.Optional;

/** An optional feature can group its flows under one application container. */
public interface PipelineGroupingPolicy {
    Optional<Group> group(Pipeline pipeline);
    record Group(String name, String identity) {}
}
