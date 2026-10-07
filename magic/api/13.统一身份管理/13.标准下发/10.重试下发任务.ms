{
  "properties": {},
  "id": "bdadee3c54325c0c8c04c28dc02c0281",
  "script": null,
  "groupId": "07899fe85f4d59fd90de0507c7f4d188",
  "name": "10.重试下发任务",
  "createTime": 1790618621046,
  "updateTime": 1790948852117,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/retry",
  "description": "校验失败任务及应用权限，重新准备可重试下发步骤；由本次请求触发，不创建自动轮询。",
  "parameters": [],
  "method": "POST",
  "options": [],
  "requestBody": "{}",
  "headers": [],
  "paths": [],
  "responseBody": null
}
================================
// 接口：10.重试下发任务
// 路由：POST /idaas/provision/retry
// 业务说明：校验失败任务及应用权限，重新准备可重试下发步骤；由本次请求触发，不创建自动轮询。
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
    var payload=body;
    // 请求参数与本接口业务处理。
    var operation = 'retry';

    // 获取当前身份域的数据库连接；租户接收流程在租户上下文内取得对应库。
    var sql=db.normal();
    var p=payload||{};
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
