package com.linewell.dataelement.platform.tenant.protocol;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.*;
@Component
@ConditionalOnProperty(prefix="idaas.protocol",name="enabled",havingValue="true")
public class MagicOAuth2ConsentService implements OAuth2AuthorizationConsentService {
 private final IdentityProtocolBridge bridge;
 public MagicOAuth2ConsentService(IdentityProtocolBridge bridge){this.bridge=bridge;}
 @Override public void save(OAuth2AuthorizationConsent c){bridge.call("protocol-store",Map.of("operation","save-consent","clientId",c.getRegisteredClientId(),"principalName",c.getPrincipalName(),"authorities",c.getAuthorities().stream().map(a->a.getAuthority()).toList()));}
 @Override public void remove(OAuth2AuthorizationConsent c){bridge.call("protocol-store",Map.of("operation","remove-consent","clientId",c.getRegisteredClientId(),"principalName",c.getPrincipalName()));}
 @Override public OAuth2AuthorizationConsent findById(String client,String principal){var r=bridge.call("protocol-store",Map.of("operation","find-consent","clientId",client,"principalName",principal));if(r==null || r.get("authorities")==null)return null;var b=OAuth2AuthorizationConsent.withId(client,principal);for(Object a:(List<?>)r.get("authorities"))b.authority(new SimpleGrantedAuthority(a.toString()));return b.build();}
}
