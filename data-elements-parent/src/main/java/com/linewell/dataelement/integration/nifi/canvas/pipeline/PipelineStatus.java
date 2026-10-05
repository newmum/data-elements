package com.linewell.dataelement.integration.nifi.canvas.pipeline;

/**
 * Lifecycle status of a pipeline / canvas. Persisted in the pipeline JSON.
 */
public enum PipelineStatus {
    DRAFT,
    SAVED,
    DEPLOYING,
    RUNNING,
    STOPPING,
    STOPPED,
    DEPLOY_FAILED,
    RUN_ERROR
}
