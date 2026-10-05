package com.linewell.dataelement.platform.tenant.protocol;

import java.util.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

/** Escaped protocol consent view. Pending state, principal and scopes come from the framework store. */
@Controller
@ConditionalOnProperty(prefix="idaas.protocol",name="enabled",havingValue="true")
public class IdentityConsentController {
 private final IdentityProtocolBridge bridge;private final MagicOAuth2AuthorizationService authorizations;private final MagicRegisteredClientRepository clients;private final AuthorizationServerSettings settings;
 public IdentityConsentController(IdentityProtocolBridge bridge,MagicOAuth2AuthorizationService authorizations,MagicRegisteredClientRepository clients,AuthorizationServerSettings settings){this.bridge=bridge;this.authorizations=authorizations;this.clients=clients;this.settings=settings;}
 @GetMapping("/idaas/oauth2/consent") public void consent(@RequestParam("client_id")String clientId,@RequestParam("state")String state,HttpServletRequest request,HttpServletResponse response)throws java.io.IOException{
  String token=request.getHeader("idaas-auth-token");if(token==null && request.getCookies()!=null)for(Cookie c:request.getCookies())if("IDAAS_AUTH".equals(c.getName()))token=c.getValue();
  var principal=token==null?null:bridge.call("protocol-access",Map.of("operation","principal","token",token));
  var pending=authorizations.findByToken(state,new OAuth2TokenType("state"));var client=clients.findByClientId(clientId);
  if(principal==null || !Boolean.TRUE.equals(principal.get("allowed")) || pending==null || client==null || !pending.getRegisteredClientId().equals(client.getId()) || !pending.getPrincipalName().equals(principal.get("accountId"))){response.sendError(403,"授权确认已失效，请重新发起登录");return;}
  OAuth2AuthorizationRequest original=pending.getAttribute(OAuth2AuthorizationRequest.class.getName());if(original==null){response.sendError(403);return;}
  StringBuilder fields=new StringBuilder();var labels=Map.of("profile","基本身份资料","email","电子邮箱","api","已获授权的应用接口");
  for(String scope:original.getScopes())if(!"openid".equals(scope))fields.append("<label class=scope><input type=checkbox name=scope value=\"").append(escape(scope)).append("\" checked> ").append(escape(labels.getOrDefault(scope,scope))).append("</label>");
  response.setContentType("text/html;charset=UTF-8");response.setHeader("Cache-Control","no-store");response.setHeader("X-Frame-Options","DENY");response.setHeader("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
  response.getWriter().write("<!doctype html><html lang=zh-CN><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>确认应用授权 · 统一身份管理平台</title><style>body{margin:0;background:#f4f6fc;color:#15243b;font:16px system-ui,sans-serif}main{max-width:480px;margin:10vh auto;padding:32px;background:white;border-radius:16px;box-shadow:0 16px 60px #15243b12}h1{font-size:24px;line-height:1.4}p{line-height:1.7;overflow-wrap:anywhere}.scope{display:flex;align-items:center;min-height:44px;gap:12px}button{min-height:48px;font:16px inherit;border:0;border-radius:8px;padding:12px 24px;margin-top:24px;background:#4f60ff;color:white;width:100%}.secondary{background:#eef1ff;color:#15243b;margin-top:12px}@media(max-width:540px){main{margin:24px 16px;padding:24px}}</style><main><p>统一身份管理平台</p><h1>确认应用访问授权</h1><p>应用“"+escape(client.getClientName())+"”申请使用你的统一认证身份。请选择本次同意提供的资料或访问范围。</p><form method=post action=\""+escape(settings.getAuthorizationEndpoint())+"\"><input type=hidden name=client_id value=\""+escape(clientId)+"\"><input type=hidden name=state value=\""+escape(state)+"\"><p>统一身份标识用于识别当前认证账号。</p>"+fields+"<button type=submit>同意并继续</button></form><form method=post action=\""+escape(settings.getAuthorizationEndpoint())+"\"><input type=hidden name=client_id value=\""+escape(clientId)+"\"><input type=hidden name=state value=\""+escape(state)+"\"><button class=secondary type=submit>拒绝授权</button></form></main></html>");
 }
 private static String escape(String value){return HtmlUtils.htmlEscape(value==null?"":value);}
}
