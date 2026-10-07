{
  "properties": {},
  "id": "1d654cff904b5826baee18e59ff03346",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "30.批量计算有效访问权限",
  "createTime": 1791193400395,
  "updateTime": 1791193400395,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/effective-access-batch",
  "description": "批量预取应用授权、任职、权限组和角色资源，再在内存中计算各人员有效访问。",
  "parameters": [
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
      "description": "身份域",
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
      "name": "subjectIds",
      "value": null,
      "description": "最多 1000 个中央人员标识",
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
// 批量计算一个应用内人员的有效访问；输入应在调用方按 1000 人以内分组。
import db;
import cn.hutool.json.JSONUtil;
import java.util.Collections;
import '@/idaas/resource-enabled' as enabled;

var sql=db.normal();
var domain=identityDomain||'workforce';
var ids=(subjectIds||[]).distinct();
if(ids.size()>1000) {
    exit 400,'每次最多计算 1000 人';
}
var result={};
if(ids.size()==0) {
    return result;
}
var app=sql.selectOne("""
select identity_domains
from iam_application_t
where tid=#{appId} and kind='BUSINESS' and is_del=0 and status='ACTIVE'
""");
var people=sql.select("""
select tid as id
from iam_subject_t
where tid in <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
  and identity_domain=#{domain} and is_del=0 and status='ACTIVE'
""");
var orgs=sql.select("""
select a.subject_id,o.tid as id,o.path
from iam_appointment_t a
join iam_org_t o on o.tid=a.org_id and o.identity_domain=#{domain}
  and o.is_del=0 and o.status='ACTIVE'
where a.subject_id in <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
  and a.is_del=0 and a.status='ACTIVE' and a.valid_from<=utc_timestamp(6)
  and (a.valid_until is null or a.valid_until>utc_timestamp(6))
  and not exists (select 1 from iam_org_t ancestor
    where locate(concat('/',ancestor.tid,'/'),o.path)>0
      and (ancestor.is_del=1 or ancestor.status!='ACTIVE'))
""");
var groups=sql.select("""
select m.subject_id,g.tid as id
from iam_group_member_t m
join iam_permission_group_t g on g.tid=m.group_id and g.identity_domain=#{domain}
  and g.status='ACTIVE' and g.is_del=0
where m.subject_id in <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
  and m.status='ACTIVE' and m.valid_from<=utc_timestamp(6)
  and (m.valid_until is null or m.valid_until>utc_timestamp(6))
""");
var grants=sql.select("""
select g.*,g.tid as id,o.path org_path,o.status org_status,o.is_del org_deleted
from iam_access_grant_t g
left join iam_org_t o on o.tid=g.org_id
where g.app_id=#{appId} and g.identity_domain=#{domain} and g.status='ACTIVE'
  and g.is_del=0 and g.valid_from<=utc_timestamp(6)
  and (g.valid_until is null or g.valid_until>utc_timestamp(6))
""");
var grantRoles=sql.select("""
select rr.grant_id,r.tid as id
from iam_grant_role_rela_t rr
join iam_role_t r on r.tid=rr.role_id and r.app_id=#{appId}
  and r.identity_domain=#{domain} and r.status='ACTIVE' and r.is_del=0
where rr.app_id=#{appId}
""");
var roleResources=sql.select("""
select rr.role_id,r.tid as id
from iam_role_resource_rela_t rr
join iam_resource_t r on r.tid=rr.resource_id and r.app_id=#{appId}
  and r.identity_domain=#{domain} and r.status='ACTIVE' and r.is_del=0
where rr.app_id=#{appId}
""");
var activeResources=enabled(appId,domain,roleResources.map(row=>row.id).distinct());
var appActive=app!=null && JSONUtil.parseArray(app.identity_domains).contains(domain);
for(id in ids) {
    var access={appId:appId,hasAccess:false,roleIds:[],resourceIds:[]};
    if(appActive && people.filter(person=>person.id==id).size()==1) {
        var subjectOrgs=orgs.filter(org=>org.subject_id==id);
        var subjectGroups=groups.filter(group=>group.subject_id==id).map(group=>group.id);
        for(grant in grants) {
            var applies=grant.principal_type=='SUBJECT' && grant.subject_id==id
                || grant.principal_type=='GROUP' && subjectGroups.contains(grant.group_id)
                || grant.principal_type=='ORG' && grant.org_status=='ACTIVE' && grant.org_deleted==0
                    && subjectOrgs.filter(org=>org.id==grant.org_id
                        || grant.include_children==1 && org.path.startsWith(grant.org_path)).size()>0;
            if(!applies) {
                continue;
            }
            access.hasAccess=true;
            for(role in grantRoles.filter(item=>item.grant_id==grant.id)) {
                if(!access.roleIds.contains(role.id)) {
                    access.roleIds.add(role.id);
                }
                for(resource in roleResources.filter(item=>item.role_id==role.id && activeResources.contains(item.id))) {
                    if(!access.resourceIds.contains(resource.id)) {
                        access.resourceIds.add(resource.id);
                    }
                }
            }
        }
        Collections.sort(access.roleIds);
        Collections.sort(access.resourceIds);
    }
    result.put(id,access);
}
return result;
