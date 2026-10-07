{
  "properties": {},
  "id": "a7aa191e975b505e96298e4aacfb365f",
  "script": null,
  "groupId": "039920aa11d55b4faed04ad7b00bbf94",
  "name": "02.分页查询待核验申请",
  "createTime": 1791193400395,
  "updateTime": 1791205192024,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/requests",
  "description": "仅有操作账号或认证账号写入权限的管理员可按身份域查看待核验申请。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：分页查询待管理员核验的密码找回申请。
// 路由：GET /idaas/password-recovery/requests
import db;
import identityRuntime;
import request;
import java.lang.Long;
import '@/idaas/api-call' as apiCall;
import '@/idaas/guard' as guard;

return identityRuntime.control(()=>apiCall(()=>{
    var param=(key,fallback)=>{
        var values=request.getValues(key);
        return values==null || values.size()==0?fallback:values[0];
    };
    var realm=param('realm','platform');
    var domain=realm=='platform'?'workforce':param('domain','public');
    var pageText=param('page','1');
    var sizeText=param('size','20');
    if(!['platform','auth-account'].contains(realm) || !['workforce','public'].contains(domain)
        || !pageText.matches('[0-9]{1,5}') || !sizeText.matches('[0-9]{1,3}')) {
        exit 400,'申请查询参数不正确';
    }
    var page=Long.parseLong(pageText);
    var size=Long.parseLong(sizeText);
    if(page<1 || size<1 || size>100) {
        exit 400,'分页范围不正确';
    }
    guard(realm=='platform'?'operators:write':'auth-accounts:write',null,domain);
    var sql=db.normal();
    var kind=realm=='platform'?'OPERATOR':'AUTH_ACCOUNT';
    var resetAction=realm=='platform'?'operators:reset-password':'auth-accounts:reset';
    var table=realm=='platform'?'iam_operator_profile_v':'iam_auth_account_t';
    var nameColumn=realm=='platform'?'a.display_name':'person.name';
    var filter="""
from iam_audit_event_t e
join """+table+""" a on a.tid=e.object_id
"""+(realm=='platform'?'':" join iam_subject_t person on person.tid=a.subject_id and person.identity_domain=#{domain} and person.is_del=0 ")+"""
where e.action='password:recovery-request' and e.result='SUCCESS'
  and e.reason_code=#{kind} and e.identity_domain=#{domain}
  and e.event_time>date_sub(utc_timestamp(6),interval 7 day)
  and a.status='ACTIVE' and a.is_del=0
  and not exists (select 1 from iam_audit_event_t finished
    where finished.object_id=e.object_id and finished.action=#{resetAction}
      and finished.result='SUCCESS' and finished.event_time>e.event_time)
  and not exists (select 1 from iam_audit_event_t newer
    where newer.object_id=e.object_id and newer.action='password:recovery-request'
      and newer.reason_code=#{kind} and newer.result='SUCCESS'
      and newer.event_time>e.event_time)
""";
    if(realm=='auth-account') {
        filter=filter+" and a.identity_domain=#{domain}";
    }
    var total=sql.selectOne('select count(*) total '+filter).total;
    var offset=(page-1)*size;
    var rows=sql.select("select e.tid as id,e.object_id account_id,e.event_time requested_at,a.username,"
        +nameColumn+" display_name,a.version "+filter+" order by e.event_time desc,e.tid desc limit #{size} offset #{offset}");
    return {list:rows.map(row=>({
        id:row.id,accountId:row.account_id,username:row.username,name:row.display_name,
        version:row.version,requestedAt:row.requested_at,realm:realm,domain:domain
    })),total:total,page:page,size:size};
}));
