{
  "properties": {},
  "id": "70125b723bc452648ef74d6ec085f2ca",
  "script": null,
  "groupId": "8e1839fbe6a55ce7aafaae3dcf2d15b4",
  "name": "09.确认导入应用目录",
  "createTime": 1790848802311,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/directory-pull",
  "description": "根据用户确认的差异项把租户人员、机构、角色和资源定义导入控制库，校验应用范围与版本并返回结果；不复制租户密码。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：09.确认导入应用目录
// 路由：POST /idaas/applications/directory-pull
// 业务说明：根据用户确认的差异项把租户人员、机构、角色和资源定义导入控制库，校验应用范围与版本并返回结果；不复制租户密码。
import db;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import '@/idaas/local-inventory' as localInventory;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import identityRuntime;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    // 请求参数与本接口业务处理。
    var operation = 'pull';
    var payload = body||{};

    // 此流程仅由明确的接口请求触发，不在后台自动扫描或下发租户数据。
    // 人工对比和按所选差异导入。不得同步账号密码、本地角色、本地授权或自动执行。
    var p=payload||{};
    var appId=''+(p.appId||'');
    var domain='workforce';
    if(!appId.matches('[A-Za-z0-9_.:-]{1,64}')) {
        exit 400,'应用不正确';
    }
    // 核验会话的功能权限及对象范围，超出范围的请求在读取或写入前拒绝。
    guard('apps:read',appId,domain);
    var local=localInventory('live',{
        appId:appId,domain:domain
    });
    if(!local.available) {
        return {
            available:false,reason:local.reason
        };
    }
    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var people=sql.select("""
select s.tid as id,s.name,s.account_hint account,a.account_alias appAccount,s.status,s.version
from iam_subject_app_t a
join iam_subject_t s on s.tid=a.subject_id
and s.identity_domain='workforce'
and s.is_del=0
where a.app_id=#{appId}
    and a.directory_assigned=1
order by s.tid
""");
    var orgs=sql.select("""
select o.tid as id,o.name,o.code,o.parent_id parentId,o.status,o.version
from iam_app_org_t a
join iam_org_t o on o.tid=a.org_id
and o.identity_domain='workforce'
and o.is_del=0
where a.app_id=#{appId}
    and a.status='ACTIVE'
order by o.depth,o.tid
""");
    var links=sql.select("""
select object_type,local_id,central_id
from iam_tenant_directory_link_t
where app_id=#{appId}
""");
    var match=(type,id,central)=>{
        var mapped=links.filter(x=>x.object_type==type && x.local_id==id);
        if(mapped.size()>0) {
            var found=central.filter(x=>x.id==mapped[0].central_id);
            return found.size()>0?found[0]:null;
        }
        // Historical 公安初始化保留了源 ID；仅在该应用已经正式分配的资料中承认同 ID。
        var same=central.filter(x=>x.id==id);
        return same.size()>0?same[0]:null;
    };
    var compare=(type,localRows,centralRows)=>localRows.map(row=>{
        var target=match(type,row.id,centralRows);
        var changed=target!=null && (target.name!=row.name || (target.status=='ACTIVE'?'enabled':'disabled')!=row.status || (type=='SUBJECT' && (target.appAccount||target.account||'')!=(row.account||'')));
        return {
            type:type,localId:row.id,localName:row.name,localCode:type=='ORG'?row.code:row.account,
            centralId:target==null?null:target.id,centralName:target==null?null:target.name,
            state:target==null?'local_only':changed?'different':'same'
        };
    });
    var localPeople=compare('SUBJECT',local.users,people);
    var localOrgs=compare('ORG',local.orgs,orgs);
    var mappedPeople=localPeople.filter(x=>x.centralId!=null).map(x=>x.centralId);
    var mappedOrgs=localOrgs.filter(x=>x.centralId!=null).map(x=>x.centralId);
    var centralOnly={
        users:people.filter(x=>!mappedPeople.contains(x.id)).map(x=>({
            id:x.id,name:x.name,code:x.account||''
        })),
        orgs:orgs.filter(x=>!mappedOrgs.contains(x.id)).map(x=>({
            id:x.id,name:x.name,code:x.code
        }))
    };
    var revision=DigestUtil.sha256Hex(JSONUtil.toJsonStr({
        localUsers:local.users,localOrgs:local.orgs,people:people,orgs:orgs,links:links
    }));
    if(operation!='pull') {
        exit 400,'操作不正确';
    }
    guard('users:write',appId,domain);
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard('orgs:write',appId,domain);
    var selected=p.items||[];
    if(selected.size()<1 || selected.size()>500) {
        exit 400,'请选择 1 至 500 条人员或机构差异';
    }
    if(p.revision!=revision) {
        exit 409,'租户或平台资料已变化，请重新查看差异';
    }
    var chosenPeople=selected.filter(x=>x.type=='SUBJECT');
    var chosenOrgs=selected.filter(x=>x.type=='ORG');
    if(chosenPeople.size()+chosenOrgs.size()!=selected.size()) {
        exit 400,'对象类型不正确';
    }
    var selectionKeys=selected.map(x=>x.type+':'+x.localId);
    if(selectionKeys.distinct().size()!=selected.size()) {
        exit 400,'所选资料重复';
    }
    for(item in selected) {
        var rows=item.type=='SUBJECT'?local.users:local.orgs;
        if(rows.filter(x=>x.id==item.localId).size()!=1) {
            exit 400,'所选租户资料不存在';
        }
    }
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,'tenant-directory:pull',p,()=>{
        guard('users:write',appId,domain);
        guard('orgs:write',appId,domain);
        var createdOrgs=[];
        var createdPeople=[];
        var updatedOrgs=[];
        var updatedPeople=[];
        var ensureOrg=(localId,depth)=>{
            if(depth>50) {
                exit 409,'本地机构层级超过 50 层';
            }
            var sourceRows=local.orgs.filter(x=>x.id==localId);
            if(sourceRows.size()!=1) {
                return null;
            }
            var source=sourceRows[0];
            var existing=match('ORG',localId,orgs);
            if(existing!=null) {
                return existing.id;
            }
            var parentId=source.parentId==null || source.parentId=='ROOT'?null:ensureOrg(source.parentId,depth+1);
            var parent=parentId==null?null:sql.selectOne("""
select path,depth
from iam_org_t
where tid=#{parentId}
    and is_del=0
""");
            var centralId=IdUtil.fastSimpleUUID();
            var linkId=IdUtil.fastSimpleUUID();
            var candidate=(source.code||'').trim();
            if(!candidate.matches('[A-Za-z0-9][A-Za-z0-9_.-]{1,99}') || sql.selectOne("""
select tid as id
from iam_org_t
where identity_domain='workforce'
    and code=#{candidate}
""")!=null) {
                candidate='TO_'+DigestUtil.sha256Hex(appId+':'+localId).substring(0,60);
            }
            var path=(parent==null?'/':parent.path)+centralId+'/';
            var level=parent==null?0:parent.depth+1;
            var status=source.status=='enabled'?'ACTIVE':'DISABLED';
            sql.update("""
insert into iam_org_t(tid,
                identity_domain, code, name, parent_id, path, depth, status, created_by, updated_by)
values(#{centralId},'workforce',#{candidate},#{source.name},#{parentId},#{path},#{level},#{status},#{ctx.userId},#{ctx.userId})
""");
            var assignmentId=IdUtil.fastSimpleUUID();
            sql.update("""
insert into iam_app_org_t(tid, app_id, org_id, include_children, status)
values(#{assignmentId},#{appId},#{centralId},0,'ACTIVE')
""");
            sql.update("""
insert into iam_tenant_directory_link_t(tid,
                app_id, object_type, local_id, central_id)
values(#{linkId},#{appId},'ORG',#{localId},#{centralId})
""");
            orgs.add({
                id:centralId,name:source.name,code:candidate,status:status
            });
            links.add({
                object_type:'ORG',local_id:localId,central_id:centralId
            });
            createdOrgs.add(centralId);
            return centralId;
        };
        for(item in chosenOrgs) {
            var source=local.orgs.filter(x=>x.id==item.localId)[0];
            var target=match('ORG',source.id,orgs);
            if(target==null) {
                ensureOrg(source.id,0);
            }
            else {
                var targetId=target.id;
                var status=source.status=='enabled'?'ACTIVE':'DISABLED';
                if(target.name!=source.name || target.status!=status) {
                    sql.update("""
update iam_org_t
set name=#{source.name},status=#{status},version=version+1,
        updated_by=#{ctx.userId},
        updated_time=utc_timestamp(6)
where tid=#{targetId}
""");
                    updatedOrgs.add(targetId);
                }
            }
        }
        for(item in chosenPeople) {
            var source=local.users.filter(x=>x.id==item.localId)[0];
            var target=match('SUBJECT',source.id,people);
            var status=source.status=='enabled'?'ACTIVE':'DISABLED';
            var centralId=target==null?IdUtil.fastSimpleUUID():target.id;
            if(target==null) {
                var code='TU_'+DigestUtil.sha256Hex(appId+':'+source.id).substring(0,60);
                sql.update("""
insert into iam_subject_t(tid,
                identity_domain, subject_code, account_hint, name, status, created_by, updated_by)
values(#{centralId},'workforce',#{code},#{source.account},#{source.name},#{status},#{ctx.userId},#{ctx.userId})
""");
                var assignmentId=IdUtil.fastSimpleUUID();
                sql.update("""
insert into iam_subject_app_t(tid,
                subject_id, app_id, account_alias, directory_assigned, assignment_mode, assignment_status,
                desired_enabled)
values(#{assignmentId},#{centralId},#{appId},#{source.account},1,'DIRECTORY_ONLY','ACTIVE',0)
""");
                var linkId=IdUtil.fastSimpleUUID();
                sql.update("""
insert into iam_tenant_directory_link_t(tid,
                app_id, object_type, local_id, central_id)
values(#{linkId},#{appId},'SUBJECT',#{source.id},#{centralId})
""");
                createdPeople.add(centralId);
            }
            else if(target.name!=source.name || (target.appAccount||target.account||'')!=(source.account||'') || target.status!=status) {
                sql.update("""
update iam_subject_t
set name=#{source.name},status=#{status},version=version+1,
        updated_by=#{ctx.userId},
        updated_time=utc_timestamp(6)
where tid=#{centralId}
""");
                sql.update("""
update iam_subject_app_t
set account_alias=#{source.account},desired_version=desired_version+1,version=version+1,
        updated_time=utc_timestamp(6)
where app_id=#{appId}
    and subject_id=#{centralId}
""");
                updatedPeople.add(centralId);
            }
            // New personnel get their organization membership. Existing central appointments remain untouched.
            if(target==null) {
                var primary=true;
                for(localOrgId in source.orgIds||[]) {
                    var orgId=ensureOrg(localOrgId,0);
                    if(orgId==null) {
                        continue;
                    }
                    var appointmentId=IdUtil.fastSimpleUUID();
                    var isPrimary=primary?1:0;
                    primary=false;
                    sql.update("""
insert into iam_appointment_t(tid,
                subject_id, org_id, is_primary, status, valid_from)
values(#{appointmentId},#{centralId},#{orgId},#{isPrimary},'ACTIVE',utc_timestamp(6))
""");
                }
            }
        }
        // 记录操作主体、目标及结果，供审计与故障追踪。
        audit(ctx,'SUCCESS','tenant-directory:pull',appId,null,{
            createdUsers:createdPeople.size(),updatedUsers:updatedPeople.size(),createdOrgs:createdOrgs.size(),updatedOrgs:updatedOrgs.size()
        });
        return {
            createdUsers:createdPeople.size(),updatedUsers:updatedPeople.size(),createdOrgs:createdOrgs.size(),updatedOrgs:updatedOrgs.size()
        };
    });
}));
