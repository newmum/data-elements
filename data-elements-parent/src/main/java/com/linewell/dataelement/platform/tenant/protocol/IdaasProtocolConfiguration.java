package com.linewell.dataelement.platform.tenant.protocol;

import java.net.URI;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.web.filter.RequestContextFilter;

/** Standards protocol only; all editable account, qualification, audience and client rules delegate to Magic. */
@Configuration
@ConditionalOnProperty(prefix="idaas.protocol",name="enabled",havingValue="true")
@PropertySource("classpath:idaas-protocol.properties")
public class IdaasProtocolConfiguration {
 @Bean @Order(1) public SecurityFilterChain idaasProtocol(HttpSecurity http, IdentityProtocolBridge bridge,
         @Value("${idaas.protocol.login-url}") String loginUrl, @Value("${idaas.protocol.consent-path}") String consentPath) throws Exception {
  OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
  http.getConfigurer(OAuth2AuthorizationServerConfigurer.class).oidc(Customizer.withDefaults())
          .authorizationEndpoint(customizer -> customizer.consentPage(consentPath));
  // Bind the request before Magic callbacks, only for requests owned by this protocol chain.
  http.addFilterBefore(new IdaasPrincipalFilter(bridge), org.springframework.security.web.authentication.logout.LogoutFilter.class);
  http.addFilterBefore(new RequestContextFilter(), IdaasPrincipalFilter.class);
  // Authentication is checked through Magic on every request; a previous
  // servlet session must not preserve an account after logout or revocation.
  http.securityContext(c->c.securityContextRepository(new org.springframework.security.web.context.NullSecurityContextRepository()));
  http.exceptionHandling(e->e.authenticationEntryPoint((request,response,error)->response.sendRedirect(loginUrl+"?returnTo="+java.net.URLEncoder.encode(request.getRequestURI()+(request.getQueryString()==null?"":"?"+request.getQueryString()),java.nio.charset.StandardCharsets.UTF_8))));
  http.oauth2ResourceServer(o->o.jwt(Customizer.withDefaults()));
  return http.build();
 }
 @Bean public AuthorizationServerSettings authorizationSettings(Environment environment, @Value("${idaas.protocol.issuer}") String issuer) {
  URI address = URI.create(issuer);
  boolean local = "http".equals(address.getScheme()) && "localhost".equals(address.getHost())
          && address.getPort() == 8088 && address.getRawPath().isEmpty();
  if (address.getHost() == null || address.getRawUserInfo() != null || address.getRawQuery() != null
          || address.getRawFragment() != null || (!"https".equals(address.getScheme()) && !local)) {
   throw new IllegalArgumentException("认证签发地址须使用HTTPS");
  }
  String prefix = "idaas.protocol.endpoints.";
  return AuthorizationServerSettings.builder().issuer(issuer)
          .authorizationEndpoint(environment.getRequiredProperty(prefix + "authorization"))
          .tokenEndpoint(environment.getRequiredProperty(prefix + "token"))
          .tokenIntrospectionEndpoint(environment.getRequiredProperty(prefix + "introspection"))
          .tokenRevocationEndpoint(environment.getRequiredProperty(prefix + "revocation"))
          .jwkSetEndpoint(environment.getRequiredProperty(prefix + "jwks"))
          .oidcUserInfoEndpoint(environment.getRequiredProperty(prefix + "userinfo"))
          .oidcLogoutEndpoint(environment.getRequiredProperty(prefix + "logout"))
          .build();
 }
 @Bean public JWKSource<SecurityContext> identitySigningKeys(@Value("${idaas.protocol.jwk-file:${IDAAS_PROTOCOL_JWK_FILE:${user.home}/.data-elements/idaas/keys/protocol-signing.jwk}}")String location)throws Exception{
  if(location.isBlank())throw new IllegalStateException("请配置 IDAAS_PROTOCOL_JWK_FILE 或 idaas.protocol.jwk-file 指向独立持久 RSA 私钥 JWK 文件");
  Path file=Path.of(location.trim()).toAbsolutePath().normalize();
  if(!Files.isRegularFile(file) || !Files.isReadable(file))throw new IllegalStateException("认证签名密钥文件不存在或不可读："+file+"；请先运行 scripts/initialize-idaas-protocol-key.ps1，或配置 IDAAS_PROTOCOL_JWK_FILE 指向已有密钥；不要在重启时重新生成密钥");
  var key=RSAKey.parse(Files.readString(file));
  if(!key.isPrivate() || key.size()<2048 || key.getKeyID()==null || key.getKeyID().isBlank()
      || (key.getKeyUse()!=null && !KeyUse.SIGNATURE.equals(key.getKeyUse()))
      || (key.getAlgorithm()!=null && !com.nimbusds.jose.JWSAlgorithm.RS256.equals(key.getAlgorithm())))throw new IllegalStateException("认证签名密钥须包含不少于2048位的 RSA 私钥、稳定 kid，并用于 RS256 签名");
  var set=new JWKSet(key);return(selector,context)->selector.select(set);
 }
 @Bean public JwtDecoder identityJwtDecoder(JWKSource<SecurityContext> source){return OAuth2AuthorizationServerConfiguration.jwtDecoder(source);}
 @Bean public OAuth2TokenCustomizer<JwtEncodingContext> identityClaims(IdentityProtocolBridge bridge){return context->{
  boolean machine=AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType());
  var result=bridge.access(Map.of("operation","issue","clientId",context.getRegisteredClient().getId(),"principalName",context.getPrincipal().getName(),"machine",machine));
  if(result==null || !Boolean.TRUE.equals(result.get("allowed")))throw new OAuth2AuthenticationException(new OAuth2Error("access_denied","当前主体没有此应用的有效授权",null));
  if(org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType()))context.getClaims().audience(List.of((String)result.get("appId")));
  context.getClaims().claim("identity_domain",result.get("domain")).claim("principal_kind",machine?"CLIENT":"AUTH_ACCOUNT").claim("roles",result.get("roleIds")).claim("resources",result.get("resourceIds"));
  context.getClaims().claim("iam_client_id",context.getRegisteredClient().getId());
  if(!machine)context.getClaims().subject((String)result.get("subjectId")).claim("auth_account_id",result.get("accountId"));
 };
 }
}
