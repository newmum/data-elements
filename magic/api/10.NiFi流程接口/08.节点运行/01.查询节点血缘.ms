{
  "properties": {},
  "id": "0a119f2475974554a8b3547dd70febf0",
  "script": null,
  "groupId": "32618128861a58318b8524b74df21a23",
  "name": "01.查询节点血缘",
  "path": "/lineage",
  "method": "POST",
  "description": "查询当前行业租户已部署流程中指定画布节点的 NiFi 血缘。id 为流程标识，nodeId 为画布节点标识，maxEvents 默认为 10，范围 1 至 100。按本租户保存的节点映射查询原生事件和血缘图，清理临时 NiFi 查询。返回原始图与脱敏事件摘要。流程不存在返回 404，部署或节点映射缺失返回 400，原生查询超时返回 504。",
  "parameters": [
    {
      "name": "id",
      "required": true,
      "dataType": "String",
      "description": "流程或映射资源标识"
    },
    {
      "name": "nodeId",
      "required": true,
      "dataType": "String",
      "description": "节点标识"
    }
  ],
  "options": [
    {
      "name": "wrap_request_parameter",
      "value": "param"
    }
  ],
  "headers": [],
  "paths": []
}
================================
import request
import tableRead
import tenantRuntime
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper as objectMapper
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiClient as nifi
import com.linewell.dataelement.integration.nifi.canvas.nifi.NifiException
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline$Dsl as PipelineDsl
import com.linewell.dataelement.model.common.CommonResponse
import com.linewell.dataelement.model.common.StatusCodeEnum
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler
import java.lang.IllegalArgumentException
import java.lang.InterruptedException
import java.lang.System
import java.lang.Thread
import java.util.LinkedHashSet
import java.util.concurrent.TimeUnit
import org.springframework.http.ResponseEntity
import org.springframework.util.NumberUtils
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException

// POST /nifi/node/lineage 查询节点血缘。 查询参数 id、nodeId。
// POST /nifi/node/lineage 查询已部署画布节点的原生血缘。
// maxEvents 默认为 10，范围 1 至 100。只读取已认证行业租户的保存态。
// 返回原始血缘图和事件摘要。业务库只读，NiFi 临时查询由本请求创建并清理。

var reject = (status, message) => ResponseEntity.status(status).body({error: message})
var rootCause = failure => {
    var cause = failure
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException)
        && cause.getCause() != null) {
        cause = cause.getCause()
    }
    return cause
}

// 沿用 MVC 的参数别名、首值和类型转换，拦截无效输入后才查询租户流程。
var eventValues = request.getValues('maxEvents')
if (eventValues == null) {
    eventValues = request.getValues('maxEvents[]')
}
var maxEvents = 10
if (eventValues != null && eventValues.length > 0 && (eventValues.length > 1 || eventValues[0] != '')) {
    try {
        maxEvents = NumberUtils.parseNumber(eventValues[0], Integer)
    } catch (failure) {
        if (!(rootCause(failure) instanceof IllegalArgumentException)) {
            throw failure
        }
        return ResponseEntity.ok(CommonResponse.fail(StatusCodeEnum.PARAMS_UNVALID.getCode(),
            '参数[maxEvents]类型错误，期望类型: int', '参数类型不匹配'))
    }
}
if (maxEvents < 1 || maxEvents > 100) {
    return reject(400, '事件数量必须为 1–100')
}

var rejectQueryErrors = (results, subject) => {
    var errors = results.path('errors')
    if (errors.isArray() && !errors.isEmpty()) {
        throw new NifiException('NiFi ' + subject + '查询失败，请检查事件存储及节点权限', 502, null)
    }
}

var pollQuery = (kind, queryId, clusterNodeId, deadline) => {
    // 事件查询共享同一个截止时间。血缘查询沿用独立的最多 20 次轮询。
    var attempt = 0
    while (attempt < 20 && (deadline == null || System.nanoTime() < deadline)) {
        var response = kind == 'provenance' ? nifi.getProvenance(queryId) : nifi.getLineage(queryId, clusterNodeId)
        var query = response.path(kind)
        if (query.path('finished').asBoolean(false)) {
            return query.path('results')
        }
        Thread.sleep(250)
        attempt = attempt + 1
    }
    var message = kind == 'provenance' ? 'Provenance query timed out' : 'Lineage query timed out'
    throw new NifiException(message, 504, null)
}

