package com.linewell.dataelement.platform.tenant.protocol;

import cn.dev33.satoken.context.SaTokenContextForThreadLocalStaff;
import com.linewell.dataelement.magic.runtime.route.RegisteredMagicFunctionInvoker;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class IdentityAuthorizationStateTest {
 @Test
 void registeredProtocolCallbacksPreserveControlScopeAndRestoreOuterContext() {
  var functions = mock(RegisteredMagicFunctionInvoker.class);
  var bridge = new IdentityProtocolBridge(functions);
  var payload = Map.<String, Object>of("operation", "validation");
  var input = Map.<String, Object>of("payload", payload);
  when(functions.invoke("f919bae96dc45a398e97ad8bfbdf2dfc", input)).thenAnswer(invocation -> {
   assertTrue(bridge.isTrusted());
   assertTrue(TenantContext.usesControlDatabase());
   assertNotNull(SaTokenContextForThreadLocalStaff.getModelBoxOrNull());
   return bridge.access(payload);
  });
  when(functions.invoke("b8e9f119f73a5c5d80fef296a2d55079", input)).thenAnswer(invocation -> {
   assertTrue(bridge.isTrusted());
   assertTrue(TenantContext.usesControlDatabase());
   return Map.of("allowed", true);
  });
  when(functions.invoke("3abcf6298afb5031ab99f4c9897f4809", input)).thenThrow(new IllegalStateException("validation"));
  var request = new MockHttpServletRequest();
  request.setParameter("trusted", "true");
  var attributes = new ServletRequestAttributes(request, new MockHttpServletResponse());
  RequestContextHolder.setRequestAttributes(attributes);
  try (var scope = TenantContext.use("industry")) {
   assertFalse(bridge.isTrusted());
   assertEquals(Map.of("allowed", true), bridge.client(payload));
   assertFalse(bridge.isTrusted());
   assertEquals("industry", TenantContext.getTenantId());
   assertNull(SaTokenContextForThreadLocalStaff.getModelBoxOrNull());
   assertThrows(IllegalStateException.class, () -> bridge.store(payload));
   assertFalse(bridge.isTrusted());
   assertEquals("industry", TenantContext.getTenantId());
   assertNull(SaTokenContextForThreadLocalStaff.getModelBoxOrNull());
  } finally {
   RequestContextHolder.resetRequestAttributes();
   attributes.requestCompleted();
  }
 }

 @Test void codePrincipalClaimsAndInvalidationSurviveEncryptedStoreBoundary(){
  var bridge=mock(IdentityProtocolBridge.class);var clients=mock(MagicRegisteredClientRepository.class);
  var client=RegisteredClient.withId("client").clientId("browser").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("https://app.example/callback").scope("openid").build();
  when(clients.findById("client")).thenReturn(client);var stored=new AtomicReference<String>();
  when(bridge.store(anyMap())).thenAnswer(inv->{Map<String,Object> p=inv.getArgument(0);if("save".equals(p.get("operation"))){stored.set((String)p.get("state"));return Map.of("saved",true);}return Map.of("state",stored.get());});
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
