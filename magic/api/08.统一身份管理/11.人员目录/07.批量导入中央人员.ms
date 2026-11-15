{
  "properties": {},
  "id": "29012d6e76a753cfa2d44cc5d086165c",
  "script": null,
  "groupId": "b3202123a27e56198aed6211ec0f770f",
  "name": "07.批量导入中央人员",
  "createTime": 1791193400395,
  "updateTime": 1791194872495,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/import",
  "description": "单次最多导入 500 人；批量校验机构、岗位、身份域和操作人范围，一次写入人员与任职档案，不创建登录凭据。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：批量导入中央人员档案（不创建登录凭据）
// 路由：POST /idaas/users/import
import db;
import identityRuntime;
import cn.hutool.core.util.IdUtil;
import '@/idaas/api-call' as apiCall;
import '@/idaas/directory-scope' as directoryScope;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;

return identityRuntime.control(()=>apiCall(()=>{
    var p=body||{};
    var domain=p.domain||'workforce';
    var rights=directoryScope('users:write',domain);
    var rows=p.rows||[];
    if(rows.size()<1 || rows.size()>500) {
        exit 400,'每次可导入 1 至 500 人';
    }
    var sql=db.normal();
    return mutate(rights.ctx,'users:import',p,()=>{
        var fresh=directoryScope('users:write',domain);
        var codes=rows.map(row=>(row.orgCode||'').trim()).filter(code=>code!='').distinct();
        var posts=rows.map(row=>(row.post||'').trim()).filter(code=>code!='').distinct();
        var orgs=codes.size()==0?[]:sql.select("""
select tid as id,code
from iam_org_t
where identity_domain=#{domain} and is_del=0 and status='ACTIVE'
  and code in <foreach collection="codes" item="code" open="(" close=")" separator=",">#{code}</foreach>
""");
        var validPosts=posts.size()==0?[]:sql.select("""
select code
from iam_dict_item_t
where identity_domain=#{domain} and category='POST' and is_del=0 and status='ACTIVE'
  and code in <foreach collection="posts" item="post" open="(" close=")" separator=",">#{post}</foreach>
""");
        var accounts=[];
        var prepared=[];
        var jobs=[];
        for(row in rows) {
            var name=(row.name||'').trim();
            var account=(row.account||'').trim();
            var email=(row.email||'').trim();
            var phone=(row.phone||'').trim();
            var orgCode=(row.orgCode||'').trim();
            var post=(row.post||'').trim();
            if(name=='' || name.length()>100 || !account.matches('[A-Za-z][A-Za-z0-9_.-]{2,99}')
                || email.length()>512 || phone.length()>256 || accounts.contains(account)) {
                exit 400,'CSV 中存在无效或重复的人员资料';
            }
            accounts.add(account);
            var matched=orgs.filter(org=>org.code==orgCode);
            if(domain=='workforce' && matched.size()!=1 || domain=='public' && orgCode!='' && matched.size()!=1) {
                exit 400,'CSV 机构编码不存在或未启用';
            }
            if(post!='' && validPosts.filter(item=>item.code==post).size()!=1) {
                exit 400,'CSV 岗位编码无效';
            }
            if(!fresh.all && (matched.size()!=1 || !fresh.orgIds.contains(matched[0].id))) {
                exit 403,'CSV 人员超出当前机构管理范围';
            }
            var id=IdUtil.fastSimpleUUID();
            prepared.add({id:id,code:'SUB_'+id,name:name,account:account,email:email,phone:phone});
            if(matched.size()==1) {
                jobs.add({id:IdUtil.fastSimpleUUID(),subjectId:id,orgId:matched[0].id,post:post});
            }
        }
        sql.update("""
insert into iam_subject_t(tid,identity_domain,subject_code,account_hint,name,email,phone,
    employment_type,status,created_by,updated_by)
values <foreach collection="prepared" item="row" open="(" close=")" separator="),(">
    #{row.id},#{domain},#{row.code},#{row.account},#{row.name},#{row.email},#{row.phone},
     'EMPLOYEE','ACTIVE',#{fresh.ctx.userId},#{fresh.ctx.userId}
</foreach>
""");
        if(jobs.size()>0) {
            sql.update("""
insert into iam_appointment_t(tid,subject_id,org_id,post_code,is_primary,status,valid_from)
values <foreach collection="jobs" item="job" open="(" close=")" separator="),(">
    #{job.id},#{job.subjectId},#{job.orgId},#{job.post},1,'ACTIVE',utc_timestamp(6)
</foreach>
""");
        }
        audit(fresh.ctx,'SUCCESS','users:import',null,null,{count:prepared.size()});
        return {count:prepared.size()};
    });
}));
