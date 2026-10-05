package com.linewell.dataelement.platform.tenant.protocol;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;

/** Maps approved Magic configuration to the established authorization framework. */
@Component
@ConditionalOnProperty(prefix="idaas.protocol",name="enabled",havingValue="true")
public class MagicRegisteredClientRepository implements RegisteredClientRepository {
 private final IdentityProtocolBridge bridge;
 public MagicRegisteredClientRepository(IdentityProtocolBridge bridge){this.bridge=bridge;}
 @Override public void save(RegisteredClient client){throw new UnsupportedOperationException("请通过Magic认证接入配置维护客户端");}
 @Override public RegisteredClient findById(String id){return find(Map.of("id",id));}
 @Override public RegisteredClient findByClientId(String id){return find(Map.of("clientKey",id));}
 @SuppressWarnings("unchecked") private RegisteredClient find(Map<String,Object> criteria){
  var r=bridge.call("protocol-client",criteria);if(r==null || !Boolean.TRUE.equals(r.get("found")))return null;
  var b=RegisteredClient.withId((String)r.get("id")).clientId((String)r.get("clientKey")).clientName((String)r.get("name"));
  String type=(String)r.get("clientType");b.clientAuthenticationMethod("PUBLIC".equals(type)?ClientAuthenticationMethod.NONE:ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
  if(!"PUBLIC".equals(type))b.clientSecret((String)r.get("secretHash"));
  for(String grant:(List<String>)r.get("grants"))b.authorizationGrantType(new AuthorizationGrantType(grant));
  for(String scope:(List<String>)r.get("scopes"))b.scope(scope);
  for(String uri:(List<String>)r.get("redirects"))b.redirectUri(uri);
  for(String uri:(List<String>)r.get("logoutRedirects"))b.postLogoutRedirectUri(uri);
  b.clientSettings(ClientSettings.builder().requireProofKey(!"SERVICE".equals(type)).requireAuthorizationConsent(!"SERVICE".equals(type)).build());
  b.tokenSettings(TokenSettings.builder().authorizationCodeTimeToLive(Duration.ofSeconds(120)).accessTokenTimeToLive(Duration.ofSeconds(((Number)r.get("accessTtl")).longValue())).refreshTokenTimeToLive(Duration.ofSeconds(((Number)r.get("refreshTtl")).longValue())).reuseRefreshTokens(false).build());
  return b.build();
 }
}
