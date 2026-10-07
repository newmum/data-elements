{
  "properties": {},
  "id": "d3584c2bbce4543ca25ffbc5654a8c46",
  "script": null,
  "groupId": "07899fe85f4d59fd90de0507c7f4d188",
  "name": "03.查询下发任务记录",
  "createTime": 1790612165860,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/tasks",
  "description": "按应用和状态查询手动创建的资料下发任务、执行结果与失败原因；不执行待处理任务。",
  "parameters": [],
  "method": "GET",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：03.查询下发任务记录
// 路由：GET /idaas/provision/tasks
// 业务说明：按应用和状态查询手动创建的资料下发任务、执行结果与失败原因；不执行待处理任务。
import db;
import cn.hutool.json.JSONUtil;
import '@/idaas/app-list-context' as listContext;
import '@/idaas/guard' as guard;
import '@/idaas/mutate' as mutate;
import '@/idaas/audit' as audit;
import identityRuntime;
import request;
import '@/idaas/api-call' as apiCall;


return identityRuntime.control(()=>apiCall(()=>{
    var payload={};
    for(key in ['appId','configId','id']) {
        var input=request.getValues(key);
        if(input!=null && input.size()>0) {
            payload.put(key,input[0]);
        }
    }
    // 请求参数与本接口业务处理。
    var operation = 'tasks';

    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
    if(operation=='tasks') {
        // 按操作员的应用管理范围过滤可见数据。
        var ctx=listContext('sync:read',p.appId||null,'workforce');
        return sql.select("""
select t.*,t.tid as id,c.display_name,date_format(t.created_time,
        '%Y-%m-%dT%H:%i:%sZ') created_utc
from iam_sync_task_t t
join iam_sync_config_t c on c.tid=t.config_id
order by t.created_time desc
limit 200
""").filter(t=>(p.appId==null || t.app_id==p.appId) && ctx.pairs.filter(pair=>pair.codes.contains('sync:read') && pair.scopes.filter(s=>s.identity_domain=='workforce' && (s.scope_kind=='ALL' || s.scope_kind=='APPLICATION' && s.app_id==t.app_id)).size()>0).size()>0).map(t=>({
            id:t.id,version:t.version,appId:t.app_id,targetTenantId:t.app_id,configId:t.config_id,name:t.display_name,createdAt:t.created_utc,status:t.status=='SUCCEEDED'?'success':['FAILED','PARTIAL'].contains(t.status)?'failed':t.status=='CANCELED'?'cancelled':'pending',serverStatus:t.status,items:sql.select("""
select *,tid as id
from iam_sync_item_t
where task_id=#{t.id}
order by sequence_no
""").map(i=>({
                eventId:i.id,objectId:i.object_id,type:i.object_type=='SUBJECT'?'user':i.object_type.toLowerCase(),version:i.source_version,attempt:i.attempt_count,status:['APPLIED','DUPLICATE'].contains(i.status)?'success':['FAILED','CONFLICT'].contains(i.status)?'failed':i.status=='CANCELED'?'cancelled':'pending',result:i.result_json==null?null:JSONUtil.parseObj(i.result_json)
            }))
        }));
    }
    var task=sql.selectOne("""
select *,tid as id
from iam_sync_task_t
where tid=#{p.id}
""");
    if(task==null) {
        exit 404,'任务不存在';
    }
    // 先校验当前会话的功能权限、身份域和应用管理范围。
    var ctx=guard('sync:execute',task.app_id,'workforce');
    // 写入通过共享的幂等事务处理，并记录审计；版本冲突直接返回。
    return mutate(ctx,'sync:'+operation,p,()=>{
        var current=sql.selectOne("""
select *,tid as id
from iam_sync_task_t
where tid=#{p.id}
    for update
""");
        if(current.version!=p.version) {
            exit 409,'任务版本已变更';
        }
        if(operation=='cancel') {
            sql.update("""
update iam_sync_task_t
set status='CANCELED',version=version+1
where tid=#{p.id}
""");
            sql.update("""
update iam_sync_item_t
set status='CANCELED'
where task_id=#{p.id}
    and status in ('PENDING','FAILED','CONFLICT')
""");
        }
        else if(operation=='retry') {
            if(current.status=='CANCELED') {
                exit 409,'已取消的任务须重新预览';
            }
            if(sql.selectOne("""
select i.tid as id
from iam_sync_item_t i
join iam_sync_config_t c on c.tid=#{current.config_id}
where i.task_id=#{p.id}
    and i.status in ('FAILED','CONFLICT')
    and i.attempt_count>=c.max_attempts
limit 1
""")!=null) {
                exit 409,'已达尝试上限，请修正原因并重新预览创建任务';
            }
            sql.update("""
update iam_sync_item_t
set status='PENDING',next_retry_time=null
where task_id=#{p.id}
    and status in ('FAILED','CONFLICT')
""");
            sql.update("""
update iam_sync_task_t
set status='PENDING',version=version+1
where tid=#{p.id}
""");
        }
        else {
            exit 400,'任务操作不正确';
        }
        // 记录操作主体、目标及结果，供审计与故障追踪。
        audit(ctx,'SUCCESS','sync:'+operation,p.id,null,{
            id:p.id
        });
        return {
            id:p.id
        };
    });

}));
