package com.linewell.dataelement.platform.tenant.api;
import java.time.Instant;
import java.util.Map;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.context.SaHolder;
import com.linewell.dataelement.platform.web.security.GlobalInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.ssssssss.magicapi.core.annotation.MagicModule;
@Component
@MagicModule("authSession")
public class AuthAccountSessionMagicModule {
 private static final String LAST_ACTIVE_AT = "authAccountLastActiveAt";
 @org.springframework.beans.factory.annotation.Autowired(required=false) private com.linewell.dataelement.platform.audit.AuditSessionEvents auditSessionEvents;
 public static final StpLogic LOGIC=new StpLogic("idaas-auth-account");
 @Value("${idaas.protocol.secure-cookie:${IDAAS_PROTOCOL_SECURE_COOKIE:true}}") private boolean secureCookie;
 public void login(String id,long version){login(id,version,1800,true);}
 public void login(String id,Number version,Number idleSeconds,Boolean concurrent){login(id,version.longValue(),idleSeconds.intValue(),Boolean.TRUE.equals(concurrent));}
 public void login(String id,long version,int idleSeconds,boolean concurrent) {
     if (idleSeconds < 60 || idleSeconds > 1800) {
         throw new IllegalArgumentException("认证空闲时间超限");
     }
     LOGIC.login(id,new SaLoginParameter().setIsShare(false).setIsConcurrent(concurrent)
             .setActiveTimeout(idleSeconds).setDeviceType("idaas-auth").setTimeout(1800));
     LOGIC.getTokenSession().set("securityVersion",version).set(LAST_ACTIVE_AT,System.currentTimeMillis());
     cookie(LOGIC.getTokenValue(),1800);
 }
 public void login(String id,Number version){login(id,version.longValue());}
 public boolean isLogin(){return LOGIC.isLogin();}
 public boolean isLogin(Number idleSeconds){return !principal(LOGIC.getTokenValue(),idleSeconds).isEmpty();}
 public Map<?, ?> verifiedContext() {
     var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
     return GlobalInterceptor.verifiedAuthAccountContext(attributes.getRequest());
 }
 public String id(){return LOGIC.getLoginIdAsString();}
 public String token(){return LOGIC.getTokenValue();}
 public SaSession session(){return LOGIC.getTokenSession();}
 public void revoke(String id){if(auditSessionEvents==null)LOGIC.kickout(id);else auditSessionEvents.revoke(null,"AUTH_ACCOUNT",id,null,"public",()->LOGIC.kickout(id));}
 public void logout(){LOGIC.logout();cookie("",0);}
 public Map<String,Object> principal(String token){return principal(token,1800);}
 public Map<String,Object> principal(String token,Number idleSeconds) {
     if (token == null || !LOGIC.isValidToken(token)) {
         return Map.of();
     }
     var session = LOGIC.getTokenSessionByToken(token,false);
     var id = LOGIC.getLoginIdByTokenNotThinkFreeze(token);
     if (session == null || id == null || session.get("securityVersion") == null) {
         return Map.of();
     }
     long now = System.currentTimeMillis();
     Object recorded = session.get(LAST_ACTIVE_AT);
     // Existing tokens can inherit an earlier library timestamp even when the deployment now disables it.
     long last = recorded instanceof Number time ? time.longValue()
             : Math.max(LOGIC.getTokenLastActiveTime(token), session.getCreateTime());
     long expiresAt = last + idleSeconds.longValue() * 1000;
     if (now >= expiresAt) {
         LOGIC.logoutByTokenValue(token);
         if (auditSessionEvents != null) {
             auditSessionEvents.expired("AUTH_ACCOUNT", String.valueOf(id),
                     session.get("auditDomain") == null ? "public" : String.valueOf(session.get("auditDomain")),
                     session.get("auditSessionRef") == null ? null : String.valueOf(session.get("auditSessionRef")),
                     Instant.ofEpochMilli(expiresAt));
         }
         return Map.of();
     }
     try {
         LOGIC.checkActiveTimeout(token);
     } catch (RuntimeException expired) {
         return Map.of();
     }
     LOGIC.updateLastActiveToNow(token);
     session.set(LAST_ACTIVE_AT, Math.max(now, last));
     return Map.of("id",String.valueOf(id),"securityVersion",session.get("securityVersion"));
 }
 private void cookie(String value,int ttl){SaHolder.getResponse().addHeader("Set-Cookie","IDAAS_AUTH="+value+"; Path=/idaas; HttpOnly; SameSite=Lax; Max-Age="+ttl+(secureCookie?"; Secure":""));}
}
