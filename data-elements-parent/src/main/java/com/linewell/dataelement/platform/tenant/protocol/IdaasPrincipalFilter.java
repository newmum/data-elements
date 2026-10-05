package com.linewell.dataelement.platform.tenant.protocol;
import java.io.IOException;
import java.util.Map;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.filter.OncePerRequestFilter;
/** Accepts only the independent auth-account namespace. Platform and tenant tokens cannot form an OAuth principal. */
public class IdaasPrincipalFilter extends OncePerRequestFilter {
 private final IdentityProtocolBridge bridge;
 public IdaasPrincipalFilter(IdentityProtocolBridge bridge){this.bridge=bridge;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  var prior=SecurityContextHolder.getContext().getAuthentication();
  if(prior instanceof UsernamePasswordAuthenticationToken && prior.getAuthorities().stream().anyMatch(a->"IDAAS_AUTH_ACCOUNT".equals(a.getAuthority())))SecurityContextHolder.clearContext();
  String token=req.getHeader("idaas-auth-token");if(token==null && req.getCookies()!=null)for(Cookie cookie:req.getCookies())if("IDAAS_AUTH".equals(cookie.getName()))token=cookie.getValue();
  if(token!=null){var result=bridge.call("protocol-access",Map.of("operation","principal","token",token));if(result!=null && Boolean.TRUE.equals(result.get("allowed"))){String id=(String)result.get("accountId");var user=User.withUsername(id).password("").authorities("IDAAS_AUTH_ACCOUNT").build();SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user,null,user.getAuthorities()));}}
  chain.doFilter(req,res);
 }
}
