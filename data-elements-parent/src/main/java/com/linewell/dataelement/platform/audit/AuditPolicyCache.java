package com.linewell.dataelement.platform.audit;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Small tenant/domain cache for request sampling only. It never caches an authorization decision. */
@Component
public class AuditPolicyCache {
    private record Cached(long until,double sampleRate) { }
    private final ConcurrentHashMap<String,Cached> values=new ConcurrentHashMap<>();
    private final AuditStore store;
    public AuditPolicyCache(AuditStore store){this.store=store;}
    private String key(String tenant,String domain){return Objects.toString(tenant,"PLATFORM")+":"+Objects.toString(domain,"global");}
    public double sampleRate(AuditIdentity actor) {
        String key=key(actor.tenantId(),actor.platform()?actor.domain():"global");Cached cached=values.get(key);long now=System.nanoTime();
        if(cached!=null&&cached.until()>now)return cached.sampleRate();
        double rate=1;
        try {
            var rows=store.jdbc(actor.tenantId()).queryForList("select sample_rate from log_policy_t where scope_kind=? and scope_id=? and identity_domain=? and category='REQUEST'",actor.platform()?"PLATFORM":"TENANT",actor.platform()?"PLATFORM":actor.tenantId(),actor.platform()?actor.domain():"global");
            if(!rows.isEmpty()) {Object value=AuditStore.lower(rows.getFirst()).get("sample_rate");if(value instanceof Number n&&n.doubleValue()>0&&n.doubleValue()<=1)rate=n.doubleValue();}
        }catch(Exception failure) {org.slf4j.LoggerFactory.getLogger("audit.health").warn("audit_policy_read_failed type={}",failure.getClass().getSimpleName());}
        values.put(key,new Cached(now+30_000_000_000L,rate));return rate;
    }
    public void invalidate(String tenant,String domain){values.remove(key(tenant,domain));}
}
