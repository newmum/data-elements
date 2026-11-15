{
  "properties": {},
  "id": "nifi_saved_canvas_rows_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "33.NiFi已保存流程列表",
  "path": "/nifiSavedCanvasRows",
  "description": "从认证行业库逐行读取有效画布，按调用方投影组装已保存列表，跳过损坏的流程和映射。",
  "parameters": [
    {
      "name": "project",
      "description": "调用方的业务参数",
      "required": true,
      "dataType": "Object",
      "type": "java.lang.Object"
    }
  ],
  "options": []
}
================================
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper as objectMapper
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline$Dsl as PipelineDsl
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException
import tableRead
import tenantRuntime

// @/common/nifiSavedCanvasRows 按可信行业库读取完整的已保存流程列表。
// project 在游标内把单条已解码画布转换成轻量响应行，回调结束后释放该行保存态。
// 兼容旧列表的坏记录跳过和空更新时间末置。只读一次业务库，不访问 NiFi。

var tenantId = tenantRuntime.id()
if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
    throw new TenantAccessException('TENANT-DATABASE-REQUIRED', '流程数据必须在当前租户数据库中访问')
}

var parseStatus = value => {
    if ('STOP'.equalsIgnoreCase(value)) {
        return PipelineStatus.STOPPED
    }
    for (status in PipelineStatus.values()) {
        if (status.name().equalsIgnoreCase(value)) {
            return status
        }
    }
    return PipelineStatus.SAVED
}

var toSavedCanvas = row => {
    var dsl = null
    try {
        if (row.dsl != null && !row.dsl.isBlank()) {
            dsl = objectMapper.readValue(row.dsl, PipelineDsl)
        }
        // 旧接口会略过任何映射无法解码的流程，虽然响应本身无需返回映射。
        if (row.mapping != null && !row.mapping.isBlank()) {
            objectMapper.readValue(row.mapping, NifiNodeMapping)
        }
    } catch (failure) {
        var cause = failure
        while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException)
            && cause.getCause() != null) {
            cause = cause.getCause()
        }
        if (cause instanceof JsonProcessingException) {
            return null
        }
        throw failure
    }
    row.dsl = dsl
    row.remove('mapping')
    row.status = parseStatus(row.status)
    return row
}

var query = tableRead.from('nifi_pipeline_t', 'p', tenantId)
query.where(tableRead.equal(tableRead.column('p', 'is_del', 'NUMBER'), 0))
var updatedAt = tableRead.column('p', 'updated_at', 'NUMBER')
var rows = []
var noUpdateTime = []

// 保留原生排序中的同值次序。旧 Java 把更新时间为空的项稳定移到末尾。
query.forEachOrderedRow({
    id: tableRead.column('p', 'id', 'TEXT'),
    name: tableRead.column('p', 'name', 'TEXT'),
    description: tableRead.column('p', 'description', 'TEXT'),
    createdAt: tableRead.column('p', 'created_at', 'NUMBER'),
    updatedAt: updatedAt,
    dsl: tableRead.column('p', 'dsl_json', 'LOB'),
    mapping: tableRead.column('p', 'node_mapping_json', 'LOB'),
    status: tableRead.column('p', 'status', 'TEXT'),
    processGroupId: tableRead.column('p', 'nifi_process_group_id', 'TEXT'),
    lastDeployedAt: tableRead.column('p', 'last_deployed_at', 'NUMBER'),
    lastStoppedAt: tableRead.column('p', 'last_stopped_at', 'NUMBER')
}, [updatedAt], ['desc'], true, row => {
    var canvas = toSavedCanvas(row)
    if (canvas != null) {
        var item = project(canvas)
        var destination = row.updatedAt == null ? noUpdateTime : rows
        destination.add(item)
    }
})
rows.addAll(noUpdateTime)
return rows
