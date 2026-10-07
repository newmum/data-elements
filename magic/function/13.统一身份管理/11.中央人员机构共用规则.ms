{
  "properties": {},
  "id": "6b934eef4c6756029a274852f9b15424",
  "script": null,
  "groupId": "5cc0c0d466b0571a9fe288ca74f0c356",
  "name": "11.中央人员机构共用规则",
  "createTime": 1790615423659,
  "updateTime": 1791216630680,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/directory",
  "description": "封装多处使用的人员、机构和任职资料校验与映射，保持中央目录与租户本地账号隔离。",
  "parameters": [
    {
      "name": "entity",
      "value": null,
      "description": "中央目录对象类别",
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
      "name": "operation",
      "value": null,
      "description": "本次执行的业务操作",
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
      "name": "payload",
      "value": null,
      "description": "请求资料或筛选条件",
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
// 共享函数：11.中央人员机构共用规则
// 业务说明：封装多处使用的人员、机构和任职资料校验与映射，保持中央目录与租户本地账号隔离。
// 函数输入：entity：中央目录对象类别；operation：本次执行的业务操作；payload：请求资料或筛选条件。
// 返回与副作用：封装多处使用的人员、机构和任职资料校验与映射，保持中央目录与租户本地账号隔离。
import db;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import java.lang.Long;
import java.time.Instant;
import java.time.LocalDate;
import '@/idaas/directory-scope' as directoryScope;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import '@/idaas/field-values' as fieldValues;
import '@/idaas/rebuild-projections' as rebuild;

// 结构化中央人员、机构、任职。全部写入同库事务，绝不创建应用本地凭据。
var p=payload||{};
var r=p.record||p;
var domain=r.domain||p.domain||'workforce';
var sql=db.normal();
if(!['subjects','orgs'].contains(entity)) {
    exit 400,'目录对象不正确';
}
var permission=(entity=='subjects'?'users':'orgs')+':'+(operation=='list'?'read':'write');
var rights=directoryScope(permission,domain);
var ctx=rights.ctx;
if(entity=='subjects' && operation!='list' && (r.initialPassword!=null || p.initialPassword!=null || r.password!=null || p.password!=null || r.existingAccount==true || p.existingAccount==true || r.linkExisting==true || p.linkExisting==true)) {
    exit 400,'中央人员资料不创建登录账号或设置密码';
}
var extensionViews={};
var appointmentsBySubject={};
var profilesBySubject={};
var allowed=(ids,scope)=>scope.all || (ids.size()>0 && ids.filter(id=>!scope.orgIds.contains(id)).size()==0);
var appointments=(id)=>sql.select("""
select a.*,a.tid as id,date_format(valid_from, '%Y-%m-%dT%H:%i:%sZ')
from iam_appointment_t a
where subject_id=#{id}
    and is_del=0
    and status='ACTIVE'
    and valid_from<=utc_timestamp(6)
    and (valid_until is null
         or valid_until>utc_timestamp(6))
order by is_primary desc,tid
""");
var dto=(row)=>{
    if(entity=='orgs') {
        return {
            id:row.id,domain:row.identity_domain,code:row.code,name:row.name,parentId:row.parent_id,sortNo:row.sort_no,leader:row.leader_subject_id||'',line:row.line_code||'',status:row.status=='ACTIVE'?'enabled':'disabled',version:row.version,createdAt:row.created_utc,updatedAt:row.updated_utc,path:row.path,depth:row.depth,ext:extensionViews.get(row.id)||{}
        };
    }
    var profile=profilesBySubject.get(row.id);
    var verified=profile!=null && profile.verification_status=='VERIFIED' && profile.result_status=='VERIFIED' && profile.fresh==1;
    var jobs=appointmentsBySubject.get(row.id)||[];
    var primary=jobs.filter(a=>a.is_primary==1);
    var main=primary.size()==0?null:primary[0];
    return {
        id:row.id,domain:row.identity_domain,name:row.name,subjectCode:row.subject_code,account:row.account_hint||'',email:row.email||'',phone:row.phone||'',employeeId:row.employee_no||'',employmentType:(row.employment_type||'EMPLOYEE').toLowerCase(),joinDate:row.join_date==null?'':row.join_date.toString(),status:row.status=='ACTIVE'?'enabled':'disabled',version:row.version,
        createdAt:row.created_utc,updatedAt:row.updated_utc,orgId:main==null?'':main.org_id,orgName:main==null?'':main.org_name||'',post:main==null?'':main.post_code||'',kind:row.identity_domain=='public'?'citizen':'person',locked:false,verified:verified,verificationStatus:profile==null?'UNVERIFIED':profile.verification_status=='VERIFIED' && !verified?'EXPIRED':profile.verification_status,history:[],
        appointments:jobs.map(a=>({
            id:a.id,orgId:a.org_id,post:a.post_code||'',position:a.position_code||'',rank:a.rank_code||'',title:a.title_code||'',primary:a.is_primary==1,version:a.version
        })),ext:extensionViews.get(row.id)||{}
    };
};
// 列表分支：先确定当前操作人可见的应用和身份域，再读取并映射业务记录。
if(operation=='list') {
    var q=(p.q||'').trim();
    if(q.length()>100) {
        exit 400,'查询文字过长';
    }
    var pageText=''+(p.page||'1');
    var sizeText=''+(p.size||'250');
    if(!pageText.matches('[0-9]{1,5}') || !sizeText.matches('[0-9]{1,4}')) {
        exit 400,'分页参数不正确';
    }
    var page=Long.parseLong(pageText);
    var size=Long.parseLong(sizeText);
    if(page<1 || size<1 || size>250) {
        exit 400,'分页参数不正确';
    }
    var table=entity=='subjects'?'iam_subject_t':'iam_org_t';
    var keyword='%'+q+'%';
    var requestedStatus=p.status||'all';
    if(!['all','enabled','disabled'].contains(requestedStatus)) {
        exit 400,'状态筛选不正确';
    }
    var wantedStatus=requestedStatus=='enabled'?'ACTIVE':requestedStatus=='disabled'?'DISABLED':'ALL';
    var requestedOrgId=entity=='subjects' && p.orgId!=null && p.orgId!='all'?''+p.orgId:'';
    if(requestedOrgId.length()>64) {
        exit 400,'机构筛选不正确';
    }
    var includeChildren=p.includeChildren==true || p.includeChildren=='true'?1:0;
    var requestedOrgPath='__none__';
    if(requestedOrgId!='' && includeChildren==1) {
        var requestedOrg=sql.selectOne("""
select path from iam_org_t
where tid=#{requestedOrgId} and identity_domain=#{domain} and is_del=0
""");
        if(requestedOrg!=null) {
            requestedOrgPath=requestedOrg.path;
        }
    }
    var offset=(page-1)*size;
    var allScope=rights.all?1:0;
    var scopeIds=rights.orgIds.size()>0?rights.orgIds:['__none__'];
    // Apply the same domain and organization scope to count and page queries.
    // A person is visible when an active appointment belongs to this scope.
    var filter=entity=='orgs'?"""
from iam_org_t t
where t.identity_domain=#{domain} and t.is_del=0 and t.name like #{keyword}
  and (#{wantedStatus}='ALL' or t.status=#{wantedStatus})
  and (#{allScope}=1 or t.tid in
    <foreach collection="scopeIds" item="scopeId" open="(" close=")" separator=",">#{scopeId}</foreach>)
""":"""
from iam_subject_t t
where t.identity_domain=#{domain} and t.is_del=0
  and (t.name like #{keyword} or t.account_hint like #{keyword} or t.email like #{keyword})
  and (#{wantedStatus}='ALL' or t.status=#{wantedStatus})
  and (#{requestedOrgId}='' or exists (
    select 1 from iam_appointment_t filterJob
    join iam_org_t filterOrg on filterOrg.tid=filterJob.org_id
    where filterJob.subject_id=t.tid and filterJob.is_del=0 and filterJob.status='ACTIVE'
      and filterJob.valid_from<=utc_timestamp(6)
      and (filterJob.valid_until is null or filterJob.valid_until>utc_timestamp(6))
      and (filterJob.org_id=#{requestedOrgId} or
        (#{includeChildren}=1 and filterOrg.path like concat(#{requestedOrgPath},'%')))
  ))
  and (#{allScope}=1 or exists (
    select 1 from iam_appointment_t a
    where a.subject_id=t.tid and a.is_del=0 and a.status='ACTIVE'
      and a.valid_from<=utc_timestamp(6)
      and (a.valid_until is null or a.valid_until>utc_timestamp(6))
      and a.org_id in
        <foreach collection="scopeIds" item="scopeId" open="(" close=")" separator=",">#{scopeId}</foreach>
  ))
""";
    var total=sql.selectOne('select count(*) as total '+filter).total;
    var list=sql.select("""
select t.*,t.tid as id,
       date_format(t.created_time,'%Y-%m-%dT%H:%i:%sZ') created_utc,
       date_format(t.updated_time,'%Y-%m-%dT%H:%i:%sZ') updated_utc
"""+filter+(entity=='orgs'?" order by t.sort_no,t.tid":" order by t.tid")+" limit #{size} offset #{offset}");
    if(entity=='subjects' && list.size()>0) {
        var subjectIds=list.map(row=>row.id);
        var jobs=sql.select("""
select a.*,a.tid as id,o.name org_name
from iam_appointment_t a
left join iam_org_t o on o.tid=a.org_id
where a.subject_id in
    <foreach collection="subjectIds" item="subjectId" open="(" close=")" separator=",">#{subjectId}</foreach>
  and a.is_del=0 and a.status='ACTIVE'
  and a.valid_from<=utc_timestamp(6)
  and (a.valid_until is null or a.valid_until>utc_timestamp(6))
order by a.subject_id,a.is_primary desc,a.tid
""");
        for(job in jobs) {
            var current=appointmentsBySubject.get(job.subject_id)||[];
            current.add(job);
            appointmentsBySubject.put(job.subject_id,current);
        }
        if(domain=='public') {
            var profiles=sql.select("""
select p.subject_id,p.verification_status,p.verification_id,
       v.status result_status,v.expires_at>utc_timestamp(6) fresh
from iam_public_profile_t p
left join iam_verification_t v on v.tid=p.verification_id
where p.subject_id in
    <foreach collection="subjectIds" item="subjectId" open="(" close=")" separator=",">#{subjectId}</foreach>
""");
            for(profile in profiles) {
                profilesBySubject.put(profile.subject_id,profile);
            }
        }
    }
    if(list.size()>0) {
        extensionViews=fieldValues('view-many',entity=='orgs'?'ORG':'SUBJECT',domain,null,list.map(row=>({
            id:row.id,values:row.ext_json==null?{}:JSONUtil.parseObj(row.ext_json)
        })),null);
    }
    return {
        list:list.map(dto),total:total,page:page,size:size
    };
}
// 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
// 事务边界：通过共享幂等写入统一处理版本冲突、提交与审计。
return mutate(ctx,entity+':'+operation,p,()=>{
    var fresh=directoryScope(permission,domain);
    var id=r.id||p.id;
    var table=entity=='subjects'?'iam_subject_t':'iam_org_t';
    var old=id==null?null:sql.selectOne('select *,tid as id from '+table+' where tid=#{id} and is_del=0 for update');
    if(old==null && p.creating!=true) {
        exit 404,'目录记录不存在';
    }
    if(old!=null && (old.identity_domain!=domain || old.version!=(r.version||p.version))) {
        exit 409,'目录记录身份域或版本已变更';
    }
    var originalOrgs=old==null?[]:entity=='orgs'?[id]:appointments(id).map(a=>a.org_id);
    if(old!=null && !allowed(originalOrgs,fresh)) {
        exit 403,'记录超出中央资料管理范围';
    }
    // 归档分支：验证版本及引用关系，保留历史记录并撤销有效关联。
    if(operation=='remove') {
        if(entity=='orgs') {
            if(sql.selectOne("""
select count(*) n
from iam_org_t
where parent_id=#{id}
    and is_del=0
""").n>0 || sql.selectOne("""
select count(*) n
from iam_appointment_t
where org_id=#{id}
    and is_del=0
    and status='ACTIVE'
""").n>0 || sql.selectOne("""
select count(*) n
from iam_app_org_t
where org_id=#{id}
    and status='ACTIVE'
""").n>0 || sql.selectOne("""
select count(*) n
from iam_operator_scope_t
where org_id=#{id}
""").n>0) {
                exit 409,'机构仍有下级、任职、分配或管理范围引用';
            }
        }
        else {
            if(sql.selectOne("""
select count(*) n
from iam_org_t
where leader_subject_id=#{id}
    and is_del=0
""").n>0 || sql.selectOne("""
select count(*) n
from iam_operator_t
where subject_id=#{id}
    and is_del=0
""").n>0) {
                exit 409,'人员仍被负责人或平台账号引用';
            }
            sql.update("""
update iam_appointment_t
set status='ENDED',version=version+1,updated_time=utc_timestamp(6)
where subject_id=#{id}
    and status='ACTIVE'
""");
            sql.update("""
update iam_subject_app_t
set desired_enabled=0,desired_version=desired_version+1,version=version+1,
        updated_time=utc_timestamp(6)
where subject_id=#{id}
""");
        }
        sql.update('update '+table+" set status='ARCHIVED',is_del=1,version=version+1,updated_by=#{ctx.userId},updated_time=utc_timestamp(6) where tid=#{id}");
        if(sql.selectOne("""
select count(*) n
from information_schema.tables
where table_schema=database()
    and table_name='iam_access_grant_t'
""").n>0) {
            rebuild(null,domain);
        }
        audit(ctx,'SUCCESS',entity+':remove',id,null,{
            id:id
        });
        return {
            id:id
        };
    }
    if(operation!='save') {
        exit 400,'目录操作不正确';
    }
    var name=(r.name||'').trim();
    var status=r.status=='disabled' || r.status=='DISABLED'?'DISABLED':'ACTIVE';
    if(name=='' || name.length()>(entity=='subjects'?100:200)) {
        exit 400,'名称不正确';
    }
    if(entity=='orgs') {
        var parentId=r.parentId||null;
        var parent=parentId==null?null:sql.selectOne("""
select *,tid as id
from iam_org_t
where tid=#{parentId}
    and identity_domain=#{domain}
    and is_del=0
    and status='ACTIVE'
""");
        if(parentId!=null && parent==null) {
            exit 400,'上级机构不存在、停用或跨域';
        }
        if((parentId!=null && parentId==id) || (old!=null && parent!=null && parent.path.startsWith(old.path))) {
            exit 400,'机构不能移入自身或下级';
        }
        if((old==null || parentId!=old.parent_id) && !allowed(parent==null?[]:[parent.id],fresh)) {
            exit 403,'上级机构超出管理范围';
        }
        var code=(r.code||'').trim();
        if(!code.matches('[A-Za-z0-9][A-Za-z0-9_.-]{1,99}')) {
            exit 400,'机构编码不正确';
        }
        if(old!=null && code!=old.code) {
            exit 400,'机构编码保持稳定';
        }
        if(old==null && sql.selectOne("""
select tid as id
from iam_org_t
where identity_domain=#{domain}
    and code=#{code}
""")!=null) {
            exit 409,'机构编码已使用';
        }
        var leader=r.leader||null;
        var line=r.line||null;
        if(leader!=null && sql.selectOne("""
select tid as id
from iam_subject_t
where tid=#{leader}
    and identity_domain=#{domain}
    and is_del=0
    and status='ACTIVE'
""")==null) {
            exit 400,'负责人须为同域有效人员';
        }
        if(line!=null && sql.selectOne("""
select tid as id
from iam_dict_item_t
where identity_domain=#{domain}
    and category='LINE'
    and code=#{line}
    and is_del=0
    and status='ACTIVE'
""")==null) {
            exit 400,'条线须选择有效字典';
        }
        if(old==null) {
            id=IdUtil.fastSimpleUUID();
        }
        var path=(parent==null?'/':parent.path)+id+'/';
        var depth=parent==null?0:parent.depth+1;
        if(path.length()>2000 || depth>50) {
            exit 400,'机构层级超限';
        }
        if(old!=null) {
            var children=sql.select("""
select tid as id,path
from iam_org_t
where path like concat(#{old.path}, '%')
    and tid<>#{id}
    and is_del=0
""");
            if(!fresh.all && children.filter(o=>!fresh.orgIds.contains(o.id)).size()>0) {
                exit 403,'移动机构涉及超出范围的下级';
            }
            for(child in children) {
                var newPath=path+child.path.substring(old.path.length());
                if(newPath.length()>2000) {
                    exit 400,'下级机构路径超限';
                }
            }
            sql.update("""
update iam_org_t
set path=concat(#{path}, substring(path,
        length(#{old.path})+1)),depth=depth+#{depth}-#{old.depth},version=version+1,
        updated_time=utc_timestamp(6)
where path like concat(#{old.path}, '%')
    and tid<>#{id}
    and is_del=0
""");
            sql.update("""
update iam_org_t
set name=#{name},parent_id=#{parentId},path=#{path},depth=#{depth},
        leader_subject_id=#{leader},line_code=#{line},
        status=#{status},version=version+1,
        updated_by=#{ctx.userId},
        updated_time=utc_timestamp(6)
where tid=#{id}
""");
        }
        else {
            sql.update("""
insert into iam_org_t(tid,
                identity_domain, code, name, parent_id, path, depth, leader_subject_id, line_code, status,
                created_by, updated_by)
values(#{id},#{domain},#{code},#{name},#{parentId},#{path},#{depth},#{leader},#{line},#{status},#{ctx.userId},#{ctx.userId})
""");
        }
    }
    else {
        var jobs=r.appointments||[];
        if(jobs.size()>30) {
            exit 400,'任职数量过多';
        }
        var primary=jobs.filter(a=>a.primary==true);
        if(jobs.size()>0 && primary.size()!=1) {
            exit 400,'任职必须且只能有一个主职';
        }
        var orgIds=[];
        var checked=[];
        for(a in jobs) {
            var org=sql.selectOne("""
select tid as id
from iam_org_t
where tid=#{a.orgId}
    and identity_domain=#{domain}
    and is_del=0
    and status='ACTIVE'
""");
            if(org==null || orgIds.contains(a.orgId)) {
                exit 400,'任职机构无效、跨域或重复';
            }
            orgIds.add(a.orgId);
            for(kind in ['POST','POSITION','RANK','TITLE']) {
                var value=kind=='POST'?a.post:kind=='POSITION'?a.position:kind=='RANK'?a.rank:a.title;
                if(value!=null && value!='' && sql.selectOne("""
select tid as id
from iam_dict_item_t
where identity_domain=#{domain}
    and category=#{kind}
    and code=#{value}
    and status='ACTIVE'
    and is_del=0
""")==null) {
                    exit 400,'岗位职务须使用有效字典编码';
                }
            }
            checked.add(a);
        }
        if(!allowed(orgIds,fresh)) {
            exit 403,'拟任职机构超出中央资料管理范围';
        }
        var hint=(r.account||'').trim();
        if(hint.length()>100 || (hint!='' && !hint.matches('[A-Za-z][A-Za-z0-9_.-]{2,99}'))) {
            exit 400,'拟用账号不正确';
        }
        var code=old==null?(r.subjectCode||'SUB_'+IdUtil.fastSimpleUUID()):old.subject_code;
        if(!code.matches('[A-Za-z0-9][A-Za-z0-9_.-]{1,63}')) {
            exit 400,'人员编号不正确';
        }
        var phone=r.phone||null;
        var email=r.email||null;
        var employee=r.employeeId||null;
        var employment=(r.employmentType||'employee').toUpperCase();
        var join=r.joinDate||null;
        if((phone!=null && phone.length()>256) || (email!=null && email.length()>512) || (employee!=null && employee.length()>100) || !['EMPLOYEE','PARTNER','TEMPORARY'].contains(employment)) {
            exit 400,'人员资料不正确';
        }
        try {
            if(join!=null) {
                LocalDate.parse(join);
            }
        }
        catch(e) {
            exit 400,'入职日期不正确';
        }
        if(old==null) {
            if(sql.selectOne("""
select tid as id
from iam_subject_t
where identity_domain=#{domain}
    and subject_code=#{code}
""")!=null) {
                exit 409,'人员编号已使用';
            }
            id=IdUtil.fastSimpleUUID();
            sql.update("""
insert into iam_subject_t(tid,
                identity_domain, subject_code, account_hint, name, phone, email, employee_no, employment_type,
                join_date, status, created_by, updated_by)
values(#{id},#{domain},#{code},#{hint},#{name},#{phone},#{email},#{employee},#{employment},#{join},#{status},#{ctx.userId},#{ctx.userId})
""");
        }
        else {
            sql.update("""
update iam_subject_t
set name=#{name},account_hint=#{hint},phone=#{phone},email=#{email},
    employee_no=#{employee},employment_type=#{employment},join_date=#{join},status=#{status},
    version=version+1,updated_by=#{ctx.userId},updated_time=utc_timestamp(6)
where tid=#{id}
""");
        }
        if(domain=='public' && old!=null && old.name!=name && sql.selectOne("""
select table_name
from information_schema.tables
where table_schema=database()
    and table_name='iam_public_profile_t'
""")!=null) {
            sql.update("""
update iam_public_profile_t
set verification_status='UNVERIFIED',verification_id=null,verified_time=null,version=version+1
where subject_id=#{id}
""");
        }
        var current=sql.select("""
select *,tid as id
from iam_appointment_t
where subject_id=#{id}
    and is_del=0
    and status='ACTIVE'
""");
        sql.update("""
update iam_appointment_t
set status='ENDED',version=version+1,updated_time=utc_timestamp(6)
where subject_id=#{id}
    and status='ACTIVE'
""");
        for(a in checked) {
            var prior=current.filter(j=>j.org_id==a.orgId && (j.post_code||'')==(a.post||'') && (j.position_code||'')==(a.position||'') && (j.rank_code||'')==(a.rank||'') && (j.title_code||'')==(a.title||''));
            var aid=prior.size()>0?prior[0].id:IdUtil.fastSimpleUUID();
            var isPrimary=a.primary==true?1:0;
            if(prior.size()>0) {
                sql.update("""
update iam_appointment_t
set status='ACTIVE',is_primary=#{isPrimary},version=version+1,updated_time=utc_timestamp(6)
where tid=#{aid}
""");
            }
            else {
                sql.update("""
insert into iam_appointment_t(tid,
                subject_id, org_id, post_code, position_code, rank_code, title_code, is_primary, status,
                valid_from)
values(#{aid},#{id},#{a.orgId},#{a.post},#{a.position},#{a.rank},#{a.title},#{isPrimary},'ACTIVE',utc_timestamp(6))
""");
            }
        }
        sql.update("""
update iam_subject_app_t
set desired_enabled=case
        when #{status}='ACTIVE'
        and assignment_mode='GRANT_DERIVED' then 1 else 0
                    end,desired_version=desired_version+1,version=version+1,
        updated_time=utc_timestamp(6)
where subject_id=#{id}
""");
    }
    var ext=fieldValues('encode',entity=='subjects'?'SUBJECT':'ORG',domain,id,r.ext||{},old==null || old.ext_json==null?{}:JSONUtil.parseObj(old.ext_json));
    var encoded=JSONUtil.toJsonStr(ext);
    sql.update('update '+table+' set ext_json=#{encoded} where tid=#{id}');
    if(sql.selectOne("""
select count(*) n
from information_schema.tables
where table_schema=database()
    and table_name='iam_access_grant_t'
""").n>0) {
        rebuild(null,domain);
    }
    audit(ctx,'SUCCESS',entity+':save',id,null,{
        id:id,status:status
    });
    var saved=sql.selectOne('select version from '+table+' where tid=#{id}');
    return {
        id:id,version:saved.version
    };
});
