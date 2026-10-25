{
  "id": "log_scope_for_actor_v1",
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "32.日志任务权限重校验",
  "path": "/log-scope-for-actor",
  "parameters": [
    {
      "name": "actorId",
      "dataType": "Object",
      "required": false,
      "description": "已校验的日志业务参数"
    },
    {
      "name": "permission",
      "dataType": "Object",
      "required": false,
      "description": "已校验的日志业务参数"
    },
    {
      "name": "domain",
      "dataType": "Object",
      "required": false,
      "description": "已校验的日志业务参数"
    },
    {
      "name": "appId",
      "dataType": "Object",
      "required": false,
      "description": "已校验的日志业务参数"
    }
  ],
  "description": "供受控日志后台任务重新校验当前平台角色与同分配管理范围；不是HTTP接口。"
}
================================
import db;
import identityRuntime;
import '@/idaas/resource-enabled' as resourceEnabled;
return identityRuntime.control(()=>{
 if(!['audit:read','audit:export','audit:configure','audit:archive'].contains(permission) || !['workforce','public'].contains(domain)) {return {allowed:false};}
 var rows=db.normal().select("""
 select s.scope_kind,s.app_id,p.tid resource_id,r.app_id resource_app
 from iam_operator_t u
 join iam_operator_role_rela_t a on a.operator_id=u.tid and a.status='ACTIVE'
  and a.valid_from<=utc_timestamp(6) and (a.valid_until is null or a.valid_until>utc_timestamp(6))
 join iam_role_t r on r.tid=a.role_id and r.status='ACTIVE' and r.is_del=0
 join iam_application_t app on app.tid=r.app_id and app.kind='INTERNAL' and app.is_del=0 and app.status='ACTIVE'
 join iam_role_resource_rela_t rr on rr.role_id=r.tid and rr.app_id=r.app_id
 join iam_resource_t p on p.tid=rr.resource_id and p.app_id=r.app_id and p.is_del=0 and p.status='ACTIVE'
 join iam_operator_scope_t s on s.assignment_id=a.tid
 where u.tid=#{actorId} and u.status='ACTIVE' and u.is_del=0 and u.must_change_password=0
  and p.code=#{permission} and s.identity_domain=#{domain}
 """);
 if(rows.size()==0) {return {allowed:false};}
 var ids=rows.map(r=>r.resourceId||r.resource_id).distinct();
 var enabled=resourceEnabled(rows[0].resourceApp||rows[0].resource_app,'workforce',ids);
 var allowed=rows.filter(r=>enabled.contains(r.resourceId||r.resource_id) && ((r.scopeKind||r.scope_kind)=='ALL' || appId!='' && (r.scopeKind||r.scope_kind)=='APPLICATION' && (r.appId||r.app_id)==appId)).size()>0;
 return {allowed:allowed};
});