try {
    var tenantId = tenantRuntime.id()
    if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
        throw new TenantAccessException('TENANT-DATABASE-REQUIRED', '流程数据必须在当前租户数据库中访问')
    }

    // 一次查询核对本租户流程主键，只读取部署标识、软删除标志和两份保存态 JSON。
    var pipeline = tableRead.from('nifi_pipeline_t', 'p', tenantId)
    pipeline.where(tableRead.equal(tableRead.column('p', 'id', 'TEXT'), param.id))
    var rows = pipeline.rows({
        processGroupId: tableRead.column('p', 'nifi_process_group_id', 'TEXT'),
        deleted: tableRead.column('p', 'is_del', 'NUMBER'),
        dsl: tableRead.column('p', 'dsl_json', 'LOB'),
        mapping: tableRead.column('p', 'node_mapping_json', 'LOB')
    }, 1)
    if (rows.isEmpty() || rows[0].deleted == 1) {
        return ResponseEntity.notFound().build()
    }

    var row = rows[0]
    var mapping = null
    try {
        // 沿用应用 Jackson 配置。保存态任一 JSON 无法解码时，流程仍按不可读取处理。
        if (row.dsl != null && !row.dsl.isBlank()) {
            objectMapper.readValue(row.dsl, PipelineDsl)
        }
        if (row.mapping != null && !row.mapping.isBlank()) {
            mapping = objectMapper.readValue(row.mapping, NifiNodeMapping)
        }
    } catch (failure) {
        if (rootCause(failure) instanceof JsonProcessingException) {
            return ResponseEntity.notFound().build()
        }
        throw failure
    }
    if (row.processGroupId == null || mapping == null) {
        return reject(400, '流程尚未部署，无法查询血缘')
    }

    // 先使用画布节点绑定的主处理器，再按保存的反向映射补充同一节点的处理器。
    var nodeId = param.nodeId
    var processorIds = new LinkedHashSet()
    var primaryIds = mapping.primaryProcessorIds()
    var primary = primaryIds == null ? null : primaryIds.get(nodeId)
    if (primary != null && !primary.isBlank()) {
        processorIds.add(primary)
    }
    var reverseIndex = mapping.processorIdToCanvasNode()
    if (reverseIndex != null) {
        for (processorId in reverseIndex.keySet()) {
            if (nodeId.equals(reverseIndex.get(processorId)) && processorId != null && !processorId.isBlank()) {
                processorIds.add(processorId)
            }
        }
    }
    if (processorIds.isEmpty()) {
        return reject(400, '节点未映射到 NiFi 处理器')
    }

    var runtime = null
    var provenanceId = null
    var lineageId = null
    var clusterNodeId = null
    try {
        // 本次查询和清理共用一次节点绑定，轮询及候选遍历期间不再查询节点配置。
        var searchDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        runtime = nifi.bindRuntimeNode()
        var events = []
        for (processorId in processorIds) {
            if (System.nanoTime() >= searchDeadline) {
                return reject(504, '节点事件查询超时，请稍后重试')
            }
            var submitted = nifi.submitProcessorProvenance(processorId, maxEvents)
            provenanceId = submitted.path('provenance').path('id').asText(null)
            if (provenanceId == null) {
                return reject(502, 'NiFi 未返回 provenance id')
            }
            var source = pollQuery('provenance', provenanceId, null, searchDeadline)
            rejectQueryErrors(source, '事件')

            // NiFi 偶尔会忽略查询过滤器。只接受该处理器自己的事件。
            for (event in source.path('provenanceEvents').elements()) {
                var eventProcessor = event.path('componentId').asText(event.path('processorId').asText(''))
                if (processorId.equals(eventProcessor)) {
                    events.add(event)
                }
            }
            nifi.deleteProvenance(provenanceId)
            provenanceId = null
            if (!events.isEmpty()) {
                break
            }
        }
        if (events.isEmpty()) {
            return ResponseEntity.ok({nodes: [], edges: [], message: '暂无血缘事件'})
        }

        // 用长整型事件 ID 选择最新锚点，ID 相同时保留首次命中的事件。
        var anchor = events[0]
        var summaries = []
        var summaryFields = ['eventId', 'eventTime', 'eventType', 'componentId', 'componentName', 'clusterNodeId']
        for (event in events) {
            if (event.path('eventId').asLong(-1) > anchor.path('eventId').asLong(-1)) {
                anchor = event
            }
            var summary = {}
            for (field in summaryFields) {
                if (event.hasNonNull(field)) {
                    summary.put(field, event.get(field))
                }
            }
            summaries.add(summary)
        }

        var eventId = anchor.path('eventId').asText(null)
        if (eventId == null) {
            return reject(502, '事件缺少 eventId')
        }
        clusterNodeId = anchor.path('clusterNodeId').asText(null)
        var submitted = nifi.submitLineage(eventId, clusterNodeId)
        lineageId = submitted.path('lineage').path('id').asText(null)
        if (lineageId == null) {
            return reject(502, 'NiFi 未返回 lineage id')
        }
        var graph = pollQuery('lineage', lineageId, clusterNodeId, null)
        rejectQueryErrors(graph, '血缘')
        return ResponseEntity.ok({anchorEventId: eventId, nodes: graph.path('nodes'),
            links: graph.path('links'), events: summaries})
    } catch (failure) {
        var cause = rootCause(failure)
        if (cause instanceof NifiException) {
            return reject(cause.status() > 0 ? cause.status() : 502, cause.getMessage())
        }
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt()
            return reject(503, 'polling interrupted')
        }
        throw failure
    } finally {
        // 退出时先清理血缘和事件查询，再释放本请求绑定的 NiFi 节点。
        try {
            if (lineageId != null) {
                nifi.deleteLineage(lineageId, clusterNodeId)
            }
            if (provenanceId != null) {
                nifi.deleteProvenance(provenanceId)
            }
        } finally {
            if (runtime != null) {
                runtime.close()
            }
        }
    }
} catch (failure) {
    var cause = rootCause(failure)
    if (cause instanceof TenantAccessException) {
        return ResponseEntity.ok(new GlobalExceptionHandler().handleTenantAccessException(cause))
    }
    if (cause instanceof Exception) {
        return ResponseEntity.ok(new GlobalExceptionHandler().handleException(cause))
    }
    throw failure
}
