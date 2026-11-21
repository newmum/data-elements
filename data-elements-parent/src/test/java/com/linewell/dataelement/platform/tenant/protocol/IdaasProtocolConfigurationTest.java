package com.linewell.dataelement.platform.tenant.protocol;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linewell.dataelement.magic.runtime.route.RegisteredMagicFunctionInvoker;
import com.linewell.dataelement.platform.tenant.domain.TenantContext;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.*;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import com.linewell.dataelement.platform.web.security.ProtocolAccess;
import static org.mockito.Mockito.mock;

class IdaasProtocolConfigurationTest {
 @TempDir Path directory;
 private final IdaasProtocolConfiguration configuration = new IdaasProtocolConfiguration();
 private RSAKey key() throws Exception {return new RSAKeyGenerator(2048).keyID("stable-test-key").keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).generate();}
 private String expression() throws Exception {return IdaasProtocolConfiguration.class.getMethod("identitySigningKeys",String.class).getParameters()[0].getAnnotation(Value.class).value();}
 @Test void missingOrFalseProtocolSwitchDoesNotLoadSigningKeysOrOAuthServices() {
  var runner=new ApplicationContextRunner().withUserConfiguration(IdaasProtocolConfiguration.class,
      MagicRegisteredClientRepository.class,MagicOAuth2AuthorizationService.class,
      MagicOAuth2ConsentService.class,IdentityConsentController.class)
      .withPropertyValues("idaas.protocol.jwk-file="+directory.resolve("missing.jwk"));
  for(var active:new String[]{null,"idaas.protocol.enabled=false"}) {
   var configured=active==null?runner:runner.withPropertyValues(active);
   configured.run(context->{
    assertNull(context.getStartupFailure());
    assertEquals(0,context.getBeanNamesForType(com.nimbusds.jose.jwk.source.JWKSource.class).length);
    assertEquals(0,context.getBeanNamesForType(JwtDecoder.class).length);
    assertEquals(0,context.getBeanNamesForType(AuthorizationServerSettings.class).length);
    assertEquals(0,context.getBeanNamesForType(MagicRegisteredClientRepository.class).length);
    assertEquals(0,context.getBeanNamesForType(MagicOAuth2AuthorizationService.class).length);
    assertEquals(0,context.getBeanNamesForType(MagicOAuth2ConsentService.class).length);
    assertEquals(0,context.getBeanNamesForType(IdentityConsentController.class).length);
   });
  }
 }
 @Test void disabledProtocolKeepsTheExistingApplicationSecurityChain() {
  new WebApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class))
      .withUserConfiguration(ExistingApplicationSecurityConfiguration.class,IdaasProtocolConfiguration.class)
      .withPropertyValues("idaas.protocol.enabled=false","idaas.protocol.jwk-file="+directory.resolve("missing.jwk"))
      .run(context->{
       assertNull(context.getStartupFailure());
       assertEquals(1,context.getBeanNamesForType(SecurityFilterChain.class).length);
       assertTrue(context.containsBean("existingApplicationSecurity"));
      });
 }
 @Test void explicitlyEnabledProtocolLoadsPersistentKeyAndBothSecurityChains() throws Exception {
  var file=directory.resolve("enabled.jwk");Files.writeString(file,key().toJSONString());
  new WebApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class))
      .withUserConfiguration(ExistingApplicationSecurityConfiguration.class,IdaasProtocolConfiguration.class,
          MagicRegisteredClientRepository.class,MagicOAuth2AuthorizationService.class,
          MagicOAuth2ConsentService.class,IdentityConsentController.class)
      .withBean(IdentityProtocolBridge.class,()->mock(IdentityProtocolBridge.class))
      .withPropertyValues("idaas.protocol.enabled=true","idaas.protocol.jwk-file="+file)
      .run(context->{
       assertNull(context.getStartupFailure());
       assertEquals(2,context.getBeanNamesForType(SecurityFilterChain.class).length);
       assertEquals(1,context.getBeanNamesForType(com.nimbusds.jose.jwk.source.JWKSource.class).length);
       assertEquals(1,context.getBeanNamesForType(JwtDecoder.class).length);
       assertEquals(1,context.getBeanNamesForType(AuthorizationServerSettings.class).length);
       assertEquals(1,context.getBeanNamesForType(IdentityConsentController.class).length);
       var settings = context.getBean(AuthorizationServerSettings.class);
       assertEquals("http://localhost:8088", settings.getIssuer());
       assertEquals(List.of("/idaas/oauth2/authorize", "/idaas/oauth2/token", "/idaas/oauth2/introspect",
               "/idaas/oauth2/revoke", "/idaas/oauth2/jwks", "/idaas/connect/userinfo", "/idaas/connect/logout"),
               List.of(settings.getAuthorizationEndpoint(), settings.getTokenEndpoint(), settings.getTokenIntrospectionEndpoint(),
                       settings.getTokenRevocationEndpoint(), settings.getJwkSetEndpoint(), settings.getOidcUserInfoEndpoint(),
                       settings.getOidcLogoutEndpoint()));
       assertFalse(IdentityConsentController.class.getAnnotation(ProtocolAccess.class).bindTenant());
       List<Class<?>> filters = context.getBean("idaasProtocol", SecurityFilterChain.class).getFilters().stream()
               .<Class<?>>map(Object::getClass).toList();
       int contextFilter = filters.indexOf(RequestContextFilter.class);
       assertTrue(contextFilter >= 0 && contextFilter < filters.indexOf(IdaasPrincipalFilter.class));
       assertFalse(context.containsBean("idaasProtocolRequestContext"));
      });
 }

 @Test void enabledProtocolIssuesAndStoresAValidationClientTokenThroughRegisteredCallbacks() throws Exception {
  var file = directory.resolve("callback-validation.jwk");
  Files.writeString(file, key().toJSONString());
  var functions = mock(RegisteredMagicFunctionInvoker.class);
  String secret = "validation-client-secret-fixture-186";
  String secretHash = new IdentityProtocolBridge(functions).hashClientSecret(secret);
  String clientResource = "f919bae96dc45a398e97ad8bfbdf2dfc";
  String accessResource = "b8e9f119f73a5c5d80fef296a2d55079";
  String storeResource = "3abcf6298afb5031ab99f4c9897f4809";
  String tokenPath = "/validation-protocol/token";
  String consentPath = "/validation-protocol/consent";
  String jwksPath = "/validation-protocol/keys";
  when(functions.invoke(eq(clientResource), anyMap())).thenAnswer(invocation -> {
   assertTrue(TenantContext.usesControlDatabase());
   var request = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
   assertNotNull(request.getResponse());
   assertTrue(List.of(tokenPath, consentPath).contains(request.getRequest().getRequestURI()));
   return Map.ofEntries(Map.entry("found", true), Map.entry("id", "validation-client-id"),
           Map.entry("clientKey", "validation-client"), Map.entry("name", "validation"),
           Map.entry("clientType", "SERVICE"), Map.entry("secretHash", secretHash),
           Map.entry("grants", List.of("client_credentials")), Map.entry("scopes", List.of("api")),
           Map.entry("redirects", List.of()), Map.entry("logoutRedirects", List.of()),
           Map.entry("accessTtl", 300), Map.entry("refreshTtl", 600));
  });
  when(functions.invoke(eq(accessResource), anyMap())).thenAnswer(invocation -> {
   assertTrue(TenantContext.usesControlDatabase());
   return Map.of("allowed", true, "appId", "validation-app", "domain", "workforce",
           "roleIds", List.of(), "resourceIds", List.of());
  });
  when(functions.invoke(eq(storeResource), anyMap())).thenAnswer(invocation -> {
   assertTrue(TenantContext.usesControlDatabase());
   return Map.of("saved", true);
  });
  new WebApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class))
      .withUserConfiguration(ProtocolValidationMvc.class, ExistingApplicationSecurityConfiguration.class,
          IdaasProtocolConfiguration.class, IdentityProtocolBridge.class, MagicRegisteredClientRepository.class,
          MagicOAuth2AuthorizationService.class, MagicOAuth2ConsentService.class, IdentityConsentController.class)
      .withBean(RegisteredMagicFunctionInvoker.class, () -> functions)
      .withPropertyValues("idaas.protocol.enabled=true", "idaas.protocol.jwk-file=" + file,
              "idaas.protocol.endpoints.token=" + tokenPath, "idaas.protocol.consent-path=" + consentPath,
              "idaas.protocol.endpoints.jwks=" + jwksPath)
      .run(context -> {
       assertNull(context.getStartupFailure());
       var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
               .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
       var discovery = mvc.perform(get("/.well-known/openid-configuration")).andExpect(status().isOk()).andReturn().getResponse();
       var metadata = new ObjectMapper().readTree(discovery.getContentAsString());
       assertEquals("http://localhost:8088" + tokenPath, metadata.path("token_endpoint").asText());
       assertEquals("http://localhost:8088" + jwksPath, metadata.path("jwks_uri").asText());
       mvc.perform(get(jwksPath)).andExpect(status().isOk());
       mvc.perform(get(consentPath).param("client_id", "validation-client").param("state", "expired"))
               .andExpect(status().isForbidden());
       mvc.perform(post("/idaas/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                       .param("grant_type", "client_credentials")).andExpect(status().isNotFound());
       var basic = Base64.getEncoder().encodeToString(("validation-client:" + secret).getBytes(StandardCharsets.UTF_8));
       var response = mvc.perform(post(tokenPath).header("Authorization", "Basic " + basic)
                       .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "client_credentials"))
               .andExpect(status().isOk()).andReturn().getResponse();
       String token = new ObjectMapper().readTree(response.getContentAsString()).path("access_token").asText();
       var claims = context.getBean(JwtDecoder.class).decode(token);
       assertEquals(List.of("validation-app"), claims.getAudience());
       assertEquals("CLIENT", claims.getClaimAsString("principal_kind"));
       verify(functions, org.mockito.Mockito.times(2)).invoke(eq(clientResource), anyMap());
       verify(functions).invoke(eq(accessResource), anyMap());
       verify(functions, org.mockito.Mockito.times(2)).invoke(eq(storeResource), anyMap());
       assertFalse(context.getBean(IdentityProtocolBridge.class).isTrusted());
       assertFalse(TenantContext.usesControlDatabase());
       assertNull(RequestContextHolder.getRequestAttributes());
      });
 }

 @Configuration(proxyBeanMethods = false)
 @EnableWebMvc
 static class ProtocolValidationMvc {
 }
 @Test void acceptsDocumentedEnvironmentNameAndExplicitPropertyWithCorrectPrecedence() throws Exception {
  var environment=new StandardEnvironment();
  var properties=new java.util.HashMap<String,Object>();properties.put("IDAAS_PROTOCOL_JWK_FILE",directory.resolve("env.jwk").toString());
  environment.getPropertySources().addFirst(new MapPropertySource("test",properties));
  assertEquals(properties.get("IDAAS_PROTOCOL_JWK_FILE"),environment.resolveRequiredPlaceholders(expression()));
  properties.put("idaas.protocol.jwk-file",directory.resolve("nacos.jwk").toString());
  assertEquals(properties.get("idaas.protocol.jwk-file"),environment.resolveRequiredPlaceholders(expression()));
 }
 @Test void reloadingPersistentKeyVerifiesTokensIssuedBeforeRestartAndExposesOnlyPublicJwks() throws Exception {
  var privateKey=key();var file=directory.resolve("key.jwk");Files.writeString(file,privateKey.toJSONString());
  var first=configuration.identitySigningKeys(file.toString());
  var selected=(RSAKey)first.get(new JWKSelector(new JWKMatcher.Builder().keyID(privateKey.getKeyID()).build()),null).get(0);
  var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(selected.getKeyID()).build(),new JWTClaimsSet.Builder().subject("persistent-account").build());jwt.sign(new RSASSASigner(selected));
  var reloaded=configuration.identitySigningKeys(file.toString());var publicKey=(RSAKey)reloaded.get(new JWKSelector(new JWKMatcher.Builder().keyID(privateKey.getKeyID()).build()),null).get(0);
  assertTrue(SignedJWT.parse(jwt.serialize()).verify(new RSASSAVerifier(publicKey.toPublicJWK())));
  assertEquals(privateKey.getKeyID(),publicKey.getKeyID());assertFalse(publicKey.toPublicJWK().isPrivate());assertFalse(publicKey.toPublicJWK().toJSONObject().containsKey("d"));
 }
 @Test void rejectsMissingOrPublicOnlyKeyBeforeServerStarts() throws Exception {
  assertTrue(assertThrows(IllegalStateException.class,()->configuration.identitySigningKeys(directory.resolve("missing.jwk").toString())).getMessage().contains("initialize-idaas-protocol-key.ps1"));
  var file=directory.resolve("public.jwk");Files.writeString(file,key().toPublicJWK().toJSONString());
  assertThrows(IllegalStateException.class,()->configuration.identitySigningKeys(file.toString()));
  assertThrows(IllegalStateException.class,()->configuration.identitySigningKeys(" "));
 }
 @Test void rejectsMissingKeyIdAndWrongSigningPurpose() throws Exception {
  var file=directory.resolve("bad.jwk");Files.writeString(file,new RSAKey.Builder(key()).keyID(null).build().toJSONString());assertThrows(IllegalStateException.class,()->configuration.identitySigningKeys(file.toString()));
  Files.writeString(file,new RSAKey.Builder(key()).keyUse(KeyUse.ENCRYPTION).build().toJSONString());assertThrows(IllegalStateException.class,()->configuration.identitySigningKeys(file.toString()));
 }
}
