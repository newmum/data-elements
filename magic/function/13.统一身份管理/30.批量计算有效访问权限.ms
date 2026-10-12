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
import java.util.List;

var sql=db.normal();
var domain=identityDomain||'workforce';
var ids=(subjectIds||[]).distinct();
if(ids.size()>1000) {
    exit 400,'每次最多计算 1000 人';
}
var result={};
var multiple=appId instanceof List;
var appIds=(multiple?appId:[appId]).distinct();
if(appIds.size()>1000) {exit 400,'每次最多读取 1000 个应用';}
if(appIds.size()==0) {for(id in ids) {result.put(id,[])};return result;}
if(ids.size()==0) {
    return result;
}
var apps=sql.select("""
select tid as id,identity_domains
from iam_application_t
where tid IN (#{appIds}) and kind='BUSINESS' and is_del=0 and status='ACTIVE'
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
select m.subject_id,g.tid as id,g.name
from iam_group_member_t m
join iam_permission_group_t g on g.tid=m.group_id and g.identity_domain=#{domain}
  and g.status='ACTIVE' and g.is_del=0
where m.subject_id in <foreach collection="ids" item="id" open="(" close=")" separator=",">#{id}</foreach>
  and m.status='ACTIVE' and m.valid_from<=utc_timestamp(6)
  and (m.valid_until is null or m.valid_until>utc_timestamp(6))
""");
var grants=sql.select("""
select g.*,g.tid as id,o.path org_path,o.name org_name,o.status org_status,o.is_del org_deleted
from iam_access_grant_t g
left join iam_org_t o on o.tid=g.org_id
where g.app_id IN (#{appIds}) and g.identity_domain=#{domain} and g.status='ACTIVE'
  and g.is_del=0 and g.valid_from<=utc_timestamp(6)
  and (g.valid_until is null or g.valid_until>utc_timestamp(6))
""");
var grantRoles=sql.select("""
select rr.grant_id,rr.app_id,r.tid as id
from iam_grant_role_rela_t rr
join iam_role_t r on r.tid=rr.role_id and r.app_id=rr.app_id
  and r.identity_domain=#{domain} and r.status='ACTIVE' and r.is_del=0
where rr.app_id IN (#{appIds})
""");
var roleResources=sql.select("""
select rr.role_id,rr.app_id,r.tid as id
from iam_role_resource_rela_t rr
join iam_resource_t r on r.tid=rr.resource_id and r.app_id=rr.app_id
  and r.identity_domain=#{domain} and r.status='ACTIVE' and r.is_del=0
where rr.app_id IN (#{appIds})
""");
// One resource-tree read for all requested applications; ancestors are checked in memory.
var resourceRows=sql.select("""
SELECT tid as id,app_id,parent_id,status,is_del FROM iam_resource_t
WHERE app_id IN (#{appIds}) AND identity_domain=#{domain}
""");
var resourceById={};
for(row in resourceRows) {resourceById.put(row.id,row)}
var enabledResource=(id,applicationId)=>{
    var visited=[];
    var current=id;
    while(current!=null && current!='') {
        if(visited.contains(current) || visited.size()>100) {return false;}
        visited.add(current);
        var row=resourceById.get(current);
        if(row==null || row.app_id!=applicationId || row.status!='ACTIVE' || row.is_del!=0) {return false;}
        current=row.parent_id;
    }
    return true;
};
for(id in ids) {
    var allAccess=[];
    for(applicationId in appIds) {
        var app=apps.filter(row=>row.id==applicationId);
        var appActive=app.size()==1 && JSONUtil.parseArray(app[0].identity_domains).contains(domain);
        var access={appId:applicationId,hasAccess:false,roleIds:[],resourceIds:[],sources:[]};
        if(appActive && people.filter(person=>person.id==id).size()==1) {
            var subjectOrgs=orgs.filter(org=>org.subject_id==id);
            var subjectGroups=groups.filter(group=>group.subject_id==id);
            for(grant in grants.filter(row=>row.app_id==applicationId)) {
                var applies=grant.principal_type=='SUBJECT' && grant.subject_id==id
                    || grant.principal_type=='GROUP' && subjectGroups.filter(group=>group.id==grant.group_id).size()>0
                    || grant.principal_type=='ORG' && grant.org_status=='ACTIVE' && grant.org_deleted==0
                        && subjectOrgs.filter(org=>org.id==grant.org_id
                            || grant.include_children==1 && org.path.startsWith(grant.org_path)).size()>0;
                if(!applies) {continue;}
                access.hasAccess=true;
                var group=subjectGroups.filter(row=>row.id==grant.group_id);
                access.sources.add({grantId:grant.id,source:grant.principal_type=='SUBJECT'?'user':grant.principal_type.toLowerCase(),label:grant.principal_type=='SUBJECT'?'直接授权':grant.principal_type=='ORG'?'机构授权：'+grant.org_name:'权限组授权：'+(group.size()>0?group[0].name:''),subjectId:grant.subject_id||grant.org_id||grant.group_id});
                for(role in grantRoles.filter(item=>item.grant_id==grant.id && item.app_id==applicationId)) {
                    if(!access.roleIds.contains(role.id)) {access.roleIds.add(role.id);}
                    for(resource in roleResources.filter(item=>item.role_id==role.id && item.app_id==applicationId)) {
                        if(!access.resourceIds.contains(resource.id) && enabledResource(resource.id,applicationId)) {access.resourceIds.add(resource.id);}
                    }
                }
            }
            Collections.sort(access.roleIds);Collections.sort(access.resourceIds);
        }
        if(!multiple || access.hasAccess) {allAccess.add(access);}
    }
    result.put(id,multiple?allAccess:allAccess[0]);
}
return result;
