package com.linewell.dataelement.platform.audit;

import java.util.*;
import org.springframework.stereotype.Component;

/** Audits actual session revocation, with no per-session identity SQL reads. */
@Component
public class AuditSessionEvents {
    private final AuditIdentityResolver identities;private final AuditRecorder recorder;private final AuditStore store;private final AuditEmergencyBuffer emergency;
    public AuditSessionEvents(AuditIdentityResolver identities,AuditRecorder recorder,AuditStore store,AuditEmergencyBuffer emergency){this.identities=identities;this.recorder=recorder;this.store=store;this.emergency=emergency;}
    public void revoke(String tenant,String kind,String targetId,String targetRef,String domain,Runnable action) {
        AuditIdentity initiator=null;
        try {initiator=identities.current();}catch(RuntimeException unavailable){org.slf4j.LoggerFactory.getLogger("audit.health").warn("session_revoke_actor_snapshot_unavailable");}
        action.run(); // A failed audit must never prevent revocation.
        if(initiator==null||!Objects.equals(tenant,initiator.tenantId()))initiator=new AuditIdentity(tenant,"SYSTEM",null,null,"系统服务",null,null,tenant==null?"idaas":null,null,domain==null?"workforce":domain);
        var event=recorder.prepare(initiator,"AUTH","AUTH_SESSION_REVOKED","SUCCESS",null,targetRef==null?kind:"SESSION",targetRef==null?targetId:targetRef,null,Map.of("id",targetId));
        try{store.insert(tenant,event.table(),event.row());}catch(Exception error){emergency.persist(tenant,event);}
    }
    public void expired(String kind,String id,String domain,String sessionRef,java.time.Instant expiresAt) {
        var actor=new AuditIdentity(null,"SYSTEM",null,null,"系统服务",null,null,"idaas",null,domain==null?"public":domain);
        var event=recorder.prepare(actor,"AUTH","AUTH_SESSION_EXPIRED","SUCCESS",null,kind,id,null,Map.of("phase","OBSERVED_EXPIRY"));
        event.row().put("session_ref",sessionRef);
        // Fact time is the confirmed idle deadline, not the later request that observed it.
        event.row().put("event_time",LogTimes.utc(expiresAt));
        try{store.insert(null,event.table(),event.row());}catch(Exception error){emergency.persist(null,event);}
    }
}
