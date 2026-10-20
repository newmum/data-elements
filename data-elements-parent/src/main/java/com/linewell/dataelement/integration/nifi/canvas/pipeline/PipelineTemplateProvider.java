package com.linewell.dataelement.integration.nifi.canvas.pipeline;

/** Optional feature templates; the canvas itself does not depend on feature implementations. */
public interface PipelineTemplateProvider {
    boolean supports(String templateType);
    Pipeline template();
}
