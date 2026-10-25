package com.linewell.dataelement.feature.logmanagement;

import java.util.List;

/** Trusted permission decision. Never deserialize this type directly from an HTTP request. */
public record LogScope(String tenantId,String actorId,String sourceApp,String domain,String appId,
                       String mode,List<String> orgIds,String capability) {
    public boolean platform() { return tenantId==null; }
    public String scopeKind() { return platform()?"PLATFORM":"TENANT"; }
    public String scopeId() { return platform()?"PLATFORM":tenantId; }
}
