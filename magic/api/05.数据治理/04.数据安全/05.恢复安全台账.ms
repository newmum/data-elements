{
  "properties": {},
  "id": "dwm_security_restore_01",
  "script": null,
  "groupId": "dwm_security_group_01",
  "name": "05.恢复安全台账",
  "path": "/records/restore",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "description": "恢复当前租户逻辑删除的安全台账。"
}
================================
// 接口：恢复安全台账
// 业务说明：按类型白名单恢复当前租户已软删除的台账，并记录审计日志。
import tenantRuntime
import cn.dev33.satoken.stp.StpUtil
import java.util.UUID

var tenantId = tenantRuntime.id()
var recordType = body?.recordType == null ? '' : ('' + body.recordType).trim()
var recordId = body?.recordId == null ? '' : ('' + body.recordId).trim()
if (recordId == '') { exit 400, '记录标识不能为空' }
var allowed = ['sec_classification_model','sec_classification_rule','sec_scan_task','sec_scan_result',
               'sec_review_task','sec_policy','sec_access_grant','sec_masking_policy','sec_key_reference',
               'sec_encryption_task','sec_watermark_policy','sec_watermark_task']
if (!allowed.contains(recordType)) { exit 400, '不支持的数据安全记录类型' }

var actor = StpUtil.isLogin() ? '' + StpUtil.getLoginId() : 'system'
return db.transaction(() => {
    var changed = db.table(recordType).where().eq('tenant_id', tenantId).eq('tid', recordId).eq('is_del', 1).update({is_del: 0})
    if (changed == 0) { exit 400, '记录不存在或不属于当前租户' }
    db.table('sec_operation_log').primary('tid').insert({
        tid: UUID.randomUUID().toString().replace('-', ''), tenant_id: tenantId,
        record_type: recordType, record_id: recordId, action_code: 'RESTORE',
        outcome: 'SUCCESS', detail_summary: '已恢复记录', operator_id: actor
    })
    return true
})
