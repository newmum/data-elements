package com.linewell.dataelement.platform.tenant.protocol;

import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;

/** Framework state serialization; persistence, revocation and replay decisions are editable Magic rules. */
@Component
@ConditionalOnProperty(prefix="idaas.protocol",name="enabled",havingValue="true")
public class MagicOAuth2AuthorizationService implements OAuth2AuthorizationService {
 private final IdentityProtocolBridge bridge;
 private final MagicRegisteredClientRepository clients;
 private final ObjectMapper mapper=new ObjectMapper();
 public MagicOAuth2AuthorizationService(IdentityProtocolBridge bridge,MagicRegisteredClientRepository clients){
  this.bridge=bridge;this.clients=clients;
  mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
  mapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
 }
 private Object standardCollections(Object value){
  if(value instanceof Map<?,?> map){var copy=new LinkedHashMap<String,Object>();map.forEach((k,v)->copy.put(String.valueOf(k),standardCollections(v)));return copy;}
  if(value instanceof Set<?> set){var copy=new HashSet<Object>();set.forEach(v->copy.add(standardCollections(v)));return copy;}
  if(value instanceof Collection<?> list){var copy=new ArrayList<Object>();list.forEach(v->copy.add(standardCollections(v)));return copy;}
  return value;
 }
 @Override public void save(OAuth2Authorization a){
  try{
   var state=new LinkedHashMap<String,Object>();state.put("id",a.getId());state.put("client",a.getRegisteredClientId());state.put("principal",a.getPrincipalName());state.put("grant",a.getAuthorizationGrantType().getValue());state.put("scopes",new ArrayList<>(a.getAuthorizedScopes()));state.put("attributes",mapper.writeValueAsString(standardCollections(a.getAttributes())));
   for(var type:List.of(OAuth2AuthorizationCode.class,OAuth2AccessToken.class,OAuth2RefreshToken.class,OidcIdToken.class)){
    var t=a.getToken(type);if(t==null)continue;var value=t.getToken();var token=new LinkedHashMap<String,Object>();token.put("value",value.getTokenValue());token.put("issued",value.getIssuedAt().toString());token.put("expires",value.getExpiresAt().toString());token.put("metadata",mapper.writeValueAsString(standardCollections(t.getMetadata())));if(value instanceof OAuth2AccessToken access)token.put("scopes",new ArrayList<>(access.getScopes()));if(value instanceof OidcIdToken id)token.put("claims",mapper.writeValueAsString(standardCollections(id.getClaims())));state.put(type.getSimpleName(),token);
   }
   var input=new LinkedHashMap<String,Object>();input.put("operation","save");input.put("state",new ObjectMapper().writeValueAsString(state));input.put("id",a.getId());input.put("clientId",a.getRegisteredClientId());input.put("principalName",a.getPrincipalName());input.put("grant",a.getAuthorizationGrantType().getValue());
   input.put("code",value(a,OAuth2AuthorizationCode.class));input.put("access",value(a,OAuth2AccessToken.class));input.put("refresh",value(a,OAuth2RefreshToken.class));input.put("stateValue",a.getAttribute("state"));
   input.put("revoked",invalidated(a,OAuth2AccessToken.class)||invalidated(a,OAuth2RefreshToken.class));
   var result=bridge.call("protocol-store",input);if(result==null || !Boolean.TRUE.equals(result.get("saved")))throw new IllegalStateException("认证状态未提交");
  }catch(java.io.IOException e){throw new IllegalStateException("协议状态编码失败",e);}
 }
 private <T extends OAuth2Token> String value(OAuth2Authorization a,Class<T> type){var t=a.getToken(type);return t==null?null:t.getToken().getTokenValue();}
 private <T extends OAuth2Token> boolean invalidated(OAuth2Authorization a,Class<T> type){var t=a.getToken(type);return t!=null && t.isInvalidated();}
 @Override public void remove(OAuth2Authorization a){bridge.call("protocol-store",Map.of("operation","remove","id",a.getId()));}
 @Override public OAuth2Authorization findById(String id){return find(Map.of("operation","find","id",id));}
 @Override public OAuth2Authorization findByToken(String token,OAuth2TokenType type){return find(Map.of("operation","find","token",token,"type",type==null?"":type.getValue()));}
 @SuppressWarnings("unchecked") private OAuth2Authorization find(Map<String,Object> input){
  var r=bridge.call("protocol-store",input);if(r==null || r.get("state")==null)return null;
  try{
   var state=new ObjectMapper().readValue((String)r.get("state"),Map.class);var client=clients.findById((String)state.get("client"));if(client==null)return null;
   var b=OAuth2Authorization.withRegisteredClient(client).id((String)state.get("id")).principalName((String)state.get("principal")).authorizationGrantType(new AuthorizationGrantType((String)state.get("grant"))).authorizedScopes(new HashSet<>((List<String>)state.get("scopes")));
   Map<String,Object> attrs=mapper.readValue((String)state.get("attributes"),Map.class);b.attributes(m->m.putAll(attrs));
   for(String type:List.of("OAuth2AuthorizationCode","OAuth2AccessToken","OAuth2RefreshToken","OidcIdToken")){
    Map<String,Object> t=(Map<String,Object>)state.get(type);if(t==null)continue;var issued=Instant.parse((String)t.get("issued"));var expires=Instant.parse((String)t.get("expires"));var value=(String)t.get("value");Map<String,Object> metadata=mapper.readValue((String)t.get("metadata"),Map.class);
    OAuth2Token token=switch(type){case "OAuth2AuthorizationCode"->new OAuth2AuthorizationCode(value,issued,expires);case "OAuth2AccessToken"->new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,value,issued,expires,new HashSet<>((List<String>)t.get("scopes")));case "OAuth2RefreshToken"->new OAuth2RefreshToken(value,issued,expires);default->new OidcIdToken(value,issued,expires,mapper.readValue((String)t.get("claims"),Map.class));};b.token(token,m->m.putAll(metadata));
   }
   return b.build();
  }catch(java.io.IOException e){throw new IllegalStateException("协议状态解码失败",e);}
 }
}
