package com.linewell.dataelement.integration.nifi.canvas.lifecycle;

/** Wire types used by the Magic deployment commands for Jackson coercion and diagnostics. */
public final class NifiDeploymentParameters {
    private NifiDeploymentParameters() {
    }

    public record DeployRequest(String catalogTid, boolean preserveExisting, boolean forceRebuild) {
    }

    public record CandidateRequest(String processGroupId) {
    }
}
