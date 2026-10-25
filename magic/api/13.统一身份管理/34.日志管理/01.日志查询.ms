{
  "id": "log_query_platform_v1",
  "groupId": "log_management_api_group_v1",
  "name": "01.日志查询",
  "path": "/query",
  "method": "GET",
  "parameters": [],
  "requestBody": "{}",
  "description": "按真实平台会话权限查询当前身份域日志及生命周期记录。"
}
================================
import request;
import identityRuntime;
import logManagement;
import '@/idaas/log-guard' as logGuard;
import '@/idaas/context' as platformContext;
return identityRuntime.control(()=>{
 var query=(name,fallback)=>{var values=request.getValues(name);return values==null||values.size()==0?fallback:values[0];};
 var kind=query('kind','auth');var operation=query('operation','page');
 var ctx=null;
 var input={};
 for(name in ['page','size','keyword','status','result','startTime','endTime','sortField','sortDir','actorId','orgId','traceId','requestId','id']) {var value=query(name,null);if(value!=null){input[name]=value;}}
 if(operation=='capabilities'||operation=='components') {
  ctx=platformContext();var domain=query('domain','workforce');
  var definitions={auth:'audit:read',operations:'audit:read',requests:'audit:read',security:'audit:read',exports:'audit:export',policies:'audit:configure',archives:'audit:archive'};
  var available=[];
  for(name in definitions.keySet()) {if(ctx.pairs.filter(pair=>pair.codes.contains(definitions[name])&&pair.scopes.filter(s=>s.identity_domain==domain&&s.scope_kind=='ALL').size()>0).size()>0){available.add(name);}}
  if(available.size()==0){exit 403,'当前账号没有日志访问权限';}
  var checked=logGuard(available[0],domain);
  if(operation=='components'){return logManagement.query(checked,available[0],'components',{});}
  return available;
 }
 ctx=logGuard(kind,query('domain','workforce'));
 if(!['page','statistics','summary','detail','items','components','lifecycle'].contains(operation)){exit 400,'日志查询操作无效';}
 return logManagement.query(ctx,kind,operation,input);
});
