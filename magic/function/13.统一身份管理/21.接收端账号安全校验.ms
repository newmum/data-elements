{
  "properties": {},
  "id": "275494036ca45d1ea4eb856c7a5bef94",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "21.接收端账号安全校验",
  "createTime": 1790619034459,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/receiver-account-check",
  "description": "校验租户接收管理接口的本地账号、签名和请求范围，防止平台会话直接作为租户账号使用。",
  "parameters": [
    {
      "name": "userId",
      "value": null,
      "description": "平台操作账号标识",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    }
  ],
  "returnType": null,
  "mappingPath": null
}
================================
// 共享函数：21.接收端账号安全校验
// 业务说明：校验租户接收管理接口的本地账号、签名和请求范围，防止平台会话直接作为租户账号使用。
// 函数输入：userId：平台操作账号标识。
// 返回与副作用：校验租户接收管理接口的本地账号、签名和请求范围，防止平台会话直接作为租户账号使用。
import db;
import identityRuntime;
import tenantRuntime;

// 被框架实际应用于本地登录与现有本地会话。未接入或已脱管的账号完全按本地规则运行。
var tenantId=tenantRuntime.id();
if(!identityRuntime.hasTable('idaas_receiver_object_t')) {
    return {
        allowed:true
    };
}
var sql=db.normal();
var rows=sql.select("""
select o.source_enabled
from idaas_receiver_object_t o
join idaas_receiver_binding_t b on b.app_id=o.app_id
and b.tenant_id=#{tenantId}
where o.object_type='SUBJECT'
    and o.local_id=#{userId}
    and o.management_state='MANAGED'
""");
if(rows.filter(x=>x.source_enabled!=1).size()==0) {
    return {
        allowed:true
    };
}
// 撤销中央来源不撤销另行授予的本地资格。
var local=sql.selectOne("""
select count(*) n
from rm_user_role_rela_t u
join rm_role_t r on r.id=u.role_id
and r.tenant_id=u.tenant_id
and r.deleted=0
and r.status=0
where u.tenant_id=#{tenantId}
    and u.user_id=#{userId}
    and coalesce(u.auth_from, 0)<>9
    and (u.end_time is null
         or u.end_time>utc_timestamp(6))
""");
return {
    allowed:local.n>0
};
