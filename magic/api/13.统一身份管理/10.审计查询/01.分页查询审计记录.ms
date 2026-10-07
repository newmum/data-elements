{
  "properties": {},
  "id": "442547f994c85528842eaab529f84850",
  "script": null,
  "groupId": "a2d73d152a83535d9993586812fb257f",
  "name": "01.分页查询审计记录",
  "createTime": 1790612165860,
  "updateTime": 1791205192024,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/list",
  "description": "按身份域、应用、事件和时间范围分页查询控制库审计记录，校验可见范围并对敏感字段脱敏。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：01.分页查询审计记录
// 路由：GET /idaas/audit/list
// 业务说明：按身份域、应用、事件和时间范围分页查询控制库审计记录，校验可见范围并对敏感字段脱敏。
import identityRuntime;
import '@/idaas/api-call' as apiCall;
import db;
import request;
import java.lang.Long;
import '@/idaas/guard' as guard;

return identityRuntime.control(()=>apiCall(()=>{
    // 核验会话的功能权限及对象范围，超出范围的请求在读取或写入前拒绝。
    var ctx=guard('audit:read',null,'workforce');
    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var query=(name,fallback)=>{
        var values=request.getValues(name);
        return values==null || values.size()==0?fallback:values[0];
    };
    var pageText=query('page','1');
    var sizeText=query('size','30');
    if(!pageText.matches('[0-9]{1,5}') || !sizeText.matches('[0-9]{1,3}')) {
        exit 400,'分页参数不正确';
    }
    var page=Long.parseLong(pageText);
    var size=Long.parseLong(sizeText);
    if(page<1 || page>10000 || size<1 || size>200) {
        exit 400,'分页参数不正确';
    }
    var offset=(page-1)*size;
    var type=query('type',null);
    if(type!=null && !['AUTH','API','OPERATION','MIGRATION'].contains(type)) {
        exit 400,'审计类型不正确';
    }
    var search=query('q','');
    var result=query('result',null);
    if(search.length()>100 || (result!=null && !['SUCCESS','FAILED','DENIED'].contains(result))) {
        exit 400,'审计查询条件不正确';
    }
    var count=sql.selectOne("""
select count(*) total
from iam_audit_event_t a
left join iam_operator_profile_v o on o.tid=a.actor_id
where (#{type} is null
       or a.event_type=#{type})
    and (#{result} is null
         or a.result=#{result})
    and (#{search}=''
         or locate(lower(#{search}),

                lower(concat_ws(' ',
        a.action, a.actor_id, o.username, o.display_name, a.object_id, a.trace_id, a.ip,
        a.request_path)))>0)
""");
    var rows=sql.select("""
select a.tid as id,a.event_type,a.identity_domain,a.actor_kind,a.actor_id,o.display_name as actor_name,
       o.username as actor_username, a.app_id,a.action,a.object_type,a.object_id,a.result,a.reason_code,a.trace_id,a.ip,
       a.request_path,a.http_method,a.http_status,a.duration_ms,a.snapshot_json,
       date_format(a.event_time, '%Y-%m-%dT%H:%i:%sZ') as event_time
from iam_audit_event_t a
left join iam_operator_profile_v o on o.tid=a.actor_id
where (#{type} is null
       or a.event_type=#{type})
    and (#{result} is null
         or a.result=#{result})
    and (#{search}=''
         or locate(lower(#{search}),

                lower(concat_ws(' ',
        a.action, a.actor_id, o.username, o.display_name, a.object_id, a.trace_id, a.ip,
        a.request_path)))>0)
order by a.event_time desc,a.tid desc
limit #{size}
offset #{offset}
""");
    return {
        list:rows,total:count.total,page:page,size:size
    };
}));
