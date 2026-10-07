{
  "properties": {},
  "id": "0fae2484c3674bae8d7a3cc61cf67225",
  "script": null,
  "groupId": "99c8dd7189af40939e5110b76bcd7160",
  "name": "03.根据id删除任务",
  "createTime": 1782955344802,
  "updateTime": 1790826928649,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/deleteById",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"tid\": \"ab6611ed06ef4403b31b6cc605fd9e46\",\n    \"props\": \"测试\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": "{\n    \"code\": 500,\n    \"msg\": \"任务不存在\",\n    \"success\": false,\n    \"data\": null\n}",
  "description": "当前租户内按任务 ID 取消接入；允许没有来源表的独立任务；使用请求参数 tid、dryRun；涉及 data_access_agg_task_t、data_access_field_mapping、data_access_task_monitor_snap_t、nifi_pipeline_t、da_prop_t 等业务表；执行数据库写入并返回处理结果。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：03.根据id删除任务
// 路由：POST /ods/task/deleteById
// 业务说明：当前租户内按任务 ID 取消接入；允许没有来源表的独立任务；使用请求参数 tid、dryRun；涉及 data_access_agg_task_t、data_access_field_mapping、data_access_task_monitor_snap_t、nifi_pipeline_t、da_prop_t 等业务表；执行数据库写入并返回处理结果。
/*
 * 删除数据接入任务
 *
 * 当前租户内按任务 ID 取消接入；允许没有来源表的独立任务。
 * 先逻辑删除任务、字段映射、监控快照和流水线持久化记录，再清理 NiFi
 * 进程组。只清空指向该任务的目录 accessTaskId，不删除目标物理表。
 * dryRun=true 只校验任务归属并返回计划，不写入或清理运行态。
 */
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifiClient
import log
import tenantRuntime

var text = value => value == null ? '' : (value + '').trim()
var tid = text(body?.tid)
var tenantId = text(tenantRuntime.id())
if (tenantId == '') { exit 401, '登录状态已失效，请重新登录' }
if (tid == '') { exit 400, 'tid不能为空' }

// 独立任务也可能没有来源表；删除权限始终由当前登录租户和任务 ID 决定。
var task = db.table('data_access_agg_task_t').where()
    .eq('tid', tid).eq('tenant_id', tenantId).eq('is_del', 0).selectOne()
if (task == null) { exit 404, '当前租户下任务不存在' }

if (body?.dryRun == true) {
    return {
        dryRun: true,
        tid: tid,
        sourceTableId: text(task.sourceTableId),
        pipelineId: text(task.pipelineId),
        sideEffectsApplied: false
    }
}

var now = new Date()
db.table('data_access_agg_task_t').where().eq('tid', tid).eq('tenant_id', tenantId).eq('is_del', 0)
    .update({is_del: 1, task_status: 0, updated_time: now})
db.table('data_access_field_mapping').where().eq('task_id', tid).eq('is_del', 0)
    .update({is_del: 1, updated_time: now})
db.table('data_access_task_monitor_snap_t').where().eq('task_id', tid).eq('is_del', 0)
    .update({is_del: 1})
if (task.pipelineId != null && task.pipelineId != '') {
    db.table('nifi_pipeline_t').where().eq('id', task.pipelineId).eq('tenant_id', tenantId).eq('is_del', 0)
        .update({is_del: 1, updated_at: System.currentTimeMillis()})
}

if (task.dataCatalogId != null && task.dataCatalogId != '') {
    var accessTaskProp = db.table('da_prop_t').where()
        .eq('parent_id', task.dataCatalogId)
        .eq('prop_name', 'accessTaskId')
        .eq('prop_value', tid)
        .eq('is_del', 0)
        .selectOne()
    if (accessTaskProp != null) {
        db.table('da_prop_t').primary('tid').update({
            tid: accessTaskProp.tid,
            prop_value: null,
            updated_time: now
        }, true)
    }
}

if (task.processGroupId != null && task.processGroupId != '') {
    try {
        nifiClient.cleanupProcessGroup(task.processGroupId)
    } catch (e) {
        log.warn('NiFi进程组清理失败, processGroupId={}, message={}', task.processGroupId, e.message)
    }
}

return {tid: tid, sourceTableId: text(task.sourceTableId), targetTableDeleted: false}
