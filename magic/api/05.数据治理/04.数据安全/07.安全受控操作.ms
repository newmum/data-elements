{
  "properties": {},
  "id": "dwm_security_action_01",
  "script": null,
  "groupId": "dwm_security_group_01",
  "name": "07.安全受控操作",
  "path": "/actions/execute",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "description": "执行受控安全工作流。"
}
================================
// 接口：安全受控操作
// 业务说明：只允许四种已登记的安全状态推进；更新与审计在同一事务中完成，不调用外部执行器。
import tenantRuntime
import cn.dev33.satoken.stp.StpUtil
import java.util.UUID

var tenantId = tenantRuntime.id()
var action = body?.action == null ? '' : ('' + body.action).trim().toUpperCase()
var recordId = body?.recordId == null ? '' : ('' + body.recordId).trim()
if (action == '') { exit 400, '操作类型不能为空' }
if (recordId == '') { exit 400, '记录标识不能为空' }

// 操作对应的表、状态列和新状态均由服务端白名单决定。
var transitions = {
    SEC_SCAN_VALIDATE: {table:'sec_scan_task', column:'task_status', state:'READY', audit:'SCAN_PREFLIGHT', detail:'已完成配置预检，等待外部扫描执行器接入'},
    SEC_REVIEW_APPROVE: {table:'sec_review_task', column:'review_status', state:'APPROVED', audit:'REVIEW_APPROVE', detail:'人工复核已通过'},
    SEC_ENCRYPTION_APPROVE: {table:'sec_encryption_task', column:'task_status', state:'APPROVED', audit:'ENCRYPTION_APPROVE', detail:'加解密申请已批准，等待密钥服务执行'},
    SEC_WATERMARK_APPROVE: {table:'sec_watermark_task', column:'task_status', state:'APPROVED', audit:'WATERMARK_APPROVE', detail:'水印申请已批准，等待水印服务执行'}
}
var selected = transitions[action]
if (selected == null) { exit 400, '不支持的数据安全操作' }
var actor = StpUtil.isLogin() ? '' + StpUtil.getLoginId() : 'system'

return db.transaction(() => {
    var changed = db.update('UPDATE ' + selected.table + ' SET ' + selected.column + ' = #{selected.state} WHERE tenant_id = #{tenantId} AND tid = #{recordId} AND is_del = 0')
    if (changed == 0) { exit 400, '任务不存在或不属于当前租户' }
    db.table('sec_operation_log').primary('tid').insert({
        tid: UUID.randomUUID().toString().replace('-', ''), tenant_id: tenantId,
        record_type: selected.table, record_id: recordId, action_code: selected.audit,
        outcome: 'SUCCESS', detail_summary: selected.detail, operator_id: actor
    })
    return db.table(selected.table).where().eq('tenant_id', tenantId).eq('tid', recordId).eq('is_del', 0).selectOne()
})
