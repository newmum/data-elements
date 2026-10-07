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
  "description": "手工预览或清理当前租户超过保留天数的任务监控历史快照。执行清理必须显式传入 confirm=true；不会删除接入任务与最新监控状态；使用请求参数 retentionDays、dryRun、confirm；涉及 data_access_task_monitor_snap_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================
// 接口：07.手工清理任务监控历史
// 路由：POST /ods/task/monitorSnapCleanup
// 业务说明：手工预览或清理当前租户超过保留天数的任务监控历史快照。执行清理必须显式传入 confirm=true；不会删除接入任务与最新监控状态；使用请求参数 retentionDays、dryRun、confirm；涉及 data_access_task_monitor_snap_t 等业务表。
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 读取请求中的业务标识与筛选条件，供下面的校验和查询使用。
var retentionDays = body?.retentionDays::int(30)
if (retentionDays < 1 || retentionDays > 1000) {
    throw new IllegalArgumentException('保留天数必须在 1–1000 天之间')
}
var cutoff = LocalDateTime.now().minusDays(retentionDays)
    .format(DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss'))
// 以下从 data_access_task_monitor_snap_t 业务表读取或维护当前操作涉及的记录。
var count = db.table('data_access_task_monitor_snap_t').where()
    .eq('is_del', 0).lt('monitor_time', cutoff).count()

if (body?.dryRun != false) {
    return {retentionDays: retentionDays, cutoff: cutoff, count: count, deleted: 0}
}
if (body?.confirm != true) {
    throw new IllegalArgumentException('请先预览并确认清理范围')
}
var deleted = 0
if (count > 0) {
    deleted = db.table('data_access_task_monitor_snap_t').where()
        .eq('is_del', 0).lt('monitor_time', cutoff).delete()
}
return {retentionDays: retentionDays, cutoff: cutoff, count: count, deleted: deleted}
