package com.linewell.dataelement.platform.audit;

import java.util.Map;

/** Frozen trusted actor snapshot; independent sessionRef is not derived from a bearer token. */
public record AuditIdentity(String tenantId, String kind, String id, String account, String name,
                            String orgId, String orgName, String sourceApp, String sessionRef,
                            String domain) {
    public boolean platform() { return tenantId == null; }
    public Map<String,Object> snapshot() {
        var out = new java.util.LinkedHashMap<String,Object>();
        out.put("tenantId",tenantId); out.put("actorKind",kind); out.put("actorId",id);
        out.put("actorAccount",account); out.put("actorName",name); out.put("orgId",orgId);
        out.put("orgName",orgName); out.put("sourceApp",sourceApp); out.put("sessionRef",sessionRef);
        out.put("domain",domain); return out;
    }
}
