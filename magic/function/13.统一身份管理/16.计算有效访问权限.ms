{
  "properties": {},
  "id": "8d8e858f6ff057739f9bf1d0eab72bef",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "16.计算有效访问权限",
  "createTime": 1790617158595,
  "updateTime": 1790949884838,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/effective-access",
  "description": "汇合人员直授、机构继承及权限组等来源，应用有效期和状态规则，返回指定人员在应用内的有效角色与资源。",
  "parameters": [
    {
      "name": "subjectId",
      "value": null,
      "description": "中央人员身份标识",
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
      "name": "appId",
      "value": null,
      "description": "目标业务应用标识",
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
// 共享函数：16.计算有效访问权限
// 业务说明：汇合人员直授、机构继承及权限组等来源，应用有效期和状态规则，返回指定人员在应用内的有效角色与资源。
// 函数输入：subjectId：中央人员身份标识；appId：目标业务应用标识；identityDomain：人员或公众身份域。
// 返回与副作用：汇合人员直授、机构继承及权限组等来源，应用有效期和状态规则，返回指定人员在应用内的有效角色与资源。
import db;
import cn.hutool.json.JSONUtil;
import java.util.Collections;
import '@/idaas/resource-enabled' as enabled;

// 每次调用重新计算有效来源。此函数供投影、认证协议和受保护接口调用，不接受客户端权限缓存。
var sql=db.normal();
var domain=identityDomain||'workforce';
var result={
    appId:appId,hasAccess:false,roleIds:[],resourceIds:[],sources:[]
};
// 先确认应用和人员在同一身份域均处于可用状态；任一无效即无访问资格。
var app=sql.selectOne("""
select tid as id,identity_domains
from iam_application_t
where tid=#{appId}
    and kind='BUSINESS'
    and is_del=0
    and status='ACTIVE'
""");
var subject=sql.selectOne("""
select tid as id
from iam_subject_t
where tid=#{subjectId}
    and identity_domain=#{domain}
    and is_del=0
    and status='ACTIVE'
""");
if(app==null || subject==null || !JSONUtil.parseArray(app.identity_domains).contains(domain)) {
    return result;
}
// 只纳入有效任职及祖先机构均启用的组织，供机构继承授权匹配。
var orgs=sql.select("""
select o.tid as id,o.path
from iam_appointment_t a
join iam_org_t o on o.tid=a.org_id
and o.identity_domain=#{domain}
and o.is_del=0
and o.status='ACTIVE'
where a.subject_id=#{subjectId}
    and a.is_del=0
    and a.status='ACTIVE'
    and a.valid_from<=utc_timestamp(6)
    and (a.valid_until is null
         or a.valid_until>utc_timestamp(6))
    and not exists
        (select 1
         from iam_org_t ancestor
         where locate(concat('/', ancestor.tid, '/'), o.path)>0
             and (ancestor.is_del=1
                  or ancestor.status!='ACTIVE'))
""");
// 权限组成员关系必须在有效期内，停用组不参与授权计算。
var groups=sql.select("""
select g.tid as id,g.name
from iam_group_member_t m
join iam_permission_group_t g on g.tid=m.group_id
and g.identity_domain=#{domain}
and g.status='ACTIVE'
and g.is_del=0
where m.subject_id=#{subjectId}
    and m.status='ACTIVE'
    and m.valid_from<=utc_timestamp(6)
    and (m.valid_until is null
         or m.valid_until>utc_timestamp(6))
""");
var grants=sql.select("""
select g.*,g.tid as id,o.path org_path,o.name org_name,o.status org_status,o.is_del org_deleted
from iam_access_grant_t g
left join iam_org_t o on o.tid=g.org_id
where g.app_id=#{appId}
    and g.identity_domain=#{domain}
    and g.status='ACTIVE'
    and g.is_del=0
    and g.valid_from<=utc_timestamp(6)
    and (g.valid_until is null
         or g.valid_until>utc_timestamp(6))
""");
// 逐条判断直接、机构和权限组授权来源；角色与资源去重后再检查资源祖先状态。
for(g in grants) {
    var applies=g.principal_type=='SUBJECT' && g.subject_id==subjectId || g.principal_type=='GROUP' && groups.filter(x=>x.id==g.group_id).size()>0 || g.principal_type=='ORG' && g.org_status=='ACTIVE' && g.org_deleted==0 && orgs.filter(x=>x.id==g.org_id || g.include_children==1 && x.path.startsWith(g.org_path)).size()>0;
    if(!applies) {
        continue;
    }
    result.hasAccess=true;
    result.sources.add({
        grantId:g.id,source:g.principal_type=='SUBJECT'?'user':g.principal_type.toLowerCase(),label:g.principal_type=='SUBJECT'?'直接授权':g.principal_type=='ORG'?'机构授权：'+g.org_name:'权限组授权',subjectId:g.subject_id||g.org_id||g.group_id
    });
    var roles=sql.select("""
select r.tid as id
from iam_grant_role_rela_t rr
join iam_role_t r on r.tid=rr.role_id
and r.app_id=#{appId}
and r.identity_domain=#{domain}
and r.status='ACTIVE'
and r.is_del=0
where rr.grant_id=#{g.id}
    and rr.app_id=#{appId}
""");
    for(role in roles) {
        if(!result.roleIds.contains(role.id)) {
            result.roleIds.add(role.id);
        }
        var resources=sql.select("""
select r.tid as id
from iam_role_resource_rela_t rr
join iam_resource_t r on r.tid=rr.resource_id
and r.app_id=#{appId}
and r.identity_domain=#{domain}
and r.status='ACTIVE'
and r.is_del=0
where rr.role_id=#{role.id}
    and rr.app_id=#{appId}
""");
        for(resource in resources) {
            if(!result.resourceIds.contains(resource.id)) {
                result.resourceIds.add(resource.id);
            }
        }
    }
}
result.resourceIds=enabled(appId,domain,result.resourceIds);
Collections.sort(result.roleIds);
Collections.sort(result.resourceIds);
return result;
