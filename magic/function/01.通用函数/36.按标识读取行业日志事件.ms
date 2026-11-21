{
  "id": "log_tenant_event_objects_v1",
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "36.按标识读取行业日志事件",
  "path": "/logeventobjects",
  "parameters": [
    {
      "name": "kind",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "operation",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    },
    {
      "name": "http",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    },
    {
      "name": "payload",
      "required": false,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": [],
  "description": "四类行业日志共用详情、批读及父事件范围内的批次明细读取。验证逐 ID 可见性并保持输入顺序。"
}
================================
import '@/common/logeventquery' as eventQuery
import cn.hutool.extra.spring.SpringUtil
import java.lang.Long
import java.lang.SecurityException
import java.util.HashMap
import java.util.HashSet
import java.util.List
import tableRead

// /common/logeventobjects 先验证授权范围，再一次批读请求 ID。详情与批次明细先执行同一父事件校验。
var detailed = operation == 'detail'
var scoped = eventQuery(kind, detailed)
var read = name => {
    var values = http.getValues(name)
    return values == null || values.isEmpty() ? null : values[0]
}
if (operation == 'batch' && !(payload instanceof List)) {
    throw new IllegalStateException('日志批量读取正文必须为 JSON 数组')
}
// 旧 MVC 接受数字和布尔标量转字符串，但拒绝数组、对象等非标量元素。
var ids = operation == 'batch' ? payload.map(value => {
    if (value == null || value instanceof String) { return value }
    if (value instanceof Number || value instanceof Boolean) { return value + '' }
    throw new IllegalStateException('日志批量读取标识必须为标量')
}) : [read('id')]
if (ids.isEmpty() || ids.size() > 100) {
    throw new IllegalArgumentException('一次最多查询100个不同事件')
}
var expected = new HashSet()
for (id in ids) {
    if (id == null || id.isBlank() || id.length() > 64 || !expected.add(id)) {
        throw new IllegalArgumentException('一次最多查询100个不同事件')
    }
}
var query = scoped.query
query.where(tableRead.in(scoped.fields.tid, ids))
var rows = query.rows(operation == 'items' ? {tid: scoped.fields.tid} : scoped.fields, ids.size()::int)
var byId = new HashMap()
for (row in rows) {
    byId.put(row.tid, row)
}
// 不在列表循环中补查。任何缺失或越权的记录都使整批失败。
if (rows.size() != ids.size() || !byId.keySet().equals(expected)) {
    throw new SecurityException('事件不存在或超出当前授权范围')
}
if (operation != 'items') {
    var formatter = SpringUtil.getBean('auditSanitizer')
    var ordered = ids.map(id => formatter.formatStoredEvent(byId.get(id), detailed))
    return detailed ? ordered[0] : ordered
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
var items = tableRead.from('log_batch_item_t', 'i', scoped.tenantId).withTimeoutSeconds(15)
var key = (name, value) => tableRead.equal(tableRead.column('i', name, 'TEXT'), value)
items.where(tableRead.all([key('scope_kind', 'TENANT'), key('scope_id', scoped.tenantId),
    key('identity_domain', 'global'), key('event_id', ids[0]), key('event_store', kind)]))
var sequence = tableRead.column('i', 'item_no', 'NUMBER')
var projection = {item_no: sequence}
for (name in ['target_type', 'target_id', 'target_name', 'action_code', 'result', 'reason_code']) {
    projection.put(name, tableRead.column('i', name, 'TEXT'))
}
var window = items.page(projection, sequence, 'asc', 'tid', page::int, size::int)
return {list: window.rows, total: window.total, page: page, size: size}
