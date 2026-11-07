{
  "properties": {},
  "id": "f541570df3bc4bf3a38e9466d2e16cd7",
  "script": null,
  "groupId": "99c8dd7189af40939e5110b76bcd7160",
  "name": "06.任务监控快照分页",
  "createTime": 1784439136875,
  "updateTime": 1784439136875,
  "lock": null,
  "createBy": null,
  "updateBy": null,
  "path": "/monitorSnapPage",
  "method": "POST",
  "parameters": [],
  "options": [],
  "requestBody": "{\n    \"taskId\": \"测试\",\n    \"pipelineId\": \"测试\",\n    \"processGroupId\": \"测试\",\n    \"delayLevel\": \"测试\",\n    \"monitorStatus\": 2,\n    \"beginTime\": \"2026-07-20 10:00:00\",\n    \"endTime\": \"2026-07-20 10:00:00\"\n}",
  "headers": [],
  "paths": [],
  "responseBody": null,
  "description": "支持按任务、流水线、NiFi 进程组、延迟等级、运行状态和监控时间范围过滤；使用请求参数 pageNum、pageSize、taskId、pipelineId、processGroupId、delayLevel、monitorStatus、beginTime；涉及 data_access_task_monitor_snap_t 等业务表。",
  "requestBodyDefinition": null,
  "responseBodyDefinition": null
}
================================

import tableSort
import tableRead
import tenantRuntime
import dataScope
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime

/*
 * POST /ods/task/monitorSnapPage 分页读取已保存的任务运行快照。
 * 输入包括分页、白名单排序、任务等值条件和监控时间起止值。时间边界包含端点。
 * 返回 {total, list}，保留快照字段及历史指标别名。
 * 当前租户全量范围保留历史快照。部门和本人范围通过有效任务关联的来源库校验。
 * 只读取持久化快照，无数据库写入或 NiFi 调用。
 */
var pageNum = body?.pageNum::long(1)
var pageSize = body?.pageSize::long(20)
pageNum = pageNum < 1 ? 1 : pageNum
pageSize = Math.min(100, pageSize < 1 ? 20 : pageSize)

var text = name => tableRead.column('s', name, 'TEXT')
var number = name => tableRead.column('s', name, 'NUMBER')
var time = name => tableRead.column('s', name, 'TIME')
var fields = {
    tid: text('tid'),
    taskId: text('task_id'),
    pipelineId: text('pipeline_id'),
    processGroupId: text('process_group_id'),
    monitorTime: time('monitor_time'),
    delayLevel: text('delay_level'),
    delayMsEstimate: number('delay_ms_estimate'),
    thresholdMs: number('threshold_ms'),
    isTimeout: number('is_timeout'),
    queuedCount: number('queued_count'),
    queuedBytes: number('queued_bytes'),
    activeThreadCount: number('active_thread_count'),
    flowFilesIn: number('flow_files_in'),
    flowFilesOut: number('flow_files_out'),
    bytesIn: number('bytes_in'),
    bytesOut: number('bytes_out'),
    monitorStatus: text('monitor_status'),
    monitorMsg: text('monitor_msg'),
    createdTime: time('created_time'),
    isDel: number('is_del'),
    tenantId: text('tenant_id')
}
var sortFields = {}
for (key in [
    'delayMsEstimate', 'thresholdMs', 'queuedBytes', 'monitorTime', 'createdTime', 'queuedCount',
    'flowFilesIn', 'flowFilesOut', 'bytesIn', 'bytesOut', 'activeThreadCount'
]) {
    sortFields[key] = key
}
var sort = fields[tableSort.field(body, sortFields, 'monitorTime')]
var direction = tableSort.direction(body)

var tenantId = tenantRuntime.id()
if (tenantId == null || tenantId == '') {
    exit 401, '当前登录租户无效'
}
var scope = dataScope.dataSource()
var scopeMode = scope?.scope
var scopeUserId = scope?.userId
var scopeOrgIds = scope?.organizationIds || []
if (!['ALL', 'ORG', 'OWNER'].contains(scopeMode) || scopeUserId == null) {
    exit 403, '无法确定当前数据范围'
}

var query = tableRead.from('data_access_task_monitor_snap_t', 's', tenantId)
query.where(tableRead.equal(fields.isDel, 0))
// 全量权限可查看无有效任务关联的历史快照，收窄范围时才关联有效任务和来源库。
if (scopeMode != 'ALL') {
    query.leftJoin('data_access_agg_task_t', 'a', 'tid', 'task_id', [
        tableRead.equal(tableRead.column('a', 'is_del', 'NUMBER'), 0)
    ])
    query.leftJoin('db_datasource_t', 'd', 'tenant_id', 'tenant_id', [
        tableRead.equal(tableRead.column('d', 'tid', 'TEXT'), tableRead.column('a', 'source_db_id', 'TEXT')),
        tableRead.equal(tableRead.column('d', 'is_del', 'NUMBER'), 0)
    ])
    if (scopeMode == 'OWNER') {
        query.where(tableRead.equal(tableRead.column('d', 'created_by', 'TEXT'), scopeUserId))
    } else {
        query.where(tableRead.in(tableRead.column('d', 'org_id', 'TEXT'), scopeOrgIds))
    }
}

for (key in ['taskId', 'pipelineId', 'processGroupId', 'delayLevel', 'monitorStatus', 'beginTime', 'endTime']) {
    var value = body == null ? null : body[key]
    if (value == null || value == '') {
        continue
    }
    var parameter = '' + value
    if (parameter.length() > 100) {
        exit 400, '筛选内容过长'
    }
    if (key == 'beginTime' || key == 'endTime') {
        // 使用 JDBC 时间类型绑定，避免数据库将文本隐式转换成时间。
        var bound = null
        try {
            var normalized = parameter.trim()
            var localTime = normalized.length() == 10 ? LocalDate.parse(normalized).atStartOfDay() :
                LocalDateTime.parse(normalized.replace(' ', 'T'))
            bound = Timestamp.valueOf(localTime)
        } catch (e) {
            exit 400, '筛选时间格式无效'
        }
        var later = tableRead.greaterThan(fields.monitorTime, bound)
        query.where(key == 'beginTime' ? tableRead.any([later, tableRead.equal(fields.monitorTime, bound)]) : tableRead.not(later))
    } else {
        query.where(tableRead.equal(fields[key], parameter))
    }
}

if (pageNum > 2147483647) {
    return { total: query.grouped({ total: tableRead.count() }, [])[0].total, list: [] }
}
// 数据库按所选字段分页，空值放末尾，同值按快照 ID 保持稳定顺序。
var page = query.page(fields, sort, direction, 'tid', pageNum::int, pageSize::int)
var aliases = [
    'monitorTime', 'monitorStatus', 'monitorMsg', 'delayLevel', 'queuedCount',
    'flowFilesIn', 'flowFilesOut', 'bytesIn', 'bytesOut', 'activeThreadCount'
]
for (row in page.rows) {
    for (key in aliases) {
        row[key.toLowerCase()] = row[key]
    }
}
return { total: page.total, list: page.rows }
