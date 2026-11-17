{
  "id": "log_tenant_event_criteria_v1",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "96.解析行业日志事件条件",
  "path": "/logeventcriteria",
  "parameters": [
    {"name": "input", "required": true, "dataType": "Object", "type": "java.lang.Object"},
    {"name": "tenantId", "required": true, "dataType": "String", "type": "java.lang.String"},
    {"name": "kind", "required": true, "dataType": "String", "type": "java.lang.String"}
  ],
  "options": [],
  "description": "行业事件查询、导出提交和归档提交共用事件筛选条件、脱敏参数匹配和时间窗口。调用方先校验会话与数据范围。"
}
================================
import '@/common/log-parameter-keyword-condition' as parameterCondition
import com.linewell.dataelement.platform.audit.AuditRecorder
import com.linewell.dataelement.platform.audit.AuditRequestParameters
import java.time.Duration
import java.time.Instant

// 在事件查询或提交文件任务前验证共用的筛选语义。租户和日志类别由已授权的调用方提供。
var text = (name, limit) => {
    var value = input.get(name)
    var rendered = value == null ? '' : value + ''
    if (rendered.length() > limit) { throw new IllegalArgumentException('日志查询内容过长') }
    return rendered
}
var filters = {}
for (name in ['result', 'actorId', 'orgId', 'traceId', 'requestId']) {
    var value = text(name, 128)
    if (!value.isBlank()) {
        if (name == 'result' && !AuditRecorder.RESULTS.contains(value)) {
            throw new IllegalArgumentException('日志结果无效')
        }
        filters.put(name, value)
    }
}
var keyword = text('keyword', 100)
var condition = parameterCondition(tenantId, kind, input.get('parameterKeyword'))
if (condition != null && (condition.operator != 'contains' || condition.column != 'parameter_search_text')) {
    throw new IllegalStateException('日志参数条件格式无效')
}
var parameterName = text('parameterName', 64).strip()
var parameterValue = text('parameterValue', 510).strip()
var exactMatch = null
if (!parameterName.isEmpty() || !parameterValue.isEmpty()) {
    if (!['operations', 'requests'].contains(kind) || parameterName.isEmpty() || parameterValue.isEmpty()) {
        throw new IllegalArgumentException('请选择操作日志或接口日志，并同时填写参数名称和参数值')
    }
    exactMatch = AuditRequestParameters.exactMatchPattern(parameterName, parameterValue)
}

var instant = (value, fallback) => {
    if (value == null || (value + '').isBlank()) { return fallback }
    try { return Instant.parse(value + '') } catch (error) {
        throw new IllegalArgumentException('日志时间格式无效，请使用带时区的时间')
    }
}
var end = instant(input.get('endTime'), Instant.now().plusSeconds(1))
var start = instant(input.get('startTime'), end.minus(Duration.ofDays(7)))
if (!start.isBefore(end) || Duration.between(start, end).compareTo(Duration.ofDays(90)) > 0) {
    throw new IllegalArgumentException('请选择不超过90天的有效时间区间')
}
var receivedBefore = input.get('receivedBefore')
return {filters: filters, keyword: keyword, parameterValue: condition == null ? null : condition.value,
    exactMatch: exactMatch, start: start, end: end, receivedBefore: receivedBefore == null ? null : instant(receivedBefore, Instant.now())}
