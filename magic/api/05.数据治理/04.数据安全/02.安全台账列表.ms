{
  "properties": {},
  "id": "dwm_security_list_01",
  "script": null,
  "groupId": "dwm_security_group_01",
  "name": "02.安全台账列表",
  "path": "/records/list",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "description": "按类型读取当前租户安全台账。"
}
================================
// 接口：安全台账列表
// 业务说明：校验允许的安全台账类型后，只读取当前租户未删除的记录。
import tenantRuntime

var tenantId = tenantRuntime.id()
var recordType = body?.recordType == null ? '' : ('' + body.recordType).trim()
if (recordType == '') { exit 400, '记录类型不能为空' }

// 表名只能来自固定白名单，请求参数不能作为任意表名执行。
var allowed = [
    'sec_classification_model', 'sec_classification_rule', 'sec_scan_task',
    'sec_scan_result', 'sec_review_task', 'sec_policy', 'sec_access_grant',
    'sec_masking_policy', 'sec_key_reference', 'sec_encryption_task',
    'sec_watermark_policy', 'sec_watermark_task'
]
if (!allowed.contains(recordType)) { exit 400, '不支持的数据安全记录类型' }

// 业务行仍按最近更新时间排序，返回结构保持与原接口一致。
var items = db.select('SELECT * FROM ' + recordType + ' WHERE tenant_id = #{tenantId} AND is_del = 0 ORDER BY updated_time DESC, tid DESC')
return {tenantId: tenantId, recordType: recordType, items: items}
