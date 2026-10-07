{
  "properties": {},
  "id": "dwm_security_history_01",
  "script": null,
  "groupId": "dwm_security_group_01",
  "name": "06.安全审计历史",
  "path": "/records/history",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": null,
  "headers": [],
  "paths": [],
  "description": "查询当前租户单条安全台账的审计历史。"
}
================================
// 接口：安全审计历史
// 业务说明：根据记录标识读取当前租户的安全操作日志，按发生时间倒序展示。
import tenantRuntime

var tenantId = tenantRuntime.id()
var recordId = body?.recordId == null ? '' : ('' + body.recordId).trim()
if (recordId == '') { exit 400, '记录标识不能为空' }

// 历史记录按租户和记录标识共同限定，不能从客户端指定其他租户。
var items = db.select("""
    SELECT tid,
           record_type,
           record_id,
           action_code,
           outcome,
           detail_summary,
           operator_id,
           created_time
      FROM sec_operation_log
     WHERE tenant_id = #{tenantId}
       AND record_id = #{recordId}
     ORDER BY created_time DESC
""")
return {tenantId: tenantId, items: items}
