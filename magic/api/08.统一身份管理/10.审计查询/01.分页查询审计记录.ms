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
import tableSort;
import '@/idaas/api-call' as apiCall;
import db;
import request;
import java.lang.Long;
import java.time.LocalDate;
import java.time.ZoneOffset;
import '@/idaas/guard' as guard;

return identityRuntime.control(()=>apiCall(()=>{

    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var query=(name,fallback)=>{
        var values=request.getValues(name);
        return values==null || values.size()==0?fallback:values[0];
    };
    var domain=query('domain','workforce');
    var ctx=guard('audit:read',null,domain);
    if(query('mode','')=='trend') {
        var days=Math.min(14,Math.max(1,query('days','7')::int));
        var start=LocalDate.now(ZoneOffset.UTC).minusDays(days-1).toString();
        var aggregates=sql.select("""
SELECT DATE_FORMAT(event_time,'%Y-%m-%d') day,COUNT(*) count,
 COUNT(DISTINCT actor_id) people,SUM(CASE WHEN result!='SUCCESS' THEN 1 ELSE 0 END) failures
FROM iam_audit_event_t WHERE identity_domain=#{domain} AND event_type='AUTH'
 AND event_time>=CAST(#{start} AS DATETIME) GROUP BY DATE_FORMAT(event_time,'%Y-%m-%d')
""");
        var points=[];
        var i=0;
        while(i<days) {
            var day=LocalDate.parse(start).plusDays(i).toString();
            var matches=aggregates.filter(x=>x.day==day);
            var row=matches.size()==0?{count:0,people:0,failures:0}:matches[0];
            points.add({date:day,count:row.count,people:row.people,failures:row.failures});
            i=i+1;
        }
        var sorting={sortField:query('sortField',null),sortDir:query('sortDir','asc')};
        return {list:tableSort.rows(points,sorting,{date:'date',count:'count',people:'people'},'date','date')};
    }
    var business=query('business','false')=='true';
    var pageText=query('page','1');
    var sizeText=query('size','30');
    if(!pageText.matches('[0-9]{1,5}') || !sizeText.matches('[0-9]{1,3}')) {
        exit 400,'分页参数不正确';
    }
    var page=Long.parseLong(pageText);
    var size=Long.parseLong(sizeText);
    if(page<1 || page>10000 || size<1 || size>100) {
        exit 400,'分页参数不正确';
    }
    var offset=(page-1)*size;
    var sorting={sortField:query('sortField',null),sortDir:query('sortDir',null)};
    var sortColumn=tableSort.field(sorting,{event_time:'a.event_time',duration_ms:'a.duration_ms'},'event_time');
    var sortDirection=tableSort.direction(sorting);
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
where a.identity_domain=#{domain}
    and (#{business}=false or (a.action!='profile:domain' and a.action not like '%:read'))
    and (#{type} is null
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
where a.identity_domain=#{domain}
    and (#{business}=false or (a.action!='profile:domain' and a.action not like '%:read'))
    and (#{type} is null
       or a.event_type=#{type})
    and (#{result} is null
         or a.result=#{result})
    and (#{search}=''
         or locate(lower(#{search}),

                lower(concat_ws(' ',
        a.action, a.actor_id, o.username, o.display_name, a.object_id, a.trace_id, a.ip,
        a.request_path)))>0)
"""+' ORDER BY CASE WHEN '+sortColumn+' IS NULL THEN 1 ELSE 0 END ASC,'+sortColumn+' '+sortDirection+',a.tid ASC LIMIT #{size} OFFSET #{offset}');
    return {
        list:rows,total:count.total,page:page,size:size
    };
}));
