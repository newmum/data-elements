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
import tableSort;
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
    for(key in ['appId','configId','id','page','size','paged','kind','sortField','sortDir']) {
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
    var scopes=[];
    for(pair in ctx.pairs) {
        if(pair.codes.contains('sync:read')) {scopes.addAll(pair.scopes.filter(s=>s.identity_domain=='workforce'));}
    }
    var allScope=scopes.filter(s=>s.scope_kind=='ALL').size()>0;
    var scopedAppIds=scopes.filter(s=>s.scope_kind=='APPLICATION').map(s=>s.app_id).distinct();
    var scopeSql=allScope?'':scopedAppIds.size()==0?' AND 1=0':' AND t.app_id IN (#{scopedAppIds})';
    if(p.appId!=null) {scopeSql=scopeSql+' AND t.app_id=#{p.appId}';}

        if(p.id!=null) {scopeSql=scopeSql+' AND t.tid=#{p.id}';}
        if(p.configId!=null) {scopeSql=scopeSql+' AND t.config_id=#{p.configId}';}
        var page=Math.max(1,p.page::int(1));
        var size=Math.min(100,Math.max(1,p.size::int(20)));
        var offset=(page-1)*size;
        if(p.kind=='items') {
            if(p.id==null) {exit 400,'任务编号不能为空';}
            var current=sql.selectOne("SELECT t.tid AS id,t.app_id FROM iam_sync_task_t t WHERE 1=1"+scopeSql);
            if(current==null) {exit 404,'任务不存在或不在当前管理范围';}
            var itemFilter=' FROM iam_sync_item_t i WHERE i.task_id=#{p.id}';
            var total=sql.selectOne('SELECT COUNT(*) total'+itemFilter).total;
            var field=tableSort.field(p,{name:'object_name',attempt:'i.attempt_count'},'name');
            var direction=tableSort.direction(p);
            var items=sql.select("""
SELECT i.*,i.tid AS id,COALESCE(CASE i.object_type WHEN 'SUBJECT' THEN s.name WHEN 'ORG' THEN o.name
 WHEN 'ROLE' THEN r.name WHEN 'RESOURCE' THEN x.name END,i.object_id) AS object_name
FROM iam_sync_item_t i
LEFT JOIN iam_subject_t s ON i.object_type='SUBJECT' AND s.tid=i.object_id AND s.identity_domain='workforce' AND s.is_del=0
LEFT JOIN iam_org_t o ON i.object_type='ORG' AND o.tid=i.object_id AND o.identity_domain='workforce' AND o.is_del=0
LEFT JOIN iam_role_t r ON i.object_type='ROLE' AND r.tid=i.object_id AND r.app_id=#{current.app_id} AND r.identity_domain='workforce' AND r.is_del=0
LEFT JOIN iam_resource_t x ON i.object_type='RESOURCE' AND x.tid=i.object_id AND x.app_id=#{current.app_id} AND x.identity_domain='workforce' AND x.is_del=0
WHERE i.task_id=#{p.id}
"""+' ORDER BY '+field+' '+direction+',i.sequence_no ASC,i.tid ASC LIMIT #{size} OFFSET #{offset}');
            return {list:items.map(i=>({eventId:i.id,objectId:i.object_id,name:i.object_name,type:i.object_type=='SUBJECT'?'user':i.object_type.toLowerCase(),
                version:i.source_version,attempt:i.attempt_count,status:['APPLIED','DUPLICATE'].contains(i.status)?'success':['FAILED','CONFLICT'].contains(i.status)?'failed':i.status=='CANCELED'?'cancelled':'pending',
                result:i.result_json==null?null:JSONUtil.parseObj(i.result_json)})),total:total,page:page,size:size};
        }
        var filter=" FROM iam_sync_task_t t JOIN iam_sync_config_t c ON c.tid=t.config_id AND c.app_id=t.app_id WHERE 1=1"+scopeSql;
        var total=sql.selectOne('SELECT COUNT(*) total'+filter).total;
        var paged=p.paged=='true';
        if(!paged && p.id==null && total>1000) {exit 400,'任务超过 1000 项，请使用分页查询';}
        if(!paged) {size=p.id==null?1000:1;offset=0;}
        var field=tableSort.field(p,{name:'c.display_name',createdAt:'t.created_time',successCount:'COALESCE(stats.success_count,0)',failedCount:'COALESCE(stats.failed_count,0)',pendingCount:'COALESCE(stats.pending_count,0)',totalCount:'COALESCE(stats.total_count,0)'},'createdAt');
        var direction=tableSort.direction(p);
        var joined=filter.replace(' WHERE 1=1',"""
 LEFT JOIN (
   SELECT task_id,COUNT(*) total_count,
     SUM(CASE WHEN status IN ('APPLIED','DUPLICATE') THEN 1 ELSE 0 END) success_count,
     SUM(CASE WHEN status IN ('FAILED','CONFLICT') THEN 1 ELSE 0 END) failed_count,
     SUM(CASE WHEN status NOT IN ('APPLIED','DUPLICATE','FAILED','CONFLICT','CANCELED') THEN 1 ELSE 0 END) pending_count
   FROM iam_sync_item_t GROUP BY task_id
 ) stats ON stats.task_id=t.tid WHERE 1=1
""");
        var tasks=sql.select("""
SELECT t.*,t.tid AS id,c.display_name,DATE_FORMAT(t.created_time,'%Y-%m-%dT%H:%i:%sZ') created_utc,
 COALESCE(stats.total_count,0) total_count,COALESCE(stats.success_count,0) success_count,
 COALESCE(stats.failed_count,0) failed_count,COALESCE(stats.pending_count,0) pending_count
"""+joined+' ORDER BY '+field+' '+direction+',t.tid ASC LIMIT #{size} OFFSET #{offset}');
        // A single user-requested task may expose its first failure; list rows use aggregates only.
        var failures=p.id==null?[]:sql.select("""
SELECT i.*,i.tid AS id FROM iam_sync_item_t i JOIN iam_sync_task_t t ON t.tid=i.task_id
WHERE i.task_id=#{p.id}
"""+scopeSql+" AND i.status IN ('FAILED','CONFLICT') ORDER BY i.sequence_no LIMIT 1");
        var rows=tasks.map(t=>({id:t.id,version:t.version,appId:t.app_id,targetTenantId:t.app_id,configId:t.config_id,name:t.display_name,createdAt:t.created_utc,
            status:t.status=='SUCCEEDED'?'success':['FAILED','PARTIAL'].contains(t.status)?'failed':t.status=='CANCELED'?'cancelled':'pending',serverStatus:t.status,
            totalCount:t.total_count,successCount:t.success_count,failedCount:t.failed_count,pendingCount:t.pending_count,
            items:failures.map(i=>({eventId:i.id,objectId:i.object_id,type:i.object_type=='SUBJECT'?'user':i.object_type.toLowerCase(),version:i.source_version,attempt:i.attempt_count,
                status:'failed',result:i.result_json==null?null:JSONUtil.parseObj(i.result_json)}))}));
        return paged?{list:rows,total:total,page:page,size:size}:rows;
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
