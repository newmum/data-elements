{
  "properties": {},
  "id": "bc768b6b6b405a21bc42cd88f24e4a54",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "10.中央资料范围校验",
  "createTime": 1790615423659,
  "updateTime": 1791193400395,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/directory-scope",
  "description": "校验中央人员、机构和应用分配的可见及可写范围，防止跨身份域或跨应用操作。",
  "parameters": [
    {
      "name": "permission",
      "value": null,
      "description": "待校验的功能权限编码",
      "required": false,
      "dataType": "Object",
      "type": null,
      "defaultValue": null,
      "validateType": null,
      "error": null,
      "expression": null,
      "children": null
    },
    {
      "name": "identityDomain",
      "value": null,
      "description": "人员或公众身份域",
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
// 共享函数：10.中央资料范围校验
// 业务说明：校验中央人员、机构和应用分配的可见及可写范围，防止跨身份域或跨应用操作。
// 函数输入：permission：待校验的功能权限编码；identityDomain：人员或公众身份域。
// 返回与副作用：校验中央人员、机构和应用分配的可见及可写范围，防止跨身份域或跨应用操作。
import db;
import '@/idaas/context' as context;

// 中央资料范围与应用范围分开。应用范围不能修改共用人员或机构。
var ctx=context();
var domain=identityDomain||'workforce';
var sql=db.normal();
if(!['workforce','public'].contains(domain)) {
    exit 400,'身份域不正确';
}
if(ctx.account.must_change_password==1) {
    exit 403,'请先修改初始密码';
}
var all=false;
var roots=[];
var orgIds=[];
for(pair in ctx.pairs) {
    if(!pair.codes.contains(permission)) {
        continue;
    }
    for(s in pair.scopes) {
        if(s.identity_domain!=domain) {
            continue;
        }
        if(s.scope_kind=='ALL') {
            all=true;
        }
        if(s.scope_kind=='ORG' && s.app_id==null) {
            roots.add(s);
        }
    }
}
if(!all && roots.size()>0) {
    var rootIds=roots.map(scope=>scope.org_id).distinct();
    var childRootIds=roots.filter(scope=>scope.include_children==1).map(scope=>scope.org_id).distinct();
    if(rootIds.size()>500) {
        exit 400,'机构管理范围超过批量上限';
    }
    if(childRootIds.size()==0) {
        childRootIds=['__none__'];
    }
    var orgs=sql.select("""
select distinct o.tid as id
from iam_org_t o
where o.identity_domain=#{domain} and o.is_del=0
  and exists (
    select 1 from iam_org_t root
    where root.tid in
      <foreach collection="rootIds" item="rootId" open="(" close=")" separator=",">#{rootId}</foreach>
      and root.identity_domain=#{domain} and root.is_del=0 and root.status='ACTIVE'
      and (o.tid=root.tid or (root.tid in
        <foreach collection="childRootIds" item="rootId" open="(" close=")" separator=",">#{rootId}</foreach>
        and o.path like concat(root.path,'%')))
  )
""");
    orgIds=orgs.map(org=>org.id);
}
ctx.requiredDirectory=true;
ctx.requiredPermission=permission;
ctx.requiredDomain=domain;
return {
    ctx:ctx,all:all,orgIds:orgIds
};
