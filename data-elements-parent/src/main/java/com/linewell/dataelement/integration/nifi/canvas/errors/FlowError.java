package com.linewell.dataelement.integration.nifi.canvas.errors;

import java.time.Instant;

/**
 * Unified error record for all error sources in a flow lifecycle.
 *
 * <p>Errors are grouped by phase:
 * <ul>
 *   <li>VALIDATION  - DSL static validation (before deployment)</li>
 *   <li>DEPLOYMENT  - calls to NiFi during start/deploy</li>
 *   <li>RUNTIME     - bulletins emitted by NiFi while the flow runs</li>
 * </ul>
 */
public record FlowError(
        String id,
        ErrorLevel level,
        ErrorPhase phase,
        String nodeId,
        String nodeLabel,
        String fieldKey,
        String message,
        String detail,
        String suggestion,
        Instant occurredAt
) {
    public enum ErrorLevel { ERROR, WARNING }

    public enum ErrorPhase { VALIDATION, DEPLOYMENT, RUNTIME }
}
