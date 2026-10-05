package com.linewell.dataelement.platform.tenant.protocol;

import java.util.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;
import org.ssssssss.magicapi.core.annotation.MagicModule;

/** Verifies standard JWTs and current Magic authorization for an explicitly integrated API. */
@Component @MagicModule("identityApi")
public class IdentityApiMagicModule {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(IdentityApiMagicModule.class);
 private final ObjectProvider<JwtDecoder> decoders;private final IdentityProtocolBridge bridge;private final org.springframework.data.redis.core.StringRedisTemplate redis;
 public IdentityApiMagicModule(ObjectProvider<JwtDecoder> decoders,IdentityProtocolBridge bridge,org.springframework.data.redis.core.StringRedisTemplate redis){this.decoders=decoders;this.bridge=bridge;this.redis=redis;}
 public String remoteAddress(){var attributes=RequestContextHolder.getRequestAttributes();if(!(attributes instanceof ServletRequestAttributes servlet))throw new IllegalStateException("接口请求上下文缺失");return servlet.getRequest().getRemoteAddr();}
 public boolean matchesAddress(String rule,String address){if(rule==null || !rule.matches("[0-9a-fA-F:.]+(?:/[0-9]{1,3})?"))throw new IllegalArgumentException("请填写IP或CIDR，不接受主机名");return new org.springframework.security.web.util.matcher.IpAddressMatcher(rule).matches(address);}
 public boolean withinRate(String bucket,int limit){if(limit<1 || limit>100000)throw new IllegalArgumentException("请求配额不正确");var script=new org.springframework.data.redis.core.script.DefaultRedisScript<Long>("local n=redis.call('INCR',KEYS[1]);if n==1 then redis.call('EXPIRE',KEYS[1],60) end;return n",Long.class);Long n=redis.execute(script,List.of("idaas:api:rate:"+bucket));return n!=null && n<=limit;}
 public Map<String,Object> authorize(String appId){
  var attributes=RequestContextHolder.getRequestAttributes();if(!(attributes instanceof ServletRequestAttributes servlet))throw new IllegalStateException("接口请求上下文缺失");
  var request=servlet.getRequest();var authorization=request.getHeader("Authorization");
  if(authorization==null || !authorization.startsWith("Bearer "))throw new org.springframework.security.access.AccessDeniedException("请使用应用访问令牌");
  String stage="signature";try{
   String raw=authorization.substring(7);var decoder=decoders.getIfAvailable();if(decoder==null)throw new IllegalStateException("统一身份认证协议未启用");var jwt=decoder.decode(raw);
   stage="audience";if(appId==null || !jwt.getAudience().equals(List.of(appId)))throw new IllegalArgumentException("令牌未限定当前应用");
   stage="session";
   var state=bridge.call("protocol-store",Map.of("operation","find","token",raw,"type","access_token"));
   if(state==null || state.get("state")==null)throw new IllegalArgumentException("访问令牌已撤销或授权失效");
   stage="current-permission";var input=new LinkedHashMap<String,Object>();input.put("operation","api");input.put("clientId",jwt.getClaimAsString("iam_client_id"));input.put("principalName","CLIENT".equals(jwt.getClaimAsString("principal_kind"))?jwt.getClaimAsString("iam_client_id"):jwt.getClaimAsString("auth_account_id"));input.put("machine","CLIENT".equals(jwt.getClaimAsString("principal_kind")));input.put("appId",appId);input.put("path",request.getRequestURI());input.put("method",request.getMethod());
   var result=bridge.call("protocol-access",input);if(result==null || !Boolean.TRUE.equals(result.get("allowed")))throw new IllegalArgumentException("当前主体没有此接口的有效授权");return result;
  }catch(RuntimeException e){LOG.warn("IDAAS_API_REJECT stage={} cause={}",stage,e.getClass().getSimpleName());throw new org.springframework.security.access.AccessDeniedException("访问令牌或当前接口授权无效");}
 }
}
