package com.linewell.dataelement.platform.audit;

import java.util.*;
import org.springframework.stereotype.Component;

/** Browser routes are untrusted annotations, resolved against authorized server menus. */
@Component
public class AuditNavigation {
    private final AuditStore store;
    public AuditNavigation(AuditStore store){this.store=store;}
    public Map<String,Object> snapshot(AuditIdentity actor){
        var request=AuditIdentityResolver.request();if(request==null)return Map.of();
        if(request.getAttribute("audit.navigation") instanceof Map<?,?> cached){@SuppressWarnings("unchecked")var value=(Map<String,Object>)cached;return value;}
        Map<String,Object> result=new LinkedHashMap<>();
        String route=Objects.toString(request.getHeader("X-Audit-Route"),"").split("[?#]",2)[0];
        if(!route.startsWith("/")||route.length()>512||route.chars().anyMatch(c->c<32))route="";
        try {
            if(!actor.platform()&&actor.sourceApp()!=null&&actor.id()!=null&&!route.isEmpty()){
                var rows=store.jdbc(actor.tenantId()).queryForList("""
                    select m.id,m.parent_id,m.name,m.url,m.code,m.status,m.deleted,
                      case when a.app_resource_id is null then 0 else 1 end permitted
                    from rm_menu_t m left join (
                      select distinct rm.app_resource_id from rm_user_role_rela_t ur
                      join rm_role_t r on r.tenant_id=ur.tenant_id and r.id=ur.role_id and r.status=0 and r.deleted=0 and r.app_id=?
                      join rm_role_menu_rela_t rm on rm.tenant_id=r.tenant_id and rm.role_id=r.id
                      where ur.tenant_id=? and ur.user_id=?
                    ) a on a.app_resource_id=m.id
                    where m.tenant_id=? and m.app_id=? limit 10001
                    """,actor.sourceApp(),actor.tenantId(),actor.id(),actor.tenantId(),actor.sourceApp());
                if(rows.size()>10000)throw new IllegalStateException("Menu snapshot exceeds bound");
                var nodes=index(rows);String selected=null;int longest=-1;
                for(var node:nodes.values()){
                    if(!"1".equals(String.valueOf(node.get("permitted"))))continue;
                    var chain=chain(nodes,String.valueOf(node.get("id")));if(chain.isEmpty())continue;
                    String path="";for(var parent:chain)path=join(path,Objects.toString(parent.get("url"),""));
                    if(Set.of("/management/logs","/governance/logs").contains(route)&&"logs:group".equals(node.get("code"))){selected=String.valueOf(node.get("id"));longest=route.length();}
                    String canonicalRoute=route.equals("/node-config")?"/nifi-node-config":route;
                    for(String candidate:List.of(path,Objects.toString(node.get("url"),"")))if(candidate.length()>1&&(canonicalRoute.equals(candidate)||canonicalRoute.equals(candidate+"/list"))&&candidate.length()>longest){selected=String.valueOf(node.get("id"));longest=candidate.length();}
                }
                if(selected!=null){put(result,selected,chain(nodes,selected));if(result.containsKey("menu_id"))result.put("page_route",route);}
            }else if(actor.platform()&&request.getAttribute("audit.platformContext") instanceof Map<?,?> context){
                result.putAll(AuditPlatformNavigation.snapshot(route,context));
            }
        }catch(Exception unavailable){org.slf4j.LoggerFactory.getLogger("audit.health").warn("audit_navigation_unavailable type={}",unavailable.getClass().getSimpleName());}
        request.setAttribute("audit.navigation",result);return result;
    }
    private void put(Map<String,Object> result,String id,List<Map<String,Object>> chain){if(chain.isEmpty())return;result.put("menu_id",id);result.put("menu_path",AuditSanitizer.text(String.join(" → ",chain.stream().map(n->Objects.toString(n.get("name"),"")).filter(n->!n.isBlank()).toList()),1000));}
    static Map<String,Map<String,Object>> index(List<Map<String,Object>> rows){var nodes=new LinkedHashMap<String,Map<String,Object>>();rows.forEach(r->{var n=AuditStore.lower(r);nodes.put(String.valueOf(n.get("id")),n);});return nodes;}
    static List<Map<String,Object>> chain(Map<String,Map<String,Object>> nodes,String id){
        var reverse=new ArrayList<Map<String,Object>>();var visited=new HashSet<String>();
        while(id!=null&&!id.isBlank()&&!Set.of("ROOT","0").contains(id)){
            var n=nodes.get(id);if(n==null||!visited.add(id)||visited.size()>40||!"0".equals(String.valueOf(n.get("status")))||!"0".equals(String.valueOf(n.get("deleted"))))return List.of();
            reverse.add(n);id=n.get("parent_id")==null?null:String.valueOf(n.get("parent_id"));
        }Collections.reverse(reverse);return reverse;
    }
    static String join(String parent,String value){if(value.isBlank()||value.equals("ROOT")||value.startsWith("http"))return parent;value="/"+value.replaceAll("^/+|/+$","");if(parent.isBlank()||value.equals(parent)||value.startsWith(parent+"/"))return value;return parent+value;}
}
