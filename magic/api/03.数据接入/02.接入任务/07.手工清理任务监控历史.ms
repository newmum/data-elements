{
  "properties": {},
  "id": "8f8b59890a174e36ad43a290e748290a",
  "script": null,
  "groupId": "99c8dd7189af40939e5110b76bcd7160",
  "name": "07.手工清理任务监控历史",
  "createTime": 1790845752950,
  "updateTime": null,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/monitorSnapCleanup",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"retentionDays\":30,\"dryRun\":true}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "当前租户全量数据范围用户可预览或分批物理删除到期监控快照，包含历史软删记录。执行必须同时传入 dryRun=false 和 confirm=true，每次最多 1000 条。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
import '@/common/purge-task-monitor-snapshots' as purgeSnapshots
import dataScope

// 接口：POST /ods/task/monitorSnapCleanup。retentionDays 确定快照的到期边界。
// 预览返回到期数量，确认后返回本批物理删除数及是否还有剩余。
// 清理横跨租户内全部任务，部门和个人数据范围不得预览或执行全库清理。
var scope = dataScope.dataSource()
if (scope?.scope != 'ALL' || scope?.userId == null) {
    exit 403, '需要当前租户的全部数据范围才能清理监控历史'
}

var dryRun = body?.dryRun != false
if (!dryRun && body?.confirm != true) {
    exit 400, '请先预览并确认清理范围'
}
var retentionDays = body?.retentionDays == null ? 30 : body.retentionDays::int(0)
return purgeSnapshots(retentionDays, dryRun)
