package com.linewell.dataelement.platform.audit;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.session.SaSession;
import com.linewell.dataelement.platform.tenant.api.PlatformSessionMagicModule;
import com.linewell.dataelement.platform.tenant.api.AuthAccountSessionMagicModule;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuditIdentityResolver {
    public static final String ATTRIBUTE = AuditIdentityResolver.class.getName();
    private final AuditStore store;
    public AuditIdentityResolver(AuditStore store) { this.store=store; }
    public static HttpServletRequest request() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? a.getRequest() : null;
    }
    public AuditIdentity current() {
        var req=request();
        if (req!=null && req.getAttribute(ATTRIBUTE) instanceof AuditIdentity identity) return identity;
        AuditIdentity identity=null;
        boolean platform=req!=null && req.getRequestURI().startsWith("/idaas/");
        try {
        if (platform && PlatformSessionMagicModule.LOGIC.isLogin()) {
            identity=platform(PlatformSessionMagicModule.LOGIC.getLoginIdAsString(),"OPERATOR",null);
        } else if(platform && AuthAccountSessionMagicModule.LOGIC.isLogin()) {
            identity=platform(AuthAccountSessionMagicModule.LOGIC.getLoginIdAsString(),"AUTH_ACCOUNT",null);
        } else if (!platform && StpUtil.isLogin()) {
            SaSession session=StpUtil.getTokenSession();
            Object tenant=session.get("tenantId");
            if (tenant!=null) identity=tenant(String.valueOf(tenant),StpUtil.getLoginIdAsString());
        }
        } catch(org.springframework.dao.DataAccessException unavailable) {
            // Revocation must remain possible even when profile storage is unavailable.
            org.slf4j.LoggerFactory.getLogger("audit.health").error("audit_actor_snapshot_unavailable type={}",unavailable.getClass().getSimpleName());
            if(platform&&PlatformSessionMagicModule.LOGIC.isLogin())identity=new AuditIdentity(null,"OPERATOR",PlatformSessionMagicModule.LOGIC.getLoginIdAsString(),null,null,null,null,"idaas",ref(PlatformSessionMagicModule.LOGIC.getTokenSession()),"workforce");
            else if(platform&&AuthAccountSessionMagicModule.LOGIC.isLogin())identity=new AuditIdentity(null,"AUTH_ACCOUNT",AuthAccountSessionMagicModule.LOGIC.getLoginIdAsString(),null,null,null,null,"idaas",ref(AuthAccountSessionMagicModule.LOGIC.getTokenSession()),Objects.toString(AuthAccountSessionMagicModule.LOGIC.getTokenSession().get("auditDomain"),"public"));
            else if(!platform&&StpUtil.isLogin()&&StpUtil.getTokenSession().get("tenantId")!=null)identity=new AuditIdentity(String.valueOf(StpUtil.getTokenSession().get("tenantId")),"TENANT_USER",StpUtil.getLoginIdAsString(),null,null,null,null,Objects.toString(StpUtil.getTokenSession().get("appId"),null),ref(StpUtil.getTokenSession()),null);
        }
        if (identity!=null && req!=null) req.setAttribute(ATTRIBUTE,identity);
        return identity;
    }
    public AuditIdentity tenant(String tenantId,String userId) {
        var rows=store.jdbc(tenantId).queryForList("select u.user_name,u.real_name,o.id org_id,o.name org_name from rm_user_t u left join rm_user_org_rela_t r on r.tenant_id=u.tenant_id and r.user_id=u.id left join rm_org_t o on o.tenant_id=r.tenant_id and o.id=r.org_id and o.deleted=0 where u.tenant_id=? and u.id=? and u.deleted=0 order by o.id",tenantId,userId);
        Map<String,Object> row=rows.isEmpty()?Map.of():AuditStore.lower(rows.getFirst());
        SaSession session=StpUtil.isLogin()?StpUtil.getTokenSession():null;
        String app=session==null?null:string(session.get("appId"));
        return new AuditIdentity(tenantId,"TENANT_USER",userId,string(row.get("user_name")),string(row.get("real_name")),string(row.get("org_id")),string(row.get("org_name")),app,ref(session),null);
    }
    public AuditIdentity platform(String id,String kind,String domain) {
        Map<String,Object> row=Map.of();
        if ("OPERATOR".equals(kind) && id!=null) {
            var rows=store.jdbc(null).queryForList("select p.username,p.display_name,o.tid org_id,o.name org_name from iam_operator_profile_v p left join iam_appointment_t a on a.subject_id=p.subject_id and a.is_primary=1 and a.is_del=0 and a.status='ACTIVE' and a.valid_from<=current_timestamp and (a.valid_until is null or a.valid_until>current_timestamp) left join iam_org_t o on o.tid=a.org_id and o.is_del=0 where p.tid=? order by a.tid",id);
            if(!rows.isEmpty()) row=AuditStore.lower(rows.getFirst());
        }
        if("AUTH_ACCOUNT".equals(kind)&&id!=null) {
            var rows=store.jdbc(null).queryForList("select a.username,a.identity_domain,s.name display_name from iam_auth_account_t a left join iam_subject_t s on s.tid=a.subject_id where a.tid=?",id);
            if(!rows.isEmpty()) {row=AuditStore.lower(rows.getFirst());domain=string(row.get("identity_domain"));}
        }
        SaSession session="AUTH_ACCOUNT".equals(kind)?(AuthAccountSessionMagicModule.LOGIC.isLogin()?AuthAccountSessionMagicModule.LOGIC.getTokenSession():null):(PlatformSessionMagicModule.LOGIC.isLogin()?PlatformSessionMagicModule.LOGIC.getTokenSession():null);
        if(session!=null)session.set("auditDomain",domain==null?"workforce":domain);
        return new AuditIdentity(null,kind,id,string(row.get("username")),string(row.get("display_name")),string(row.get("org_id")),string(row.get("org_name")),"idaas",ref(session),domain==null?"workforce":domain);
    }
    public void freeze(AuditIdentity identity) { if(request()!=null) request().setAttribute(ATTRIBUTE,identity); }
    private String ref(SaSession session) {
        if(session==null) return null;
        synchronized(session) {
            String ref=string(session.get("auditSessionRef"));
            if(ref==null) { ref=AuditTrace.random(); session.set("auditSessionRef",ref); }
            return ref;
        }
    }
    private static String string(Object value) { return value==null?null:String.valueOf(value); }
}
