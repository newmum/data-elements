package com.linewell.dataelement.platform.audit;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deployment-installed allowlist destination. Implementations deliver one bounded batch, never arbitrary URLs. */
public interface AuditDestination {
    String code();
    Set<String> deliver(List<Map<String,Object>> events) throws Exception;
}
