package com.linewell.dataelement.platform.tenant.api;
import java.util.Map;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.context.SaHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
@Component
@MagicModule("authSession")
public class AuthAccountSessionMagicModule {
 public static final StpLogic LOGIC=new StpLogic("idaas-auth-account");
 @Value("${idaas.protocol.secure-cookie:${IDAAS_PROTOCOL_SECURE_COOKIE:true}}") private boolean secureCookie;
 public void login(String id,long version){login(id,version,1800,true);}
 public void login(String id,Number version,Number idleSeconds,Boolean concurrent){login(id,version.longValue(),idleSeconds.intValue(),Boolean.TRUE.equals(concurrent));}
 public void login(String id,long version,int idleSeconds,boolean concurrent){if(idleSeconds<60 || idleSeconds>1800)throw new IllegalArgumentException("认证空闲时间超限");LOGIC.login(id,new SaLoginParameter().setIsShare(false).setIsConcurrent(concurrent).setActiveTimeout(idleSeconds).setDeviceType("idaas-auth").setTimeout(1800));LOGIC.getTokenSession().set("securityVersion",version);cookie(LOGIC.getTokenValue(),1800);}
 public void login(String id,Number version){login(id,version.longValue());}
 public boolean isLogin(){return LOGIC.isLogin();}
 public boolean isLogin(Number idleSeconds){return !principal(LOGIC.getTokenValue(),idleSeconds).isEmpty();}
 public String id(){return LOGIC.getLoginIdAsString();}
 public String token(){return LOGIC.getTokenValue();}
 public SaSession session(){return LOGIC.getTokenSession();}
 public void revoke(String id){LOGIC.kickout(id);}
 public void logout(){LOGIC.logout();cookie("",0);}
 public Map<String,Object> principal(String token){return principal(token,1800);}
 public Map<String,Object> principal(String token,Number idleSeconds){if(token==null || !LOGIC.isValidToken(token))return Map.of();long last=LOGIC.getTokenLastActiveTime(token);if(last>0 && System.currentTimeMillis()-last>idleSeconds.longValue()*1000){LOGIC.logoutByTokenValue(token);return Map.of();}try{LOGIC.checkActiveTimeout(token);}catch(RuntimeException e){return Map.of();}LOGIC.updateLastActiveToNow(token);var session=LOGIC.getTokenSessionByToken(token,false);return session==null?Map.of():Map.of("id",String.valueOf(LOGIC.getLoginIdByTokenNotThinkFreeze(token)),"securityVersion",session.get("securityVersion"));}
 private void cookie(String value,int ttl){SaHolder.getResponse().addHeader("Set-Cookie","IDAAS_AUTH="+value+"; Path=/idaas; HttpOnly; SameSite=Lax; Max-Age="+ttl+(secureCookie?"; Secure":""));}
}
