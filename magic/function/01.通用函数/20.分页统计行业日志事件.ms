{
  "id": "log_tenant_event_search_v1",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "20.分页统计行业日志事件",
  "path": "/logeventsearch",
  "parameters": [
    {
      "name": "kind",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "stats",
      "required": true,
      "dataType": "Boolean",
      "type": "java.lang.Boolean"
    },
    {
      "name": "http",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": [],
  "description": "四类行业日志分页和统计共用授权、日期、关键词和精确筛选，兼容历史上海时间与无事件统计。"
}
================================
import '@/common/logeventquery' as eventQuery
import '@/common/logeventcriteria' as eventCriteria
import cn.hutool.extra.spring.SpringUtil
import com.linewell.dataelement.platform.audit.LogTimes
import java.time.LocalDateTime
import java.time.ZoneId
import java.lang.Long
import tableRead

// /common/logeventsearch 为四类日志的分页及统计应用同一组条件，先校验当前角色和范围再访问事件表。
var scoped = eventQuery(kind, false)
var query = scoped.query
var fields = scoped.fields
var read = name => {
    var values = http.getValues(name)
    return values == null || values.isEmpty() ? null : values[0]
}
var text = (name, limit) => {
    var value = read(name)
    if (value != null && value.length() > limit) {
        throw new IllegalArgumentException('日志查询内容过长')
    }
    return value == null ? '' : value
}
var input = {}
for (name in ['result', 'actorId', 'orgId', 'traceId', 'requestId', 'keyword', 'parameterKeyword',
    'parameterName', 'parameterValue', 'startTime', 'endTime', 'receivedBefore']) {
    input.put(name, read(name))
}
var criteria = eventCriteria(input, scoped.tenantId, kind)
var filters = {result: 'result', actorId: 'actor_id', orgId: 'actor_org_id', traceId: 'trace_id', requestId: 'request_id'}
for (name in criteria.filters.keySet()) {
    query.where(tableRead.equal(fields.get(filters.get(name)), criteria.filters.get(name)))
}
if (!criteria.keyword.isBlank()) {
    var searchable = ['actor_account', 'actor_name', 'actor_org_name', 'action_code', 'target_name',
        'trace_id', 'menu_path', 'operation_name', 'function_code', 'page_route', 'route_path']
    query.where(tableRead.any(searchable.map(name => tableRead.containsLiteral(fields.get(name), criteria.keyword))))
}

// 完整长文本匹配继续走数据库方言，成对参数保留原精确匹配契约。
if (criteria.parameterValue != null) {
    query.where(tableRead.containsFullText(tableRead.column('e', 'parameter_search_text', 'LOB'), criteria.parameterValue))
}
if (criteria.exactMatch != null) {
    query.where(tableRead.like(tableRead.column('e', 'detail_json', 'LOB'), criteria.exactMatch))
}
var start = criteria.start
var end = criteria.end
// 新事件为 UTC，旧记录缺少 event_time 时使用历史上海墙钟。两个区间均左闭右开。
var range = (field, from, until) => tableRead.all([
    tableRead.any([tableRead.greaterThan(field, from), tableRead.equal(field, from)]),
    tableRead.not(tableRead.any([tableRead.greaterThan(field, until), tableRead.equal(field, until)]))
])
var shanghai = ZoneId.of('Asia/Shanghai')
query.where(tableRead.any([
    range(fields.event_time, LogTimes.utc(start), LogTimes.utc(end)),
    tableRead.all([tableRead.isNull(fields.event_time),
        range(fields.created_time, LocalDateTime.ofInstant(start, shanghai), LocalDateTime.ofInstant(end, shanghai))])
]))
var cutoff = criteria.receivedBefore
if (cutoff != null) {
    var received = tableRead.column('e', 'received_time', 'TIME')
    query.where(tableRead.any([tableRead.isNull(received),
        tableRead.not(tableRead.greaterThan(received, LogTimes.utc(cutoff)))]))
}
if (stats) {
    // 空结果保持原 SUM 为 null 的响应，total 仍为零。
    var countResult = result => tableRead.countIf(tableRead.equal(fields.result, result))
    var totals = query.grouped({total: tableRead.count(), success_count: countResult('SUCCESS'),
        failure_count: countResult('FAILED'), denied_count: countResult('DENIED'), partial_count: countResult('PARTIAL')}, [])[0]
    if (totals.total == 0) {
        return {total: 0, success_count: null, failure_count: null, denied_count: null, partial_count: null}
    }
    return totals
}
var number = (name, fallback, maximum) => {
    var value = read(name)
    try {
        var parsed = value == null ? fallback : Long.parseLong(value)
        if (parsed >= 1 && parsed <= maximum) { return parsed }
    } catch (error) { /* 统一旧分页参数错误。 */ }
    throw new IllegalArgumentException('分页参数无效')
}
var page = number('page', 1, 10000)
var size = number('size', 20, 100)
var sortField = text('sortField', 40)
var direction = text('sortDir', 4)
if (sortField.isEmpty()) { sortField = 'event_time' }
if (direction.isEmpty()) { direction = 'desc' }
if (!['event_time', 'actor_name', 'action_code', 'duration_ms', 'total_count', 'success_count', 'failure_count'].contains(sortField)) {
    throw new IllegalArgumentException('排序字段无效')
}
if (!['asc', 'desc'].contains(direction)) {
    throw new IllegalArgumentException('排序方向无效')
}
var timeOrder = sortField == 'event_time'
var sort = timeOrder ? tableRead.coalesce([fields.event_time, tableRead.shiftHours(fields.created_time, -8)]) : fields.get(sortField)
var window = query.page(fields, sort, direction, 'tid', 'asc', page::int, size::int, timeOrder)
var formatter = SpringUtil.getBean('auditSanitizer')
return {list: window.rows.map(row => formatter.formatStoredEvent(row, false)), total: window.total,
    page: page, size: size, timeZone: 'Asia/Shanghai', appliedScopeSummary: scoped.mode}
