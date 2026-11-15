{
  "properties": {},
  "id": "nifi_pipe_detail_http_v1",
  "script": null,
  "groupId": "0f27d7cf99494ceca369c14f35fbb987",
  "name": "45.NiFi流程详情接口响应",
  "path": "/nifiPipelineDetailHttp",
  "description": "为 GET 与 HEAD 接口按路径标识读取当前认证行业租户的流程。沿用原始画布 JSON 和保存版本，可见且可解析时返回 200，其他流程返回空体 404。只读取业务库，无 NiFi 请求。",
  "parameters": [
    {
      "name": "flowId",
      "description": "当前行业租户的画布标识，取自请求路径",
      "required": true,
      "dataType": "String",
      "type": "java.lang.String"
    }
  ],
  "options": []
}
================================
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper as objectMapper
import com.linewell.dataelement.integration.nifi.canvas.pipeline.HiveSinkPublicationPolicy
import com.linewell.dataelement.integration.nifi.canvas.pipeline.NifiNodeMapping
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline
import com.linewell.dataelement.integration.nifi.canvas.pipeline.Pipeline$Dsl as PipelineDsl
import com.linewell.dataelement.integration.nifi.canvas.pipeline.PipelineStatus
import com.linewell.dataelement.platform.tenant.domain.TenantAccessException
import com.linewell.dataelement.platform.tenant.domain.TenantContext
import com.linewell.dataelement.platform.web.error.GlobalExceptionHandler
import java.lang.Exception
import java.lang.SecurityException
import org.springframework.http.ResponseEntity
import org.springframework.web.server.ResponseStatusException
import org.ssssssss.script.exception.MagicScriptException
import org.ssssssss.script.exception.MagicScriptRuntimeException
import tableRead
import tenantRuntime

// @/common/nifiPipelineDetailHttp 为 GET 和 HEAD 按 flowId 读取当前行业租户的画布。
// 返回原始画布和保存版本，不可见或无法解析时返回空体 404。只查一次业务库，无 NiFi 请求。

var rootCause = failure => {
    var cause = failure
    while ((cause instanceof MagicScriptException || cause instanceof MagicScriptRuntimeException)
        && cause.getCause() != null) {
        cause = cause.getCause()
    }
    return cause
}

var pipelineStatus = value => {
    // 旧记录的 STOP、大小写差异及未知值继续按历史状态规则读取。
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

var readPipeline = () => {
    // 查询只使用可信租户和路径标识，浏览器提供的租户、资源映射均不参与读权限。
    var tenantId = tenantRuntime.id()
    if (TenantContext.usesControlDatabase() || TenantContext.isIgnored()) {
        throw new TenantAccessException('TENANT-DATABASE-REQUIRED', '流程数据必须在当前租户数据库中访问')
    }
    var query = tableRead.from('nifi_pipeline_t', 'p', tenantId)
    query.where(tableRead.equal(tableRead.column('p', 'id', 'TEXT'), flowId))
    var rows = query.rows({
        id: tableRead.column('p', 'id', 'TEXT'),
        name: tableRead.column('p', 'name', 'TEXT'),
        description: tableRead.column('p', 'description', 'TEXT'),
        createdAt: tableRead.column('p', 'created_at', 'NUMBER'),
        updatedAt: tableRead.column('p', 'updated_at', 'NUMBER'),
        dsl: tableRead.column('p', 'dsl_json', 'LOB'),
        processGroupId: tableRead.column('p', 'nifi_process_group_id', 'TEXT'),
        status: tableRead.column('p', 'status', 'TEXT'),
        lastDeployedHash: tableRead.column('p', 'last_deployed_hash', 'TEXT'),
        lastDeployedAt: tableRead.column('p', 'last_deployed_at', 'NUMBER'),
        lastStoppedAt: tableRead.column('p', 'last_stopped_at', 'NUMBER'),
        mapping: tableRead.column('p', 'node_mapping_json', 'LOB'),
        lastBulletinId: tableRead.column('p', 'last_bulletin_id', 'NUMBER'),
        deleted: tableRead.column('p', 'is_del', 'NUMBER')
    }, 1)
    if (rows.isEmpty() || rows[0].deleted == 1) {
        return null
    }

    var row = rows[0]
    var dsl = null
    var mapping = null
    try {
        // 沿用画布和映射的解码约定，保留缺省保存态并拒绝损坏的内容。
        if (row.dsl != null && !row.dsl.isBlank()) {
            dsl = objectMapper.readValue(row.dsl, PipelineDsl)
        }
        if (row.mapping != null && !row.mapping.isBlank()) {
            mapping = objectMapper.readValue(row.mapping, NifiNodeMapping)
        }
    } catch (failure) {
        if (rootCause(failure) instanceof JsonProcessingException) {
            return null
        }
        throw failure
    }
    return new Pipeline(row.id, row.name, row.description, row.createdAt?.longValue(),
        row.updatedAt?.longValue(), dsl, row.processGroupId, pipelineStatus(row.status),
        row.lastDeployedHash, row.lastDeployedAt?.longValue(), row.lastStoppedAt?.longValue(),
        mapping, row.lastBulletinId?.longValue())
}

try {
    var stored = readPipeline()
    if (stored == null) {
        return ResponseEntity.notFound().build()
    }

    // 复用保存与发布的纯画布投影，编辑器下一次保存仍校验持久化设计的哈希。
    var canvas = HiveSinkPublicationPolicy.forCanvas(stored)
    return ResponseEntity.ok(canvas.withStoredDesignVersion(stored))
} catch (failure) {
    // 迁移后保留原 MVC 的错误信封，且未找到画布的分支继续使用空体 404。
    var cause = rootCause(failure)
    if (cause instanceof ResponseStatusException) {
        var message = cause.getReason() == null ? '流程配置校验失败' : cause.getReason()
        var status = cause.getStatusCode()
        return ResponseEntity.status(status).body({code: status.value(), success: false, msg: message, error: message})
    }
    var handler = new GlobalExceptionHandler()
    if (cause instanceof TenantAccessException) {
        return ResponseEntity.ok(handler.handleTenantAccessException(cause))
    }
    if (cause instanceof SecurityException) {
        return ResponseEntity.ok(handler.handleSecurityException(cause))
    }
    if (cause instanceof Exception) {
        return ResponseEntity.ok(handler.handleException(cause))
    }
    throw failure
}
