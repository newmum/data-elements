{
  "properties": {},
  "id": "b964b2d54ba0477b86d3fb6e6d18c45b",
  "groupId": "99c8dd7189af40939e5110b76bcd7160",
  "name": "09.任务监控小时趋势",
  "path": "/monitor/trend",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\"beginTime\":\"2026-10-03 00:00:00\",\"endTime\":\"2026-10-04 00:00:00\",\"interval\":\"hour\"}",
  "headers": [],
  "paths": [],
  "description": "POST /ods/task/monitor/trend；当前租户至多7天小时级监控快照峰值，绝非目标库确认写入。",
  "script": null
}
================================
// 流入/流出是 NiFi 快照窗口计数，连续采样可能重叠。按任务取每小时峰值再求和，避免直接相加采样值。
import tenantRuntime
import cn.hutool.core.date.DateUtil
import java.sql.Timestamp

var tenantId = tenantRuntime.id()
if (tenantId == null || ('' + tenantId).trim() == '') {
    exit 401, '当前登录租户无效，请重新登录后查看趋势'
}
var beginTime = body?.beginTime == null ? '' : ('' + body.beginTime).trim()
var endTime = body?.endTime == null ? '' : ('' + body.endTime).trim()
var interval = body?.interval == null ? 'hour' : ('' + body.interval).trim().toLowerCase()
if (interval != 'hour') {
    exit 400, '当前只支持按小时汇总'
}
if (!beginTime.matches('^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}$')
    || !endTime.matches('^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}$')) {
    exit 400, '时间格式必须为YYYY-MM-DD HH:mm:ss'
}
var startMillis = DateUtil.parse(beginTime, 'yyyy-MM-dd HH:mm:ss').getTime()
var finishMillis = DateUtil.parse(endTime, 'yyyy-MM-dd HH:mm:ss').getTime()
if (finishMillis <= startMillis || finishMillis - startMillis > 7 * 24 * 60 * 60 * 1000) {
    exit 400, '时间范围必须大于0且不超过7天'
}
var beginBound = new Timestamp(startMillis)
var endBound = new Timestamp(finishMillis)

// 只取原始快照并在脚本内按小时聚合，避免 MySQL DATE_FORMAT 方言绑定。
// 单次最多 50000 行；超出时明确拒绝，不能静默截断出错误的趋势。
var maxRows = 50000
var raw = db.page("""
    SELECT task_id AS taskId,
           monitor_time AS monitorTime,
           COALESCE(flow_files_in, 0) AS flowFilesIn,
           COALESCE(flow_files_out, 0) AS flowFilesOut,
           COALESCE(bytes_in, 0) AS bytesIn,
           COALESCE(bytes_out, 0) AS bytesOut,
           COALESCE(queued_count, 0) AS queuedCount
      FROM data_access_task_monitor_snap_t
     WHERE tenant_id = #{tenantId}
       AND is_del = 0
       AND monitor_time >= #{beginBound}
       AND monitor_time < #{endBound}
     ORDER BY monitor_time, task_id
""", maxRows + 1, 0)
if (raw.total > maxRows) {
    exit 413, '时间范围内快照超过50000条，请缩短查询时间范围'
}
var value = (row, name) => {
    if (row[name] != null) { return row[name] }
    if (row[name.toLowerCase()] != null) { return row[name.toLowerCase()] }
    return row[name.toUpperCase()]
}
var asLong = (item) => item == null ? 0 : item::long
var finishBucket = (bucket, tasks) => {
    var point = {
        bucketTime: bucket, sampleCount: 0, flowFilesIn: 0, flowFilesOut: 0,
        bytesIn: 0, bytesOut: 0, queuedCountMax: 0
    }
    for (task in tasks) {
        point.sampleCount = point.sampleCount + task.sampleCount
        point.flowFilesIn = point.flowFilesIn + task.flowFilesIn
        point.flowFilesOut = point.flowFilesOut + task.flowFilesOut
        point.bytesIn = point.bytesIn + task.bytesIn
        point.bytesOut = point.bytesOut + task.bytesOut
        point.queuedCountMax = Math.max(point.queuedCountMax, task.queuedCountMax)
    }
    return point
}
var series = []
var currentBucket = ''
var tasks = []
var taskIndex = {}
for (row in (raw.list == null ? [] : raw.list)) {
    // JDBC 的 DATETIME/TIMESTAMP.toString() 均以 yyyy-MM-dd HH 开头；T 分隔符也兼容。
    var monitorText = ('' + value(row, 'monitorTime')).replace('T', ' ')
    if (!monitorText.matches('^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:.*$')) {
        exit 500, '监控快照时间格式无法识别，已拒绝返回不完整趋势'
    }
    var bucket = monitorText.substring(0, 13) + ':00:00'
    if (currentBucket != '' && bucket != currentBucket) {
        series.add(finishBucket(currentBucket, tasks))
        tasks = []
        taskIndex = {}
    }
    currentBucket = bucket
    var taskId = '' + value(row, 'taskId')
    var index = taskIndex[taskId]
    if (index == null) {
        tasks.add({sampleCount: 0, flowFilesIn: 0, flowFilesOut: 0,
            bytesIn: 0, bytesOut: 0, queuedCountMax: 0})
        index = tasks.size()
        taskIndex[taskId] = index
    }
    var task = tasks[index - 1]
    task.sampleCount = task.sampleCount + 1
    task.flowFilesIn = Math.max(task.flowFilesIn, asLong(value(row, 'flowFilesIn')))
    task.flowFilesOut = Math.max(task.flowFilesOut, asLong(value(row, 'flowFilesOut')))
    task.bytesIn = Math.max(task.bytesIn, asLong(value(row, 'bytesIn')))
    task.bytesOut = Math.max(task.bytesOut, asLong(value(row, 'bytesOut')))
    task.queuedCountMax = Math.max(task.queuedCountMax, asLong(value(row, 'queuedCount')))
}
if (currentBucket != '') {
    series.add(finishBucket(currentBucket, tasks))
}
return {
    interval: 'hour',
    metric: 'SUM_OF_TASK_HOURLY_SNAPSHOT_PEAKS',
    beginTime: beginTime,
    endTime: endTime,
    points: series
}
