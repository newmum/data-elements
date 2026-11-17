{
  "id": "log_tenant_lifecycle_page_v1",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "22.分页读取行业日志任务",
  "path": "/logtaskpage",
  "parameters": [
    {
      "name": "kind",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "http",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": [],
  "description": "行业导出和归档共用任务分页，限定行业范围与任务所有人，批量投影操作者快照。"
}
================================
import '@/common/loggrants' as grants
import cn.dev33.satoken.stp.StpUtil
import com.linewell.dataelement.platform.audit.LogTimes
import java.lang.Long
import java.lang.SecurityException
import java.time.Duration
import java.time.Instant
import tableRead
import tenantRuntime

// /common/logtaskpage 供行业导出和归档的 GET、HEAD 共用。应用和账号均取自已认证会话。
var tenant = tenantRuntime.id()
var actor = StpUtil.getLoginIdAsString()
var application = StpUtil.getTokenSession().get('appId')
var mode = grants(actor, application == null ? '' : application + '', kind).modes.get(kind)
if (mode == null) {
    throw new SecurityException('没有此日志模块的访问权限')
}
var exporting = kind == 'exports'
if (!exporting && mode != 'ALL') {
    throw new SecurityException('此操作需要日志模块的完整范围授权')
}

var get = name => {
    var values = http.getValues(name)
    return values == null || values.isEmpty() ? null : values[0]
}
var text = (name, limit) => {
    var value = get(name)
    if (value != null && value.length() > limit) {
        throw new IllegalArgumentException('日志查询内容过长')
    }
    return value == null ? '' : value
}
var number = (name, fallback, maximum) => {
    var value = get(name)
    try {
        var parsed = value == null ? fallback : Long.parseLong(value)
        if (parsed >= 1 && parsed <= maximum) { return parsed }
    } catch (error) { /* 保留原分页参数错误。 */ }
    throw new IllegalArgumentException('分页参数无效')
}
var page = number('page', 1, 10000)
var size = number('size', 20, 100)
var column = (name, type) => tableRead.column('j', name, type)
var query = tableRead.from(exporting ? 'log_export_job_t' : 'log_archive_batch_t', 'j', tenant).withTimeoutSeconds(15)
query.where(tableRead.equal(column('scope_kind', 'TEXT'), 'TENANT'))
query.where(tableRead.equal(column('scope_id', 'TEXT'), tenant))
query.where(tableRead.equal(column('identity_domain', 'TEXT'), 'global'))
// 导出任务只属于发起人，即使该人具备模块的 ALL 范围。
if (exporting) { query.where(tableRead.equal(column('owner_id', 'TEXT'), actor)) }
var kindField = exporting ? 'format_code' : 'event_store'
var keyword = text('keyword', 100)
if (!keyword.isBlank()) {
    query.where(tableRead.any(['tid', 'actor_name', kindField].map(name =>
        tableRead.containsLiteral(column(name, 'TEXT'), keyword))))
}
var status = text('status', 24)
if (!status.isBlank()) {
    if (!['PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'EXPIRED', 'CANCELLED',
        'PREPARING', 'WRITTEN', 'VERIFIED', 'PURGING', 'COMPLETE'].contains(status)) {
        throw new IllegalArgumentException('状态无效')
    }
    query.where(tableRead.equal(column('status', 'TEXT'), status))
}
var created = column('created_time', 'TIME')
if (get('startTime') != null && get('endTime') != null) {
    var start = Instant.parse(get('startTime'))
    var end = Instant.parse(get('endTime'))
    if (!start.isBefore(end) || Duration.between(start, end).compareTo(Duration.ofDays(90)) > 0) {
        throw new IllegalArgumentException('请选择不超过90天的时间区间')
    }
    var minimum = LogTimes.utc(start)
    var maximum = LogTimes.utc(end)
    query.where(tableRead.any([tableRead.greaterThan(created, minimum), tableRead.equal(created, minimum)]))
    query.where(tableRead.not(tableRead.any([tableRead.greaterThan(created, maximum), tableRead.equal(created, maximum)])))
}

var finished = exporting ? 'finished_at' : 'verified_at'
var count = exporting ? 'row_count' : 'record_count'
var fields = {}
var textFields = ['tid', 'actor_name', 'actor_account', 'actor_org_name', 'status',
    kindField, exporting ? 'last_error_code' : 'hold_status']
for (name in textFields) { fields.put(name, column(name, 'TEXT')) }
var times = exporting ? ['created_time', finished, 'expires_at'] : ['created_time', finished, 'range_start', 'range_end']
for (name in times) { fields.put(name, column(name, 'TIME')) }
fields.put(count, column(count, 'NUMBER'))
var extraCount = exporting ? 'row_limit' : 'byte_count'
fields.put(extraCount, column(extraCount, 'NUMBER'))
var sorts = {created_time: created, finished_at: fields.get(finished), name: fields.get(kindField),
    row_count: fields.get(count), actor_name: fields.actor_name}
if (!exporting) {
    sorts.put('range_start', fields.range_start)
    sorts.put('range_end', fields.range_end)
}
var field = text('sortField', 40)
var direction = text('sortDir', 4)
if ((!field.isBlank() && !sorts.containsKey(field)) || (!direction.isBlank() && !['asc', 'desc'].contains(direction))) {
    throw new IllegalArgumentException('排序参数无效')
}
var window = query.page(fields, field.isBlank() ? created : sorts.get(field),
    direction.isBlank() ? 'desc' : direction, 'tid', page::int, size::int)
for (row in window.rows) {
    for (name in times) {
        if (row.get(name) != null) { row.put(name, LogTimes.instant(row.get(name)).toString()) }
    }
}
return {list: window.rows, total: window.total, page: page, size: size}
