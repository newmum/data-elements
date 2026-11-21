{
  "properties": {},
  "id": "nifi_processor_page_get_v1",
  "script": null,
  "groupId": "139cd4c65b0f5440a2d10b3650aeacab",
  "name": "04.查询处理器状态分页",
  "path": "/nodes",
  "method": "GET",
  "description": "在确认当前行业租户拥有流程之后读取一次原生递归快照。对全部处理器按约定排序，再返回指定页。",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "流程或映射资源标识"
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    },
    {
      "name": "access",
      "value": "TENANT_SESSION"
    },
    {
      "name": "head",
      "value": "true"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import '@/common/nifiFindCanvas' as findCanvas
import '@/common/nifiPipelineReadHttp' as withHttpResponse
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.model.common.StatusCodeEnum
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.lang.Math
import java.math.BigDecimal
import java.util.Locale
import java.util.regex.Pattern
import org.springframework.http.ResponseEntity
import org.springframework.util.NumberUtils
import tableSort
import tenantRuntime

// 在确认当前行业租户拥有流程之后读取一次原生递归快照。
// 对全部处理器按约定排序，再返回指定页。

var flowId = param.id

return withHttpResponse((parameterValues, textParameter, rootCause) => {
    var integerParameter = (name, fallback) => {
        var values = parameterValues(name)
        // 保留 MVC 对空值、方括号别名和重复数字参数的绑定规则。
        if (values == null || values.isEmpty() || (values.size() == 1 && values[0] == '')) {
            return fallback
        }
        try {
            return NumberUtils.parseNumber(values[0], Integer)
        } catch (failure) {
            if (!(rootCause(failure) instanceof IllegalArgumentException)) {
                throw failure
            }
            return null
        }
    }
    var invalidInteger = name => ResponseEntity.ok(CommonResponse.fail(StatusCodeEnum.PARAMS_UNVALID.getCode(),
        '参数[' + name + ']类型错误，期望类型: int', '参数类型不匹配'))
    var page = integerParameter('page', 1)
    if (page == null) {
        return invalidInteger('page')
    }
    var size = integerParameter('size', 20)
    if (size == null) {
        return invalidInteger('size')
    }

    // 先确认画布属于可信行业租户，再访问它保存的原生组。不存在的画布保留空体 404。
    var tenantId = tenantRuntime.id()
    if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
        throw new IllegalStateException('流程状态必须绑定行业租户')
    }
    var canvas = findCanvas(flowId, tenantId)
    if (canvas == null) {
        return ResponseEntity.notFound().build()
    }
    var groupId = canvas.nifiProcessGroupId()
    if (page < 1 || size < 1 || size > 100) {
        throw new IllegalArgumentException('处理器分页参数无效')
    }
    var snapshot = null
    if (groupId != null) {
        var nodeId = canvas.nodeMapping()?.runtimeNodeId()
        var runtime = nodeId == null ? null : nifi.bindAssignedNode(nodeId)
        try {
            snapshot = nifi.getProcessGroupStatus(groupId)
        } finally {
            if (runtime != null) { runtime.close() }
        }
    }

    var display = (row, key) => row.hasNonNull(key) ? row.path(key).asText() : '—'
    var units = ['B', 'KB', 'MB', 'GB', 'TB']
    var numberPattern = Pattern.compile('^([0-9]+(?:\\.[0-9]+)?)(?:\\s*(B|KB|MB|GB|TB))?', Pattern.CASE_INSENSITIVE)
    var number = (row, key, formatted, bytes) => {
        if (row.path(key).isNumber()) {
            return row.path(key).decimalValue()
        }
        var match = numberPattern.matcher(row.path(formatted).asText('').trim().replace(',', ''))
        if (!match.find()) {
            return null
        }
        var value = new BigDecimal(match.group(1))
        var unit = match.group(2)
        return bytes && unit != null
            ? value.multiply(BigDecimal.valueOf(1024L).pow(units.indexOf(unit.toUpperCase(Locale.ROOT))))
            : value
    }

    // 兼容原生响应中已包装与未包装的状态结构，同时限制组和处理器总数。
    var pending = []
    if (snapshot != null && !snapshot.isNull()) {
        var aggregate = snapshot.path('processGroupStatus').path('aggregateSnapshot')
        if (aggregate.isMissingNode()) {
            aggregate = snapshot.path('aggregateSnapshot')
        }
        pending.add(aggregate.isMissingNode() ? snapshot : aggregate)
    }
    var rows = []
    var groupIndex = 0
    while (groupIndex < pending.size()) {
        if (groupIndex >= 1000) {
            throw new IllegalArgumentException('流程状态范围过大，请拆分流程后查看')
        }
        var group = pending[groupIndex]
        groupIndex = groupIndex + 1
        for (wrapper in group.path('processorStatusSnapshots').elements()) {
            if (rows.size() >= 1000) {
                throw new IllegalArgumentException('处理器数量过多，请拆分流程后查看')
            }
            var row = wrapper.has('processorStatusSnapshot') ? wrapper.path('processorStatusSnapshot') : wrapper
            var id = row.path('id').asText('')
            if (id.isBlank()) {
                throw new IllegalStateException('处理器状态缺少标识')
            }
            rows.add({
                key: id, name: row.path('name').asText(''), type: row.path('type').asText(''),
                state: row.path('runStatus').asText(''),
                flowFilesIn: display(row, 'input'), flowFilesOut: display(row, 'output'),
                bytesIn: display(row, 'read'), bytesOut: display(row, 'written'), tasks: display(row, 'tasks'),
                flowFilesInCount: number(row, 'flowFilesIn', 'input', false),
                flowFilesOutCount: number(row, 'flowFilesOut', 'output', false),
                bytesInCount: number(row, 'bytesRead', 'read', true),
                bytesOutCount: number(row, 'bytesWritten', 'written', true),
                tasksCount: number(row, 'taskCount', 'tasks', false)
            })
        }
        for (child in group.path('processGroupStatusSnapshots').elements()) {
            pending.add(child.has('processGroupStatusSnapshot') ? child.path('processGroupStatusSnapshot') : child)
        }
    }

    var sort = {sortField: textParameter('sortField'), sortDir: textParameter('sortDir')}
    var sortFields = {name: 'name', flowFilesIn: 'flowFilesInCount', flowFilesOut: 'flowFilesOutCount',
        bytesIn: 'bytesInCount', bytesOut: 'bytesOutCount', tasks: 'tasksCount'}
    var ordered = tableSort.rows(rows, sort, sortFields, 'name', 'key')
    var offset = (page::long - 1L) * size::long
    var start = Math.min(offset, ordered.size()::long)::int
    var end = Math.min(start + size, ordered.size())
    return ResponseEntity.ok({list: ordered.subList(start, end), total: rows.size(), page: page, size: size,
        deployed: groupId != null})
})
