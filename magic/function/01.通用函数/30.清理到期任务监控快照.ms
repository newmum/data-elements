{
  "properties": {},
  "id": "377fbf22fd334ef1b29464ba6a42fe31",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "30.清理到期任务监控快照",
  "path": "/purge-task-monitor-snapshots",
  "description": "按可信租户和快照保留天数预览，或一次物理删除最多 1000 条到期任务监控快照。",
  "parameters": [
    { "name": "retentionDays", "description": "保留天数", "required": true, "dataType": "Integer", "type": "java.lang.Integer" },
    { "name": "dryRun", "description": "仅预览清理数量", "required": true, "dataType": "Boolean", "type": "java.lang.Boolean" }
  ],
  "options": []
}
================================
import tenantRuntime
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.sql.Timestamp

// 函数：@/common/purge-task-monitor-snapshots。按已认证请求或定时任务绑定的租户预览或清理到期快照。
// 写入每次最多处理 1000 条并重新核对到期条件，覆盖以往标为 is_del=1 的快照。
var tenantId = tenantRuntime.id()
if (retentionDays == null || retentionDays < 1 || retentionDays > 1000) {
    exit 400, '保留天数必须在 1–1000 天之间'
}

var cutoffTime = LocalDateTime.now().minusDays(retentionDays).withNano(0)
var cutoff = cutoffTime.format(DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss'))
var cutoffValue = Timestamp.valueOf(cutoffTime)
var batchSize = 1000
var expired = () => db.table('data_access_task_monitor_snap_t').where()
    .eq('tenant_id', tenantId).lt('monitor_time', cutoffValue)

if (dryRun != false) {
    return {retentionDays: retentionDays, cutoff: cutoff, count: expired().count(),
        deleted: 0, batchSize: batchSize}
}

// 用现有方言分页读取固定窗口。传入窗口上限可跳过总量统计，额外一行用于判断积压。
var windowSize = batchSize + 1
var candidates = db.page(windowSize, """
    SELECT tid FROM data_access_task_monitor_snap_t
    WHERE tenant_id = #{tenantId} AND monitor_time < #{cutoffValue}
    ORDER BY monitor_time ASC, tid ASC
    """, windowSize, 0, {tenantId: tenantId, cutoffValue: cutoffValue}).getList()
var ids = []
for (row in candidates) {
    if (ids.size() == batchSize) { break }
    ids.add(row.tid)
}
var deleted = ids.size() == 0 ? 0 : expired().in('tid', ids).delete()
return {retentionDays: retentionDays, cutoff: cutoff, count: ids.size(),
    deleted: deleted, batchSize: batchSize, hasMore: candidates.size() > batchSize}
