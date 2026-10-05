package com.linewell.dataelement.platform.tenant.protocol;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class IdentityAuthorizationStateTest {
 @Test void codePrincipalClaimsAndInvalidationSurviveEncryptedStoreBoundary(){
  var bridge=mock(IdentityProtocolBridge.class);var clients=mock(MagicRegisteredClientRepository.class);
  var client=RegisteredClient.withId("client").clientId("browser").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("https://app.example/callback").scope("openid").build();
  when(clients.findById("client")).thenReturn(client);var stored=new AtomicReference<String>();
  when(bridge.call(eq("protocol-store"),anyMap())).thenAnswer(inv->{Map<String,Object> p=inv.getArgument(1);if("save".equals(p.get("operation"))){stored.set((String)p.get("state"));return Map.of("saved",true);}return Map.of("state",stored.get());});
  var service=new MagicOAuth2AuthorizationService(bridge,clients);var now=Instant.now();
  var principal=User.withUsername("account").password("").authorities("IDAAS_AUTH_ACCOUNT").build();
  var auth=UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
  var request=OAuth2AuthorizationRequest.authorizationCode().authorizationUri("https://issuer.example/authorize").clientId("browser").redirectUri("https://app.example/callback").scopes(Set.of("openid")).state("random-state").additionalParameters(Map.of("code_challenge","pkce","code_challenge_method","S256")).build();
  var original=OAuth2Authorization.withRegisteredClient(client).id("authorization").principalName("account").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizedScopes(Set.of("openid"))
   .attribute(java.security.Principal.class.getName(),auth).attribute(OAuth2AuthorizationRequest.class.getName(),request).attribute("state","random-state")
   .token(new OAuth2AuthorizationCode("code",now,now.plusSeconds(120)),m->m.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME,true))
   .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"access",now,now.plusSeconds(300),Set.of("openid")))
   .refreshToken(new OAuth2RefreshToken("refresh",now,now.plusSeconds(600)))
   .token(new OidcIdToken("id-token",now,now.plusSeconds(300),Map.of("sub","subject","aud",List.of("browser"),"iat",now,"exp",now.plusSeconds(300)))) .build();
  service.save(original);var decoded=service.findById("authorization");
  assertEquals("account",decoded.getPrincipalName());assertEquals("random-state",decoded.getAttribute("state"));
  assertTrue(decoded.getToken(OAuth2AuthorizationCode.class).isInvalidated());
  assertEquals("refresh",decoded.getRefreshToken().getToken().getTokenValue());
  assertEquals(request.getAdditionalParameters(),((OAuth2AuthorizationRequest)decoded.getAttribute(OAuth2AuthorizationRequest.class.getName())).getAdditionalParameters());
  assertEquals("account",((UsernamePasswordAuthenticationToken)decoded.getAttribute(java.security.Principal.class.getName())).getName());
  assertEquals("subject",decoded.getToken(OidcIdToken.class).getToken().getSubject());
 }
}
