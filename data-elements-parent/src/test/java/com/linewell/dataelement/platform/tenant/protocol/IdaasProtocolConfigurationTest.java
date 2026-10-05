package com.linewell.dataelement.platform.tenant.protocol;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
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
import org.springframework.core.env.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
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
      });
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
