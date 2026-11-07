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
  "description": "按当前租户汇总最多七天的监控快照。每个任务先取小时峰值，再汇总为趋势点。开始时间包含，结束时间不包含，单次最多处理 50000 条快照。",
  "script": null
}
================================
import tenantRuntime
import cn.hutool.core.date.DateUtil
import java.sql.Timestamp

/**
 * 任务监控小时趋势。POST /ods/task/monitor/trend
 * 接收 beginTime、endTime 和可选 interval=hour。按当前租户读取开始时间至结束时间前的有效快照。
 * 返回逐小时任务峰值合计和样本数。NiFi 连续采样可能重叠，指标仅用于监控趋势。
 */

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

// 分页方言由单表查询处理。多读取一条用于识别超限，避免截断后返回错误趋势。
var maxRows = 50000
var raw = db.camel().table('data_access_task_monitor_snap_t')
    .columns('task_id', 'monitor_time', 'flow_files_in', 'flow_files_out', 'bytes_in', 'bytes_out', 'queued_count')
    .where().eq('tenant_id', tenantId).eq('is_del', 0)
    .gte('monitor_time', beginBound).lt('monitor_time', endBound)
    .orderBy('monitor_time', 'asc').orderBy('task_id', 'asc').page(maxRows + 1, 0)
if (raw.total > maxRows) {
    exit 413, '时间范围内快照超过50000条，请缩短查询时间范围'
}
var asLong = item => item == null ? 0 : item::long
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
    var monitorText = ('' + row.monitorTime).replace('T', ' ')
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
    var taskId = '' + row.taskId
    var index = taskIndex[taskId]
    if (index == null) {
        tasks.add({sampleCount: 0, flowFilesIn: 0, flowFilesOut: 0,
            bytesIn: 0, bytesOut: 0, queuedCountMax: 0})
        index = tasks.size()
        taskIndex[taskId] = index
    }
    var task = tasks[index - 1]
    task.sampleCount = task.sampleCount + 1
    task.flowFilesIn = Math.max(task.flowFilesIn, asLong(row.flowFilesIn))
    task.flowFilesOut = Math.max(task.flowFilesOut, asLong(row.flowFilesOut))
    task.bytesIn = Math.max(task.bytesIn, asLong(row.bytesIn))
    task.bytesOut = Math.max(task.bytesOut, asLong(row.bytesOut))
    task.queuedCountMax = Math.max(task.queuedCountMax, asLong(row.queuedCount))
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
